package com.chiptrack.app.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.chiptrack.app.R
import com.chiptrack.app.model.GameSession
import com.chiptrack.app.share.macro.ShareMacroController
import com.chiptrack.app.share.macro.ShareMacroStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 不接微信 SDK：优先直达微信；记住上次目标 App；必要时再走系统选择器。
 * 测试版可选无障碍宏：首次录制微信内点击，之后回放。
 */
object ShareHelper {
    private const val WECHAT_PACKAGE = "com.tencent.mm"

    fun shareSessionTable(fragment: Fragment, session: GameSession) {
        val context = fragment.requireContext()
        if (session.players.isEmpty()) {
            Toast.makeText(context, R.string.share_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val activity = fragment.requireActivity()
        val snapshotStore = ShareSnapshotStore(context)
        val deltas = snapshotStore.deltasFor(session)
        Toast.makeText(context, R.string.share_generating, Toast.LENGTH_SHORT).show()

        ScoreboardImageExporter.capture(activity, session, deltas) { result ->
            result
                .onSuccess { uri ->
                    snapshotStore.saveFromSession(session)
                    launchShare(fragment, uri)
                }
                .onFailure {
                    Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun launchShare(fragment: Fragment, uri: Uri) {
        val context = fragment.requireContext()
        val macroStore = ShareMacroStore(context)
        if (macroStore.featureEnabled) {
            if (tryMacroShare(fragment, uri, macroStore)) return
        }

        val prefs = ShareTargetStore(context)
        val preferred = prefs.lastPackage
        val wechatInstalled = isPackageInstalled(context, WECHAT_PACKAGE)

        // 1) 上次记住的目标仍可用 → 直达
        if (!preferred.isNullOrBlank() && canShareTo(context, preferred, uri)) {
            if (shareToPackage(fragment, uri, preferred)) return
        }

        // 2) 微信已安装 → 直达微信并记住
        if (wechatInstalled && canShareTo(context, WECHAT_PACKAGE, uri)) {
            if (shareToPackage(fragment, uri, WECHAT_PACKAGE)) {
                prefs.lastPackage = WECHAT_PACKAGE
                return
            }
        }

        // 3) 让用户选：微信 / 其他
        showSharePicker(fragment, uri, wechatInstalled, prefs)
    }

    /**
     * @return true 表示已接管本次分享（含弹出引导）；false 表示应走普通分享。
     */
    private fun tryMacroShare(
        fragment: Fragment,
        uri: Uri,
        macroStore: ShareMacroStore
    ): Boolean {
        val context = fragment.requireContext()
        if (!ShareMacroController.isServiceEnabled(context)) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.share_macro_need_a11y_title)
                .setMessage(R.string.share_macro_need_a11y_body)
                .setPositiveButton(R.string.share_macro_open_a11y) { _, _ ->
                    ShareMacroController.openAccessibilitySettings(context)
                }
                .setNegativeButton(R.string.share_macro_share_without) { _, _ ->
                    shareToWechatOrFallback(fragment, uri)
                }
                .setNeutralButton(R.string.share_macro_action_disable) { _, _ ->
                    macroStore.featureEnabled = false
                    shareToWechatOrFallback(fragment, uri)
                }
                .show()
            return true
        }

        if (!shareToPackage(fragment, uri, WECHAT_PACKAGE)) {
            Toast.makeText(context, R.string.share_wechat_failed, Toast.LENGTH_SHORT).show()
            return false
        }
        ShareTargetStore(context).lastPackage = WECHAT_PACKAGE

        if (macroStore.hasSteps()) {
            ShareMacroController.scheduleReplay(context)
        } else {
            ShareMacroController.startRecording(context)
        }
        return true
    }

    private fun shareToWechatOrFallback(fragment: Fragment, uri: Uri) {
        if (shareToPackage(fragment, uri, WECHAT_PACKAGE)) {
            ShareTargetStore(fragment.requireContext()).lastPackage = WECHAT_PACKAGE
            return
        }
        openSystemChooser(fragment, uri)
    }

    private fun showSharePicker(
        fragment: Fragment,
        uri: Uri,
        wechatInstalled: Boolean,
        prefs: ShareTargetStore
    ) {
        val context = fragment.requireContext()
        val options = buildList {
            if (wechatInstalled) add(context.getString(R.string.share_to_wechat))
            add(context.getString(R.string.share_to_other))
        }.toTypedArray()

        if (options.size == 1) {
            openSystemChooser(fragment, uri)
            return
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.share_chooser_title)
            .setItems(options) { _, which ->
                val label = options[which]
                if (label == context.getString(R.string.share_to_wechat)) {
                    if (shareToPackage(fragment, uri, WECHAT_PACKAGE)) {
                        prefs.lastPackage = WECHAT_PACKAGE
                    } else {
                        Toast.makeText(context, R.string.share_wechat_failed, Toast.LENGTH_SHORT).show()
                        openSystemChooser(fragment, uri)
                    }
                } else {
                    openSystemChooser(fragment, uri)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun shareToPackage(fragment: Fragment, uri: Uri, packageName: String): Boolean {
        val context = fragment.requireContext()
        return runCatching {
            context.grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val send = buildSendIntent(context, uri).apply {
                setPackage(packageName)
            }
            fragment.startActivity(send)
            true
        }.getOrElse {
            false
        }
    }

    private fun openSystemChooser(fragment: Fragment, uri: Uri) {
        val context = fragment.requireContext()
        val send = buildSendIntent(context, uri)
        val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser_title))
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        fragment.startActivity(chooser)
    }

    private fun buildSendIntent(context: Context, uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
            clipData = android.content.ClipData.newUri(context.contentResolver, "share", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    private fun canShareTo(context: Context, packageName: String, uri: Uri): Boolean {
        if (!isPackageInstalled(context, packageName)) return false
        val intent = buildSendIntent(context, uri).apply { setPackage(packageName) }
        return intent.resolveActivity(context.packageManager) != null
    }
}

/** 记住上次直达分享的目标包名 */
class ShareTargetStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastPackage: String?
        get() = prefs.getString(KEY_PACKAGE, null)
        set(value) {
            prefs.edit().putString(KEY_PACKAGE, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "chiptrack_share_target"
        private const val KEY_PACKAGE = "last_package"
    }
}

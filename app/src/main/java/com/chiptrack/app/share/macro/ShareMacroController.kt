package com.chiptrack.app.share.macro

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import com.chiptrack.app.R

object ShareMacroController {

    fun isServiceEnabled(context: Context): Boolean {
        if (ShareMacroAccessibilityService.instance != null) return true
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val expected = ShareMacroAccessibilityService::class.java.canonicalName ?: return false
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val id = info.resolveInfo?.serviceInfo?.let { si ->
                    "${si.packageName}/${si.name}"
                } ?: info.id
                id.contains(expected) || info.id.endsWith(".ShareMacroAccessibilityService")
            }
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun statusSummary(context: Context): String {
        val store = ShareMacroStore(context)
        val a11y = if (isServiceEnabled(context)) {
            context.getString(R.string.share_macro_a11y_on)
        } else {
            context.getString(R.string.share_macro_a11y_off)
        }
        val feature = if (store.featureEnabled) {
            context.getString(R.string.share_macro_feature_on)
        } else {
            context.getString(R.string.share_macro_feature_off)
        }
        val steps = store.steps.size
        val macro = if (steps > 0) {
            context.getString(R.string.share_macro_steps_count, steps)
        } else {
            context.getString(R.string.share_macro_steps_empty)
        }
        return "$feature\n$a11y\n$macro"
    }

    fun startRecording(context: Context): Boolean {
        val service = ShareMacroAccessibilityService.instance
        if (service == null) {
            Toast.makeText(context, R.string.share_macro_need_a11y, Toast.LENGTH_LONG).show()
            return false
        }
        service.startRecording()
        Toast.makeText(context, R.string.share_macro_recording, Toast.LENGTH_LONG).show()
        return true
    }

    /** 从微信返回 App 时调用：结束录制并保存 */
    fun finishRecordingIfNeeded(context: Context) {
        val service = ShareMacroAccessibilityService.instance ?: return
        if (!service.isRecording()) return
        val store = ShareMacroStore(context)
        val count = service.stopRecordingAndSave(store)
        val msg = if (count > 0) {
            context.getString(R.string.share_macro_saved, count)
        } else {
            context.getString(R.string.share_macro_saved_empty)
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    fun scheduleReplay(context: Context): Boolean {
        val service = ShareMacroAccessibilityService.instance
        if (service == null) {
            Toast.makeText(context, R.string.share_macro_need_a11y, Toast.LENGTH_LONG).show()
            return false
        }
        val steps = ShareMacroStore(context).steps
        if (steps.isEmpty()) {
            Toast.makeText(context, R.string.share_macro_steps_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        Toast.makeText(context, R.string.share_macro_replaying, Toast.LENGTH_SHORT).show()
        val appCtx = context.applicationContext
        service.replay(steps) { ok ->
            val text = if (ok) {
                appCtx.getString(R.string.share_macro_replay_done)
            } else {
                appCtx.getString(R.string.share_macro_replay_failed)
            }
            Toast.makeText(appCtx, text, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    fun clearMacro(context: Context) {
        ShareMacroAccessibilityService.instance?.cancel()
        ShareMacroStore(context).clearSteps()
        Toast.makeText(context, R.string.share_macro_cleared, Toast.LENGTH_SHORT).show()
    }
}

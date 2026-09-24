package com.chiptrack.app.share.macro

import android.content.Context
import com.chiptrack.app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ShareMacroSettingsUi {

    fun show(context: Context) {
        val store = ShareMacroStore(context)
        val summary = ShareMacroController.statusSummary(context)
        val enableLabel = if (store.featureEnabled) {
            context.getString(R.string.share_macro_action_disable)
        } else {
            context.getString(R.string.share_macro_action_enable)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.share_macro_settings_title)
            .setMessage(
                context.getString(R.string.share_macro_settings_body) + "\n\n" + summary
            )
            .setPositiveButton(R.string.share_macro_open_a11y) { _, _ ->
                ShareMacroController.openAccessibilitySettings(context)
            }
            .setNeutralButton(enableLabel) { _, _ ->
                store.featureEnabled = !store.featureEnabled
                show(context)
            }
            .setNegativeButton(R.string.share_macro_clear) { _, _ ->
                ShareMacroController.clearMacro(context)
            }
            .show()
    }
}

package com.chiptrack.app.share.macro

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 测试用：保存分享宏步骤，以及是否启用宏分享。
 */
class ShareMacroStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 测试开关：开启后走录制/回放；关闭则用普通微信分享 */
    var featureEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var steps: List<ShareMacroStep>
        get() {
            val raw = prefs.getString(KEY_STEPS, null) ?: return emptyList()
            return runCatching {
                json.decodeFromString(ListSerializer(ShareMacroStep.serializer()), raw)
            }.getOrDefault(emptyList())
        }
        set(value) {
            val raw = json.encodeToString(ListSerializer(ShareMacroStep.serializer()), value)
            prefs.edit().putString(KEY_STEPS, raw).apply()
        }

    fun clearSteps() {
        prefs.edit().remove(KEY_STEPS).apply()
    }

    fun hasSteps(): Boolean = steps.isNotEmpty()

    companion object {
        private const val PREFS_NAME = "chiptrack_share_macro"
        private const val KEY_ENABLED = "feature_enabled"
        private const val KEY_STEPS = "steps_json"
    }
}

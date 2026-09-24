package com.chiptrack.app.share.macro

import kotlinx.serialization.Serializable

@Serializable
data class ShareMacroStep(
    /** 相对上一步的等待毫秒（第一步也是相对「开始回放」） */
    val delayMs: Long,
    val x: Float,
    val y: Float,
    /** 调试用：点击控件文案 */
    val label: String = ""
)

enum class ShareMacroPhase {
    IDLE,
    RECORDING,
    REPLAYING
}

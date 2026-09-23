package com.chiptrack.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ChipAction {
    /** 开局/中途入局拿取 */
    JOIN,
    /** 对局中拿取 */
    TAKE,
    /** 对局中退还（提早离场等） */
    RETURN,
    /** 终局统一退还（可填 0） */
    FINAL_RETURN,
    /** 标记离桌 */
    EXIT
}

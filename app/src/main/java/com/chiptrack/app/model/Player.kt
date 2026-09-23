package com.chiptrack.app.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlayerStatus {
    ACTIVE,
    EXITED
}

@Serializable
data class Player(
    val id: String,
    val name: String,
    /** 累计拿取（入局 + 中途拿取） */
    val totalTaken: Int,
    /** 累计退还（中途或终局退还） */
    val totalReturned: Int = 0,
    val status: PlayerStatus = PlayerStatus.ACTIVE
) {
    /** 盈亏 = 累计退还 - 累计拿取（结算用） */
    val profit: Int get() = totalReturned - totalTaken

    /** 对局主显示：拿取 − 退还（拿取增加、退还减少） */
    val netTaken: Int get() = totalTaken - totalReturned

    fun withTake(amount: Int): Player {
        require(amount > 0) { "拿取积分必须大于 0" }
        return copy(totalTaken = totalTaken + amount)
    }

    fun withReturn(amount: Int): Player {
        require(amount >= 0) { "退还积分不能为负数" }
        if (amount == 0) return this
        return copy(totalReturned = totalReturned + amount)
    }

    fun exitTable(): Player {
        if (status == PlayerStatus.EXITED) return this
        return copy(status = PlayerStatus.EXITED)
    }
}

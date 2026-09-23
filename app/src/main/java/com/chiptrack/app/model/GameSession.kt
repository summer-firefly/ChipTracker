package com.chiptrack.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class SessionPhase {
    SETUP,
    PLAYING,
    SETTLED
}

/**
 * 计分策略：
 * 1. 积分只从「池子」经拿取流出、经退还流回
 * 2. 每人：盈亏 = 累计退还 - 累计拿取
 * 3. 全场对账：差额 = 全场退还合计 - 全场拿取合计
 *    - 0：账平
 *    - >0：多退了
 *    - <0：少退了
 */
@Serializable
data class GameSession(
    val defaultBuyIn: Int = 1000,
    val players: List<Player> = emptyList(),
    val records: List<ChipRecord> = emptyList(),
    val phase: SessionPhase = SessionPhase.SETUP
) {
    val activePlayers: List<Player> get() = players.filter { it.status == PlayerStatus.ACTIVE }
    val exitedPlayers: List<Player> get() = players.filter { it.status == PlayerStatus.EXITED }

    val totalTakenSum: Int get() = players.sumOf { it.totalTaken }
    val totalReturnedSum: Int get() = players.sumOf { it.totalReturned }
    val totalProfit: Int get() = players.sumOf { it.profit }

    /** 退还合计 − 拿取合计；0 账平，>0 多退，<0 少退 */
    val reconcileGap: Int get() = totalReturnedSum - totalTakenSum

    fun recordsFor(playerId: String): List<ChipRecord> =
        records.filter { it.playerId == playerId }.sortedByDescending { it.timestamp }

    fun recordsNewestFirst(): List<ChipRecord> =
        records.sortedByDescending { it.timestamp }

    fun start(names: List<String>, buyIn: Int): GameSession {
        require(names.isNotEmpty()) { "至少需要一位玩家" }
        require(buyIn > 0) { "初始积分必须大于 0" }
        val now = System.currentTimeMillis()
        val createdPlayers = mutableListOf<Player>()
        val createdRecords = mutableListOf<ChipRecord>()
        names.forEachIndexed { index, name ->
            val player = Player(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifEmpty { "玩家" },
                totalTaken = buyIn,
                totalReturned = 0,
                status = PlayerStatus.ACTIVE
            )
            createdPlayers += player
            createdRecords += recordOf(player, ChipAction.JOIN, buyIn, now + index)
        }
        return copy(
            defaultBuyIn = buyIn,
            players = createdPlayers,
            records = createdRecords,
            phase = SessionPhase.PLAYING
        )
    }

    fun addPlayer(name: String, buyIn: Int = defaultBuyIn): GameSession {
        require(phase == SessionPhase.PLAYING) { "仅对局中可加入玩家" }
        require(name.isNotBlank()) { "玩家名称不能为空" }
        require(buyIn > 0) { "入局积分必须大于 0" }
        val player = Player(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            totalTaken = buyIn,
            totalReturned = 0,
            status = PlayerStatus.ACTIVE
        )
        return copy(
            players = players + player,
            records = records + recordOf(player, ChipAction.JOIN, buyIn)
        )
    }

    fun take(playerId: String, amount: Int): GameSession =
        mutatePlayer(playerId, allowExited = false) { player ->
            val updated = player.withTake(amount)
            updated to recordOf(updated, ChipAction.TAKE, amount)
        }

    /** 在桌或已退出玩家均可退还（离桌后补退手上剩余积分） */
    fun returnPoints(playerId: String, amount: Int): GameSession =
        mutatePlayer(playerId, allowExited = true) { player ->
            require(amount > 0) { "退还积分必须大于 0" }
            val updated = player.withReturn(amount)
            updated to recordOf(updated, ChipAction.RETURN, amount)
        }

    /**
     * 终局统一退还：仅为**在桌**玩家填写手上剩余（可为 0）。
     * 已退出玩家应已通过「退还」结清，终局记 0，不再要求填写。
     */
    fun finalizeReturnsAndSettle(amounts: Map<String, Int>): GameSession {
        require(phase == SessionPhase.PLAYING) { "仅对局中可结算" }
        require(amounts.keys.containsAll(activePlayers.map { it.id })) {
            "请为在桌玩家填写退还"
        }

        var nextPlayers = players
        var nextRecords = records
        val now = System.currentTimeMillis()

        players.forEachIndexed { index, player ->
            val amount = if (player.status == PlayerStatus.EXITED) {
                0
            } else {
                amounts[player.id] ?: 0
            }
            require(amount >= 0) { "${player.name} 的退还不能为负" }
            val updated = if (amount > 0) player.withReturn(amount) else player
            nextPlayers = nextPlayers.toMutableList().also { it[index] = updated }
            nextRecords = nextRecords + recordOf(
                player = updated,
                action = ChipAction.FINAL_RETURN,
                amount = amount,
                timestamp = now + index
            )
        }

        return copy(
            players = nextPlayers,
            records = nextRecords,
            phase = SessionPhase.SETTLED
        )
    }

    fun exitPlayer(playerId: String): GameSession =
        mutatePlayer(playerId, allowExited = false) { player ->
            val updated = player.exitTable()
            updated to recordOf(updated, ChipAction.EXIT, 0)
        }

    private fun recordOf(
        player: Player,
        action: ChipAction,
        amount: Int,
        timestamp: Long = System.currentTimeMillis()
    ): ChipRecord = ChipRecord(
        playerId = player.id,
        playerName = player.name,
        action = action,
        amount = amount,
        takenAfter = player.totalTaken,
        returnedAfter = player.totalReturned,
        timestamp = timestamp
    )

    private fun mutatePlayer(
        playerId: String,
        allowExited: Boolean,
        transform: (Player) -> Pair<Player, ChipRecord>
    ): GameSession {
        require(phase == SessionPhase.PLAYING) { "仅对局中可操作" }
        var record: ChipRecord? = null
        val updatedPlayers = players.map { player ->
            if (player.id != playerId) player
            else {
                if (player.status == PlayerStatus.EXITED && !allowExited) {
                    error("已退出玩家不能再拿取或再次退出")
                }
                val (next, nextRecord) = transform(player)
                record = nextRecord
                next
            }
        }
        val appended = record ?: error("未找到玩家")
        return copy(players = updatedPlayers, records = records + appended)
    }
}

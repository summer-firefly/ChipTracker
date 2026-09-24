package com.chiptrack.app.llm

import com.chiptrack.app.model.ChipAction
import com.chiptrack.app.model.GameSession
import com.chiptrack.app.model.PlayerStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GameReportGenerator {
    private const val MAX_RECORDS = 100

    private val systemPrompt = """
你是「猫和老鼠计分器」的轻松牌局复盘助手。根据提供的德州扑克本地计分数据，用中文写一份本局总结报告。

要求：
1. 先写 2～4 句总评（气氛、波动、是否胶着）。
2. 再按盈亏从高到低，每人 1～3 句：净拿取/盈亏、流水里能看出的节奏（猛拿、频繁进出、中途离桌等），可用调侃口吻点评是否像「上头」，但不要人身攻击、不要骂人。
3. 最后给一句轻松收束。
4. 只用给定数据推断，不要编造未出现的牌局细节。
5. 纯文本，可用换行与「·」「-」分点，不要 Markdown 代码块。
    """.trimIndent()

    fun generate(session: GameSession, client: LlmClient = LlmClient()): String {
        val user = buildUserPrompt(session)
        return client.chat(systemPrompt, user)
    }

    fun buildUserPrompt(session: GameSession): String {
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val ranked = session.players.sortedByDescending { it.profit }
        val playerBlock = ranked.mapIndexed { index, p ->
            val status = if (p.status == PlayerStatus.EXITED) "已离桌" else "在桌至终局"
            "${index + 1}. ${p.name}｜拿取 ${p.totalTaken}｜退还 ${p.totalReturned}｜盈亏 ${signed(p.profit)}｜净拿取 ${p.netTaken}｜$status"
        }.joinToString("\n")

        val records = session.records.sortedBy { it.timestamp }.takeLast(MAX_RECORDS)
        val recordBlock = records.joinToString("\n") { r ->
            val t = timeFmt.format(Date(r.timestamp))
            "$t｜${r.playerName}｜${actionLabel(r.action)}｜${r.amount}｜之后拿取=${r.takenAfter} 退还=${r.returnedAfter}"
        }

        return """
本局结算数据：
- 默认初始积分：${session.defaultBuyIn}
- 拿取合计：${session.totalTakenSum}
- 退还合计：${session.totalReturnedSum}
- 盈亏合计：${signed(session.totalProfit)}
- 对账差额（退还-拿取）：${session.reconcileGap}（0=账平，>0多退，<0少退）

玩家（按盈亏排序）：
$playerBlock

流水（时间升序，最多 $MAX_RECORDS 条）：
$recordBlock
        """.trimIndent()
    }

    private fun actionLabel(action: ChipAction): String = when (action) {
        ChipAction.JOIN -> "入局拿取"
        ChipAction.TAKE -> "拿取"
        ChipAction.RETURN -> "退还"
        ChipAction.FINAL_RETURN -> "终局退还"
        ChipAction.EXIT -> "离桌"
    }

    private fun signed(value: Int): String = if (value > 0) "+$value" else "$value"
}

package com.chiptrack.app.ui

import android.content.Context
import com.chiptrack.app.R
import com.chiptrack.app.model.ChipAction
import com.chiptrack.app.model.ChipRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordFormat {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun actionLabel(context: Context, action: ChipAction): String {
        val res = when (action) {
            ChipAction.JOIN -> R.string.record_action_join
            ChipAction.TAKE -> R.string.record_action_take
            ChipAction.RETURN -> R.string.record_action_return
            ChipAction.FINAL_RETURN -> R.string.record_action_final_return
            ChipAction.EXIT -> R.string.record_action_exit
        }
        return context.getString(res)
    }

    fun amountText(record: ChipRecord): String = when (record.action) {
        ChipAction.JOIN, ChipAction.TAKE -> "+${record.amount}"
        ChipAction.RETURN, ChipAction.FINAL_RETURN -> record.amount.toString()
        ChipAction.EXIT -> "—"
    }

    fun amountColor(context: Context, record: ChipRecord): Int {
        val profitLike = when (record.action) {
            ChipAction.TAKE -> -1
            ChipAction.RETURN, ChipAction.FINAL_RETURN -> 1
            ChipAction.JOIN, ChipAction.EXIT -> 0
        }
        return ProfitFormat.color(context, profitLike)
    }

    fun timeText(timestamp: Long): String = timeFormat.format(Date(timestamp))

    fun detailText(context: Context, record: ChipRecord): String {
        return context.getString(
            R.string.record_detail,
            record.takenAfter,
            record.returnedAfter
        )
    }
}

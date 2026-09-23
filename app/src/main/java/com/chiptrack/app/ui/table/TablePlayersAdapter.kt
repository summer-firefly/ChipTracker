package com.chiptrack.app.ui.table

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chiptrack.app.R
import com.chiptrack.app.databinding.ItemPlayerTableBinding
import com.chiptrack.app.databinding.ItemSectionHeaderBinding
import com.chiptrack.app.model.Player
import com.chiptrack.app.model.PlayerStatus
import com.chiptrack.app.ui.ProfitFormat

sealed class TableRow {
    data class Header(val titleRes: Int) : TableRow()
    data class PlayerRow(val player: Player) : TableRow()
}

class TablePlayersAdapter(
    private val onTake: (Player) -> Unit,
    private val onReturn: (Player) -> Unit,
    private val onExit: (Player) -> Unit,
    private val onHistory: (Player) -> Unit
) : ListAdapter<TableRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TableRow.Header -> TYPE_HEADER
        is TableRow.PlayerRow -> TYPE_PLAYER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemSectionHeaderBinding.inflate(inflater, parent, false))
        } else {
            PlayerVH(ItemPlayerTableBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TableRow.Header -> (holder as HeaderVH).bind(item)
            is TableRow.PlayerRow -> (holder as PlayerVH).bind(item.player)
        }
    }

    inner class HeaderVH(private val binding: ItemSectionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TableRow.Header) {
            binding.sectionTitle.setText(item.titleRes)
        }
    }

    inner class PlayerVH(private val binding: ItemPlayerTableBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(player: Player) {
            val ctx = binding.root.context
            binding.nameText.text = player.name
            binding.takenText.text = ctx.getString(R.string.label_taken, player.totalTaken)
            binding.returnedText.text = ctx.getString(R.string.label_returned, player.totalReturned)
            binding.profitText.text = ProfitFormat.netTakenText(player.netTaken)
            binding.profitText.setTextColor(ProfitFormat.netTakenColor(ctx, player.netTaken))

            val active = player.status == PlayerStatus.ACTIVE
            binding.actionsContainer.visibility = View.VISIBLE
            binding.buyInButton.visibility = if (active) View.VISIBLE else View.GONE
            binding.exitButton.visibility = if (active) View.VISIBLE else View.GONE
            // 已退出仍可「退还」手上剩余积分
            binding.cashOutButton.visibility = View.VISIBLE
            binding.exitedBadge.visibility = if (active) View.GONE else View.VISIBLE
            binding.root.alpha = if (active) 1f else 0.78f

            binding.buyInButton.setOnClickListener { onTake(player) }
            binding.cashOutButton.setOnClickListener { onReturn(player) }
            binding.exitButton.setOnClickListener { onExit(player) }
            binding.historyButton.setOnClickListener { onHistory(player) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PLAYER = 1

        private val Diff = object : DiffUtil.ItemCallback<TableRow>() {
            override fun areItemsTheSame(oldItem: TableRow, newItem: TableRow): Boolean =
                when {
                    oldItem is TableRow.Header && newItem is TableRow.Header ->
                        oldItem.titleRes == newItem.titleRes
                    oldItem is TableRow.PlayerRow && newItem is TableRow.PlayerRow ->
                        oldItem.player.id == newItem.player.id
                    else -> false
                }

            override fun areContentsTheSame(oldItem: TableRow, newItem: TableRow): Boolean =
                oldItem == newItem
        }
    }
}

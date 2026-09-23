package com.chiptrack.app.ui.settle

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chiptrack.app.R
import com.chiptrack.app.databinding.ItemPlayerSettleBinding
import com.chiptrack.app.model.Player
import com.chiptrack.app.ui.ProfitFormat

class SettlePlayersAdapter(
    private val onClick: (Player) -> Unit
) : ListAdapter<Player, SettlePlayersAdapter.VH>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPlayerSettleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemPlayerSettleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(player: Player) {
            val ctx = binding.root.context
            binding.nameText.text = player.name
            binding.detailText.text = buildString {
                append(ctx.getString(R.string.label_settle_taken, player.totalTaken))
                append(" · ")
                append(ctx.getString(R.string.label_settle_returned, player.totalReturned))
                append(" · ")
                append(ctx.getString(R.string.action_player_history))
            }
            binding.profitText.text = ProfitFormat.text(ctx, player.profit)
            binding.profitText.setTextColor(ProfitFormat.color(ctx, player.profit))
            binding.root.setOnClickListener { onClick(player) }
        }
    }

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<Player>() {
            override fun areItemsTheSame(oldItem: Player, newItem: Player) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Player, newItem: Player) = oldItem == newItem
        }
    }
}

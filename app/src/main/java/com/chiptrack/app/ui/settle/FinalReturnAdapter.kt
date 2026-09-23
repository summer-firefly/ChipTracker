package com.chiptrack.app.ui.settle

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chiptrack.app.R
import com.chiptrack.app.databinding.ItemFinalReturnBinding
import com.chiptrack.app.model.Player

class FinalReturnAdapter : ListAdapter<Player, FinalReturnAdapter.VH>(Diff) {
    private val drafts = mutableMapOf<String, String>()

    fun draftOf(playerId: String): String = drafts[playerId] ?: "0"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFinalReturnBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemFinalReturnBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var boundId: String? = null

        init {
            binding.amountInput.doAfterTextChanged { text ->
                val id = boundId ?: return@doAfterTextChanged
                drafts[id] = text?.toString().orEmpty()
            }
        }

        fun bind(player: Player) {
            val ctx = binding.root.context
            boundId = player.id
            binding.nameText.text = player.name
            binding.statsText.text = buildString {
                append(ctx.getString(R.string.final_return_taken, player.totalTaken))
                append(" · ")
                append(ctx.getString(R.string.final_return_already, player.totalReturned))
            }
            val draft = draftOf(player.id)
            if (binding.amountInput.text?.toString() != draft) {
                binding.amountInput.setText(draft)
                binding.amountInput.setSelection(draft.length.coerceAtMost(draft.length))
            }
        }
    }

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<Player>() {
            override fun areItemsTheSame(oldItem: Player, newItem: Player) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Player, newItem: Player) = oldItem == newItem
        }
    }
}

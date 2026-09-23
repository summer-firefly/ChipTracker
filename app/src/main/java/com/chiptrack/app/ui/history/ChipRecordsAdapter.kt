package com.chiptrack.app.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chiptrack.app.databinding.ItemChipRecordBinding
import com.chiptrack.app.model.ChipRecord
import com.chiptrack.app.ui.RecordFormat

class ChipRecordsAdapter(
    private val showPlayerName: Boolean
) : ListAdapter<ChipRecord, ChipRecordsAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChipRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemChipRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(record: ChipRecord) {
            val ctx = binding.root.context
            binding.timeText.text = RecordFormat.timeText(record.timestamp)
            binding.playerNameText.visibility = if (showPlayerName) View.VISIBLE else View.GONE
            binding.playerNameText.text = record.playerName
            binding.actionText.text = RecordFormat.actionLabel(ctx, record.action)
            binding.amountText.text = RecordFormat.amountText(record)
            binding.amountText.setTextColor(RecordFormat.amountColor(ctx, record))
            binding.detailText.text = RecordFormat.detailText(ctx, record)
        }
    }

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<ChipRecord>() {
            override fun areItemsTheSame(oldItem: ChipRecord, newItem: ChipRecord) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChipRecord, newItem: ChipRecord) =
                oldItem == newItem
        }
    }
}

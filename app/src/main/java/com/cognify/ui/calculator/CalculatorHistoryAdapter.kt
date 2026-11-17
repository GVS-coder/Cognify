package com.cognify.ui.calculator

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cognify.data.local.CalculatorHistoryEntity
import com.cognify.databinding.ItemCalculatorHistoryBinding

class CalculatorHistoryAdapter(
    private val onItemClick: (CalculatorHistoryEntity) -> Unit
) : ListAdapter<CalculatorHistoryEntity, CalculatorHistoryAdapter.HistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemCalculatorHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class HistoryViewHolder(private val binding: ItemCalculatorHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CalculatorHistoryEntity) {
            binding.tvHistoryExpression.text = item.expression
            binding.tvHistoryResult.text = item.result
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<CalculatorHistoryEntity>() {
            override fun areItemsTheSame(
                oldItem: CalculatorHistoryEntity,
                newItem: CalculatorHistoryEntity
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: CalculatorHistoryEntity,
                newItem: CalculatorHistoryEntity
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}

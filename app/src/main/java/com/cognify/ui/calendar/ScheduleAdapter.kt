package com.cognify.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cognify.R
import com.cognify.databinding.ItemScheduleBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScheduleAdapter(
    private val onItemClick: (ScheduleItem) -> Unit,
    private val onItemDelete: ((ScheduleItem) -> Unit)? = null
) : ListAdapter<ScheduleItem, ScheduleAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ScheduleItem>() {
            override fun areItemsTheSame(old: ScheduleItem, new: ScheduleItem) = old.id == new.id
            override fun areContentsTheSame(old: ScheduleItem, new: ScheduleItem) = old == new
        }
        private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    inner class ViewHolder(private val binding: ItemScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScheduleItem) {
            binding.tvScheduleTitle.text = item.title
            binding.tvScheduleSubtitle.text = item.subtitle
            binding.tvScheduleTime.text = TIME_FORMAT.format(Date(item.timeInMillis))

            // Strike through if completed
            if (item.isCompleted) {
                binding.tvScheduleTitle.paintFlags = binding.tvScheduleTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.tvScheduleTitle.paintFlags = binding.tvScheduleTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            // Set appropriate schedule icon and tint
            val (iconRes, tintColor) = when (item.type) {
                ScheduleItemType.TASK_DEADLINE -> Pair(R.drawable.ic_pin, R.color.color_accent)
                ScheduleItemType.GENERAL_REMINDER -> Pair(R.drawable.ic_alarm, R.color.color_primary)
                ScheduleItemType.BIRTHDAY -> Pair(R.drawable.ic_cake, R.color.color_error)
                ScheduleItemType.EVENT -> Pair(R.drawable.ic_event, R.color.color_secondary)
            }

            binding.ivScheduleIcon.setImageResource(iconRes)
            binding.ivScheduleIcon.imageTintList = ContextCompat.getColorStateList(
                binding.root.context, tintColor
            )

            binding.btnScheduleEdit.setOnClickListener { onItemClick(item) }
            binding.btnScheduleDelete.setOnClickListener { onItemDelete?.invoke(item) }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScheduleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

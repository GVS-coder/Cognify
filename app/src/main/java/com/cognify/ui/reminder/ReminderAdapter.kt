package com.cognify.ui.reminder

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cognify.R
import com.cognify.data.local.ReminderEntity
import com.cognify.data.model.ReminderPriority
import com.cognify.data.model.ReminderType
import com.cognify.data.model.RepeatInterval
import com.cognify.databinding.ItemReminderBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderAdapter(
    private val onItemClick: (ReminderEntity) -> Unit,
    private val onCheckChanged: (ReminderEntity, Boolean) -> Unit
) : ListAdapter<ReminderEntity, ReminderAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ReminderEntity>() {
            override fun areItemsTheSame(old: ReminderEntity, new: ReminderEntity) = old.id == new.id
            override fun areContentsTheSame(old: ReminderEntity, new: ReminderEntity) = old == new
        }
        private val DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
    }

    inner class ViewHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReminderEntity) {
            binding.tvTitle.text = item.title
            binding.tvDescription.text = item.description
            binding.tvDescription.visibility = if (item.description.isBlank()) View.GONE else View.VISIBLE
            binding.tvTime.text = DATE_FORMAT.format(Date(item.dateTime))

            // Checkbox state
            binding.cbCompleted.setOnCheckedChangeListener(null)
            binding.cbCompleted.isChecked = item.status == com.cognify.data.model.ReminderStatus.COMPLETED
            binding.cbCompleted.setOnCheckedChangeListener { _, isChecked ->
                onCheckChanged(item, isChecked)
            }

            // Priority Indicator tint
            val priorityColor = when (item.priority) {
                ReminderPriority.HIGH -> R.color.color_error
                ReminderPriority.MEDIUM -> R.color.color_warning
                ReminderPriority.LOW -> R.color.color_success
            }
            binding.viewPriorityIndicator.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, priorityColor)
            )

            // Repeat type
            if (item.repeatInterval != RepeatInterval.NONE) {
                binding.tvRepeat.visibility = View.VISIBLE
                binding.tvRepeat.text = item.repeatInterval.displayName
                binding.tvDivider.visibility = View.VISIBLE
            } else {
                binding.tvRepeat.visibility = View.GONE
                binding.tvDivider.visibility = View.GONE
            }

            // Type icon indicator
            val typeIcon = when (item.type) {
                ReminderType.GENERAL -> R.drawable.ic_alarm
                ReminderType.TASK_DEADLINE -> R.drawable.ic_pin
                ReminderType.BIRTHDAY -> R.drawable.ic_cake
                ReminderType.EVENT -> R.drawable.ic_event
            }
            binding.ivTypeIcon.setImageResource(typeIcon)

            // Linked item label
            if (!item.linkedItemId.isNullOrBlank() && item.linkedItemType != com.cognify.data.model.LinkedItemType.NONE) {
                binding.tvLinkedItem.visibility = View.VISIBLE
                binding.tvLinkedItem.text = itemView.context.getString(R.string.reminder_linked, item.linkedItemType.name)
            } else {
                binding.tvLinkedItem.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReminderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

package com.cognify.ui.clock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cognify.R
import com.cognify.data.local.ReminderEntity
import com.cognify.data.model.ReminderStatus
import com.cognify.data.model.RepeatInterval
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmAdapter(
    private val onCheckChanged: (ReminderEntity, Boolean) -> Unit,
    private val onDelete: (ReminderEntity) -> Unit,
    private val onClick: (ReminderEntity) -> Unit
) : ListAdapter<ReminderEntity, AlarmAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ReminderEntity>() {
            override fun areItemsTheSame(oldItem: ReminderEntity, newItem: ReminderEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ReminderEntity, newItem: ReminderEntity) = oldItem == newItem
        }
        private val TIME_FORMAT = SimpleDateFormat("hh:mm a", Locale.getDefault())
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAlarmTime: TextView = itemView.findViewById(R.id.tvAlarmTime)
        private val tvAlarmRemaining: TextView = itemView.findViewById(R.id.tvAlarmRemaining)
        private val tvAlarmLabel: TextView = itemView.findViewById(R.id.tvAlarmLabel)
        private val switchAlarm: MaterialSwitch = itemView.findViewById(R.id.switchAlarm)
        private val btnDeleteAlarm: ImageButton = itemView.findViewById(R.id.btnDeleteAlarm)

        fun bind(item: ReminderEntity) {
            tvAlarmTime.text = TIME_FORMAT.format(Date(item.dateTime))

            val repeatLabel = if (item.repeatInterval != RepeatInterval.NONE) {
                item.repeatInterval.displayName
            } else {
                "Once"
            }

            tvAlarmLabel.text = itemView.context.getString(R.string.alarm_item_subtitle, repeatLabel)

            switchAlarm.setOnCheckedChangeListener(null)
            switchAlarm.isChecked = (item.status == ReminderStatus.SCHEDULED || item.status == ReminderStatus.PENDING)
            
            switchAlarm.setOnCheckedChangeListener { _, isChecked ->
                onCheckChanged(item, isChecked)
            }

            btnDeleteAlarm.setOnClickListener {
                onDelete(item)
            }
            
            itemView.setOnClickListener {
                onClick(item)
            }
            
            updateRemainingTime(item)
        }
        
        fun updateRemainingTime(item: ReminderEntity) {
            if (item.status != ReminderStatus.SCHEDULED && item.status != ReminderStatus.PENDING) {
                tvAlarmRemaining.visibility = View.GONE
                return
            }
            
            val remainingText = getRemainingTimeText(item.dateTime, item.repeatInterval)
            if (remainingText.isEmpty()) {
                tvAlarmRemaining.visibility = View.GONE
            } else {
                tvAlarmRemaining.visibility = View.VISIBLE
                tvAlarmRemaining.text = remainingText
            }
        }
        
        private fun getRemainingTimeText(alarmTimeMillis: Long, repeatInterval: RepeatInterval): String {
            val now = System.currentTimeMillis()
            var target = alarmTimeMillis
            if (target <= now && repeatInterval != RepeatInterval.NONE) {
                val calendar = java.util.Calendar.getInstance()
                calendar.timeInMillis = target
                val targetHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val targetMin = calendar.get(java.util.Calendar.MINUTE)
                
                calendar.timeInMillis = now
                calendar.set(java.util.Calendar.HOUR_OF_DAY, targetHour)
                calendar.set(java.util.Calendar.MINUTE, targetMin)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                
                if (calendar.timeInMillis <= now) {
                     calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                target = calendar.timeInMillis
            } else if (target <= now) {
                return ""
            }
            
            val diff = target - now
            if (diff < 0) return "" 
            
            val seconds = diff / 1000
            if (seconds < 60) {
                return "$seconds seconds left"
            }
            
            val totalMinutes = seconds / 60
            if (totalMinutes < 60) {
                return "$totalMinutes minutes left"
            }
            
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            if (hours < 24) {
                return if (minutes > 0) "$hours hours $minutes minutes left" else "$hours hours left"
            }
            
            val days = hours / 24
            val remainingHours = hours % 24
            return if (remainingHours > 0) {
                "$days day${if (days > 1L) "s" else ""} $remainingHours hours left"
            } else {
                "$days day${if (days > 1L) "s" else ""} left"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("TICK")) {
            holder.updateRemainingTime(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
}

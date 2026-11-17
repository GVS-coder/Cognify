package com.cognify.ui.task

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cognify.R
import com.cognify.data.local.TaskEntity
import com.cognify.data.model.TaskPriority
import com.cognify.data.model.TaskStatus
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private val onTaskClick: (TaskEntity) -> Unit,
    private val onTaskChecked: (TaskEntity, Boolean) -> Unit,
    private val onTaskDelete: ((TaskEntity) -> Unit)? = null
) : ListAdapter<TaskEntity, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task)
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvTaskCategory)
        private val chipPriority: com.google.android.material.chip.Chip = itemView.findViewById(R.id.chipPriority)
        private val btnActionComplete: com.google.android.material.button.MaterialButton? = itemView.findViewById(R.id.btnActionComplete)
        private val btnActionEdit: View? = itemView.findViewById(R.id.btnActionEdit)
        private val btnActionDelete: View? = itemView.findViewById(R.id.btnActionDelete)

        fun bind(task: TaskEntity) {
            tvTitle.text = task.title
            
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            val dateStr = task.dueDate?.let { dateFormat.format(Date(it)) } ?: "No Date"
            tvCategory.text = itemView.context.getString(R.string.task_subtitle, task.category, dateStr)

            chipPriority.text = task.priority.name

            val isCompleted = task.status == TaskStatus.COMPLETED

            btnActionComplete?.let { btn ->
                if (isCompleted) {
                    btn.setIconResource(com.cognify.R.drawable.ic_check_circle)
                    btn.contentDescription = "Mark task as pending"
                } else {
                    btn.setIconResource(com.cognify.R.drawable.ic_radio_button_unchecked)
                    btn.contentDescription = "Mark task as completed"
                }
                btn.setOnClickListener {
                    onTaskChecked(task, !isCompleted)
                }
            }

            btnActionEdit?.setOnClickListener {
                onTaskClick(task)
            }

            btnActionDelete?.setOnClickListener {
                onTaskDelete?.invoke(task)
            }


        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<TaskEntity>() {
        override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem == newItem
        }
    }
}

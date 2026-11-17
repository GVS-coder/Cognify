package com.cognify.ui.analytics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cognify.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimelineAdapter : ListAdapter<TimelineItem, TimelineAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timeline, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        if (item.titleRes != null) holder.tvTitle.setText(item.titleRes) else holder.tvTitle.text = item.title
        if (item.descRes != null) holder.tvDescription.setText(item.descRes) else holder.tvDescription.text = item.description
        
        // Use a default icon or the one provided by item
        holder.ivIcon.setImageResource(item.typeIconRes)
        
        val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        holder.tvTime.text = format.format(Date(item.timestamp))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<TimelineItem>() {
        override fun areItemsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
            return oldItem == newItem
        }
    }
}

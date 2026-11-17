package com.cognify.ui.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cognify.R
import com.cognify.data.local.ConversationEntity

class ConversationsAdapter : ListAdapter<ConversationEntity, ConversationsAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    var onConversationClick: ((String) -> Unit)? = null
    var onDeleteClick: ((String) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bind(conversation, onConversationClick, onDeleteClick)
    }

    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)

        fun bind(
            conversation: ConversationEntity,
            onConversationClick: ((String) -> Unit)?,
            onDeleteClick: ((String) -> Unit)?
        ) {
            tvTitle.text = conversation.title
            val format = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            tvDate.text = format.format(java.util.Date(conversation.updatedAt))

            itemView.setOnClickListener { onConversationClick?.invoke(conversation.id) }
            btnDelete.setOnClickListener { onDeleteClick?.invoke(conversation.id) }
        }
    }

    class ConversationDiffCallback : DiffUtil.ItemCallback<ConversationEntity>() {
        override fun areItemsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean {
            return oldItem == newItem
        }
    }
}

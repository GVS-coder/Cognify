package com.cognify.ui.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cognify.R
import com.cognify.data.model.ChatMessage
import com.cognify.data.model.Role

class AIChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    var onCopyClick: ((String) -> Unit)? = null
    var onShareClick: ((String) -> Unit)? = null
    var onSaveClick: ((String) -> Unit)? = null
    var onRegenerateClick: (() -> Unit)? = null

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).role == Role.USER) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_ai, parent, false)
            AiViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is UserViewHolder) {
            holder.bind(message)
        } else if (holder is AiViewHolder) {
            holder.bind(message, onCopyClick, onShareClick, onSaveClick, onRegenerateClick)
        }
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvUserMessage: TextView = itemView.findViewById(R.id.tvUserMessage)
        private val llDocumentAttachment: View = itemView.findViewById(R.id.llDocumentAttachment)
        private val tvDocFileName: TextView = itemView.findViewById(R.id.tvDocFileName)
        private val tvDocStatus: TextView = itemView.findViewById(R.id.tvDocStatus)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())

        fun bind(message: ChatMessage) {
            tvTimestamp.text = timeFormat.format(java.util.Date(message.timestamp))
            if (message.documentAttachment != null) {
                llDocumentAttachment.visibility = View.VISIBLE
                tvUserMessage.visibility = View.GONE
                tvDocFileName.text = message.documentAttachment.fileName
                tvDocStatus.text = message.documentAttachment.processingStatus
            } else {
                llDocumentAttachment.visibility = View.GONE
                tvUserMessage.visibility = View.VISIBLE
                tvUserMessage.text = message.content
            }
        }
    }

    class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cvAiContainer: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.cvAiContainer)
        private val tvAiMessage: TextView = itemView.findViewById(R.id.tvAiMessage)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val btnCopy: View = itemView.findViewById(R.id.btnCopy)
        private val btnShare: View = itemView.findViewById(R.id.btnShare)
        private val btnSave: View = itemView.findViewById(R.id.btnSave)
        private val btnRegenerate: View = itemView.findViewById(R.id.btnRegenerate)
        private val btnRetry: View = itemView.findViewById(R.id.btnRetry)
        private val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())

        fun bind(
            message: ChatMessage,
            onCopyClick: ((String) -> Unit)?,
            onShareClick: ((String) -> Unit)?,
            onSaveClick: ((String) -> Unit)?,
            onRegenerateClick: (() -> Unit)?
        ) {
            val markwon = io.noties.markwon.Markwon.create(itemView.context)
            tvTimestamp.text = timeFormat.format(java.util.Date(message.timestamp))
            if (message.role == Role.ERROR) {
                cvAiContainer.setCardBackgroundColor(itemView.resources.getColor(com.google.android.material.R.color.material_dynamic_primary70, null))
                tvAiMessage.setTextColor(itemView.resources.getColor(com.google.android.material.R.color.design_default_color_error, null))
                tvAiMessage.text = "⚠ ${message.content}"
                
                btnCopy.visibility = View.GONE
                btnShare.visibility = View.GONE
                btnSave.visibility = View.GONE
                btnRegenerate.visibility = View.GONE
                btnRetry.visibility = View.VISIBLE
            } else {
                cvAiContainer.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurfaceVariant))
                tvAiMessage.setTextColor(com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurface))
                markwon.setMarkdown(tvAiMessage, message.content)
                
                btnCopy.visibility = View.VISIBLE
                btnShare.visibility = View.VISIBLE
                btnSave.visibility = View.VISIBLE
                btnRegenerate.visibility = View.VISIBLE
                btnRetry.visibility = View.GONE
            }

            btnCopy.setOnClickListener { onCopyClick?.invoke(message.content) }
            btnShare.setOnClickListener { onShareClick?.invoke(message.content) }
            btnSave.setOnClickListener { onSaveClick?.invoke(message.content) }
            btnRegenerate.setOnClickListener { onRegenerateClick?.invoke() }
            btnRetry.setOnClickListener { onRegenerateClick?.invoke() }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}

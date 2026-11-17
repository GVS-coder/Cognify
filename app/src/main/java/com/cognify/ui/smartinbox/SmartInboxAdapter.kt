package com.cognify.ui.smartinbox

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cognify.R
import com.cognify.data.model.GmailMessage
import com.cognify.databinding.ItemSmartInboxEmailBinding
import com.google.android.material.chip.Chip

class SmartInboxAdapter(
    private val onItemClick: (GmailMessage) -> Unit
) : ListAdapter<GmailMessage, SmartInboxAdapter.ViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSmartInboxEmailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSmartInboxEmailBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            binding.btnOpenInGmail.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val message = getItem(position)
                    val context = binding.root.context
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                        intent.data = android.net.Uri.parse("intent://mail.google.com/mail/u/0/#inbox/${message.id}#Intent;scheme=https;package=com.google.android.gm;end")
                        context.startActivity(intent)
                    } catch (e: android.content.ActivityNotFoundException) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.data = android.net.Uri.parse("https://mail.google.com/mail/u/0/#inbox/${message.id}")
                            context.startActivity(intent)
                        } catch (ex: Exception) {
                            android.widget.Toast.makeText(context, context.getString(R.string.smart_inbox_cant_open), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, context.getString(R.string.smart_inbox_cant_open), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        fun bind(message: GmailMessage) {
            binding.tvSender.text = message.senderName
            binding.tvSubject.text = message.subject
            binding.tvSnippet.text = message.snippet

            // Format date (e.g., "Oct 20" or "10:30 AM")
            binding.tvDate.text = DateUtils.getRelativeTimeSpanString(
                message.receivedAt,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )

            // Read/unread styling
            if (message.isRead) {
                binding.tvSender.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Body1)
                binding.tvSubject.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Body1)
                binding.vUnreadIndicator.visibility = View.GONE
            } else {
                binding.tvSender.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Body2)
                binding.tvSubject.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Body2)
                binding.vUnreadIndicator.visibility = View.VISIBLE
            }

            // Categories
            binding.cgCategories.removeAllViews()
            message.matchedCategories.forEach { category ->
                val chip = Chip(binding.root.context).apply {
                    text = com.cognify.util.LocalizationUtils.getCategoryName(binding.root.context, category)
                    textSize = 12f
                    isClickable = false
                    isCheckable = false
                    // Default chip minimum height is usually fine
                }
                binding.cgCategories.addView(chip)
            }
            binding.cgCategories.visibility = if (message.matchedCategories.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<GmailMessage>() {
        override fun areItemsTheSame(oldItem: GmailMessage, newItem: GmailMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GmailMessage, newItem: GmailMessage): Boolean {
            return oldItem == newItem
        }
    }
}

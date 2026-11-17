package com.cognify.ui.knowledge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.model.Difficulty
import com.cognify.data.model.ProgrammingLanguage
import com.cognify.databinding.ItemKnowledgeBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KnowledgeAdapter(
    private val onItemClick: (KnowledgeEntity) -> Unit,
    private val onDeleteClick: (KnowledgeEntity) -> Unit
) : ListAdapter<KnowledgeEntity, KnowledgeAdapter.ViewHolder>(DiffCallback) {

    private var currentQuery: String = ""

    fun submitListAndQuery(list: List<KnowledgeEntity>?, query: String) {
        currentQuery = query
        submitList(list)
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<KnowledgeEntity>() {
            override fun areItemsTheSame(old: KnowledgeEntity, new: KnowledgeEntity) = old.id == new.id
            override fun areContentsTheSame(old: KnowledgeEntity, new: KnowledgeEntity) = old == new
        }
        private val DATE_FORMAT = SimpleDateFormat("MMM dd", Locale.getDefault())
    }

    inner class ViewHolder(private val binding: ItemKnowledgeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private fun highlightText(text: String, query: String): CharSequence {
            if (query.isBlank()) return text
            val spannable = android.text.SpannableString(text)
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()
            var start = lowerText.indexOf(lowerQuery)
            while (start >= 0) {
                val end = start + query.length
                val color = binding.root.context.getColor(com.cognify.R.color.color_primary_variant)
                spannable.setSpan(android.text.style.BackgroundColorSpan(color), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                start = lowerText.indexOf(lowerQuery, end)
            }
            return spannable
        }

        fun bind(item: KnowledgeEntity) {
            binding.tvTitle.text = highlightText(item.title, currentQuery)
            if (item.isPrivate) {
                binding.tvDescription.visibility = View.GONE
                binding.tvDescription.text = null
            } else {
                binding.tvDescription.visibility = View.VISIBLE
                binding.tvDescription.text = highlightText(item.description, currentQuery)
            }
            binding.chipType.text = item.type.name
            binding.tvDate.text = DATE_FORMAT.format(Date(item.updatedAt))
            binding.ivFavorite.setImageResource(
                if (item.isFavorite) com.cognify.R.drawable.ic_favorite else com.cognify.R.drawable.ic_favorite_border
            )
            binding.ivFavorite.contentDescription = if (item.isFavorite) "Favorited" else "Not favorited"

            binding.ivPinned.setImageResource(
                if (item.pinned) com.cognify.R.drawable.ic_pin else com.cognify.R.drawable.ic_pin_outline
            )
            binding.ivPinned.contentDescription = if (item.pinned) "Pinned" else "Not pinned"

            binding.ivPrivate.setImageResource(
                if (item.isPrivate) com.cognify.R.drawable.ic_lock else com.cognify.R.drawable.ic_lock_open
            )
            binding.ivPrivate.contentDescription = if (item.isPrivate) "Private" else "Public"

            // Code block - must only be visible for public items
            if (!item.isPrivate && !item.codeBlock.isNullOrBlank()) {
                binding.layoutCodeBlock.visibility = View.VISIBLE
                binding.tvCode.text = item.codeBlock

                // Copy action
                binding.btnCopyCode.setOnClickListener {
                    val clipboard = binding.root.context
                        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", item.codeBlock))
                    com.google.android.material.snackbar.Snackbar.make(binding.root, "Code copied", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                }

                // Share action
                binding.btnShareCode.setOnClickListener {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${item.title}\n\n${item.codeBlock}")
                    }
                    binding.root.context.startActivity(
                        Intent.createChooser(intent, "Share code")
                    )
                }
            } else {
                binding.layoutCodeBlock.visibility = View.GONE
                binding.tvCode.text = null
                binding.btnCopyCode.setOnClickListener(null)
                binding.btnShareCode.setOnClickListener(null)
            }

            // Language chip
            if (item.language != ProgrammingLanguage.NONE) {
                binding.chipLanguage.visibility = View.VISIBLE
                binding.chipLanguage.text = item.language.displayName
            } else {
                binding.chipLanguage.visibility = View.GONE
            }

            // Difficulty chip
            if (item.difficulty != Difficulty.NONE) {
                binding.chipDifficulty.visibility = View.VISIBLE
                binding.chipDifficulty.text = item.difficulty.name.lowercase()
                    .replaceFirstChar { it.uppercase() }
            } else {
                binding.chipDifficulty.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener { onDeleteClick(item); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKnowledgeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

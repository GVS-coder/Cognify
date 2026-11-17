package com.cognify.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cognify.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConversationsFragment : Fragment() {

    private val viewModel: ConversationsViewModel by viewModels()
    private lateinit var adapter: ConversationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_conversations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val rvConversations = view.findViewById<RecyclerView>(R.id.rvConversations)
        val layoutEmpty = view.findViewById<View>(R.id.layoutEmpty)
        
        adapter = ConversationsAdapter().apply {
            onConversationClick = { id ->
                val savedStateHandle = findNavController().previousBackStackEntry?.savedStateHandle
                savedStateHandle?.set("selectedConversationId", id)
                findNavController().navigateUp()
            }
            onDeleteClick = { id ->
                viewModel.deleteConversation(id)
            }
        }
        
        rvConversations.layoutManager = LinearLayoutManager(requireContext())
        rvConversations.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.conversations.collect { list ->
                    adapter.submitList(list)
                    if (list.isEmpty()) {
                        layoutEmpty.visibility = View.VISIBLE
                        rvConversations.visibility = View.GONE
                    } else {
                        layoutEmpty.visibility = View.GONE
                        rvConversations.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}

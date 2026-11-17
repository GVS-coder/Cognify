package com.cognify.ui.code

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.cognify.R
import com.cognify.data.model.KnowledgeType
import com.cognify.databinding.FragmentCodeBinding
import com.cognify.ui.knowledge.KnowledgeAdapter
import com.cognify.ui.knowledge.KnowledgeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CodeFragment : Fragment() {

    private var _binding: FragmentCodeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: KnowledgeViewModel by viewModels()
    private lateinit var adapter: KnowledgeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }

        setupRecyclerView()
        setupSearch()
        observeState()

        binding.fabAddCode.setOnClickListener {
            val args = Bundle().apply { putBoolean("isCodeSnippet", true) }
            findNavController().navigate(R.id.action_codeFragment_to_addEditKnowledgeFragment, args)
        }
    }

    private fun setupRecyclerView() {
        adapter = KnowledgeAdapter(
            onItemClick = { item ->
                val args = Bundle().apply {
                    putString("knowledgeId", item.id)
                    putBoolean("isCodeSnippet", true)
                }
                findNavController().navigate(R.id.action_codeFragment_to_addEditKnowledgeFragment, args)
            },
            onDeleteClick = { item ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Code Snippet")
                    .setMessage("Delete \"${item.title}\"?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteKnowledge(item.id) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvCodeSnippets.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CodeFragment.adapter
        }
    }

    private fun setupSearch() {
        binding.etSearchCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is com.cognify.ui.knowledge.KnowledgeUiState.Success -> {
                            val codeItems = state.items.filter {
                                !it.isPrivate && (it.type == KnowledgeType.CODE || it.type == KnowledgeType.SQL || it.type == KnowledgeType.LINUX)
                            }
                            if (codeItems.isEmpty()) {
                                binding.tvEmptyCode.visibility = View.VISIBLE
                                binding.rvCodeSnippets.visibility = View.GONE
                            } else {
                                binding.tvEmptyCode.visibility = View.GONE
                                binding.rvCodeSnippets.visibility = View.VISIBLE
                                adapter.submitListAndQuery(codeItems, state.query)
                            }
                        }
                        is com.cognify.ui.knowledge.KnowledgeUiState.Empty -> {
                            binding.tvEmptyCode.visibility = View.VISIBLE
                            binding.rvCodeSnippets.visibility = View.GONE
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.cognify.ui.smartinbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cognify.R
import com.cognify.data.model.SmartInboxCategory
import com.cognify.databinding.FragmentSmartInboxBinding
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmartInboxFragment : Fragment() {

    private var _binding: FragmentSmartInboxBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SmartInboxViewModel by viewModels()
    private lateinit var adapter: SmartInboxAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSmartInboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupCategoryChips()
        setupCustomSearch()
        setupSwipeRefresh()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        adapter = SmartInboxAdapter { message ->
            val args = android.os.Bundle().apply {
                putString("messageId", message.id)
                putString("threadId", message.threadId)
                putString("subject", message.subject)
                putString("sender", message.senderName)
            }
            findNavController().navigate(com.cognify.R.id.action_smartInboxFragment_to_gmailEmailDetailsFragment, args)
        }
        binding.rvEmails.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEmails.adapter = adapter
    }

    private fun setupCategoryChips() {
        // "All" chip
        val allChip = Chip(requireContext()).apply {
            text = "All"
            isCheckable = true
            isChecked = true
            id = View.generateViewId()
        }
        binding.chipGroupCategories.addView(allChip)
        
        val categoryIds = mutableMapOf<Int, SmartInboxCategory>()
        SmartInboxCategory.entries.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = com.cognify.util.LocalizationUtils.getCategoryName(requireContext(), category)
                isCheckable = true
                id = View.generateViewId()
            }
            binding.chipGroupCategories.addView(chip)
            categoryIds[chip.id] = category
        }
        
        // Restore checked state
        val currentCategory = viewModel.uiState.value.selectedCategory
        if (currentCategory == null) {
            binding.chipGroupCategories.check(allChip.id)
            binding.tilCustomSearch.isVisible = false
        } else {
            val chipIdToSelect = categoryIds.entries.find { it.value == currentCategory }?.key
            if (chipIdToSelect != null) {
                binding.chipGroupCategories.check(chipIdToSelect)
                if (currentCategory == SmartInboxCategory.CUSTOM) {
                    binding.tilCustomSearch.isVisible = true
                    val currentQuery = viewModel.uiState.value.customQuery
                    if (currentQuery != null) {
                        binding.etCustomSearch.setText(currentQuery)
                    }
                }
            } else {
                binding.chipGroupCategories.check(allChip.id)
            }
        }

        binding.chipGroupCategories.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            val selectedId = checkedIds.first()
            if (selectedId == allChip.id) {
                binding.tilCustomSearch.isVisible = false
                viewModel.setCategory(null)
            } else {
                val cat = categoryIds[selectedId]
                if (cat == SmartInboxCategory.CUSTOM) {
                    binding.tilCustomSearch.isVisible = true
                    viewModel.setCategory(cat)
                } else {
                    binding.tilCustomSearch.isVisible = false
                    viewModel.setCategory(cat)
                }
            }
        }
    }

    private fun setupCustomSearch() {
        binding.etCustomSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString()
                if (query.isNotBlank()) {
                    viewModel.setCustomQuery(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadMessages(isRefresh = true)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    // Loading
                    binding.progressBar.isVisible = state.isLoading && !binding.swipeRefresh.isRefreshing
                    if (!state.isLoading) {
                        binding.swipeRefresh.isRefreshing = false
                    }

                    // Error
                    if (state.error != null) {
                        if (state.error.contains("expired", ignoreCase = true) || state.error.contains("denied", ignoreCase = true)) {
                            binding.tvEmpty.text = getString(R.string.smart_inbox_conn_expired)
                        } else if (state.error.contains("Unable to resolve host", ignoreCase = true)) {
                            binding.tvEmpty.text = getString(R.string.smart_inbox_no_internet)
                        } else {
                            binding.tvEmpty.text = state.error
                        }
                        binding.tvEmpty.isVisible = true
                        adapter.submitList(emptyList())
                    } else {
                        // Data
                        adapter.submitList(state.messages)
                        binding.tvEmpty.text = getString(R.string.smart_inbox_no_messages)
                        binding.tvEmpty.isVisible = state.messages.isEmpty() && !state.isLoading
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

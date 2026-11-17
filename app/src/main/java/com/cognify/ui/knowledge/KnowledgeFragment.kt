package com.cognify.ui.knowledge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cognify.R
import com.cognify.data.model.KnowledgeType
import com.cognify.databinding.FragmentKnowledgeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.cognify.util.setupMaterialSharedAxisTransitions

@AndroidEntryPoint
class KnowledgeFragment : Fragment() {

    private var _binding: FragmentKnowledgeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: KnowledgeViewModel by viewModels()
    private lateinit var adapter: KnowledgeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupMaterialSharedAxisTransitions()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKnowledgeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupFilterChips()
        observeState()

        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.action_knowledgeFragment_to_addEditKnowledgeFragment)
        }

        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }

        viewModel.syncFromCloud()
    }

    private fun setupRecyclerView() {
        adapter = KnowledgeAdapter(
            onItemClick = { item ->
                val args = Bundle().apply { putString("knowledgeId", item.id) }
                findNavController().navigate(
                    R.id.action_knowledgeFragment_to_addEditKnowledgeFragment, args
                )
            },
            onDeleteClick = { item ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete")
                    .setMessage("Delete \"${item.title}\"?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteKnowledge(item.id) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvKnowledge.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@KnowledgeFragment.adapter
        }
    }

    private fun setupSearch() {
        binding.searchBar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun setupFilterChips() {
        binding.chipFilterAll.setOnClickListener {
            viewModel.setFilter(null)
            viewModel.setFavoritesOnly(false)
        }
        binding.chipFilterNotes.setOnClickListener { viewModel.setFilter(KnowledgeType.NOTE) }
        binding.chipFilterCode.setOnClickListener { viewModel.setFilter(KnowledgeType.CODE) }
        binding.chipFilterSql.setOnClickListener { viewModel.setFilter(KnowledgeType.SQL) }
        binding.chipFilterLinux.setOnClickListener { viewModel.setFilter(KnowledgeType.LINUX) }
        binding.chipFilterInterview.setOnClickListener { viewModel.setFilter(KnowledgeType.INTERVIEW) }
        binding.chipFilterFavorites.setOnClickListener { viewModel.setFavoritesOnly(true) }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvKnowledge.visibility = View.GONE

                    when (state) {
                        is KnowledgeUiState.Loading -> binding.progressBar.visibility = View.VISIBLE
                        is KnowledgeUiState.Empty -> binding.layoutEmpty.visibility = View.VISIBLE
                        is KnowledgeUiState.Success -> {
                            binding.rvKnowledge.visibility = View.VISIBLE
                            adapter.submitListAndQuery(state.items, state.query)
                        }
                        is KnowledgeUiState.Error -> {
                            binding.layoutEmpty.visibility = View.VISIBLE
                        }
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

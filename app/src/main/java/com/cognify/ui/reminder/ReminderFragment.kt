package com.cognify.ui.reminder

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
import com.cognify.R
import com.cognify.databinding.FragmentReminderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.cognify.util.setupMaterialSharedAxisTransitions

@AndroidEntryPoint
class ReminderFragment : Fragment() {

    private var _binding: FragmentReminderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReminderViewModel by viewModels()
    private lateinit var adapter: ReminderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupMaterialSharedAxisTransitions()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterChips()
        observeState()

        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.action_reminderFragment_to_addEditReminderFragment)
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
        adapter = ReminderAdapter(
            onItemClick = { item ->
                val args = Bundle().apply { putString("reminderId", item.id) }
                findNavController().navigate(
                    R.id.action_reminderFragment_to_addEditReminderFragment, args
                )
            },
            onCheckChanged = { item, isChecked ->
                viewModel.updateReminderStatus(item, isChecked)
            }
        )
        binding.rvReminders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ReminderFragment.adapter
        }
    }

    private fun setupFilterChips() {
        binding.chipFilterAll.setOnClickListener { viewModel.setFilter(ReminderFilter.ALL) }
        binding.chipFilterPending.setOnClickListener { viewModel.setFilter(ReminderFilter.ACTIVE) }
        binding.chipFilterCompleted.setOnClickListener { viewModel.setFilter(ReminderFilter.COMPLETED) }
        binding.chipFilterBirthdays.setOnClickListener { viewModel.setFilter(ReminderFilter.BIRTHDAYS) }
        binding.chipFilterTasks.setOnClickListener { viewModel.setFilter(ReminderFilter.TASKS) }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvReminders.visibility = View.GONE

                    when (state) {
                        is ReminderUiState.Loading -> binding.progressBar.visibility = View.VISIBLE
                        is ReminderUiState.Empty -> binding.layoutEmpty.visibility = View.VISIBLE
                        is ReminderUiState.Success -> {
                            binding.rvReminders.visibility = View.VISIBLE
                            adapter.submitList(state.items)
                        }
                        is ReminderUiState.Error -> {
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

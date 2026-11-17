package com.cognify.ui.task

import com.cognify.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cognify.databinding.FragmentAddEditTaskBinding
import com.cognify.data.model.TaskPriority
import com.cognify.data.model.TaskStatus
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AddEditTaskFragment : Fragment() {

    private var _binding: FragmentAddEditTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditTaskViewModel by viewModels()
    
    private var taskId: String? = null
    private var selectedDueDate: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskId = arguments?.getString("taskId")
        viewModel.loadTask(taskId)

        setupUI()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(com.cognify.R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }

        if (taskId != null) {
            binding.toolbar.title = getString(R.string.ui_edit_task)
            binding.btnDelete.visibility = View.VISIBLE
        } else {
            binding.toolbar.title = getString(R.string.ui_new_task)
            binding.btnDelete.visibility = View.GONE
            
            // Handle prefill arguments for AI Analysis
            arguments?.getString("prefillTitle")?.let { binding.etTitle.setText(it) }
            arguments?.getString("prefillDescription")?.let { binding.etDescription.setText(it) }
            arguments?.getLong("prefillDueDate", -1L)?.let { dateMs ->
                if (dateMs != -1L) {
                    selectedDueDate = dateMs
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    binding.btnSelectDate.text = dateFormat.format(Date(dateMs))
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSave.setOnClickListener {
            saveTask()
        }

        binding.btnDelete.setOnClickListener {
            taskId?.let { id -> viewModel.deleteTask(id) }
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Due Date")
            .setSelection(selectedDueDate ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()
            
        picker.addOnPositiveButtonClickListener { selection ->
            selectedDueDate = selection
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.btnSelectDate.text = dateFormat.format(Date(selection))
        }
        
        picker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun saveTask() {
        val title = binding.etTitle.text.toString()
        val description = binding.etDescription.text.toString()
        val category = binding.etCategory.text.toString().ifBlank { "General" }
        
        if (title.isBlank()) {
            binding.etTitle.error = "Title is required"
            return
        }

        val priority = when (binding.chipGroupPriority.checkedChipId) {
            binding.chipHigh.id -> TaskPriority.HIGH
            binding.chipLow.id -> TaskPriority.LOW
            else -> TaskPriority.MEDIUM
        }

        val status = when (binding.chipGroupStatus.checkedChipId) {
            binding.chipInProgress.id -> TaskStatus.IN_PROGRESS
            binding.chipCompleted.id -> TaskStatus.COMPLETED
            else -> TaskStatus.PENDING
        }

        viewModel.saveTask(taskId, title, description, priority, status, category, selectedDueDate)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.task.collect { task ->
                    if (task != null) {
                        populateTaskDetails(task)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AddEditTaskEvent.SaveSuccess -> {
                            Toast.makeText(requireContext(), "Task saved successfully.", Toast.LENGTH_SHORT).show()
                            findNavController().navigateUp()
                        }
                        is AddEditTaskEvent.DeleteSuccess -> {
                            Toast.makeText(requireContext(), "Task deleted successfully.", Toast.LENGTH_SHORT).show()
                            findNavController().navigateUp()
                        }
                        is AddEditTaskEvent.Error -> {
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun populateTaskDetails(task: com.cognify.data.local.TaskEntity) {
        binding.etTitle.setText(task.title)
        binding.etDescription.setText(task.description)
        binding.etCategory.setText(task.category)
        
        when (task.priority) {
            TaskPriority.HIGH -> binding.chipHigh.isChecked = true
            TaskPriority.LOW -> binding.chipLow.isChecked = true
            else -> binding.chipMedium.isChecked = true
        }

        when (task.status) {
            TaskStatus.IN_PROGRESS -> binding.chipInProgress.isChecked = true
            TaskStatus.COMPLETED -> binding.chipCompleted.isChecked = true
            else -> binding.chipPending.isChecked = true
        }

        if (task.dueDate != null) {
            selectedDueDate = task.dueDate
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.btnSelectDate.text = dateFormat.format(Date(task.dueDate))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

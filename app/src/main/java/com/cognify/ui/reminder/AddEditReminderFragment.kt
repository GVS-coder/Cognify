package com.cognify.ui.reminder

import com.cognify.R
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cognify.data.model.LinkedItemType
import com.cognify.data.model.ReminderPriority
import com.cognify.data.model.ReminderType
import com.cognify.data.model.RepeatInterval
import com.cognify.databinding.FragmentAddEditReminderBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AddEditReminderFragment : Fragment() {

    private var _binding: FragmentAddEditReminderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditReminderViewModel by viewModels()

    private var selectedDateTime = Calendar.getInstance().apply {
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private var selectedType = ReminderType.GENERAL
    private var selectedPriority = ReminderPriority.MEDIUM
    private var selectedRepeat = RepeatInterval.NONE
    private var selectedLinkType = LinkedItemType.NONE

    private val dateTimeFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDropdowns()
        setupDateTimePickers()
        observeState()
        observeEvents()

        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(com.cognify.R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }
        
        val reminderId = arguments?.getString("reminderId")
        if (reminderId == null) {
            arguments?.getString("prefillTitle")?.let { binding.etTitle.setText(it) }
            arguments?.getString("prefillDescription")?.let { binding.etDescription.setText(it) }
            arguments?.getLong("prefillDateTime", -1L)?.let { dateMs ->
                if (dateMs != -1L) {
                    selectedDateTime.timeInMillis = dateMs
                    binding.tvSelectedDateTime.text = getString(R.string.reminder_time) + dateTimeFormat.format(selectedDateTime.time)
                }
            }
        }
        
        binding.fabSave.setOnClickListener { saveReminder() }
    }

    private fun getReminderTypeString(type: ReminderType): String {
        return when (type) {
            ReminderType.GENERAL -> getString(com.cognify.R.string.ui_type_general)
            ReminderType.TASK_DEADLINE -> getString(com.cognify.R.string.ui_type_deadline)
            ReminderType.BIRTHDAY -> getString(com.cognify.R.string.ui_birthdays)
            ReminderType.EVENT -> getString(com.cognify.R.string.ui_type_meeting)
        }
    }
    private fun getReminderPriorityString(priority: ReminderPriority): String {
        return when(priority) {
            ReminderPriority.HIGH -> getString(R.string.ui_priority_high)
            ReminderPriority.MEDIUM -> getString(R.string.ui_priority_medium)
            ReminderPriority.LOW -> getString(R.string.ui_priority_low)
        }
    }

    private fun getRepeatIntervalString(interval: RepeatInterval): String {
        return when (interval) {
            RepeatInterval.NONE -> getString(com.cognify.R.string.ui_repeat_none)
            RepeatInterval.DAILY -> getString(com.cognify.R.string.ui_repeat_daily)
            RepeatInterval.WEEKLY -> getString(com.cognify.R.string.ui_repeat_weekly)
            RepeatInterval.MONTHLY -> getString(com.cognify.R.string.ui_repeat_monthly)
            RepeatInterval.YEARLY -> getString(com.cognify.R.string.ui_repeat_yearly)
        }
    }
    private fun getLinkedItemTypeString(link: LinkedItemType): String {
        return when(link) {
            LinkedItemType.NONE -> getString(com.cognify.R.string.ui_link_none)
            LinkedItemType.TASK -> getString(com.cognify.R.string.ui_link_task)
            LinkedItemType.KNOWLEDGE -> getString(com.cognify.R.string.ui_link_knowledge)
            LinkedItemType.EVENT -> getString(com.cognify.R.string.ui_type_meeting)
        }
    }

    private fun setupDropdowns() {
        // Types
        val types = ReminderType.values().map { getReminderTypeString(it) } + getString(R.string.ui_others)
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        binding.acvType.setAdapter(typeAdapter)
        binding.acvType.setText(getReminderTypeString(ReminderType.GENERAL), false)
        binding.acvType.setOnItemClickListener { _, _, pos, _ ->
            if (types[pos] == getString(R.string.ui_others)) {
                selectedType = ReminderType.GENERAL
            } else {
                selectedType = ReminderType.values()[pos]
            }
        }

        // Priorities
        val priorities = ReminderPriority.values().map { getReminderPriorityString(it) }
        val priorityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, priorities)
        binding.acvPriority.setAdapter(priorityAdapter)
        binding.acvPriority.setText(getReminderPriorityString(ReminderPriority.MEDIUM), false)
        binding.acvPriority.setOnItemClickListener { _, _, pos, _ ->
            selectedPriority = ReminderPriority.values()[pos]
        }

        // Repeat Intervals
        val repeats = RepeatInterval.values().map { getRepeatIntervalString(it) }
        val repeatAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, repeats)
        binding.acvRepeat.setAdapter(repeatAdapter)
        binding.acvRepeat.setText(getRepeatIntervalString(RepeatInterval.NONE), false)
        binding.acvRepeat.setOnItemClickListener { _, _, pos, _ ->
            selectedRepeat = RepeatInterval.values()[pos]
        }

        // Link Types
        val links = LinkedItemType.values().map { getLinkedItemTypeString(it) }
        val linkAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, links)
        binding.acvLinkType.setAdapter(linkAdapter)
        binding.acvLinkType.setText(getLinkedItemTypeString(LinkedItemType.NONE), false)
        binding.acvLinkType.setOnItemClickListener { _, _, pos, _ ->
            selectedLinkType = LinkedItemType.values()[pos]
        }

        // Snooze duration (placeholder support)
        val snoozeOptions = listOf("5 minutes", "10 minutes", "15 minutes", "30 minutes")
        val snoozeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, snoozeOptions)
        binding.acvSnooze.setAdapter(snoozeAdapter)
        binding.acvSnooze.setText("15 minutes", false)
    }

    private fun setupDateTimePickers() {
        binding.tvSelectedDateTime.text = getString(R.string.reminder_time) + dateTimeFormat.format(selectedDateTime.time)

        binding.btnDatePicker.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, yr, mo, dy ->
                    selectedDateTime.set(Calendar.YEAR, yr)
                    selectedDateTime.set(Calendar.MONTH, mo)
                    selectedDateTime.set(Calendar.DAY_OF_MONTH, dy)
                    binding.tvSelectedDateTime.text = getString(R.string.reminder_time) + dateTimeFormat.format(selectedDateTime.time)
                },
                selectedDateTime.get(Calendar.YEAR),
                selectedDateTime.get(Calendar.MONTH),
                selectedDateTime.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.btnTimePicker.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hr, min ->
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, hr)
                    selectedDateTime.set(Calendar.MINUTE, min)
                    binding.tvSelectedDateTime.text = getString(R.string.reminder_time) + dateTimeFormat.format(selectedDateTime.time)
                },
                selectedDateTime.get(Calendar.HOUR_OF_DAY),
                selectedDateTime.get(Calendar.MINUTE),
                false
            ).show()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.existingReminder.collect { item ->
                    if (item != null) {
                        binding.toolbar.title = getString(R.string.ui_edit_reminder)
                        binding.etTitle.setText(item.title)
                        binding.etDescription.setText(item.description)
                        selectedDateTime.timeInMillis = item.dateTime
                        binding.tvSelectedDateTime.text = getString(R.string.reminder_time) + dateTimeFormat.format(selectedDateTime.time)

                        selectedType = item.type
                        selectedPriority = item.priority
                        selectedRepeat = item.repeatInterval
                        selectedLinkType = item.linkedItemType

                        binding.acvType.setText(getReminderTypeString(item.type), false)
                        binding.acvPriority.setText(getReminderPriorityString(item.priority), false)
                        binding.acvRepeat.setText(getRepeatIntervalString(item.repeatInterval), false)
                        binding.acvLinkType.setText(getLinkedItemTypeString(item.linkedItemType), false)
                        binding.etLinkId.setText(item.linkedItemId ?: "")
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AddEditReminderEvent.SaveSuccess -> findNavController().navigateUp()
                        is AddEditReminderEvent.Error ->
                            Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun saveReminder() {
        viewModel.save(
            title = binding.etTitle.text.toString(),
            description = binding.etDescription.text.toString(),
            dateTime = selectedDateTime.timeInMillis,
            type = selectedType,
            priority = selectedPriority,
            repeatInterval = selectedRepeat,
            linkedItemId = binding.etLinkId.text.toString(),
            linkedItemType = selectedLinkType
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

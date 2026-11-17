package com.cognify.ui.calendar

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
import com.cognify.databinding.FragmentCalendarBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import com.cognify.util.setupMaterialSharedAxisTransitions
import java.util.Locale

@AndroidEntryPoint
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalendarViewModel by viewModels()
    private lateinit var adapter: ScheduleAdapter

    private val headerDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupMaterialSharedAxisTransitions()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupCalendar()
        observeState()

        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }
        binding.fabAdd.setOnClickListener { showAddOptions() }
    }

    private fun setupRecyclerView() {
        adapter = ScheduleAdapter(
            onItemClick = { item ->
                val bundle = Bundle()
                if (item.type == ScheduleItemType.TASK_DEADLINE) {
                    bundle.putString("taskId", item.id)
                    findNavController().navigate(R.id.action_calendarFragment_to_addEditTaskFragment, bundle)
                } else {
                    bundle.putString("reminderId", item.id)
                    findNavController().navigate(R.id.action_calendarFragment_to_addEditReminderFragment, bundle)
                }
            },
            onItemDelete = { item ->
                viewModel.deleteScheduleItem(item)
            }
        )
        binding.rvDailyPlanner.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CalendarFragment.adapter
        }
    }

    private fun setupCalendar() {
        binding.calendarView.setOnDateChangeListener { _, yr, mo, dy ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, yr)
                set(Calendar.MONTH, mo)
                set(Calendar.DAY_OF_MONTH, dy)
            }
            viewModel.selectDate(cal.timeInMillis)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedDate.collect { time ->
                        binding.tvPlannerHeader.text = getString(R.string.ui_schedule_for, headerDateFormat.format(Date(time)))
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        binding.plannerProgressBar.visibility = View.GONE
                        binding.layoutEmptyPlanner.visibility = View.GONE
                        binding.rvDailyPlanner.visibility = View.GONE
                        binding.tvPlannerCount.text = ""

                        when (state) {
                            is CalendarUiState.Loading -> binding.plannerProgressBar.visibility = View.VISIBLE
                            is CalendarUiState.Empty -> binding.layoutEmptyPlanner.visibility = View.VISIBLE
                            is CalendarUiState.Success -> {
                                binding.rvDailyPlanner.visibility = View.VISIBLE
                                adapter.submitList(state.items)
                                binding.tvPlannerCount.text = getString(R.string.calendar_items_scheduled, state.items.size)
                            }
                            is CalendarUiState.Error -> {
                                binding.layoutEmptyPlanner.visibility = View.VISIBLE
                                binding.tvEmptyPlannerText.text = state.message
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showAddOptions() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_fab, null)
        bottomSheetDialog.setContentView(view)

        // Find buttons in the custom layout
        val btnNewTask = view.findViewById<View>(R.id.btnNewTask)
        val btnNewReminder = view.findViewById<View>(R.id.btnNewReminder)

        btnNewTask.setOnClickListener {
            bottomSheetDialog.dismiss()
            findNavController().navigate(R.id.action_calendarFragment_to_addEditTaskFragment)
        }

        btnNewReminder?.setOnClickListener {
            bottomSheetDialog.dismiss()
            findNavController().navigate(R.id.action_calendarFragment_to_addEditReminderFragment)
        }

        bottomSheetDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

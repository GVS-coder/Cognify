package com.cognify.ui.dashboard

import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import android.animation.ValueAnimator
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
import com.cognify.databinding.FragmentDashboardBinding
import com.cognify.ui.calendar.ScheduleAdapter
import com.cognify.ui.calendar.ScheduleItemType
import com.cognify.ui.knowledge.KnowledgeAdapter
import com.cognify.ui.task.TaskAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.cognify.util.setupMaterialSharedAxisTransitions
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.color.MaterialColors
import java.util.Calendar

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var scheduleAdapter: ScheduleAdapter

    private var backgroundAnimator1: ObjectAnimator? = null
    private var backgroundAnimator2: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupMaterialSharedAxisTransitions()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupRecyclerView()
        setupScheduleRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        if (!com.cognify.BuildConfig.GMAIL_FEATURE_ENABLED) {
            val navMenu = binding.navigationView.menu
            navMenu.findItem(R.id.nav_smart_inbox)?.isVisible = false
        }
    }

    override fun onResume() {
        super.onResume()
        startAmbientAnimation()
    }

    override fun onPause() {
        super.onPause()
        pauseAmbientAnimation()
    }

    private fun startAmbientAnimation() {
        if (backgroundAnimator1 == null) {
            backgroundAnimator1 = ObjectAnimator.ofFloat(binding.ivAmbientEmerald, "translationX", -300f, 0f, -300f).apply {
                duration = 20000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
        }
        if (backgroundAnimator2 == null) {
            backgroundAnimator2 = ObjectAnimator.ofFloat(binding.ivAmbientCyan, "translationY", 200f, -100f, 200f).apply {
                duration = 25000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
        }
        if (backgroundAnimator1?.isStarted == true) {
            backgroundAnimator1?.resume()
        } else {
            backgroundAnimator1?.start()
        }
        if (backgroundAnimator2?.isStarted == true) {
            backgroundAnimator2?.resume()
        } else {
            backgroundAnimator2?.start()
        }
    }

    private fun pauseAmbientAnimation() {
        backgroundAnimator1?.pause()
        backgroundAnimator2?.pause()
    }

    private fun setupUI() {
        // Initial setup only. Data binding moved to observeViewModel.
        binding.tvGreeting.text = getString(R.string.dashboard_loading)
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onTaskClick = { task ->
                val bundle = Bundle().apply { putString("taskId", task.id) }
                findNavController().navigate(R.id.action_dashboardFragment_to_addEditTaskFragment, bundle)
            },
            onTaskChecked = { task, isChecked ->
                viewModel.toggleTaskCompletion(task, isChecked)
            },
            onTaskDelete = { task ->
                viewModel.deleteTask(task)
            }
        )
        binding.rvTasks.adapter = taskAdapter
    }

    private fun setupScheduleRecyclerView() {
        scheduleAdapter = ScheduleAdapter(
            onItemClick = { item ->
                val bundle = Bundle()
                if (item.type == ScheduleItemType.TASK_DEADLINE) {
                    bundle.putString("taskId", item.id)
                    findNavController().navigate(R.id.action_dashboardFragment_to_addEditTaskFragment, bundle)
                } else {
                    bundle.putString("reminderId", item.id)
                    findNavController().navigate(R.id.action_dashboardFragment_to_addEditReminderFragment, bundle)
                }
            },
            onItemDelete = { item ->
                if (item.type == ScheduleItemType.TASK_DEADLINE) {
                    viewModel.deleteTaskById(item.id)
                } else {
                    viewModel.deleteReminderById(item.id)
                }
            }
        )
        binding.rvTodaySchedule.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduleAdapter
        }
    }

    private fun setupClickListeners() {
        binding.ivDrawerMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_schedule -> {
                    findNavController().navigate(R.id.action_dashboardFragment_to_calendarFragment)
                    true
                }
                R.id.nav_knowledge -> {
                    findNavController().navigate(R.id.action_dashboardFragment_to_knowledgeFragment)
                    true
                }
                R.id.nav_clock -> {
                    findNavController().navigate(R.id.action_dashboardFragment_to_clockFragment)
                    true
                }
                R.id.nav_smart_inbox -> {
                    if (com.cognify.BuildConfig.GMAIL_FEATURE_ENABLED) {
                        findNavController().navigate(R.id.action_dashboardFragment_to_smartInboxFragment)
                    }
                    true
                }
                R.id.nav_ai -> {
                    findNavController().navigate(R.id.action_dashboardFragment_to_aiFragment)
                    true
                }
                R.id.nav_analytics -> {
                    findNavController().navigate(R.id.action_dashboardFragment_to_analyticsFragment)
                    true
                }
                R.id.nav_calculator -> {
                    findNavController().navigate(R.id.action_dashboardFragment_to_calculatorFragment)
                    true
                }
                R.id.nav_code -> {
                    findNavController().navigate(R.id.action_dashboardFragment_to_codeFragment)
                    true
                }
                R.id.nav_profile -> {
                    findNavController().navigate(R.id.action_dashboardFragment_to_profileFragment)
                    true
                }
                else -> false
            }
        }

        binding.toggleTaskFilter.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnFilterToday -> viewModel.setFilter(TaskFilter.TODAY)
                    R.id.btnFilterPending -> viewModel.setFilter(TaskFilter.PENDING)
                    R.id.btnFilterCompleted -> viewModel.setFilter(TaskFilter.COMPLETED)
                }
            }
        }

        binding.btnQuickTask.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_addEditTaskFragment)
        }

        binding.btnQuickNote.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_addEditKnowledgeFragment)
        }

        binding.btnQuickReminder.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_addEditReminderFragment)
        }

        binding.btnQuickSchedule.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_calendarFragment)
        }

        binding.tvViewCalendar.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_calendarFragment)
        }

        binding.ivProfile.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_profileFragment)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is DashboardUiState.Loading -> {
                                binding.pbTasksLoading.visibility = View.VISIBLE
                                binding.rvTasks.visibility = View.GONE
                                binding.tvEmptyTasks.visibility = View.GONE
                            }
                            is DashboardUiState.Success -> {
                                binding.pbTasksLoading.visibility = View.GONE
                                binding.rvTasks.visibility = View.VISIBLE
                                binding.tvEmptyTasks.visibility = View.GONE
                                taskAdapter.submitList(state.tasks)
                            }
                            is DashboardUiState.Empty -> {
                                binding.pbTasksLoading.visibility = View.GONE
                                binding.rvTasks.visibility = View.GONE
                                binding.tvEmptyTasks.visibility = View.VISIBLE
                            }
                            is DashboardUiState.Error -> {
                                binding.pbTasksLoading.visibility = View.GONE
                                binding.tvEmptyTasks.visibility = View.VISIBLE
                                binding.tvEmptyTasks.text = state.message
                            }
                        }
                    }
                }
                launch {
                    viewModel.todaySchedule.collect { items ->
                        if (items.isEmpty()) {
                            binding.rvTodaySchedule.visibility = View.GONE
                            binding.tvEmptySchedule.visibility = View.VISIBLE
                        } else {
                            binding.rvTodaySchedule.visibility = View.VISIBLE
                            binding.tvEmptySchedule.visibility = View.GONE
                            scheduleAdapter.submitList(items)
                        }
                    }
                }
                launch {
                    viewModel.aiSuggestion.collect { suggestion ->
                        binding.tvAISuggestionText.text = suggestion
                    }
                }
                launch {
                    viewModel.productivityStats.collect { stats ->
                        binding.tvStatTodayTasks.text = stats.todaysTasks.toString()
                        binding.tvStatPendingTasks.text = stats.pendingTasks.toString()
                        binding.tvStatCompletedTasks.text = stats.completedTasks.toString()
                        binding.tvStatKnowledge.text = stats.knowledgeEntries.toString()
                        binding.tvStatNotes.text = stats.notes.toString()
                        binding.tvStatSnippets.text = stats.codeSnippets.toString()

                        val total = stats.todaysTasks
                        val completed = stats.completedTasks
                        val progress = if (total > 0) (completed * 100) / total else 0
                        binding.pbTaskProgress.progress = progress
                        binding.tvProgressPercentage.text = "$progress%"
                        binding.tvProgressText.text = getString(R.string.dashboard_progress_tasks, completed, total)
                    }
                }
                launch {
                    viewModel.weeklyChartData.collect { chartData ->
                        setupBarChart(chartData)
                    }
                }
                launch {
                    viewModel.currentUser.collect { localUser ->
                        val fbUser = FirebaseAuth.getInstance().currentUser
                        val displayName = fbUser?.displayName ?: localUser?.displayName?.takeIf { it.isNotBlank() } ?: "User"
                        val photoUrl = fbUser?.photoUrl?.toString() ?: localUser?.photoUrl
                        
                        val cal = Calendar.getInstance()
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        val greeting = when (hour) {
                            in 5..11 -> getString(R.string.ui_good_morning)
                            in 12..16 -> getString(R.string.ui_good_afternoon)
                            in 17..20 -> getString(R.string.ui_good_evening)
                            else -> getString(R.string.ui_good_night)
                        }
                        binding.tvGreeting.text = getString(R.string.dashboard_greeting, greeting, displayName)

                        val email = fbUser?.email ?: localUser?.email ?: ""
                        
                        val headerView = binding.navigationView.getHeaderView(0)
                        val tvNavName = headerView.findViewById<android.widget.TextView>(R.id.tvNavHeaderName)
                        val tvNavEmail = headerView.findViewById<android.widget.TextView>(R.id.tvNavHeaderEmail)
                        tvNavName?.text = displayName
                        tvNavEmail?.text = email

                        val avatarDrawable = com.cognify.util.AvatarUtils.getAvatar(requireContext(), displayName, 60)

                        com.bumptech.glide.Glide.with(this@DashboardFragment)
                            .load(photoUrl)
                            .circleCrop()
                            .placeholder(avatarDrawable)
                            .error(avatarDrawable)
                            .fallback(avatarDrawable)
                            .into(binding.ivProfile)

                        val navProfile = headerView.findViewById<android.widget.ImageView>(R.id.ivNavProfile)
                        if (navProfile != null) {
                            com.bumptech.glide.Glide.with(this@DashboardFragment)
                                .load(photoUrl)
                                .circleCrop()
                                .placeholder(avatarDrawable)
                                .error(avatarDrawable)
                                .fallback(avatarDrawable)
                                .into(navProfile)
                        }
                    }
                }
                launch {
                    viewModel.lastSyncTime.collect { timeMs ->
                        if (timeMs != null) {
                            val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                            val timeStr = sdf.format(java.util.Date(timeMs))
                            binding.tvLastSync.text = getString(R.string.dashboard_last_synced, timeStr)
                            binding.tvLastSync.visibility = View.VISIBLE
                        } else {
                            binding.tvLastSync.visibility = View.GONE
                        }
                    }
                }
            }
        }
        binding.cardAISuggestions.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_aiFragment)
        }
    }

    private fun setupBarChart(chartData: Map<String, Int>) {
        val barChart = binding.barChart
        if (chartData.isEmpty()) {
            barChart.clear()
            return
        }

        val primaryColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorPrimary)
        val onSurfaceColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariantColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurfaceVariant)

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        var index = 0
        chartData.forEach { (label, value) ->
            entries.add(BarEntry(index.toFloat(), value.toFloat()))
            labels.add(label)
            index++
        }

        val dataSet = BarDataSet(entries, "Tasks")
        dataSet.color = primaryColor
        dataSet.valueTextColor = onSurfaceColor
        dataSet.valueTextSize = 10f
        
        dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value > 0) value.toInt().toString() else ""
            }
        }

        val barData = BarData(dataSet)
        barData.barWidth = 0.5f

        barChart.data = barData
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.setTouchEnabled(false)

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.isGranularityEnabled = true
        xAxis.granularity = 1f
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.textColor = onSurfaceVariantColor
        xAxis.textSize = 10f

        barChart.axisLeft.setDrawGridLines(true)
        barChart.axisLeft.gridColor = onSurfaceVariantColor
        barChart.axisLeft.textColor = onSurfaceVariantColor
        barChart.axisLeft.axisMinimum = 0f
        barChart.axisLeft.granularity = 1f
        
        barChart.axisRight.isEnabled = false
        
        barChart.setNoDataText("No activity data available")
        barChart.setNoDataTextColor(onSurfaceVariantColor)

        barChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

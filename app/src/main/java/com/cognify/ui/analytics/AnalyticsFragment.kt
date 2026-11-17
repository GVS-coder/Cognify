package com.cognify.ui.analytics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.cognify.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AnalyticsFragment : Fragment() {

    private val viewModel: AnalyticsViewModel by viewModels()
    private lateinit var timelineAdapter: TimelineAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }

        timelineAdapter = TimelineAdapter()
        view.findViewById<RecyclerView>(R.id.rvTimeline).adapter = timelineAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(view, state)
                }
            }
        }
    }

    private fun updateUi(view: View, state: AnalyticsUiState) {
        val loadingOverlay = view.findViewById<FrameLayout>(R.id.loadingOverlay)
        when (state) {
            is AnalyticsUiState.Loading -> {
                loadingOverlay.visibility = View.VISIBLE
            }
            is AnalyticsUiState.Success -> {
                loadingOverlay.visibility = View.GONE
                
                val isEmpty = state.tasksCompleted == 0 && state.knowledgeEntries == 0 && state.timelineItems.isEmpty() && state.weeklyTasksData.isEmpty()
                if (isEmpty) {
                    view.findViewById<View>(R.id.svContent).visibility = View.GONE
                    view.findViewById<View>(R.id.llEmptyState).visibility = View.VISIBLE
                } else {
                    view.findViewById<View>(R.id.svContent).visibility = View.VISIBLE
                    view.findViewById<View>(R.id.llEmptyState).visibility = View.GONE
                
                    view.findViewById<TextView>(R.id.tvProductivity).text = "${state.productivityPercentage}%"
                    view.findViewById<TextView>(R.id.tvStreak).text = resources.getQuantityString(R.plurals.analytics_study_duration_days, state.studyStreak, state.studyStreak)
                    view.findViewById<TextView>(R.id.tvTasksDone).text = state.tasksCompleted.toString()
                    view.findViewById<TextView>(R.id.tvKnowledgeCount).text = state.knowledgeEntries.toString()
                    
                    view.findViewById<TextView>(R.id.tvAiInsight).text = resources.getQuantityString(R.plurals.analytics_weekly_completed_tasks, state.aiInsight.toIntOrNull() ?: 0, state.aiInsight.toIntOrNull() ?: 0)
                    
                    timelineAdapter.submitList(state.timelineItems)

                    setupBarChart(view.findViewById(R.id.chartWeeklyTasks), state.weeklyTasksData)
                }
            }
            is AnalyticsUiState.Error -> {
                loadingOverlay.visibility = View.GONE
                // Show error snackbar or text
            }
        }
    }

    private fun setupBarChart(chart: BarChart, data: List<Pair<String, Int>>) {
        chart.setNoDataText(getString(R.string.analytics_no_data))
        if (data.isEmpty()) {
            chart.clear()
            return
        }

        val entries = data.mapIndexed { index, pair ->
            BarEntry(index.toFloat(), pair.second.toFloat())
        }

        val dataSet = BarDataSet(entries, getString(R.string.analytics_tasks_completed))
        dataSet.color = requireContext().getColor(R.color.color_primary)
        dataSet.valueTextColor = requireContext().getColor(R.color.color_on_surface)
        dataSet.valueTextSize = 10f
        
        val barData = BarData(dataSet)
        barData.barWidth = 0.5f
        chart.data = barData
        
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        
        val colorOnSurface = requireContext().getColor(R.color.color_on_surface)
        val colorOnSurfaceVariant = requireContext().getColor(R.color.color_on_surface_variant)
        val colorPrimaryVariant = requireContext().getColor(R.color.color_primary_variant)

        chart.xAxis.apply {
            setDrawGridLines(false)
            textColor = colorOnSurfaceVariant
            axisLineColor = colorPrimaryVariant
            position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(data.map { it.first })
        }
        
        chart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor = colorPrimaryVariant
            textColor = colorOnSurfaceVariant
            axisLineColor = colorPrimaryVariant
            axisMinimum = 0f
            granularity = 1f
        }
        
        chart.axisRight.isEnabled = false
        
        // Add subtle animation
        chart.animateY(800)
        
        chart.invalidate()
    }
}

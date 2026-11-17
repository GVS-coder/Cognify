package com.cognify.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cognify.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AISettingsFragment : Fragment() {

    private val viewModel: AISettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ai_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val tvAssistantLimit = view.findViewById<TextView>(R.id.tvAssistantLimit)
        val pbAssistant = view.findViewById<ProgressBar>(R.id.pbAssistant)
        val tvDocumentLimit = view.findViewById<TextView>(R.id.tvDocumentLimit)
        val pbDocument = view.findViewById<ProgressBar>(R.id.pbDocument)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.usageState.collectLatest { usage ->
                    val chatCount = usage?.aiAssistantRequests ?: 0
                    val docCount = usage?.documentAnalyses ?: 0

                    val chatRemaining = maxOf(0, 10 - chatCount)
                    val docRemaining = maxOf(0, 3 - docCount)

                    tvAssistantLimit.text = "$chatRemaining / 10 requests remaining"
                    pbAssistant.progress = chatCount

                    tvDocumentLimit.text = "$docRemaining / 3 analyses remaining"
                    pbDocument.progress = docCount
                }
            }
        }
    }
}

package com.cognify.ui.smartinbox

import com.cognify.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.cognify.databinding.FragmentGmailEmailDetailsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GmailEmailDetailsFragment : Fragment() {

    private var _binding: FragmentGmailEmailDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GmailEmailDetailsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGmailEmailDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val subject = arguments?.getString("subject") ?: getString(R.string.smart_inbox_no_subject)
        val sender = arguments?.getString("sender") ?: getString(R.string.smart_inbox_unknown_sender)
        val messageId = arguments?.getString("messageId") ?: ""

        binding.tvSubject.text = subject
        binding.tvSender.text = sender

        viewModel.fetchAiUsage()
        if (messageId.isNotEmpty()) {
            viewModel.loadContent(messageId)
        }

        binding.btnAnalyze.setOnClickListener {
            showConsentDialog()
        }
        
        binding.btnOpenInGmail.setOnClickListener {
            val threadId = arguments?.getString("threadId") ?: ""
            openInGmail(threadId)
        }

        observeUiState()
    }

    private fun openInGmail(threadId: String) {
        if (threadId.isEmpty()) return
        
        try {
            // Try opening in Gmail app
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("https://mail.google.com/mail/u/0/#all/$threadId")
            intent.setPackage("com.google.android.gm")
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            try {
                // Fallback to browser
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse("https://mail.google.com/mail/u/0/#all/$threadId")
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(requireContext(), getString(R.string.smart_inbox_cant_open), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.smart_inbox_cant_open), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showConsentDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.smart_inbox_dialog_title))
            .setMessage(getString(R.string.smart_inbox_dialog_message))
            .setPositiveButton(getString(R.string.smart_inbox_dialog_proceed)) { _, _ ->
                val content = binding.tvContent.text.toString()
                if (content.isNotBlank()) {
                    viewModel.analyzeEmail(content)
                }
            }
            .setNegativeButton(getString(R.string.smart_inbox_dialog_cancel), null)
            .show()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    // Content Loading
                    binding.progressContent.isVisible = state.isLoadingContent
                    if (state.contentError != null) {
                        binding.tvError.isVisible = true
                        binding.tvError.text = state.contentError
                    } else {
                        binding.tvError.isVisible = false
                    }

                    if (state.content != null) {
                        binding.tvContent.isVisible = true
                        binding.tvContent.text = state.content
                        
                        // Enable AI card if content is available
                        binding.cardAiAnalysis.isVisible = true
                    } else {
                        binding.tvContent.isVisible = false
                        binding.cardAiAnalysis.isVisible = false
                    }

                    // AI Usage
                    state.aiUsage?.let { usage ->
                        binding.tvAiUsage.text = getString(R.string.smart_inbox_analyses_rem, (usage.emailAnalysisLimit - usage.emailAnalysisUsed), usage.emailAnalysisUsed, usage.emailAnalysisLimit)
                    }

                    // AI Analysis
                    binding.btnAnalyze.isEnabled = !state.isAnalyzing
                    binding.progressAi.isVisible = state.isAnalyzing

                    if (state.analyzeError != null) {
                        Toast.makeText(requireContext(), state.analyzeError, Toast.LENGTH_LONG).show()
                    }

                    // AI Result
                    if (state.analysisResult != null) {
                        binding.llAiResults.isVisible = true
                        binding.btnAnalyze.isVisible = false
                        binding.tvAiUsage.isVisible = false
                        
                        val res = state.analysisResult
                        binding.tvAiType.text = res.type ?: "OTHER"
                        binding.tvAiTitle.text = res.title ?: "No Title"
                        binding.tvAiOrganization.text = res.organization ?: "No Organization"
                        
                        binding.tvAiDateTime.isVisible = !res.dateTime.isNullOrBlank()
                        binding.tvAiDateTime.text = getString(R.string.smart_inbox_datetime, res.dateTime)
                        
                        binding.tvAiDeadline.isVisible = !res.deadline.isNullOrBlank()
                        binding.tvAiDeadline.text = getString(R.string.smart_inbox_deadline, res.deadline)
                        
                        binding.tvAiSummary.text = res.summary ?: ""
                        
                        if (!res.actionItems.isNullOrEmpty()) {
                            binding.tvAiActionItems.isVisible = true
                            binding.tvAiActionItems.text = getString(R.string.smart_inbox_action_items) + res.actionItems.joinToString("\n") { "- $it" }
                        } else {
                            binding.tvAiActionItems.isVisible = false
                        }
                        
                        binding.btnAddAiTask.setOnClickListener {
                            navigateToAddAiTask(res)
                        }
                        
                        binding.btnAddAiReminder.setOnClickListener {
                            navigateToAddAiReminder(res)
                        }
                    } else {
                        binding.llAiResults.isVisible = false
                    }
                }
            }
        }
    }

    private fun navigateToAddAiTask(result: com.cognify.data.model.EmailAnalysisResult) {
        val prefill = com.cognify.ui.smartinbox.EmailAnalysisMapper.mapToTaskPrefill(result)
        val bundle = Bundle().apply {
            putString("prefillTitle", prefill["prefillTitle"] as? String)
            putString("prefillDescription", prefill["prefillDescription"] as? String)
            putLong("prefillDueDate", prefill["prefillDueDate"] as? Long ?: -1L)
        }
        findNavController().navigate(R.id.action_gmailEmailDetailsFragment_to_addEditTaskFragment, bundle)
    }

    private fun navigateToAddAiReminder(result: com.cognify.data.model.EmailAnalysisResult) {
        val prefill = com.cognify.ui.smartinbox.EmailAnalysisMapper.mapToReminderPrefill(result)
        val bundle = Bundle().apply {
            putString("prefillTitle", prefill["prefillTitle"] as? String)
            putString("prefillDescription", prefill["prefillDescription"] as? String)
            putLong("prefillDateTime", prefill["prefillDateTime"] as? Long ?: -1L)
        }
        findNavController().navigate(R.id.action_gmailEmailDetailsFragment_to_addEditReminderFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

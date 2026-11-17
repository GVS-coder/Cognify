package com.cognify.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cognify.databinding.FragmentAiBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.cognify.util.setupMaterialSharedAxisTransitions

@AndroidEntryPoint
class AIFragment : Fragment() {

    private var _binding: FragmentAiBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AIViewModel by viewModels()
    private lateinit var chatAdapter: AIChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupMaterialSharedAxisTransitions()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(com.cognify.R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                com.cognify.R.id.action_new_chat -> {
                    viewModel.startNewChat()
                    true
                }
                com.cognify.R.id.action_recent_conversations -> {
                    try { findNavController().navigate(com.cognify.R.id.action_aiFragment_to_conversationsFragment) } catch (e: Exception) {}
                    true
                }
                com.cognify.R.id.action_settings -> {
                    try { findNavController().navigate(com.cognify.R.id.action_aiFragment_to_aiSettingsFragment) } catch (e: Exception) {}
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = AIChatAdapter().apply {
            onCopyClick = { text ->
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Cognify AI", text)
                clipboard.setPrimaryClip(clip)
                com.google.android.material.snackbar.Snackbar.make(binding.root, "Copied to clipboard", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
            }
            onShareClick = { text ->
                val sendIntent: android.content.Intent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
            }
            onSaveClick = { text ->
                com.google.android.material.snackbar.Snackbar.make(binding.root, getString(com.cognify.R.string.ui_saved_knowledge_vault), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                // Placeholder for actual save action
            }
            onRegenerateClick = {
                val lastUserMessage = viewModel.uiState.value.messages.lastOrNull { it.role == com.cognify.data.model.Role.USER }
                if (lastUserMessage != null) {
                    viewModel.sendMessage(lastUserMessage.content)
                }
            }
        }
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private val speechLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        com.cognify.util.SpeechToTextHelper.handleSpeechResult(result.resultCode, result.data) { spokenText ->
            binding.etMessage.setText(spokenText)
        }
    }

    private fun setupClickListeners() {
        binding.btnAttach.setOnClickListener {
            findNavController().navigate(com.cognify.R.id.action_aiFragment_to_documentAnalysisFragment)
        }

        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString()
            if (message.isNotBlank()) {
                val prefs = requireContext().getSharedPreferences("cognify_prefs", android.content.Context.MODE_PRIVATE)
                if (!prefs.getBoolean("ai_privacy_accepted", false)) {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(getString(com.cognify.R.string.ui_how_does_ai_mode_work))
                        .setMessage(getString(com.cognify.R.string.ui_cognify_uses_a_locally_integra))
                        .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                            prefs.edit().putBoolean("ai_privacy_accepted", true).apply()
                            viewModel.sendMessage(message)
                            binding.etMessage.text?.clear()
                        }
                        .setNegativeButton(getString(android.R.string.cancel), null)
                        .show()
                } else {
                    viewModel.sendMessage(message)
                    binding.etMessage.text?.clear()
                }
            }
        }
        
        // Listen for document analysis results
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        
        savedStateHandle?.getLiveData<String>("selectedConversationId")
            ?.observe(viewLifecycleOwner) { id ->
                if (!id.isNullOrBlank()) {
                    viewModel.loadConversation(id)
                    savedStateHandle.remove<String>("selectedConversationId")
                }
            }
        binding.btnMic.setOnClickListener {
            com.cognify.util.SpeechToTextHelper.launchSpeechInput(
                requireContext(),
                speechLauncher,
                "Ask Cognify AI..."
            )
        }
        
        binding.btnConfirmationConfirm.setOnClickListener {
            it.isEnabled = false
            viewModel.confirmPendingCommand()
        }
        
        binding.btnConfirmationCancel.setOnClickListener {
            viewModel.cancelPendingCommand()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    chatAdapter.submitList(state.messages) {
                        if (state.messages.isNotEmpty()) {
                            binding.rvChat.scrollToPosition(state.messages.size - 1)
                        }
                    }
                    
                    if (state.messages.isEmpty() && state.suggestedPrompts.isNotEmpty()) {
                        binding.llSuggestions.visibility = View.VISIBLE
                        binding.llSuggestions.removeAllViews() // Using LinearLayout instead of ChipGroup
                        state.suggestedPrompts.forEach { prompt ->
                            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                                text = prompt
                                isCheckable = false
                                setChipBackgroundColorResource(com.cognify.R.color.color_surface_variant)
                                setTextColor(requireContext().getColor(com.cognify.R.color.color_on_surface))
                                setOnClickListener {
                                    binding.etMessage.setText(prompt)
                                    binding.etMessage.requestFocus()
                                    val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                    imm.showSoftInput(binding.etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                }
                                layoutParams = ViewGroup.MarginLayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    bottomMargin = resources.getDimensionPixelSize(com.cognify.R.dimen.spacing_sm)
                                }
                            }
                            binding.llSuggestions.addView(chip)
                        }
                        
                        // Set Greeting
                        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                        val name = user?.displayName?.split(" ")?.firstOrNull() ?: "there"
                        binding.tvGreeting.text = getString(com.cognify.R.string.ai_greeting, name)
                    } else {
                        binding.llSuggestions.visibility = View.GONE
                    }
                    
                    binding.layoutEmpty.visibility = if (state.messages.isEmpty()) View.VISIBLE else View.GONE

                    binding.layoutAiThinking.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    
                    binding.llAiUsage.visibility = if (state.aiUsage != null) View.VISIBLE else View.GONE
                    state.aiUsage?.let { usage ->
                        val remaining = usage.aiChatLimit - usage.aiChatUsed
                        binding.tvUsageText.text = getString(com.cognify.R.string.settings_ai_usage, remaining, usage.aiChatLimit)
                        binding.pbUsage.max = usage.aiChatLimit
                        binding.pbUsage.progress = usage.aiChatUsed
                        
                        binding.llAiUsage.contentDescription = "AI Chat Usage: ${usage.aiChatUsed} out of ${usage.aiChatLimit} requests used today."
                        binding.llAiUsage.isFocusable = true
                        
                        val ratio = if (usage.aiChatLimit > 0) usage.aiChatUsed.toFloat() / usage.aiChatLimit else 0f
                        when {
                            ratio >= 1.0f -> {
                                binding.tvUsageText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                                binding.pbUsage.progressTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(android.R.color.holo_red_dark))
                                binding.btnSend.isEnabled = false
                                binding.btnMic.isEnabled = false
                            }
                            ratio >= 0.8f -> {
                                binding.tvUsageText.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
                                binding.pbUsage.progressTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(android.R.color.holo_orange_dark))
                                binding.btnSend.isEnabled = !state.isLoading
                                binding.btnMic.isEnabled = !state.isLoading
                            }
                            else -> {
                                val defaultColor = com.google.android.material.color.MaterialColors.getColor(binding.tvUsageText, com.google.android.material.R.attr.colorOnSurface)
                                binding.tvUsageText.setTextColor(defaultColor)
                                binding.pbUsage.progressTintList = android.content.res.ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(binding.pbUsage, com.google.android.material.R.attr.colorPrimary))
                                binding.btnSend.isEnabled = !state.isLoading
                                binding.btnMic.isEnabled = !state.isLoading
                            }
                        }
                    } ?: run {
                        binding.btnSend.isEnabled = !state.isLoading
                        binding.btnMic.isEnabled = !state.isLoading
                    }
                    
                    val confDetails = state.pendingConfirmationDetails
                    if (confDetails != null) {
                        binding.cardConfirmation.visibility = View.VISIBLE
                        binding.btnConfirmationConfirm.isEnabled = true
                        
                        when (confDetails) {
                            is com.cognify.ai.ConfirmationDetails.Delete -> {
                                binding.tvConfirmationTitle.text = "Delete ${confDetails.itemType}?"
                                binding.tvConfirmationItemTitle.text = confDetails.itemTitle
                                binding.tvConfirmationDetails.text = confDetails.additionalInfo ?: ""
                                binding.tvConfirmationDetails.visibility = if (confDetails.additionalInfo != null) View.VISIBLE else View.GONE
                                binding.tvConfirmationWarning.visibility = View.VISIBLE
                                binding.btnConfirmationConfirm.text = "Delete"
                                binding.btnConfirmationConfirm.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                            }
                            is com.cognify.ai.ConfirmationDetails.Update -> {
                                binding.tvConfirmationTitle.text = "Update ${confDetails.itemType}?"
                                binding.tvConfirmationItemTitle.text = confDetails.itemTitle
                                
                                val detailsText = StringBuilder()
                                confDetails.changes.forEach { change ->
                                    detailsText.append("${change.fieldName}:\n")
                                    if (change.oldValue != null) {
                                        detailsText.append("  Old: ${change.oldValue}\n")
                                    }
                                    if (change.newValue != null) {
                                        detailsText.append("  New: ${change.newValue}\n")
                                    }
                                }
                                binding.tvConfirmationDetails.text = detailsText.toString().trimEnd()
                                binding.tvConfirmationDetails.visibility = View.VISIBLE
                                binding.tvConfirmationWarning.visibility = View.GONE
                                binding.btnConfirmationConfirm.text = "Update"
                                binding.btnConfirmationConfirm.setTextColor(requireContext().getColor(com.cognify.R.color.color_primary))
                            }
                        }
                    } else {
                        binding.cardConfirmation.visibility = View.GONE
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

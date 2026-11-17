package com.cognify.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cognify.databinding.FragmentDocumentAnalysisBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DocumentAnalysisFragment : Fragment() {

    private var _binding: FragmentDocumentAnalysisBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DocumentAnalysisViewModel by activityViewModels()

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.parseDocument(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.fetchAiUsage()

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.cvSelectDocument.setOnClickListener {
            documentPickerLauncher.launch(arrayOf("application/pdf"))
        }
        binding.btnSelectDocument.setOnClickListener {
            documentPickerLauncher.launch(arrayOf("application/pdf"))
        }

        binding.btnDiscard.setOnClickListener {
            binding.etInstruction.text?.clear()
            viewModel.discardDocument()
        }

        binding.btnAnalyze.setOnClickListener {
            binding.cvError.visibility = View.GONE
            val instruction = binding.etInstruction.text?.toString()
            val action = if (instruction.isNullOrBlank()) "Summarize this document" else instruction
            
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(com.cognify.R.string.ui_document_analysis_privacy))
                .setMessage(getString(com.cognify.R.string.ui_document_analysis_desc))
                .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                    viewModel.analyzeDocument(action)
                }
                .setNegativeButton(getString(android.R.string.cancel), null)
                .show()
        }

        binding.btnErrorAction.setOnClickListener {
            binding.cvError.visibility = View.GONE
            // If it's a try again action, we can just trigger analyze again
            binding.btnAnalyze.performClick()
        }

        setupSuggestionChips()

        binding.btnCopyResponse.setOnClickListener {
            val text = binding.tvResponse.text.toString()
            if (text.isNotBlank()) {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Cognify AI", text)
                clipboard.setPrimaryClip(clip)
                com.google.android.material.snackbar.Snackbar.make(binding.root, "Copied to clipboard", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
            }
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is DocumentState.Idle -> {
                            binding.loadingOverlay.visibility = View.GONE
                            binding.llDocumentInfo.visibility = View.GONE
                            binding.llResponse.visibility = View.GONE
                            binding.cvSelectDocument.visibility = View.VISIBLE
                            binding.cvError.visibility = View.GONE
                        }
                        is DocumentState.Loading -> {
                            binding.loadingOverlay.visibility = View.VISIBLE
                            binding.tvLoadingState.text = "Preparing document..."
                        }
                        is DocumentState.Success -> {
                            binding.loadingOverlay.visibility = View.GONE
                            binding.cvSelectDocument.visibility = View.GONE
                            binding.llResponse.visibility = View.GONE
                            binding.llDocumentInfo.visibility = View.VISIBLE
                            binding.cvError.visibility = View.GONE
                            
                            binding.tvDocumentName.text = state.document.name
                            binding.tvDocumentDetails.text = "${state.document.type} • ${state.document.formatSize()}"
                        }
                        is DocumentState.Analyzing -> {
                            binding.loadingOverlay.visibility = View.VISIBLE
                            binding.tvLoadingState.text = "Analyzing document..."
                            binding.llDocumentInfo.visibility = View.VISIBLE
                            binding.llResponse.visibility = View.GONE
                            binding.cvError.visibility = View.GONE
                        }
                        is DocumentState.AnalysisSuccess -> {
                            binding.loadingOverlay.visibility = View.GONE
                            binding.llDocumentInfo.visibility = View.GONE
                            binding.llResponse.visibility = View.VISIBLE
                            binding.tvResponse.text = state.response
                        }
                        is DocumentState.Error -> {
                            binding.loadingOverlay.visibility = View.GONE
                            if (state.document != null) {
                                binding.cvSelectDocument.visibility = View.GONE
                                binding.llResponse.visibility = View.GONE
                                binding.llDocumentInfo.visibility = View.VISIBLE
                                binding.tvDocumentName.text = state.document.name
                                binding.tvDocumentDetails.text = "${state.document.type} • ${state.document.formatSize()}"
                                binding.btnAnalyze.isEnabled = true
                                
                                // Show error card
                                binding.cvError.visibility = View.VISIBLE
                                val errorDetails = state.errorDetails
                                binding.tvErrorTitle.text = getErrorTitle(errorDetails.type)
                                binding.tvErrorDescription.text = getErrorDescription(errorDetails)
                            } else {
                                // If no document is selected, we can just show a toast for now or update a general error area
                                Toast.makeText(requireContext(), state.errorDetails.detailMessage ?: "An error occurred", Toast.LENGTH_LONG).show()
                                if (viewModel.uiState.value !is DocumentState.Analyzing) {
                                    viewModel.discardDocument()
                                }
                            }
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.aiUsage.collect { usage ->
                    binding.llAiUsage.visibility = if (usage != null) View.VISIBLE else View.GONE
                    usage?.let {
                        val remaining = kotlin.math.max(0, it.documentAnalysisLimit - it.documentAnalysisUsed)
                        binding.tvUsageText.text = getString(com.cognify.R.string.settings_ai_usage, remaining, it.documentAnalysisLimit)
                        binding.pbUsage.max = it.documentAnalysisLimit
                        binding.pbUsage.progress = it.documentAnalysisUsed
                        
                        binding.llAiUsage.contentDescription = "Document Analysis Usage: ${it.documentAnalysisUsed} out of ${it.documentAnalysisLimit} requests used today."
                        binding.llAiUsage.isFocusable = true
                        
                        val ratio = if (it.documentAnalysisLimit > 0) it.documentAnalysisUsed.toFloat() / it.documentAnalysisLimit else 0f
                        val isAtLimit = ratio >= 1.0f
                        
                        when {
                            ratio >= 1.0f -> {
                                binding.tvUsageText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                                binding.pbUsage.progressTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(android.R.color.holo_red_dark))
                            }
                            ratio >= 0.8f -> {
                                binding.tvUsageText.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
                                binding.pbUsage.progressTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(android.R.color.holo_orange_dark))
                            }
                            else -> {
                                val defaultColor = com.google.android.material.color.MaterialColors.getColor(binding.tvUsageText, com.google.android.material.R.attr.colorOnSurface)
                                binding.tvUsageText.setTextColor(defaultColor)
                                binding.pbUsage.progressTintList = android.content.res.ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(binding.pbUsage, com.google.android.material.R.attr.colorPrimary))
                            }
                        }
                        
                        binding.btnAnalyze.isEnabled = !isAtLimit
                    } ?: run {
                        binding.btnAnalyze.isEnabled = true
                    }
                }
            }
        }
    }

    private fun setupSuggestionChips() {
        val suggestions = listOf(
            "Summarize this document",
            "Extract key metrics",
            "Identify action items"
        )
        
        binding.llSuggestedPrompts.removeAllViews()
        for (prompt in suggestions) {
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = prompt
                setChipBackgroundColorResource(com.cognify.R.color.color_surface_variant)
                setTextColor(requireContext().getColor(com.cognify.R.color.color_on_surface))
                setOnClickListener {
                    binding.etInstruction.setText(prompt)
                    binding.etInstruction.requestFocus()
                    val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(binding.etInstruction, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(com.cognify.R.dimen.spacing_sm)
                }
            }
            binding.llSuggestedPrompts.addView(chip)
        }
    }

    private fun getErrorTitle(type: com.cognify.ai.DocumentAnalysisErrorType): String {
        return when (type) {
            com.cognify.ai.DocumentAnalysisErrorType.FILE_TOO_LARGE -> "⚠ Document too large"
            com.cognify.ai.DocumentAnalysisErrorType.PAGE_LIMIT_EXCEEDED -> "⚠ Too many pages"
            com.cognify.ai.DocumentAnalysisErrorType.UNSUPPORTED_FILE -> "Unsupported document"
            com.cognify.ai.DocumentAnalysisErrorType.EMPTY_DOCUMENT -> "No readable text found"
            com.cognify.ai.DocumentAnalysisErrorType.OCR_FAILED -> "Document text extraction failed"
            com.cognify.ai.DocumentAnalysisErrorType.AI_FAILED -> "AI analysis unavailable"
            com.cognify.ai.DocumentAnalysisErrorType.NETWORK_ERROR -> "No internet connection"
            com.cognify.ai.DocumentAnalysisErrorType.SERVER_ERROR, 
            com.cognify.ai.DocumentAnalysisErrorType.TIMEOUT -> "Document analysis server error"
            com.cognify.ai.DocumentAnalysisErrorType.RATE_LIMIT -> "Document analysis limit reached"
            com.cognify.ai.DocumentAnalysisErrorType.UNKNOWN -> "Analysis failed"
        }
    }

    private fun getErrorDescription(details: com.cognify.ai.DocumentAnalysisErrorDetails): String {
        return when (details.type) {
            com.cognify.ai.DocumentAnalysisErrorType.FILE_TOO_LARGE -> {
                val current = details.currentValue?.let { "This PDF is $it." } ?: ""
                val max = details.maximumValue?.let { "Maximum supported size: $it." } ?: ""
                "$current\n$max\n\nPlease choose a smaller file.".trim()
            }
            com.cognify.ai.DocumentAnalysisErrorType.PAGE_LIMIT_EXCEEDED -> {
                val current = details.currentValue?.let { "This document contains $it pages." } ?: ""
                val max = details.maximumValue?.let { "Maximum supported pages: $it." } ?: ""
                "$current\n$max\n\nPlease upload a shorter PDF.".trim()
            }
            com.cognify.ai.DocumentAnalysisErrorType.UNSUPPORTED_FILE -> "This file type is not supported.\nPlease select a PDF document."
            com.cognify.ai.DocumentAnalysisErrorType.EMPTY_DOCUMENT -> "The document was processed, but no\nreadable text could be extracted.\n\nTry uploading a text-based PDF."
            com.cognify.ai.DocumentAnalysisErrorType.OCR_FAILED -> "The document could not be processed\nby the text extraction service.\n\nPlease try again later."
            com.cognify.ai.DocumentAnalysisErrorType.AI_FAILED -> "The document text was extracted\nsuccessfully, but the AI service could\nnot complete the analysis.\n\nPlease try again."
            com.cognify.ai.DocumentAnalysisErrorType.NETWORK_ERROR -> "Please check your internet connection\nand try again."
            com.cognify.ai.DocumentAnalysisErrorType.SERVER_ERROR,
            com.cognify.ai.DocumentAnalysisErrorType.TIMEOUT -> "The Cognify analysis service is\ntemporarily unavailable.\n\nPlease try again later."
            com.cognify.ai.DocumentAnalysisErrorType.RATE_LIMIT -> {
                if (details.currentValue != null && details.maximumValue != null) {
                    "You have used ${details.currentValue} / ${details.maximumValue} document analyses today.\n\nYour limit will reset tomorrow."
                } else {
                    details.detailMessage ?: "The service is temporarily busy. Please try again later."
                }
            }
            com.cognify.ai.DocumentAnalysisErrorType.UNKNOWN -> details.detailMessage ?: "An unexpected error occurred."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

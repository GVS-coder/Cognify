package com.cognify.ui.export

import com.cognify.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cognify.databinding.FragmentExportBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExportViewModel by viewModels()

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val exportNotes = binding.checkNotes.isChecked
            val exportCode = binding.checkCode.isChecked
            viewModel.generateAndExportCsv(requireContext(), uri, exportNotes, exportCode)
        } else {
            Toast.makeText(requireContext(), getString(R.string.export_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupListeners()
        observeExportState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(com.cognify.R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }
    }
    
    private fun setupListeners() {
        binding.btnExport.setOnClickListener {
            val exportNotes = binding.checkNotes.isChecked
            val exportCode = binding.checkCode.isChecked
            
            if (!exportNotes && !exportCode) {
                Toast.makeText(requireContext(), getString(R.string.export_select_data), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val fileName = when {
                exportNotes && exportCode -> "Cognify_Knowledge_$dateStr.csv"
                exportNotes -> "Cognify_Notes_$dateStr.csv"
                else -> "Cognify_Code_$dateStr.csv"
            }
            
            createDocumentLauncher.launch(fileName)
        }
    }

    private fun observeExportState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exportState.collectLatest { state ->
                    if (state.isSuccess) {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        viewModel.clearState()
                    } else if (state.error != null) {
                        Toast.makeText(requireContext(), state.error, Toast.LENGTH_LONG).show()
                        viewModel.clearState()
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

package com.cognify.ui.backup

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cognify.BuildConfig
import com.cognify.databinding.FragmentBackupBinding
import com.cognify.data.repository.BackupMetadata
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class BackupFragment : Fragment() {

    private var _binding: FragmentBackupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BackupViewModel by viewModels()

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            viewModel.exportLocalBackup(requireContext(), it, BuildConfig.VERSION_NAME)
        }
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            AlertDialog.Builder(requireContext())
                .setTitle("Restore Data")
                .setMessage("Are you sure you want to restore from this backup? This will replace your local data. Make sure you have exported any unsaved notes.")
                .setPositiveButton("Restore") { _, _ ->
                    viewModel.importLocalBackup(requireContext(), it)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupListeners()
        observeUiState()
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
        binding.btnBackupNow.setOnClickListener {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val filename = "Cognify_Backup_${sdf.format(Date())}.json"
            createDocumentLauncher.launch(filename)
        }
        
        binding.btnRestore.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    when (state) {
                        is BackupUiState.Initial -> {
                            binding.progressBar.visibility = View.GONE
                        }
                        is BackupUiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.btnBackupNow.isEnabled = false
                            binding.btnRestore.isEnabled = false
                        }
                        is BackupUiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnBackupNow.isEnabled = true
                            binding.btnRestore.isEnabled = true
                            updateMetadata(state.metadata)
                        }
                        is BackupUiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnBackupNow.isEnabled = true
                            binding.btnRestore.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            binding.tvSyncStatus.text = "Error"
                        }
                    }
                }
            }
        }
    }

    private fun updateMetadata(metadata: BackupMetadata) {
        binding.tvSyncStatus.text = "Ready" // Show ready instead of Cloud Sync status if local
        
        if (metadata.lastBackupTime > 0) {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            binding.tvLastBackup.text = sdf.format(Date(metadata.lastBackupTime))
        } else {
            binding.tvLastBackup.text = "Never"
        }
        
        binding.tvItemsSynced.text = metadata.itemsSynced.toString()
        binding.tvBackupSize.text = Formatter.formatShortFileSize(requireContext(), metadata.backupSizeBytes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

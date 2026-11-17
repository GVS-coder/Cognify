package com.cognify.ui.profile

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
import com.cognify.R
import com.cognify.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    @Inject
    lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupClickListeners()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try {
                        findNavController().navigate(R.id.dashboardFragment)
                    } catch (e: Exception) {
                        // Handled
                    }
                }
            }
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    findNavController().navigate(R.id.action_profileFragment_to_settingsFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
        
        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }
        
        binding.cardExport.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_exportFragment)
        }
        
        binding.cardBackup.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_backupFragment)
        }
        
        binding.btnEmptyStateLogin.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val file = java.io.File(requireContext().filesDir, "profile_photo.jpg")
                val outputStream = java.io.FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                viewModel.updateProfilePhoto(android.net.Uri.fromFile(file))
                Snackbar.make(binding.root, getString(R.string.profile_photo_updated), Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, getString(R.string.profile_photo_failed), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditProfileDialog() {
        val currentName = binding.tvDisplayName.text.toString()
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
        }

        val inputLayout = com.google.android.material.textfield.TextInputLayout(requireContext())
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "Display Name"
            setText(currentName)
        }
        inputLayout.addView(input)
        container.addView(inputLayout)

        val btnChangePhoto = com.google.android.material.button.MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Change Profile Photo"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
            setOnClickListener {
                pickImageLauncher.launch("image/*")
            }
        }
        container.addView(btnChangePhoto)

        val btnRemovePhoto = com.google.android.material.button.MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Remove Profile Photo"
            setTextColor(android.graphics.Color.RED)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            setOnClickListener {
                viewModel.updateProfilePhoto(null)
                Snackbar.make(binding.root, getString(R.string.profile_photo_removed), Snackbar.LENGTH_SHORT).show()
            }
        }
        container.addView(btnRemovePhoto)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Profile")
            .setView(container)
            .setPositiveButton("Save") { dialog, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    viewModel.updateDisplayName(newName)
                    binding.tvDisplayName.text = newName
                    Snackbar.make(binding.root, getString(R.string.profile_updated_success), Snackbar.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    
                    state.user?.let { user ->
                        binding.svContent.visibility = View.VISIBLE
                        binding.llEmptyState.visibility = View.GONE
                        
                        binding.tvDisplayName.text = user.displayName ?: "No Name"
                        binding.tvEmail.text = user.email
                        
                        val fbUser = FirebaseAuth.getInstance().currentUser
                        val photoUrl = fbUser?.photoUrl?.toString() ?: user.photoUrl
                        
                        binding.ivProfilePhoto.setPadding(0, 0, 0, 0)
                        
                        val avatarDrawable = com.cognify.util.AvatarUtils.getAvatar(requireContext(), user.displayName ?: "User", 100)

                        com.bumptech.glide.Glide.with(this@ProfileFragment)
                            .load(photoUrl)
                            .circleCrop()
                            .placeholder(avatarDrawable)
                            .error(avatarDrawable)
                            .fallback(avatarDrawable)
                            .into(binding.ivProfilePhoto)
                    } ?: run {
                        if (!state.isLoading) {
                            binding.svContent.visibility = View.GONE
                            binding.llEmptyState.visibility = View.VISIBLE
                        }
                    }
                    
                    binding.tvTotalTasks.text = state.totalTasks.toString()
                    binding.tvTotalKnowledge.text = state.totalKnowledgeEntries.toString()
                    
                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }
    
    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
                // Navigate to Login/Splash
                findNavController().navigate(R.id.action_profileFragment_to_splashFragment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

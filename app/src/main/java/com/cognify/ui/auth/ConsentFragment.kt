package com.cognify.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.cognify.R
import com.cognify.databinding.FragmentConsentBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ConsentFragment : Fragment() {

    private var _binding: FragmentConsentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConsentViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConsentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupListeners()
        observeViewModel()
        
        // Handle back button press
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If user hits back, logout and return to login screen safely
                authViewModel.logout()
                findNavController().navigate(R.id.action_consentFragment_to_loginFragment)
            }
        })
    }

    private fun setupListeners() {
        binding.tvPrivacyPolicyLink.setOnClickListener {
            val url = getString(R.string.ui_privacy_policy_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        binding.cbConsent.setOnCheckedChangeListener { _, isChecked ->
            binding.btnAccept.isEnabled = isChecked
        }

        binding.btnAccept.setOnClickListener {
            viewModel.acceptConsent()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnAccept.isEnabled = binding.cbConsent.isChecked && !isLoading
            binding.cbConsent.isEnabled = !isLoading
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (!errorMsg.isNullOrBlank()) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = errorMsg
            } else {
                binding.tvError.visibility = View.GONE
            }
        }

        viewModel.consentSaved.observe(viewLifecycleOwner) { saved ->
            if (saved) {
                // Consent is saved (or was already saved), proceed to dashboard
                findNavController().navigate(R.id.action_consentFragment_to_dashboardFragment)
            }
        }
        
        viewModel.logoutRequired.observe(viewLifecycleOwner) { required ->
            if (required) {
                authViewModel.logout()
                findNavController().navigate(R.id.action_consentFragment_to_loginFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

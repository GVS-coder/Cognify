package com.cognify.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.cognify.R
import com.cognify.databinding.FragmentSignupBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignupFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInputListeners()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupInputListeners() {
        binding.etDisplayName.doAfterTextChanged { viewModel.clearErrors() }
        binding.etEmail.doAfterTextChanged { viewModel.clearErrors() }
        binding.etPassword.doAfterTextChanged { viewModel.clearErrors() }
        binding.etConfirmPassword.doAfterTextChanged { viewModel.clearErrors() }
    }

    private fun setupClickListeners() {
        binding.btnSignup.setOnClickListener {
            val displayName = binding.etDisplayName.text.toString()
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            viewModel.signup(displayName, email, password, confirmPassword)
        }

        binding.tvLogin.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            // Loading
            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.btnSignup.isEnabled = !state.isLoading

            // Validation errors
            binding.tilDisplayName.error = state.displayNameError
            binding.tilEmail.error = state.emailError
            binding.tilPassword.error = state.passwordError
            binding.tilConfirmPassword.error = state.confirmPasswordError

            // General error
            if (!state.errorMessage.isNullOrBlank()) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = state.errorMessage
            } else {
                binding.tvError.visibility = View.GONE
            }

            // Success → navigate to consent gate
            if (state.signupSuccess != null) {
                findNavController().navigate(R.id.action_signupFragment_to_consentFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

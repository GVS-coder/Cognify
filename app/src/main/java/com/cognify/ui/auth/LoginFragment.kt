package com.cognify.ui.auth

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.cognify.R
import com.cognify.databinding.DialogForgotPasswordBinding
import com.cognify.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInputListeners()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupInputListeners() {
        binding.etEmail.doAfterTextChanged { viewModel.clearErrors() }
        binding.etPassword.doAfterTextChanged { viewModel.clearErrors() }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            viewModel.login(email, password)
        }

        binding.btnGoogleSignIn.setOnClickListener {
            performGoogleSignIn()
        }

        binding.tvSignUp.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_signupFragment)
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun performGoogleSignIn() {
        val credentialManager = CredentialManager.create(requireContext())

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(true)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = requireContext()
                )
                
                val credential = result.credential
                if (credential is GoogleIdTokenCredential) {
                    val idToken = credential.idToken
                    viewModel.loginWithGoogle(idToken)
                } else if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.loginWithGoogle(googleIdTokenCredential.idToken)
                }
            } catch (e: GetCredentialException) {
                Timber.e(e, "Google Sign-In failed with GetCredentialException")
                Toast.makeText(requireContext(), "Google Sign-In failed. Please try again.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during Google Sign-In")
                Toast.makeText(requireContext(), "An unexpected error occurred.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            // Loading
            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = !state.isLoading

            // Field-level validation errors
            binding.tilEmail.error = state.emailError
            binding.tilPassword.error = state.passwordError

            // General error
            if (!state.errorMessage.isNullOrBlank()) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = state.errorMessage
            } else {
                binding.tvError.visibility = View.GONE
            }

            // Success → navigate to consent gate
            if (state.loginSuccess != null) {
                findNavController().navigate(R.id.action_loginFragment_to_consentFragment)
            }
        }
    }

    private fun showForgotPasswordDialog() {
        val dialogBinding = DialogForgotPasswordBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSendReset.setOnClickListener {
            val email = dialogBinding.etResetEmail.text.toString()
            viewModel.sendPasswordReset(email)
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            dialogBinding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            dialogBinding.tilResetEmail.error = state.emailError

            if (state.passwordResetEmailSent) {
                com.google.android.material.snackbar.Snackbar.make(binding.root, state.successMessage ?: "Success", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                viewModel.clearSuccess()
                dialog.dismiss()
            }

            if (!state.errorMessage.isNullOrBlank() && !state.isLoading) {
                dialogBinding.tvResetError.visibility = View.VISIBLE
                dialogBinding.tvResetError.text = state.errorMessage
            } else {
                dialogBinding.tvResetError.visibility = View.GONE
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

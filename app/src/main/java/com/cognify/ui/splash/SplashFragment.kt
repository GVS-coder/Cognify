package com.cognify.ui.splash

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.cognify.R
import com.cognify.databinding.FragmentSplashBinding
import com.cognify.ui.auth.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startAnimationsAndNavigate()
    }

    private fun startAnimationsAndNavigate() {
        // Logo fade-in + scale animation
        val logoAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.splash_logo_anim)
        binding.ivLogo.startAnimation(logoAnim)

        // Title slide-in
        val titleAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.splash_title_anim)
        binding.tvAppTitle.startAnimation(titleAnim)

        // Subtitle fade-in
        val subtitleAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.splash_subtitle_anim)
        binding.tvSubtitle.startAnimation(subtitleAnim)

        lifecycleScope.launch {
            delay(1200L)
            navigateNext()
        }
    }

    @Inject
    lateinit var settingsRepository: com.cognify.data.repository.SettingsRepository

    private fun navigateNext() {
        if (authViewModel.isUserLoggedIn()) {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user != null) {
                lifecycleScope.launch {
                    val accepted = settingsRepository.isConsentAccepted(user.uid).first()
                    if (accepted) {
                        findNavController().navigate(R.id.action_splashFragment_to_dashboardFragment)
                    } else {
                        findNavController().navigate(R.id.action_splashFragment_to_consentFragment)
                    }
                }
            } else {
                findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
            }
        } else {
            findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

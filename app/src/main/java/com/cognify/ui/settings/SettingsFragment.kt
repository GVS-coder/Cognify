package com.cognify.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cognify.R
import com.cognify.databinding.FragmentSettingsBinding
import com.cognify.data.repository.ThemeMode
import com.cognify.data.repository.AiMode
import com.cognify.data.repository.AiUsageRepository
import com.cognify.ui.ai.AISettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import androidx.activity.result.contract.ActivityResultContracts
import com.cognify.data.repository.GmailConnectionState
import android.widget.Toast

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()
    private val aiSettingsViewModel: AISettingsViewModel by viewModels()
    private var isInitializing = true
    private var progressDialog: androidx.appcompat.app.AlertDialog? = null

    private val googleReauthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (!idToken.isNullOrBlank()) {
                viewModel.reauthenticateWithGoogle(idToken)
            } else {
                Toast.makeText(requireContext(), "Google authentication token not available. Please try again.", Toast.LENGTH_LONG).show()
                viewModel.resetDeleteState()
            }
        } catch (e: ApiException) {
            val errorMessage = if (e.statusCode == com.google.android.gms.common.api.CommonStatusCodes.CANCELED) {
                "Reauthentication cancelled"
            } else {
                "Google sign-in failed (Code: ${e.statusCode})"
            }
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            viewModel.resetDeleteState()
        }
    }

    private val gmailSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            // Successful connection
            viewModel.updateGmailConnectionState(GmailConnectionState.CONNECTED, account.email)
            Toast.makeText(requireContext(), getString(R.string.settings_gmail_connected), Toast.LENGTH_SHORT).show()
        } catch (e: ApiException) {
            // Connection failed or cancelled
            viewModel.updateGmailConnectionState(GmailConnectionState.DISCONNECTED, null)
            val errorMessage = if (e.statusCode == com.google.android.gms.common.api.CommonStatusCodes.CANCELED) {
                "Gmail connection cancelled"
            } else {
                "Failed to connect Gmail (Code: ${e.statusCode})"
            }
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupExpandableSections()
        setupAboutSection()
        setupSpinners()
        setupListeners()
        observeSettings()
        observeAiUsage()
        observeDeleteState()
        
        if (!com.cognify.BuildConfig.GMAIL_FEATURE_ENABLED) {
            binding.cardSmartInbox.visibility = View.GONE
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }
    }
    
    private fun setupExpandableSections() {
        setupExpandToggle(binding.headerAppearance, binding.contentAppearance, binding.iconExpandAppearance)
        setupExpandToggle(binding.headerNotifications, binding.contentNotifications, binding.iconExpandNotifications)
        setupExpandToggle(binding.headerAiSettings, binding.contentAiSettings, binding.iconExpandAiSettings)
        setupExpandToggle(binding.headerSmartInbox, binding.contentSmartInbox, binding.iconExpandSmartInbox)
        setupExpandToggle(binding.headerProductivity, binding.contentProductivity, binding.iconExpandProductivity)
        setupExpandToggle(binding.headerAbout, binding.contentAbout, binding.iconExpandAbout)
        setupExpandToggle(binding.headerDangerZone, binding.contentDangerZone, binding.iconExpandDangerZone)
    }

    private fun setupExpandToggle(header: View, content: View, icon: android.widget.ImageView) {
        header.setOnClickListener {
            val isExpanded = content.visibility == View.VISIBLE
            if (isExpanded) {
                content.visibility = View.GONE
                icon.setImageResource(R.drawable.ic_expand_more)
            } else {
                content.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_expand_less)
            }
        }
    }

    private fun setupAboutSection() {
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.tvAppVersion.text = getString(R.string.settings_version, pInfo.versionName)
        } catch (e: Exception) {
            binding.tvAppVersion.text = "Version 1.0"
        }
    }
    
    private fun setupSpinners() {
        val themeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, arrayOf(getString(R.string.settings_theme_light), getString(R.string.settings_theme_dark), getString(R.string.settings_theme_system)))
        binding.spinnerThemeMode.setAdapter(themeAdapter)
        
        val languageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, arrayOf(getString(R.string.settings_lang_en), getString(R.string.settings_lang_ta)))
        binding.spinnerLanguage.setAdapter(languageAdapter)
        
        val aiAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, arrayOf(getString(R.string.settings_ai_general), getString(R.string.settings_ai_task), getString(R.string.settings_ai_knowledge), getString(R.string.settings_ai_study)))
        binding.spinnerAiMode.setAdapter(aiAdapter)
        
        val priorityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, arrayOf(getString(R.string.settings_priority_low), getString(R.string.settings_priority_medium), getString(R.string.settings_priority_high)))
        binding.spinnerPriority.setAdapter(priorityAdapter)
        
        val studyAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, arrayOf("15m", "25m", "45m", "60m"))
        binding.spinnerStudyDuration.setAdapter(studyAdapter)
        
        val daysAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, arrayOf(getString(R.string.settings_day_sunday), getString(R.string.settings_day_monday)))
        binding.spinnerFirstDay.setAdapter(daysAdapter)
    }
    
    private fun setupListeners() {
        binding.spinnerThemeMode.setOnItemClickListener { _, _, position, _ ->
            if (!isInitializing) {
                val mode = when (position) {
                    0 -> ThemeMode.LIGHT
                    1 -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                }
                viewModel.updateThemeMode(mode)
            }
        }
        
        binding.spinnerLanguage.setOnItemClickListener { _, _, position, _ ->
            if (!isInitializing) {
                val lang = if (position == 1) "ta" else "en"
                viewModel.updateLanguage(lang)
                val appLocale = androidx.core.os.LocaleListCompat.forLanguageTags(lang)
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
            }
        }
        
        binding.switchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
            if (!isInitializing) viewModel.updateDynamicColor(isChecked)
        }
        
        binding.switchNotifyTasks.setOnCheckedChangeListener { _, isChecked ->
            if (!isInitializing) viewModel.updateNotifications(tasks = isChecked)
        }
        binding.switchNotifyCalendar.setOnCheckedChangeListener { _, isChecked ->
            if (!isInitializing) viewModel.updateNotifications(calendar = isChecked)
        }
        binding.switchNotifyDailySummary.setOnCheckedChangeListener { _, isChecked ->
            if (!isInitializing) viewModel.updateNotifications(dailySummary = isChecked)
        }
        binding.switchNotifyAiSuggestions.setOnCheckedChangeListener { _, isChecked ->
            if (!isInitializing) viewModel.updateNotifications(aiSuggestions = isChecked)
        }
        binding.switchNotifyWeeklyReport.setOnCheckedChangeListener { _, isChecked ->
            if (!isInitializing) viewModel.updateNotifications(weeklyReport = isChecked)
        }
        
        binding.spinnerAiMode.setOnItemClickListener { _, _, position, _ ->
            if (!isInitializing) {
                val mode = when(position) {
                    0 -> AiMode.GENERAL
                    1 -> AiMode.TASK
                    2 -> AiMode.KNOWLEDGE
                    else -> AiMode.STUDY
                }
                viewModel.updateAiDefaultMode(mode)
            }
        }
        
        binding.spinnerPriority.setOnItemClickListener { _, _, position, _ ->
            if (!isInitializing) {
                viewModel.updateProductivityDefaults(priority = position)
            }
        }
        
        binding.spinnerStudyDuration.setOnItemClickListener { _, _, position, _ ->
            if (!isInitializing) {
                val duration = when(position) {
                    0 -> 15
                    1 -> 25
                    2 -> 45
                    else -> 60
                }
                viewModel.updateProductivityDefaults(studyDuration = duration)
            }
        }
        
        binding.spinnerFirstDay.setOnItemClickListener { _, _, position, _ ->
            if (!isInitializing) {
                val day = if (position == 0) 1 else 2 // 1=Sunday, 2=Monday
                viewModel.updateProductivityDefaults(firstDayOfWeek = day)
            }
        }
        
        binding.btnConnectGmail.setOnClickListener {
            if (!com.cognify.BuildConfig.GMAIL_FEATURE_ENABLED) return@setOnClickListener
            val currentState = viewModel.appSettings.value.gmailConnectionState
            if (currentState == GmailConnectionState.CONNECTED) {
                // Disconnect
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestScopes(Scope("https://www.googleapis.com/auth/gmail.readonly"))
                    .requestEmail()
                    .build()
                val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
                googleSignInClient.revokeAccess().addOnCompleteListener {
                    viewModel.updateGmailConnectionState(GmailConnectionState.DISCONNECTED, null)
                    Toast.makeText(requireContext(), getString(R.string.settings_gmail_disconnected), Toast.LENGTH_SHORT).show()
                }
            } else {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.ui_gmail_integration))
                    .setMessage(getString(R.string.ui_gmail_integration_desc))
                    .setPositiveButton(getString(R.string.ui_connect_gmail)) { _, _ ->
                        viewModel.updateGmailConnectionState(GmailConnectionState.CONNECTING, null)
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestScopes(Scope("https://www.googleapis.com/auth/gmail.readonly"))
                            .requestEmail()
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
                        val signInIntent = googleSignInClient.signInIntent
                        gmailSignInLauncher.launch(signInIntent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        
        binding.btnOpenSmartInbox.setOnClickListener {
            if (!com.cognify.BuildConfig.GMAIL_FEATURE_ENABLED) return@setOnClickListener
            findNavController().navigate(R.id.action_settingsFragment_to_smartInboxFragment)
        }
        
        binding.tvHelp.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_helpFragment)
        }
        
        binding.tvPrivacy.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_privacyFragment)
        }
        
        binding.tvAbout.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_aboutFragment)
        }
        
        binding.tvClearCache.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Clear Local Cache")
                .setMessage("This will remove all downloaded and cached data from this device. Some data may need to be re-downloaded from the cloud. Are you sure?")
                .setPositiveButton("Clear") { _, _ ->
                    viewModel.clearLocalCache {
                        android.widget.Toast.makeText(requireContext(), getString(R.string.settings_cache_cleared), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.tvDeleteAccount.setOnClickListener {
            if (viewModel.deleteState.value is DeleteAccountState.Loading) {
                return@setOnClickListener
            }
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.settings_delete_account))
                .setMessage("Are you sure you want to completely delete your account? This will permanently delete all your data, notes, and tasks from our servers. This action CANNOT be undone.")
                .setPositiveButton("Delete Forever") { _, _ ->
                    viewModel.deleteAccount()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.appSettings.collectLatest { settings ->
                    isInitializing = true
                    
                    val themeOptions = arrayOf(getString(R.string.settings_theme_light), getString(R.string.settings_theme_dark), getString(R.string.settings_theme_system))
                    val themePos = when (settings.themeMode) {
                        ThemeMode.LIGHT -> 0
                        ThemeMode.DARK -> 1
                        ThemeMode.SYSTEM -> 2
                    }
                    binding.spinnerThemeMode.setText(themeOptions[themePos], false)
                    
                    val currentLang = settings.language
                    val deviceLang = java.util.Locale.getDefault().language
                    val effectiveLang = if (currentLang.isEmpty()) {
                        if (deviceLang == "ta") "ta" else "en"
                    } else currentLang
                    
                    val languagePos = if (effectiveLang == "ta") 1 else 0
                    val languageOptions = arrayOf(getString(R.string.settings_lang_en), getString(R.string.settings_lang_ta))
                    binding.spinnerLanguage.setText(languageOptions[languagePos], false)
                    
                    binding.switchDynamicColor.isChecked = settings.useDynamicColor
                    
                    binding.switchNotifyTasks.isChecked = settings.notifyTasks
                    binding.switchNotifyCalendar.isChecked = settings.notifyCalendar
                    binding.switchNotifyDailySummary.isChecked = settings.notifyDailySummary
                    binding.switchNotifyAiSuggestions.isChecked = settings.notifyAiSuggestions
                    binding.switchNotifyWeeklyReport.isChecked = settings.notifyWeeklyReport
                    
                    val aiOptions = arrayOf(getString(R.string.settings_ai_general), getString(R.string.settings_ai_task), getString(R.string.settings_ai_knowledge), getString(R.string.settings_ai_study))
                    val aiPos = when(settings.aiDefaultMode) {
                        AiMode.GENERAL -> 0
                        AiMode.TASK -> 1
                        AiMode.KNOWLEDGE -> 2
                        AiMode.STUDY -> 3
                    }
                    binding.spinnerAiMode.setText(aiOptions[aiPos], false)
                    
                    val priorityOptions = arrayOf(getString(R.string.settings_priority_low), getString(R.string.settings_priority_medium), getString(R.string.settings_priority_high))
                    val priorityPos = if (settings.defaultTaskPriority in 0..2) settings.defaultTaskPriority else 1
                    binding.spinnerPriority.setText(priorityOptions[priorityPos], false)
                    
                    val studyOptions = arrayOf("15m", "25m", "45m", "60m")
                    val studyPos = when(settings.defaultStudyDuration) {
                        15 -> 0
                        25 -> 1
                        45 -> 2
                        60 -> 3
                        else -> 1
                    }
                    binding.spinnerStudyDuration.setText(studyOptions[studyPos], false)
                    
                    val dayOptions = arrayOf(getString(R.string.settings_day_sunday), getString(R.string.settings_day_monday))
                    val dayPos = if (settings.firstDayOfWeek == 1) 0 else 1
                    binding.spinnerFirstDay.setText(dayOptions[dayPos], false)
                    
                    // Update Smart Inbox UI
                    when (settings.gmailConnectionState) {
                        GmailConnectionState.CONNECTED -> {
                            binding.tvGmailStatus.text = getString(R.string.ui_connected)
                            binding.tvGmailAccount.visibility = View.VISIBLE
                            binding.tvGmailAccount.text = settings.connectedGmailAccount ?: ""
                            binding.btnConnectGmail.text = getString(R.string.ui_disconnect_gmail)
                            binding.btnConnectGmail.isEnabled = true
                            binding.btnOpenSmartInbox.visibility = View.VISIBLE
                        }
                        GmailConnectionState.CONNECTING -> {
                            binding.tvGmailStatus.text = getString(R.string.ui_connecting)
                            binding.tvGmailAccount.visibility = View.GONE
                            binding.btnConnectGmail.text = getString(R.string.ui_connecting)
                            binding.btnConnectGmail.isEnabled = false
                            binding.btnOpenSmartInbox.visibility = View.GONE
                        }
                        else -> {
                            binding.tvGmailStatus.text = getString(R.string.ui_not_connected)
                            binding.tvGmailAccount.visibility = View.GONE
                            binding.btnConnectGmail.text = getString(R.string.ui_connect_gmail)
                            binding.btnConnectGmail.isEnabled = true
                            binding.btnOpenSmartInbox.visibility = View.GONE
                        }
                    }
                    
                    isInitializing = false
                }
            }
        }
    }

    private fun observeAiUsage() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                aiSettingsViewModel.usageState.collectLatest { usage ->
                    if (usage != null) {
                        val aiMax = AiUsageRepository.AI_ASSISTANT_DAILY_LIMIT
                        val docMax = AiUsageRepository.DOCUMENT_ANALYSIS_DAILY_LIMIT
                        
                        binding.tvAiAssistantUsage.text = getString(R.string.settings_ai_usage, aiMax - usage.aiAssistantRequests, aiMax)
                        binding.progressAiAssistant.max = aiMax
                        binding.progressAiAssistant.progress = usage.aiAssistantRequests
                        
                        binding.tvDocAnalysisUsage.text = getString(R.string.settings_ai_usage, docMax - usage.documentAnalyses, docMax)
                        binding.progressDocAnalysis.max = docMax
                        binding.progressDocAnalysis.progress = usage.documentAnalyses
                    }
                }
            }
        }
    }

    private fun observeDeleteState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteState.collect { state ->
                    when (state) {
                        is DeleteAccountState.Idle -> {
                            hideLoading()
                        }
                        is DeleteAccountState.Loading -> {
                            showLoading("Deleting your account and data... Please wait.")
                        }
                        is DeleteAccountState.RequiresReauth -> {
                            hideLoading()
                            if (state.providerId == "google.com") {
                                promptGoogleReauth()
                            } else {
                                promptPasswordReauth(state.email)
                            }
                        }
                        is DeleteAccountState.Error -> {
                            hideLoading()
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Account Deletion Failed")
                                .setMessage(state.message)
                                .setPositiveButton("OK") { _, _ ->
                                    viewModel.resetDeleteState()
                                }
                                .show()
                        }
                        is DeleteAccountState.Success -> {
                            hideLoading()
                            Toast.makeText(requireContext(), getString(R.string.settings_account_deleted), Toast.LENGTH_SHORT).show()
                            viewModel.resetDeleteState()
                            try {
                                findNavController().navigate(R.id.action_settingsFragment_to_loginFragment)
                            } catch (e: Exception) {
                                val navOptions = androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.nav_graph, true)
                                    .build()
                                findNavController().navigate(R.id.loginFragment, null, navOptions)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showLoading(message: String) {
        if (progressDialog?.isShowing == true) return
        val progressBar = android.widget.ProgressBar(requireContext()).apply {
            setPadding(0, 32, 0, 32)
        }
        progressDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_delete_account))
            .setMessage(message)
            .setView(progressBar)
            .setCancelable(false)
            .show()
    }

    private fun hideLoading() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun promptGoogleReauth() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Verify Identity")
            .setMessage("For your security, deleting your account requires recent authentication. Please sign in with Google to continue.")
            .setPositiveButton("Sign In") { _, _ ->
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build()
                val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
                googleReauthLauncher.launch(googleSignInClient.signInIntent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                viewModel.resetDeleteState()
            }
            .setCancelable(false)
            .show()
    }

    private fun promptPasswordReauth(email: String) {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
        }

        val inputLayout = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        inputLayout.addView(input)
        container.addView(inputLayout)

        val targetEmail = email.ifBlank { "your account" }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Verify Identity")
            .setMessage("For your security, deleting your account requires recent authentication. Please enter your password for $targetEmail to continue.")
            .setView(container)
            .setPositiveButton("Confirm & Delete") { _, _ ->
                val password = input.text?.toString() ?: ""
                if (password.isNotBlank()) {
                    viewModel.reauthenticateWithPassword(password)
                } else {
                    Toast.makeText(requireContext(), "Password cannot be empty.", Toast.LENGTH_SHORT).show()
                    viewModel.resetDeleteState()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                viewModel.resetDeleteState()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        hideLoading()
        super.onDestroyView()
        _binding = null
    }
}

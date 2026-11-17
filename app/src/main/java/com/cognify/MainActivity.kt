package com.cognify

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cognify.databinding.ActivityMainBinding
import com.cognify.util.NetworkMonitor
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.cognify.data.repository.SettingsRepository
import com.cognify.data.repository.ThemeMode

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // We need to apply dynamic colors before super.onCreate
        // But to do it conditionally, we need to know the preference.
        // It's usually better to apply it conditionally globally or just apply if available.
        DynamicColors.applyToActivityIfAvailable(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            networkMonitor.isOnline.collectLatest { isOnline ->
                binding.tvOfflineBanner.visibility = if (isOnline) View.GONE else View.VISIBLE
            }
        }
        
        lifecycleScope.launch {
            settingsRepository.appSettingsFlow.collectLatest { settings ->
                val mode = when (settings.themeMode) {
                    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (AppCompatDelegate.getDefaultNightMode() != mode) {
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
            }
        }
    }
}
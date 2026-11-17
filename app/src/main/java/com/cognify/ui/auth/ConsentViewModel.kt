package com.cognify.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.repository.SettingsRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isConsentAccepted = MutableLiveData(false)
    val isConsentAccepted: LiveData<Boolean> = _isConsentAccepted

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _consentSaved = MutableLiveData(false)
    val consentSaved: LiveData<Boolean> = _consentSaved
    
    private val _logoutRequired = MutableLiveData(false)
    val logoutRequired: LiveData<Boolean> = _logoutRequired

    init {
        checkConsent()
    }

    private fun checkConsent() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Timber.e("FirebaseAuth.currentUser is unexpectedly null in ConsentViewModel.")
            _logoutRequired.value = true
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            try {
                val accepted = settingsRepository.isConsentAccepted(user.uid).first()
                if (accepted) {
                    _consentSaved.value = true
                }
                _isConsentAccepted.value = accepted
            } catch (e: Exception) {
                Timber.e(e, "Failed to read consent state.")
                _error.value = "Failed to read consent state."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun acceptConsent() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Timber.e("FirebaseAuth.currentUser is unexpectedly null during acceptConsent.")
            _logoutRequired.value = true
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                settingsRepository.setConsentAccepted(user.uid)
                _consentSaved.value = true
            } catch (e: Exception) {
                Timber.e(e, "Failed to save consent.")
                _error.value = "Failed to save consent. Please try again."
                _isLoading.value = false
            }
        }
    }
}

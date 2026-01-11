package com.tutpro.baresip

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic

class ViewModel: ViewModel() {

    data class DialerState(
        val callUri: MutableState<String> = mutableStateOf(""),
        val callUriEnabled: MutableState<Boolean> = mutableStateOf(true),
        val callUriLabel: MutableState<String> = mutableStateOf(""),
        val showSuggestions: MutableState<Boolean> = mutableStateOf(false),
        val showCallButton: MutableState<Boolean> = mutableStateOf(true),
        val callButtonEnabled: MutableState<Boolean> = mutableStateOf(true),
    )

    val dialerState = DialerState()

    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls = _calls.asStateFlow()

    private val _selectedAor = MutableStateFlow("")
    val selectedAor = _selectedAor.asStateFlow()

    private val _accountUpdate = MutableStateFlow(0)
    val accountUpdate = _accountUpdate.asStateFlow()

    private val _micIcon = MutableStateFlow(Icons.Filled.Mic)
    val micIcon = _micIcon.asStateFlow()

    private val _isDialpadVisible = MutableStateFlow(false)
    val isDialpadVisible = _isDialpadVisible.asStateFlow()

    private val _showKeyboard = MutableStateFlow(0)
    val showKeyboard = _showKeyboard.asStateFlow()

    private val _hideKeyboard = MutableStateFlow(0)
    val hideKeyboard = _hideKeyboard.asStateFlow()

    fun onNewMessageReceived(aor: String, peerUri: String) {
        val acc = Account.ofAor(aor)
        if (acc != null) {
            acc.unreadMessages = true
            triggerAccountUpdate()
        }
    }

    fun updateCalls(calls: List<Call>) {
        _calls.value = calls
    }

    fun updateSelectedAor(aor: String) {
        _selectedAor.value = aor
    }

    fun triggerAccountUpdate() {
        _accountUpdate.value = _accountUpdate.value + 1
    }

    fun updateMicIcon(icon: ImageVector) {
        _micIcon.value = icon
    }

    fun toggleDialpadVisibility() {
        _isDialpadVisible.value = !_isDialpadVisible.value
    }

    fun requestShowKeyboard() {
        _showKeyboard.value = _showKeyboard.value + 1
    }

    fun requestHideKeyboard() {
        _hideKeyboard.value = _hideKeyboard.value + 1
    }

    fun navigateToHome() {
        // This function can be used to navigate to the main screen
    }

    fun navigateToCalls(aor: String) {
        // This function can be used to navigate to the calls screen
    }

}
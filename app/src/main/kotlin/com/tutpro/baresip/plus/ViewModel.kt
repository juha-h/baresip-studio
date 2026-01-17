package com.tutpro.baresip.plus

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import kotlinx.coroutines.launch

// Sealed class for type-safe navigation events
sealed class NavigationCommand {
    object NavigateToHome : NavigationCommand()
    data class NavigateToCalls(val aor: String) : NavigationCommand()
    data class NavigateToChat(val aor: String, val peerUri: String) : NavigationCommand()
}

class ViewModel: ViewModel() {

    // A map to store message drafts. Key is "aor:peerUri"
    private val messageDrafts = mutableMapOf<String, String>()

    fun getAorPeerMessage(aor: String, peerUri: String): String {
        return messageDrafts["$aor:$peerUri"] ?: ""
    }

    fun updateAorPeerMessage(aor: String, peerUri: String, message: String) {
        val key = "$aor:$peerUri"
        if (message.isEmpty()) {
            messageDrafts.remove(key)
        } else {
            messageDrafts[key] = message
        }
    }

    data class DialerState(
        val callUri: MutableState<String> = mutableStateOf(""),
        val callUriEnabled: MutableState<Boolean> = mutableStateOf(true),
        val callUriLabel: MutableState<String> = mutableStateOf(""),
        val showSuggestions: MutableState<Boolean> = mutableStateOf(false),
        val showCallButton: MutableState<Boolean> = mutableStateOf(true),
        val showCallVideoButton: MutableState<Boolean> = mutableStateOf(true),
        val showCallConferenceButton: MutableState<Boolean> = mutableStateOf(true),
        val callButtonsEnabled: MutableState<Boolean> = mutableStateOf(true),
        val callVideoButtonEnabled: MutableState<Boolean> = mutableStateOf(true),
        val conferenceCall: MutableState<Boolean> = mutableStateOf(false),
        val videoIcon: MutableState<Video> = mutableStateOf(Video.NONE)
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

    private val _navigationCommand = MutableSharedFlow<NavigationCommand>()
    val navigationCommand = _navigationCommand.asSharedFlow()

    private var _selectedCallRow: CallRow? = null

    fun selectCallRow(callRow: CallRow) {
        _selectedCallRow = callRow
    }

    fun consumeSelectedCallRow(): CallRow? {
        val callRow = _selectedCallRow
        _selectedCallRow = null
        return callRow
    }

    fun onNewMessageReceived(aor: String, peerUri: String) {
        viewModelScope.launch {
            _navigationCommand.emit(NavigationCommand.NavigateToChat(aor, peerUri))
        }
    }

    fun updateCalls(calls: List<Call>) {
        _calls.value = calls
    }

    fun updateSelectedAor(aor: String) {
        _selectedAor.value = aor
    }

    fun triggerAccountUpdate() {
        _accountUpdate.value += 1
    }

    fun updateMicIcon(icon: ImageVector) {
        _micIcon.value = icon
    }

    fun toggleDialpadVisibility() {
        _isDialpadVisible.value = !_isDialpadVisible.value
    }

    fun requestShowKeyboard() {
        _showKeyboard.value += 1
    }

    fun requestHideKeyboard() {
        _hideKeyboard.value += 1
    }

    fun navigateToHome() {
        viewModelScope.launch {
            _navigationCommand.emit(NavigationCommand.NavigateToHome)
        }
    }

    fun navigateToCalls(aor: String) {
        viewModelScope.launch {
            _navigationCommand.emit(NavigationCommand.NavigateToCalls(aor))
        }
    }

}

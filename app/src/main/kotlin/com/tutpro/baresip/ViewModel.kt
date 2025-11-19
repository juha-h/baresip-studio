package com.tutpro.baresip

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NavigationCommand {
    data class NavigateToChat(val aor: String, val peer: String?) : NavigationCommand()
    data class NavigateToCalls(val aor: String) : NavigationCommand()
    object NavigateToHome: NavigationCommand()
    // Add other navigation commands as needed
}

data class AorPeer(val aor: String, val peer: String)

class ViewModel(application: Application) : AndroidViewModel(application) {

    private val _selectedAor = MutableStateFlow("")
    val selectedAor: StateFlow<String> = _selectedAor.asStateFlow()

    fun updateSelectedAor(newValue: String) {
        _selectedAor.value = newValue
    }

    private val _accountUpdate = MutableStateFlow(0)
    val accountUpdate: StateFlow<Int> = _accountUpdate

    fun triggerAccountUpdate() {
        _accountUpdate.value++
    }

    private val _aorPeerMessage = MutableStateFlow(mutableMapOf<AorPeer, String>())

    fun updateAorPeerMessage(aor: String, peerUri: String, message: String) {
        val key = AorPeer(aor, peerUri)
        if (message == "")
            _aorPeerMessage.value.remove(key)
        else
            _aorPeerMessage.value[key] = message
    }

    fun getAorPeerMessage(aor: String, peerUri: String): String {
        val key = AorPeer(aor, peerUri)
        return _aorPeerMessage.value.getOrDefault(key, "")
    }

    private val _showKeyboard = mutableIntStateOf(0)
    val showKeyboard: State<Int> = _showKeyboard

    fun requestShowKeyboard() {
        _showKeyboard.intValue++
    }

    private val _hideKeyboard = mutableIntStateOf(0)
    val hideKeyboard: State<Int> = _hideKeyboard

    fun requestHideKeyboard() {
        _hideKeyboard.intValue++
    }

    private val _micIcon = MutableStateFlow(Icons.Filled.Mic)
    val micIcon: StateFlow<ImageVector> = _micIcon.asStateFlow()

    fun updateMicIcon(icon: ImageVector) {
        _micIcon.value = icon
    }

    private val _isDialpadVisible = MutableStateFlow(false)
    val isDialpadVisible: StateFlow<Boolean> = _isDialpadVisible

    fun toggleDialpadVisibility() {
        _isDialpadVisible.value = !_isDialpadVisible.value
    }

    private val _selectedCallRow = MutableStateFlow<CallRow?>(null)

    fun selectCallRow(callRow: CallRow) {
        _selectedCallRow.value = callRow
    }

    fun consumeSelectedCallRow(): CallRow? {
        val callRow = _selectedCallRow.value
        _selectedCallRow.value = null
        return callRow
    }

    private val _navigationCommand = MutableSharedFlow<NavigationCommand>()
    val navigationCommand = _navigationCommand.asSharedFlow()

    fun onNewMessageReceived(aor: String, peer: String) {
        viewModelScope.launch {
            _navigationCommand.emit(NavigationCommand.NavigateToChat(aor, peer))
        }
    }

    fun navigateToCalls(aor: String) {
        viewModelScope.launch {
            _navigationCommand.emit(NavigationCommand.NavigateToCalls(aor))
        }
    }

    fun navigateToHome() {
        viewModelScope.launch {
            _navigationCommand.emit(NavigationCommand.NavigateToHome)
        }
    }
}

package com.tutpro.baresip

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
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

    private val _speakerIcon = MutableStateFlow(R.drawable.speaker_off)
    val speakerIcon: StateFlow<Int> = _speakerIcon.asStateFlow()

    fun updateSpeakerIcon(iconResId: Int) {
        _speakerIcon.value = iconResId
    }

    private val _micIcon = MutableStateFlow(R.drawable.mic_on)
    val micIcon: StateFlow<Int> = _micIcon.asStateFlow()

    fun updateMicIcon(iconResId: Int) {
        _micIcon.value = iconResId
    }

    private val _vmIcon = MutableStateFlow(R.drawable.voicemail)
    val vmIcon: StateFlow<Int> = _vmIcon.asStateFlow()

    fun updateVmIcon(newIcon: Int) {
        _vmIcon.value = newIcon
    }

    private val _showVmIcon = MutableStateFlow(false)
    val showVmIcon: StateFlow<Boolean> = _showVmIcon.asStateFlow()

    fun updateShowVmIcon(show: Boolean) {
        _showVmIcon.value = show
    }

    private val _messagesIcon = MutableStateFlow(R.drawable.messages)
    val messagesIcon: StateFlow<Int> = _messagesIcon.asStateFlow()

    fun updateMessagesIcon(newIcon: Int) {
        _messagesIcon.value = newIcon
    }

    private val _callsIcon = MutableStateFlow(R.drawable.calls)
    val callsIcon: StateFlow<Int> = _callsIcon.asStateFlow()

    fun updateCallsIcon(newIcon: Int) {
        _callsIcon.value = newIcon
    }

    private val _dialpadIcon = MutableStateFlow(R.drawable.dialpad_off)
    val dialpadIcon: StateFlow<Int> = _dialpadIcon.asStateFlow()

    fun updateDialpadIcon(newIcon: Int) {
        _dialpadIcon.value = newIcon
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

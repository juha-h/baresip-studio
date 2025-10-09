package com.tutpro.baresip

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.State
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _aorPeerMessage = MutableStateFlow(mutableMapOf<AorPeer, TextFieldValue>())
    val aorPeerMessage: StateFlow<Map<AorPeer, TextFieldValue>> = _aorPeerMessage.asStateFlow()

    fun updateAorPeerMessage(aor: String, peerUri: String, value: TextFieldValue) {
        val key = AorPeer(aor, peerUri)
        val newMap = _aorPeerMessage.value.toMutableMap()

        if (value.text.isEmpty())
            newMap.remove(key)
        else
            newMap[key] = value
        _aorPeerMessage.value = newMap
    }

    private val _showKeyboard = mutableStateOf(0)
    val showKeyboard: State<Int> = _showKeyboard

    fun requestShowKeyboard() {
        _showKeyboard.value++
    }

    private val _hideKeyboard = mutableStateOf(0)
    val hideKeyboard: State<Int> = _hideKeyboard

    fun requestHideKeyboard() {
        _hideKeyboard.value++
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

    private val _audioSettingsResult = mutableStateOf<Boolean?>(null)
    val audioSettingsResult: State<Boolean?> get() = _audioSettingsResult

    fun setAudioSettingsResult(result: Boolean) {
        _audioSettingsResult.value = result
    }

    fun clearAudioSettingsResult() {
        _audioSettingsResult.value = null
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

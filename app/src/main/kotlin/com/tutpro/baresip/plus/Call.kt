package com.tutpro.baresip.plus

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import java.util.*

class Call(val callp: Long, val ua: UserAgent, val peerUri: String, val dir: String, initialStatus: String) {

    var status: MutableState<String> = mutableStateOf(initialStatus)

    var onhold = false
    var held = false
    val terminated = mutableStateOf(false)
    var conferenceCall = false
    var videoCall = false
    var onHoldCall: Call? = null
    var newCall: Call? = null
    var rejected = false  // Incoming rejected by user or outgoing fails but not due to 408 or 480
    var security = R.color.colorTrafficRed
    var zid = ""
    var startTime: GregorianCalendar? = null  // Set when call is established
    var referTo = ""
    var videoRequest = 0
    var dumpfiles = arrayOf("", "")

    fun diverterUri(): String {
        return Api.call_diverter_uri(callp)
    }

    // UI state properties
    val callUri: MutableState<String> = mutableStateOf("")
    val callUriEnabled: MutableState<Boolean> = mutableStateOf(true)
    val callUriLabel: MutableState<String> = mutableStateOf("")
    val callUri2: MutableState<String> = mutableStateOf("")
    val callUriLabel2: MutableState<String> = mutableStateOf("")
    val securityIconTint: MutableState<Int> = mutableIntStateOf(-1)
    val showCallTimer: MutableState<Boolean> = mutableStateOf(false)
    var callDuration: Int = 0
    val showSuggestions: MutableState<Boolean> = mutableStateOf(false)
    val showCallButton: MutableState<Boolean> = mutableStateOf(true)
    val showCallVideoButton: MutableState<Boolean> = mutableStateOf(true)
    val videoIcon: MutableState<Video> = mutableStateOf(Video.NONE)
    val showCancelButton: MutableState<Boolean> = mutableStateOf(false)
    val showAnswerRejectButtons: MutableState<Boolean> = mutableStateOf(false)
    val showHangupButton: MutableState<Boolean> = mutableStateOf(false)
    val showOnHoldNotice: MutableState<Boolean> = mutableStateOf(false)
    val callOnHold: MutableState<Boolean> = mutableStateOf(false)
    val transferButtonEnabled: MutableState<Boolean> = mutableStateOf(false)
    val callTransfer: MutableState<Boolean> = mutableStateOf(false)
    val dtmfText: MutableState<String> = mutableStateOf("")
    val dtmfEnabled: MutableState<Boolean> = mutableStateOf(false)
    val focusDtmf: MutableState<Boolean> = mutableStateOf(false)

    fun add() {
        BaresipService.calls.add(this)
    }

    fun remove() {
        BaresipService.calls.remove(this)
    }

    fun connect(uri: String): Boolean {
        return Api.call_connect(callp, uri) == 0
    }

    fun startVideoDisplay(): Int {
        return Api.call_start_video_display(callp)
    }

    fun stopVideoDisplay() {
        Api.call_stop_video_display(callp)
    }

    fun setVideoSource(front: Boolean): Int {
        return Api.call_set_video_source(callp, front)
    }

    fun hold(): Boolean {
        if (onhold) return true
        if (Api.call_hold(callp, true)) {
            onhold = true
            callOnHold.value = true
            showOnHoldNotice.value = false
            ConnectionService.connections[callp]?.setOnHold()
            return true
        }
        return false
    }

    fun resume(): Boolean {
        if (!onhold && !held) return true
        // 1. Hold other calls first
        for (c in BaresipService.calls) {
            if (c.callp != this.callp && !c.onhold && !c.held) {
                Log.d("Baresip", "Auto-holding active call ${c.callp}")
                c.hold()
            }
        }
        val connection = ConnectionService.connections[callp]
        // 2. SIP Signaling
        if (Api.call_hold(callp, false)) {
            onhold = false
            callOnHold.value = false
            showOnHoldNotice.value = false
            // 3. Telecom Sync
            connection?.setAddress("sip:$peerUri".toUri(), android.telecom.TelecomManager.PRESENTATION_ALLOWED)
            connection?.setActive()
            return true
        }
        return false
    }

    fun transfer(uri: String): Boolean {
        if (!onhold) hold()
        Log.d(TAG, "Transferring call $callp to $uri")
        return Api.call_transfer(callp, uri) == 0
    }

    fun executeTransfer(): Boolean {
        return if (onHoldCall != null) {
            if (Api.call_hold(callp, true))
                Api.call_replace_transfer(onHoldCall!!.callp, callp)
            else
                false
        } else
            false
    }

    fun sendDigit(digit: Char): Int {
        return Api.call_send_digit(callp, digit)
    }

    fun notifySipfrag(code: Int, reason: String) {
        Api.call_notify_sipfrag(callp, code, reason)
    }

    fun hasVideo(): Boolean {
        return Api.call_has_video(callp)
    }

    fun videoEnabled(): Boolean {
        return Api.call_video_enabled(callp)
    }

    fun disableVideoStream(disable: Boolean) {
        Api.call_disable_video_stream(callp, disable)
    }

    fun setVideoDirection(vdir: Int) {
        Api.call_set_video_direction(callp, vdir)
    }

    fun setMediaDirection(adir: Int, vdir: Int) {
        Api.call_set_media_direction(callp, adir, vdir)
    }

    fun duration(): Int {
        return Api.call_duration(callp)
    }

    fun stats(stream: String): String {
        return Api.call_stats(callp, stream)
    }

    fun state(): Int {
        return Api.call_state(callp)
    }

    fun audioCodecs(): String {
        return Api.call_audio_codecs(callp)
    }

    fun videoCodecs(): String {
        return Api.call_video_codecs(callp)
    }

    fun replaces(): Boolean {
        return Api.call_replaces(callp)
    }

    init {
        if (ua.account.mediaEnc != "") security = R.color.colorTrafficRed
    }

    fun destroy() {
        Api.call_destroy(callp)
    }

    companion object {

        fun calls(): ArrayList<Call> {
            return BaresipService.calls
        }

        fun ofCallp(callp: Long): Call? {
            for (c in BaresipService.calls)
                if (c.callp == callp) return c
            return null
        }

        fun call(status: String): Call? {
            for (c in BaresipService.calls)
                if (c.status.value == status) return c
            return null
        }

        fun inCall(): Boolean {
            return BaresipService.calls.isNotEmpty()
        }

        fun isAnyCallActive(ctx: Context): Boolean {
            // Check if there exist SIP calls that are not onhold or held
            if (BaresipService.calls.any { !it.onhold && !it.held }) return true
            // MODE_IN_CALL indicates a PSTN call is active
            return Utils.isAudioMode(ctx, AudioManager.MODE_IN_CALL)
        }
    }
}

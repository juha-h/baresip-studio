package com.tutpro.baresip

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
    var onHoldCall: Call? = null
    var newCall: Call? = null
    var rejected = false  // Incoming rejected by user or outgoing fails but not due to 408 or 480
    var security = R.color.colorTrafficRed
    var zid = ""
    var startTime: GregorianCalendar? = null  // Set when call is established
    var referTo = ""
    var dumpfiles = arrayOf("", "")

    // UI state properties
    val callUri: MutableState<String> = mutableStateOf("")
    val callUriEnabled: MutableState<Boolean> = mutableStateOf(true)
    val callUriLabel: MutableState<String> = mutableStateOf("")
    val securityIconTint: MutableState<Int> = mutableIntStateOf(-1)
    val showCallTimer: MutableState<Boolean> = mutableStateOf(false)
    var callDuration: Int = 0
    val showSuggestions: MutableState<Boolean> = mutableStateOf(false)
    val showCallButton: MutableState<Boolean> = mutableStateOf(true)
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

    fun hold(): Boolean {
        if (onhold) return true
        val connection = ConnectionService.connections[callp]
        if (connection != null) {
            connection.setOnHold()
            return true
        }
        // Fallback if no Telecom connection exists
        if (Api.call_hold(callp, true)) {
            onhold = true
            callOnHold.value = true
            showOnHoldNotice.value = true
        }
        return onhold
    }

    fun resume(): Boolean {
        if (!onhold) return true
        val connection = ConnectionService.connections[callp]
        if (connection != null) {
            connection.setAddress(
                "sip:$peerUri".toUri(),
                android.telecom.TelecomManager.PRESENTATION_ALLOWED
            )
            connection.setActive()
            return true
        }
        // Fallback if no Telecom connection exists
        if (Api.call_hold(callp, false)) {
            onhold = false
            callOnHold.value = false
            showOnHoldNotice.value = false
        }
        return !onhold
    }

    fun transfer(uri: String): Boolean {
        if (!Api.call_hold(callp, true))
            return false
        onhold = true
        referTo = uri
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

    fun replaces(): Boolean {
        return Api.call_replaces(callp)
    }

    fun diverterUri(): String {
        return Api.call_diverter_uri(callp)
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
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return am.mode == AudioManager.MODE_IN_CALL
        }
    }
}

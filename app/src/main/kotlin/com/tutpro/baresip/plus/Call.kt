package com.tutpro.baresip.plus

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import java.util.*

open class Call(val callp: Long, val ua: UserAgent, val peerUri: String, val dir: String, initialStatus: String) {

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
        synchronized(BaresipService.calls) {
            BaresipService.calls.add(this)
        }
    }

    fun remove() {
        synchronized(BaresipService.calls) {
            BaresipService.calls.remove(this)
        }
    }

    open fun connect(uri: String): Boolean {
        return Api.call_connect(callp, uri) == 0
    }

    open fun hold(): Boolean {
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

    open fun startVideoDisplay(): Int {
        return Api.call_start_video_display(callp)
    }

    open fun stopVideoDisplay() {
        Api.call_stop_video_display(callp)
    }

    open fun setVideoSource(front: Boolean): Int {
        return Api.call_set_video_source(callp, front)
    }

    open fun resume(): Boolean {
        if (!onhold && !held) return true
        // 1. Hold other calls first
        for (c in BaresipService.calls)
            if (c.callp != this.callp && !c.onhold && !c.held) {
                Log.d("Baresip", "Auto-holding active call ${c.callp}")
                c.hold()
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

    open fun transfer(uri: String): Boolean {
        if (!onhold) hold()
        Log.d(TAG, "Transferring call $callp to $uri")
        return Api.call_transfer(callp, uri) == 0
    }

    open fun executeTransfer(): Boolean {
        return if (onHoldCall != null) {
            if (Api.call_hold(callp, true))
                Api.call_replace_transfer(onHoldCall!!.callp, callp)
            else
                false
        } else
            false
    }

    open fun sendDigit(digit: Char): Int {
        return Api.call_send_digit(callp, digit)
    }

    open fun notifySipfrag(code: Int, reason: String) {
        Api.call_notify_sipfrag(callp, code, reason)
    }

    open fun duration(): Int {
        return Api.call_duration(callp)
    }

    open fun stats(stream: String): String {
        return Api.call_stats(callp, stream)
    }

    open fun state(): Int {
        return Api.call_state(callp)
    }

    open fun audioCodecs(): String {
        return Api.call_audio_codecs(callp)
    }

    open fun videoCodecs(): String {
        return Api.call_video_codecs(callp)
    }

    open fun replaces(): Boolean {
        return Api.call_replaces(callp)
    }

    open fun diverterUri(): String {
        return Api.call_diverter_uri(callp)
    }

    open fun hasVideo(): Boolean {
        return Api.call_has_video(callp)
    }

    open fun videoEnabled(): Boolean {
        return Api.call_video_enabled(callp)
    }

    open fun disableVideoStream(disable: Boolean) {
        Api.call_disable_video_stream(callp, disable)
    }

    open fun setVideoDirection(vdir: Int) {
        Api.call_set_video_direction(callp, vdir)
    }

    open fun setMediaDirection(adir: Int, vdir: Int) {
        Api.call_set_media_direction(callp, adir, vdir)
    }

    init {
        if (ua.account.mediaEnc != "") security = R.color.colorTrafficRed
    }

    open fun destroy() {
        Api.call_destroy(callp)
    }

    open fun hangup(code: Int, reason: String) {
        val connection = ConnectionService.connections[callp]
        if (connection != null)
            connection.onDisconnect()
        else
            Api.ua_hangup(ua.uap, callp, code, reason)
    }

    open fun answer(video: Boolean = false) {
        Api.ua_answer(ua.uap, callp, if (video) Api.VIDMODE_ON else Api.VIDMODE_OFF)
    }

    open fun reject() {
        this.rejected = true
        hangup(486, "Busy Here")
    }

    class ExternalCall(
        val telecomCall: android.telecom.Call,
        ua: UserAgent,
        peerUri: String,
        dir: String,
        initialStatus: String
    ) : Call(telecomCall.hashCode().toLong(), ua, peerUri, dir, initialStatus) {

        override fun connect(uri: String): Boolean {
            telecomCall.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            return true
        }

        override fun answer(video: Boolean) {
            telecomCall.answer(if (video) android.telecom.VideoProfile.STATE_BIDIRECTIONAL else android.telecom.VideoProfile.STATE_AUDIO_ONLY)
        }

        override fun hold(): Boolean {
            telecomCall.hold()
            onhold = true
            callOnHold.value = true
            return true
        }

        override fun resume(): Boolean {
            telecomCall.unhold()
            onhold = false
            callOnHold.value = false
            return true
        }

        override fun hangup(code: Int, reason: String) {
            telecomCall.disconnect()
        }

        override fun reject() {
            this.rejected = true
            telecomCall.disconnect()
        }

        override fun destroy() {
            telecomCall.disconnect()
        }

        override fun sendDigit(digit: Char): Int {
            telecomCall.playDtmfTone(digit)
            telecomCall.stopDtmfTone()
            return 0
        }

        override fun diverterUri(): String = ""

        override fun duration(): Int = 0
        override fun stats(stream: String): String = ""
        override fun state(): Int = 0
        override fun audioCodecs(): String = "PSTN"

        override fun hasVideo(): Boolean = false
        override fun videoEnabled(): Boolean = false
        override fun startVideoDisplay(): Int = 0
        override fun stopVideoDisplay() {}
        override fun setVideoSource(front: Boolean): Int = 0
        override fun transfer(uri: String): Boolean = false
        override fun executeTransfer(): Boolean = false
        override fun notifySipfrag(code: Int, reason: String) {}
        override fun videoCodecs(): String = ""
        override fun replaces(): Boolean = false
        override fun disableVideoStream(disable: Boolean) {}
        override fun setVideoDirection(vdir: Int) {}
        override fun setMediaDirection(adir: Int, vdir: Int) {}
    }

    companion object {

        fun calls(): ArrayList<Call> {
            synchronized(BaresipService.calls) {
                return ArrayList(BaresipService.calls)
            }
        }

        fun ofCallp(callp: Long): Call? {
            synchronized(BaresipService.calls) {
                for (c in BaresipService.calls)
                    if (c.callp == callp) return c
                return null
            }
        }

        fun call(status: String): Call? {
            synchronized(BaresipService.calls) {
                for (c in BaresipService.calls)
                    if (c.status.value == status) return c
                return null
            }
        }

        fun inCall(): Boolean {
            synchronized(BaresipService.calls) {
                return BaresipService.calls.isNotEmpty()
            }
        }

        fun hasTelecomCall(): Boolean {
            synchronized(BaresipService.calls) {
                return BaresipService.calls.any {
                    it is ExternalCall || ConnectionService.connections.containsKey(it.callp)
                } || ConnectionService.pendingOutgoingConnection != null
            }
        }

        fun isAnyCallActive(ctx: Context): Boolean {
            synchronized(BaresipService.calls) {
                // Check if there exist SIP calls that are not onhold or held
                if (BaresipService.calls.any { !it.onhold && !it.held }) return true
                // MODE_IN_CALL indicates a PSTN call is active
                return Utils.isAudioMode(ctx, AudioManager.MODE_IN_CALL)
            }
        }
    }
}

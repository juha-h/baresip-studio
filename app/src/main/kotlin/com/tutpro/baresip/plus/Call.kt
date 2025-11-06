package com.tutpro.baresip.plus

import java.util.*

class Call(val callp: Long, val ua: UserAgent, val peerUri: String, val dir: String, var status: String) {

    var onhold = false
    var held = false
    var onHoldCall: Call? = null
    var newCall: Call? = null
    var rejected = false  // Incoming rejected by user or outgoing fails but not due to 408 or 480
    var security = R.drawable.unlocked
    var zid = ""
    var startTime: GregorianCalendar? = null  // Set when call is established
    var referTo = ""
    var videoRequest = 0
    var dumpfiles = arrayOf("", "")

    fun diverterUri(): String {
        return Api.call_diverter_uri(callp)
    }

    fun add() {
        BaresipService.calls.add(0, this)
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
        return Api.call_hold(callp, true) == 0
    }

    fun resume(): Boolean {
        return Api.call_hold(callp, false) == 0
    }

    fun transfer(uri: String): Boolean {
        val err = Api.call_hold(callp, true)
        if (err != 0)
            return false
        onhold = true
        referTo = uri
        return Api.call_transfer(callp, uri) == 0
    }

    fun executeTransfer(): Boolean {
        return if (onHoldCall != null) {
            if (Api.call_hold(callp, true) == 0)
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
            for (c in BaresipService.calls.reversed())
                if (c.status == status) return c
            return null
        }

        fun inCall(): Boolean {
            return BaresipService.calls.isNotEmpty()
        }

    }
}

package com.tutpro.baresip

import android.text.TextWatcher
import java.util.ArrayList

class Call(val callp: String, val ua: UserAgent, val peerURI: String, val dir: String,
           var status: String, val dtmfWatcher: TextWatcher?) {

    var onhold = false
    var security = 0
    var zid = ""
    var hasHistory = false
    var videoEnabled = false

    init {
        if (ua.account.mediaEnc != "") security = R.drawable.box_red
    }

    fun add() {
        BaresipService.calls.add(this)
    }

    fun remove() {
        BaresipService.calls.remove(this)
    }

    fun connect(uri: String): Int {
        return call_connect(callp, uri)
    }

    fun startAudio() {
        call_start_audio(callp)
    }

    fun stopAudio() {
        call_stop_audio(callp)
    }

    fun startVideo(): Int {
        return call_start_video(callp)
    }

    fun startVideoDisplay(): Int {
        return call_start_video_display(callp)
    }

    fun stopVideoDisplay() {
        call_stop_video_display(callp)
    }

    fun setVideoSource(front: Boolean): Int {
        return call_set_video_source(callp, front)
    }

    fun hold(): Int {
        return call_hold(callp)
    }

    fun unhold(): Int {
        return call_unhold(callp)
    }

    fun sendDigit(digit: Char): Int {
        return call_send_digit(callp, digit)
    }

    fun notifySipfrag(code: Int, reason: String) {
        call_notify_sipfrag(callp, code, reason)
    }

    fun hasVideo(): Boolean {
        return call_has_video(callp)
    }

    fun setVideo(enabled: Boolean): Int {
        val res = call_set_video(callp, enabled)
        if (res == 0)
            videoEnabled = enabled
        return res
    }

    fun disableVideoStream() {
        call_disable_video_stream(callp)
        videoEnabled = false
    }

    fun status(): String {
        return call_status(callp)
    }

    fun audioCodecs(): String {
        return call_audio_codecs(callp)
    }

    private external fun call_connect(callp: String, peer_uri: String): Int
    private external fun call_hold(callp: String): Int
    private external fun call_unhold(callp: String): Int
    private external fun call_send_digit(callp: String, digit: Char): Int
    private external fun call_notify_sipfrag(callp: String, code: Int, reason: String)
    private external fun call_start_audio(callp: String)
    private external fun call_stop_audio(callp: String)
    private external fun call_start_video(callp: String): Int
    private external fun call_start_video_display(callp: String): Int
    private external fun call_stop_video_display(callp: String)
    private external fun call_audio_codecs(callp: String): String
    private external fun call_status(callp: String): String
    private external fun call_has_video(callp: String): Boolean
    private external fun call_set_video(callp: String, enabled: Boolean): Int
    private external fun call_set_video_source(callp: String, front: Boolean): Int
    private external fun call_disable_video_stream(callp: String)

    external fun call_video_debug()

    companion object {

        fun calls(): ArrayList<Call> {
            return BaresipService.calls
        }

        fun uaCalls(ua: UserAgent, dir: String): ArrayList<Call> {
            val result = ArrayList<Call>()
            for (c in BaresipService.calls)
                if ((c.ua == ua) && ((dir == "") || c.dir == dir)) result.add(c)
            return result
        }

        fun ofCallp(callp: String): Call? {
            for (c in BaresipService.calls)
                if (c.callp == callp) return c
            return null
        }

        fun call(status: String): Call? {
            for (c in BaresipService.calls)
                if (c.status == status) return c
            return null
        }

    }
}

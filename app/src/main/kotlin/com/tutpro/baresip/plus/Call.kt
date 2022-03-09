package com.tutpro.baresip.plus

import android.text.TextWatcher
import java.util.*

class Call(val callp: Long, val ua: UserAgent, val peerUri: String, val dir: String,
           var status: String, val dtmfWatcher: TextWatcher?) {

    var onhold = false
    var held = false
    var rejected = false
    var security = 0
    var zid = ""
    var startTime: GregorianCalendar? = null  // Set when call is established
    var referTo = ""
    var videoRequest = 0

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
        return call_hold(callp, true)
    }

    fun resume(): Int {
        return call_hold(callp, false)
    }

    fun transfer(uri: String): Int {
        val err = call_hold(callp, true)
        if (err != 0)
            return err
        referTo = uri
        return call_transfer(callp, uri)
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

    fun videoEnabled(): Boolean {
        return call_video_enabled(callp)
    }

    fun disableVideoStream(disable: Boolean) {
        call_disable_video_stream(callp, disable)
    }

    fun setVideoDirection(vdir: Int) {
        call_set_video_direction(callp, vdir)
    }

    fun setMediaDirection(adir: Int, vdir: Int): Int {
        return call_set_media_direction(callp, adir, vdir)
    }

    fun duration(): Int {
        return call_duration(callp)
    }


    fun stats(stream: String): String {
        return call_stats(callp, stream)
    }

    fun audioCodecs(): String {
        return call_audio_codecs(callp)
    }

    fun videoCodecs(): String {
        return call_video_codecs(callp)
    }

    private external fun call_connect(callp: Long, peer_uri: String): Int
    private external fun call_hold(callp: Long, hold: Boolean): Int
    private external fun call_ismuted(callp: Long): Boolean
    private external fun call_transfer(callp: Long, peer_uri: String): Int
    private external fun call_send_digit(callp: Long, digit: Char): Int
    private external fun call_notify_sipfrag(callp: Long, code: Int, reason: String)
    private external fun call_start_video_display(callp: Long): Int
    private external fun call_stop_video_display(callp: Long)
    private external fun call_audio_codecs(callp: Long): String
    private external fun call_video_codecs(callp: Long): String
    private external fun call_duration(callp: Long): Int
    private external fun call_stats(callp: Long, stream: String): String
    private external fun call_has_video(callp: Long): Boolean
    private external fun call_set_video_source(callp: Long, front: Boolean): Int
    private external fun call_set_video_direction(callp: Long, dir: Int)
    private external fun call_set_media_direction(callp: Long, adir: Int, vdir: Int): Int
    private external fun call_disable_video_stream(callp: Long, disable: Boolean)
    private external fun call_video_enabled(callp: Long): Boolean

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

        fun ofCallp(callp: Long): Call? {
            for (c in BaresipService.calls)
                if (c.callp == callp) return c
            return null
        }

        fun call(status: String): Call? {
            for (c in BaresipService.calls)
                if (c.status == status) return c
            return null
        }

        fun isStatus(status: String): Boolean {
            for (call in BaresipService.calls)
                if (call.status == status)
                    return true
            return false
        }

    }
}

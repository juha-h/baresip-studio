package com.tutpro.baresip

import android.text.TextWatcher
import java.util.*

class Call(val callp: Long, val ua: UserAgent, val peerUri: String, val dir: String,
           var status: String, val dtmfWatcher: TextWatcher?) {

    var onhold = false
    var held = false
    var onHoldCall: Call? = null
    var newCall: Call? = null
    var rejected = false
    var security = 0
    var zid = ""
    var startTime: GregorianCalendar? = null  // Set when call is established
    var referTo = ""

    fun add() {
        BaresipService.calls.add(0, this)
    }

    fun remove() {
        BaresipService.calls.remove(this)
    }

    fun connect(uri: String): Boolean {
        return call_connect(callp, uri) == 0
    }

    fun hold(): Boolean {
        return call_hold(callp, true) == 0
    }

    fun resume(): Boolean {
        return call_hold(callp, false) == 0
    }

    fun transfer(uri: String): Boolean {
        val err = call_hold(callp, true)
        if (err != 0)
            return false
        referTo = uri
        return call_transfer(callp, uri) == 0
    }

    fun executeTransfer(): Boolean {
        return if (this.onHoldCall != null) {
            if (call_hold(callp, true) == 0)
                call_replace_transfer(this.onHoldCall!!.callp, callp)
            else
                false
        } else
            false
    }

    fun sendDigit(digit: Char): Int {
        return call_send_digit(callp, digit)
    }

    fun notifySipfrag(code: Int, reason: String) {
        call_notify_sipfrag(callp, code, reason)
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

    fun replaces(): Boolean {
        return call_replaces(callp)
    }

    init {
        if (ua.account.mediaEnc != "") security = R.drawable.box_red
    }

    private external fun call_connect(callp: Long, peer_uri: String): Int
    private external fun call_hold(callp: Long, hold: Boolean): Int
    private external fun call_ismuted(callp: Long): Boolean
    private external fun call_transfer(callp: Long, peer_uri: String): Int
    private external fun call_send_digit(callp: Long, digit: Char): Int
    private external fun call_notify_sipfrag(callp: Long, code: Int, reason: String)
    private external fun call_start_audio(callp: Long)
    private external fun call_audio_codecs(callp: Long): String
    private external fun call_duration(callp: Long): Int
    private external fun call_stats(callp: Long, stream: String): String
    private external fun call_has_video(callp: Long): Boolean
    private external fun call_replaces(callp: Long): Boolean
    private external fun call_replace_transfer(xfer_callp: Long, callp: Long): Boolean

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
                if (c.status == status) return c
            return null
        }

    }
}

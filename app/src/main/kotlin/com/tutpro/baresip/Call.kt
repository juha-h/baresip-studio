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
    var security = R.drawable.unlocked
    var zid = ""
    var startTime: GregorianCalendar? = null  // Set when call is established
    var referTo = ""
    var dumpfiles = arrayOf("", "")

    fun add() {
        BaresipService.calls.add(0, this)
    }

    fun remove() {
        BaresipService.calls.remove(this)
    }

    fun connect(uri: String): Boolean {
        return Api.call_connect(callp, uri) == 0
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
        referTo = uri
        return Api.call_transfer(callp, uri) == 0
    }

    fun executeTransfer(): Boolean {
        return if (this.onHoldCall != null) {
            if (Api.call_hold(callp, true) == 0)
                Api.call_replace_transfer(this.onHoldCall!!.callp, callp)
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
        if (ua.account.mediaEnc != "") security = R.drawable.unlocked
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

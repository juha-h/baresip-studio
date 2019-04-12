package com.tutpro.baresip

import android.text.TextWatcher
import java.util.ArrayList

class Call(val callp: String, val ua: UserAgent, val peerURI: String, val dir: String,
           var status: String, val dtmfWatcher: TextWatcher?) {

    var onhold = false
    var security = 0
    var zid = ""
    var hasHistory = false

    init {
        if (ua.account.mediaEnc != "") security = R.drawable.box_red
    }

    companion object {

        fun calls(): ArrayList<Call> {
            return BaresipService.calls
        }

        fun calls(calls: ArrayList<Call>, dir: String): ArrayList<Call> {
            val result = ArrayList<Call>()
            for (i in calls.indices) {
                if (calls[i].dir == dir) result.add(calls[i])
            }
            return result
        }

        fun calls(dir: String): ArrayList<Call> {
            val result = ArrayList<Call>()
            for (c in BaresipService.calls) {
                if (c.dir == dir) result.add(c)
            }
            return result
        }

        fun uaCalls(ua: UserAgent, dir: String): ArrayList<Call> {
            val result = ArrayList<Call>()
            for (c in BaresipService.calls)
                if ((c.ua == ua) && ((dir == "") || c.dir == dir)) result.add(c)
            return result
        }

        fun find(callp: String): Call? {
            for (c in BaresipService.calls)
                if (c.callp == callp) return c
            return null
        }
    }
}

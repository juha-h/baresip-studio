package com.tutpro.baresip

import android.text.TextWatcher
import java.util.ArrayList

class Call(val callp: String, val ua: UserAgent, val peerURI: String, val dir: String,
           var status: String, val dtmfWatcher: TextWatcher?) {

    var hold = false
    var security = 0
    var zid = ""
    var hasHistory = false

    companion object {

        fun calls(calls: ArrayList<Call>, dir: String): ArrayList<Call> {
            val result = ArrayList<Call>()
            for (i in calls.indices) {
                if (calls[i].dir == dir) result.add(calls[i])
            }
            return result
        }

        fun uaCalls(calls: ArrayList<Call>, ua: UserAgent, dir: String): ArrayList<Call> {
            val result = ArrayList<Call>()
            for (i in calls.indices) {
                if ((calls[i].ua == ua) && (calls[i].dir == dir)) result.add(calls[i])
            }
            return result
        }

        fun find(calls: ArrayList<Call>, callp: String): Call? {
            for (c in calls) {
                if (c.callp == callp) return c
            }
            return null
        }

        fun uaCallIndex(calls: ArrayList<Call>, ua: UserAgent, call: Call, dir: String): Int {
            return uaCalls(calls, ua, dir).indexOf(call)
        }

    }
}

package com.tutpro.baresip

import android.text.TextWatcher
import java.util.ArrayList

class Call(val ua: String, val call: String, val peerURI: String, var status: String,
           val dtmfWatcher: TextWatcher?) {

    var hold: Boolean = false
    var security: Int = 0
    var zid: String = ""

    companion object {

        fun uaCalls(calls: ArrayList<Call>, ua: String): ArrayList<Call> {
            val result = ArrayList<Call>()
            for (i in calls.indices) {
                if (calls[i].ua == ua) result.add(calls[i])
            }
            return result
        }

        fun callIndex(calls: ArrayList<Call>, ua: String, call: String): Int {
            for (i in calls.indices) {
                if (calls[i].ua.equals(ua) && calls[i].call == call)
                    return i
            }
            return -1
        }


    }
}

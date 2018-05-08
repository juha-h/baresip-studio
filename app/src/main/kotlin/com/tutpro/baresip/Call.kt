package com.tutpro.baresip

import android.text.TextWatcher

class Call(val ua: String, val call: String, val peerURI: String, var status: String,
           val dtmfWatcher: TextWatcher?) {
    var hold: Boolean = false
    var security: Int = 0
    var zid: String = ""
}

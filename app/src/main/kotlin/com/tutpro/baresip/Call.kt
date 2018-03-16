package com.tutpro.baresip

class Call(val ua: String, val call: String, val peerURI: String, var status: String) {
    var hold: Boolean = false
}

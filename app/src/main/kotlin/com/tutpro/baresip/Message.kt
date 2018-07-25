package com.tutpro.baresip

import java.io.Serializable

class Message(val aor: String, val peerURI: String, var direction: Int, val message: String,
              val timeStamp: Long, var new: Boolean): Serializable {
}

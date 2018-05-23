package com.tutpro.baresip

import java.io.Serializable
import java.util.GregorianCalendar

class History(val aor: String, val peerURI: String, val direction: String,
              val connected: Boolean) : Serializable {

    val time: GregorianCalendar = GregorianCalendar()

}

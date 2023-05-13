package com.tutpro.baresip.plus

import java.util.*
import kotlin.collections.ArrayList

class CallRow(val aor: String, val peerUri: String, val direction: Int, val rejected: Boolean,
              startTime: GregorianCalendar?, val stopTime: GregorianCalendar,
              val recording: Array<String>)
{

    class Details(val direction: Int, val rejected: Boolean,
                  val startTime: GregorianCalendar?, val stopTime: GregorianCalendar,
                  val recording: Array<String>)

    val details = ArrayList<Details>()

    init {
        details.add(Details(direction, rejected, startTime, stopTime, recording))
    }

}

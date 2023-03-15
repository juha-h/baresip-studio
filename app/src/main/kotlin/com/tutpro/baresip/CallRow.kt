package com.tutpro.baresip

import java.util.*
import kotlin.collections.ArrayList

class CallRow(val aor: String, val peerUri: String, val direction: Int,
              startTime: GregorianCalendar?,
              val stopTime: GregorianCalendar,
              val recording: Array<String>)

{

    class Details(val direction: Int, val startTime: GregorianCalendar?,
                  val stopTime: GregorianCalendar, val recording: Array<String>)

    val details = ArrayList<Details>()

    init {
        details.add(Details(direction, startTime, stopTime, recording))
    }

}

package com.tutpro.baresip

import java.util.GregorianCalendar

data class CallRow(
    val aor: String,
    val peerUri: String,
    var direction: Int,
    var startTime: GregorianCalendar?,
    var stopTime: GregorianCalendar,
    var recording: Array<String>
) {
    data class Details(
        var direction: Int,
        var startTime: GregorianCalendar?,
        var stopTime: GregorianCalendar,
        var recording: Array<String>
    )
    val details = mutableListOf(Details(direction, startTime, stopTime, recording))
}

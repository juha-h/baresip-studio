package com.tutpro.baresip

class HistoryRow(val peerURI: String, val direction: Int, val time: String) {

    val directions = ArrayList<Int>()

    init {
        directions.add(direction)
    }
}

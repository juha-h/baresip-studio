package com.tutpro.baresip

class HistoryRow(val peerURI: String, val peerDomain: String, val direction: Int, val time: String,
                 val index: Int) {

    val directions = ArrayList<Int>()
    val indexes = ArrayList<Int>()

    init {
        directions.add(direction)
        indexes.add(index)
    }
}

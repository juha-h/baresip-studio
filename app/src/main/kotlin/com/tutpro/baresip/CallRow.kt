package com.tutpro.baresip

class CallRow(val aor: String, val peerUri: String, val direction: Int, val stopTime: String, val index: Int) {

    val directions = ArrayList<Int>()
    val indexes = ArrayList<Int>()

    init {
        directions.add(direction)
        indexes.add(index)
    }

}

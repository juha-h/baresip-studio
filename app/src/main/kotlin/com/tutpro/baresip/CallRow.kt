package com.tutpro.baresip

class CallRow(val aor: String, val peerUri: String, direction: Int, val time: String,
              index: Int) {

    val directions = ArrayList<Int>()
    val indexes = ArrayList<Int>()

    init {
        directions.add(direction)
        indexes.add(index)
    }
}

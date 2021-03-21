package com.tutpro.baresip

class CallRow(val aor: String, val peerUri: String, private val direction: Int, val time: String,
              private val index: Int) {

    val directions = ArrayList<Int>()
    val indexes = ArrayList<Int>()

    init {
        directions.add(direction)
        indexes.add(index)
    }
}

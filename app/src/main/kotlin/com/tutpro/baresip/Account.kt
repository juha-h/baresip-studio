package com.tutpro.baresip

class Account(val ua: String, val aoR: String, val mediaenc: String, var status: String) {
    private var statusImage: Int = 0

    init {
        this.statusImage = 0
    }

}

package com.tutpro.baresip.plus

class UserAgent(val uap: String) {

    val account = Account(Api.ua_account(uap))
    var registrationFailed = false

    fun add(status: Int) {
        BaresipService.uas.add(this)
        BaresipService.status.add(status)
    }

    fun remove() {
        val index = BaresipService.uas.indexOf(this)
        BaresipService.uas.remove(this)
        BaresipService.status.removeAt(index)
    }

    fun updateStatus(status: Int) {
        BaresipService.status[BaresipService.uas.indexOf(this)] = status
    }

    companion object {

        fun uas(): ArrayList<UserAgent> {
            return BaresipService.uas
        }

        fun status(): ArrayList<Int> {
            return BaresipService.status
        }

        fun ofAor(aor: String): UserAgent? {
            for (ua in BaresipService.uas)
                if (ua.account.aor == aor) return ua
            return null
        }

        fun ofUap(uap: String): UserAgent? {
            for (ua in BaresipService.uas)
                if (ua.uap == uap) return ua
            return null
        }

        fun uaAlloc(uri: String): UserAgent? {
            val uap = Api.ua_alloc(uri)
            if (uap != "") return UserAgent(uap)
            Log.e("Baresip", "Failed to allocate UserAgent for $uri")
            return null
        }

        fun findAorIndex(aor: String): Int? {
            for (i in BaresipService.uas.indices) {
                if (BaresipService.uas[i].account.aor == aor) return i
            }
            return null
        }

        fun register() {
            for (ua in BaresipService.uas) {
                if (ua.account.regint > 0) {
                    if (Api.ua_register(ua.uap) != 0)
                        Log.d("Baresip", "Failed to register ${ua.account.aor}")
                }
            }
        }
    }
}


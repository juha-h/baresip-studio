package com.tutpro.baresip

class UserAgent(val uap: Long) {

    val account = Account(Api.ua_account(uap))
    var status = R.drawable.circle_white

    fun callAlloc(xCall: Long, videoMode: Int): Long {
        return Api.ua_call_alloc(uap, xCall, videoMode)
    }

    fun add() {
        BaresipService.uas.add(this)
    }

    fun remove() {
        BaresipService.uas.remove(this)
    }

    fun calls(dir: String = ""): ArrayList<Call> {
        val result = ArrayList<Call>()
        for (c in BaresipService.calls)
            if ((c.ua == this) && ((dir == "") || c.dir == dir)) result.add(c)
        return result
    }

    fun currentCall(): Call? {
        for (c in BaresipService.calls)
            if (c.ua == this)
                return c
        return null
    }

    companion object {

        fun ofAor(aor: String): UserAgent? {
            for (ua in BaresipService.uas)
                if (ua.account.aor == aor) return ua
            return null
        }

        fun ofDomain(domain: String): UserAgent? {
            for (ua in BaresipService.uas)
                if (Utils.aorDomain(ua.account.aor) == domain) return ua
            return null
        }

        fun ofUap(uap: Long): UserAgent? {
            for (ua in BaresipService.uas)
                if (ua.uap == uap) return ua
            return null
        }

        fun uaAlloc(uri: String): UserAgent? {
            val uap = Api.ua_alloc(uri)
            if (uap != 0L) return UserAgent(uap)
            Log.e(TAG, "Failed to allocate UserAgent for $uri")
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
                        Log.d(TAG, "Failed to register ${ua.account.aor}")
                }
            }
        }
    }
}


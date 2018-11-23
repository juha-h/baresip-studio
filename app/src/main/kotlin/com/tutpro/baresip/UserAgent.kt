package com.tutpro.baresip

import android.util.Log

class UserAgent (val uap: String) {

    val account = Account(Api.ua_account(uap))

    fun register() {
        if (account.regint > 0) {
            Log.d("Baresip", "Registering ${account.aor} UA ${uap}")
            if (Api.ua_register(uap) != 0)
                Log.e("Baresip", "Registering failed")
        }
    }

    fun destroy() {
        Api.ua_destroy(uap)
    }

    companion object {

        fun uas(): ArrayList<UserAgent> {
            return BaresipService.uas
        }

        fun status(): ArrayList<Int> {
            return BaresipService.status
        }

        fun add(ua: UserAgent, status: Int) {
            BaresipService.uas.add(ua)
            BaresipService.status.add(status)
        }

        fun remove(ua: UserAgent) {
            val index = BaresipService.uas.indexOf(ua)
            if (index != -1) {
                BaresipService.uas[index].destroy()
                BaresipService.status.removeAt(index)
            }
        }

        fun uaAlloc(uri: String): UserAgent? {
            val uap = Api.ua_alloc(uri)
            if (uap != "") return UserAgent(uap)
            return null
        }

        fun find(uap: String): UserAgent? {
            for (ua in BaresipService.uas) {
                if (ua.uap == uap) return ua
            }
            return null
        }

        fun findAorIndex(uas: ArrayList<UserAgent>, aor: String): Int? {
            for (i in uas.indices) {
                if (uas[i].account.aor == aor) return i
            }
            return null
        }

        fun register(uas: ArrayList<UserAgent>) {
            for (ua in uas) {
                if (ua.account.regint > 0)
                    if (Api.ua_register(ua.uap) != 0)
                        Log.e("Baresip", "Failed to register ${ua.account.aor}")
            }
        }
    }
}


package com.tutpro.baresip

import android.util.Log

class UserAgent (val uap: String) {

    val account = Account(ua_account(uap))

    fun register() {
        if (account.regint > 0) {
            Log.d("Baresip", "Registering ${account.aor} UA ${uap}")
            val accp = ua_account(uap)
            val reg_int = account_regint(accp)
            Log.d("Baresip", "reg_int is $reg_int")
            if (ua_register(uap) != 0)
                Log.e("Baresip", "Registering failed")
        }
    }

    fun destroy() {
        ua_destroy(uap)
    }

    companion object {

        external fun ua_alloc(uri: String): String
        external fun ua_update_account(ua: String): Int
        external fun ua_destroy(ua: String)
        external fun ua_register(ua: String): Int
        external fun ua_isregistered(ua: String): Boolean
        external fun ua_unregister(ua: String)

        fun uaAlloc(uri: String): UserAgent? {
            val uap = ua_alloc(uri)
            if (uap != "") return UserAgent(uap)
            return null
        }

        fun find(uas: ArrayList<UserAgent>, uap: String): UserAgent? {
            for (ua in uas) {
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
                if (ua_register(ua.uap) != 0)
                    Log.e("Baresip", "Failed to register ${ua.account.aor}")
            }
        }
    }
}

external fun ua_account(ua: String): String
external fun ua_aor(ua: String): String


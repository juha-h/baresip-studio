package com.tutpro.baresip

import android.util.Log

class UserAgent (val uap: String) {

    val account = Account(ua_account(uap))
    val aor = ua_aor(uap)

    companion object {

        external fun ua_alloc(uri: String): Int
        external fun ua_destroy(ua: String)
        external fun ua_register(ua: String): Int
        external fun ua_isregistered(ua: String): Boolean
        external fun ua_unregister(ua: String)

        fun find(uas: ArrayList<UserAgent>, uap: String): UserAgent? {
            for (ua in uas) {
                if (ua.uap == uap) return ua
            }
            return null
        }

        fun findAorIndex(uas: ArrayList<UserAgent>, aor: String): Int? {
            for (i in uas.indices) {
                if (uas[i].aor == aor) return i
            }
            return null
        }

        fun register(uas: ArrayList<UserAgent>) {
            for (ua in uas) {
                if (ua_register(ua.uap) != 0)
                    Log.e("Baresip", "Failed to register ${ua.aor}")
            }
        }
    }
}

external fun ua_account(ua: String): String
external fun ua_aor(ua: String): String


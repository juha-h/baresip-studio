package com.tutpro.baresip

class UserAgent (val uap: String) {

    val account = Account(ua_account(uap))
    val aor = ua_aor(uap)

    companion object {

        external fun ua_alloc(uri: String): Int
        external fun ua_destroy(uri: String)
        external fun ua_register(ua: String): Int
        external fun ua_isregistered(ua: String): Boolean
        external fun ua_unregister(ua: String)

        fun userAgents(): ArrayList<UserAgent> {
            val res = ArrayList<UserAgent>()
            for (ua in MainActivity.uas) {
                res.add(ua)
            }
            return res
        }

        fun find(uas: ArrayList<UserAgent>, uap: String): UserAgent? {
            for (u in uas) {
                if (u.uap == uap) return u
            }
            return null
        }
    }
}

external fun ua_account(ua: String): String
external fun ua_aor(ua: String): String


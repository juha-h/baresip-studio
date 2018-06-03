package com.tutpro.baresip

class Account(val accp: String) {

    var displayName = account_display_name(accp)
    val aor = account_aor(accp)
    var authUser = account_auth_user(accp)
    var authPass = account_auth_pass(accp)
    var outbound = ArrayList<String>()
    var audioCodec = ArrayList<String>()
    var regint = account_regint(accp)
    var mediaenc = account_mediaenc(accp)

    init {
        var i = 0
        while (true) {
            val ob = account_outbound(accp, i)
            if (ob != "") {
                outbound.add(ob)
                i++
            } else {
                break
            }
        }
        i = 0
        while (true) {
            val ac = account_audio_codec(accp, i)
            if (ac != "") {
                audioCodec.add(ac)
                i++
            } else {
                break
            }
        }
    }

    fun print() : String {

        var res: String

        if (displayName != "")
            res = "\"${displayName}\" "
        else
            res = ""

        res = res + "<${aor}>"

        if (authUser != "") res = res + ";auth_user=\"${authUser}\""

        if (authPass != "") res = res + ";auth_pass=\"${authPass}\""

        if (outbound.size > 0) {
            res = res + ";outbound=\"${outbound[0]}\""
            if (outbound.size > 1) res = res + ";outbound2=\"${outbound[1]}\""
            res = res + ";sipnat=outbound"
        }

        if (audioCodec.size > 0) {
            var first = true
            res = res + ";audio_codecs="
            for (c in audioCodec)
                if (first) {
                    res = res + c
                    first = false
                } else {
                    res = res + ",$c"
                }
        }

        if (mediaenc != "") res = res + ";mediaenc=${mediaenc}"

        res = res + ";ptime=20;regint=${regint};regq=0.5;pubint=0;answermode=manual"

        return res
    }

    companion object {

        fun accounts(): ArrayList<Account> {
            val res = ArrayList<Account>()
            for (ua in MainActivity.uas) {
                res.add(ua.account)
            }
            return res
        }

        fun find(uas: ArrayList<UserAgent>, accp: String): Account? {
            for (ua in uas) {
                if (ua.account.accp == accp) return ua.account
            }
            return null
        }

        fun findUA(aor: String): UserAgent? {
            for (ua in MainActivity.uas) {
                if (ua.account.aor == aor) return ua
            }
            return null
        }

        fun exists(uas: ArrayList<UserAgent>, aor: String): Boolean {
            for (ua in uas) {
                if (ua.account.aor == aor) return true
            }
            return false
        }

    }
}

external fun account_set_display_name(acc: String, dn: String): Int
external fun account_display_name(acc: String): String
external fun account_aor(acc: String): String
external fun account_auth_user(acc: String): String
external fun account_set_auth_user(acc: String, user: String): Int
external fun account_auth_pass(acc: String): String
external fun account_set_auth_pass(acc: String, pass: String): Int
external fun account_outbound(acc: String, ix: Int): String
external fun account_set_outbound(acc: String, ob: String, ix: Int): Int
external fun account_sipnat(acc: String): String
external fun account_set_sipnat(acc: String, sipnat: String): Int
external fun account_audio_codec(acc: String, ix: Int): String
external fun account_regint(acc: String): Int
external fun account_set_regint(acc: String, regint: Int): Int
external fun account_mediaenc(acc: String): String
external fun account_set_mediaenc(acc: String, mencid: String): Int
external fun account_set_audio_codecs(acc: String, codecs: String): Int
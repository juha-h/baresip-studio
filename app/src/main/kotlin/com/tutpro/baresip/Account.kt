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
    var vmUri = account_vm_uri(accp)
    var vmNew = 0
    var vmOld = 0
    var missedCalls = false

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

        if (vmUri == "")
            res = res + ";mwi=no"
        else
            res = res + ";vm_uri=\"$vmUri\""

        res = res + ";ptime=20;regint=${regint};regq=0.5;pubint=0;answermode=manual;call_transfer=yes"

        return res
    }

    fun vmMessage() : String {
        var new = ""
        var old = ""
        if (vmNew > 0)
            if (vmNew == 1)
                new = "$vmNew new message"
            else
                new = "$vmNew new messages"
        if (vmOld > 0)
            if (vmOld == 1)
                old = "$vmOld old message"
            else
                old = "$vmOld old messages"
        var msg = "You have"
        if (new != "") {
            msg = "$msg $new"
            if (old != "") msg = "$msg and $old"
        } else {
            if (old != "")
                msg = "$msg $old"
            else
                msg = "$msg no messages"
        }
        return "$msg."
    }

    fun host() : String {
        return aor.split("@")[1]
    }
    companion object {

        fun accounts(): ArrayList<Account> {
            val res = ArrayList<Account>()
            for (ua in UserAgent.uas()) {
                res.add(ua.account)
            }
            return res
        }

        fun find(accp: String): Account? {
            for (ua in UserAgent.uas()) {
                if (ua.account.accp == accp) return ua.account
            }
            return null
        }

        fun findUa(aor: String): UserAgent? {
            for (ua in UserAgent.uas()) {
                if (ua.account.aor == aor) return ua
            }
            return null
        }

        fun exists(aor: String): Boolean {
            for (ua in UserAgent.uas()) {
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
external fun account_set_sipnat(acc: String, sipnat: String): Int
external fun account_audio_codec(acc: String, ix: Int): String
external fun account_regint(acc: String): Int
external fun account_set_regint(acc: String, regint: Int): Int
external fun account_mediaenc(acc: String): String
external fun account_set_mediaenc(acc: String, mencid: String): Int
external fun account_set_audio_codecs(acc: String, codecs: String): Int
external fun account_set_mwi(acc: String, value: String): Int
external fun account_vm_uri(acc: String): String

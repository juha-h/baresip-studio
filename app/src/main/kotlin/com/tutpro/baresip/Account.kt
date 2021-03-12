package com.tutpro.baresip

import android.content.Context
import java.util.*
import kotlin.collections.ArrayList

class Account(val accp: String) {

    var displayName = Api.account_display_name(accp)
    val aor = Api.account_aor(accp)
    var luri = Api.account_luri(accp)
    var authUser = Api.account_auth_user(accp)
    var authPass = Api.account_auth_pass(accp)
    var outbound = ArrayList<String>()
    var mediaNat = Api.account_medianat(accp)
    var stunServer = Api.account_stun_uri(accp)
    var stunUser = Api.account_stun_user(accp)
    var stunPass = Api.account_stun_pass(accp)
    var audioCodec = ArrayList<String>()
    var videoCodec = ArrayList<String>()
    var regint = Api.account_regint(accp)
    var mediaEnc = Api.account_mediaenc(accp)
    var preferIPv6Media = false
    var dtmfMode = Api.account_dtmfmode(accp)
    var answerMode = Api.account_answermode(accp)
    var vmUri = Api.account_vm_uri(accp)
    var vmNew = 0
    var vmOld = 0
    var missedCalls = false
    var unreadMessages = false
    var callHistory = true
    var resumeUri = ""

    init {

        var i = 0
        while (true) {
            val ob = Api.account_outbound(accp, i)
            if (ob != "") {
                outbound.add(ob)
                i++
            } else {
                break
            }
        }

        i = 0
        while (true) {
            val ac = Api.account_audio_codec(accp, i)
            if (ac != "") {
                audioCodec.add(ac)
                i++
            } else {
                break
            }
        }

        val extra = Api.account_extra(accp)
        preferIPv6Media = Utils.paramValue(extra,"prefer_ipv6_media") == "yes"
        callHistory = Utils.paramValue(extra,"call_history") == ""

    }

    fun print() : String {

        var res: String

        if (displayName != "")
            res = "\"${displayName}\" "
        else
            res = ""

        res = "$res<$luri>"

        if (authUser != "") res += ";auth_user=\"${authUser}\""

        if ((authPass != "") && !MainActivity.aorPasswords.containsKey(aor))
            res += ";auth_pass=\"${authPass}\""

        if (outbound.size > 0) {
            res += ";outbound=\"${outbound[0]}\""
            if (outbound.size > 1) res = res + ";outbound2=\"${outbound[1]}\""
            res = "$res;sipnat=outbound"
        }

        if (mediaNat != "") res += ";medianat=${mediaNat}"

        if (stunServer != "")
            res += ";stunserver=\"${stunServer}\""

        if (stunUser != "")
            res += ";stunuser=\"${stunUser}\""

        if (stunPass != "")
            res += ";stunpass=\"${stunPass}\""

        if (audioCodec.size > 0) {
            var first = true
            res = "$res;audio_codecs="
            for (c in audioCodec)
                if (first) {
                    res += c
                    first = false
                } else {
                    res = "$res,$c"
                }
        }

        if (mediaEnc != "") res += ";mediaenc=${mediaEnc}"

        if (vmUri == "")
            res = "$res;mwi=no"
        else
            res = "$res;mwi=yes;vm_uri=\"$vmUri\""

        if (answerMode == Api.ANSWERMODE_AUTO)
            res += ";answermode=auto"

        res += ";ptime=20;regint=${regint};regq=0.5;pubint=0;call_transfer=yes"

        var extra = ""

        if (!callHistory)
            extra += ";call_history=no"

        if (preferIPv6Media)
            extra += ";prefer_ipv6_media=yes"

        if (extra != "")
            res += ";extra=\"" + extra.substringAfter(";") + "\""

        return res
    }

    fun vmMessages(cxt: Context) : String {
        var new = ""
        var old = ""
        if (vmNew > 0)
            if (vmNew == 1)
                new = cxt.getString(R.string.one_new_message)
            else
                new = "$vmNew ${cxt.getString(R.string.new_messages)}"
        if (vmOld > 0)
            if (vmOld == 1)
                old = cxt.getString(R.string.one_old_message)
            else
                old = "$vmOld ${cxt.getString(R.string.old_messages)}"
        var msg = cxt.getString(R.string.you_have)
        if (new != "") {
            msg = "$msg $new"
            if (old != "") msg = "$msg ${cxt.getString(R.string.and)} $old"
        } else {
            if (old != "")
                msg = "$msg $old"
            else
                msg = cxt.getString(R.string.no_messages)
        }
        return "$msg."
    }

    fun host() : String {
        return aor.split("@")[1]
    }

    private fun removeAudioCodecsStartingWith(prefix: String) {
        val newCodecs = ArrayList<String>()
        for (acSpec in audioCodec)
            if (!acSpec.toLowerCase(Locale.ROOT).startsWith(prefix)) newCodecs.add(acSpec)
        audioCodec = newCodecs
    }

    fun removeAudioCodecs(codecModule: String) {
        when (codecModule) {
            "g711" ->
                removeAudioCodecsStartingWith("pcm")
            "g722" ->
                removeAudioCodecsStartingWith("g722/")
            else ->
                removeAudioCodecsStartingWith(codecModule)
        }
    }

    companion object {

        fun accounts(): ArrayList<Account> {
            val res = ArrayList<Account>()
            for (ua in UserAgent.uas()) {
                res.add(ua.account)
            }
            return res
        }

        fun ofAor(aor: String): Account? {
            for (ua in UserAgent.uas())
                if (ua.account.aor == aor) return ua.account
            return null
        }

        fun checkDisplayName(dn: String): Boolean {
            if (dn == "") return true
            val dnRegex = Regex("^([* .!%_`'~]|[+]|[-a-zA-Z0-9]){1,64}\$")
            return dnRegex.matches(dn)
        }

        fun checkAuthUser(au: String): Boolean {
            if (au == "") return true
            val ud = au.split("@")
            val userIDRegex = Regex("^([* .!%_`'~]|[+]|[-a-zA-Z0-9]){1,64}\$")
            val telnoRegex = Regex("^[+]?[0-9]{1,16}\$")
            if (ud.size == 1) {
                return userIDRegex.matches(ud[0]) || telnoRegex.matches(ud[0])
            } else {
                return (userIDRegex.matches(ud[0]) || telnoRegex.matches(ud[0])) &&
                        Utils.checkDomain(ud[1])
            }
        }

        fun checkAuthPass(ap: String): Boolean {
            return (ap.isNotEmpty()) && (ap.length <= 64) &&
                    Regex("^[ -~]*\$").matches(ap) && !ap.contains('"')
        }
    }
}

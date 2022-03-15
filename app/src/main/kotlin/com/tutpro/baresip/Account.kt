package com.tutpro.baresip

import android.content.Context
import java.util.*
import kotlin.collections.ArrayList
import java.net.URLEncoder
import java.net.URLDecoder

class Account(val accp: Long) {

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
    var dtmfMode = Api.account_dtmfmode(accp)
    var answerMode = Api.account_answermode(accp)
    var vmUri = Api.account_vm_uri(accp)
    var vmNew = 0
    var vmOld = 0
    var missedCalls = false
    var unreadMessages = false
    var callHistory = true
    var telProvider = Utils.aorDomain(aor)
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
        callHistory = Utils.paramValue(extra,"call_history") == ""
        telProvider = if (Utils.paramExists(extra, "tel_provider"))
            URLDecoder.decode(Utils.paramValue(extra,"tel_provider"), "UTF-8")
        else
            Utils.aorDomain(aor)

    }

    fun print() : String {

        var res = if (displayName != "")
            "\"${displayName}\" "
        else
            ""

        res = "$res<$luri>"

        if (authUser != "") res += ";auth_user=\"${authUser}\""

        if ((authPass != "") && !MainActivity.aorPasswords.containsKey(aor))
            res += ";auth_pass=\"${authPass}\""

        if (outbound.size > 0) {
            res += ";outbound=\"${outbound[0]}\""
            if (outbound.size > 1) res += ";outbound2=\"${outbound[1]}\""
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

        res = if (vmUri == "")
            "$res;mwi=no"
        else
            "$res;mwi=yes;vm_uri=\"$vmUri\""

        if (answerMode == Api.ANSWERMODE_AUTO)
            res += ";answermode=auto"

        res += ";ptime=20;regint=${regint};regq=0.5;pubint=0;call_transfer=yes"

        var extra = ""

        if (!callHistory)
            extra += ";call_history=no"

        extra += ";tel_provider=${URLEncoder.encode(telProvider, "UTF-8")}"

        if (extra != "")
            res += ";extra=\"" + extra.substringAfter(";") + "\""

        return res
    }

    fun vmMessages(cxt: Context) : String {

        val new = if (vmNew > 0) {
            if (vmNew == 1)
                cxt.getString(R.string.one_new_message)
            else
                "$vmNew ${cxt.getString(R.string.new_messages)}"
        } else
            ""

        val old = if (vmOld > 0) {
            if (vmOld == 1)
                cxt.getString(R.string.one_old_message)
            else
                    "$vmOld ${cxt.getString(R.string.old_messages)}"
        } else
            ""

        var msg = cxt.getString(R.string.you_have)
        if (new != "") {
            msg = "$msg $new"
            if (old != "") msg = "$msg ${cxt.getString(R.string.and)} $old"
        } else {
            msg = if (old != "")
                "$msg $old"
            else
                cxt.getString(R.string.no_messages)
        }

         return "$msg."
    }

    fun host() : String {
        return aor.split("@")[1]
    }

    private fun removeAudioCodecsStartingWith(prefix: String) {
        val newCodecs = ArrayList<String>()
        for (acSpec in audioCodec)
            if (!acSpec.lowercase(Locale.ROOT).startsWith(prefix)) newCodecs.add(acSpec)
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
            for (ua in BaresipService.uas) {
                res.add(ua.account)
            }
            return res
        }

        fun telProviderAccounts(): Array<String> {
            val res = ArrayList<String>()
            for (account in accounts())
                if (account.telProvider != "")
                    res.add(account.aor.substring(4))
            return res.toTypedArray()
        }

        fun ofAor(aor: String): Account? {
            for (ua in BaresipService.uas)
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
            return when (ud.size) {
                1 -> Utils.checkUriUser(au)
                2 -> Utils.checkUriUser(ud[0]) && Utils.checkDomain(ud[1])
                else -> false
            }
        }

        fun checkAuthPass(ap: String): Boolean {
            return (ap.isNotEmpty()) && (ap.length <= 64) &&
                    Regex("^[ -~]*\$").matches(ap) && !ap.contains('"')
        }
    }
}

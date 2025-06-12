package com.tutpro.baresip

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.util.*
import kotlin.collections.ArrayList
import java.net.URLEncoder
import java.net.URLDecoder

class Account(val accp: Long) {

    var nickName = ""
    var displayName = Api.account_display_name(accp)
    val aor = Api.account_aor(accp)
    val luri = Api.account_luri(accp)
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
    var configuredRegInt = REGISTRATION_INTERVAL
    var mediaEnc = Api.account_mediaenc(accp)
    var rtcpMux = Api.account_rtcp_mux(accp)
    var rel100Mode = Api.account_rel100_mode(accp)
    var dtmfMode = Api.account_dtmfmode(accp)
    var answerMode = Api.account_answermode(accp)
    var autoRedirect = Api.account_sip_autoredirect(accp)
    var vmUri = Api.account_vm_uri(accp)
    var vmNew = 0
    var vmOld = 0
    var missedCalls = false
    var unreadMessages = false
    var callHistory = true
    var countryCode = ""
    var telProvider = Utils.aorDomain(aor)
    var resumeUri = ""
    var numericKeypad = false

    init {

        if (authPass == "")
            authPass = NO_AUTH_PASS

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
        if (Utils.paramExists(extra, "nickname"))
            nickName = Utils.paramValue(extra, "nickname")
        if (Utils.paramExists(extra, "regint"))
            configuredRegInt = Utils.paramValue(extra, "regint").toInt()
        callHistory = Utils.paramValue(extra, "call_history") == ""
        if (Utils.paramExists(extra, "country_code"))
            countryCode = Utils.paramValue(extra, "country_code")
        if (Utils.paramExists(extra, "tel_provider"))
            telProvider = URLDecoder.decode(Utils.paramValue(extra, "tel_provider"), "UTF-8")
        numericKeypad = Utils.paramExists(extra, "numeric_keypad")
    }

    fun print() : String {

        var res = if (displayName != "")
            "\"${displayName}\" "
        else
            ""

        res = "$res<$luri>"

        if (authUser != "") res += ";auth_user=\"${authUser}\""

        if ((authPass != "") && !BaresipService.aorPasswords.containsKey(aor))
            res += ";auth_pass=\"${authPass}\""

        if (outbound.isNotEmpty()) {
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

        if (audioCodec.isNotEmpty()) {
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

        if (rtcpMux)
            res += ";rtcp_mux=yes"

        res += if (rel100Mode == Api.REL100_ENABLED)
            ";100rel=yes"
        else
            ";100rel=no"

        if (dtmfMode == Api.DTMFMODE_SIP_INFO)
            res += ";dtmfmode=info"
        else if (dtmfMode == Api.DTMFMODE_AUTO)
            res += ";dtmfmode=auto"

        res = if (vmUri == "")
            "$res;mwi=no"
        else
            "$res;mwi=yes;vm_uri=\"$vmUri\""

        if (answerMode == Api.ANSWERMODE_AUTO)
            res += ";answermode=auto"

        if (autoRedirect)
            res += ";sip_autoredirect=yes"

        res += ";ptime=20;regint=${regint};regq=0.5;pubint=0;inreq_allowed=yes;call_transfer=yes"

        var extra = ""

        if (nickName != "")
            extra += ";nickname=${nickName}"

        if (!callHistory)
            extra += ";call_history=no"

        extra += ";tel_provider=${URLEncoder.encode(telProvider, "UTF-8")}"

        if (countryCode != "")
            extra += ";country_code=$countryCode"

        if (configuredRegInt != REGISTRATION_INTERVAL)
            extra += ";regint=$configuredRegInt"

        if (numericKeypad)
            extra += ";numeric_keypad=yes"

        if (extra !="")
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

    fun text(): String {
        return if (nickName != "")
            nickName
        else
            aor.split(":")[1].substringBefore(";")
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
            for (ua in BaresipService.uas.value) {
                res.add(ua.account)
            }
            return res
        }

        fun saveAccounts() {
            var accounts = ""
            for (a in accounts()) accounts = accounts + a.print() + "\n"
            Utils.putFileContents(BaresipService.filesPath + "/accounts",
                accounts.toByteArray(Charsets.UTF_8))
            // Log.d(TAG, "Saved accounts '${accounts}' to '${BaresipService.filesPath}/accounts'")
        }

        fun ofAor(aor: String): Account? {
            for (ua in BaresipService.uas.value)
                if (ua.account.aor == aor) return ua.account
            return null
        }

        fun checkDisplayName(dn: String): Boolean {
            if (dn == "") return true
            val dnRegex = Regex("^([* .!%_`'~]|[+]|[-a-zA-Z0-9]){1,64}$")
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
                    Regex("^[ -~]*$").matches(ap) && !ap.contains('"')
        }

        fun uniqueNickName(nickName: String): Boolean {
            for (ua in BaresipService.uas.value)
                if (ua.account.nickName == nickName)
                    return false
            return true
        }
    }
}

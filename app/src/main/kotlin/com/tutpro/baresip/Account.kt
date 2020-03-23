package com.tutpro.baresip

import android.content.Context
import java.util.*
import kotlin.collections.ArrayList

class Account(val accp: String) {

    var displayName = account_display_name(accp)
    val aor = account_aor(accp)
    var laddr = account_laddr(accp)
    var authUser = account_auth_user(accp)
    var authPass = account_auth_pass(accp)
    var outbound = ArrayList<String>()
    var mediaNat = account_medianat(accp)
    var stunServer = ""
    var audioCodec = ArrayList<String>()
    var regint = account_regint(accp)
    var mediaEnc = account_mediaenc(accp)
    var preferIPv6Media = false
    var answerMode = ""
    var vmUri = account_vm_uri(accp)
    var vmNew = 0
    var vmOld = 0
    var missedCalls = false
    var unreadMessages = false
    var callHistory = true

    init {

        val stunHost = account_stun_host(accp)
        if (stunHost != "") {
            val stunPort = account_stun_port(accp)
            if (stunPort == 0)
                stunServer = stunHost
            else
                stunServer = "$stunHost:$stunPort"
        }

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

        val extra = account_extra(accp)
        preferIPv6Media = Utils.paramValue(extra,"prefer_ipv6_media") == "yes"
        answerMode = Utils.paramValue(extra,"answer_mode")
        if (answerMode == "") answerMode = "manual"
        callHistory = Utils.paramValue(extra,"call_history") == ""

    }

    fun print() : String {

        var res: String

        if (displayName != "")
            res = "\"${displayName}\" "
        else
            res = ""

        res = res + "<$laddr>"

        if (authUser != "") res = res + ";auth_user=\"${authUser}\""

        if ((authPass != "") && !MainActivity.aorPasswords.containsKey(aor))
            res = res + ";auth_pass=\"${authPass}\""

        if (outbound.size > 0) {
            res = res + ";outbound=\"${outbound[0]}\""
            if (outbound.size > 1) res = res + ";outbound2=\"${outbound[1]}\""
            res = res + ";sipnat=outbound"
        }

        if (mediaNat != "") res = res + ";medianat=${mediaNat}"

        if (stunServer != "") res = res + ";stunserver=\"stun:${stunServer}\""

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

        if (mediaEnc != "") res = res + ";mediaenc=${mediaEnc}"

        if (vmUri == "")
            res = res + ";mwi=no"
        else
            res = res + ";vm_uri=\"$vmUri\""

        res += ";ptime=20;regint=${regint};regq=0.5;pubint=0;call_transfer=yes"

        var extra = ""

        if (!callHistory)
            extra += ";call_history=no"

        if (answerMode == "auto")
                extra += ";answer_mode=auto"

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
            "g711" -> {
                removeAudioCodecsStartingWith("pcm")
            }
            "g722" -> {
                removeAudioCodecsStartingWith("g722/")
            }
            else -> {
                removeAudioCodecsStartingWith(codecModule)
            }
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
                if (ua.account.aor.split(":")[1] == aor.split(":")[0])
                    return true
            }
            return false
        }

    }
}

external fun account_set_display_name(acc: String, dn: String): Int
external fun account_display_name(acc: String): String
external fun account_aor(acc: String): String
external fun account_laddr(acc: String): String
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
external fun account_stun_host(acc: String): String
external fun account_stun_port(acc: String): Int
external fun account_set_stun_host(acc: String, host: String): Int
external fun account_set_stun_port(acc: String, port: Int): Int
external fun account_mediaenc(acc: String): String
external fun account_set_mediaenc(acc: String, mediaenc: String): Int
external fun account_medianat(acc: String): String
external fun account_set_medianat(acc: String, medianat: String): Int
external fun account_set_audio_codecs(acc: String, codecs: String): Int
external fun account_set_mwi(acc: String, value: String): Int
external fun account_vm_uri(acc: String): String
external fun account_extra(acc: String): String

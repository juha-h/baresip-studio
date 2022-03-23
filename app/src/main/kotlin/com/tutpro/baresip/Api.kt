package com.tutpro.baresip

object Api {

    const val VIDMODE_OFF = 0
    // const val VIDMODE_ON = 1
    const val ANSWERMODE_MANUAL = 0
    const val ANSWERMODE_AUTO = 2
    const val DTMFMODE_RTP_EVENT = 0
    const val DTMFMODE_SIP_INFO = 1

    external fun account_set_display_name(acc: Long, dn: String): Int
    external fun account_display_name(acc: Long): String
    external fun account_aor(acc: Long): String
    external fun account_luri(acc: Long): String
    external fun account_auth_user(acc: Long): String
    external fun account_set_auth_user(acc: Long, user: String): Int
    external fun account_auth_pass(acc: Long): String
    external fun account_set_auth_pass(acc: Long, pass: String): Int
    external fun account_outbound(acc: Long, ix: Int): String
    external fun account_set_outbound(acc: Long, ob: String, ix: Int): Int
    external fun account_set_sipnat(acc: Long, sipnat: String): Int
    external fun account_audio_codec(acc: Long, ix: Int): String
    external fun account_regint(acc: Long): Int
    external fun account_set_regint(acc: Long, regint: Int): Int
    external fun account_stun_uri(acc: Long): String
    external fun account_set_stun_uri(acc: Long, uri: String): Int
    external fun account_stun_user(acc: Long): String
    external fun account_set_stun_user(acc: Long, user: String): Int
    external fun account_stun_pass(acc: Long): String
    external fun account_set_stun_pass(acc: Long, pass: String): Int
    external fun account_mediaenc(acc: Long): String
    external fun account_set_mediaenc(acc: Long, mediaenc: String): Int
    external fun account_medianat(acc: Long): String
    external fun account_set_medianat(acc: Long, medianat: String): Int
    external fun account_set_audio_codecs(acc: Long, codecs: String): Int
    external fun account_set_video_codecs(acc: Long, codecs: String): Int
    external fun account_set_mwi(acc: Long, value: String): Int
    external fun account_vm_uri(acc: Long): String
    external fun account_answermode(acc: Long): Int
    external fun account_set_answermode(acc: Long, mode: Int): Int
    external fun account_dtmfmode(acc: Long): Int
    external fun account_set_dtmfmode(acc: Long, mode: Int): Int
    external fun account_extra(acc: Long): String
    external fun account_debug(acc: Long)

    external fun uag_reset_transp(register: Boolean, reinvite: Boolean)
    external fun uag_enable_sip_trace(enable: Boolean)

    external fun ua_account(uap: Long): Long
    external fun ua_alloc(uri: String): Long
    external fun ua_update_account(uap: Long): Int
    external fun ua_destroy(uap: Long)
    external fun ua_register(uap: Long): Int
    external fun ua_isregistered(uap: Long): Boolean
    external fun ua_unregister(uap: Long)
    external fun ua_hangup(uap: Long, callp: Long, code: Int, reason: String)
    external fun ua_call_alloc(uap: Long, xcallp: Long, video: Int): Long
    external fun ua_answer(uap: Long, callp: Long, video: Int)
    external fun ua_debug(uap: Long)

    external fun call_connect(callp: Long, peer_uri: String): Int
    external fun call_hold(callp: Long, hold: Boolean): Int
    external fun call_ismuted(callp: Long): Boolean
    external fun call_transfer(callp: Long, peer_uri: String): Int
    external fun call_send_digit(callp: Long, digit: Char): Int
    external fun call_notify_sipfrag(callp: Long, code: Int, reason: String)
    external fun call_start_audio(callp: Long)
    external fun call_audio_codecs(callp: Long): String
    external fun call_duration(callp: Long): Int
    external fun call_stats(callp: Long, stream: String): String
    external fun call_has_video(callp: Long): Boolean
    external fun call_replaces(callp: Long): Boolean
    external fun call_replace_transfer(xfer_callp: Long, callp: Long): Boolean

    external fun calls_mute(mute: Boolean)

    external fun message_send(uap: Long, peer_uri: String, message: String, time: String): Int

    external fun audio_codecs(): String
    external fun video_codecs(): String

    external fun log_level_set(level: Int)

    external fun net_use_nameserver(servers: String): Int
    external fun net_add_address_ifname(ip_addr: String, if_name: String): Int
    external fun net_rm_address(ip_addr: String): Int
    external fun net_debug()
    external fun net_dns_debug()

    external fun cmd_exec(cmd: String): Int

    external fun reload_config(): Int
    external fun module_load(module: String): Int
    external fun module_unload(module: String)

}

package com.tutpro.baresip

object Api {

    const val AF_UNSPEC = 0
    const val AF_INET = 2
    const val AF_INET6 = 10
    const val VIDMODE_OFF = 0
    const val VIDMODE_ON = 1
    const val ANSWERMODE_MANUAL = 0
    const val ANSWERMODE_AUTO = 2
    const val DTMFMODE_RTP_EVENT = 0
    const val DTMFMODE_SIP_INFO = 1

    external fun account_set_display_name(acc: String, dn: String): Int
    external fun account_display_name(acc: String): String
    external fun account_aor(acc: String): String
    external fun account_luri(acc: String): String
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
    external fun account_stun_uri(acc: String): String
    external fun account_set_stun_uri(acc: String, uri: String): Int
    external fun account_stun_user(acc: String): String
    external fun account_set_stun_user(acc: String, user: String): Int
    external fun account_stun_pass(acc: String): String
    external fun account_set_stun_pass(acc: String, pass: String): Int
    external fun account_mediaenc(acc: String): String
    external fun account_set_mediaenc(acc: String, mediaenc: String): Int
    external fun account_medianat(acc: String): String
    external fun account_set_medianat(acc: String, medianat: String): Int
    external fun account_set_audio_codecs(acc: String, codecs: String): Int
    external fun account_set_video_codecs(acc: String, codecs: String): Int
    external fun account_set_mwi(acc: String, value: String): Int
    external fun account_vm_uri(acc: String): String
    external fun account_answermode(acc: String): Int
    external fun account_set_answermode(acc: String, mode: Int): Int
    external fun account_dtmfmode(acc: String): Int
    external fun account_set_dtmfmode(acc: String, mode: Int): Int
    external fun account_extra(acc: String): String
    external fun account_debug(acc: String)

    external fun uag_reset_transp(reg: Boolean, reinvite: Boolean)
    external fun uag_enable_sip_trace(enable: Boolean)

    external fun ua_account(ua: String): String
    external fun ua_alloc(uri: String): String
    external fun ua_update_account(ua: String): Int
    external fun ua_destroy(ua: String)
    external fun ua_register(ua: String): Int
    external fun ua_isregistered(ua: String): Boolean
    external fun ua_unregister(ua: String)
    external fun ua_connect(uap: String, peer_uri: String, video: Int): String
    external fun ua_hangup(uap: String, callp: String, code: Int, reason: String)
    external fun ua_call_alloc(uap: String, xcallp: String, video: Int): String
    external fun ua_answer(uap: String, callp: String, video: Int)
    external fun ua_set_media_af(uap: String, af: Int)
    external fun ua_debug(uap: String)

    external fun call_peeruri(callp: String): String

    external fun message_send(uap: String, peer_uri: String, message: String, time: String): Int

    external fun contact_add(contact: String)
    external fun contacts_remove()

    external fun audio_codecs(): String
    external fun video_codecs(): String

    external fun log_level_set(level: Int)

    external fun net_use_nameserver(servers: String): Int
    external fun net_set_address(ip_addr: String): Int
    external fun net_unset_address(af: Int)
    external fun net_debug()
    external fun net_dns_debug()

    external fun cmd_exec(cmd: String): Int

    external fun reload_config(): Int
    external fun module_load(module: String): Int
    external fun module_unload(module: String)

}

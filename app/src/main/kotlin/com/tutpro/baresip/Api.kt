package com.tutpro.baresip

object Api {

    val AF_UNSPEC = 0
    val AF_INET = 2
    val AF_INET6 = 10
    val VIDMODE_OFF = 0
    val VIDMODE_ON = 1

    external fun audio_codecs(): String
    external fun uag_current_set(uap: String)
    external fun uag_reset_transp(reg: Boolean, reinvite: Boolean)
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
    external fun account_debug(accp: String)
    external fun ua_debug(uap: String)
    external fun call_hold(callp: String): Int
    external fun call_unhold(callp: String): Int
    external fun call_connect(callp: String, peer_uri: String): Int
    external fun call_peeruri(callp: String): String
    external fun call_send_digit(callp: String, digit: Char): Int
    external fun call_notify_sipfrag(callp: String, code: Int, reason: String)
    external fun call_start_audio(callp: String)
    external fun call_stop_audio(callp: String)
    external fun call_audio_codecs(callp: String): String
    external fun call_status(callp: String): String
    external fun call_video_debug()
    external fun message_send(uap: String, peer_uri: String, message: String, time: String): Int
    external fun reload_config(): Int
    external fun cmd_exec(cmd: String): Int
    external fun contact_add(contact: String)
    external fun contacts_remove()
    external fun log_level_set(level: Int)
    external fun net_use_nameserver(servers: String): Int
    external fun net_set_address(ip_addr: String): Int
    external fun net_unset_address(af: Int)
    external fun net_debug()
    external fun net_dns_debug()
    external fun module_load(module: String): Int
    external fun module_unload(module: String)

}

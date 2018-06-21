package com.tutpro.baresip

object Api {

    external fun audio_codecs(): String
    external fun call_peeruri(callp: String): String
    external fun cmd_exec(cmd: String): Int
    external fun uri_decode(uri: String): Boolean

}
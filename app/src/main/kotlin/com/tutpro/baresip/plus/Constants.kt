package com.tutpro.baresip.plus

const val TAG = "Baresip+"

const val LOW_CHANNEL_ID = "com.tutpro.baresip.plus.low"
const val MEDIUM_CHANNEL_ID = "com.tutpro.baresip.plus.medium"
const val HIGH_CHANNEL_ID = "com.tutpro.baresip.plus.high"

const val KEY_TEXT_REPLY = "key_text_reply_baresip_plus"

const val STATUS_NOTIFICATION_ID = 101
const val CALL_NOTIFICATION_ID = 102
const val CALL_MISSED_NOTIFICATION_ID = 103
const val TRANSFER_NOTIFICATION_ID = 104
const val MESSAGE_NOTIFICATION_ID = 105

const val STATUS_REQ_CODE = 1
const val CALL_REQ_CODE = 2
const val ANSWER_REQ_CODE = 3
const val REJECT_REQ_CODE = 4
const val TRANSFER_REQ_CODE = 5
const val ACCEPT_REQ_CODE = 6
const val DENY_REQ_CODE = 7
const val MESSAGE_REQ_CODE = 8
const val SAVE_REQ_CODE = 9
const val DELETE_REQ_CODE = 10
const val DIRECT_REPLY_REQ_CODE = 11

const val REGISTRATION_INTERVAL = 900
const val NO_AUTH_PASS = "t%Qa?~?J8,~6"

const val MESSAGE_DOWN = 2131165304
const val MESSAGE_UP = 2131165306
const val MESSAGE_UP_FAIL = 2131165307
const val MESSAGE_UP_WAIT = 2131165308

val mediaEncMap = mapOf("zrtp" to "ZRTP", "dtls_srtp" to "DTLS-SRTPF", "srtp-mand" to "SRTP-MAND",
    "srtp" to "SRTP", "" to "--")

val mediaNatMap = mapOf("stun" to "STUN", "turn" to "TURN", "ice" to "ICE", "" to "--")



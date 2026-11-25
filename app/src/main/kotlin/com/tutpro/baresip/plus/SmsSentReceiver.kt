package com.tutpro.baresip.plus

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = Utils.popPendingSms()
        val aor = request?.first ?: ""
        val time = request?.second ?: 0L
        val resultCode = resultCode

        Log.d("Baresip", "SMS Sent Result: $resultCode for $aor at $time")

        if (aor.isNotEmpty() && time != 0L) {
            if (resultCode == Activity.RESULT_OK) {
                Message.updateMessageStatus(aor, time, MESSAGE_UP)
            } else {
                val reason = when (resultCode) {
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
                    else -> "Failed ($resultCode)"
                }
                Message.updateMessageStatus(aor, time, MESSAGE_UP_FAIL, reason)
            }
        }
    }
}

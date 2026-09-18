package com.tutpro.baresip.plus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat

class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
            val contentType = intent.type
            if (contentType == "application/vnd.wap.mms-message") {
                val pdu = intent.getByteArrayExtra("data")
                val subId = intent.getIntExtra("subscription", -1)
                Log.d(TAG, "Received MMS WAP Push Deliver with PDU size: ${pdu?.size ?: 0}, subId: $subId")
                val serviceIntent = Intent(context, BaresipService::class.java).apply {
                    action = "Process Incoming MMS"
                    putExtra("pdu", pdu)
                    putExtra("subId", subId)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }

    companion object {
        private const val TAG = "MmsReceiver"
    }
}

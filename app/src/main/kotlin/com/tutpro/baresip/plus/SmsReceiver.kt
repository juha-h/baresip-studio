package com.tutpro.baresip.plus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) return

            val sender = messages[0].displayOriginatingAddress ?: return
            val body = messages.joinToString("") { it.displayMessageBody ?: "" }
            val timestamp = messages[0].timestampMillis

            Log.d(TAG, "Received SMS from $sender: $body")

            val mobileUa = BaresipService.uas.value.find { it.account.isMobile }
            if (mobileUa != null) {
                // Notify Service for history update, notification, and alert sound
                if (BaresipService.isServiceRunning) {
                    BaresipService.instance?.handleIncomingMessage(mobileUa.uap, "tel:$sender", body, timestamp)
                } else {
                    // Service not running, at least save to history
                    val aor = mobileUa.account.aor
                    Message(aor, "tel:$sender", body, timestamp, MESSAGE_DOWN, 0, "", true).add()
                    mobileUa.account.unreadMessages = true
                }
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}

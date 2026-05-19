package com.tutpro.baresip.plus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.net.toUri

/**
 * MMS receiver that extracts text components from incoming MMS messages.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
            val contentType = intent.type
            if (contentType == "application/vnd.wap.mms-message") {
                Log.d(TAG, "Received MMS WAP Push Deliver")
                extractTextFromProvider(context)
            }
        }
    }

    private fun extractTextFromProvider(context: Context) {
        // Query the most recent MMS message
        val uri = "content://mms".toUri()
        val cursor = context.contentResolver.query(uri, null, null, null, "date DESC LIMIT 1")
        
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getString(c.getColumnIndexOrThrow("_id"))
                val date = c.getLong(c.getColumnIndexOrThrow("date")) * 1000 // mms date is in seconds
                
                // Get sender address
                val address = getMmsAddr(context, id) ?: "unknown"
                
                // Get text parts
                val body = getMmsText(context, id)
                
                if (body.isNotEmpty()) {
                    Log.d(TAG, "Extracted MMS text from $address: $body")
                    
                    val mobileUa = BaresipService.uas.value.find { it.account.isMobile }
                    if (mobileUa != null) {
                        // Notify Service for history update, notification, and alert sound
                        if (BaresipService.isServiceRunning) {
                            BaresipService.instance?.handleIncomingMessage(mobileUa.uap, "tel:$address", body, date)
                        } else {
                            // Service not running, at least save to history
                            val aor = mobileUa.account.aor
                            // Check if this message was already added (simple deduplication by timestamp)
                            val lastMsg = Message.messages().lastOrNull { m -> m.aor == aor }
                            if (lastMsg == null || lastMsg.timeStamp != date || lastMsg.peerUri != "tel:$address") {
                                Message(aor, "tel:$address", body, date, MESSAGE_DOWN, 0, "", true).add()
                                mobileUa.account.unreadMessages = true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getMmsAddr(context: Context, id: String): String? {
        val uri = "content://mms/$id/addr".toUri()
        val cursor = context.contentResolver.query(uri, null, "msg_id=$id", null, null)
        var addr: String? = null
        cursor?.use {
            if (it.moveToFirst()) {
                do {
                    val type = it.getInt(it.getColumnIndexOrThrow("type"))
                    if (type == 137) { // PDU_FROM
                        addr = it.getString(it.getColumnIndexOrThrow("address"))
                        break
                    }
                } while (it.moveToNext())
            }
        }
        return addr
    }

    private fun getMmsText(context: Context, id: String): String {
        val selectionPart = "mid=$id"
        val uri = "content://mms/part".toUri()
        val cursor = context.contentResolver.query(uri, null, selectionPart, null, null)
        val sb = StringBuilder()

        cursor?.use {
            while (it.moveToNext()) {
                val type = it.getString(it.getColumnIndexOrThrow("ct"))
                if (type == "text/plain") {
                    val data = it.getString(it.getColumnIndexOrThrow("_data"))
                    val body = if (data != null) {
                        getPartText(context, it.getString(it.getColumnIndexOrThrow("_id")))
                    } else {
                        it.getString(it.getColumnIndexOrThrow("text"))
                    }
                    if (body != null) sb.append(body)
                }
            }
        }
        return sb.toString()
    }

    private fun getPartText(context: Context, partId: String): String? {
        val partUri = "content://mms/part/$partId".toUri()
        return try {
            context.contentResolver.openInputStream(partUri)?.use { isStream ->
                isStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read MMS part $partId", e)
            null
        }
    }

    companion object {
        private const val TAG = "MmsReceiver"
    }
}

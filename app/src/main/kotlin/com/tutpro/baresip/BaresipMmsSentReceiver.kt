package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.klinker.android.send_message.MmsSentReceiver
import java.io.File

class BaresipMmsSentReceiver : MmsSentReceiver() {
    override fun onMessageStatusUpdated(context: Context, intent: Intent, resultCode: Int) {
        super.onMessageStatusUpdated(context, intent, resultCode)

        // Poll the next pending message from the queue
        val request = Utils.popPendingMms()
        val aor = request?.first ?: ""
        val time = request?.second ?: 0L

        Log.d("Baresip", "MMS Sent Result: $resultCode for $aor at $time")

        if (aor.isNotEmpty() && time != 0L) {
            if (resultCode == Activity.RESULT_OK)
                Message.updateMessageStatus(aor, time, MESSAGE_UP)
            else
                Message.updateMessageStatus(aor, time, MESSAGE_UP_FAIL, "MMS failed ($resultCode)")
        }

        // Cleanup temporary PDU file created by the library
        val filePath = intent.getStringExtra("file_path")
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
                Log.d("Baresip", "Deleted temporary MMS file: $filePath")
            }
        }

        // After MMS is finished, trigger a deferred network update if needed
        if (!Utils.isMmsInProgress) {
            Log.d("Baresip", "MMS finished, scheduling deferred network update")
            BaresipService.scheduleNetworkUpdate(5000L)
        }
    }
}

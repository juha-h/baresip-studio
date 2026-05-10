package com.tutpro.baresip

import android.content.Intent
import android.os.IBinder
import android.telecom.Call
import android.telecom.InCallService

class InCallService : InCallService() {

    override fun onBind(intent: Intent): IBinder? {
        instance = this
        return super.onBind(intent)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "InCallService: Call added")

        val handle = call.details.accountHandle
        val baresipHandle = BaresipService.getPhoneAccountHandle(this)

        if (handle == baresipHandle) {
            Log.d(TAG, "InCallService: Identified as SIP call")
            // The SIP call is already managed by ConnectionService/BaresipService.
            // We just need to ensure the InCallService stays bound.
        } else {
            Log.d(TAG, "InCallService: Identified as PSTN call from $handle")
            val aor = call.details.intentExtras?.getString("aor")
            BaresipService.instance?.handleExternalCall(call, aor)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "InCallService: Call removed")
        BaresipService.instance?.handleExternalCallRemoved(call)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "Baresip"
        var instance: InCallService? = null
    }
}

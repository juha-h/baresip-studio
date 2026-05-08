package com.tutpro.baresip

import android.telecom.Call
import android.telecom.InCallService

class InCallService : InCallService() {

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
            // This is a cellular call. We need to wrap it so MainScreen can show it.
            BaresipService.instance?.handleExternalCall(call)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "InCallService: Call removed")
        BaresipService.instance?.handleExternalCallRemoved(call)
    }

    companion object {
        private const val TAG = "Baresip"
    }
}

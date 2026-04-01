package com.tutpro.baresip

import android.app.Service
import android.content.Intent
import android.os.IBinder

// This is needed in order to allow choosing baresip as default Phone app
class InCallService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "InCallService onBind with intent: ${intent.action}")
        return null
    }
}

/*import android.telecom.InCallService
import android.telecom.Call

class InCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        // This is triggered when the system wants YOU to show the call UI
        Log.d("Baresip", "InCallService: Call added")
    }
}*/

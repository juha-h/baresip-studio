package com.tutpro.baresip

import android.app.Service
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.IBinder

// This is needed in order to allow choosing baresip as default Phone app

class InCallService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        // TODO("Return the communication channel to the service.")
        Log.d(TAG, "InCallService onBind with intent: ${intent.action}")
        return null
    }
}
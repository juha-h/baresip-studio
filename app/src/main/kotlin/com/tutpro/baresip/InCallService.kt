package com.tutpro.baresip

import android.content.Intent
import android.os.IBinder
import android.telecom.Call
import android.telecom.InCallService
import java.lang.ref.WeakReference

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
            // SIP call is already managed by ConnectionService/BaresipService
        }
        else {
            Log.d(TAG, "InCallService: Identified as PSTN call from $handle")
            val aor = call.details.intentExtras?.getString("aor")

            if (BaresipService.instance == null) {
                Log.i(TAG, "InCallService: BaresipService not running, starting it")
                val intent = Intent(this, BaresipService::class.java)
                intent.action = "Start"
                androidx.core.content.ContextCompat.startForegroundService(this, intent)
            }

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
        private var _instance = WeakReference<InCallService>(null)
        var instance: InCallService?
            get() = _instance.get()
            set(value) {
                _instance = WeakReference(value)
            }
    }
}

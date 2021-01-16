package com.tutpro.baresip.plus

import android.telecom.Call
import android.telecom.InCallService

class CallService : InCallService() {

    override fun onCallAdded(call: Call) {
        //OngoingCall.call = call
        Log.d("Baresip", "onCallAdded $call}")
        Log.d("Baresip", "onCallAdded HANDLE ${call.details.handle}")
        // CallActivity.start(this, call)
    }

    override fun onCallRemoved(call: Call) {
        Log.d("Baresip", "onCallAdded ${call.details}")
        //OngoingCall.call = null
    }
}
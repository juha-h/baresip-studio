package com.tutpro.baresip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "TaskReceiver: received intent ${intent.action}")
        var aor = intent.getStringExtra("aor")
        if (aor == null) {
            Log.i(TAG,"TaskReceiver: 'aor' extra is missing")
            return
        }
        if (!aor.startsWith("sip:"))
            aor = "sip:$aor"
        val ua = UserAgent.ofAor(aor)
        if (ua == null) {
            Log.i(TAG, "TaskReceiver: user agent of AoR $aor is not found")
            return
        }
        val acc = ua.account
        when (intent.action) {
            "com.tutpro.baresip.REGISTER" -> {
                Log.d(TAG, "TaskReceiver: registering $aor")
                Api.ua_register(ua.uap)
                acc.regint = 3600
            }
            "com.tutpro.baresip.UNREGISTER" -> {
                Log.d(TAG, "TaskReceiver: un-registering $aor")
                Api.ua_unregister(ua.uap)
                acc.regint = 0
            }
            else -> return
        }
        AccountsActivity.saveAccounts()
        Api.ua_update_account(ua.uap)
    }

}

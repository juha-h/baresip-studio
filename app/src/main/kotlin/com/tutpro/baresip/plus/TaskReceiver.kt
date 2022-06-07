package com.tutpro.baresip.plus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.*
import kotlin.concurrent.schedule

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
            "com.tutpro.baresip.plus.REGISTER" -> {
                Log.d(TAG, "TaskReceiver: registering $aor")
                Api.account_set_regint(acc.accp, REGISTRATION_INTERVAL)
                Api.ua_update_account(ua.uap)
                if (!Api.ua_isregistered(ua.uap))
                    Api.ua_register(ua.uap)
            }
            "com.tutpro.baresip.plus.UNREGISTER" -> {
                Log.d(TAG, "TaskReceiver: un-registering $aor")
                if (Api.ua_isregistered(ua.uap))
                    Api.ua_unregister(ua.uap)
                Api.account_set_regint(acc.accp,0)
                Api.ua_update_account(ua.uap)
            }
            else -> return
        }
    }

}

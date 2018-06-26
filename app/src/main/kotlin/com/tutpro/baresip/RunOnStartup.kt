package com.tutpro.baresip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

class RunOnStartup : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val i = Intent(context, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val b = Bundle()
            b.putBoolean("onstartup", true)
            i.putExtras(b)
            context.startActivity(i)
        }
    }

}
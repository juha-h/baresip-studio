package com.tutpro.baresip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

import java.io.File

class RunOnStartup : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if ((intent.action == Intent.ACTION_BOOT_COMPLETED) or
                (intent.action == "com.tutpro.baresip.Restart")) {
            val configFile = File(context.filesDir.absolutePath + "/config")
            val config = Utils.getFileContents(configFile)
            val asCv = Utils.getNameValue(config,"auto_start")
            if ((asCv.size > 0) && (asCv[0] == "yes")) {
                Log.d("Baresip", "Start baresip upon boot completed or restart")
                val i = Intent(context, MainActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val b = Bundle()
                b.putBoolean("onStartup", true)
                i.putExtras(b)
                context.startActivity(i)
            }
        }
    }

}
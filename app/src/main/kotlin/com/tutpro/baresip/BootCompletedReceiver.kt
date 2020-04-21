package com.tutpro.baresip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

import java.nio.charset.StandardCharsets

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("Baresip", "BootCompletedReceiver received intent ${intent.action}")
        if ((intent.action == Intent.ACTION_BOOT_COMPLETED) or
                (intent.action == "com.tutpro.baresip.Restart")) {
            val configPath = context.filesDir.absolutePath + "/config"
            val config = String(Utils.getFileContents(configPath)!!, StandardCharsets.ISO_8859_1)
            val asCv = Utils.getNameValue(config,"auto_start")
            if ((asCv.size > 0) && (asCv[0] == "yes")) {
                Log.i("Baresip", "Start baresip upon boot completed or restart")
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
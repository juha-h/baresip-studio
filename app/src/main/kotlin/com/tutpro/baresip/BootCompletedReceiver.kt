package com.tutpro.baresip

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

import java.nio.charset.StandardCharsets

class BootCompletedReceiver : BroadcastReceiver() {

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onReceive(context: Context, intent: Intent) {

        Log.i(TAG, "BootCompletedReceiver received intent ${intent.action}")

        val configPath = context.filesDir.absolutePath + "/config"
        val config = String(Utils.getFileContents(configPath)!!, StandardCharsets.ISO_8859_1)
        val asCv = Utils.getNameValue(config,"auto_start")

        if ((asCv.size > 0) && (asCv[0] == "yes")) {
            Log.i(TAG, "Start baresip upon boot completed")
            val i = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val b = Bundle()
                b.putBoolean("onStartup", true)
                putExtras(b)
            }
            context.startActivity(i)
        }

    }

}
package com.tutpro.baresip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

import java.nio.charset.StandardCharsets

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        Log.i(TAG, "BootCompletedReceiver received intent ${intent.action}")

        val configPath = context.filesDir.absolutePath + "/config"
        val config = Utils.getFileContents(configPath) ?: return
        val asCv = Utils.getNameValue(String(config, StandardCharsets.ISO_8859_1), "auto_start")
        if ((asCv.isNotEmpty()) && (asCv[0] == "yes")) {
            Log.i(TAG, "Start baresip service upon boot completed")
            val baresipService = Intent(context, BaresipService::class.java).apply {
                action = "Start"
                putExtra("onStartup", true)
            }
            ContextCompat.startForegroundService(context, baresipService)
        }

    }

}
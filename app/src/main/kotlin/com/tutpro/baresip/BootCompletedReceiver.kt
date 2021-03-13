package com.tutpro.baresip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings.Global.getString
import androidx.core.app.NotificationCompat

import java.nio.charset.StandardCharsets

const val START_NOTIFICATION_IN = 105

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        Log.i("Baresip", "BootCompletedReceiver received intent ${intent.action}")

        val configPath = context.filesDir.absolutePath + "/config"
        val config = String(Utils.getFileContents(configPath)!!, StandardCharsets.ISO_8859_1)
        val asCv = Utils.getNameValue(config,"auto_start")

        if ((asCv.size > 0) && (asCv[0] == "yes")) {

            Log.i("Baresip", "Start baresip upon boot completed")

            val i = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val b = Bundle()
                b.putBoolean("onStartup", true)
                putExtras(b)
            }

            if (Build.VERSION.SDK_INT < 29) {
                context.startActivity(i)
            } else {
                val nm = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel("default", "default",
                        NotificationManager.IMPORTANCE_DEFAULT)
                nm.createNotificationChannel(channel)
                val pi = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_ONE_SHOT)
                with(NotificationCompat.Builder(context, "default")) {
                    setSmallIcon(R.drawable.ic_stat)
                    setContentTitle(context.getString(R.string.app_name))
                    setContentText(context.getString(R.string.tap_to_start))
                    setContentIntent(pi)
                    setAutoCancel(true)
                    nm.notify(START_NOTIFICATION_IN, build())
                }
            }
        }

    }

}
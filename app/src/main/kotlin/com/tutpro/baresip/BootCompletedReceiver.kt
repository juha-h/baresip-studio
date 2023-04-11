package com.tutpro.baresip

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat

import java.nio.charset.StandardCharsets

class BootCompletedReceiver : BroadcastReceiver() {

    private val START_NOTIFICATION_IN = 105

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

            if (Build.VERSION.SDK_INT < 29) {
                context.startActivity(i)
            } else {
                val nm = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel("default", "default",
                        NotificationManager.IMPORTANCE_DEFAULT)
                nm.createNotificationChannel(channel)
                val pi = PendingIntent.getActivity(context, 0, i,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
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
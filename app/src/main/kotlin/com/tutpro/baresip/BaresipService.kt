package com.tutpro.baresip

import android.app.*
import android.app.Notification.VISIBILITY_PUBLIC
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.support.annotation.Keep
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import android.support.v4.content.LocalBroadcastManager
import android.os.Build
import android.support.v4.app.NotificationCompat.VISIBILITY_PRIVATE
import java.nio.charset.StandardCharsets
import kotlin.experimental.and
import android.R.attr.name
import java.nio.charset.Charset

class BaresipService: Service() {

    private val LOG_TAG = "Baresip Service"
    internal lateinit var intent: Intent
    internal lateinit var nm: NotificationManager
    internal lateinit var snb: NotificationCompat.Builder
    internal lateinit var npi: PendingIntent
    internal lateinit var nr: BroadcastReceiver
    internal lateinit var wl: PowerManager.WakeLock
    internal lateinit var fl: WifiManager.WifiLock

    override fun onCreate() {

        Log.d(LOG_TAG, "At onCreate")

        intent = Intent("com.tutpro.baresip.EVENT")
        intent.setPackage("com.tutpro.baresip")

        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        snb = NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)

        val ni = Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
        npi = PendingIntent.getActivity(this, 0, ni, 0)

        nr = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val intentExtras = intent.extras
                val info = intentExtras.getParcelable<NetworkInfo>("networkInfo")
                Log.d("Baresip", "Got event $info")
                if (info.isConnected) {
                    if (disconnected) {
                        UserAgent.register(MainActivity.uas)
                        disconnected = false
                    }
                } else {
                    disconnected = true
                }
            }
        }

        super.onCreate()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        Log.d(LOG_TAG, "Received onStartCommand action ${intent.getAction()}")

        when (intent.getAction()) {

            "Start" -> {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Baresip")
                wl.acquire()

                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                fl = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Baresip")

                val assets = arrayOf("accounts", "contacts", "config", "busy.wav", "callwaiting.wav",
                        "error.wav", "message.wav", "notfound.wav", "ring.wav", "ringback.wav")
                val path = applicationContext.filesDir.path
                var file = File(path)
                if (!file.exists()) {
                    Log.d(LOG_TAG, "Creating baresip directory")
                    try {
                        File(path).mkdirs()
                    } catch (e: Error) {
                        Log.e(LOG_TAG, "Failed to create directory: " + e.toString())
                    }

                }
                for (a in assets) {
                    file = File("$path/$a")
                    if (!file.exists()) {
                        Log.d(LOG_TAG, "Copying asset $a")
                        Utils.copyAssetToFile(applicationContext, a, "$path/$a")
                    } else {
                        Log.d(LOG_TAG, "Asset $a already copied")
                    }
                }

                file = File(path, "history")
                if (file.exists()) {
                    try {
                        val fis = FileInputStream(file)
                        val ois = ObjectInputStream(fis)
                        @SuppressWarnings("unchecked")
                        MainActivity.history = ois.readObject() as ArrayList<History>
                        Log.d(LOG_TAG, "Restored History of ${MainActivity.history.size} entries")
                        ois.close()
                        fis.close()
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "InputStream exception: - " + e.toString())
                    }
                }

                file = File(path, "messages")
                if (file.exists()) {
                    try {
                        val fis = FileInputStream(file)
                        val ois = ObjectInputStream(fis)
                        @SuppressWarnings("unchecked")
                        MainActivity.messages = ois.readObject() as ArrayList<Message>
                        Log.d(LOG_TAG, "Restored ${MainActivity.messages.size} messages")
                        ois.close()
                        fis.close()
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "InputStream exception: - " + e.toString())
                    }
                }

                ContactsActivity.generateContacts(path + "/contacts")

                Thread(Runnable { baresipStart(path) }).start()
                BaresipService.IS_SERVICE_RUNNING = true
                registerReceiver(nr, IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"))
                showStatusNotification()
                if (!RUN_FOREGROUNG) super.onStartCommand(intent, flags, startId)
            }

            "UpdateNotification" -> {
                updateStatusNotification()
            }

            "Stop" -> {
                cleanStop()
                if (RUN_FOREGROUNG) stopForeground(true)
                stopSelf()
            }

            "Kill" -> {
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.i(LOG_TAG, "In onDestroy")
        super.onDestroy()
        if (IS_SERVICE_RUNNING) {
            Log.i(LOG_TAG, "Restart baresip killed by Android")
            cleanStop()
            val broadcastIntent = Intent("com.tutpro.baresip.Restart")
            sendBroadcast(broadcastIntent)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val defaultChannel = NotificationChannel(DEFAULT_CHANNEL_ID, "Default",
                    NotificationManager.IMPORTANCE_DEFAULT)
            defaultChannel.description = "Tells that baresip is running"
            defaultChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(defaultChannel)
            val highChannel = NotificationChannel(HIGH_CHANNEL_ID, "High",
                    NotificationManager.IMPORTANCE_HIGH)
            highChannel.description = "Tells about incoming call or message"
            highChannel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            highChannel.enableVibration(true)
            nm.createNotificationChannel(highChannel)
        }
    }

    private fun showStatusNotification() {
        snb.setVisibility(VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(npi)
                .setOngoing(true)
                .setContent(RemoteViews(packageName, R.layout.status_notification))
        if (RUN_FOREGROUNG)
            startForeground(STATUS_NOTIFICATION_ID, snb.build())
        else
            nm.notify(STATUS_NOTIFICATION_ID, snb.build())
    }

    @Keep
    fun uaAdd(uap: String) {
        Log.d(LOG_TAG, "addUA at BaresipService")
        val ua = UserAgent(uap)
        MainActivity.uas.add(ua)
        if (UserAgent.ua_isregistered(uap)) {
            Log.d(LOG_TAG, "Ua ${ua.account.aor} is registered")
            MainActivity.images.add(R.drawable.dot_green)
        } else {
            Log.d(LOG_TAG, "Ua ${ua.account.aor} is NOT registered")
            MainActivity.images.add(R.drawable.dot_yellow)
            ua.register()
        }
        val intent = Intent("service event")
        intent.putExtra("event", "ua added")
        intent.putExtra("params", arrayListOf(uap))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        updateStatusNotification()
    }

    @Keep
    fun uaEvent(event: String, uap: String, callp: String) {
        Log.d(LOG_TAG, "updateStatus got event $event/$uap/$callp")
        if (!IS_SERVICE_RUNNING) return
        val ua = UserAgent.find(MainActivity.uas, uap)
        if (ua == null) {
            Log.w(LOG_TAG, "updateStatus did not find ua $uap")
            return
        }
        val aor = ua.account.aor
        for (account_index in MainActivity.uas.indices) {
            if (MainActivity.uas[account_index].account.aor == aor) {
                when (event) {
                    "registering" -> {
                        return
                    }
                    "registered" -> {
                        if (ua.account.regint == 0)
                            MainActivity.images[account_index] = R.drawable.dot_yellow
                        else
                            MainActivity.images[account_index] = R.drawable.dot_green
                        updateStatusNotification()
                        if (!Utils.isVisible()) return
                    }
                    "registering failed" -> {
                        MainActivity.images[account_index] = R.drawable.dot_red
                        updateStatusNotification()
                        if (!Utils.isVisible()) return
                    }
                    "unregistering" -> {
                        MainActivity.images[account_index] = R.drawable.dot_yellow
                        updateStatusNotification()
                        if (!Utils.isVisible()) return
                    }
                    "call ringing" -> {
                        return
                    }
                    "call incoming" -> {
                        if (!Utils.isVisible()) {
                            val cnb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                            cnb.setSmallIcon(R.drawable.ic_stat)
                                    .setColor(0x0ca1fd)
                                    .setContentIntent(npi)
                                    .setAutoCancel(true)
                                    .setContentTitle("Call from ${ContactsActivity.contactName(
                                            Api.call_peeruri(callp))}")
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                                cnb.setVibrate(LongArray(0))
                                        .setVisibility(VISIBILITY_PRIVATE)
                                        .setPriority(Notification.PRIORITY_HIGH)
                            }
                            /* val view = RemoteViews(getPackageName(), R.layout.call_notification)
                            view.setTextViewText(R.id.callFrom, "Call from ${Api.call_peeruri(callp)}")
                            cnb.setContent(view) */
                            val answerIntent = Intent(this, MainActivity::class.java)
                            answerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            answerIntent.putExtra("action", "answer")
                            answerIntent.putExtra("callp", callp)
                            val answerPendingIntent = PendingIntent.getActivity(this,
                                    0, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                            val rejectIntent = Intent(this, MainActivity::class.java)
                            rejectIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            rejectIntent.putExtra("action", "reject")
                            rejectIntent.putExtra("callp", callp)
                            val rejectPendingIntent = PendingIntent.getActivity(this,
                                    1, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                            /* view.setOnClickPendingIntent(R.id.answerButton, answerPendingIntent)
                            view.setOnClickPendingIntent(R.id.rejectButton, rejectPendingIntent)
                            cnb.setCustomBigContentView(view) */
                            cnb.addAction(R.drawable.ic_stat, "Answer", answerPendingIntent)
                            cnb.addAction(R.drawable.ic_stat, "Reject", rejectPendingIntent)
                            nm.notify(CALL_NOTIFICATION_ID, cnb.build())
                        }
                    }
                    "call established", "call closed" -> {
                        nm.cancel(CALL_NOTIFICATION_ID)
                    }
                }
            }
        }
        val intent = Intent("service event")
        intent.putExtra("event", event)
        intent.putExtra("params", arrayListOf(uap, callp))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @Keep
    fun messageEvent(uap: String, peer: String, msg: ByteArray) {
        var s = "Decoding of message failed!"
        try {
            s = String(msg, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "UTF-8 decode failed")
        }
        Log.d(LOG_TAG, "Message event $uap/$peer/$s")
        val timeStamp = System.currentTimeMillis().toString()
        if (!Utils.isVisible()) {
            val cnb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
            cnb.setSmallIcon(R.drawable.ic_stat)
                    .setColor(0x0ca1fd)
                    .setContentIntent(npi)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .setAutoCancel(true)
                    .setContentTitle("Message from ${ContactsActivity.contactName(peer)}")
                    .setContentText(s)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                cnb.setVibrate(LongArray(0))
                        .setVisibility(VISIBILITY_PRIVATE)
                        .setPriority(Notification.PRIORITY_HIGH)
            }
            /* val view = RemoteViews(getPackageName(), R.layout.call_notification)
            view.setTextViewText(R.id.callFrom, "Call from ${Api.call_peeruri(callp)}")
            cnb.setContent(view) */
            val replyIntent = Intent(this, MainActivity::class.java)
            replyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            replyIntent.putExtra("action", "reply")
            replyIntent.putExtra("uap", uap)
            replyIntent.putExtra("peer", peer)
            val replyPendingIntent = PendingIntent.getActivity(this,
                    0, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            val archiveIntent = Intent(this, MainActivity::class.java)
            archiveIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP)
            archiveIntent.putExtra("action", "archive")
            archiveIntent.putExtra("uap", uap)
            archiveIntent.putExtra("time", timeStamp)
            val archivePendingIntent = PendingIntent.getActivity(this,
                    1, archiveIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            val deleteIntent = Intent(this, MainActivity::class.java)
            deleteIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP)
            deleteIntent.putExtra("action", "delete")
            deleteIntent.putExtra("uap", uap)
            deleteIntent.putExtra("time", timeStamp)
            val deletePendingIntent = PendingIntent.getActivity(this,
                    2, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            /* view.setOnClickPendingIntent(R.id.answerButton, answerPendingIntent)
            view.setOnClickPendingIntent(R.id.rejectButton, rejectPendingIntent)
            cnb.setCustomBigContentView(view) */
            cnb.addAction(R.drawable.ic_stat, "Reply", replyPendingIntent)
            cnb.addAction(R.drawable.ic_stat, "Archive", archivePendingIntent)
            cnb.addAction(R.drawable.ic_stat, "Delete", deletePendingIntent)
            nm.notify(MESSAGE_NOTIFICATION_ID, cnb.build())
        }
        val intent = Intent("service event")
        intent.putExtra("event", "message")
        intent.putExtra("params", arrayListOf(uap, peer, s, timeStamp))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @Keep
    fun messageResponse(responseCode: Int, time: String) {
        Log.d(LOG_TAG, "Message response $responseCode at $time")
        val intent = Intent("message response")
        intent.putExtra("response code", responseCode)
        intent.putExtra("time", time)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun updateStatusNotification() {
        val contentView = RemoteViews(getPackageName(), R.layout.status_notification)
        for (i: Int in 0 .. 5)  {
            val resID = resources.getIdentifier("status$i", "id", packageName)
            if (i < MainActivity.images.size) {
                contentView.setImageViewResource(resID, MainActivity.images[i])
                contentView.setViewVisibility(resID, View.VISIBLE)
            } else {
                contentView.setViewVisibility(resID, View.INVISIBLE)
            }
        }
        if (MainActivity.images.size > 4)
            contentView.setViewVisibility(R.id.etc, View.VISIBLE)
        else
            contentView.setViewVisibility(R.id.etc, View.INVISIBLE)
        snb.setContent(contentView)
        nm.notify(STATUS_NOTIFICATION_ID, snb.build())
    }

    private fun cleanStop() {
        HistoryActivity.saveHistory()
        MessagesActivity.saveMessages()
        MainActivity.uas.clear()
        MainActivity.images.clear()
        MainActivity.history.clear()
        MainActivity.messages.clear()
        baresipStop()
        BaresipService.IS_SERVICE_RUNNING = false
        unregisterReceiver(nr)
        nm.cancelAll()
        if (wl.isHeld) wl.release()
        if (fl.isHeld) fl.release()
    }

    external fun baresipStart(path: String)
    external fun baresipStop()

    companion object {

        var IS_SERVICE_RUNNING = false
        val STATUS_NOTIFICATION_ID = 101
        val CALL_NOTIFICATION_ID = 102
        val MESSAGE_NOTIFICATION_ID = 103
        val DEFAULT_CHANNEL_ID = "com.tutpro.baresip.default"
        val HIGH_CHANNEL_ID = "com.tutpro.baresip.high"
        var disconnected = false
        val RUN_FOREGROUNG = false

    }

    init {
        System.loadLibrary("baresip")
    }
}

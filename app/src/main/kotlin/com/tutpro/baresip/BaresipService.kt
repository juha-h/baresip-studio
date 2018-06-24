package com.tutpro.baresip

import android.app.Notification
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import android.media.RingtoneManager
import android.media.Ringtone

class BaresipService: Service() {

    private val LOG_TAG = "Baresip Service"
    internal lateinit var intent: Intent
    internal lateinit var nm: NotificationManager
    internal lateinit var nb: NotificationCompat.Builder
    internal lateinit var ni: Intent
    internal lateinit var npi: PendingIntent
    internal lateinit var nr: BroadcastReceiver
    internal lateinit var wl: PowerManager.WakeLock
    internal lateinit var fl: WifiManager.WifiLock
    internal lateinit var rt: Ringtone

    override fun onCreate() {

        Log.d(LOG_TAG, "At onCreate")

        intent = Intent("com.tutpro.baresip.EVENT")
        intent.setPackage("com.tutpro.baresip")

        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nb = NotificationCompat.Builder(this)

        ni = Intent(this, MainActivity::class.java)
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

        val currentRtUri = RingtoneManager
                .getActualDefaultRingtoneUri(applicationContext, RingtoneManager.TYPE_RINGTONE)
        rt = RingtoneManager.getRingtone(applicationContext, currentRtUri)

        super.onCreate()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        when (intent.getAction()) {

            "Start" -> {
                Log.i(LOG_TAG, "Received Start Foreground Intent")

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

                ContactsActivity.generateContacts(path + "/contacts")

                Thread(Runnable { baresipStart(path) }).start()
                BaresipService.IS_SERVICE_RUNNING = true
                registerReceiver(nr, IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"))
                showNotification()
            }

            "UpdateNotification" -> {
                Log.i(LOG_TAG, "Received UpdateNotification")
                updateNotification()
            }

            "Stop" -> {
                Log.i(LOG_TAG, "Received Stop Foreground Intent")
                HistoryActivity.saveHistory()
                MainActivity.uas.clear()
                MainActivity.images.clear()
                MainActivity.history.clear()
                baresipStop()
                BaresipService.IS_SERVICE_RUNNING = false
                unregisterReceiver(nr)
                nm.cancelAll()
                if (wl.isHeld) wl.release()
                if (fl.isHeld) fl.release()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun showNotification() {
        nb.setVisibility(VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(npi)
                .setPriority(Notification.PRIORITY_HIGH)
                .setOngoing(true)
                .setContent(RemoteViews(packageName, R.layout.notification))
        startForeground(STATUS_NOTIFICATION_ID, nb.build())
    }

    @Keep
    fun addUA(uap: String) {
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
        intent.putExtra("uap", uap)
        intent.putExtra("callp", "")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        updateNotification()
    }

    @Keep
    fun updateStatus(event: String, uap: String, callp: String) {
        Log.d(LOG_TAG, "updateStatus got event $event")
        if (!IS_SERVICE_RUNNING) return
        val ua = UserAgent.find(MainActivity.uas, uap)
        if (ua == null) {
            Log.e(LOG_TAG, "updateStatus did not find ua $uap")
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
                        updateNotification()
                        if (!Utils.isVisible()) return
                    }
                    "registering failed" -> {
                        MainActivity.images[account_index] = R.drawable.dot_red
                        updateNotification()
                        if (!Utils.isVisible()) return
                    }
                    "unregistering" -> {
                        MainActivity.images[account_index] = R.drawable.dot_yellow
                        updateNotification()
                        if (!Utils.isVisible()) return
                    }
                    "call ringing" -> {
                        return
                    }
                    "call incoming" -> {
                        if (!Utils.isVisible()) {
                            nb.setVibrate(LongArray(0))
                            val view = RemoteViews(getPackageName(), R.layout.notification)
                            view.setTextViewText(R.id.incoming, "Call from ${Api.call_peeruri(callp)}")
                            view.setViewVisibility(R.id.incoming, View.VISIBLE)
                            nb.setContent(view)
                            nm.notify(STATUS_NOTIFICATION_ID, nb.build())
                        }
                        rt.play()
                    }
                    "call established", "call closed" -> {
                        nm.cancel(STATUS_NOTIFICATION_ID)
                        val view = RemoteViews(getPackageName(), R.layout.notification)
                        view.setViewVisibility(R.id.incoming, View.GONE)
                        nb.setContent(view)
                        nb.setVibrate(null)
                        nm.notify(STATUS_NOTIFICATION_ID, nb.build())
                        rt.stop()
                    }
                }
            }
        }
        val intent = Intent("service event")
        intent.putExtra("event", event)
        intent.putExtra("uap", uap)
        intent.putExtra("callp", callp)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.i(LOG_TAG, "In onDestroy")
        super.onDestroy()
    }

    fun updateNotification() {
        val contentView = RemoteViews(getPackageName(), R.layout.notification)
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
        nb.setContent(contentView)
        nm.notify(STATUS_NOTIFICATION_ID, nb.build())
    }

    external fun baresipStart(path: String)
    external fun baresipStop()

    companion object {

        var IS_SERVICE_RUNNING = false
        val STATUS_NOTIFICATION_ID = 101
        var disconnected = false

    }

    init {
        System.loadLibrary("baresip")
    }
}

package com.tutpro.baresip

import android.annotation.TargetApi
import android.app.*
import android.app.Notification.VISIBILITY_PUBLIC
import android.content.*
import android.media.*
import android.net.*
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.support.annotation.Keep
import android.support.v4.app.NotificationCompat
import android.view.View
import android.widget.RemoteViews
import android.support.v4.content.LocalBroadcastManager
import android.os.Build
import android.support.v4.app.NotificationCompat.VISIBILITY_PRIVATE
import android.support.v4.content.ContextCompat
import android.provider.Settings
import android.telephony.TelephonyManager

import java.io.File
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.roundToInt

class BaresipService: Service() {

    private val LOG_TAG = "Baresip Service"

    internal lateinit var intent: Intent
    internal lateinit var am: AudioManager
    internal lateinit var rt: Ringtone
    internal lateinit var nm: NotificationManager
    internal lateinit var snb: NotificationCompat.Builder
    internal lateinit var cm: ConnectivityManager
    internal lateinit var pm: PowerManager
    internal lateinit var tm: TelephonyManager
    internal lateinit var partialWakeLock: PowerManager.WakeLock
    internal lateinit var proximityWakeLock: PowerManager.WakeLock
    internal lateinit var fl: WifiManager.WifiLock

    internal var rtTimer: Timer? = null
    internal var audioFocusRequest: AudioFocusRequest? = null
    internal var audioFocused = false
    internal var origCallVolume = -1

    override fun onCreate() {

        Log.d(LOG_TAG, "At onCreate")

        intent = Intent("com.tutpro.baresip.EVENT")
        intent.setPackage("com.tutpro.baresip")

        filesPath = filesDir.absolutePath

        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val rtUri = RingtoneManager.getActualDefaultRingtoneUri(applicationContext,
                RingtoneManager.TYPE_RINGTONE)
        rt = RingtoneManager.getRingtone(applicationContext, rtUri)

        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        snb = NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)

        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()
        cm.registerNetworkCallback(
                builder.build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        Log.d(LOG_TAG, "Network '$network' is available")
                        if (dynDns) {
                            val linkProperties = cm.getLinkProperties(network)
                            val dnsServers = linkProperties.dnsServers
                            if (Config.updateDnsServers(dnsServers) != 0)
                                Log.w(LOG_TAG, "Failed to update DNS servers '$dnsServers'")
                        }
                        UserAgent.register()
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        Log.d(LOG_TAG, "Network '$network' is lost")
                    }

                    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                        super.onLinkPropertiesChanged(network, linkProperties)
                        Log.d(LOG_TAG, "Network $network link properties changed")
                        if (dynDns) {
                            val dnsServers = linkProperties.dnsServers
                            if (Config.updateDnsServers(dnsServers) != 0)
                                Log.w(LOG_TAG, "Failed to update DNS servers '$dnsServers'")
                        }
                        UserAgent.register()
                    }
                }
        )

        pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "com.tutpro.baresip:partial_wakelog")
        partialWakeLock.acquire()

        proximityWakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "com.tutpro.baresip:proximity_wakelog")

        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val action: String

        if (intent == null) {
            action = "Start"
            Log.d(LOG_TAG, "Received onStartCommand with null intent")
        } else {
            // Utils.dumpIntent(intent)
            action = intent.action!!
            Log.d(LOG_TAG, "Received onStartCommand action $action")
        }

        when (action) {

            "Start" -> {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                fl = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Baresip")

                val assets = arrayOf("accounts", "config", "contacts", "busy.wav", "callwaiting.wav",
                        "error.wav", "notfound.wav", "ring.wav", "ringback.wav")
                var file = File(filesPath)
                if (!file.exists()) {
                    Log.d(LOG_TAG, "Creating baresip directory")
                    try {
                        File(filesPath).mkdirs()
                    } catch (e: Error) {
                        Log.e(LOG_TAG, "Failed to create directory: " + e.toString())
                    }
                }
                for (a in assets) {
                    file = File("${filesPath}/$a")
                    if (!file.exists()) {
                        Log.d(LOG_TAG, "Copying asset '$a'")
                        Utils.copyAssetToFile(applicationContext, a, "$filesPath/$a")
                    } else {
                        Log.d(LOG_TAG, "Asset '$a' already copied")
                    }
                    if (a == "config") {
                        var dnsServers = listOf<InetAddress>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val activeNetwork = cm.activeNetwork
                            if (activeNetwork != null) {
                                dnsServers = cm.getLinkProperties(activeNetwork).dnsServers
                                Log.d(LOG_TAG, "DNS Servers = $dnsServers")
                            } else {
                                Log.d(LOG_TAG, "No active network!")
                            }
                        }
                        Config.initialize(dnsServers)
                    }
                }

                ContactsActivity.restoreContacts(applicationContext.filesDir, "contacts")

                Thread(Runnable { baresipStart(filesPath) }).start()
                isServiceRunning = true
                showStatusNotification()

                if (Config.variable("dyn_dns")[0] == "yes")
                    Config.remove("dns_server")
            }

            "Call Show", "Call Answer" -> {
                val newIntent = Intent(this, MainActivity::class.java)
                newIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                newIntent.putExtra("action", action.toLowerCase())
                newIntent.putExtra("callp", intent!!.getStringExtra("callp"))
                startActivity(newIntent)
            }

            "Call Reject" -> {
                val callp = intent!!.getStringExtra("callp")
                val call = Call.find(callp)
                if (call == null) {
                    Log.w(LOG_TAG, "onStartCommand did not find call $callp")
                } else {
                    val peerUri = call.peerURI
                    val aor = call.ua.account.aor
                    Log.d(LOG_TAG, "Aor $aor rejected incoming call $callp from $peerUri")
                    Api.ua_hangup(call.ua.uap, callp, 486, "Rejected")
                    CallHistory.add(CallHistory(aor, peerUri, "in", false))
                    CallHistory.save(filesPath)
                }
            }

            "Transfer Show", "Transfer Accept" -> {
                val uap = intent!!.getStringExtra("uap")
                val ua = UserAgent.find(uap)
                if (ua == null) {
                    Log.w(LOG_TAG, "onStartCommand did not find ua $uap")
                } else {
                    val newIntent = Intent(this, MainActivity::class.java)
                    newIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    newIntent.putExtra("action", action.toLowerCase())
                    newIntent.putExtra("callp", intent.getStringExtra("callp"))
                    newIntent.putExtra("uri", intent.getStringExtra("uri"))
                    startActivity(newIntent)
                    nm.cancel(BaresipService.TRANSFER_NOTIFICATION_ID)
                }
            }

            "Transfer Deny" -> {
                val callp = intent!!.getStringExtra("callp")
                val call = Call.find(callp)
                if (call == null)
                    Log.w(LOG_TAG, "onStartCommand did not find call $callp")
                else
                    Api.call_notify_sipfrag(callp, 603, "Decline")
                nm.cancel(BaresipService.TRANSFER_NOTIFICATION_ID)
            }

            "Message Show", "Message Reply" -> {
                val newIntent = Intent(this, MainActivity::class.java)
                newIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                newIntent.putExtra("action", action.toLowerCase())
                newIntent.putExtra("uap", intent!!.getStringExtra("uap"))
                newIntent.putExtra("peer", intent.getStringExtra("peer"))
                startActivity(newIntent)
                nm.cancel(BaresipService.MESSAGE_NOTIFICATION_ID)
            }

            "Message Save" -> {
                val uap = intent!!.getStringExtra("uap")
                val ua = UserAgent.find(uap)
                if (ua == null)
                    Log.w(LOG_TAG, "onStartCommand did not find UA $uap")
                else
                    ChatsActivity.saveUaMessage(ua.account.aor,
                            intent.getStringExtra("time").toLong(),
                            applicationContext.filesDir.absolutePath)
                nm.cancel(BaresipService.MESSAGE_NOTIFICATION_ID)
            }

            "Message Delete" -> {
                val uap = intent!!.getStringExtra("uap")
                val ua = UserAgent.find(uap)
                if (ua == null)
                    Log.w(LOG_TAG, "onStartCommand did not find UA $uap")
                else
                    ChatsActivity.deleteUaMessage(ua.account.aor,
                            intent.getStringExtra("time").toLong(),
                            applicationContext.filesDir.absolutePath)
                nm.cancel(BaresipService.MESSAGE_NOTIFICATION_ID)
            }

            "UpdateNotification" -> {
                updateStatusNotification()
            }

            "ToggleSpeaker" -> {
                Log.d(LOG_TAG, "Toggling speakerphone from $speakerPhone")
                am.isSpeakerphoneOn = !am.isSpeakerphoneOn
                speakerPhone = am.isSpeakerphoneOn
            }

            "ProximitySensing" -> {
                proximitySensing(intent!!.getBooleanExtra("enable", false))
            }

            "Stop", "Stop Force" -> {
                if (!isServiceClean) cleanService()
                if (isServiceRunning) baresipStop(action == "Stop Force")
            }

            "Kill" -> {
                if (!isServiceClean) cleanService()
                isServiceRunning = false
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "At Baresip Service onDestroy")
        super.onDestroy()
        cleanService()
        if (isServiceRunning) {
            val broadcastIntent = Intent("com.tutpro.baresip.Restart")
            sendBroadcast(broadcastIntent)
        }
    }

    @Keep
    fun uaAdd(uap: String) {
        Log.d(LOG_TAG, "uaAdd at BaresipService")
        val ua = UserAgent(uap)
        uas.add(ua)
        if (Api.ua_isregistered(uap)) {
            Log.d(LOG_TAG, "Ua ${ua.account.aor} is registered")
            status.add(R.drawable.dot_green)
        } else {
            Log.d(LOG_TAG, "Ua ${ua.account.aor} is NOT registered")
            status.add(R.drawable.dot_yellow)
        }
        val intent = Intent("service event")
        intent.putExtra("event", "ua added")
        intent.putExtra("params", arrayListOf(uap))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        updateStatusNotification()
    }

    @Keep
    fun uaEvent(event: String, uap: String, callp: String) {
        Log.d(LOG_TAG, "uaEvent got event $event/$uap/$callp")
        if (!isServiceRunning) return
        val ua = UserAgent.find(uap)
        if (ua == null) {
            Log.w(LOG_TAG, "uaEvent did not find ua $uap")
            return
        }
        val aor = ua.account.aor
        var newEvent: String? = null
        val ev = event.split(",")
        for (account_index in uas.indices) {
            if (uas[account_index].account.aor == aor) {
                when (ev[0]) {
                    "registering" -> {
                        return
                    }
                    "registered" -> {
                        if (ua.account.regint == 0)
                            status[account_index] = R.drawable.dot_yellow
                        else
                            status[account_index] = R.drawable.dot_green
                        updateStatusNotification()
                        if (!Utils.isVisible())
                            return
                    }
                    "registering failed" -> {
                        status[account_index] = R.drawable.dot_red
                        updateStatusNotification()
                            if ((ev.size > 1) && (ev[1] == "Invalid argument")) {
                                // Most likely this error is due to DNS lookup failure
                                newEvent = "registering failed,DNS lookup failed"
                                if (dynDns)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val activeNetwork = cm.activeNetwork
                                        if (activeNetwork != null) {
                                            val dnsServers = cm.getLinkProperties(activeNetwork).dnsServers
                                            Log.d(LOG_TAG, "Updating DNS Servers = $dnsServers")
                                            if (Config.updateDnsServers(dnsServers) != 0)
                                                Log.w(LOG_TAG, "Failed to update DNS servers '$dnsServers'")
                                        } else {
                                            Log.d(LOG_TAG, "No active network!")
                                        }
                                    }
                            }
                        if (!Utils.isVisible())
                            return
                    }
                    "unregistering" -> {
                        status[account_index] = R.drawable.dot_yellow
                        updateStatusNotification()
                        if (!Utils.isVisible())
                            return
                    }
                    "call progress", "call ringing" -> {
                        if (!isAudioFocused()) {
                            requestAudioFocus(AudioManager.STREAM_VOICE_CALL)
                            setCallVolume()
                        } else {
                            return
                        }
                    }
                    "call incoming" -> {
                        val peerUri = Api.call_peeruri(callp)
                        if ((Call.calls().size > 0) || (tm.callState != TelephonyManager.CALL_STATE_IDLE)) {
                            Log.d(LOG_TAG, "Auto-rejecting incoming call $uap/$callp/$peerUri")
                            Api.ua_hangup(uap, callp, 486, "Busy Here")
                            CallHistory.add(CallHistory(aor, peerUri, "in", false))
                            CallHistory.save(filesPath)
                            ua.account.missedCalls = true
                            if (!Utils.isVisible())
                                return
                            newEvent = "call rejected"
                        } else {
                            Log.d(LOG_TAG, "Incoming call $uap/$callp/$peerUri")
                            calls.add(Call(callp, ua, peerUri, "in", "incoming",
                                    Utils.dtmfWatcher(callp)))
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                Log.d(LOG_TAG, "CurrentInterruptionFilter ${nm.currentInterruptionFilter}")
                                if (nm.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL)
                                    startRinging()
                            } else {
                                startRinging()
                            }
                        }
                        if (!Utils.isVisible()) {
                            val intent = Intent(this, BaresipService::class.java)
                            intent.action = "Call Show"
                            intent.putExtra("callp", callp)
                            val pi = PendingIntent.getService(this, CALL_REQ_CODE, intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT)
                            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                            val caller = Utils.friendlyUri(ContactsActivity.contactName(peerUri),
                                    Utils.aorDomain(aor))
                            nb.setSmallIcon(R.drawable.ic_stat)
                                    .setColor(ContextCompat.getColor(this,
                                            R.color.colorBaresip))
                                    .setContentIntent(pi)
                                    .setCategory(Notification.CATEGORY_CALL)
                                    .setAutoCancel(true)
                                    .setOngoing(true)
                                    .setContentTitle(getString(R.string.incoming_call_from))
                                    .setContentText(caller)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                                nb.setVibrate(LongArray(0))
                                        .setVisibility(VISIBILITY_PRIVATE)
                                        .setPriority(Notification.PRIORITY_HIGH)
                            }
                            val answerIntent = Intent(this, BaresipService::class.java)
                            answerIntent.action = "Call Answer"
                            answerIntent.putExtra("callp", callp)
                            val answerPendingIntent = PendingIntent.getService(this,
                                    ANSWER_REQ_CODE, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                            val rejectIntent = Intent(this, BaresipService::class.java)
                            rejectIntent.action = "Call Reject"
                            rejectIntent.putExtra("callp", callp)
                            val rejectPendingIntent = PendingIntent.getService(this,
                                    REJECT_REQ_CODE, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                            nb.addAction(R.drawable.ic_stat, getString(R.string.answer), answerPendingIntent)
                            nb.addAction(R.drawable.ic_stat, getString(R.string.reject), rejectPendingIntent)
                            nm.notify(CALL_NOTIFICATION_ID, nb.build())
                            return
                        }
                    }
                    "call established" -> {
                        nm.cancel(CALL_NOTIFICATION_ID)
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.w(LOG_TAG, "Call $callp that is established is not found")
                            return
                        }
                        Log.d(LOG_TAG, "AoR $aor call $callp established")
                        call.status = "connected"
                        call.onhold = false
                        CallHistory.add(CallHistory(aor, call.peerURI, call.dir, true))
                        CallHistory.save(filesPath)
                        call.hasHistory = true
                        if (call.dir == "in") {
                            stopRinging()
                            am.mode = AudioManager.MODE_IN_COMMUNICATION
                        }
                        if (!isAudioFocused()) {
                            requestAudioFocus(AudioManager.STREAM_VOICE_CALL)
                            setCallVolume()
                        }
                        if (!Utils.isVisible())
                            return
                    }
                    "call verified", "call secure" -> {
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.w("Baresip", "Call $callp that is verified is not found")
                            return
                        }
                        if (ev[0] == "call secure") {
                            call.security = R.drawable.box_yellow
                        } else {
                            call.security = R.drawable.box_green
                            call.zid = ev[1]
                        }
                        if (!Utils.isVisible())
                            return
                    }
                    "call transfer" -> {
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.w(LOG_TAG, "Call $callp to be transferred is not found")
                            return
                        }
                        if (!Utils.isVisible()) {
                            val intent = Intent(this, BaresipService::class.java)
                            intent.action = "Transfer Show"
                            intent.putExtra("uap", uap)
                                    .putExtra("callp", callp)
                                    .putExtra("uri", ev[1])
                            val pi = PendingIntent.getService(this, TRANSFER_REQ_CODE,
                                    intent, PendingIntent.FLAG_UPDATE_CURRENT)
                            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                            val target = Utils.friendlyUri(ContactsActivity.contactName(ev[1]),
                                    Utils.aorDomain(aor))
                            nb.setSmallIcon(R.drawable.ic_stat)
                                    .setColor(ContextCompat.getColor(this, R.color.colorBaresip))
                                    .setContentIntent(pi)
                                    .setDefaults(Notification.DEFAULT_SOUND)
                                    .setAutoCancel(true)
                                    .setContentTitle(getString(R.string.transfer_request))
                                    .setContentText(target)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                                nb.setVibrate(LongArray(0))
                                        .setVisibility(VISIBILITY_PRIVATE)
                                        .setPriority(Notification.PRIORITY_HIGH)
                            }
                            val acceptIntent = Intent(this, BaresipService::class.java)
                            acceptIntent.action = "Transfer Accept"
                            acceptIntent.putExtra("uap", uap)
                                    .putExtra("callp", callp)
                                    .putExtra("uri", ev[1])
                            val acceptPendingIntent = PendingIntent.getService(this,
                                    ACCEPT_REQ_CODE, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                            val denyIntent = Intent(this, BaresipService::class.java)
                            denyIntent.action = "Transfer Deny"
                            denyIntent.putExtra("callp", callp)
                            val denyPendingIntent = PendingIntent.getService(this,
                                    DENY_REQ_CODE, denyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                            nb.addAction(R.drawable.ic_stat, getString(R.string.accept),
                                    acceptPendingIntent)
                            nb.addAction(R.drawable.ic_stat, getString(R.string.deny),
                                    denyPendingIntent)
                            nm.notify(TRANSFER_NOTIFICATION_ID, nb.build())
                            return
                        }
                    }
                    "call closed" -> {
                        nm.cancel(CALL_NOTIFICATION_ID)
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.d(LOG_TAG, "AoR $aor call $callp that is closed is not found")
                            return
                        }
                        Log.d(LOG_TAG, "AoR $aor call $callp is closed")
                        if (call.status == "incoming") stopRinging()
                        calls.remove(call)
                        if (!call.hasHistory) {
                            CallHistory.add(CallHistory(aor, call.peerURI, call.dir, false))
                            CallHistory.save(filesPath)
                            if (call.dir == "in") ua.account.missedCalls = true
                        }
                        if (Call.calls().size == 0) {
                            abandonAudioFocus()
                            resetCallVolume()
                            am.mode = AudioManager.MODE_NORMAL
                            if (am.isSpeakerphoneOn) am.isSpeakerphoneOn = false
                            speakerPhone = false
                            proximitySensing(false)
                        }
                        if (!Utils.isVisible())
                            return
                    }
                    "transfer failed" -> {
                        Log.d(LOG_TAG, "AoR $aor hanging up call $callp with ${ev[1]}")
                        Api.ua_hangup(uap, callp, 0, "")
                        return
                    }
                }
            }
        }
        if (newEvent == null) newEvent = event
        val intent = Intent("service event")
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra("event", newEvent)
        intent.putExtra("params", arrayListOf(uap, callp))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @Keep
    fun messageEvent(uap: String, peer: String, msg: ByteArray) {
        var text = "Decoding of message failed!"
        try {
            text = String(msg, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "UTF-8 decode failed")
        }
        val ua = UserAgent.find(uap)
        if (ua == null) {
            Log.w(LOG_TAG, "messageEvent did not find ua $uap")
            return
        }
        val timeStamp = System.currentTimeMillis().toString()
        Log.d(LOG_TAG, "Message event for $uap from $peer at $timeStamp")
        Message.add(Message(ua.account.aor, peer, text, timeStamp.toLong(),
                R.drawable.arrow_down_green, 0, "", true))
        Message.saveMessages(filesPath)
        ua.account.unreadMessages = true
        if (!Utils.isVisible()) {
            val intent = Intent(this, BaresipService::class.java)
            intent.action = "Message Show"
            intent.putExtra("uap", uap)
                    .putExtra("peer", peer)
            val pi = PendingIntent.getService(this, MESSAGE_REQ_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT)
            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
            val sender = Utils.friendlyUri(ContactsActivity.contactName(peer),
                    Utils.aorDomain(ua.account.aor))
            nb.setSmallIcon(R.drawable.ic_stat)
                    .setColor(ContextCompat.getColor(this, R.color.colorBaresip))
                    .setContentIntent(pi)
                    .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                    .setAutoCancel(true)
                    .setContentTitle(getString(R.string.message_from) + " " + sender)
                    .setContentText(text)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                nb.setVibrate(LongArray(0))
                        .setVisibility(VISIBILITY_PRIVATE)
                        .setPriority(Notification.PRIORITY_HIGH)
            }
            val replyIntent = Intent(this, BaresipService::class.java)
            replyIntent.action = "Message Reply"
            replyIntent.putExtra("uap", uap)
                    .putExtra("peer", peer)
            val replyPendingIntent = PendingIntent.getService(this,
                    REPLY_REQ_CODE, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            val saveIntent = Intent(this, BaresipService::class.java)
            saveIntent.action = "Message Save"
            saveIntent.putExtra("uap", uap)
                    .putExtra("time", timeStamp)
            val savePendingIntent = PendingIntent.getService(this,
                    SAVE_REQ_CODE, saveIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            val deleteIntent = Intent(this, BaresipService::class.java)
            deleteIntent.action = "Message Delete"
            deleteIntent.putExtra("uap", uap)
                    .putExtra("time", timeStamp)
            val deletePendingIntent = PendingIntent.getService(this,
                    DELETE_REQ_CODE, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            nb.addAction(R.drawable.ic_stat, "Reply", replyPendingIntent)
            nb.addAction(R.drawable.ic_stat, "Save", savePendingIntent)
            nb.addAction(R.drawable.ic_stat, "Delete", deletePendingIntent)
            nm.notify(MESSAGE_NOTIFICATION_ID, nb.build())
            return
        }
        val intent = Intent("service event")
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra("event", "message show")
        intent.putExtra("params", arrayListOf(uap, peer))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @Keep
    fun messageResponse(responseCode: Int, responseReason: String, time: String) {
        Log.d(LOG_TAG, "Message response '$responseCode $responseReason' at $time")
        val intent = Intent("message response")
        intent.putExtra("response code", responseCode)
        intent.putExtra("response reason", responseReason)
        intent.putExtra("time", time)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @Keep
    fun stopped(error: String) {
        Log.d(LOG_TAG, "'stopped' from baresip with error $error")
        isServiceRunning = false
        if (error == "ua_init") {
            Config.remove("sip_listen")
            Config.remove("sip_certificate")
            Config.remove("sip_cafile")
        }
        val intent = Intent("service event")
        intent.putExtra("event", "stopped")
        intent.putExtra("params", arrayListOf(error))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val defaultChannel = NotificationChannel(DEFAULT_CHANNEL_ID, "Default",
                    NotificationManager.IMPORTANCE_LOW)
            defaultChannel.lockscreenVisibility = VISIBILITY_PUBLIC
            nm.createNotificationChannel(defaultChannel)
            val highChannel = NotificationChannel(HIGH_CHANNEL_ID, "High",
                    NotificationManager.IMPORTANCE_HIGH)
            highChannel.lockscreenVisibility = VISIBILITY_PUBLIC
            highChannel.enableVibration(true)
            nm.createNotificationChannel(highChannel)
        }
    }

    private fun showStatusNotification() {
        val intent = Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
        val pi = PendingIntent.getActivity(this, STATUS_REQ_CODE, intent, 0)
        snb.setVisibility(VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(pi)
                .setOngoing(true)
                .setContent(RemoteViews(packageName, R.layout.status_notification))
        startForeground(STATUS_NOTIFICATION_ID, snb.build())
    }

    private fun updateStatusNotification() {
        val contentView = RemoteViews(getPackageName(), R.layout.status_notification)
        for (i: Int in 0..5) {
            val resID = resources.getIdentifier("status$i", "id", packageName)
            if (i < status.size) {
                contentView.setImageViewResource(resID, status[i])
                contentView.setViewVisibility(resID, View.VISIBLE)
            } else {
                contentView.setViewVisibility(resID, View.INVISIBLE)
            }
        }
        if (status.size > 4)
            contentView.setViewVisibility(R.id.etc, View.VISIBLE)
        else
            contentView.setViewVisibility(R.id.etc, View.INVISIBLE)
        snb.setContent(contentView)
        nm.notify(STATUS_NOTIFICATION_ID, snb.build())
    }

    private fun requestAudioFocus(streamType: Int) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && (audioFocusRequest == null)) {
            @TargetApi(26)
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setLegacyStreamType(streamType)
                    build()
                })
                build()
            }
            @TargetApi(26)
            if (am.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(LOG_TAG, "Audio focus granted for stream $streamType")
            } else {
                Log.d(LOG_TAG, "Audio focus denied")
                audioFocusRequest = null
            }
        } else {
            if (am.requestAudioFocus(null, streamType, AudioManager.AUDIOFOCUS_GAIN) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(LOG_TAG, "Audio focus granted for stream $streamType")
                audioFocused = true
            } else {
                Log.d(LOG_TAG, "Audio focus denied")
                audioFocused = false
            }
        }
    }

    private fun isAudioFocused(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            return audioFocusRequest != null
        else
            return audioFocused
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                am.abandonAudioFocusRequest(audioFocusRequest!!)
                audioFocusRequest = null
            }
        } else {
            if (audioFocused) {
                am.abandonAudioFocus(null)
                audioFocused = false
            }
        }
        if (isAudioFocused())
            Log.w(LOG_TAG, "Failed to abandon audio focus")
    }

    private fun startRinging() {
        am.mode = AudioManager.MODE_RINGTONE
        requestAudioFocus(AudioManager.STREAM_RING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            rt.isLooping = true
            rt.play()
        } else {
            rt.play()
            rtTimer = Timer()
            rtTimer!!.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (!rt.isPlaying()) {
                        rt.play()
                    }
                }
            }, 1000 * 1, 1000 * 1)
        }
    }

    private fun stopRinging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (rt.isPlaying) rt.stop()
        } else {
            if (rtTimer != null) {
                rtTimer!!.cancel()
                rtTimer = null
                if (rt.isPlaying) rt.stop()
            }
        }
        abandonAudioFocus()
    }

    private fun setCallVolume() {
        if (callVolume != 0) {
            origCallVolume = am.getStreamVolume(am.mode)
            am.setStreamVolume(am.mode,
                    (callVolume * 0.1 * am.getStreamMaxVolume(am.mode)).roundToInt(),
                    0)
            Log.d(LOG_TAG, "Original/new call volume of stream ${am.mode} is " +
                    "$origCallVolume/${am.getStreamVolume(am.mode)}")
        }
    }

    private fun resetCallVolume() {
        if (origCallVolume != -1) {
            am.setStreamVolume(am.mode, origCallVolume, 0)
            origCallVolume = -1
        }
        Log.d(LOG_TAG, "Call volume of stream ${am.mode} is ${am.getStreamVolume(am.mode)}")
    }

    private fun proximitySensing(enable: Boolean) {
        if (enable) {
            if (!proximityWakeLock.isHeld()) {
                Log.d(LOG_TAG, "Acquiring proximity wake lock")
                proximityWakeLock.acquire()
            } else {
                Log.d(LOG_TAG, "Proximity wake lock already acquired")
            }
        } else {
            if (proximityWakeLock.isHeld()) {
                proximityWakeLock.release()
                Log.d(LOG_TAG, "Released proximity wake lock")
            } else {
                Log.d(LOG_TAG, "Proximity wake lock is not held")
            }
        }
    }

    private fun cleanService() {
        uas.clear()
        status.clear()
        history.clear()
        messages.clear()
        if (this::nm.isInitialized)
            nm.cancelAll()
        if (this::partialWakeLock.isInitialized && partialWakeLock.isHeld)
            partialWakeLock.release()
        if (this::proximityWakeLock.isInitialized && proximityWakeLock.isHeld)
            proximityWakeLock.release()
        if (this::fl.isInitialized && fl.isHeld)
            fl.release()
        isServiceClean = true
    }

    external fun baresipStart(path: String)
    external fun baresipStop(force: Boolean)

    companion object {

        val STATUS_NOTIFICATION_ID = 101
        val CALL_NOTIFICATION_ID = 102
        val TRANSFER_NOTIFICATION_ID = 103
        val MESSAGE_NOTIFICATION_ID = 104

        val STATUS_REQ_CODE = 1
        val CALL_REQ_CODE = 2
        val ANSWER_REQ_CODE = 3
        val REJECT_REQ_CODE = 4
        val TRANSFER_REQ_CODE = 5
        val ACCEPT_REQ_CODE = 6
        val DENY_REQ_CODE = 7
        val MESSAGE_REQ_CODE = 8
        val REPLY_REQ_CODE = 9
        val SAVE_REQ_CODE = 10
        val DELETE_REQ_CODE = 11

        val DEFAULT_CHANNEL_ID = "com.tutpro.baresip.default"
        val HIGH_CHANNEL_ID = "com.tutpro.baresip.high"

        var isServiceRunning = false
        var libraryLoaded = false
        var isServiceClean = false
        var speakerPhone = false
        var callVolume = 0
        var dynDns = false

        var filesPath = ""
        var uas = ArrayList<UserAgent>()
        var status = ArrayList<Int>()
        var calls = ArrayList<Call>()
        var history = ArrayList<CallHistory>()
        var messages = ArrayList<Message>()
        var contacts = ArrayList<Contact>()
        var chatTexts: MutableMap<String, String> = mutableMapOf<String, String>()

    }

    init {
        if (!libraryLoaded) {
            Log.d(LOG_TAG, "Loading baresip library")
            System.loadLibrary("baresip")
            libraryLoaded = true
        }
    }
}

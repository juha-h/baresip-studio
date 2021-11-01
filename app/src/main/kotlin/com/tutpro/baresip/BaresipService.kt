package com.tutpro.baresip

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.*
import android.media.*
import android.net.*
import android.net.wifi.WifiManager
import android.os.*
import android.os.Build.VERSION
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import android.content.Intent
import android.content.BroadcastReceiver
import androidx.annotation.ColorRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule
import kotlin.math.roundToInt
import android.media.MediaPlayer

class BaresipService: Service() {

    internal lateinit var intent: Intent
    internal lateinit var am: AudioManager
    internal lateinit var rt: Ringtone
    private lateinit var nt: Ringtone
    private lateinit var nm: NotificationManager
    private lateinit var snb: NotificationCompat.Builder
    private lateinit var cm: ConnectivityManager
    private lateinit var pm: PowerManager
    private lateinit var wm: WifiManager
    private lateinit var tm: TelephonyManager
    private lateinit var partialWakeLock: PowerManager.WakeLock
    private lateinit var proximityWakeLock: PowerManager.WakeLock
    private lateinit var wifiLock: WifiManager.WifiLock
    private lateinit var bluetoothReceiver: BroadcastReceiver
    private lateinit var hotSpotReceiver: BroadcastReceiver

    private var rtTimer: Timer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusUsage = -1
    private var origVolume = mutableMapOf<Int, Int>()
    private val btAdapter = BluetoothAdapter.getDefaultAdapter()
    private var linkAddresses = mutableMapOf<String, String>()
    private var activeNetwork: Network? = null
    private var hotSpotIsEnabled = false
    private var hotSpotAddresses = mapOf<String, String>()
    private var mediaPlayer: MediaPlayer? = null

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {

        Log.d(TAG, "At onCreate")

        intent = Intent("com.tutpro.baresip.EVENT")
        intent.setPackage("com.tutpro.baresip")

        filesPath = filesDir.absolutePath

        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val rtUri = RingtoneManager.getActualDefaultRingtoneUri(applicationContext,
                RingtoneManager.TYPE_RINGTONE)
        rt = RingtoneManager.getRingtone(applicationContext, rtUri)

        val ntUri = RingtoneManager.getActualDefaultRingtoneUri(applicationContext,
                RingtoneManager.TYPE_NOTIFICATION)
        nt = RingtoneManager.getRingtone(applicationContext, ntUri)

        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        snb = NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)

        pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        // This is needed to keep service running also in Doze Mode
        partialWakeLock = pm.run {
            newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "com.tutpro.baresip:partial_wakelog"
            ).apply {
                acquire()
            }
        }

        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        cm.registerNetworkCallback(
                builder.build(),
                object : ConnectivityManager.NetworkCallback() {

                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        Log.d(TAG, "Network $network is available")
                        // If API >= 26, this will be followed by onCapabilitiesChanged
                        if (isServiceRunning && VERSION.SDK_INT < 26)
                            updateNetwork()
                    }

                    override fun onLosing(network: Network, maxMsToLive: Int) {
                        super.onLosing(network, maxMsToLive)
                        Log.d(TAG, "Network $network is losing after $maxMsToLive ms")
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        Log.d(TAG, "Network $network is lost")
                        if (isServiceRunning)
                            updateNetwork()
                    }

                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        super.onCapabilitiesChanged(network, caps)
                        Log.d(TAG, "Network $network capabilities changed: $caps")
                        if (isServiceRunning)
                            updateNetwork()
                    }

                    override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) {
                        super.onLinkPropertiesChanged(network, props)
                        Log.d(TAG, "Network $network link properties changed: $props")
                        if (isServiceRunning)
                            updateNetwork()
                    }

                }
        )

        wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        hotSpotIsEnabled = Utils.isHotSpotOn(wm)

        hotSpotReceiver = object : BroadcastReceiver() {
            override fun onReceive(contxt: Context, intent: Intent) {
                val action = intent.action
                if ("android.net.wifi.WIFI_AP_STATE_CHANGED" == action) {
                    val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0)
                    if (WifiManager.WIFI_STATE_ENABLED == state % 10) {
                        if (hotSpotIsEnabled) {
                            Log.d(TAG, "HotSpot is still enabled")
                        } else {
                            Log.d(TAG, "HotSpot is enabled")
                            hotSpotIsEnabled = true
                            Timer().schedule(1000) {
                                hotSpotAddresses = Utils.hotSpotAddresses()
                                Log.d(TAG, "HotSpot addresses $hotSpotAddresses")
                                if (hotSpotAddresses.isNotEmpty()) {
                                    for ((k, v) in hotSpotAddresses)
                                        if (Api.net_add_address_ifname(k, v) != 0)
                                            Log.e(TAG, "Failed to add $v address $k")
                                    Timer().schedule(2000) {
                                        Api.uag_reset_transp(register = true, reinvite = false)
                                    }
                                } else {
                                    Log.w(TAG, "Could not get hotspot addresses")
                                }
                            }
                        }
                    } else {
                        if (!hotSpotIsEnabled) {
                            Log.d(TAG, "HotSpot is still disabled")
                        } else {
                            Log.d(TAG, "HotSpot is disabled")
                            hotSpotIsEnabled = false
                            if (hotSpotAddresses.isNotEmpty()) {
                                for ((k, _) in hotSpotAddresses)
                                    if (Api.net_rm_address(k) != 0)
                                        Log.e(TAG, "Failed to remove address $k")
                                hotSpotAddresses = mapOf()
                                Api.uag_reset_transp(register = true, reinvite = false)
                            }
                        }
                    }
                }
            }
        }

        this.registerReceiver(hotSpotReceiver,
            IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"))

        tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        proximityWakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "com.tutpro.baresip:proximity_wakelog")

        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Baresip")
        wifiLock.setReferenceCounted(false)

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                                BluetoothHeadset.STATE_DISCONNECTED)
                        when (state) {
                            BluetoothHeadset.STATE_CONNECTED -> {
                                Log.d(TAG, "Bluetooth headset is connected")
                                if (isAudioFocused()) {
                                    // Without delay, SCO_AUDIO_STATE_CONNECTING ->
                                    // SCO_AUDIO_STATE_DISCONNECTED
                                    Timer("Sco", false).schedule(1000) {
                                        Log.d(TAG, "Starting Bluetooth SCO")
                                        am.startBluetoothSco()
                                    }
                                }
                            }
                            BluetoothHeadset.STATE_DISCONNECTED -> {
                                Log.d(TAG, "Bluetooth headset is disconnected")
                                if (am.isBluetoothScoOn) {
                                    Log.d(TAG, "Stopping Bluetooth SCO")
                                    am.stopBluetoothSco()
                                }
                            }

                        }
                    }
                    BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                                BluetoothHeadset.STATE_AUDIO_DISCONNECTED)
                        when (state) {
                            BluetoothHeadset.STATE_AUDIO_CONNECTED -> {
                                Log.d(TAG, "Bluetooth headset audio is connected")
                            }
                            BluetoothHeadset.STATE_AUDIO_DISCONNECTED -> {
                                Log.d(TAG, "Bluetooth headset audio is disconnected")
                                if (am.isBluetoothScoOn) {
                                    Log.d(TAG, "Stopping Bluetooth SCO")
                                    am.stopBluetoothSco()
                                }
                            }

                        }
                    }
                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                        val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
                        when (state) {
                            AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                                Log.d(TAG, "Bluetooth headset SCO is connecting")
                            }
                            AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                                Log.d(TAG, "Bluetooth headset SCO is connected")
                            }
                            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                                Log.d(TAG, "Bluetooth headset SCO is disconnected")
                                abandonAudioFocus()
                            }
                            AudioManager.SCO_AUDIO_STATE_ERROR -> {
                                Log.d(TAG, "Bluetooth headset SCO state ERROR")
                            }
                        }
                    }
                }
            }
        }

        if (btAdapter != null) {
            val filter = IntentFilter()
            filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
            filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            this.registerReceiver(bluetoothReceiver, filter)
        }

        super.onCreate()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val action: String

        if (intent == null) {
            action = "Start"
            Log.d(TAG, "Received onStartCommand with null intent")
        } else {
            // Utils.dumpIntent(intent)
            action = intent.action!!
            Log.d(TAG, "Received onStartCommand action $action")
        }

        when (action) {

            "Start" -> {

                updateDnsServers()

                val assets = arrayOf("accounts", "config", "contacts")
                var file = File(filesPath)
                if (!file.exists()) {
                    Log.d(TAG, "Creating baresip directory")
                    try {
                        File(filesPath).mkdirs()
                    } catch (e: Error) {
                        Log.e(TAG, "Failed to create directory: $e")
                    }
                }
                for (a in assets) {
                    file = File("${filesPath}/$a")
                    if (!file.exists()) {
                        Log.d(TAG, "Copying asset '$a'")
                        Utils.copyAssetToFile(applicationContext, a, "$filesPath/$a")
                    } else {
                        Log.d(TAG, "Asset '$a' already copied")
                    }
                    if (a == "config")
                        Config.initialize()
                }

                if (File(filesDir, "history").exists())
                    File(filesDir, "history").renameTo(File(filesDir, "calls"))

                Contact.restore()
                CallHistory.restore()
                Message.restore()

                linkAddresses = linkAddresses()
                var addrs = ""
                for (la in linkAddresses)
                    addrs = "$addrs;${la.key};${la.value}"

                Log.d(TAG, "Link addresses: $addrs")

                Thread {
                    baresipStart(filesPath, addrs.removePrefix(";"), logLevel)
                }.start()

                isServiceRunning = true

                showStatusNotification()

                if (AccountsActivity.noAccounts()) {
                    val newIntent = Intent(this, MainActivity::class.java)
                    newIntent.flags =
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NEW_TASK
                    newIntent.putExtra("action", "accounts")
                    startActivity(newIntent)
                }

                if (linkAddresses.isEmpty()) {
                    val newIntent = Intent(this, MainActivity::class.java)
                    newIntent.flags =
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NEW_TASK
                    newIntent.putExtra("action", "no network")
                    startActivity(newIntent)
                }

            }

            "Call Reject" -> {
                val callp = intent!!.getStringExtra("callp")!!
                val call = Call.ofCallp(callp)
                if (call == null) {
                    Log.w(TAG, "onStartCommand did not find call $callp")
                } else {
                    val peerUri = call.peerUri
                    val aor = call.ua.account.aor
                    Log.d(TAG, "Aor $aor rejected incoming call $callp from $peerUri")
                    Api.ua_hangup(call.ua.uap, callp, 486, "Rejected")
                    if (call.ua.account.callHistory) {
                        CallHistory.add(CallHistory(aor, peerUri, "in", false))
                        CallHistory.save()
                    }
                }
            }

            "Transfer Deny" -> {
                val callp = intent!!.getStringExtra("callp")!!
                val call = Call.ofCallp(callp)
                if (call == null)
                    Log.w(TAG, "onStartCommand did not find call $callp")
                else
                    call.notifySipfrag(603, "Decline")
                nm.cancel(TRANSFER_NOTIFICATION_ID)
            }

            "Message Save" -> {
                val uap = intent!!.getStringExtra("uap")!!
                val ua = UserAgent.ofUap(uap)
                if (ua == null)
                    Log.w(TAG, "onStartCommand did not find UA $uap")
                else
                    ChatsActivity.saveUaMessage(
                        ua.account.aor,
                        intent.getStringExtra("time")!!.toLong()
                    )
                nm.cancel(MESSAGE_NOTIFICATION_ID)
            }

            "Message Delete" -> {
                val uap = intent!!.getStringExtra("uap")!!
                val ua = UserAgent.ofUap(uap)
                if (ua == null)
                    Log.w(TAG, "onStartCommand did not find UA $uap")
                else
                    ChatsActivity.deleteUaMessage(
                        ua.account.aor,
                        intent.getStringExtra("time")!!.toLong()
                    )
                nm.cancel(MESSAGE_NOTIFICATION_ID)
            }

            "UpdateNotification" -> {
                updateStatusNotification()
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
        Log.d(TAG, "At Baresip Service onDestroy")
        super.onDestroy()
        this.unregisterReceiver(bluetoothReceiver)
        this.unregisterReceiver(hotSpotReceiver)
        if (am.isBluetoothScoOn) am.stopBluetoothSco()
        cleanService()
        if (isServiceRunning) {
            sendBroadcast(Intent("com.tutpro.baresip.Restart"))
        }
    }

    @Keep
    fun uaAdd(uap: String) {
        val ua = UserAgent(uap)
        Log.d(TAG, "uaAdd ${ua.account.aor} at BaresipService")
        uas.add(ua)
        if (ua.account.preferIPv6Media)
            Api.account_set_mediaaf(ua.account.accp, Api.AF_INET6)
        if (Api.ua_isregistered(uap)) {
            Log.d(TAG, "Ua ${ua.account.aor} is registered")
            status.add(R.drawable.dot_green)
        } else {
            Log.d(TAG, "Ua ${ua.account.aor} is NOT registered")
            if (ua.account.regint == 0)
                status.add(R.drawable.dot_white)
            else
                status.add(R.drawable.dot_yellow)
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    @Keep
    fun uaEvent(event: String, uap: String, callp: String) {
        if (!isServiceRunning) return
        val ua = UserAgent.ofUap(uap)
        if (ua == null) {
            Log.w(TAG, "uaEvent did not find ua $uap")
            return
        }
        Log.d(TAG, "got uaEvent $event/${ua.account.aor}/$callp")

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
                            status[account_index] = R.drawable.dot_white
                        else
                            status[account_index] = R.drawable.dot_green
                        updateStatusNotification()
                        if (!Utils.isVisible())
                            return
                    }
                    "registering failed" -> {
                        status[account_index] = R.drawable.dot_red
                        updateStatusNotification()
                        if (ev.size > 1 && ev[1] == "Invalid argument")
                            // Likely due to DNS lookup failure
                            newEvent = "registering failed,DNS lookup failed"
                        if (!Utils.isVisible())
                            return
                    }
                    "unregistering" -> {
                        status[account_index] = R.drawable.dot_white
                        updateStatusNotification()
                        if (!Utils.isVisible())
                            return
                    }
                    "call outgoing" -> {
                        stopMediaPlayer()
                        am.mode = AudioManager.MODE_IN_CALL
                        requestAudioFocus(AudioAttributes.USAGE_VOICE_COMMUNICATION,
                            AudioAttributes.CONTENT_TYPE_SPEECH)
                        setCallVolume()
                        proximitySensing(true)
                        return
                    }
                    "call ringing" -> {
                        playRingBack()
                    }
                    "call progress" -> {
                        if (ev[1] != "0")
                            stopMediaPlayer()
                        else
                            playRingBack()
                    }
                    "call incoming" -> {
                        val peerUri = Api.call_peeruri(callp)
                        if ((Call.calls().size > 0) ||
                                (tm.callState != TelephonyManager.CALL_STATE_IDLE) ||
                                !Utils.checkPermission(applicationContext,
                                        Manifest.permission.RECORD_AUDIO)) {
                            Log.d(TAG, "Auto-rejecting incoming call $uap/$callp/$peerUri")
                            Api.ua_hangup(uap, callp, 486, "Busy Here")
                            if (ua.account.callHistory) {
                                CallHistory.add(CallHistory(aor, peerUri, "in", false))
                                CallHistory.save()
                                ua.account.missedCalls = true
                            }
                            playUnInterrupted(R.raw.callwaiting, 1)
                            if (!Utils.isVisible())
                                return
                            newEvent = "call rejected"
                        } else {
                            Log.d(TAG, "Incoming call $uap/$callp/$peerUri")
                            Call(callp, ua, peerUri, "in", "incoming",
                                    Utils.dtmfWatcher(callp)).add()
                            if (ua.account.answerMode == Api.ANSWERMODE_MANUAL) {
                                if (VERSION.SDK_INT >= 23) {
                                    Log.d(TAG, "CurrentInterruptionFilter ${nm.currentInterruptionFilter}")
                                    if (nm.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL)
                                        startRinging()
                                } else {
                                    startRinging()
                                }
                            } else {
                                val newIntent = Intent(this, MainActivity::class.java)
                                newIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                newIntent.putExtra("action", "call answer")
                                newIntent.putExtra("callp", callp)
                                startActivity(newIntent)
                                return
                            }
                        }
                        if (!Utils.isVisible()) {
                            val intent = Intent(applicationContext, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            intent.putExtra("action", "call show")
                                .putExtra("callp", callp)
                            val pi = if (VERSION.SDK_INT >= 23)
                                PendingIntent.getActivity(applicationContext, CALL_REQ_CODE, intent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                            else
                                PendingIntent.getActivity(applicationContext, CALL_REQ_CODE, intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT)
                            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                            val caller = Utils.friendlyUri(ContactsActivity.contactName(peerUri),
                                    Utils.aorDomain(aor))
                            nb.setSmallIcon(R.drawable.ic_stat_call)
                                .setColor(ContextCompat.getColor(this, R.color.colorBaresip))
                                .setContentIntent(pi)
                                .setCategory(Notification.CATEGORY_CALL)
                                .setAutoCancel(true)
                                .setOngoing(true)
                                .setContentTitle(getString(R.string.incoming_call_from))
                                .setContentText(caller)
                                .setWhen(System.currentTimeMillis())
                                .setShowWhen(true)
                                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                .setFullScreenIntent(pi, true)
                            if (VERSION.SDK_INT < 26) {
                                @Suppress("DEPRECATION")
                                nb.setVibrate(LongArray(0))
                                    .priority = Notification.PRIORITY_HIGH
                            }
                            val answerIntent = Intent(applicationContext, MainActivity::class.java)
                            answerIntent.putExtra("action", "call answer")
                                .putExtra("callp", callp)
                            val api = if (VERSION.SDK_INT >= 23)
                                PendingIntent.getActivity(applicationContext, ANSWER_REQ_CODE, answerIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                            else
                                PendingIntent.getActivity(applicationContext, ANSWER_REQ_CODE, answerIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT)
                            val rejectIntent = Intent(this, BaresipService::class.java)
                            rejectIntent.action = "Call Reject"
                            rejectIntent.putExtra("callp", callp)
                            val rpi = if (VERSION.SDK_INT >= 23)
                                PendingIntent.getService(this, REJECT_REQ_CODE, rejectIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                            else
                                PendingIntent.getService(this, REJECT_REQ_CODE, rejectIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT)
                            nb.addAction(R.drawable.ic_stat_call,
                                    getActionText(R.string.answer, R.color.colorGreen), api)
                            nb.addAction(R.drawable.ic_stat_call_end,
                                    getActionText(R.string.reject, R.color.colorRed), rpi)
                            nm.notify(CALL_NOTIFICATION_ID, nb.build())
                            return
                        }
                    }
                    "call answered" -> {
                        stopRinging()
                        stopMediaPlayer()
                        if (am.mode != AudioManager.MODE_IN_CALL)
                            am.mode = AudioManager.MODE_IN_CALL
                        requestAudioFocus(AudioAttributes.USAGE_VOICE_COMMUNICATION,
                            AudioAttributes.CONTENT_TYPE_SPEECH)
                        setCallVolume()
                        proximitySensing(true)
                        return
                    }
                    "call established" -> {
                        nm.cancel(CALL_NOTIFICATION_ID)
                        val call = Call.ofCallp(callp)
                        if (call == null) {
                            Log.w(TAG, "Call $callp that is established is not found")
                            return
                        }
                        Log.d(TAG, "AoR $aor call $callp established")
                        call.status = "connected"
                        call.onhold = false
                        if (ua.account.callHistory) {
                            CallHistory.add(CallHistory(aor, call.peerUri, call.dir, true))
                            CallHistory.save()
                            call.hasHistory = true
                        }
                        if (!Utils.isVisible())
                            return
                    }
                    "call verified", "call secure" -> {
                        val call = Call.ofCallp(callp)
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
                        val call = Call.ofCallp(callp)
                        if (call == null) {
                            Log.w(TAG, "Call $callp to be transferred is not found")
                            return
                        }
                        if (!Utils.isVisible()) {
                            val intent = Intent(applicationContext, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            intent.putExtra("action", "transfer show")
                                .putExtra("callp", callp).putExtra("uri", ev[1])
                            val pi = if (VERSION.SDK_INT >= 23)
                                PendingIntent.getActivity(applicationContext, TRANSFER_REQ_CODE, intent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                            else
                                PendingIntent.getActivity(applicationContext, TRANSFER_REQ_CODE,
                                    intent, PendingIntent.FLAG_UPDATE_CURRENT)
                            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                            val target = Utils.friendlyUri(ContactsActivity.contactName(ev[1]),
                                    Utils.aorDomain(aor))
                            nb.setSmallIcon(R.drawable.ic_stat_call)
                                .setColor(ContextCompat.getColor(this, R.color.colorBaresip))
                                .setContentIntent(pi)
                                .setDefaults(Notification.DEFAULT_SOUND)
                                .setAutoCancel(true)
                                .setContentTitle(getString(R.string.transfer_request_to))
                                .setContentText(target)
                            if (VERSION.SDK_INT < 26)
                                @Suppress("DEPRECATION")
                                nb.setVibrate(LongArray(0))
                                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                                    .priority = Notification.PRIORITY_HIGH
                            val acceptIntent = Intent(applicationContext, MainActivity::class.java)
                            acceptIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            acceptIntent.putExtra("action","transfer accept")
                                .putExtra("callp", callp).putExtra("uri", ev[1])
                            val acceptPendingIntent = if (VERSION.SDK_INT >= 23)
                                PendingIntent.getActivity(applicationContext, ACCEPT_REQ_CODE,
                                    acceptIntent, PendingIntent.FLAG_IMMUTABLE or
                                            PendingIntent.FLAG_UPDATE_CURRENT)
                            else
                                PendingIntent.getActivity(applicationContext, ACCEPT_REQ_CODE,
                                    acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                            val denyIntent = Intent(this, BaresipService::class.java)
                            denyIntent.action = "Transfer Deny"
                            denyIntent.putExtra("callp", callp)
                            val denyPendingIntent = PendingIntent.getService(this,
                                    DENY_REQ_CODE, denyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                            nb.addAction(R.drawable.ic_stat_call, getString(R.string.accept), acceptPendingIntent)
                            nb.addAction(R.drawable.ic_stat_call_end, getString(R.string.deny), denyPendingIntent)
                            nm.notify(TRANSFER_NOTIFICATION_ID, nb.build())
                            return
                        }
                    }
                    "call closed" -> {
                        nm.cancel(CALL_NOTIFICATION_ID)
                        val call = Call.ofCallp(callp)
                        if (call == null) {
                            Log.d(TAG, "AoR $aor call $callp that is closed is not found")
                            return
                        }
                        Log.d(TAG, "AoR $aor call $callp is closed")
                        stopRinging()
                        stopMediaPlayer()
                        when (ev[2]) {
                            "busy" ->
                                playMedia(R.raw.busy, 1)
                            "error" ->
                                playMedia(R.raw.error, 1)
                        }
                        call.remove()
                        if (Call.calls().size == 0) {
                            resetCallVolume()
                            am.isSpeakerphoneOn = false
                            am.stopBluetoothSco()
                            abandonAudioFocus()
                            am.mode = AudioManager.MODE_NORMAL
                            proximitySensing(false)
                        }
                        if (ua.account.callHistory && !call.hasHistory) {
                            CallHistory.add(CallHistory(aor, call.peerUri, call.dir, false))
                            CallHistory.save()
                            if (call.dir == "in") ua.account.missedCalls = true
                        }
                        if (!Utils.isVisible() && !call.hasHistory && call.dir == "in") {
                            val caller = Utils.friendlyUri(ContactsActivity.contactName(call.peerUri),
                                    Utils.aorDomain(aor))
                            val intent = Intent(applicationContext, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            intent.putExtra("action", "call missed")
                                .putExtra("uap", uap)
                            val pi = if (VERSION.SDK_INT >= 23)
                                PendingIntent.getActivity(applicationContext, CALL_REQ_CODE, intent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                            else
                                PendingIntent.getActivity(applicationContext, CALL_REQ_CODE, intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT)
                            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                            nb.setSmallIcon(R.drawable.ic_stat_phone_missed)
                                .setColor(ContextCompat.getColor(this, R.color.colorBaresip))
                                .setContentIntent(pi)
                                .setCategory(Notification.CATEGORY_CALL)
                                .setAutoCancel(true)
                            if (VERSION.SDK_INT < 23) {
                                nb.setContentTitle(getString(R.string.missed_call_from))
                                nb.setContentText(caller)
                            } else {
                                var missedCalls = 0
                                for (notification in nm.activeNotifications)
                                    if (notification.id == CALL_MISSED_NOTIFICATION_ID)
                                        missedCalls++
                                if (missedCalls == 0) {
                                    nb.setContentTitle(getString(R.string.missed_call_from))
                                    nb.setContentText(caller)
                                } else {
                                    nb.setContentTitle(getString(R.string.missed_calls))
                                    nb.setContentText(
                                        String.format(getString(R.string.missed_calls_count),
                                            missedCalls + 1))
                                }
                            }
                            if (VERSION.SDK_INT < 26) {
                                @Suppress("DEPRECATION")
                                nb.setVibrate(LongArray(0))
                                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                                        .priority = Notification.PRIORITY_HIGH
                            }
                            nm.notify(CALL_MISSED_NOTIFICATION_ID, nb.build())
                            return
                        }
                    }
                    "refer failed" -> {
                        Log.d(TAG, "AoR $aor hanging up call $callp with ${ev[1]}")
                        Api.ua_hangup(uap, callp, 0, "")
                        val call = Call.ofCallp(callp)
                        if (call == null) {
                            Log.w(TAG, "Call $callp with failed refer is not found")
                        } else {
                            call.referTo = ""
                        }
                        if (!Utils.isVisible()) return
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

    @SuppressLint("UnspecifiedImmutableFlag")
    @Keep
    fun messageEvent(uap: String, peer: String, msg: ByteArray) {
        var text = "Decoding of message failed!"
        try {
            text = String(msg, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "UTF-8 decode failed")
        }
        val ua = UserAgent.ofUap(uap)
        if (ua == null) {
            Log.w(TAG, "messageEvent did not find ua $uap")
            return
        }
        val timeStamp = System.currentTimeMillis().toString()
        Log.d(TAG, "Message event for $uap from $peer at $timeStamp")
        Message(ua.account.aor, peer, text, timeStamp.toLong(),
                R.drawable.arrow_down_green, 0, "", true).add()
        Message.save()
        ua.account.unreadMessages = true
        if (!Utils.isVisible()) {
            val intent = Intent(applicationContext, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("action", "message show").putExtra("uap", uap)
                    .putExtra("peer", peer)
            val pi = if (VERSION.SDK_INT >= 23)
                PendingIntent.getActivity(applicationContext, MESSAGE_REQ_CODE, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            else
                PendingIntent.getActivity(applicationContext, MESSAGE_REQ_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT)
            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
            val sender = Utils.friendlyUri(ContactsActivity.contactName(peer),
                    Utils.aorDomain(ua.account.aor))
            nb.setSmallIcon(R.drawable.ic_stat_message)
                .setColor(ContextCompat.getColor(this, R.color.colorBaresip))
                .setContentIntent(pi)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setAutoCancel(true)
                .setContentTitle(getString(R.string.message_from) + " " + sender)
                .setContentText(text)
            if (VERSION.SDK_INT < 26) {
                @Suppress("DEPRECATION")
                nb.setVibrate(LongArray(0))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .priority = Notification.PRIORITY_HIGH
            }
            val replyIntent = Intent(applicationContext, MainActivity::class.java)
            replyIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            replyIntent.putExtra("action", "message reply")
                .putExtra("uap", uap).putExtra("peer", peer)
            val rpi = if (VERSION.SDK_INT >= 23)
                PendingIntent.getActivity(applicationContext, REPLY_REQ_CODE, replyIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            else
                PendingIntent.getActivity(applicationContext, REPLY_REQ_CODE, replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT)
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
            nb.addAction(R.drawable.ic_stat_reply, "Reply", rpi)
            nb.addAction(R.drawable.ic_stat_save, "Save", savePendingIntent)
            nb.addAction(R.drawable.ic_stat_delete, "Delete", deletePendingIntent)
            nm.notify(MESSAGE_NOTIFICATION_ID, nb.build())
            return
        } else {
            nt.play()
        }
        val intent = Intent("service event")
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra("event", "message show")
        intent.putExtra("params", arrayListOf(uap, peer))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @Keep
    @Suppress("UNUSED")
    fun messageResponse(responseCode: Int, responseReason: String, time: String) {
        Log.d(TAG, "Message response '$responseCode $responseReason' at $time")
        val intent = Intent("message response")
        intent.putExtra("response code", responseCode)
        intent.putExtra("response reason", responseReason)
        intent.putExtra("time", time)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @Keep
    fun getPassword(aor: String): String {
        if (!isServiceRunning) return ""
        Log.d(TAG, "getPassword of $aor")
        return if (MainActivity.aorPasswords[aor] != null)
            MainActivity.aorPasswords[aor]!!
        else
            ""
    }

    @Keep
    fun started() {
        Log.d(TAG, "Received 'started' from baresip")
        Api.net_debug()
        val intent = Intent("service event")
        intent.putExtra("event", "started")
        intent.putExtra("params", arrayListOf(callActionUri))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        callActionUri = ""
        if (VERSION.SDK_INT >= 23)
            Log.d(TAG, "Battery optimizations are ignored: " +
                    "${pm.isIgnoringBatteryOptimizations(applicationContext.packageName)}")
        Log.d(TAG, "Partial wake lock/wifi lock is held: " +
                "${partialWakeLock.isHeld}/${wifiLock.isHeld}")
    }

    @Keep
    fun stopped(error: String) {
        Log.d(TAG, "Received 'stopped' from baresip with param '$error'")
        isServiceRunning = false
        val intent = Intent("service event")
        intent.putExtra("event", "stopped")
        intent.putExtra("params", arrayListOf(error))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannels() {
        if (VERSION.SDK_INT >= 26) {
            val defaultChannel = NotificationChannel(DEFAULT_CHANNEL_ID, "Default",
                    NotificationManager.IMPORTANCE_LOW)
            defaultChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(defaultChannel)
            val highChannel = NotificationChannel(HIGH_CHANNEL_ID, "High",
                    NotificationManager.IMPORTANCE_HIGH)
            highChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            highChannel.enableVibration(true)
            nm.createNotificationChannel(highChannel)
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun showStatusNotification() {
        val intent = Intent(applicationContext, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
        val pi = if (VERSION.SDK_INT >= 23)
            PendingIntent.getActivity(applicationContext, STATUS_REQ_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE)
        else
            PendingIntent.getActivity(applicationContext, STATUS_REQ_CODE, intent, 0)
        snb.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(pi)
                .setOngoing(true)
                .setContent(RemoteViews(packageName, R.layout.status_notification))
        startForeground(STATUS_NOTIFICATION_ID, snb.build())
    }

    private fun updateStatusNotification() {
        val contentView = RemoteViews(packageName, R.layout.status_notification)
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
        // Don't know why, but without the delay the notification is not always updated
        Timer().schedule(250) {
            nm.notify(STATUS_NOTIFICATION_ID, snb.build())
        }
    }

    private fun getActionText(@StringRes stringRes: Int, @ColorRes colorRes: Int): Spannable {
        val spannable: Spannable = SpannableString(applicationContext.getText(stringRes))
        if (VERSION.SDK_INT >= 25) {
            spannable.setSpan(
                    ForegroundColorSpan(applicationContext.getColor(colorRes)),
                    0, spannable.length, 0)
        }
        return spannable
    }

    private fun requestAudioFocus(usage: Int, type: Int) {
        if (audioFocusUsage != -1) {
            if (audioFocusUsage == usage)
                return
            else
                abandonAudioFocus()
        }
        if ((VERSION.SDK_INT >= 26) && (audioFocusRequest == null)) {
            @TargetApi(26)
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .run {
                        setAudioAttributes(AudioAttributes.Builder().run {
                            setUsage(usage)
                            setContentType(type)
                            build()
                        })
                build()
            }
            @TargetApi(26)
            if (am.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "Audio focus granted for usage $usage")
                audioFocusUsage = usage
                if (isBluetoothHeadsetConnected() && !am.isBluetoothScoOn) {
                    Log.d(TAG, "Starting Bluetooth SCO")
                    am.startBluetoothSco()
                }
            } else {
                Log.d(TAG, "Audio focus denied")
                audioFocusRequest = null
                audioFocusUsage = -1
            }
        } else {
            @Suppress("DEPRECATION")
            if (am.requestAudioFocus(null, usage, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "Audio focus granted for usage $usage")
                audioFocusUsage = usage
                if (isBluetoothHeadsetConnected() && !am.isBluetoothScoOn)
                    am.startBluetoothSco()
            } else {
                Log.d(TAG, "Audio focus denied")
                audioFocusUsage = -1
            }
        }
    }

    private fun isBluetoothHeadsetConnected(): Boolean {
        return (btAdapter != null) && btAdapter.isEnabled &&
                (btAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) ==
                        BluetoothHeadset.STATE_CONNECTED)
    }

    private fun isAudioFocused(): Boolean {
        return audioFocusUsage != -1
    }

    private fun abandonAudioFocus() {
        if (VERSION.SDK_INT >= 26) {
            if (audioFocusRequest != null) {
                if (am.abandonAudioFocusRequest(audioFocusRequest!!) ==
                        AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.d(TAG, "Audio focus abandoned")
                    audioFocusRequest = null
                    audioFocusUsage = -1
                } else {
                    Log.d(TAG, "Failed to abandon audio focus")
                }
            }
        } else {
            if (audioFocusUsage != -1) {
                @Suppress("DEPRECATION")
                if (am.abandonAudioFocus(null) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.d(TAG, "Audio focus abandoned")
                    audioFocusUsage = -1
                } else {
                    Log.d(TAG, "Failed to abandon audio focus")
                }
            }
        }
    }

    private fun startRinging() {
        am.mode = AudioManager.MODE_RINGTONE
        requestAudioFocus(AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
            AudioAttributes.CONTENT_TYPE_MUSIC)
        if (VERSION.SDK_INT >= 28) {
            rt.isLooping = true
            rt.play()
        } else {
            rt.play()
            rtTimer = Timer()
            rtTimer!!.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (!rt.isPlaying) {
                        rt.play()
                    }
                }
            }, 1000, 1000)
        }
    }

    private fun stopRinging() {
        if (am.mode == AudioManager.MODE_RINGTONE)
            abandonAudioFocus()
        if ((VERSION.SDK_INT < 28) && (rtTimer != null)) {
            rtTimer!!.cancel()
            rtTimer = null
        }
        rt.stop()
    }

    private fun playRingBack() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.ringback)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        }
    }

    private fun playMedia(raw: Int, count: Int) {
        if (mediaPlayer == null ) {
            mediaPlayer = MediaPlayer.create(this, raw)
            mediaPlayer?.setOnCompletionListener {
                stopMediaPlayer()
                if (count > 1) playMedia(raw, count - 1)
            }
            mediaPlayer?.start()
        }
    }

    private fun playUnInterrupted(raw: Int, count: Int) {
        val player = MediaPlayer.create(this, raw)
        player.setOnCompletionListener {
            it.stop()
            it.release()
            if (count > 1) playUnInterrupted(raw, count - 1)
        }
        player.start()
    }

    private fun stopMediaPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun setCallVolume() {
        if (callVolume != 0 && origVolume.isEmpty()) {
            for (streamType in listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_VOICE_CALL)) {
                origVolume[streamType] = am.getStreamVolume(streamType)
                am.setStreamVolume(
                    streamType,
                    (callVolume * 0.1 * am.getStreamMaxVolume(streamType)).roundToInt(),
                    0
                )
                Log.d(TAG, "Orig/new $streamType volume is " +
                        "${origVolume[streamType]}/${am.getStreamVolume(streamType)}")
            }
        }
    }

    private fun resetCallVolume() {
        for ((streamType, streamVolume) in origVolume) {
            am.setStreamVolume(streamType, streamVolume, 0)
            Log.d(TAG, "Reset $streamType volume to ${am.getStreamVolume(streamType)}")
        }
        origVolume.clear()
    }

    @SuppressLint("WakelockTimeout")
    private fun proximitySensing(enable: Boolean) {
        if (enable) {
            if (!proximityWakeLock.isHeld) {
                Log.d(TAG, "Acquiring proximity wake lock")
                proximityWakeLock.acquire()
            } else {
                Log.d(TAG, "Proximity wake lock already acquired")
            }
        } else {
            if (proximityWakeLock.isHeld) {
                proximityWakeLock.release()
                Log.d(TAG, "Released proximity wake lock")
            } else {
                Log.d(TAG, "Proximity wake lock is not held")
            }
        }
    }

    private fun updateNetwork() {

        if (!isServiceRunning)
            return

        /* for (n in cm.allNetworks)
            Log.d(TAG, "NETWORK $n with caps ${cm.getNetworkCapabilities(n)} and props " +
                    "${cm.getLinkProperties(n)} is active ${isNetworkActive(n)}") */

        updateDnsServers()

        val lnAddrs = linkAddresses()

        Log.d(TAG, "Old/new link addresses $linkAddresses/$lnAddrs")

        var added = 0
        for (a in lnAddrs)
            if (!linkAddresses.containsKey(a.key)) {
                if (Api.net_add_address_ifname(a.key, a.value) != 0)
                    Log.e(TAG, "Failed to add address: $a")
                else
                    added++
            }
        var removed = 0
        for (a in linkAddresses)
            if (!lnAddrs.containsKey(a.key)) {
                if (Api.net_rm_address(a.key) != 0)
                    Log.e(TAG, "Failed to remove address: $a")
                else
                    removed++
            }

        val active = activeNetwork()
        Log.d(TAG, "Added/Removed/Active = $added/$removed/$active")

        if (added > 0 || removed > 0 || active != activeNetwork) {
            linkAddresses = lnAddrs
            activeNetwork = active
            Api.uag_reset_transp(register = true, reinvite = true)
        }

        Api.net_debug()

        if (activeNetwork != null) {
            val caps = cm.getNetworkCapabilities(activeNetwork)
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Log.d(TAG, "Acquiring WiFi Lock")
                wifiLock.acquire()
                return
            }
        }
        Log.d(TAG, "Releasing WiFi Lock")
        wifiLock.release()
    }

    private fun linkAddresses(): MutableMap<String, String> {
        val lnAddrs = mutableMapOf<String, String>()
        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (VERSION.SDK_INT < 28 ||
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)) {
                    val props = cm.getLinkProperties(n) ?: continue
                    for (la in props.linkAddresses)
                        if (la.scope == android.system.OsConstants.RT_SCOPE_UNIVERSE &&
                            props.interfaceName != null)
                                lnAddrs[la.address.hostAddress] = props.interfaceName!!
            }
        }
        if (hotSpotIsEnabled) {
            hotSpotAddresses = Utils.hotSpotAddresses()
            Log.d(TAG, "HotSpot addresses $hotSpotAddresses")
            for ((k, v) in hotSpotAddresses)
                lnAddrs[k] = v
        }
        return lnAddrs
    }

    private fun updateDnsServers() {
        if (isServiceRunning && !dynDns)
            return
        val servers = mutableListOf<InetAddress>()
        // Use DNS servers first from active network (if available)
        for (n in cm.allNetworks)
            if (isNetworkActive(n)) {
                val linkProps = cm.getLinkProperties(n)
                if (linkProps != null) {
                    servers.addAll(linkProps.dnsServers)
                    break
                }
            }
        // Then add DNS servers from the other networks
        for (n in cm.allNetworks) {
            if (isNetworkActive(n)) continue
            val linkProps = cm.getLinkProperties(n)
            if (linkProps != null)
                for (server in linkProps.dnsServers)
                    if (!servers.contains(server)) servers.add(server)
        }
        // Update if change
        if (servers != dnsServers) {
            if (isServiceRunning && Config.updateDnsServers(servers) != 0) {
                Log.w(TAG, "Failed to update DNS servers '${servers}'")
            } else {
                // Log.d(TAG, "Updated DNS servers: '${servers}'")
                dnsServers = servers
            }
        }
    }

    private fun activeNetwork(): Network? {
        return if (VERSION.SDK_INT >= 23)
            cm.activeNetwork
        else {
            for (n in cm.allNetworks)
                if (isNetworkActive(n)) return n
            return null
        }
    }

    private fun isNetworkActive(network: Network): Boolean {
        if (VERSION.SDK_INT >= 23)
            return network == cm.activeNetwork
        @Suppress("DEPRECATION")
        if ((cm.activeNetworkInfo != null) && (cm.getNetworkInfo(network) != null))
            return cm.activeNetworkInfo!!.toString() == cm.getNetworkInfo(network)!!.toString()
        return false
    }

    private fun cleanService() {
        am.mode = AudioManager.MODE_NORMAL
        abandonAudioFocus()
        stopRinging()
        stopMediaPlayer()
        uas.clear()
        status.clear()
        callHistory.clear()
        messages.clear()
        if (this::nm.isInitialized)
            nm.cancelAll()
        if (this::partialWakeLock.isInitialized && partialWakeLock.isHeld)
            partialWakeLock.release()
        if (this::proximityWakeLock.isInitialized && proximityWakeLock.isHeld)
            proximityWakeLock.release()
        if (this::wifiLock.isInitialized)
            wifiLock.release()
        isServiceClean = true
    }

    private external fun baresipStart(path: String, addrs: String, logLevel: Int)
    private external fun baresipStop(force: Boolean)

    companion object {

        var isServiceRunning = false
        var isConfigInitialized = false
        var libraryLoaded = false
        var isServiceClean = false
        var callVolume = 0
        var dynDns = false
        var filesPath = ""
        var logLevel = 2
        var sipTrace = false
        var callActionUri = ""

        val uas = ArrayList<UserAgent>()
        val status = ArrayList<Int>()
        val calls = ArrayList<Call>()
        var callHistory = ArrayList<CallHistory>()
        var messages = ArrayList<Message>()
        val contacts = ArrayList<Contact>()
        val chatTexts: MutableMap<String, String> = mutableMapOf()
        val activities = mutableListOf<String>()
        var dnsServers = listOf<InetAddress>()

    }

    init {
        if (!libraryLoaded) {
            Log.d(TAG, "Loading baresip library")
            System.loadLibrary("baresip")
            libraryLoaded = true
        }
    }
}

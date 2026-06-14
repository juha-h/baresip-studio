@file:Suppress("DEPRECATION")
package com.tutpro.baresip

import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.ImageDecoder
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.MODE_IN_COMMUNICATION
import android.media.AudioManager.MODE_IN_CALL
import android.media.AudioManager.MODE_NORMAL
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build.VERSION
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.provider.Settings
import android.system.OsConstants
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.MessagingStyle
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import com.tutpro.baresip.Utils.e164Uri
import com.tutpro.baresip.Utils.toCircle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.GregorianCalendar
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class BaresipService: Service() {

    internal lateinit var intent: Intent
    private lateinit var am: AudioManager
    private lateinit var nt: Ringtone
    private lateinit var nm: NotificationManager
    private lateinit var snb: NotificationCompat.Builder
    private lateinit var cm: ConnectivityManager
    private lateinit var pm: PowerManager
    private lateinit var wm: WifiManager
    private lateinit var tm: TelecomManager
    private lateinit var btm: BluetoothManager
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var telephonyCallback: TelephonyCallback
    @Suppress("DEPRECATION")
    private lateinit var phoneStateListener: PhoneStateListener
    private lateinit var vibrator: Vibrator
    private lateinit var partialWakeLock: PowerManager.WakeLock
    private lateinit var proximityWakeLock: PowerManager.WakeLock
    private lateinit var wifiLock: WifiManager.WifiLock
    private lateinit var bluetoothReceiver: BroadcastReceiver
    private lateinit var hotSpotReceiver: BroadcastReceiver
    private lateinit var airplaneModeReceiver: BroadcastReceiver
    private lateinit var androidContactsObserver: ContentObserver
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private lateinit var stopState: String
    private lateinit var quitTimer: CountDownTimer

    private var vbTimer: Timer? = null
    private var origVolume = mutableMapOf<Int, Int>()
    private var linkAddresses = mutableMapOf<String, String>()
    private var activeNetwork: Network? = null
    private var allNetworks = mutableSetOf<Network>()
    private var hotSpotIsEnabled = false
    private var hotSpotAddresses = mapOf<String, String>()
    private var mediaPlayer: MediaPlayer? = null
    private var androidContactsObserverRegistered = false
    private var hotSpotReceiverRegistered = false
    private var bluetoothReceiverRegistered = false
    private var airplaneModeReceiverRegistered = false
    private var telephonyCallbackRegistered = false
    private var isNotificationInCall = false
    private var isServiceClean = false
    private var cleanupRunnable: Runnable? = null

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "BaresipService onCreate")
        instance = this

        intent = Intent("com.tutpro.baresip.EVENT")
        intent.setPackage("com.tutpro.baresip")

        filesPath = filesDir.absolutePath
        pName = packageName

        am = getSystemService(AUDIO_SERVICE) as AudioManager

        val ntUri = RingtoneManager.getActualDefaultRingtoneUri(this,
            RingtoneManager.TYPE_NOTIFICATION)
        nt = RingtoneManager.getRingtone(this, ntUri)

        val rtUri = if (Preferences(this).ringtoneUri == "")
            Settings.System.DEFAULT_RINGTONE_URI
        else
            Preferences(this).ringtoneUri!!.toUri()
        rt = RingtoneManager.getRingtone(this, rtUri)

        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        snb = NotificationCompat.Builder(this, LOW_CHANNEL_ID)

        pm = getSystemService(POWER_SERVICE) as PowerManager

        vibrator = if (VERSION.SDK_INT >= 31) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as
                    VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        // This is needed to keep service running also in Doze Mode
        partialWakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "com.tutpro.baresip:wakelock"
        ).apply { setReferenceCounted(false) }

        networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "Network $network is available")
                synchronized(allNetworks) {
                    if (network !in allNetworks)
                        allNetworks.add(network)
                }
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                super.onLosing(network, maxMsToLive)
                Log.d(TAG, "Network $network is losing after $maxMsToLive ms")
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "Network $network is lost")
                synchronized(allNetworks) {
                    if (network in allNetworks)
                        allNetworks.remove(network)
                }
                if (isServiceRunning)
                    updateNetwork()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, caps)
                synchronized(allNetworks) {
                    if (network !in allNetworks)
                        allNetworks.add(network)
                }
                if (isServiceRunning)
                    updateNetwork()
            }

            override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) {
                super.onLinkPropertiesChanged(network, props)
                Log.d(TAG, "Network $network link properties changed: $props")
                synchronized(allNetworks) {
                    if (network !in allNetworks)
                        allNetworks.add(network)
                }
                if (isServiceRunning)
                    updateNetwork()
            }
        }

        cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerNetworkCallback(
            NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(),
            networkCallback
        )

        wm = getSystemService(WIFI_SERVICE) as WifiManager
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
                                    var reset = false
                                    for ((k, v) in hotSpotAddresses)
                                        if (afMatch(k))
                                            if (Api.net_add_address_ifname(k, v) != 0)
                                                Log.e(TAG, "Failed to add $v address $k")
                                            else
                                                reset = true
                                    if (reset)
                                        Timer().schedule(2000) {
                                            updateNetwork()
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
                                updateNetwork()
                            }
                        }
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            this,
            hotSpotReceiver,
            IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        hotSpotReceiverRegistered = true

        airplaneModeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                    val isAirplaneModeOn = intent.getBooleanExtra("state", false)
                    Log.d(TAG, "Airplane mode changed: $isAirplaneModeOn")
                    updateMobileStatus()
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            airplaneModeReceiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        airplaneModeReceiverRegistered = true

        tm = getSystemService(TELECOM_SERVICE) as TelecomManager

        btm = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btm.adapter

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                                BluetoothHeadset.STATE_DISCONNECTED)
                        when (state) {
                            BluetoothHeadset.STATE_CONNECTED -> {
                                Log.d(TAG, "Bluetooth headset is connected")
                                if (audioFocusRequest != null)
                                    startBluetoothSco(this@BaresipService, 1000L, 3)
                            }
                            BluetoothHeadset.STATE_DISCONNECTED -> {
                                Log.d(TAG, "Bluetooth headset is disconnected")
                                if (audioFocusRequest != null)
                                    stopBluetoothSco(this@BaresipService)
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
                                resetCallVolume()
                            }
                            AudioManager.SCO_AUDIO_STATE_ERROR -> {
                                Log.d(TAG, "Bluetooth headset SCO state ERROR")
                            }
                        }
                    }
                }
            }
        }

        val bluetoothFilter = IntentFilter()
        bluetoothFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        bluetoothFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        bluetoothFilter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            bluetoothFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
        bluetoothReceiverRegistered = true

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        if (VERSION.SDK_INT >= 31) {
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                override fun onServiceStateChanged(serviceState: ServiceState) {
                    updateMobileStatusFromServiceState(serviceState.state)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onServiceStateChanged(serviceState: ServiceState) {
                    updateMobileStatusFromServiceState(serviceState.state)
                }
            }
        }

        proximityWakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "com.tutpro.baresip:proximity_wakelock")

        wifiLock = if (VERSION.SDK_INT < 29)
            @Suppress("DEPRECATION")
            wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Baresip")
        else
            wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Baresip")
        wifiLock.setReferenceCounted(false)

        androidContactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(self: Boolean) {
                Log.d(TAG, "Android contacts change")
                if (contactsMode != "baresip") {
                    Contact.loadAndroidContacts(this@BaresipService)
                    Contact.contactsUpdate()
                }
            }
        }

        stopState = "initial"
        quitTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                Log.d(TAG, "Seconds remaining: ${millisUntilFinished / 1000}")
            }
            override fun onFinish() {
                when (stopState) {
                    "initial" -> {
                        if (isServiceRunning)
                            baresipStop(true)
                        stopState = "force"
                        quitTimer.start()
                    }
                    "force" -> {
                        cleanService()
                        isServiceRunning = false
                        postServiceEvent(
                            ServiceEvent("stopped", arrayListOf(""), System.nanoTime())
                        )
                        stopSelf()
                        // exitProcess(0)
                    }
                }
            }
        }

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

                isStartReceived = true

                if (VERSION.SDK_INT < 31) {
                    @Suppress("DEPRECATION")
                    allNetworks = cm.allNetworks.toMutableSet()
                }

                updateDnsServers()
                updatePartialWakeLock()

                val assets = arrayOf("accounts", "config", "contacts")
                var file = File(filesPath)
                if (!file.exists()) {
                    Log.i(TAG, "Creating baresip directory")
                    try {
                        File(filesPath).mkdirs()
                    } catch (e: Error) {
                        Log.e(TAG, "Failed to create directory: ${e.message}")
                    }
                }
                for (a in assets) {
                    file = File("${filesPath}/$a")
                    if (!file.exists() && a != "config") {
                        Log.i(TAG, "Copying asset '$a'")
                        Utils.copyAssetToFile(this, a, "$filesPath/$a")
                    }
                    else
                        Log.i(TAG, "Asset '$a' already copied")
                    if (a == "config")
                        Config.initialize(this)
                }

                if (contactsMode != "android")
                    Contact.restoreBaresipContacts()
                if (contactsMode != "baresip") {
                    Contact.loadAndroidContacts(this)
                    registerAndroidContactsObserver()
                }
                Contact.contactsUpdate()

                val history = CallHistory.get()
                if (history.isEmpty()) {
                    CallHistoryNew.restore()
                } else {
                    for (old in history) {
                        val new = CallHistoryNew(old.aor, old.peerUri, old.direction)
                        new.stopTime = old.stopTime
                        new.startTime = old.startTime
                        new.recording = old.recording
                        new.add()
                    }
                }

                Blocked.restore()

                val recordings = File(filesDir, "recordings")

                val restored = File(filesPath, "restored")
                if (restored.exists()) {
                    Log.d(TAG, "Clearing recordings")
                    CallHistoryNew.clearRecordings()
                    CallHistoryNew.save()
                    if (recordings.exists())
                        recordings.deleteRecursively()
                    restored.delete()
                }

                File(filesDir, "recordings").mkdir()
                File(filesDir, "tmp").mkdir()

                Message.restore()

                hotSpotAddresses = Utils.hotSpotAddresses()
                linkAddresses = linkAddresses()
                var addresses = ""
                for (la in linkAddresses)
                    addresses = "$addresses;${la.key};${la.value}"
                Log.i(TAG, "Link addresses: $addresses")
                activeNetwork = cm.activeNetwork
                Log.i(TAG, "Active network: $activeNetwork")

                registerPhoneAccount()

                Log.i(TAG, "AEC/AGC/NS available = $aecAvailable/$agcAvailable/$nsAvailable")

                val userAgent = Config.variable("user_agent")
                Thread {
                    baresipStart(
                        filesPath,
                        addresses.removePrefix(";"),
                        logLevel,
                        if (userAgent != "")
                            userAgent
                        else
                            "baresip v${BuildConfig.VERSION_NAME} " +
                                "(Android ${VERSION.RELEASE}/${System.getProperty("os.arch") ?: "?"})"
                    )
                }.start()

                isServiceRunning = true

                showStatusNotification()

                if (linkAddresses.isEmpty())
                    toast(getString(R.string.no_network), Toast.LENGTH_LONG)

                if (!aecAvailable)
                    toast(getString(R.string.no_aec), Toast.LENGTH_LONG)
            }

            "Notification Dismissed" -> {
                updateStatusNotification()
            }

            "Start Content Observer" -> {
                registerAndroidContactsObserver()
            }

            "Stop Content Observer" -> {
                unRegisterAndroidContactsObserver()
            }

            "Call Answer" -> {
                val callp = intent!!.getLongExtra("callp", 0L)
                val call = Call.ofCallp(callp)
                stopRinging()
                stopMediaPlayer()
                setCallVolume()
                proximitySensing(proximitySensing)
                call?.answer()
                updateStatusNotification()
            }

            "Call Reject" -> {
                val callp = intent!!.getLongExtra("callp", 0L)
                val call = Call.ofCallp(callp)
                stopRinging()
                stopMediaPlayer()
                if (call == null) {
                    Log.w(TAG, "onStartCommand did not find call $callp")
                } else {
                    val peerUri = call.peerUri
                    val aor = call.ua.account.aor
                    Log.d(TAG, "Aor $aor rejected incoming call $callp from $peerUri")
                    call.reject()
                }
            }

            "Call Hangup" -> {
                val callp = intent!!.getLongExtra("callp", 0L)
                Log.d(TAG, "onStartCommand Hangup action for $callp")
                val call = Call.ofCallp(callp)
                call?.hangup(0, "")
            }

            "Transfer Deny" -> {
                val callp = intent!!.getLongExtra("callp", 0L)
                val call = Call.ofCallp(callp)
                if (call == null)
                    Log.w(TAG, "onStartCommand did not find call $callp")
                else
                    call.notifySipfrag(603, "Decline")
                nm.cancel(TRANSFER_NOTIFICATION_ID)
            }

            "Message Save" -> {
                val uap = intent!!.getLongExtra("uap", 0L)
                val ua = UserAgent.ofUap(uap)
                if (ua == null)
                    Log.w(TAG, "onStartCommand did not find UA $uap")
                else {
                    Message.updateAorMessage(
                        ua.account.aor,
                        intent.getStringExtra("time")!!.toLong()
                    )
                    ua.account.unreadMessages = Message.unreadMessages(ua.account.aor)
                }
                nm.cancel(MESSAGE_NOTIFICATION_ID)
            }

            "Message Delete" -> {
                val uap = intent!!.getLongExtra("uap", 0L)
                val ua = UserAgent.ofUap(uap)
                if (ua == null)
                    Log.w(TAG, "onStartCommand did not find UA $uap")
                else {
                    Message.deleteAorMessage(
                        ua.account.aor,
                        intent.getStringExtra("time")!!.toLong()
                    )
                    ua.account.unreadMessages = Message.unreadMessages(ua.account.aor)
                }
                nm.cancel(MESSAGE_NOTIFICATION_ID)
            }

            "Message Inline Reply" -> {
                val remoteInputResults = RemoteInput.getResultsFromIntent(intent!!)
                if (remoteInputResults != null) {
                    val replyText = remoteInputResults.getCharSequence(KEY_TEXT_REPLY)?.toString()
                    if (!replyText.isNullOrEmpty()) {
                        val uap = intent.getLongExtra("uap", -1L)
                        val ua = UserAgent.ofUap(uap)!!
                        val aor = ua.account.aor
                        var peerUri = intent.getStringExtra("peer")!!
                        val timeStamp = intent.getLongExtra("time", 0L)
                        if (Utils.isTelUri(peerUri)) {
                            if (ua.account.telProvider == "") {
                                Log.w(TAG, "No telephony provider for $aor")
                                peerUri = ""
                            } else
                                peerUri = Utils.telToSip(peerUri, ua.account)
                        }
                        if (peerUri != "") {
                            Log.d(TAG, "Direct Reply from $aor to $peerUri: $replyText")
                            Message.updateAorMessage(aor, timeStamp)
                            val time = System.currentTimeMillis()
                            val msg = Message(
                                aor,
                                peerUri,
                                replyText,
                                time,
                                MESSAGE_UP_WAIT,
                                0,
                                "",
                                false
                            )
                            msg.add()
                            if (Api.message_send(uap, peerUri, replyText, time.toString()) != 0) {
                                Log.w(TAG, "message_send failed")
                                msg.direction = MESSAGE_UP_FAIL
                                msg.responseReason = getString(R.string.message_failed)
                            }
                            else {
                                ua.account.unreadMessages = Message.unreadMessages(aor)
                            }
                        }
                    }
                }
                nm.cancel(MESSAGE_NOTIFICATION_ID)
            }

            "Update Notification" -> {
                updateStatusNotification()
            }

            "Start Call" -> {
                val uap = intent!!.getLongExtra("uap", 0L)
                val uri = intent.getStringExtra("uri")!!
                val conferenceCall = intent.getBooleanExtra("conferenceCall", false)
                val onHoldCallp = intent.getLongExtra("onHoldCallp", 0L)
                if (!requestAudioFocus(this)) {
                    toast(getString(R.string.audio_focus_denied))
                    return START_STICKY
                }
                runCall(uap, uri, conferenceCall, onHoldCallp)
            }

            "Stop" -> {
                cleanService()
                if (isServiceRunning) {
                    baresipStop(false)
                    quitTimer.start()
                }
            }

            else -> {
                Log.e(TAG, "Unknown start action $action")
            }
        }

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val currentNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        when (currentNightMode) {
            Configuration.UI_MODE_NIGHT_NO -> {
                darkTheme.value = Preferences(this).displayTheme == AppCompatDelegate.MODE_NIGHT_YES
            }
            Configuration.UI_MODE_NIGHT_YES -> {
                darkTheme.value = true
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy at Baresip Service")
        cleanService()
        instance = null
        if (isServiceRunning)
            sendBroadcast(Intent("com.tutpro.baresip.Restart"))
    }

    @Suppress("unused")
    @SuppressLint("UnspecifiedImmutableFlag", "DiscouragedApi", "FullScreenIntentPolicy")
    @Keep
    fun uaEvent(event: String, uap: Long, callp: Long) {

        if (!isServiceRunning) return

        val ev = event.split(",")

        if (ev[0] == "create") {

            val ua = UserAgent(uap)
            ua.status = if (ua.account.isMobile)
                R.drawable.circle_green
            else if (ua.account.regint == 0)
                R.drawable.circle_white
            else
                circleYellow.getValue(colorblind)
            ua.add()

            val acc = ua.account
            if (acc.authUser != "" && acc.authPass == NO_AUTH_PASS) {
                val password = aorPasswords[acc.aor]
                if (password != null) {
                    Api.account_set_auth_pass(acc.accp, password)
                    acc.authPass = password
                } else {
                    Api.account_set_auth_pass(acc.accp, NO_AUTH_PASS)
                }
            }

            Log.d(TAG, "got uaEvent $event/${acc.aor}")
            return
        }

        if (ev[0] == "sndfile dump") {
            Log.d(TAG, "Got sndfile dump ${ev[1]}")
            if (Call.inCall()) {
                if (ev[1].endsWith("enc.wav"))
                    Call.calls()[0].dumpfiles[0] = ev[1]
                else
                    Call.calls()[0].dumpfiles[1] = ev[1]
            }
            return
        }

        if (ev[0] == "recorder sessionid") {
            val sessionId = ev[1].toInt()
            if (sessionId > 0 && sessionId != recorderSessionId) {
                Log.d(TAG, "got new recorder sessionid $sessionId (was $recorderSessionId)")
                recorderSessionId = sessionId
                aec?.release()
                aec = null
                agc?.release()
                agc = null
                ns?.release()
                ns = null
                if (aecAvailable) {
                    try {
                        aec = AcousticEchoCanceler.create(sessionId)
                        if (aec != null) {
                            aec!!.enabled = true
                            Log.d(TAG, "AEC is ${if (aec!!.enabled) "enabled" else "not enabled"}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception creating AEC: $e")
                    }
                }
                if (agcAvailable) {
                    try {
                        agc = AutomaticGainControl.create(sessionId)
                        if (agc != null) {
                            agc!!.enabled = true
                            Log.d(TAG, "AGC is ${if (agc!!.enabled) "enabled" else "not enabled"}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception creating AGC: $e")
                    }
                }
                if (nsAvailable) {
                    try {
                        ns = NoiseSuppressor.create(sessionId)
                        if (ns != null) {
                            ns!!.enabled = true
                            Log.d(TAG, "NS is ${if (ns!!.enabled) "enabled" else "failed to enable"}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception creating NS: $e")
                    }
                }
            }
            return
        }

        val ua = UserAgent.ofUap(uap)
        if (ua == null) {
            Log.w(TAG, "uaEvent $event did not find ua $uap")
            return
        }

        val aor = ua.account.aor

        Log.d(TAG, "got uaEvent $event/$aor/$callp")

        val call = Call.ofCallp(callp)
        if (call == null && callp != 0L &&
            !setOf("incoming call", "call incoming", "call closed").contains(ev[0])) {
            Log.w(TAG, "uaEvent $event did not find call $callp")
            return
        }

        for (accountIndex in uas.value.indices) {
            if (uas.value[accountIndex].account.aor == aor) {
                when (ev[0]) {
                    "registering", "unregistering" -> {
                        ua.updateStatus(circleYellow.getValue(colorblind))
                        updateStatusNotification()
                        if (isMainVisible)
                            registrationUpdate.postValue(System.currentTimeMillis())
                        return
                    }
                    "registered" -> {
                        ua.updateStatus(
                            if (Api.account_regint(ua.account.accp) == 0)
                                R.drawable.circle_white
                            else
                                circleGreen.getValue(colorblind)
                        )
                        updateStatusNotification()
                        if (isMainVisible)
                            registrationUpdate.postValue(System.currentTimeMillis())
                        return
                    }
                    "registering failed" -> {
                        ua.updateStatus(if (Api.account_regint(ua.account.accp) == 0)
                            R.drawable.circle_white
                        else
                            circleRed.getValue(colorblind)
                        )
                        updateStatusNotification()
                        if (isMainVisible)
                            registrationUpdate.postValue(System.currentTimeMillis())
                        if (Utils.isVisible()) {
                            val reason = if (ev.size > 1) {
                                if (ev[1] == "Invalid argument") // Likely due to DNS lookup failure
                                    ": DNS lookup failed"
                                else
                                    ": ${ev[1]}"
                            } else
                                ""
                            toast(String.format(getString(R.string.registering_failed), aor) + reason)
                        }
                        return
                    }
                    "call outgoing" -> {
                        if (call!!.status.value == "transferring")
                            break
                        speakerPhone = speakerPhoneAuto
                        stopMediaPlayer()
                        setCallVolume()
                        ensureCommunicationMode()
                        proximitySensing(proximitySensing)
                    }
                    "call ringing" -> {
                        playRingBack()
                        return
                    }
                    "call progress" -> {
                        if ((ev[1].toInt() and Api.SDP_RECVONLY) != 0)
                            stopMediaPlayer()
                        else {
                            playRingBack()
                        }
                        return
                    }
                    "incoming call" -> {
                        speakerPhone = speakerPhoneAuto
                        val peerUri = ev[1]
                    val toastMsg = if (Call.isAnyCallActive(this))
                            String.format(
                                getString(R.string.call_auto_rejected),
                                Utils.friendlyUri(this, peerUri, ua.account)
                            )
                        else if (ua.account.blockUnknown && Contact.contactName(peerUri) == peerUri)
                            String.format(
			                    getString(R.string.call_blocked),
                                Utils.friendlyUri(this, peerUri, ua.account)
                            )
                        else if (ua.account.blockHidden && peerUri.contains("anonymous"))
                            getString(R.string.hidden_call_blocked)
                        else if (!Utils.checkPermissions(this, arrayOf(RECORD_AUDIO)))
                            getString(R.string.no_calls)
                        else
                            ""
                        if (toastMsg != "") {
                            Log.d(TAG, "Auto-rejecting incoming call to $uap from $peerUri")
                            Api.sip_treply(callp, 486, "Busy Here")
                            Api.bevent_stop(ev[2].toLong())
                            toast(toastMsg)
                            if (toastMsg.contains(getString(R.string.call_blocked)) ||
                                toastMsg.contains(getString(R.string.hidden_call_blocked))) {
                                if (ua.account.callHistory)
                                    Blocked(
                                        ua.account.aor,
                                        peerUri,
                                        "invite",
                                        GregorianCalendar().timeInMillis
                                    ).add()
                            }
                            else {
                                val name = "callwaiting_$toneCountry"
                                val resourceId = resources.getIdentifier(
                                    name,
                                    "raw",
                                    packageName
                                )
                                if (resourceId != 0) {
                                    ensureCommunicationMode()
                                } else {
                                    Log.e(TAG, "Callwaiting tone $name.wav not found")
                                }
                                if (ua.account.callHistory) {
                                    CallHistoryNew(aor, peerUri, "in").add()
                                    ua.account.missedCalls = true
                                }
                            }
                            return
                        }
                        // callp holds SIP message pointer
                        Api.ua_accept(uap, callp)
                        return
                    }
                    "call incoming" -> {
                        val peerUri = ev[1]
                        Log.d(TAG, "Incoming call $uap/$callp/$peerUri")
                        if (Call.ofCallp(callp) == null)
                            Call(callp, ua, peerUri, "in", "incoming").add()
                        val extras = android.os.Bundle()
                        extras.putLong("uap", uap)
                        extras.putLong("callp", callp)
                        extras.putString("peerUri", peerUri)
                        try {
                            tm.addNewIncomingCall(getPhoneAccountHandle(this), extras)
                        } catch (e: Exception) {
                            Log.e(TAG, "Telecom addNewIncomingCall failed: ${e.message}")
                        }
                        return
                    }
                    "call answered" -> {
                        stopMediaPlayer()
                        ConnectionService.connections[callp]?.setActive()
                        ensureCommunicationMode()
                        Handler(Looper.getMainLooper()).post {
                            if (call != null) {
                                if (call.status.value == "incoming")
                                    call.status.value = "answered"
                                else
                                    return@post
                            }
                        }
                    }
                    "call redirect" -> {
                        stopMediaPlayer()
                    }
                    "call established" -> {
                        ensureCommunicationMode()
                        stopMediaPlayer()
                        nm.cancel(CALL_NOTIFICATION_ID)
                        Log.d(TAG, "AoR $aor call $callp established")
                        Handler(Looper.getMainLooper()).post {
                            if (call != null) {
                                call.status.value = "connected"
                                call.onhold = false
                                call.startTime = GregorianCalendar()
                                if (call.conferenceCall)
                                    Api.cmd_exec("conference")
                            }
                            updateStatusNotification()
                            proximitySensing(proximitySensing)
                            if (isMicMuted)
                                setMicMute(true)
                        }
                        if (!isMainVisible)
                            return
                    }
                    "call update" -> {
                        Handler(Looper.getMainLooper()).post {
                            if (call != null) {
                                val newHeldState = when (ev[1].toInt()) {
                                    Api.SDP_INACTIVE, Api.SDP_RECVONLY -> true
                                    else -> false
                                }
                                val connection = ConnectionService.connections[callp]
                                if (call.held && !newHeldState) {
                                    Log.d(TAG, "Call ${call.callp} un-held by peer.")
                                    call.onhold = false
                                    // Use a Coroutine with a small delay to let the SIP
                                    // transaction (the re-INVITE from the peer) finish
                                    // before trying to hold the other call and resume this one.
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(100.milliseconds)
                                        call.resume()
                                    }
                                }
                                call.held = newHeldState
                                if (newHeldState) {
                                    // Peer put us on hold
                                    call.showOnHoldNotice.value = true
                                    call.callOnHold.value = true
                                    if (connection?.state != Connection.STATE_HOLDING)
                                        connection?.setOnHold()
                                } else {
                                    // Peer un-held us
                                    call.showOnHoldNotice.value = false
                                    if (!call.onhold) {
                                        call.callOnHold.value = false
                                        if (connection?.state != Connection.STATE_ACTIVE)
                                            connection?.setActive()
                                    }
                                }
                                if (call.state() == Api.CALL_STATE_EARLY) {
                                    if ((ev[1].toInt() and Api.SDP_RECVONLY) != 0)
                                        stopMediaPlayer()
                                }
                                if (call.status.value == "connected" && !call.held && !call.onhold) {
                                    if (call.callOnHold.value || call.showOnHoldNotice.value) {
                                        Log.d(
                                            TAG,
                                            "Safety guard: Clearing stuck hold flags for ${call.callp}"
                                        )
                                        call.callOnHold.value = false
                                        call.showOnHoldNotice.value = false
                                        connection?.setActive()
                                    }
                                }
                            }
                        }
                        if (!isMainVisible || call?.status?.value != "connected")
                            return
                    }
                    "call verified", "call secure" -> {
                        Handler(Looper.getMainLooper()).post {
                            if (call != null) {
                                if (ev[0] == "call secure") {
                                    call.security = R.color.colorTrafficYellow
                                } else {
                                    call.security = R.color.colorTrafficGreen
                                    call.zid = ev[1]
                                }
                            }
                        }
                        if (!isMainVisible)
                            return
                    }
                    "call transfer" -> {
                        if (!Utils.isVisible()) {
                            val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            intent.putExtra("action", "transfer show")
                                .putExtra("callp", callp).putExtra("uri", ev[1])
                            val pi = PendingIntent.getActivity(
                                this,
                                TRANSFER_REQ_CODE,
                                intent,
                                piFlags
                            )
                            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                            val target = Utils.friendlyUri(this, ev[1], ua.account)
                            nb.setSmallIcon(R.drawable.ic_notification_call)
                                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                                .setContentIntent(pi)
                                .setDefaults(Notification.DEFAULT_SOUND)
                                .setAutoCancel(true)
                                .setContentTitle(getString(R.string.transfer_request_to))
                                .setContentText(target)
                            val acceptIntent = Intent(this, MainActivity::class.java)
                            acceptIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            acceptIntent.putExtra("action", "transfer accept")
                                .putExtra("callp", callp).putExtra("uri", ev[1])
                            val acceptPendingIntent = PendingIntent.getActivity(
                                this,
                                ACCEPT_REQ_CODE,
                                acceptIntent,
                                piFlags
                            )
                            val denyIntent = Intent(this, BaresipService::class.java)
                            denyIntent.action = "Transfer Deny"
                            denyIntent.putExtra("callp", callp)
                            val denyPendingIntent = PendingIntent.getService(this,
                                    DENY_REQ_CODE, denyIntent, piFlags)
                            nb.addAction(R.drawable.ic_notification_call,
                                getString(R.string.accept), acceptPendingIntent)
                            nb.addAction(R.drawable.ic_notification_call_end,
                                getString(R.string.deny), denyPendingIntent)
                            nm.notify(TRANSFER_NOTIFICATION_ID, nb.build())
                            return
                        }
                    }
                    "transfer failed" -> {
                        Log.d(TAG, "AoR $aor call $callp transfer failed: ${ev[1]}")
                        stopMediaPlayer()
                        call!!.referTo = ""
                        if (Utils.isVisible())
                            toast("${getString(R.string.transfer_failed)}: ${ev[1].trim()}")
                        if (!isMainVisible)
                            return
                    }
                    "call closed" -> {
                        Log.d(TAG, "AoR $aor call $callp is closed prm: ${ev[1]}")
                        val connection = ConnectionService.connections[callp]
                        if (connection != null) {
                            val cause = when {
                                ev[1].contains("200") -> DisconnectCause.LOCAL
                                ev[1].contains("486") -> DisconnectCause.BUSY
                                ev[1].contains("404") -> DisconnectCause.ERROR
                                ev[1].contains("403") || ev[1].contains("401") ->
                                    DisconnectCause.RESTRICTED
                                else -> DisconnectCause.REMOTE
                            }
                            connection.setDisconnected(DisconnectCause(cause))
                            connection.safeDestroy()
                            ConnectionService.connections.remove(callp)
                        }
                        if (call != null) {
                            call.terminated.value = true
                            call.remove()
                            val noMoreCalls = synchronized(calls) { !Call.inCall() }

                            stopRinging()
                            stopMediaPlayer()

                            if (noMoreCalls) {
                                proximitySensing(false)
                                abandonAudioFocus(this)
                            }

                            val newCall = call.newCall
                            if (newCall != null) {
                                newCall.onHoldCall = null
                                call.newCall = null
                            }
                            val onHoldCall = call.onHoldCall
                            if (onHoldCall != null) {
                                onHoldCall.newCall = null
                                onHoldCall.referTo = ""
                                call.onHoldCall = null
                            }
                            val isConference = call.conferenceCall
                            val hasOtherCalls = synchronized(calls) { calls.any { it.ua == ua } }
                            if (noMoreCalls)
                                releaseAudioEffects()
                            updateStatusNotification()
                            if (isConference && !hasOtherCalls) {
                                Log.d(TAG, "Last conference call closed, scheduling mixminus unload")
                                Handler(Looper.getMainLooper()).postDelayed({
                                    val conferenceActive = synchronized(calls) {
                                        calls.any { it.conferenceCall }
                                    }
                                    if (!conferenceActive) {
                                        Log.d(TAG, "Unloading mixminus module")
                                        Api.module_unload("mixminus")
                                    }
                                }, 1000)
                            }
                            val reason = ev[1]
                            val tone = ev[2]
                            if (tone == "busy")
                                playBusy()
                            else
                                ensureCommunicationMode()
                            if (call.dir == "out")
                                call.rejected = call.startTime == null &&
                                        !reason.startsWith("408") &&
                                        !reason.startsWith("480") &&
                                        !reason.startsWith("Connection reset by")

                            val missed = call.dir == "in" && call.startTime == null && !call.rejected
                            if (ua.account.callHistory) {
                                val completedElsewhere = missed && ev[2].startsWith("SIP") &&
                                        ev[2].contains(";cause=200")
                                CoroutineScope(Dispatchers.IO).launch {
                                    val history = CallHistoryNew(aor, call.peerUri, call.dir)
                                    history.stopTime = GregorianCalendar()
                                    history.startTime = if (completedElsewhere)
                                        history.stopTime
                                    else
                                        call.startTime
                                    history.rejected = call.rejected
                                    if (call.dumpfiles[0] != "") {
                                        history.recording = call.dumpfiles
                                    }
                                    history.add()
                                    if (call.startTime != null && call.dumpfiles[0] != "") {
                                        delay(500.milliseconds)
                                        val rxFile = File(call.dumpfiles[0])
                                        val txFile = File(call.dumpfiles[1])
                                        val mergedFileName = rxFile.name
                                            .replace("dump", "rec")
                                            .replace("=>", "-")
                                            .replace("sip:", "")
                                            .replace("-enc", "")
                                            .replace("*", "#")
                                            .replace(";user=phone", "")
                                        val mergedFile = File(filesPath, mergedFileName)
                                        if (Utils.mergeWavFiles(rxFile, txFile, mergedFile)) {
                                            Log.d(TAG, "Automatic merge succeeded.")
                                            history.recording = arrayOf(mergedFile.absolutePath, "")
                                            CallHistoryNew.save()
                                            try {
                                                rxFile.delete()
                                                txFile.delete()
                                            } catch (e: Exception) {
                                                Log.w(
                                                    TAG,
                                                    "Could not delete temporary raw files after merge: ${e.message}"
                                                )
                                            }
                                        }
                                        else {
                                            Log.e(
                                                TAG,
                                                "Automatic merge failed. Storing raw file paths as fallback."
                                            )
                                            history.recording = call.dumpfiles
                                        }
                                    }
                                }
                                ua.account.missedCalls = ua.account.missedCalls || missed
                            }
                            if (missed) {
                                val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                val caller = Utils.friendlyUri(this, call.peerUri, ua.account)
                                val intent = Intent(this, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                intent.putExtra("action", "call missed")
                                        .putExtra("uap", uap)
                                val pi = PendingIntent.getActivity(
                                    this,
                                    CALL_REQ_CODE,
                                    intent,
                                    piFlags
                                )
                                val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                                nb.setSmallIcon(R.drawable.ic_notification_call_missed)
                                        .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                                        .setContentIntent(pi)
                                        .setCategory(Notification.CATEGORY_CALL)
                                        .setAutoCancel(true)
                                var missedCalls = 0
                                for (notification in nm.activeNotifications)
                                    if (notification.id == CALL_MISSED_NOTIFICATION_ID)
                                        missedCalls++
                                if (missedCalls == 0) {
                                    nb.setContentTitle(getString(R.string.missed_call_from))
                                    nb.setContentText(caller)
                                }
                                else {
                                    nb.setContentTitle(getString(R.string.missed_calls))
                                    nb.setContentText(
                                            String.format(getString(R.string.missed_calls_count),
                                                    missedCalls + 1))
                                }
                                nm.notify(CALL_MISSED_NOTIFICATION_ID, nb.build())
                            }
                            if (!Utils.isVisible())
                                return
                        }
                        val reason = ev[1].trim()
                        if ((reason != "") && (ua.calls().isEmpty())) {
                            if (reason[0].isDigit()) {
                                if (reason[0] != '3')
                                    toast("${getString(R.string.call_failed)}: $reason")
                            }
                            else
                                toast("${getString(R.string.call_closed)}: ${Api.call_peer_uri(callp)}: $reason")
                        }
                    }
                }
                break
            }
        }

        postServiceEvent(
            ServiceEvent(event, arrayListOf(uap, callp), System.nanoTime())
        )

    }

    @Suppress("unused")
    @SuppressLint("UnspecifiedImmutableFlag")
    @Keep
    fun messageEvent(uap: Long, peerUri: String, cType: String, msg: ByteArray) {

        val ua = UserAgent.ofUap(uap)
        if (ua == null) {
            Log.w(TAG, "messageEvent did not find ua $uap")
            return
        }

        val blockedHidden = ua.account.blockHidden && peerUri.contains("anonymous")
        val blockedUnknown = ua.account.blockUnknown && Contact.contactName(peerUri) == peerUri

        if (blockedHidden || blockedUnknown) {
            Log.d(TAG, "Auto-rejecting incoming message by $uap from $peerUri")
            Blocked(
                ua.account.aor,
                peerUri,
                "message",
                GregorianCalendar().timeInMillis
            ).add()
            toast(if (blockedHidden)
                getString(R.string.hidden_message_blocked)
            else
                String.format(
                    getString(R.string.message_blocked),
                    Utils.friendlyUri(this, peerUri, ua.account)
                )
            )
            return
        }

        val charsetString = Utils.paramValue(cType.replace(" ", ""),
                "charset")
        val charset = try {
            Charset.forName(charsetString)
        } catch (_: Exception) {
            StandardCharsets.UTF_8
        }
        val text = try {
            String(msg, charset)
        } catch (e: Exception) {
            val error = "Decoding of message failed using charset $charset from $cType: ${e.message}!"
            Log.w(TAG, error)
            error
        }

        handleIncomingMessage(uap, peerUri, text, System.currentTimeMillis())
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    fun handleIncomingMessage(uap: Long, peerUri: String, text: String, timeStamp: Long) {

        val ua = UserAgent.ofUap(uap)
        if (ua == null) {
            Log.w(TAG, "handleIncomingMessage did not find ua $uap")
            return
        }

        val aor = ua.account.aor

        // Check for duplicates
        val lastMsg = messages.lastOrNull { m -> m.aor == aor }
        if (lastMsg != null && lastMsg.timeStamp == timeStamp && lastMsg.peerUri == peerUri &&
                lastMsg.message == text) {
            Log.d(TAG, "Omit duplicate message from $peerUri")
            return
        }

        Log.d(TAG, "Message event for $uap from $peerUri at $timeStamp")
        Message(
            aor,
            peerUri,
            text,
            timeStamp,
            MESSAGE_DOWN,
            0,
            "",
            true
        ).add()
        ua.account.unreadMessages = true

        if (!Utils.isVisible()) {
            val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("action", "message show")
                .putExtra("uap", uap)
                .putExtra("peer", peerUri)
            val pi = PendingIntent.getActivity(
                this,
                MESSAGE_REQ_CODE,
                intent,
                piFlags
            )

            val sender = createPerson(this, peerUri, ua.account)
            val localUserPerson = Person.Builder()
                .setName(getString(R.string.you))
                .setKey(ua.account.aor)
                .build()
            val messagingStyle = MessagingStyle(localUserPerson)
                .setConversationTitle(null)
                .setGroupConversation(false)
                .addMessage(text, timeStamp, sender)

            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_message)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setContentIntent(pi)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setAutoCancel(true)
                .setStyle(messagingStyle)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addPerson(sender)

            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(getString(R.string.reply))
                .build()
            val directReplyIntent = Intent(this, BaresipService::class.java)
            directReplyIntent.action = "Message Inline Reply"
            directReplyIntent.putExtra("uap", uap)
                .putExtra("peer", peerUri).putExtra("time", timeStamp)
            val directReplyPendingIntent = PendingIntent.getService(
                this,
                DIRECT_REPLY_REQ_CODE,
                directReplyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val inlineReplyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_notification_reply,
                getString(R.string.reply),
                directReplyPendingIntent
            ).addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).
                build()

            val saveIntent = Intent(this, BaresipService::class.java)
            saveIntent.action = "Message Save"
            saveIntent.putExtra("uap", uap)
                .putExtra("time", timeStamp.toString())
            val savePendingIntent = PendingIntent
                .getService(this, SAVE_REQ_CODE, saveIntent, piFlags)
            val saveAction = NotificationCompat.Action.Builder(
                R.drawable.ic_notification_save,
                getString(R.string.save),
                savePendingIntent
            ).build()

            val deleteIntent = Intent(this, BaresipService::class.java)
            deleteIntent.action = "Message Delete"
            deleteIntent.putExtra("uap", uap)
                .putExtra("time", timeStamp.toString())
            val deletePendingIntent = PendingIntent
                .getService(this, DELETE_REQ_CODE, deleteIntent, piFlags)
            val deleteAction = NotificationCompat.Action.Builder(
                R.drawable.ic_notification_delete,
                getString(R.string.delete),
                deletePendingIntent
            ).build()

            nb.addAction(inlineReplyAction).addAction(saveAction).addAction(deleteAction)
            nm.notify(MESSAGE_NOTIFICATION_ID, nb.build())

            return
        }

        if (nm.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL)
            nt.play()

        postServiceEvent(
            ServiceEvent(
                "message show",
                arrayListOf(uap, peerUri),
                System.nanoTime()
            )
        )
    }

    @Keep
    @Suppress("UNUSED")
    fun messageResponse(responseCode: Int, responseReason: String, time: String) {
        Log.d(TAG, "Message response '$responseCode $responseReason' at $time")
        val timeStamp = time.toLong()
        for (m in messages.reversed())
            if (m.timeStamp == timeStamp) {
                if (responseCode < 300) {
                    m.direction = MESSAGE_UP
                } else {
                    m.direction = MESSAGE_UP_FAIL
                    m.responseCode = responseCode
                    m.responseReason = responseReason
                }
                messageUpdate.postValue(System.currentTimeMillis())
                break
            } else {
                if (m.timeStamp < timeStamp - 60000)
                    break
            }
    }

    private var audioModeChangedListener: AudioManager.OnModeChangedListener? = null

    fun runCall(uap: Long, uri: String, conferenceCall: Boolean, onHoldCallp: Long) {

        val executeCall = {
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                val ua = UserAgent.ofUap(uap)
                if (ua != null) {
                    if (conferenceCall && ua.calls().isEmpty())
                        Api.module_load("mixminus")
                    val callp = ua.callAlloc(0L, Api.VIDMODE_OFF)
                    if (callp != 0L) {
                        ConnectionService.promoteOutgoingConnection(callp)
                        val onHoldCall = Call.ofCallp(onHoldCallp)
                        val call = Call(callp, ua, uri, "out", "outgoing")
                        call.onHoldCall = onHoldCall
                        call.conferenceCall = conferenceCall
                        call.add()
                        updateStatusNotification()
                        if (onHoldCall != null)
                            onHoldCall.newCall = call
                        if (!call.connect(uri)) {
                            Log.w(TAG, "call_connect $callp failed")
                            ConnectionService.onCallClosed(callp)
                            call.remove()
                            call.destroy()
                            updateStatusNotification()
                        }
                    } else {
                        ConnectionService.pendingOutgoingConnection?.let {
                            it.setDisconnected(DisconnectCause(DisconnectCause.ERROR))
                            it.destroy()
                            ConnectionService.pendingOutgoingConnection = null
                        }
                    }
                }
            }, audioDelay)
        }

        if (Call.hasTelecomCall())
            executeCall()
        else if (VERSION.SDK_INT < 31) {
            Log.d(TAG, "Setting audio mode to MODE_IN_COMMUNICATION")
            am.mode = MODE_IN_COMMUNICATION
            executeCall()
        }
        else {
            val isAnyCallMode = am.mode == MODE_IN_COMMUNICATION || am.mode == MODE_IN_CALL
            if (isAnyCallMode) {
                Log.d(TAG, "Audio mode already in a call mode (${am.mode})")
                executeCall()
            }
            else {
                audioModeChangedListener = AudioManager.OnModeChangedListener { mode ->
                    if (mode == MODE_IN_COMMUNICATION || mode == MODE_IN_CALL) {
                        Log.d(TAG, "Audio mode changed to $mode")
                        audioModeChangedListener?.let {
                            am.removeOnModeChangedListener(it)
                            audioModeChangedListener = null
                        }
                        executeCall()
                    }
                }
                am.addOnModeChangedListener(mainExecutor, audioModeChangedListener!!)
                Log.d(TAG, "Setting audio mode to MODE_IN_COMMUNICATION (waiting for callback)")
                am.mode = MODE_IN_COMMUNICATION
            }
        }
    }

    @Suppress("unused")
    @Keep
    fun started() {
        Log.d(TAG, "Received 'started' from baresip")
        isNativeReady = true
        addMobileUserAgent()
        if (VERSION.SDK_INT >= 31 && !telephonyCallbackRegistered) {
            try {
                telephonyManager.registerTelephonyCallback(mainExecutor, telephonyCallback)
                telephonyCallbackRegistered = true
                Log.d(TAG, "Registered TelephonyCallback")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register TelephonyCallback: ${e.message}")
            }
        } else if (!telephonyCallbackRegistered) {
            @Suppress("DEPRECATION")
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE)
            telephonyCallbackRegistered = true
            Log.d(TAG, "Registered PhoneStateListener")
        }
        Api.net_debug()
        postServiceEvent(
            ServiceEvent("started", arrayListOf(callActionUri), System.nanoTime())
        )
        callActionUri = ""
        Log.d(TAG, "Battery optimizations are ignored: " +
                "${pm.isIgnoringBatteryOptimizations(packageName)}")
        Log.d(TAG, "Partial wake lock/wifi lock is held: " +
                "${partialWakeLock.isHeld}/${wifiLock.isHeld}")
        updateStatusNotification()
    }

    @Suppress("unused")
    @Keep
    fun stopped(error: String) {
        Log.d(TAG, "Received 'stopped' from baresip with start error '$error'")
        isNativeReady = false
        quitTimer.cancel()
        cleanService()
        isServiceRunning = false
        postServiceEvent(
            ServiceEvent("stopped", arrayListOf(error), System.nanoTime())
        )
        stopSelf()
    }

    private fun createNotificationChannels() {
        val lowChannel = NotificationChannel(LOW_CHANNEL_ID, "No sound, no vibrate",
            NotificationManager.IMPORTANCE_LOW)
        lowChannel.description = "Background status notifications"
        lowChannel.enableVibration(false)
        lowChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(lowChannel)
        val ringAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build()
        val highChannel = NotificationChannel(HIGH_CHANNEL_ID, "Sound, vibrate, and peek",
            NotificationManager.IMPORTANCE_HIGH)
        highChannel.description = "Incoming calls and important alerts"
        highChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        highChannel.enableVibration(true)
        highChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, ringAttributes)
        nm.createNotificationChannel(highChannel)
        val mediumChannel = NotificationChannel(MEDIUM_CHANNEL_ID, "Sound only",
            NotificationManager.IMPORTANCE_DEFAULT)
        mediumChannel.description = "Incoming messages"
        mediumChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        mediumChannel.enableVibration(false)
        mediumChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, ringAttributes)
        nm.createNotificationChannel(mediumChannel)
    }

    private fun buildStatusNotification(): Notification {
        if (VERSION.SDK_INT >= 31)
            snb.foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE
        snb.setOngoing(true)
        val notification = snb.build()
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or
                Notification.FLAG_ONGOING_EVENT
        return notification
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun showStatusNotification() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val pi = PendingIntent.getActivity(
            this,
            STATUS_REQ_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val deleteIntent = Intent(this, BaresipService::class.java)
            .setAction("Notification Dismissed")
        val dpi = PendingIntent.getService(
            this,
            STATUS_REQ_CODE,
            deleteIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notificationLayout = RemoteViews(packageName, R.layout.status_notification)
        snb.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_notification_b)
            .setContentIntent(pi)
            .setDeleteIntent(dpi)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(notificationLayout)
        val notification = buildStatusNotification()
        try {
            if (VERSION.SDK_INT >= 29)
                startForeground(STATUS_NOTIFICATION_ID, notification, foregroundServiceType())
            else
                startForeground(STATUS_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
            // Fallback to standard notification if background start is denied
            nm.notify(STATUS_NOTIFICATION_ID, notification)
        }
    }

    fun updateStatusNotification() {

        Handler(Looper.getMainLooper()).post {
            val activeCall = synchronized(calls) {
                calls.find {
                    it.status.value == "connected" || it.status.value == "outgoing"
                            || it.status.value == "answered"
                }
            }

            val builder = NotificationCompat.Builder(this, LOW_CHANNEL_ID)
            val intent = Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)

            val pi = PendingIntent.getActivity(
                this,
                STATUS_REQ_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            val deleteIntent =
                Intent(this, BaresipService::class.java)
                    .setAction("Notification Dismissed")
            val dpi = PendingIntent.getService(
                this,
                STATUS_REQ_CODE,
                deleteIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_notification_b)
                .setContentIntent(pi)
                .setDeleteIntent(dpi)
                .setOngoing(true)

            if (activeCall != null) {
                val peerUri = activeCall.peerUri
                val caller = Utils.friendlyUri(this, peerUri, activeCall.ua.account)
                val person = Person.Builder().setName(caller).build()

                val hangupIntent = Intent(this, BaresipService::class.java)
                hangupIntent.action = "Call Hangup"
                hangupIntent.putExtra("callp", activeCall.callp)
                val hpi = PendingIntent.getService(
                    this,
                    REJECT_REQ_CODE,
                    hangupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hpi))
                    .setCategory(Notification.CATEGORY_CALL)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setWhen(activeCall.startTime?.timeInMillis ?: System.currentTimeMillis())
                    .setUsesChronometer(true)
                    .setContentText(
                        if (activeCall.onhold)
                            getString(R.string.call_is_on_hold)
                        else
                            when (activeCall.status.value) {
                                "outgoing", "incoming" -> getString(R.string.call_is_ringing)
                                "connected", "answered" -> getString(R.string.call_is_connected)
                                else -> getString(R.string.call)
                            }
                    )
            } else {
                builder.setStyle(null)
                builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setWhen(0)
                    .setShowWhen(false)
                    .setUsesChronometer(false)
                    .setContentTitle("")
                    .setContentText("")

                val notificationLayout = RemoteViews(packageName, R.layout.status_notification)
                for (i in 0..3) {
                    val resId = when (i) {
                        0 -> R.id.status0
                        1 -> R.id.status1
                        2 -> R.id.status2
                        else -> R.id.status3
                    }
                    if (i < uas.value.size) {
                        notificationLayout.setImageViewResource(resId, uas.value[i].status)
                        notificationLayout.setViewVisibility(resId, View.VISIBLE)
                    } else {
                        notificationLayout.setViewVisibility(resId, View.INVISIBLE)
                    }
                }
                if (uas.value.size > 4)
                    notificationLayout.setViewVisibility(R.id.etc, View.VISIBLE)
                else
                    notificationLayout.setViewVisibility(R.id.etc, View.INVISIBLE)

                builder.setCustomContentView(notificationLayout)
            }

            if (VERSION.SDK_INT >= 31)
                builder.foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE

            builder.setOngoing(true)
            val notification = builder.build()
            notification.flags =
                notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

            try {
                if (activeCall != null) {
                    if (!isNotificationInCall) {
                        // Only call startForeground when the FIRST call starts.
                        if (VERSION.SDK_INT >= 29)
                            startForeground(
                                STATUS_NOTIFICATION_ID,
                                notification,
                                foregroundServiceType(activeCall)
                            )
                        else
                            startForeground(STATUS_NOTIFICATION_ID, notification)
                        isNotificationInCall = true
                    }
                    else
                        nm.notify(STATUS_NOTIFICATION_ID, notification)
                } else {
                    if (isNotificationInCall) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        // Only call startForeground to drop the "Call" type when the LAST call ends.
                        if (VERSION.SDK_INT >= 29)
                            startForeground(STATUS_NOTIFICATION_ID, notification, foregroundServiceType())
                        else
                            startForeground(STATUS_NOTIFICATION_ID, notification)
                        isNotificationInCall = false
                    }
                    else
                        // Already in standby, just keep the notification current.
                        nm.notify(STATUS_NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update notification: ${e.message}")
                nm.notify(STATUS_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun foregroundServiceType(activeCall: Call? = null): Int {
        var type = 0
        if (VERSION.SDK_INT >= 30) {
            if (activeCall != null) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                if (ContextCompat.checkSelfPermission(this, RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED)
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        }
        if (VERSION.SDK_INT >= 34) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        // Fallback for API 30-33 when no active call (specialUse not available)
        if (type == 0 && VERSION.SDK_INT >= 30)
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL

        return type
    }

    @SuppressLint("WakelockTimeout")
    private fun updatePartialWakeLock() {
        // Hold the wake lock as long as the service is active to ensure
        // native SIP timers (registration, keep-alives) continue to run.
        try {
            if (!partialWakeLock.isHeld) {
                Log.i(TAG, "Acquiring Partial Wake Lock")
                partialWakeLock.acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error managing partialWakeLock: ${e.message}")
        }
    }

    fun handleExternalCall(telecomCall: android.telecom.Call, preferredAor: String? = null) {
        val rawUri = telecomCall.details.handle?.toString() ?: getString(R.string.unknown)
        val uri = Utils.uriUnescape(rawUri)

        if (uas.value.isEmpty()) {
            Log.e(TAG, "No User Agents available to handle external call")
            return
        }

        val ua = preferredAor?.let { UserAgent.ofAor(it) }
            ?: uas.value.find { it.account.isMobile }
            ?: uas.value[0]

        val telecomState = if (VERSION.SDK_INT >= 31)
            telecomCall.details.state
        else
            @Suppress("DEPRECATION") telecomCall.state

        val isIncoming = telecomState == android.telecom.Call.STATE_RINGING
        Log.d(TAG, "Handling external call " +
                "${if (isIncoming) "from" else "to"} $uri (preferredAor=$preferredAor)")

        val initialStatus = when (telecomState) {
            android.telecom.Call.STATE_RINGING -> "incoming"
            android.telecom.Call.STATE_DIALING, android.telecom.Call.STATE_CONNECTING -> "outgoing"
            else -> "connected"
        }

        if (isIncoming) {
            val e164Uri = e164Uri(uri, ua.account.countryCode)
            val presentation = telecomCall.details.handlePresentation
            if (ua.account.blockHidden &&
                (presentation == TelecomManager.PRESENTATION_RESTRICTED ||
                        presentation == TelecomManager.PRESENTATION_UNKNOWN)) {
                Log.d(TAG, "Auto-rejecting incoming PSTN hidden call")
                telecomCall.disconnect()
                toast(getString(R.string.hidden_call_blocked))
                if (ua.account.callHistory) {
                    Blocked(
                        ua.account.aor,
                        uri,
                        "invite",
                        GregorianCalendar().timeInMillis
                    ).add()
                }
                return
            }
            if (ua.account.blockUnknown && Contact.contactName(e164Uri) == e164Uri) {
                Log.d(TAG, "Auto-rejecting incoming PSTN call from $uri")
                telecomCall.disconnect()
                toast(
                    String.format(getString(R.string.call_blocked),
                    Utils.friendlyUri(this, uri, ua.account))
                )
                if (ua.account.callHistory) {
                    Blocked(
                        ua.account.aor,
                        uri,
                        "invite",
                        GregorianCalendar().timeInMillis
                    ).add()
                }
                return
            }
        }

        val call = Call.ExternalCall(
            telecomCall,
            ua,
            uri,
            if (telecomState == android.telecom.Call.STATE_RINGING) "in" else "out",
            initialStatus
        )

        telecomCall.registerCallback(object : android.telecom.Call.Callback() {
            override fun onStateChanged(call: android.telecom.Call, state: Int) {
                super.onStateChanged(call, state)
                val newStatus = when (state) {
                    android.telecom.Call.STATE_RINGING -> "incoming"
                    android.telecom.Call.STATE_DIALING, android.telecom.Call.STATE_CONNECTING ->
                        "outgoing"
                    android.telecom.Call.STATE_ACTIVE -> "connected"
                    android.telecom.Call.STATE_DISCONNECTED, android.telecom.Call.STATE_DISCONNECTING ->
                        "closed"
                    android.telecom.Call.STATE_HOLDING -> {
                        calls.find { it.callp == call.hashCode().toLong() }?.onhold = true
                        "connected"
                    }
                    else -> "connected"
                }
                calls.find { it.callp == call.hashCode().toLong() }?.let {
                    if (it.status.value != newStatus) {
                        it.status.value = newStatus
                        if (newStatus == "connected") {
                            it.startTime = GregorianCalendar()
                            nm.cancel(CALL_NOTIFICATION_ID)
                            updateStatusNotification()
                        }
                        postServiceEvent(ServiceEvent(
                            "call update",
                            arrayListOf(it.ua.uap, it.callp),
                            System.nanoTime())
                        )
                        if (newStatus == "closed")
                            handleExternalCallRemoved(call)
                    }
                }
            }
        })

        synchronized(calls) {
            calls.add(call)
        }
        setCallVolume()
        ensureCommunicationMode()

        if (isIncoming) {
            handleIncomingCall(call)
            if (ua.account.answerMode == Api.ANSWERMODE_AUTO) {
                Log.d(TAG, "Auto-answering external call ${call.callp}")
                Handler(Looper.getMainLooper()).postDelayed({
                    call.answer()
                }, 2000)
            }
        }
        else
            postServiceEvent(ServiceEvent(
                "call outgoing",
                arrayListOf(ua.uap, call.callp),
                System.nanoTime())
            )
    }

    fun handleExternalCallRemoved(telecomCall: android.telecom.Call) {
        val callp = telecomCall.hashCode().toLong()
        val call = calls.find { it.callp == callp }
        if (call != null) {
            val uap = call.ua.uap
            stopRinging()
            stopMediaPlayer()
            if (call.ua.account.callHistory) {
                val historyPeerUri = e164Uri(call.peerUri, call.ua.account.countryCode)
                val history = CallHistoryNew(call.ua.account.aor, historyPeerUri, call.dir)
                history.stopTime = GregorianCalendar()
                history.startTime = call.startTime
                history.rejected = call.rejected
                history.add()
                if (call.dir == "in" && call.startTime == null && !call.rejected)
                    call.ua.account.missedCalls = true
            }
            synchronized(calls) {
                calls.remove(call)
            }
            postServiceEvent(
                ServiceEvent(
                    "call closed", arrayListOf(uap, callp), System.nanoTime()
                )
            )
        }
        if (!Call.inCall()) {
            proximitySensing(false)
            stopMediaPlayer()
        }
        updateStatusNotification()
        messageUpdate.postValue(System.currentTimeMillis())
    }

    fun addMobileUserAgent() {
        if (VERSION.SDK_INT < 29) return

        val mobileAccountHandle = Utils.pstnAccountHandle(this)
        val existingMobileUa = uas.value.find { it.account.isMobile }

        // If mobile account should not exist (role lost or no SIM), remove it if it exists
        if (mobileAccountHandle == null) {
            if (existingMobileUa != null) {
                Log.d(TAG, "Removing Mobile account (role lost or SIM missing)")
                existingMobileUa.remove()
                Account.saveAccounts()
                CallHistoryNew.save()
                Message.save()
                updateStatusNotification()
            }
            return
        }

        val mobileAor = "sip:mobile@pstn"

        if (existingMobileUa != null) {
            return
        }

        Log.d(TAG, "Injecting new virtual Mobile account: $mobileAor")
        val account = Account(0L, mobileAor)
        account.isMobile = true
        account.nickName = "Mobile"
        account.regint = 0
        account.telProvider = ""
        val mobileUa = UserAgent(0L, account)
        val isAirplaneModeOn = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        mobileUa.status = if (isAirplaneModeOn) R.drawable.circle_white else circleGreen.getValue(colorblind)
        updateMobileStatus()

        val updatedUas = uas.value.toMutableList()
        updatedUas.add(mobileUa)
        uas.value = updatedUas.toList()
        uasStatus.value = UserAgent.statusMap()

        Account.saveAccounts()
        CallHistoryNew.save()
        Message.save()
        updateStatusNotification()
    }

    private fun toast(message: String, length: Int = Toast.LENGTH_SHORT) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, length).show()
        }
    }

    private fun updateMobileStatus(newStatus: Int? = null) {
        uas.value.find { it.account.isMobile }?.let { ua ->
            val isAirplaneModeOn = Utils.isAirplaneModeOn(this)
            val status = newStatus ?: if (isAirplaneModeOn) {
                if (ua.status == circleGreen.getValue(colorblind))
                    ua.status
                else
                    R.drawable.circle_white
            } else {
                if (ua.status == R.drawable.circle_white) {
                    // Show "Red" (not yet in service)
                    circleRed.getValue(colorblind)
                } else {
                    ua.status
                }
            }
            if (ua.status != status) {
                Log.d(TAG, "Updating Mobile status to $status")
                ua.updateStatus(status)
                updateStatusNotification()
                if (isMainVisible)
                    registrationUpdate.postValue(System.currentTimeMillis())
            }
        }
    }

    private fun updateMobileStatusFromServiceState(state: Int) {
        val isAirplaneModeOn = Utils.isAirplaneModeOn(this)
        val status = if (state == ServiceState.STATE_IN_SERVICE)
            circleGreen.getValue(colorblind)
        else if (isAirplaneModeOn)
            R.drawable.circle_white
        else
            circleRed.getValue(colorblind)
        Log.d(TAG, "Mobile service state changed: $state, updating status to $status (Airplane=$isAirplaneModeOn)")
        updateMobileStatus(status)
    }

    @SuppressLint("FullScreenIntentPolicy")
    fun handleIncomingCall(call: Call) {
        val ua = call.ua
        val peerUri = call.peerUri
        val callp = call.callp

        val callerNumber = Utils.uriUserPart(peerUri)
        if (call !is Call.ExternalCall && shouldStartRinging(callerNumber))
            startRinging()

        if (!Utils.isVisible()) {
            val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val intent = Intent(this, MainActivity::class.java)
                .putExtra("action", "call show")
                .putExtra("callp", callp)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            val pi = PendingIntent.getActivity(
                this,
                CALL_REQ_CODE,
                intent,
                piFlags
            )

            val diverterUri = call.diverterUri()
            val contentText = if (diverterUri != "")
                "${getString(R.string.is_calling)} " +
                        "(${getString(R.string.diverted_by)} " +
                        "${Utils.friendlyUri(this, diverterUri, ua.account)})"
            else
                getString(R.string.is_calling)
            val caller = createPerson(this, peerUri, ua.account)
            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
            nb.setSmallIcon(R.drawable.ic_notification_call)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setContentIntent(pi)
                .setCategory(Notification.CATEGORY_CALL)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentText(contentText)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .addPerson(caller)
                .setFullScreenIntent(pi, true)

            val answerIntent = Intent(this, MainActivity::class.java)
                .putExtra("action", "call answer")
                .putExtra("callp", callp)
            val api = PendingIntent.getActivity(
                this,
                ANSWER_REQ_CODE,
                answerIntent,
                piFlags
            )

            val rejectIntent = Intent(this, BaresipService::class.java)
            rejectIntent.action = "Call Reject"
            rejectIntent.putExtra("callp", callp)
            val rpi = PendingIntent.getService(
                this,
                REJECT_REQ_CODE,
                rejectIntent,
                piFlags
            )

            nb.setStyle(NotificationCompat.CallStyle.forIncomingCall(
                caller,
                rpi,
                api
            ))
            nm.notify(CALL_NOTIFICATION_ID, nb.build())
        }

        postServiceEvent(ServiceEvent(
            "call incoming",
            arrayListOf(ua.uap, callp),
            System.nanoTime()
        ))
    }

    private fun createPerson(ctx: Context, peerUri: String, account: Account): Person {
        val peer = Utils.friendlyUri(ctx, peerUri, account)
        val contact = Contact.findContact(peerUri)
        val contactColor = contact?.color() ?: "#B0B0B0"
        val initial = if (peer.isNotEmpty()) peer.take(1) else "?"
        val textAvatarBitmap = Utils.createTextAvatar(initial, contactColor)
        var icon = IconCompat.createWithBitmap(textAvatarBitmap)
        if (contact is Contact.BaresipContact) {
            if (contact.avatarImage != null)
                icon = IconCompat.createWithBitmap(contact.avatarImage!!.toCircle())
        }
        else if (contact is Contact.AndroidContact)
            if (contact.thumbnailUri != null)
                try {
                    val source = ImageDecoder.createSource(contentResolver, contact.thumbnailUri!!)
                    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    icon = IconCompat.createWithBitmap(bitmap.toCircle())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load Android contact avatar: $e")
                }
        return Person.Builder().setName(peer).setIcon(icon).build()
    }

    private fun startRinging() {
        am.mode = AudioManager.MODE_RINGTONE
        rt!!.isLooping = true
        rt!!.play()
        if (shouldVibrate()) {
            val effect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
            vbTimer = Timer()
            vbTimer!!.schedule(object : TimerTask() {
                override fun run() {
                    if (VERSION.SDK_INT >= 33) {
                        vibrator.vibrate(
                            effect,
                            android.os.VibrationAttributes.Builder()
                                .setUsage(android.os.VibrationAttributes.USAGE_RINGTONE)
                                .build()
                        )
                    } else {
                        val attributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(effect, attributes)
                    }
                }
            }, 500L, 2000L)
        }
    }

    private fun shouldStartRinging(callerNumber: String): Boolean {
        val currentFilter = nm.currentInterruptionFilter
        if (currentFilter <= NotificationManager.INTERRUPTION_FILTER_ALL)
            return true
        val channel = nm.getNotificationChannel(HIGH_CHANNEL_ID)
        if (channel != null && channel.canBypassDnd())
            return true
        return isStarredContact(callerNumber)
    }

    private fun shouldVibrate(): Boolean {
        // 1. If the phone is in Silent mode, never vibrate
        if (am.ringerMode == AudioManager.RINGER_MODE_SILENT) return false
        // 2. If the phone is in Vibrate mode, always vibrate
        if (am.ringerMode == AudioManager.RINGER_MODE_VIBRATE) return true
        // 3. If the phone is in Normal (Ringing) mode:
        // First, check if the ringer volume is actually non-zero
        if (am.getStreamVolume(AudioManager.STREAM_RING) == 0) return false
        // Finally, check the system "Vibrate for calls" setting.
        // Although deprecated, it is the standard way to check the "Also vibrate for calls" toggle.
        return try {
            @Suppress("DEPRECATION")
            Settings.System.getInt(
                contentResolver,
                Settings.System.VIBRATE_WHEN_RINGING,
                0
            ) != 0
        } catch (_: Exception) {
            // If the setting can't be read, default to FALSE.
            false
        }
    }

    private fun isStarredContact(callerNumber: String): Boolean {
        if (contactsMode == "baresip" || callerNumber.isBlank())
            return false
        val phoneUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(callerNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.STARRED)
        try {
            val cursor = contentResolver
                .query(phoneUri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val isStarred = it.getInt(0) == 1
                    if (isStarred) {
                        Log.d(TAG, "Caller '$callerNumber' is a starred contact.")
                    }
                    return isStarred
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not query contacts for starred status: ${e.message}")
        }
        return false
    }

    private fun stopRinging() {
        rt!!.stop()
        if (vbTimer != null) {
            vbTimer!!.cancel()
            vbTimer = null
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun playRingBack() {
        if (mediaPlayer == null) {
            val name = "ringback_$toneCountry"
            val resourceId = resources.getIdentifier(name, "raw", packageName)
            if (resourceId != 0) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    val afd = resources.openRawResourceFd(resourceId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    isLooping = true
                    prepare()
                    start()
                }
            } else {
                Log.e(TAG, "Ringback tone $name.wav not found")
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun playBusy() {
        if (mediaPlayer == null) {
            val name = "busy_$toneCountry"
            val resourceId = resources.getIdentifier(name, "raw", packageName)
            if (resourceId != 0) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    val afd = resources.openRawResourceFd(resourceId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    setOnCompletionListener {
                        stopMediaPlayer()
                        ensureCommunicationMode()
                    }
                    prepare()
                    start()
                }
            } else {
                Log.e(TAG, "Busy tone $name.wav not found")
            }
        }
    }

    private fun stopMediaPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun setCallVolume() {
        if (callVolume != 0)
            for (streamType in listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_VOICE_CALL)) {
                origVolume[streamType] = am.getStreamVolume(streamType)
                val maxVolume = am.getStreamMaxVolume(streamType)
                am.setStreamVolume(
                    streamType,
                    (callVolume * 0.1 * maxVolume).roundToInt(),
                    0
                )
                Log.d(TAG, "Orig/new/max $streamType volume is " +
                        "${origVolume[streamType]}/${am.getStreamVolume(streamType)}/$maxVolume")
            }
    }

    private fun resetCallVolume() {
        if (callVolume != 0)
            for ((streamType, streamVolume) in origVolume) {
                am.setStreamVolume(streamType, streamVolume, 0)
                Log.d(TAG, "Reset $streamType volume to ${am.getStreamVolume(streamType)}")
            }
    }

    private fun ensureCommunicationMode() {
        val currentMode = am.mode
        val isAnyCallMode = currentMode == MODE_IN_COMMUNICATION || currentMode == MODE_IN_CALL

        val isSpeakerphoneOn = if (VERSION.SDK_INT >= 31)
            am.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn
        }

        if (Call.inCall() && isAnyCallMode) {
            cleanupRunnable?.let {
                Log.d(TAG, "Canceling pending speakerphone cleanup because call is active")
                Handler(Looper.getMainLooper()).removeCallbacks(it)
                cleanupRunnable = null
            }
            if (isSpeakerphoneOn == speakerPhone) {
                if (Call.hasTelecomCall() || currentMode == MODE_IN_COMMUNICATION) {
                    Log.d(TAG, "Already in valid call mode ($currentMode) " +
                            "with correct speaker state.")
                    return
                }
            }
        } else if (!Call.inCall() && currentMode == MODE_NORMAL) {
            if (speakerPhone) {
                Log.d(TAG, "Resetting speakerPhone UI state while idle")
                speakerPhone = false
                postServiceEvent(
                    ServiceEvent(
                        "speaker update,false",
                        arrayListOf(0L, 0L),
                        System.nanoTime()
                    )
                )
            }
            return
        }

        Log.d(TAG, "Scheduling ensureCommunicationMode (current: $currentMode) in 500ms " +
                "(target speaker: $speakerPhone)")
        val handler = Handler(Looper.getMainLooper())
        cleanupRunnable?.let { handler.removeCallbacks(it) }

        val runnable = Runnable {
            cleanupRunnable = null
            if (Call.inCall()) {
                val hasTelecom = Call.hasTelecomCall()
                if (!hasTelecom && am.mode != MODE_IN_COMMUNICATION && am.mode != MODE_IN_CALL) {
                    am.mode = MODE_IN_COMMUNICATION
                    Log.d(TAG, "Manual Mode Guard: Setting MODE_IN_COMMUNICATON from ${am.mode}")
                }
                Log.d(TAG, "Applying speakerphone state: $speakerPhone")
                if (InCallService.instance != null) {
                    Log.d(TAG, "Using InCallService for audio route: $speakerPhone")
                    @Suppress("DEPRECATION")
                    InCallService.instance!!.setAudioRoute(
                        if (speakerPhone)
                            android.telecom.CallAudioState.ROUTE_SPEAKER
                        else
                            android.telecom.CallAudioState.ROUTE_EARPIECE
                    )
                } else if (!hasTelecom) {
                    Log.d(TAG, "No Telecom connection, using AudioManager for speaker")
                    Utils.setSpeakerPhone(mainExecutor, am, speakerPhone)
                } else {
                    for (c in Call.calls()) {
                        ConnectionService.setOutput(c.callp, speakerPhone)
                    }
                }
            } else {
                if (am.mode != MODE_NORMAL) {
                    am.mode = MODE_NORMAL
                    Log.d(TAG, "Manual Mode Guard: Resetting to MODE_NORMAL")
                    Utils.clearCommunicationDevice(am)
                }
                if (speakerPhone) {
                    Log.d(TAG, "Resetting speakerPhone runtime state after call")
                    speakerPhone = false
                    postServiceEvent(
                        ServiceEvent(
                            "speaker update,false",
                            arrayListOf(0L, 0L),
                            System.nanoTime()
                        )
                    )
                }
                if (isMicMuted) {
                    setMicMute(false)
                    postServiceEvent(
                        ServiceEvent(
                            "mic muted,false",
                            arrayListOf(0L, 0L),
                            System.nanoTime()
                        )
                    )
                }
                if (!Call.hasTelecomCall())
                    resetCallVolume()
            }
        }
        cleanupRunnable = runnable
        handler.postDelayed(runnable, 500)
    }

    fun toggleSpeakerphone() {
        speakerPhone = !speakerPhone
        // Notify the UI to update the icon color
        postServiceEvent(
            ServiceEvent(
                "speaker update,$speakerPhone",
                arrayListOf(0L, 0L),
                System.nanoTime()
            )
        )
        // Apply the new audio route
        ensureCommunicationMode()
    }

    @SuppressLint("WakelockTimeout", "Wakelock")
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
        if (!isNativeReady) return

        val dnsChanged = updateDnsServers()

        val addresses = linkAddresses()
        if (linkAddresses != addresses)
            Log.d(TAG, "Old/new link addresses $linkAddresses/$addresses")

        var added = 0
        for (a in addresses)
            if (!linkAddresses.containsKey(a.key)) {
                if (Api.net_add_address_ifname(a.key, a.value) != 0)
                    Log.e(TAG, "Failed to add address: $a")
                else
                    added++
            }

        var removed = 0
        for (a in linkAddresses)
            if (!addresses.containsKey(a.key)) {
                if (Api.net_rm_address(a.key) != 0)
                    Log.e(TAG, "Failed to remove address: $a")
                else
                    removed++
            }

        val active = cm.activeNetwork
        if (added != removed || activeNetwork != active || dnsChanged)
            Log.d(TAG, "Added/Removed = $added/$removed, " +
                    "Old/New Active = $activeNetwork/$active, DNS Changed = $dnsChanged"
            )

        if (added > 0 || removed > 0 || active != activeNetwork || dnsChanged) {
            Api.net_debug()
            linkAddresses = addresses
            activeNetwork = active
            Api.uag_reset_transp(register = true, reinvite = true)
        }

        val hasWifi = allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

        if (hasWifi) {
            if (!wifiLock.isHeld) {
                Log.d(TAG, "Acquiring WiFi Lock")
                wifiLock.acquire()
            }
        }
        else {
            if (wifiLock.isHeld) {
                Log.d(TAG, "Releasing WiFi Lock")
                wifiLock.release()
            }
        }
    }

    private fun linkAddresses(): MutableMap<String, String> {
        val addresses = mutableMapOf<String, String>()
        synchronized(allNetworks) {
            for (n in allNetworks) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                ) {
                    val props = cm.getLinkProperties(n) ?: continue
                    for (la in props.linkAddresses)
                        if (la.scope == OsConstants.RT_SCOPE_UNIVERSE &&
                                props.interfaceName != null && la.address.hostAddress != null &&
                                afMatch(la.address.hostAddress!!))
                            addresses[la.address.hostAddress!!] = props.interfaceName!!
                }
            }
        }
        if (hotSpotIsEnabled) {
            for ((k, v) in hotSpotAddresses)
                if (afMatch(k))
                    addresses[k] = v
        }
        return addresses
    }

    private fun afMatch(address: String): Boolean {
        return when (addressFamily) {
            "" -> true
            "ipv4" -> address.contains(".")
            else -> address.contains(":")
        }
    }

    private fun updateDnsServers(): Boolean {
        if (isServiceRunning && !dynDns)
            return false
        val servers = mutableListOf<InetAddress>()
        // Use DNS servers first from active network (if available)
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val linkProps = cm.getLinkProperties(activeNetwork)
            if (linkProps != null)
                servers.addAll(linkProps.dnsServers)
        }
        // Then add DNS servers from the other networks
        for (n in allNetworks) {
            if (n == cm.activeNetwork) continue
            val linkProps = cm.getLinkProperties(n)
            if (linkProps != null)
                for (server in linkProps.dnsServers)
                    if (!servers.contains(server)) servers.add(server)
        }
        // Update if change
        synchronized(dnsServers) {
            if (servers != dnsServers) {
                if (isServiceRunning && Config.updateDnsServers(servers) != 0) {
                    Log.w(TAG, "Failed to update DNS servers '${servers}'")
                } else {
                    // Log.d(TAG, "Updated DNS servers: '${servers}'")
                    dnsServers = servers
                    return true
                }
            }
        }
        return false
    }

    private fun registerAndroidContactsObserver() {
        if (!androidContactsObserverRegistered)
            try {
                contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                        true, androidContactsObserver)
                androidContactsObserverRegistered = true
            } catch (e: SecurityException) {
                Log.i(TAG, "No Contacts permission: ${e.message}")
            }
    }

    private fun unRegisterAndroidContactsObserver() {
        if (androidContactsObserverRegistered) {
            contentResolver.unregisterContentObserver(androidContactsObserver)
            androidContactsObserverRegistered = false
        }
    }

    private fun registerPhoneAccount() {
        val phoneAccountHandle = getPhoneAccountHandle(this)
        val phoneAccount = android.telecom.PhoneAccount.builder(phoneAccountHandle, getString(R.string.app_name))
            .setCapabilities(android.telecom.PhoneAccount.CAPABILITY_SELF_MANAGED)
            .addSupportedUriScheme(android.telecom.PhoneAccount.SCHEME_SIP)
            .addSupportedUriScheme(android.telecom.PhoneAccount.SCHEME_TEL)
            .build()
        tm.registerPhoneAccount(phoneAccount)
    }

    private fun cleanService() {
        if (!isServiceClean) {
            if (hotSpotReceiverRegistered) {
                try {
                    unregisterReceiver(hotSpotReceiver)
                } catch (_: IllegalArgumentException) {}
                hotSpotReceiverRegistered = false
            }
            if (bluetoothReceiverRegistered) {
                try {
                    unregisterReceiver(bluetoothReceiver)
                } catch (_: IllegalArgumentException) {}
                bluetoothReceiverRegistered = false
            }
            if (airplaneModeReceiverRegistered) {
                try {
                    unregisterReceiver(airplaneModeReceiver)
                } catch (_: IllegalArgumentException) {}
                airplaneModeReceiverRegistered = false
            }
            if (telephonyCallbackRegistered) {
                if (VERSION.SDK_INT >= 31) {
                    try {
                        telephonyManager.unregisterTelephonyCallback(telephonyCallback)
                    } catch (_: Exception) {}
                } else {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
                }
                telephonyCallbackRegistered = false
            }
            val callps = ConnectionService.connections.keys.toList()
            for (callp in callps)
                ConnectionService.onCallClosed(callp)
            ConnectionService.pendingOutgoingConnection?.let {
                it.setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
                it.destroy()
                ConnectionService.pendingOutgoingConnection = null
            }
            stopRinging()
            stopMediaPlayer()
            abandonAudioFocus(this)
            uas.value = emptyList()
            uasStatus.value = emptyMap()
            callHistory.clear()
            messages = emptyList()
            if (this::nm.isInitialized)
                nm.cancelAll()
            if (this::partialWakeLock.isInitialized && partialWakeLock.isHeld)
                partialWakeLock.release()
            if (this::proximityWakeLock.isInitialized && proximityWakeLock.isHeld)
                proximityWakeLock.release()
            if (this::wifiLock.isInitialized)
                wifiLock.release()
            if (this::networkCallback.isInitialized) {
                try {
                    cm.unregisterNetworkCallback(networkCallback)
                } catch (_: Exception) {}
            }
            if (androidContactsObserverRegistered) {
                try {
                    contentResolver.unregisterContentObserver(androidContactsObserver)
                } catch (_: Exception) {}
                androidContactsObserverRegistered = false
            }
            releaseAudioEffects()
            isServiceClean = true
        }
    }

    private external fun baresipStart(
        path: String,
        addresses: String,
        logLevel: Int,
        software: String
    )

    external fun baresipStop(force: Boolean)

    @SuppressLint("MutableCollectionMutableState")
    companion object {

        private const val TAG = "BaresipService"

        var instance: BaresipService? = null
        var isServiceRunning = false
        var isNativeReady = false
        var isStartReceived = false
        var isConfigInitialized = false
        var libraryLoaded = false
        var callVolume = 0

        @Volatile
        var speakerPhone = false
        var speakerPhoneAuto = false
        var audioDelay = if (VERSION.SDK_INT < 31) 1500L else 500L
        var dynDns = false
        var filesPath = ""
        var pName = ""
        var logLevel = 2
        var sipTrace = false
        var callActionUri = ""
        var isMainVisible = false
        var isMicMuted = false
        var isRecOn = false
        var toneCountry = "us"
        var proximitySensing = true

        val uas = mutableStateOf(emptyList<UserAgent>())
        val uasStatus = mutableStateOf(emptyMap<String, Int>())
        var contacts by mutableStateOf(mutableListOf<Contact>())
        val baresipContacts = mutableStateOf(emptyList<Contact.BaresipContact>())
        val androidContacts = mutableStateOf(emptyList<Contact.AndroidContact>())
        val contactNames = mutableStateOf(emptyList<String>())

        val darkTheme = mutableStateOf(false)
        val dynamicColors = mutableStateOf(false)
        var messages by mutableStateOf(emptyList<Message>())
        val messageUpdate = MutableLiveData<Long>()
        val registrationUpdate = MutableLiveData<Long>()

        val serviceEvent = MutableLiveData<Event<Long>>()
        val serviceEvents = mutableListOf<ServiceEvent>()

        val calls = ArrayList<Call>()
        var callHistory = ArrayList<CallHistoryNew>()
        var blocked = ArrayList<Blocked>()

        var contactsMode by mutableStateOf("baresip")
        var addressFamily = ""
        var dnsServers = listOf<InetAddress>()

        // <aor, password> of those accounts that have auth username without auth password
        val aorPasswords = mutableMapOf<String, String>()
        var aecAvailable = false
        var agcAvailable = false
        var nsAvailable = false
        private var aec: AcousticEchoCanceler? = null
        private var agc: AutomaticGainControl? = null
        private var ns: NoiseSuppressor? = null
        private var recorderSessionId = 0
        private var btAdapter: BluetoothAdapter? = null
        private var audioFocusRequest: AudioFocusRequest? = null

        private fun releaseAudioEffects() {
            aec?.enabled = false
            aec?.release()
            aec = null
            agc?.enabled = false
            agc?.release()
            agc = null
            ns?.enabled = false
            ns?.release()
            ns = null
            recorderSessionId = 0
        }

        var rt: Ringtone? = null

        var colorblind = false
        val circleGreen = mapOf(
            true to R.drawable.circle_green_blind,
            false to R.drawable.circle_green
        )
        val circleYellow = mapOf(
            true to R.drawable.circle_yellow_blind,
            false to R.drawable.circle_yellow
        )
        val circleRed = mapOf(
            true to R.drawable.circle_red_blind,
            false to R.drawable.circle_red
        )

        internal const val KEY_TEXT_REPLY = "key_text_reply_baresip"
        private const val PHONE_ACCOUNT_ID = "baresip_phone_account"

        fun getPhoneAccountHandle(ctx: Context): PhoneAccountHandle {
            val componentName = android.content.ComponentName(ctx, ConnectionService::class.java)
            return PhoneAccountHandle(componentName, PHONE_ACCOUNT_ID)
        }

        fun setMicMute(mute: Boolean) {
            instance?.let {
                val am = it.getSystemService(AUDIO_SERVICE) as AudioManager
                am.isMicrophoneMute = mute
            }
            isMicMuted = mute
            Api.calls_mute(mute)
        }

        fun postServiceEvent(event: ServiceEvent) {
            synchronized(serviceEvents) {
                serviceEvents.add(event)
                if (serviceEvents.size == 1) {
                    Log.d(TAG, "Posted service event ${event.event} at ${event.timeStamp}")
                    serviceEvent.postValue(Event(event.timeStamp))
                } else
                    Log.d(TAG, "Added service event ${event.event}")
            }
        }

        fun requestAudioFocus(ctx: Context): Boolean {
            Log.d(TAG, "Requesting audio focus")
            if (audioFocusRequest != null) {
                Log.d(TAG, "Already focused")
                return true
            }
            val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener { }
                    .build()
            if (am.requestAudioFocus(audioFocusRequest!!) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                Log.d(TAG, "requestAudioFocus granted")
            else {
                Log.w(TAG, "requestAudioFocus denied")
                audioFocusRequest = null
            }
            return audioFocusRequest != null
        }

        fun abandonAudioFocus(ctx: Context) {
            if (audioFocusRequest != null) {
                Log.d(TAG, "Abandoning audio focus")
                val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
                if (am.abandonAudioFocusRequest(audioFocusRequest!!) ==
                        AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                    audioFocusRequest = null
                else
                    Log.e(TAG, "Failed to abandon audio focus")
            }
        }

        private fun isBluetoothScoOn(am: AudioManager): Boolean {
            return if (VERSION.SDK_INT < 31)
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn
            else
                if (am.communicationDevice != null)
                    am.communicationDevice!!.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                else
                    false
        }

        private fun startBluetoothSco(ctx: Context, delay: Long, count: Int) {
            val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
            if (isBluetoothScoOn(am)) {
                Log.d(TAG, "Bluetooth SCO is already on")
                return
            }
            Log.d(TAG, "Starting Bluetooth SCO at count $count")
            Handler(Looper.getMainLooper()).postDelayed({
                if (VERSION.SDK_INT < 31) {
                    @Suppress("DEPRECATION")
                    am.startBluetoothSco()
                }
                else
                    Utils.setCommunicationDevice(am, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
                if (!isBluetoothScoOn(am) && count > 1)
                    startBluetoothSco(ctx, delay, count - 1)
                else
                    am.isBluetoothScoOn = true
            }, delay)
        }

        private fun stopBluetoothSco(ctx: Context) {
            Log.d(TAG, "Stopping Bluetooth SCO")
            val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
            if (!isBluetoothScoOn(am)) {
                Log.d(TAG, "Bluetooth SCO is already off")
                return
            }
            Handler(Looper.getMainLooper()).postDelayed({
                if (VERSION.SDK_INT < 31)
                    @Suppress("DEPRECATION")
                    am.stopBluetoothSco()
                else
                    am.clearCommunicationDevice()
                am.isBluetoothScoOn = false
            }, 100)
        }
    }
}

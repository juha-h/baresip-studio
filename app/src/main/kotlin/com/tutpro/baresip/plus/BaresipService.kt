package com.tutpro.baresip.plus

import android.Manifest
import android.Manifest.permission.CAMERA
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
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.MODE_IN_COMMUNICATION
import android.media.AudioManager.MODE_NORMAL
import android.media.AudioManager.RINGER_MODE_SILENT
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
import android.telecom.TelecomManager
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.MessagingStyle
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.GregorianCalendar
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.math.roundToInt

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
    private lateinit var vibrator: Vibrator
    private lateinit var partialWakeLock: PowerManager.WakeLock
    private lateinit var proximityWakeLock: PowerManager.WakeLock
    private lateinit var wifiLock: WifiManager.WifiLock
    private lateinit var bluetoothReceiver: BroadcastReceiver
    private lateinit var hotSpotReceiver: BroadcastReceiver
    private lateinit var androidContactsObserver: ContentObserver
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
    private var isServiceClean = false

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "BaresipService onCreate")

        intent = Intent("com.tutpro.baresip.EVENT")
        intent.setPackage("com.tutpro.baresip")

        filesPath = filesDir.absolutePath
        pName = packageName

        am = applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager

        val ntUri = RingtoneManager.getActualDefaultRingtoneUri(applicationContext,
                RingtoneManager.TYPE_NOTIFICATION)
        nt = RingtoneManager.getRingtone(applicationContext, ntUri)

        val rtUri = if (Preferences(applicationContext).ringtoneUri == "")
            Settings.System.RINGTONE.toUri()
        else
            Preferences(applicationContext).ringtoneUri!!.toUri()
        rt = RingtoneManager.getRingtone(applicationContext, rtUri)

        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        snb = NotificationCompat.Builder(this, LOW_CHANNEL_ID)

        pm = getSystemService(POWER_SERVICE) as PowerManager

        vibrator = if (VERSION.SDK_INT >= 31) {
            val vibratorManager = applicationContext.getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            applicationContext.getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        // This is needed to keep service running also in Doze Mode
        partialWakeLock = pm.run {
            newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "com.tutpro.baresip:partial_wakelock"
            ).apply {
                acquire()
            }
        }

        cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        cm.registerNetworkCallback(
                builder.build(),
                object : ConnectivityManager.NetworkCallback() {

                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        Log.d(TAG, "Network $network is available")
                        if (network !in allNetworks)
                            allNetworks.add(network)
                    }

                    override fun onLosing(network: Network, maxMsToLive: Int) {
                        super.onLosing(network, maxMsToLive)
                        Log.d(TAG, "Network $network is losing after $maxMsToLive ms")
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        Log.d(TAG, "Network $network is lost")
                        if (network in allNetworks)
                            allNetworks.remove(network)
                        if (isServiceRunning)
                            updateNetwork()
                    }

                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        super.onCapabilitiesChanged(network, caps)
                        Log.d(TAG, "Network $network capabilities changed: $caps")
                        if (network !in allNetworks)
                            allNetworks.add(network)
                        if (isServiceRunning)
                            updateNetwork()
                    }

                    override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) {
                        super.onLinkPropertiesChanged(network, props)
                        Log.d(TAG, "Network $network link properties changed: $props")
                        if (network !in allNetworks)
                            allNetworks.add(network)
                        if (isServiceRunning)
                            updateNetwork()
                    }

                }
        )

        wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
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

        this.registerReceiver(hotSpotReceiver,
            IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"))

        tm = getSystemService(TELECOM_SERVICE) as TelecomManager

        btm = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btm.adapter

        proximityWakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "com.tutpro.baresip:proximity_wakelock")

        wifiLock = if (VERSION.SDK_INT < 29)
            @Suppress("DEPRECATION")
            wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Baresip")
        else
            wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Baresip")

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
                                if (audioFocusRequest != null)
                                    startBluetoothSco(applicationContext, 1000L, 3)
                            }
                            BluetoothHeadset.STATE_DISCONNECTED -> {
                                Log.d(TAG, "Bluetooth headset is disconnected")
                                if (audioFocusRequest != null)
                                    stopBluetoothSco(applicationContext)
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

        if (btAdapter != null) {
            val filter = IntentFilter()
            filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
            filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            this.registerReceiver(bluetoothReceiver, filter)
        }

        androidContactsObserver = object : ContentObserver(null) {
            override fun onChange(self: Boolean) {
                Log.d(TAG, "Android contacts change")
                if (contactsMode != "baresip") {
                    Contact.loadAndroidContacts(this@BaresipService.applicationContext)
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
                        postServiceEvent(ServiceEvent("stopped", arrayListOf(""), System.nanoTime()))
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
                        Utils.copyAssetToFile(applicationContext, a, "$filesPath/$a")
                    } else {
                        Log.i(TAG, "Asset '$a' already copied")
                    }
                    if (a == "config")
                        Config.initialize(applicationContext)
                }

                if (!File(filesDir, "tmp").exists())
                    File(filesDir, "tmp").mkdir()

                if (!File(filesDir, "recordings").exists())
                    File(filesDir, "recordings").mkdir()

                if (contactsMode != "android")
                    Contact.restoreBaresipContacts()
                if (contactsMode != "baresip") {
                    Contact.loadAndroidContacts(applicationContext)
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

                Message.restore()

                hotSpotAddresses = Utils.hotSpotAddresses()
                linkAddresses = linkAddresses()
                var addresses = ""
                for (la in linkAddresses)
                    addresses = "$addresses;${la.key};${la.value}"
                Log.i(TAG, "Link addresses: $addresses")
                activeNetwork = cm.activeNetwork
                Log.i(TAG, "Active network: $activeNetwork")

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

                val accounts = Utils.getFileContents("$filesPath/accounts")
                if ((accounts != null) && accounts.isNotEmpty()) {
                    Utils.putFileContents("$filesPath/accounts",
                        accounts.toString(Charsets.UTF_8).replace(
                            "pubint=0;call_transfer",
                            "pubint=0;inreq_allowed=yes;call_transfer"
                        ).toByteArray(Charsets.UTF_8)
                    )
                }

                if (linkAddresses.isEmpty())
                    toast(getString(R.string.no_network), Toast.LENGTH_LONG)

                if (!aecAvailable)
                    toast(getString(R.string.no_aec), Toast.LENGTH_LONG)

                if (!supportedCameras)
                    toast(getString(R.string.no_cameras), Toast.LENGTH_LONG)
            }

            "Start Content Observer" -> {
                registerAndroidContactsObserver()
            }

            "Stop Content Observer" -> {
                unRegisterAndroidContactsObserver()
            }

            "Call Answer" -> {
                val uap = intent!!.getLongExtra("uap", 0L)
                val callp = intent.getLongExtra("callp", 0L)
                stopRinging()
                stopMediaPlayer()
                am.mode = MODE_IN_COMMUNICATION
                setCallVolume()
                proximitySensing(true)
                Api.ua_answer(uap, callp, Api.VIDMODE_ON)
            }

            "Call Reject" -> {
                val callp = intent!!.getLongExtra("callp", 0L)
                val call = Call.ofCallp(callp)
                if (call == null) {
                    Log.w(TAG, "onStartCommand did not find call $callp")
                } else {
                    val peerUri = call.peerUri
                    val aor = call.ua.account.aor
                    Log.d(TAG, "Aor $aor rejected incoming call $callp from $peerUri")
                    call.rejected = true
                    Api.ua_hangup(call.ua.uap, callp, 486, "Rejected")
                }
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
                            Log.d(TAG, "Inline Reply from $aor to $peerUri: $replyText")
                            Message.updateAorMessage(aor, timeStamp)
                            val time = System.currentTimeMillis()
                            val msg = Message(aor, peerUri, replyText, time, MESSAGE_UP_WAIT, 0, "", false)
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
                darkTheme.value = Preferences(applicationContext).displayTheme ==
                    AppCompatDelegate.MODE_NIGHT_YES
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
        if (isServiceRunning)
            sendBroadcast(Intent("com.tutpro.baresip.Restart"))
    }

    @Suppress("unused")
    @SuppressLint("UnspecifiedImmutableFlag", "DiscouragedApi")
    @Keep
    fun uaEvent(event: String, uap: Long, callp: Long) {

        if (!isServiceRunning) return

        val ev = event.split(",")

        if (ev[0] == "create") {

            val ua = UserAgent(uap)
            ua.status = if (ua.account.regint == 0)
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
            recorderSessionId = ev[1].toInt()
            Log.d(TAG, "got recorder sessionid $recorderSessionId")
            if (recorderSessionId != 0) {
                if (aecAvailable) {
                    aec = AcousticEchoCanceler.create(recorderSessionId)
                    if (aec != null) {
                        if (!aec!!.getEnabled()) {
                            aec!!.setEnabled(true)
                            if (aec!!.getEnabled())
                                Log.d(TAG, "AEC is enabled")
                            else
                                Log.w(TAG, "Failed to enable AEC")
                        }
                        else
                            Log.d(TAG, "AEC is already enabled")
                    } else
                        Log.w(TAG, "Failed to create AEC for session $recorderSessionId")
                }
                if (agcAvailable) {
                    agc = AutomaticGainControl.create(recorderSessionId)
                    if (agc != null) {
                        if (!agc!!.getEnabled()) {
                            agc!!.setEnabled(true)
                            if (agc!!.getEnabled())
                                Log.d(TAG, "AGC is enabled")
                        }
                    } else
                        Log.w(TAG, "Failed to create AGC")
                }
                if (nsAvailable) {
                    ns = NoiseSuppressor.create(recorderSessionId)
                    if (ns != null) {
                        if (!ns!!.getEnabled()) {
                            ns!!.setEnabled(true)
                            if (ns!!.getEnabled())
                                Log.d(TAG, "NS is enabled")
                        }
                    } else
                        Log.w(TAG, "Failed to create NS")
                }
                recorderSessionId = 0
            }
            return
        }

        val ua = UserAgent.ofUap(uap)
        val aor = ua?.account?.aor

        Log.d(TAG, "got uaEvent $event/$aor/$callp")

        if (ua == null) {
            when (ev[0]) {
                "snapshot" -> {
                    val file = File(ev[2])
                    if (file.length() > 0) {
                        val fileName = ev[2].split("/").last()
                        try {
                            val bitmap = BitmapFactory.decodeStream(file.inputStream())
                            Utils.savePicture(this, bitmap, fileName)
                            Log.d(TAG, "Saved snapshot to $fileName")
                        } catch (e: IOException) {
                            Log.e(TAG, "Failed to save snapshot to $fileName: ${e.message}")
                        }
                    }
                    file.delete()
                }
                else -> {
                    Log.w(TAG, "uaEvent did not find ua $uap")
                }
            }
            return
        }

        val call = Call.ofCallp(callp)
        if (call == null && callp != 0L &&
            !setOf("incoming call", "call incoming", "call closed").contains(ev[0])) {
            Log.w(TAG, "uaEvent '$event' did not find call $callp")
            return
        }

        var newEvent: String? = null

        for (accountIndex in uas.value.indices) {
            if (uas.value[accountIndex].account.aor == aor) {
                when (ev[0]) {
                    "registering", "unregistering" -> {
                        updateStatusNotification()
                        ua.uaUpdateStatus(circleYellow.getValue(colorblind))
                        if (isMainVisible)
                            registrationUpdate.postValue(System.currentTimeMillis())
                        return
                    }
                    "registered" -> {
                        ua.uaUpdateStatus(
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
                        ua.uaUpdateStatus(if (Api.account_regint(ua.account.accp) == 0)
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
                        if (call!!.status == "transferring")
                            break
                        stopMediaPlayer()
                        setCallVolume()
                        if (speakerPhone && !Utils.isSpeakerPhoneOn(am))
                            Utils.toggleSpeakerPhone(ContextCompat.getMainExecutor(this), am)
                        proximitySensing(true)
                    }
                    "call ringing" -> {
                        playRingBack()
                        return
                    }
                    "call progress" -> {
                        if ((ev[1].toInt() and Api.SDP_RECVONLY) != 0)
                            stopMediaPlayer()
                        else
                            playRingBack()
                        return
                    }
                    "incoming call" -> {
                        val peerUri = ev[1]
                        val bevent = ev[2].toLong()
                        val toastMsg = if (!Utils.checkPermissions(this, arrayOf(RECORD_AUDIO)))
                            getString(R.string.no_calls)
                        else if (!requestAudioFocus(applicationContext))
                            // request fails if there is an active telephony call
                            getString(R.string.audio_focus_denied)
                        else if (Call.inCall())
                            String.format(getString(R.string.call_auto_rejected),
                                Utils.friendlyUri(this, peerUri, ua.account))
                        else
                            ""
                        if (toastMsg != "") {
                            Log.d(TAG, "Auto-rejecting incoming call $uap/$peerUri")
                            Api.sip_treply(callp, 486, "Busy Here")
                            Api.bevent_stop(bevent)
                            toast(toastMsg)
                            val name = "callwaiting_$toneCountry"
                            val resourceId = applicationContext.resources.getIdentifier(
                                name,
                                "raw",
                                applicationContext.packageName)
                            if (resourceId != 0) {
                                playUnInterrupted(resourceId, 1)
                            } else {
                                Log.e(TAG, "Callwaiting tone $name.wav not found\")")
                            }
                            if (ua.account.callHistory) {
                                CallHistoryNew(aor, peerUri, "in").add()
                                ua.account.missedCalls = true
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
                        Call(callp, ua, peerUri, "in", "incoming").add()
                        if (speakerPhone && !Utils.isSpeakerPhoneOn(am))
                            Utils.toggleSpeakerPhone(ContextCompat.getMainExecutor(this), am)
                        if (ua.account.answerMode == Api.ANSWERMODE_AUTO) {
                            val newIntent = Intent(this, MainActivity::class.java)
                            newIntent.flags =
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                            newIntent.putExtra("action", "call answer")
                            newIntent.putExtra("callp", callp)
                            startActivity(newIntent)
                            return
                        }
                        val channelId = if (shouldVibrate()) MEDIUM_CHANNEL_ID else HIGH_CHANNEL_ID
                        if (shouldStartRinging(channelId))
                            startRinging()
                        if (!Utils.isVisible()) {
                            val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            val intent = Intent(applicationContext, MainActivity::class.java)
                                .putExtra("action", "call show")
                                .putExtra("callp", callp)
                            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                            val pi = PendingIntent.getActivity(applicationContext, CALL_REQ_CODE, intent, piFlags)
                            val nb = NotificationCompat.Builder(this, channelId)
                            val caller = Utils.friendlyUri(this, peerUri, ua.account)
                            val person = Person.Builder().setName(caller).build()
                            nb.setSmallIcon(R.drawable.ic_stat_call)
                                .setColor(ContextCompat.getColor(this, R.color.colorBaresip))
                                .setContentIntent(pi)
                                .setCategory(Notification.CATEGORY_CALL)
                                .setAutoCancel(false)
                                .setOngoing(true)
                                .setContentTitle(getString(R.string.incoming_call_from))
                                .setContentText(caller)
                                .setWhen(System.currentTimeMillis())
                                .setShowWhen(true)
                                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setFullScreenIntent(pi, true)
                            val answerIntent = Intent(applicationContext, MainActivity::class.java)
                                .putExtra("action", "call answer")
                                .putExtra("callp", callp)
                            val api = PendingIntent.getActivity(applicationContext, ANSWER_REQ_CODE,
                                answerIntent, piFlags)
                            val rejectIntent = Intent(this, BaresipService::class.java)
                            rejectIntent.action = "Call Reject"
                            rejectIntent.putExtra("callp", callp)
                            val rpi = PendingIntent.getService(this, REJECT_REQ_CODE, rejectIntent, piFlags)
                            nb.setStyle(NotificationCompat.CallStyle.forIncomingCall(person, rpi, api))
                            nm.notify(CALL_NOTIFICATION_ID, nb.build())
                            return
                        }
                    }
                    "remote call answered" -> {
                        newEvent = "call update"
                    }
                    "remote call offered" -> {
                        val callHasVideo = ev[1] == "1"
                        val remoteHasVideo = ev[2] == "1"
                        val ldir = ev[3].toInt()
                        val rdir = if (Utils.isCameraAvailable(this))
                            ev[4].toInt()
                        else
                            ev[4].toInt() and Api.SDP_RECVONLY
                        when (ev[5]) {
                            "0", "1" -> call!!.held = true  // inactive, recvonly
                            "2", "3" -> call!!.held = false // sendonly, sendrecv
                        }
                        if (!isMainVisible || call!!.status != "connected")
                            return
                        if (!(callHasVideo && remoteHasVideo && ldir == 0) &&
                            (!callHasVideo && remoteHasVideo &&
                                    (rdir == Api.SDP_SENDRECV) && (ldir != rdir))) {
                            postServiceEvent(ServiceEvent("call video request",
                                arrayListOf(uap, callp, rdir), System.nanoTime()))
                            return
                        }
                        newEvent = "call update"
                    }
                    "call answered" -> {
                        stopMediaPlayer()
                        if (call!!.status == "incoming")
                            call.status = "answered"
                        else
                            return
                    }
                    "call redirect", "video call redirect"-> {
                        stopMediaPlayer()
                    }
                    "call established" -> {
                        nm.cancel(CALL_NOTIFICATION_ID)
                        Log.d(TAG, "AoR $aor call $callp established in mode ${am.mode}")
                        if (am.mode != MODE_IN_COMMUNICATION)
                            am.mode = MODE_IN_COMMUNICATION
                        call!!.status = "connected"
                        call.onhold = false
                        if (ua.account.callHistory)
                            call.startTime = GregorianCalendar()
                        if (!isMainVisible)
                            return
                    }
                    "call update" -> {
                        call!!.held = when (ev[1].toInt()) {
                            Api.SDP_INACTIVE, Api.SDP_RECVONLY -> true
                            else /* Api.SDP_SENDONLY, Api.SDP_SENDRECV */ -> false
                        }
                        if (call.state() == Api.CALL_STATE_EARLY) {
                            if ((ev[1].toInt() and Api.SDP_RECVONLY) != 0)
                                stopMediaPlayer()
                            else
                                playRingBack()
                        }
                        if (!isMainVisible || call.status != "connected")
                            return
                    }
                    "call verified", "call secure" -> {
                        if (ev[0] == "call secure") {
                            call!!.security = R.drawable.locked_yellow
                        } else {
                            call!!.security = R.drawable.locked_green
                            call.zid = ev[1]
                        }
                        if (!isMainVisible)
                            return
                    }
                    "call transfer" -> {
                        if (!Utils.isVisible()) {
                            val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            val intent = Intent(applicationContext, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            intent.putExtra("action", "transfer show")
                                .putExtra("callp", callp).putExtra("uri", ev[1])
                            val pi = PendingIntent.getActivity(applicationContext, TRANSFER_REQ_CODE, intent, piFlags)
                            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                            val target = Utils.friendlyUri(this, ev[1], ua.account)
                            nb.setSmallIcon(R.drawable.ic_stat_call)
                                .setColor(ContextCompat.getColor(this, R.color.colorBaresip))
                                .setContentIntent(pi)
                                .setDefaults(Notification.DEFAULT_SOUND)
                                .setAutoCancel(true)
                                .setContentTitle(getString(R.string.transfer_request_to))
                                .setContentText(target)
                            val acceptIntent = Intent(applicationContext, MainActivity::class.java)
                            acceptIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            acceptIntent.putExtra("action", "transfer accept")
                                .putExtra("callp", callp).putExtra("uri", ev[1])
                            val acceptPendingIntent = PendingIntent.getActivity(applicationContext,
                                ACCEPT_REQ_CODE, acceptIntent, piFlags)
                            val denyIntent = Intent(this, BaresipService::class.java)
                            denyIntent.action = "Transfer Deny"
                            denyIntent.putExtra("callp", callp)
                            val denyPendingIntent = PendingIntent.getService(this,
                                DENY_REQ_CODE, denyIntent, piFlags)
                            nb.addAction(R.drawable.ic_stat_call, getString(R.string.accept), acceptPendingIntent)
                            nb.addAction(R.drawable.ic_stat_call_end, getString(R.string.deny), denyPendingIntent)
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
                        if (call != null) {
                            nm.cancel(CALL_NOTIFICATION_ID)
                            stopRinging()
                            stopMediaPlayer()
                            aec?.release()
                            aec = null
                            agc?.release()
                            agc = null
                            ns?.release()
                            ns = null
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
                            call.remove()
                            val reason = ev[1]
                            val tone = ev[2]
                            if (tone == "busy") {
                                playBusy()
                            } else if (!Call.inCall()) {
                                resetCallVolume()
                                abandonAudioFocus(applicationContext)
                                proximitySensing(false)
                            }
                            if (call.dir == "out")
                                call.rejected = call.startTime == null &&
                                        !reason.startsWith("408") &&
                                        !reason.startsWith("480") &&
                                        !reason.startsWith("Connection reset by")
                            val missed = call.dir == "in" && call.startTime == null && !call.rejected
                            val completedElsewhere = missed && ev[2].startsWith("SIP") &&
                                    ev[2].contains(";cause=200")
                            if (ua.account.callHistory) {
                                val history = CallHistoryNew(aor, call.peerUri, call.dir)
                                history.stopTime = GregorianCalendar()
                                history.startTime = if (completedElsewhere) history.stopTime else call.startTime
                                history.rejected = call.rejected
                                if (call.startTime != null && call.dumpfiles[0] != "")
                                    history.recording = call.dumpfiles
                                history.add()
                                ua.account.missedCalls = ua.account.missedCalls || missed
                            }
                            if (!Utils.isVisible()) {
                                if (missed) {
                                    val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                    val caller = Utils.friendlyUri(this, call.peerUri, ua.account)
                                    val intent = Intent(applicationContext, MainActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                    intent.putExtra("action", "call missed")
                                            .putExtra("uap", uap)
                                    val pi = PendingIntent.getActivity(applicationContext, CALL_REQ_CODE,
                                        intent, piFlags)
                                    val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                                    nb.setSmallIcon(R.drawable.ic_stat_phone_missed)
                                            .setColor(ContextCompat.getColor(this, R.color.colorBaresip))
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
                                    } else {
                                        nb.setContentTitle(getString(R.string.missed_calls))
                                        nb.setContentText(
                                                String.format(getString(R.string.missed_calls_count),
                                                        missedCalls + 1))
                                    }
                                    nm.notify(CALL_MISSED_NOTIFICATION_ID, nb.build())
                                }
                                return
                            }
                        }
                        val reason = ev[1].trim()
                        if ((reason != "") && (ua.calls().isEmpty())) {
                            if (reason[0].isDigit()) {
                                if (reason[0] != '3')
                                    toast("${getString(R.string.call_failed)}: $reason")
                            } else {
                                toast("${getString(R.string.call_closed)}: ${Api.call_peer_uri(callp)}: $reason")
                            }
                        }
                    }
                }
            }
        }

        postServiceEvent(ServiceEvent(newEvent ?: event, arrayListOf(uap, callp), System.nanoTime()))

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

        val charsetString = Utils.paramValue(cType.replace(" ", ""), "charset")
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

        val timeStamp = System.currentTimeMillis()
        val timeStampString = timeStamp.toString()
        Log.d(TAG, "Message event for $uap from $peerUri at $timeStampString")
        Message(ua.account.aor, peerUri, text, timeStamp, MESSAGE_DOWN, 0, "", true).add()
        ua.account.unreadMessages = true

        if (!Utils.isVisible()) {

            // common flags
            val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

            // message show
            val intent = Intent(applicationContext, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("action", "message show").putExtra("uap", uap).putExtra("peer", peerUri)
            val pi = PendingIntent.getActivity(applicationContext, MESSAGE_REQ_CODE, intent, piFlags)

            // message notification builder
            val senderDisplayName = Utils.friendlyUri(this, peerUri, ua.account)
            val senderPerson = Person.Builder()
                .setName(senderDisplayName)
                .setKey(peerUri)
                .build()
            val localUserPerson = Person.Builder()
                .setName(getString(R.string.you))
                .setKey(ua.account.aor)
                .build()
            val messagingStyle = MessagingStyle(localUserPerson)
                .setConversationTitle(null)
                .setGroupConversation(false)
                .addMessage(text, timeStamp, senderPerson)
            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_message)
                .setColor(ContextCompat.getColor(this, R.color.colorBaresip))
                .setContentIntent(pi)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setAutoCancel(true)
                .setStyle(messagingStyle)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            // messafe inline reply
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(getString(R.string.reply))
                .build()
            val directReplyIntent = Intent(this, BaresipService::class.java)
            directReplyIntent.action = "Message Inline Reply"
            directReplyIntent.putExtra("uap", uap).putExtra("peer", peerUri).putExtra("time", timeStamp)
            val directReplyPendingIntent = PendingIntent.getService(
                this,
                DIRECT_REPLY_REQ_CODE,
                directReplyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val inlineReplyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_stat_reply,
                getString(R.string.reply),
                directReplyPendingIntent
            ).addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).
                build()

            // message save
            val saveIntent = Intent(this, BaresipService::class.java)
            saveIntent.action = "Message Save"
            saveIntent.putExtra("uap", uap).putExtra("time", timeStampString)
            val savePendingIntent = PendingIntent.getService(this, SAVE_REQ_CODE, saveIntent, piFlags)
            val saveAction = NotificationCompat.Action.Builder(
                R.drawable.ic_stat_save,
                getString(R.string.save),
                savePendingIntent
            ).build()

            // message delete
            val deleteIntent = Intent(this, BaresipService::class.java)
            deleteIntent.action = "Message Delete"
            deleteIntent.putExtra("uap", uap).putExtra("time", timeStampString)
            val deletePendingIntent = PendingIntent.getService(this, DELETE_REQ_CODE, deleteIntent, piFlags)
            val deleteAction = NotificationCompat.Action.Builder(
                R.drawable.ic_stat_delete,
                getString(R.string.delete),
                deletePendingIntent
            ).build()

            nb.addAction(inlineReplyAction).addAction(saveAction).addAction(deleteAction)
            nm.notify(MESSAGE_NOTIFICATION_ID, nb.build())

            return
        }

        if (nm.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL)
            nt.play()

        postServiceEvent(ServiceEvent("message show", arrayListOf(uap, peerUri), System.nanoTime()))
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

    @Suppress("unused")
    @Keep
    fun started() {
        Log.d(TAG, "Received 'started' from baresip")
        Api.net_debug()
        postServiceEvent(ServiceEvent("started", arrayListOf(callActionUri), System.nanoTime()))
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
        quitTimer.cancel()
        cleanService()
        isServiceRunning = false
        postServiceEvent(ServiceEvent("stopped", arrayListOf(error), System.nanoTime()))
        stopSelf()
    }

    private fun createNotificationChannels() {
        val lowChannel = NotificationChannel(LOW_CHANNEL_ID, "No sound or vibrate",
            NotificationManager.IMPORTANCE_LOW)
        lowChannel.enableVibration(false)
        lowChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(lowChannel)

        val highChannel = NotificationChannel(HIGH_CHANNEL_ID, "Sound and vibrate",
            NotificationManager.IMPORTANCE_HIGH)
        highChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        highChannel.enableVibration(true)
        nm.createNotificationChannel(highChannel)

        val mediumChannel = NotificationChannel(MEDIUM_CHANNEL_ID, "Sound",
            NotificationManager.IMPORTANCE_HIGH)
        mediumChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        mediumChannel.enableVibration(false)
        nm.createNotificationChannel(mediumChannel)
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun showStatusNotification() {
        val intent = Intent(applicationContext, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
        val pi = PendingIntent.getActivity(applicationContext, STATUS_REQ_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE)
        val notificationLayout = RemoteViews(packageName, R.layout.status_notification)
        snb.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(pi)
                .setOngoing(true)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(notificationLayout)
        if (VERSION.SDK_INT >= 31)
            snb.foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_DEFAULT
        try {
            if (VERSION.SDK_INT >= 29) {
                var types = FOREGROUND_SERVICE_TYPE_PHONE_CALL
                if (VERSION.SDK_INT >= 30) {
                    if (ContextCompat.checkSelfPermission(this, RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                        types = types or FOREGROUND_SERVICE_TYPE_MICROPHONE
                    else
                        Log.w(TAG, "showStatusNotification: RECORD_AUDIO permission not granted")
                    if (ContextCompat.checkSelfPermission(this, CAMERA) == PackageManager.PERMISSION_GRANTED)
                        types = types or FOREGROUND_SERVICE_TYPE_CAMERA
                    else
                        Log.w(TAG, "showStatusNotification: CAMERA permission not granted")
                }
                startForeground(STATUS_NOTIFICATION_ID, snb.build(), types)
            }
            else
                startForeground(STATUS_NOTIFICATION_ID, snb.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }
    }

    private fun updateStatusNotification() {
        val notificationLayout = RemoteViews(packageName, R.layout.status_notification)
        for (i: Int in 0..3) {
            val resId = when (i) {
                0-> R.id.status0
                1-> R.id.status1
                2-> R.id.status2
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
        snb.setCustomContentView(notificationLayout)
        // Don't know why, but without the delay the notification is not always updated
        Timer().schedule(250) {
            nm.notify(STATUS_NOTIFICATION_ID, snb.build())
        }
    }

    private fun toast(message: String, length: Int = Toast.LENGTH_SHORT) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@BaresipService.applicationContext, message, length).show()
        }
    }

    private fun startRinging() {
        am.mode = AudioManager.MODE_RINGTONE
        rt!!.isLooping = true
        rt!!.play()
        if (shouldVibrate()) {
            vbTimer = Timer()
            vbTimer!!.schedule(object : TimerTask() {
                override fun run() {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            500,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                }
            }, 500L, 2000L)
        }
    }


    private fun shouldStartRinging(channelId: String): Boolean {
        val currentFilter = nm.currentInterruptionFilter
        val dndAllowsRinging = currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
        if (dndAllowsRinging)
            return true
        val channel = nm.getNotificationChannel(channelId)
        return channel != null && channel.canBypassDnd()
    }

    private fun shouldVibrate(): Boolean {
        return if (am.ringerMode != RINGER_MODE_SILENT)
            if (am.ringerMode == AudioManager.RINGER_MODE_VIBRATE)
                true
            else
                if (am.getStreamVolume(AudioManager.STREAM_RING) != 0)
                    @Suppress("DEPRECATION")
                    Settings.System.getInt(contentResolver, Settings.System.VIBRATE_WHEN_RINGING, 0) != 0
                else
                    false
        else
            false
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
            val resourceId = applicationContext.resources.getIdentifier(
                name,
                "raw",
                applicationContext.packageName)
            if (resourceId != 0) {
                mediaPlayer = MediaPlayer.create(this, resourceId)
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            } else {
                Log.e(TAG, "Ringback tone $name.wav not found")
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun playBusy() {
        if (mediaPlayer == null ) {
            val name = "busy_$toneCountry"
            val resourceId = applicationContext.resources.getIdentifier(
                name,
                "raw",
                applicationContext.packageName)
            if (resourceId != 0) {
                mediaPlayer = MediaPlayer.create(this, resourceId)
                mediaPlayer?.setOnCompletionListener {
                    stopMediaPlayer()
                    if (!Call.inCall()) {
                        resetCallVolume()
                        abandonAudioFocus(applicationContext)
                        proximitySensing(false)
                    }
                }
                mediaPlayer?.start()
            } else {
                Log.e(TAG, "Busy tone $name.wav not found")
            }
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
        if (callVolume != 0)
            for (streamType in listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_VOICE_CALL)) {
                origVolume[streamType] = am.getStreamVolume(streamType)
                val maxVolume = am.getStreamMaxVolume(streamType)
                am.setStreamVolume(streamType, (callVolume * 0.1 * maxVolume).roundToInt(), 0)
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

        /* for (n in allNetworks)
            Log.d(TAG, "NETWORK $n with caps ${cm.getNetworkCapabilities(n)} and props " +
                    "${cm.getLinkProperties(n)} is active ${isNetworkActive(n)}") */

        updateDnsServers()

        val addresses = linkAddresses()

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
        Log.d(TAG, "Added/Removed/Old/New Active = $added/$removed/$activeNetwork/$active")

        if (added > 0 || removed > 0 || active != activeNetwork) {
            linkAddresses = addresses
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
        val addresses = mutableMapOf<String, String>()
        for (n in allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)) {
                val props = cm.getLinkProperties(n) ?: continue
                for (la in props.linkAddresses)
                    if (la.scope == OsConstants.RT_SCOPE_UNIVERSE &&
                            props.interfaceName != null && la.address.hostAddress != null &&
                            afMatch(la.address.hostAddress!!))
                        addresses[la.address.hostAddress!!] = props.interfaceName!!
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

    private fun updateDnsServers() {
        if (isServiceRunning && !dynDns)
            return
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
        if (servers != dnsServers) {
            if (isServiceRunning && Config.updateDnsServers(servers) != 0) {
                Log.w(TAG, "Failed to update DNS servers '${servers}'")
            } else {
                // Log.d(TAG, "Updated DNS servers: '${servers}'")
                dnsServers = servers
            }
        }
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

    private fun cleanService() {
        if (!isServiceClean) {
            if (btAdapter != null) this.unregisterReceiver(bluetoothReceiver)
            this.unregisterReceiver(hotSpotReceiver)
            stopRinging()
            stopMediaPlayer()
            abandonAudioFocus(applicationContext)
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
            if (this::androidContactsObserver.isInitialized)
                contentResolver.unregisterContentObserver(androidContactsObserver)
            isServiceClean = true
        }
    }

    @Suppress("unused")
    private external fun baresipStart(
        path: String,
        addresses: String,
        logLevel: Int,
        software: String
    )

    @Suppress("unused")
    external fun baresipStop(force: Boolean)

    @SuppressLint("MutableCollectionMutableState")
    companion object {

        var isServiceRunning = false
        var isStartReceived = false
        var isConfigInitialized = false
        var libraryLoaded = false
        var supportedCameras = false
        var cameraFront = true
        var callVolume = 0
        var speakerPhone = false
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
        var videoSize = Size(0, 0)

        val uas = mutableStateOf(emptyList<UserAgent>())
        val uasStatus = mutableStateOf(emptyMap<String, Int>())
        var contacts by mutableStateOf(mutableListOf<Contact>())
        val baresipContacts = mutableStateOf(emptyList<Contact.BaresipContact>())
        val androidContacts = mutableStateOf(emptyList<Contact.AndroidContact>())
        val contactNames = mutableStateOf(emptyList<String>())
        val darkTheme = mutableStateOf(false)
        var messages by mutableStateOf(emptyList<Message>())
        val messageUpdate = MutableLiveData<Long>()

        val calls = ArrayList<Call>()
        var callHistory = ArrayList<CallHistoryNew>()
        val registrationUpdate = MutableLiveData<Long>()
        var contactsMode = "baresip"
        val activities = mutableListOf<String>()
        var addressFamily = ""
        var dnsServers = listOf<InetAddress>()
        val serviceEvent = MutableLiveData<Event<Long>>()
        val serviceEvents = mutableListOf<ServiceEvent>()
        // <aor, password> of those accounts that have auth username without auth password
        val aorPasswords = mutableMapOf<String, String>()
        var audioFocusRequest: AudioFocusRequestCompat? = null
        var aecAvailable = false
        private var aec: AcousticEchoCanceler? = null
        var agcAvailable = false
        var rt: Ringtone? = null

        var colorblind = false
        val circleGreen = mapOf(true to R.drawable.circle_green_blind,
            false to R.drawable.circle_green)
        val circleYellow = mapOf(true to R.drawable.circle_yellow_blind,
            false to R.drawable.circle_yellow)
        val circleRed = mapOf(true to R.drawable.circle_red_blind,
            false to R.drawable.circle_red)

        private var agc: AutomaticGainControl? = null
        private val nsAvailable = NoiseSuppressor.isAvailable()
        private var ns: NoiseSuppressor? = null
        private var btAdapter: BluetoothAdapter? = null
        private var recorderSessionId = 0

        fun postServiceEvent(event: ServiceEvent) {
            serviceEvents.add(event)
            if (serviceEvents.size == 1) {
                Log.d(TAG, "Posted service event ${event.event} at ${event.timeStamp}")
                serviceEvent.postValue(Event(event.timeStamp))
            } else {
                Log.d(TAG, "Added service event ${event.event}")
            }
        }
        
        fun requestAudioFocus(ctx: Context): Boolean  {
            Log.d(TAG, "Requesting audio focus")
            if (audioFocusRequest != null) {
                Log.d(TAG, "Already focused")
                return true
            }
            val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
            val attributes = AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()
            if (AudioManagerCompat.requestAudioFocus(am, audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "requestAudioFocus granted")
                if (isBluetoothHeadsetConnected(ctx))
                    startBluetoothSco(ctx, 250L, 3)
            } else {
                Log.w(TAG, "requestAudioFocus denied")
                audioFocusRequest = null
            }
            return audioFocusRequest != null
        }

        fun abandonAudioFocus(ctx: Context) {
            val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
            if (audioFocusRequest != null) {
                Log.d(TAG, "Abandoning audio focus")
                if (AudioManagerCompat.abandonAudioFocusRequest(am, audioFocusRequest!!) ==
                        AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    audioFocusRequest = null
                    if (isBluetoothHeadsetConnected(ctx))
                        stopBluetoothSco(ctx)
                } else {
                    Log.e(TAG, "Failed to abandon audio focus")
                }
            }
            am.mode = MODE_NORMAL
        }

        private fun isBluetoothHeadsetConnected(ctx: Context): Boolean {
            if (VERSION.SDK_INT >= 31 &&
                    ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) ==
                        PackageManager.PERMISSION_DENIED)
                return false
            return btAdapter != null && btAdapter!!.isEnabled &&
                    btAdapter!!.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothAdapter.STATE_CONNECTED
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
                } else {
                    Utils.setCommunicationDevice(am, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
                }
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

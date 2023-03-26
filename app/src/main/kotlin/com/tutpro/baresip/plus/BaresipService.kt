package com.tutpro.baresip.plus

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.media.*
import android.media.AudioManager.MODE_IN_COMMUNICATION
import android.media.AudioManager.MODE_NORMAL
import android.media.AudioManager.RINGER_MODE_SILENT
import android.net.*
import android.net.wifi.WifiManager
import android.os.*
import android.os.Build.VERSION
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.roundToInt

class BaresipService: Service() {

    internal lateinit var intent: Intent
    internal lateinit var am: AudioManager
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
    private lateinit var contentObserver: ContentObserver
    private lateinit var stopState: String
    private lateinit var quitTimer: CountDownTimer

    private var rt: Ringtone? = null
    private var rtTimer: Timer? = null
    private var vbTimer: Timer? = null
    private var origVolume = mutableMapOf<Int, Int>()
    private var linkAddresses = mutableMapOf<String, String>()
    private var activeNetwork: Network? = null
    private var allNetworks = mutableSetOf<Network>()
    private var hotSpotIsEnabled = false
    private var hotSpotAddresses = mapOf<String, String>()
    private var mediaPlayer: MediaPlayer? = null
    private var contentObserverRegistered = false
    private var isServiceClean = false

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "BaresipService onCreate")

        intent = Intent("com.tutpro.baresip.plus.EVENT")
        intent.setPackage("com.tutpro.baresip.plus")

        filesPath = filesDir.absolutePath
        pName = packageName

        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val ntUri = RingtoneManager.getActualDefaultRingtoneUri(applicationContext,
                RingtoneManager.TYPE_NOTIFICATION)
        nt = RingtoneManager.getRingtone(applicationContext, ntUri)

        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        snb = NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)

        pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        vibrator = if (VERSION.SDK_INT >= 31) {
            val vibratorManager = applicationContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            applicationContext.getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
        }

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
                        if (network !in allNetworks)
                            allNetworks.add(network)
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
                                    var reset = false
                                    for ((k, v) in hotSpotAddresses)
                                        if (afMatch(k))
                                            if (Api.net_add_address_ifname(k, v) != 0)
                                                Log.e(TAG, "Failed to add $v address $k")
                                            else
                                                reset = true
                                    if (reset)
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

        tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager

        btm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btm.adapter

        proximityWakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "com.tutpro.baresip.plus:proximity_wakelog")

        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Baresip+")
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
                                    startBluetoothSco(am, 1000L, 3)
                            }
                            BluetoothHeadset.STATE_DISCONNECTED -> {
                                Log.d(TAG, "Bluetooth headset is disconnected")
                                if (audioFocusRequest != null)
                                    stopBluetoothSco(am)
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

        contentObserver = object : ContentObserver(null) {
            override fun onChange(self: Boolean) {
                Log.d(TAG, "Contacts change")
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
                        Log.e(TAG, "Failed to create directory: $e")
                    }
                }
                for (a in assets) {
                    file = File("${filesPath}/$a")
                    if (!file.exists()) {
                        Log.i(TAG, "Copying asset '$a'")
                        Utils.copyAssetToFile(applicationContext, a, "$filesPath/$a")
                    } else {
                        Log.d(TAG, "Asset '$a' already copied")
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
                    registerContentObserver()
                }
                Contact.contactsUpdate()

                val history = NewCallHistory.get()
                if (history.isEmpty()) {
                    CallHistory.restore()
                } else {
                    for (old in history) {
                        val new = CallHistory(old.aor, old.peerUri, old.direction)
                        new.stopTime = old.stopTime
                        new.startTime = old.startTime
                        callHistory.add(new)
                    }
                    CallHistory.save()
                }

                Message.restore()

                linkAddresses = linkAddresses()
                var addresses = ""
                for (la in linkAddresses)
                    addresses = "$addresses;${la.key};${la.value}"

                Log.i(TAG, "Link addresses: $addresses")

                Thread {
                    baresipStart(filesPath, addresses.removePrefix(";"), logLevel)
                }.start()

                isServiceRunning = true

                showStatusNotification()

                if (linkAddresses.isEmpty()) {
                    val newIntent = Intent(this, MainActivity::class.java)
                    newIntent.flags =
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NEW_TASK
                    newIntent.putExtra("action", "no network")
                    startActivity(newIntent)
                }

            }

            "Start Content Observer" -> {
                registerContentObserver()
            }

            "Stop Content Observer" -> {
                unRegisterContentObserver()
            }

            "Call Answer" -> {
                val uap = intent!!.getLongExtra("uap", 0L)
                val callp = intent.getLongExtra("callp", 0L)
                stopRinging()
                stopMediaPlayer()
                //requestAudioFocus(applicationContext, am, AudioAttributes.CONTENT_TYPE_SPEECH)
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
                else
                    ChatsActivity.saveUaMessage(
                        ua.account.aor,
                        intent.getStringExtra("time")!!.toLong()
                    )
                nm.cancel(MESSAGE_NOTIFICATION_ID)
            }

            "Message Delete" -> {
                val uap = intent!!.getLongExtra("uap", 0L)
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

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy at Baresip Service")
        cleanService()
        if (isServiceRunning)
            sendBroadcast(Intent("com.tutpro.baresip.plus.Restart"))
    }

    @Keep
    fun uaEvent(event: String, uap: Long, callp: Long) {

        if (!isServiceRunning) return

        val ev = event.split(",")

        if (ev[0] == "create") {
            val ua = UserAgent(uap)
            ua.status = if (ua.account.regint == 0)
                R.drawable.dot_white
            else
                R.drawable.dot_yellow
            uas.add(ua)

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
                            Log.d(TAG, "Saved snapshot $fileName")
                        } catch (e: IOException) {
                            Log.d(TAG, "Failed to save snapshot $fileName")
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
                !setOf("call incoming", "call rejected", "call closed").contains(ev[0])) {
            Log.w(TAG, "uaEvent $event did not find call $callp")
            return
        }

        var newEvent: String? = null

	    for (accountIndex in uas.indices) {
            if (uas[accountIndex].account.aor == aor) {
                when (ev[0]) {
                    "registering", "unregistering" -> {
                        ua.status = R.drawable.dot_yellow
                        updateStatusNotification()
                        if (isMainVisible)
                            registrationUpdate.postValue(System.currentTimeMillis())
                        return
                    }
                    "registered" -> {
                        ua.status = if (Api.account_regint(ua.account.accp) == 0)
                            R.drawable.dot_white
                        else
                            R.drawable.dot_green
                        updateStatusNotification()
                        if (isMainVisible)
                            registrationUpdate.postValue(System.currentTimeMillis())
                        return
                    }
                    "registering failed" -> {
                        ua.status = if (Api.account_regint(ua.account.accp) == 0)
                            R.drawable.dot_white
                        else
                            R.drawable.dot_red
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
                        proximitySensing(true)
                        return
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
                    "call incoming" -> {
                        val peerUri = ev[1]
                        if (!Utils.checkPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO))) {
                            toast(getString(R.string.no_calls))
                            if (ua.account.callHistory) {
                                CallHistory.add(CallHistory(aor, peerUri, "in"))
                                CallHistory.save()
                                ua.account.missedCalls = true
                            }
                            if (!isMainVisible)
                                return
                            newEvent = "call rejected"
                        } else {
                            Log.d(TAG, "Incoming call $uap/$callp/$peerUri")
                            Call(callp, ua, peerUri, "in", "incoming", Utils.dtmfWatcher(callp)).add()
                            if (ua.account.answerMode == Api.ANSWERMODE_MANUAL) {
                                Log.d(TAG, "CurrentInterruptionFilter ${nm.currentInterruptionFilter}")
                                if (nm.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL)
                                    startRinging()
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
                            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                            intent.putExtra("action", "call show")
                                .putExtra("callp", callp)
                            val pi = PendingIntent.getActivity(applicationContext, CALL_REQ_CODE, intent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                            val nb = NotificationCompat.Builder(this,
                                if (shouldVibrate()) MEDIUM_CHANNEL_ID else HIGH_CHANNEL_ID)
                            val caller = Utils.friendlyUri(this, peerUri, ua.account)
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
                            if (VERSION.SDK_INT < 26) {
                                @Suppress("DEPRECATION")
                                nb.setVibrate(LongArray(0))
                                    .priority = Notification.PRIORITY_HIGH
                            }
                            val answerIntent = Intent(applicationContext, MainActivity::class.java)
                            answerIntent.putExtra("action", "call answer")
                                .putExtra("callp", callp)
                            val api = PendingIntent.getActivity(applicationContext, ANSWER_REQ_CODE, answerIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                            val rejectIntent = Intent(this, BaresipService::class.java)
                            rejectIntent.action = "Call Reject"
                            rejectIntent.putExtra("callp", callp)
                            val rpi = PendingIntent.getService(this, REJECT_REQ_CODE, rejectIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                            nb.addAction(R.drawable.ic_stat_call,
                                getActionText(R.string.answer, R.color.colorGreen), api)
                            nb.addAction(R.drawable.ic_stat_call_end,
                                getActionText(R.string.reject, R.color.colorRed), rpi)
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
                                        (rdir != Api.SDP_INACTIVE) && (ldir != rdir))) {
                            postServiceEvent(ServiceEvent("call video request",
                                    arrayListOf(uap, callp, rdir), System.nanoTime()))
                            return
                        }
                        newEvent = "call update"
                    }
                    "call rejected" -> {
                        playUnInterrupted(R.raw.callwaiting, 1)
                        if (ua.account.callHistory) {
                            CallHistory.add(CallHistory(aor, ev[1], "in"))
                            CallHistory.save()
                            ua.account.missedCalls = true
                            if (!isMainVisible)
                                return
                            newEvent = "call rejected"
                        } else {
                            return
                        }
                    }
                    "call answered" -> {
                        stopMediaPlayer()
                        if (call!!.status == "incoming")
                            call.status = "answered"
                        else
                            return
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
                            val intent = Intent(applicationContext, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            intent.putExtra("action", "transfer show")
                                .putExtra("callp", callp).putExtra("uri", ev[1])
                            val pi = PendingIntent.getActivity(applicationContext, TRANSFER_REQ_CODE, intent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
                            val target = Utils.friendlyUri(this, ev[1], ua.account)
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
                            val api = PendingIntent.getActivity(applicationContext, ACCEPT_REQ_CODE,
                                    acceptIntent, PendingIntent.FLAG_IMMUTABLE or
                                            PendingIntent.FLAG_UPDATE_CURRENT)
                            val denyIntent = Intent(this, BaresipService::class.java)
                            denyIntent.action = "Transfer Deny"
                            denyIntent.putExtra("callp", callp)
                            val dpi = PendingIntent.getService(this, DENY_REQ_CODE, denyIntent,
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                            nb.addAction(R.drawable.ic_stat_call, getString(R.string.accept), api)
                            nb.addAction(R.drawable.ic_stat_call_end, getString(R.string.deny), dpi)
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
                        Log.d(TAG, "AoR $aor call $callp is closed")
                        if (call != null) {
                            nm.cancel(CALL_NOTIFICATION_ID)
                            stopRinging()
                            stopMediaPlayer()
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
                            if (!Call.inCall()) {
                                resetCallVolume()
                                abandonAudioFocus(am)
                                Utils.clearCommunicationDevice(am)
                                proximitySensing(false)
                            }
                            val missed = call.startTime == null && call.dir == "in" && !call.rejected
                            if (ua.account.callHistory) {
                                val history = CallHistory(aor, call.peerUri, call.dir)
                                history.startTime = call.startTime
                                history.stopTime = GregorianCalendar()
                                if (call.startTime != null && call.dumpfiles[0] != "")
                                    history.recording = call.dumpfiles
                                CallHistory.add(history)
                                CallHistory.save()
                                ua.account.missedCalls = ua.account.missedCalls || missed
                            }
                            if (!Utils.isVisible()) {
                                if (missed) {
                                    val caller = Utils.friendlyUri(this, call.peerUri, ua.account)
                                    val intent = Intent(applicationContext, MainActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                    intent.putExtra("action", "call missed")
                                            .putExtra("uap", uap)
                                    val pi = PendingIntent.getActivity(applicationContext, CALL_REQ_CODE, intent,
                                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
                                    if (VERSION.SDK_INT < 26) {
                                        @Suppress("DEPRECATION")
                                        nb.setVibrate(LongArray(0))
                                                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                                                .priority = Notification.PRIORITY_HIGH
                                    }
                                    nm.notify(CALL_MISSED_NOTIFICATION_ID, nb.build())
                                }
                                return
                            }
                        }
                        val reason = ev[1].trim()
                        if ((reason != "") && (ua.calls().isEmpty())) {
                            if (reason[0].isDigit())
                                toast("${getString(R.string.call_failed)}: $reason")
                            else
                                toast("${getString(R.string.call_closed)}: ${Api.call_peer_uri(callp)}: $reason")
                        }
                    }
                }
            }
        }

        postServiceEvent(ServiceEvent(newEvent ?: event, arrayListOf(uap, callp),
                System.nanoTime()))

    }

    private fun postServiceEvent(event: ServiceEvent) {
        serviceEvents.add(event)
        if (serviceEvents.size == 1) {
            Log.d(TAG, "Posted service event ${event.event} at ${event.timeStamp}")
            serviceEvent.postValue(Event(event.timeStamp))
        } else {
            Log.d(TAG, "Added service event ${event.event}")
        }
    }

    @Keep
    fun messageEvent(uap: Long, peerUri: String, msg: ByteArray) {
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
        val timeStamp = System.currentTimeMillis()
        val timeStampString = timeStamp.toString()
        Log.d(TAG, "Message event for $uap from $peerUri at $timeStampString")
        Message(ua.account.aor, peerUri, text, timeStamp,
                R.drawable.arrow_down_green, 0, "", true).add()
        Message.save()
        ua.account.unreadMessages = true
        if (!Utils.isVisible()) {
            val intent = Intent(applicationContext, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("action", "message show").putExtra("uap", uap)
                    .putExtra("peer", peerUri)
            val pi = PendingIntent.getActivity(applicationContext, MESSAGE_REQ_CODE, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val nb = NotificationCompat.Builder(this, HIGH_CHANNEL_ID)
            val sender = Utils.friendlyUri(this, peerUri, ua.account)
            nb.setSmallIcon(R.drawable.ic_stat_message)
                    .setColor(ContextCompat.getColor(this, R.color.colorBaresip))
                    .setContentIntent(pi)
                    .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                    .setAutoCancel(true)
                    .setContentTitle(getString(R.string.message_from) + " " + sender)
                    .setContentText(text)
            if (VERSION.SDK_INT < 26)
                @Suppress("DEPRECATION")
                nb.setVibrate(LongArray(0))
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .priority = Notification.PRIORITY_HIGH
            val replyIntent = Intent(applicationContext, MainActivity::class.java)
            replyIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            replyIntent.putExtra("action", "message reply")
                .putExtra("uap", uap).putExtra("peer", peerUri)
            val rpi = PendingIntent.getActivity(applicationContext, REPLY_REQ_CODE, replyIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val saveIntent = Intent(this, BaresipService::class.java)
            saveIntent.action = "Message Save"
            saveIntent.putExtra("uap", uap)
                    .putExtra("time", timeStampString)
            val spi = PendingIntent.getService(this, SAVE_REQ_CODE, saveIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val deleteIntent = Intent(this, BaresipService::class.java)
            deleteIntent.action = "Message Delete"
            deleteIntent.putExtra("uap", uap)
                    .putExtra("time", timeStampString)
            val dpi = PendingIntent.getService(this, DELETE_REQ_CODE, deleteIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nb.addAction(R.drawable.ic_stat_reply, "Reply", rpi)
            nb.addAction(R.drawable.ic_stat_save, "Save", spi)
            nb.addAction(R.drawable.ic_stat_delete, "Delete", dpi)
            nm.notify(MESSAGE_NOTIFICATION_ID, nb.build())
            return
        }
        if (nm.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL)
            nt.play()
        postServiceEvent(ServiceEvent("message show", arrayListOf(uap, peerUri), System.nanoTime()))
    }

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

    @Keep
    @Suppress("UNUSED")
    fun messageResponse(responseCode: Int, responseReason: String, time: String) {
        Log.d(TAG, "Message response '$responseCode $responseReason' at $time")
        val timeStamp = time.toLong()
        for (m in messages.reversed())
            if (m.timeStamp == timeStamp) {
                if (responseCode < 300) {
                    m.direction = R.drawable.arrow_up_green
                } else {
                    m.direction = R.drawable.arrow_up_red
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

    /*
    @Keep
    fun checkVideo(uap: String, callp: String, reqDir: Int): Int {
        if (!isServiceRunning) return -1
        val ua = UserAgent.ofUap(uap)
        if (ua == null) {
            Log.w(LOG_TAG, "checkVideo did not find ua $uap")
            return -1
        }
        val call = Call.ofCallp(callp)
        if (call == null) {
            Log.d(LOG_TAG, "checkVideo did not find call $callp")
            return -1
        }
        Log.d(LOG_TAG, "checkVideo reqDir = $reqDir, videoAllowed = ${call.videoAllowed}," +
                " call.video = ${call.video}")
        if ((reqDir == Api.SDP_INACTIVE) || (!cameraAvailable && (reqDir == Api.SDP_SENDONLY))) {
            call.video = Api.SDP_INACTIVE
            return Api.SDP_INACTIVE
        }
        if ((call.video == Api.SDP_INACTIVE) && !call.videoAllowed) {
            serviceEvent.postValue(Event(ServiceEvent("call video request", arrayListOf(uap, peer),
                timeStamp)))
            return Api.SDP_INACTIVE
        }
        call.videoAllowed = false
        if (cameraAvailable)
            call.video = reqDir
        else
            call.video = Api.SDP_RECVONLY
        return call.video
    }*/

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
            val mediumChannel = NotificationChannel(MEDIUM_CHANNEL_ID, "Medium",
                NotificationManager.IMPORTANCE_HIGH)
            highChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            highChannel.enableVibration(false)
            nm.createNotificationChannel(mediumChannel)
        }
    }

    private fun showStatusNotification() {
        val intent = Intent(applicationContext, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
        val pi = PendingIntent.getActivity(applicationContext, STATUS_REQ_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE)
        snb.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(pi)
                .setOngoing(true)
                .setContent(RemoteViews(packageName,
                        if (VERSION.SDK_INT >= 33)
                            R.layout.status_notification_33
                        else
                            R.layout.status_notification)
                )
        startForeground(STATUS_NOTIFICATION_ID, snb.build())
    }

    private fun updateStatusNotification() {
        val contentView = RemoteViews(packageName,
                if (VERSION.SDK_INT >= 33)
                    R.layout.status_notification_33
                else
                    R.layout.status_notification
        )
        for (i: Int in 0..3) {
            val resId = when (i) {
                0-> R.id.status0
                1-> R.id.status1
                2-> R.id.status2
                else -> R.id.status3
            }
            if (i < uas.size) {
                contentView.setImageViewResource(resId, uas[i].status)
                contentView.setViewVisibility(resId, View.VISIBLE)
            } else {
                contentView.setViewVisibility(resId, View.INVISIBLE)
            }
        }
        if (uas.size > 4)
            contentView.setViewVisibility(R.id.etc, View.VISIBLE)
        else
            contentView.setViewVisibility(R.id.etc, View.INVISIBLE)
        snb.setContent(contentView)
        // Don't know why, but without the delay the notification is not always updated
        Timer().schedule(250) {
            nm.notify(STATUS_NOTIFICATION_ID, snb.build())
        }
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@BaresipService.applicationContext, message, Toast.LENGTH_LONG).show()
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

    private fun startRinging() {
        requestAudioFocus(applicationContext, am, AudioAttributes.CONTENT_TYPE_MUSIC)
        if (rt == null) {
            val rtUri = RingtoneManager.getActualDefaultRingtoneUri(
                applicationContext,
                RingtoneManager.TYPE_RINGTONE
            )
            rt = RingtoneManager.getRingtone(applicationContext, rtUri)
        }
        if (VERSION.SDK_INT >= 28) {
            rt!!.isLooping = true
            rt!!.play()
        } else {
            rt!!.play()
            rtTimer = Timer()
            rtTimer!!.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (rt != null && !rt!!.isPlaying) {
                        rt!!.play()
                    }
                }
            }, 1000, 1000)
        }
        if (shouldVibrate()) {
            vbTimer = Timer()
            vbTimer!!.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (VERSION.SDK_INT < 26) {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(500)
                    } else {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                500,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    }
                }
            }, 500L, 2000L)
        }
    }

    private fun shouldVibrate(): Boolean {
        return if (am.ringerMode != RINGER_MODE_SILENT) {
            if (am.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                true
            } else {
                if (am.getStreamVolume(AudioManager.STREAM_RING) != 0) {
                    @Suppress("DEPRECATION")
                    Settings.System.getInt(contentResolver, Settings.System.VIBRATE_WHEN_RINGING, 0) == 1
                } else {
                    false
                }
            }
        } else {
            false
        }
    }

    private fun stopRinging() {
        if (VERSION.SDK_INT < 28 && rtTimer != null) {
            rtTimer!!.cancel()
            rtTimer = null
        }
        if (rt != null) {
            rt!!.stop()
            rt = null
        }
        if (vbTimer != null) {
            vbTimer!!.cancel()
            vbTimer = null
        }
    }

    private fun playRingBack() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.ringback)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        }
    }

    @Suppress("UNUSED")
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
                val maxVolume = am.getStreamMaxVolume(streamType)
                am.setStreamVolume(streamType, (callVolume * 0.1 * maxVolume).roundToInt(), 0)
                Log.d(TAG, "Orig/new/max $streamType volume is " +
                        "${origVolume[streamType]}/${am.getStreamVolume(streamType)}/$maxVolume")
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
        Log.d(TAG, "Added/Removed/Active = $added/$removed/$active")

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
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND) ||
                VERSION.SDK_INT < 28) {
                val props = cm.getLinkProperties(n) ?: continue
                for (la in props.linkAddresses)
                    if (la.scope == android.system.OsConstants.RT_SCOPE_UNIVERSE &&
                            props.interfaceName != null && la.address.hostAddress != null &&
                            afMatch(la.address.hostAddress!!))
                        addresses[la.address.hostAddress!!] = props.interfaceName!!
            }
        }
        if (hotSpotIsEnabled) {
            hotSpotAddresses = Utils.hotSpotAddresses()
            Log.d(TAG, "HotSpot addresses $hotSpotAddresses")
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
        if (cm.activeNetwork != null) {
            val linkProps = cm.getLinkProperties(cm.activeNetwork)
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

    private fun registerContentObserver() {
        if (!contentObserverRegistered)
            try {
                contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                        true, contentObserver)
                contentObserverRegistered = true
            } catch (e: SecurityException) {
                Log.i(TAG, "No Contacts permission")
            }
    }

    private fun unRegisterContentObserver() {
        if (contentObserverRegistered) {
            contentResolver.unregisterContentObserver(contentObserver)
            contentObserverRegistered = false
        }
    }

    private fun cleanService() {
        if (!isServiceClean) {
            this.unregisterReceiver(bluetoothReceiver)
            this.unregisterReceiver(hotSpotReceiver)
            stopRinging()
            abandonAudioFocus(am)
            stopMediaPlayer()
            uas.clear()
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
            if (this::contentObserver.isInitialized)
                contentResolver.unregisterContentObserver(contentObserver)
            isServiceClean = true
        }
    }

    private external fun baresipStart(path: String, addresses: String, logLevel: Int)
    private external fun baresipStop(force: Boolean)

    companion object {

        var isServiceRunning = false
        var isConfigInitialized = false
        var isAudioFocused = false
        var libraryLoaded = false
        var supportedCameras = false
        var cameraFront = true
        var callVolume = 0
        var dynDns = false
        var filesPath = ""
        var pName = ""
        var logLevel = 2
        var sipTrace = false
        var callActionUri = ""
        var isMainVisible = false
        var isMicMuted = false
        var isRecOn = false

        val uas = ArrayList<UserAgent>()
        val calls = ArrayList<Call>()
        var callHistory = ArrayList<CallHistory>()
        var messages = ArrayList<Message>()
        val messageUpdate = MutableLiveData<Long>()
        val contactUpdate = MutableLiveData<Long>()
        val registrationUpdate = MutableLiveData<Long>()
        val baresipContacts = ArrayList<Contact.BaresipContact>()
        val androidContacts = ArrayList<Contact.AndroidContact>()
        val contacts = ArrayList<Contact>()
        val contactNames = ArrayList<String>()
        var contactsMode = "baresip"
        val chatTexts: MutableMap<String, String> = mutableMapOf()
        val activities = mutableListOf<String>()
        var addressFamily = ""
        var dnsServers = listOf<InetAddress>()
        val serviceEvent = MutableLiveData<Event<Long>>()
        val serviceEvents = mutableListOf<ServiceEvent>()
        // <aor, password> of those accounts that have auth username without auth password
        val aorPasswords = mutableMapOf<String, String>()

        private var audioFocusRequest: AudioFocusRequestCompat? = null
        private var btAdapter: BluetoothAdapter? = null

        fun requestAudioFocus(ctx: Context, am: AudioManager, type: Int): Boolean  {
            Log.d(TAG, "Requesting audio focus of type $type")
            if (audioFocusRequest != null) {
                if (audioFocusRequest!!.audioAttributesCompat.contentType == type) {
                    Log.d(TAG, "Already focused")
                    am.mode = MODE_IN_COMMUNICATION
                    return true
                } else {
                    abandonAudioFocus(am)
                }
            }
            val attributes = AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_VOICE_COMMUNICATION)
                .setContentType(type)
                .build()
            audioFocusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()
            if (AudioManagerCompat.requestAudioFocus(am, audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "Audio focus granted")
                isAudioFocused = true
                if (isBluetoothHeadsetConnected(ctx) /* && !am.isBluetoothScoOn */) {
                    Log.d(TAG, "Starting Bluetooth SCO")
                    startBluetoothSco(am, 10L, 1)
                }
                am.mode = MODE_IN_COMMUNICATION
                //if (type == AudioAttributes.CONTENT_TYPE_SPEECH || isBluetoothHeadsetConnected(ctx))
                    //am.mode = AudioManager.MODE_IN_COMMUNICATION
            } else {
                Log.i(TAG, "Audio focus denied")
                audioFocusRequest = null
                isAudioFocused = false
            }
            return isAudioFocused
        }

        private fun abandonAudioFocus(am: AudioManager) {
            if (audioFocusRequest != null) {
                Log.d(TAG, "Abandoning audio focus")
                AudioManagerCompat.abandonAudioFocusRequest(am, audioFocusRequest!!)
                audioFocusRequest = null
                isAudioFocused = false
                stopBluetoothSco(am)
                Utils.clearCommunicationDevice(am)
                am.mode = MODE_NORMAL
            }
        }

        @SuppressLint("MissingPermission")
        private fun isBluetoothHeadsetConnected(ctx: Context): Boolean {
            return if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED)
                btAdapter != null && btAdapter!!.isEnabled &&
                        btAdapter!!.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothAdapter.STATE_CONNECTED
            else
                true
        }

        private fun isBluetoothScoOn(am: AudioManager): Boolean {
            return if (VERSION.SDK_INT < 31)
                am.isBluetoothScoOn
            else
                if (am.communicationDevice != null)
                    am.communicationDevice!!.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                else
                    false
        }

        private fun startBluetoothSco(am: AudioManager, delay: Long, count: Int) {
            Log.d(TAG, "Starting Bluetooth SCO at count $count")
            Handler(Looper.getMainLooper()).postDelayed({
                if (VERSION.SDK_INT < 31) {
                    am.startBluetoothSco()
                } else {
                    Utils.setCommunicationDevice(am, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
                }
                if (!isBluetoothScoOn(am) && count > 1)
                    startBluetoothSco(am, delay, count - 1)
            }, delay)
        }

        private fun stopBluetoothSco(am: AudioManager) {
            Log.d(TAG, "Stopping Bluetooth SCO")
            Handler(Looper.getMainLooper()).postDelayed({
                if (VERSION.SDK_INT < 31)
                    am.stopBluetoothSco()
                else
                    am.clearCommunicationDevice()
            }, 100)
        }

    }

    init {
        if (!libraryLoaded) {
            Log.d(TAG, "Loading baresip library")
            System.loadLibrary("baresip")
            libraryLoaded = true
        }
    }
}

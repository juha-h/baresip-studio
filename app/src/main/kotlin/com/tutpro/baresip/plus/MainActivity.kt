package com.tutpro.baresip.plus

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.*
import android.content.Intent.ACTION_CALL
import android.content.pm.PackageManager
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.media.AudioManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.content.Intent
import android.content.BroadcastReceiver
import android.media.MediaActionSound
import android.os.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.tutpro.baresip.plus.Utils.showSnackBar
import com.tutpro.baresip.plus.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.system.exitProcess
import android.content.IntentFilter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var defaultLayout: RelativeLayout
    private lateinit var videoLayout: RelativeLayout
    private lateinit var videoView: VideoView
    private lateinit var callTitle: TextView
    private lateinit var callUri: AutoCompleteTextView
    private lateinit var securityButton: ImageButton
    private lateinit var callButton: ImageButton
    private lateinit var callVideoButton: ImageButton
    private lateinit var hangupButton: ImageButton
    private lateinit var answerButton: ImageButton
    private lateinit var answerVideoButton: ImageButton
    private lateinit var rejectButton: ImageButton
    private lateinit var callControl: RelativeLayout
    private lateinit var holdButton: ImageButton
    private lateinit var transferButton: ImageButton
    private lateinit var videoButton: ImageButton
    private lateinit var voicemailButton: ImageButton
    private lateinit var contactsButton: ImageButton
    private lateinit var messagesButton: ImageButton
    private lateinit var callsButton: ImageButton
    private lateinit var dialpadButton: ImageButton
    private lateinit var dtmf: EditText
    private var dtmfWatcher: TextWatcher? = null
    private lateinit var infoButton: ImageButton
    private lateinit var onHoldNotice: TextView
    private lateinit var uaAdapter: UaSpinnerAdapter
    private lateinit var aorSpinner: Spinner
    private lateinit var imm: InputMethodManager
    private lateinit var nm: NotificationManager
    private lateinit var am: AudioManager
    private lateinit var kgm: KeyguardManager
    private lateinit var serviceEventReceiver: BroadcastReceiver
    private lateinit var screenEventReceiver: BroadcastReceiver
    private lateinit var quitTimer: CountDownTimer
    private lateinit var stopState: String
    private var micIcon: MenuItem? = null
    private var speakerIcon: MenuItem? = null
    private lateinit var speakerButton: ImageButton
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private lateinit var accountsRequest: ActivityResultLauncher<Intent>
    private lateinit var chatRequests: ActivityResultLauncher<Intent>
    private lateinit var configRequest: ActivityResultLauncher<Intent>
    private lateinit var backupRequest: ActivityResultLauncher<Intent>
    private lateinit var restoreRequest: ActivityResultLauncher<Intent>
    private lateinit var contactsRequest: ActivityResultLauncher<Intent>
    private lateinit var callsRequest: ActivityResultLauncher<Intent>

    private var downloadsInputStream: FileInputStream? = null
    private var downloadsOutputStream: FileOutputStream? = null
    private var downloadsInputFile = "baresip+.bs"
    private var downloadsOutputFile = "baresip+.bs"

    private lateinit var baresipService: Intent

    private var restart = false
    private var atStartup = false
    private var alerting = false

    private var resumeUri = ""
    private var resumeUap = ""
    private var resumeCall: Call? = null
    private var resumeAction = ""
    private var firstRun = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        binding = ActivityMainBinding.inflate(layoutInflater)

        val extraAction = intent.getStringExtra("action")
        Log.d(TAG, "MainActivity onCreate ${intent.action}/${intent.data}/$extraAction")

        if (intent?.action == ACTION_CALL && !BaresipService.isServiceRunning)
            BaresipService.callActionUri = URLDecoder.decode(intent.data.toString(), "UTF-8")

        window.addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)

        setContentView(binding.root)

        // Must be done after view has been created
        Utils.setShowWhenLocked(this, true)
        Utils.setTurnScreenOn(this, true)
        Utils.requestDismissKeyguard(this)

        setSupportActionBar(binding.toolbar)

        defaultLayout = binding.defaultLayout
        videoLayout = binding.videoLayout
        videoView = VideoView(applicationContext)
        aorSpinner = binding.aorSpinner
        callTitle = binding.callTitle
        callUri = binding.callUri
        securityButton = binding.securityButton
        callButton = binding.callButton
        callVideoButton = binding.callVideoButton
        hangupButton = binding.hangupButton
        answerButton = binding.answerButton
        answerVideoButton = binding.answerVideoButton
        rejectButton = binding.rejectButton
        callControl = binding.callControl
        holdButton = binding.holdButton
        transferButton = binding.transferButton
        dtmf = binding.dtmf
        infoButton = binding.info
        onHoldNotice = binding.onHoldNotice
        voicemailButton = binding.voicemailButton
        videoButton = binding.videoButton
        contactsButton = binding.contactsButton
        messagesButton = binding.messagesButton
        callsButton = binding.callsButton
        dialpadButton = binding.dialpadButton
        swipeRefresh = binding.swipeRefresh

        BaresipService.supportedCameras = Utils.supportedCameras(applicationContext).isNotEmpty()

        addVideoLayoutViews()

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        am = getSystemService(AUDIO_SERVICE) as AudioManager
        kgm = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        serviceEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handleServiceEvent(intent.getStringExtra("event")!!,
                        intent.getStringArrayListExtra("params")!!)
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(serviceEventReceiver,
                IntentFilter("service event"))

        screenEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Utils.setShowWhenLocked(this@MainActivity, Call.calls().size > 0)
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(screenEventReceiver,
                IntentFilter("screen event"))

        stopState = "initial"
        quitTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                Log.d(TAG, "Seconds remaining: ${millisUntilFinished / 1000}")
            }

            override fun onFinish() {
                when (stopState) {
                    "initial" -> {
                        baresipService.action = "Stop Force"
                        startService(baresipService)
                        stopState = "force"
                        quitTimer.start()
                    }
                    "force" -> {
                        baresipService.action = "Kill"
                        startService(baresipService)
                        finishAndRemoveTask()
                        exitProcess(0)
                    }
                }
            }
        }

        uaAdapter = UaSpinnerAdapter(applicationContext, BaresipService.uas, BaresipService.status)
        aorSpinner.adapter = uaAdapter
        aorSpinner.setSelection(-1)
        aorSpinner.tag = ""
        aorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            // Have to allow NULL view, since sometimes when onItemSelected is called, view is NULL.
            // Haven't found any explanation why this can happen.
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                Log.d(TAG, "aorSpinner selecting $position")
                val acc = UserAgent.uas()[position].account
                aorSpinner.tag = acc.aor
                val ua = UserAgent.uas()[position]
                showCall(ua)
                updateIcons(acc)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                Log.d(TAG, "Nothing selected")
            }
        }

        accountsRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            uaAdapter.notifyDataSetChanged()
            spinToAor(activityAor)
            if (aorSpinner.tag != "")
                updateIcons(Account.ofAor(aorSpinner.tag.toString())!!)
            if (BaresipService.isServiceRunning) {
                baresipService.action = "UpdateNotification"
                startService(baresipService)
            }
        }

        accountRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            spinToAor(activityAor)
            val ua = UserAgent.ofAor(activityAor)!!
            updateIcons(ua.account)
            if (it.resultCode == Activity.RESULT_OK)
                if (aorPasswords.containsKey(activityAor) && aorPasswords[activityAor] == "")
                    askPassword(getString(R.string.authentication_password), ua)
        }

        aorSpinner.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (aorSpinner.selectedItemPosition == -1) {
                    val i = Intent(this@MainActivity, AccountsActivity::class.java)
                    val b = Bundle()
                    b.putString("aor", "")
                    i.putExtras(b)
                    accountsRequest.launch(i)
                    true
                } else {
                    if ((event.x - view.left) < 100) {
                        val i = Intent(this@MainActivity, AccountActivity::class.java)
                        val b = Bundle()
                        b.putString("aor", aorSpinner.tag.toString())
                        i.putExtras(b)
                        accountRequest!!.launch(i)
                        true
                    } else {
                        UserAgent.uas()[aorSpinner.selectedItemPosition].account.resumeUri =
                                callUri.text.toString()
                        false
                    }
                }
            } else {
                // view.performClick()
                false
            }
        }

        callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                Contact.contacts().map { Contact -> Contact.name }))
        callUri.threshold = 2
        callUri.setOnFocusChangeListener { view, b ->
            if (b) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        callUri.setOnClickListener { view ->
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }

        securityButton.setOnClickListener {
            when (securityButton.tag) {
                "red" -> {
                    Utils.alertView(this, getString(R.string.alert),
                            getString(R.string.call_not_secure))
                }
                "yellow" -> {
                    Utils.alertView(this, getString(R.string.alert),
                            getString(R.string.peer_not_verified))
                }
                "green" -> {
                    val titleView = View.inflate(this, R.layout.alert_title, null) as TextView
                    titleView.text = getString(R.string.info)
                    with(AlertDialog.Builder(this)) {
                        setCustomTitle(titleView)
                        setMessage(getString(R.string.call_is_secure))
                        setPositiveButton(getString(R.string.unverify)) { dialog, _ ->
                            val calls = Call.uaCalls(UserAgent.uas()[aorSpinner.selectedItemPosition], "")
                            if (calls.size > 0) {
                                if (Api.cmd_exec("zrtp_unverify " + calls[0].zid) != 0) {
                                    Log.e(TAG, "Command 'zrtp_unverify ${calls[0].zid}' failed")
                                } else {
                                    securityButton.setImageResource(R.drawable.box_yellow)
                                    securityButton.tag = "yellow"
                                }
                            }
                            dialog.dismiss()
                        }
                        setNegativeButton(getString(R.string.no)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        show()
                    }
                }
            }
        }

        callButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition == -1)
                return@setOnClickListener
            if (Utils.checkPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO)))
                makeCall("voice")
            else
                Toast.makeText(applicationContext, getString(R.string.no_calls),
                        Toast.LENGTH_SHORT).show()
        }

        callVideoButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition == -1)
                return@setOnClickListener
            val permissions =
                if (BaresipService.supportedCameras)
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                else
                    arrayOf(Manifest.permission.RECORD_AUDIO)
            if (Utils.checkPermissions(this, permissions))
                makeCall("video")
            else
                if (!Utils.checkPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO)))
                    Toast.makeText(applicationContext, getString(R.string.no_calls),
                            Toast.LENGTH_SHORT).show()
                else
                    Toast.makeText(applicationContext, getString(R.string.no_video_calls),
                            Toast.LENGTH_SHORT).show()
        }

        hangupButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val uaCalls = Call.uaCalls(ua, "")
            if (uaCalls.size > 0) {
                val callp = uaCalls[uaCalls.size - 1].callp
                Log.d(TAG, "AoR $aor hanging up call $callp with ${callUri.text}")
                hangupButton.isEnabled = false
                Api.ua_hangup(ua.uap, callp, 0, "")
            }
        }

        answerButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val call = Call.uaCalls(ua, "in")[0]
            Log.d(TAG, "AoR $aor answering call ${call.callp} from ${callUri.text}")
            answerButton.isEnabled = false
            answerVideoButton.isEnabled = false
            rejectButton.isEnabled = false
            call.setMediaDirection(Api.SDP_SENDRECV, Api.SDP_INACTIVE)
            call.disableVideoStream(true)
            val intent = Intent(this@MainActivity, BaresipService::class.java)
            intent.action = "Call Answer"
            intent.putExtra("uap", ua.uap)
            intent.putExtra("callp", call.callp)
            startService(intent)
        }

        answerVideoButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val call = Call.uaCalls(ua, "in")[0]
            Log.d(TAG, "AoR $aor answering video call ${call.callp} from ${callUri.text}")
            answerButton.isEnabled = false
            answerVideoButton.isEnabled = false
            rejectButton.isEnabled = false
            val videoDir = if (Utils.isCameraAvailable(this))
                Api.SDP_SENDRECV
            else
                Api.SDP_RECVONLY
            call.setMediaDirection(Api.SDP_SENDRECV, videoDir)
            val intent = Intent(this@MainActivity, BaresipService::class.java)
            intent.action = "Call Answer"
            intent.putExtra("uap", ua.uap)
            intent.putExtra("callp", call.callp)
            startService(intent)
        }

        rejectButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val call = Call.uaCalls(ua, "in")[0]
            val callp = call.callp
            Log.d(TAG, "AoR $aor rejecting call $callp from ${callUri.text}")
            answerButton.isEnabled = false
            answerVideoButton.isEnabled = false
            rejectButton.isEnabled = false
            call.rejected = true
            Api.ua_hangup(ua.uap, callp, 486, "Rejected")
        }

        holdButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val call = Call.uaCalls(ua, "")[0]
            if (call.onhold) {
                Log.d(TAG, "AoR $aor resuming call ${call.callp} with ${callUri.text}")
                call.resume()
                call.onhold = false
                holdButton.setImageResource(R.drawable.hold)
            } else {
                Log.d(TAG, "AoR $aor holding call ${call.callp} with ${callUri.text}")
                call.hold()
                call.onhold = true
                holdButton.setImageResource(R.drawable.resume)
            }
        }

        transferButton.setOnClickListener {
            makeTransfer(UserAgent.uas()[aorSpinner.selectedItemPosition])
        }

        infoButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val calls = Call.uaCalls(ua, "")
            if (calls.size > 0) {
                val call = calls[0]
                val stats = call.stats("audio")
                if (stats != "") {
                    val parts = stats.split(",")
                    val codecs = call.audioCodecs()
                    val duration = call.duration()
                    val txCodec = codecs.split(',')[0].split("/")
                    val rxCodec = codecs.split(',')[1].split("/")
                    Utils.alertView(this, getString(R.string.call_info),
                            "${String.format(getString(R.string.duration), duration)}\n" +
                                    "${getString(R.string.codecs)}: ${txCodec[0]} ch ${txCodec[2]}/" +
                                    "${rxCodec[0]} ch ${rxCodec[2]}\n" +
                                    "${String.format(getString(R.string.rate), parts[0])}\n" +
                                    "${String.format(getString(R.string.average_rate), parts[1])}\n" +
                                    "${String.format(getString(R.string.jitter), parts[4])}\n" +
                                    "${getString(R.string.packets)}: ${parts[2]}\n" +
                                    "${getString(R.string.lost)}: ${parts[3]}")
                } else {
                    Utils.alertView(this, getString(R.string.call_info),
                            getString(R.string.call_info_not_available))
                }
            }
        }

        voicemailButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
                val acc = ua.account
                if (acc.vmUri != "") {
                    val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                val i = Intent(this, MainActivity::class.java)
                                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                i.putExtra("action", "call")
                                i.putExtra("uap", ua.uap)
                                i.putExtra("peer", acc.vmUri)
                                startActivity(i)
                            }
                            DialogInterface.BUTTON_NEGATIVE -> {
                            }
                        }
                    }
                    val titleView = View.inflate(this, R.layout.alert_title, null) as TextView
                    titleView.text = getString(R.string.voicemail_messages)
                    with(AlertDialog.Builder(this)) {
                        setCustomTitle(titleView)
                        setMessage(acc.vmMessages(this@MainActivity))
                        setPositiveButton(getString(R.string.listen), dialogClickListener)
                        setNegativeButton(getString(R.string.cancel), dialogClickListener)
                        show()
                    }
                }
            }
        }

        contactsRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                Contact.contacts().map { Contact -> Contact.name }))
            }

        contactsButton.setOnClickListener {
            val i = Intent(this@MainActivity, ContactsActivity::class.java)
            val b = Bundle()
            if (aorSpinner.selectedItemPosition >= 0)
                b.putString("aor", aorSpinner.tag.toString())
            else
                b.putString("aor", "")
            i.putExtras(b)
            contactsRequest.launch(i)
        }

        chatRequests = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            spinToAor(activityAor)
            updateIcons(Account.ofAor(activityAor)!!)
        }

        messagesButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val i = Intent(this@MainActivity, ChatsActivity::class.java)
                val b = Bundle()
                b.putString("aor", aorSpinner.tag.toString())
                b.putString("peer", resumeUri)
                i.putExtras(b)
                chatRequests.launch(i)
            }
        }

        callsRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            spinToAor(activityAor)
            callsButton.setImageResource(R.drawable.calls)
        }

        callsButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val i = Intent(this@MainActivity, CallsActivity::class.java)
                val b = Bundle()
                b.putString("aor", aorSpinner.tag.toString())
                i.putExtras(b)
                callsRequest.launch(i)
            }
        }

        dialpadButton.tag = "off"
        dialpadButton.setOnClickListener {
            if (dialpadButton.tag == "off") {
                callUri.inputType = InputType.TYPE_CLASS_PHONE
                dialpadButton.setImageResource(R.drawable.dialpad_on)
                dialpadButton.tag = "on"
                //Log.d(TAG, "Screen ${Utils.getScreenOrientation(applicationContext)}")
                //val path = BaresipService.downloadsPath + "/video.mp4"
                //Utils.ffmpegExecute("-video_size hd720 -f android_camera -camera_index 1 -i anything -r 10 -t 5 -y $path")
            } else {
                callUri.inputType = InputType.TYPE_CLASS_TEXT +
                        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                dialpadButton.setImageResource(R.drawable.dialpad_off)
                dialpadButton.tag = "off"
            }
        }

        videoButton.setOnClickListener {
            videoButton.isClickable = false
            videoButton.setImageResource(R.drawable.video_pending)
            Handler(Looper.getMainLooper()).postDelayed({
                val call = Call.call("connected")
                if (call != null) {
                    val dir = call.videoRequest
                    if (dir != 0) {
                        call.videoRequest = 0
                        call.setVideoDirection(dir)
                    } else {
                        if (Utils.isCameraAvailable(this))
                            call.setVideoDirection(Api.SDP_SENDRECV)
                        else
                            call.setVideoDirection(Api.SDP_RECVONLY)
                    }
                    am.isSpeakerphoneOn = true
                    speakerButton.setImageResource(R.drawable.speaker_on_button)
                    imm.hideSoftInputFromWindow(dtmf.windowToken, 0)
                }
            }, 250)
        }

        configRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if ((it.data != null) && it.data!!.hasExtra("restart")) {
                val titleView = View.inflate(this, R.layout.alert_title, null) as TextView
                titleView.text = getString(R.string.restart_request)
                with(AlertDialog.Builder(this)) {
                    setCustomTitle(titleView)
                    setMessage(getString(R.string.config_restart))
                    setPositiveButton(getText(R.string.restart)) { dialog, _ ->
                        dialog.dismiss()
                        quitRestart(true)
                    }
                    setNegativeButton(getText(R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    show()
                }
            }
            val displayTheme = Preferences(applicationContext).displayTheme
            if (displayTheme != AppCompatDelegate.getDefaultNightMode()) {
                AppCompatDelegate.setDefaultNightMode(displayTheme)
                delegate.applyDayNight()
            }
        }

        backupRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK)
                it.data?.data?.also { uri ->
                    downloadsOutputStream = applicationContext.contentResolver.openOutputStream(uri)
                            as FileOutputStream
                    if (downloadsOutputStream != null) {
                        downloadsOutputFile = Utils.fileNameOfUri(applicationContext, uri)
                        askPassword(getString(R.string.encrypt_password))
                    }
                }
        }

        restoreRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK)
                it.data?.data?.also { uri ->
                    downloadsInputStream = applicationContext.contentResolver.openInputStream(uri)
                            as FileInputStream
                    if (downloadsInputStream != null) {
                        downloadsInputFile = Utils.fileNameOfUri(applicationContext, uri)
                        askPassword(getString(R.string.decrypt_password))
                    }
                }
        }

        swipeRefresh.setOnTouchListener(object : OnSwipeTouchListener(this@MainActivity) {

            override fun onSwipeLeft() {
                super.onSwipeLeft()
                if (UserAgent.uas().size > 0) {
                    val curPos = aorSpinner.selectedItemPosition
                    val newPos = if (curPos == -1)
                        0
                    else
                        (curPos + 1) % UserAgent.uas().size
                    if (curPos != newPos) {
                        aorSpinner.setSelection(newPos)
                        showCall(UserAgent.uas()[newPos])
                    }
                }
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                if (UserAgent.uas().size > 0) {
                    val curPos = aorSpinner.selectedItemPosition
                    val newPos = when (curPos) {
                        -1 -> 0
                        0 -> UserAgent.uas().size - 1
                        else -> curPos - 1
                    }
                    if (curPos != newPos) {
                        aorSpinner.setSelection(newPos)
                        showCall(UserAgent.uas()[newPos])
                    }
                }
            }
        })

        swipeRefresh.setOnRefreshListener {
            if (UserAgent.uas().size > 0) {
                if (aorSpinner.selectedItemPosition == -1)
                    aorSpinner.setSelection(0)
                val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
                if (ua.account.regint > 0)
                    Api.ua_register(ua.uap)
            }
            swipeRefresh.isRefreshing = false
        }

        baresipService = Intent(this@MainActivity, BaresipService::class.java)

        atStartup = intent.hasExtra("onStartup")

        if (!BaresipService.isServiceRunning)
            if (File(filesDir.absolutePath + "/accounts").exists()) {
                val accounts = String(
                    Utils.getFileContents(filesDir.absolutePath + "/accounts")!!,
                    Charsets.UTF_8
                ).lines().toMutableList()
                askPasswords(accounts)
            } else {
                // Baresip is started for the first time
                firstRun = true
                startBaresip()
            }

        if (Preferences(applicationContext).displayTheme != AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.setDefaultNightMode(Preferences(applicationContext).displayTheme)
            delegate.applyDayNight()
        }

        requestPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    } // OnCreate

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "Main onStart")

        if (!Utils.checkPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO)))
            requestCallPermission(Manifest.permission.RECORD_AUDIO)
        else
            if (BaresipService.supportedCameras)
                if (!Utils.checkPermissions(this, arrayOf(Manifest.permission.CAMERA)))
                    requestCallPermission(Manifest.permission.CAMERA)

        val action = intent.getStringExtra("action")
        if (action != null) {
            // MainActivity was not visible when call, message, or transfer request came in
            intent.removeExtra("action")
            handleIntent(intent, action)
        }

    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Main onResume with action '$resumeAction'")
        nm.cancelAll()
        BaresipService.isMainVisible = true
        when (resumeAction) {
            "call show" -> {
                handleServiceEvent ("call incoming",
                    arrayListOf(resumeCall!!.ua.uap, resumeCall!!.callp))
            }
            "call answer" -> {
                answerButton.performClick()
                showCall(resumeCall!!.ua)
            }
            "call missed" -> {
                callsButton.performClick()
            }
            "call reject" ->
                rejectButton.performClick()
            "call" -> {
                callUri.setText(UserAgent.uas()[aorSpinner.selectedItemPosition].account.resumeUri)
                callButton.performClick()
            }
            "transfer show", "transfer accept" ->
                handleServiceEvent("$resumeAction,$resumeUri",
                    arrayListOf(resumeCall!!.ua.uap, resumeCall!!.callp))
            "message", "message show", "message reply" ->
                handleServiceEvent(resumeAction, arrayListOf(resumeUap, resumeUri))
            else -> {
                val incomingCall = Call.call("incoming")
                if (incomingCall != null) {
                    spinToAor(incomingCall.ua.account.aor)
                } else {
                    restoreActivities()
                    if (UserAgent.uas().size > 0) {
                        if (aorSpinner.selectedItemPosition == -1) {
                            if (Call.calls().size > 0)
                                spinToAor(Call.calls()[0].ua.account.aor)
                            else {
                                aorSpinner.setSelection(0)
                                aorSpinner.tag = UserAgent.uas()[0].account.aor
                            }
                        }
                    }
                }
                uaAdapter.notifyDataSetChanged()
                if (UserAgent.uas().size > 0) {
                    val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
                    showCall(ua)
                    updateIcons(ua.account)
                }
            }
        }
        resumeAction = ""
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Main onPause")
        Utils.addActivity("main")
        BaresipService.isMainVisible = false
        saveCallUri()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "Main onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Main onDestroy")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(screenEventReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceEventReceiver)
        BaresipService.activities.clear()
    }

    private fun addVideoLayoutViews() {

        videoLayout.addView(videoView.surfaceView)

        // Video Button
        val vb = ImageButton(this)
        vb.setImageResource(R.drawable.video_off)
        vb.setBackgroundResource(0)
        var prm: RelativeLayout.LayoutParams =
                RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT)
        prm.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        prm.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        prm.marginStart = 15
        prm.bottomMargin = 15
        vb.layoutParams = prm
        vb.setOnClickListener {
            Call.call("connected")?.setVideoDirection(Api.SDP_INACTIVE)
            am.isSpeakerphoneOn = false
            if (speakerIcon != null) speakerIcon!!.setIcon(R.drawable.speaker_off)
        }
        videoLayout.addView(vb)

        // Snapshot Button
        if ((Build.VERSION.SDK_INT >= 29) ||
                Utils.checkPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
            val sb = ImageButton(this)
            sb.setImageResource(R.drawable.snapshot)
            sb.setBackgroundResource(0)
            prm = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT)
            prm.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            prm.addRule(RelativeLayout.CENTER_VERTICAL)
            prm.marginStart = 15
            sb.layoutParams = prm
            sb.setOnClickListener {
                val sdf = SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault())
                val fileName = "IMG_" + sdf.format(Date()) + ".png"
                val filePath = BaresipService.filesPath + "/" + fileName
                if (Api.cmd_exec("snapshot_recv $filePath") != 0)
                    Log.e(TAG, "Command 'snapshot_recv $filePath' failed")
                else
                    MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
            }
            videoLayout.addView(sb)
        }

        // Camera Button
        if (Utils.isCameraAvailable(this)) {
            val cb = ImageButton(this)
            cb.setImageResource(R.drawable.camera_front)
            cb.setBackgroundResource(0)
            prm = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            prm.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            prm.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            prm.marginStart = 15
            prm.topMargin = 15
            cb.layoutParams = prm
            cb.setOnClickListener {
                val call = Call.call("connected")
                if (call != null) {
                    if (call.setVideoSource(!BaresipService.cameraFront) != 0)
                        Log.w(TAG, "Failed to set video source")
                    else
                        BaresipService.cameraFront = !BaresipService.cameraFront
                    if (BaresipService.cameraFront)
                        cb.setImageResource(R.drawable.camera_front)
                    else
                        cb.setImageResource(R.drawable.camera_rear)
                }
            }
            videoLayout.addView(cb)
        }

        // Speaker Button
        speakerButton = ImageButton(this)
        speakerButton.id = View.generateViewId()
        speakerButton.setBackgroundResource(0)
        prm = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT)
        prm.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        prm.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        prm.marginEnd = 15
        prm.topMargin = 15
        speakerButton.layoutParams = prm
        speakerButton.setOnClickListener {
            am.isSpeakerphoneOn = !am.isSpeakerphoneOn
            if (am.isSpeakerphoneOn) {
                speakerButton.setImageResource(R.drawable.speaker_on_button)
                if (speakerIcon != null) speakerIcon!!.setIcon(R.drawable.speaker_on)
            } else {
                speakerButton.setImageResource(R.drawable.speaker_off_button)
                if (speakerIcon != null) speakerIcon!!.setIcon(R.drawable.speaker_off)
            }
        }
        videoLayout.addView(speakerButton)

        // Mic Button
        val mb = ImageButton(this)
        mb.setBackgroundResource(0)
        prm = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT)
        prm.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        prm.addRule(RelativeLayout.BELOW, speakerButton.id)
        prm.marginEnd = 15
        prm.topMargin= 24
        mb.layoutParams = prm
        if (BaresipService.isMicMuted)
            mb.setImageResource(R.drawable.mic_off_button)
        else
            mb.setImageResource(R.drawable.mic_on_button)
        mb.setOnClickListener {
            BaresipService.isMicMuted = !BaresipService.isMicMuted
            if (BaresipService.isMicMuted) {
                mb.setImageResource(R.drawable.mic_off_button)
                Api.calls_mute(true)
            } else {
                mb.setImageResource(R.drawable.mic_on_button)
                Api.calls_mute(false)
            }
        }
        videoLayout.addView(mb)

        // Hangup Button
        val hb = ImageButton(this)
        hb.id = View.generateViewId()
        hb.setImageResource(R.drawable.hangup)
        hb.setBackgroundResource(0)
        prm = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT)
        prm.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        prm.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        prm.marginEnd = 15
        prm.bottomMargin = 15
        hb.layoutParams = prm
        hb.setOnClickListener {
            if (!Utils.isCameraAvailable(this))
                Call.call("connected")?.setVideoDirection(Api.SDP_INACTIVE)
            hangupButton.performClick()
        }
        videoLayout.addView(hb)

        // Info Button
        val ib = ImageButton(this)
        ib.setImageResource(R.drawable.video_info)
        ib.setBackgroundResource(0)
        prm = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT)
        prm.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        prm.addRule(RelativeLayout.ABOVE, hb.id)
        prm.marginEnd = 15
        prm.bottomMargin= 24
        ib.layoutParams = prm
        ib.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val calls = Call.uaCalls(ua, "")
            if (calls.size > 0) {
                val call = calls[0]
                val stats = call.stats("video")
                if (stats != "") {
                    val parts = stats.split(",")
                    val codecs = call.videoCodecs().split(',')
                    val duration = call.duration()
                    val txCodec = codecs[0]
                    val rxCodec = codecs[1]
                    Utils.alertView(this, getString(R.string.call_info),
                            "${String.format(getString(R.string.duration), duration)}\n" +
                                    "${getString(R.string.codecs)}: $txCodec/$rxCodec\n" +
                                    "${String.format(getString(R.string.rate), parts[0])}\n" +
                                    "${String.format(getString(R.string.average_rate), parts[1])}\n" +
                                    "${String.format(getString(R.string.jitter), parts[4])}\n" +
                                    "${getString(R.string.packets)}: ${parts[2]}\n" +
                                    "${getString(R.string.lost)}: ${parts[3]}")
                } else {
                    Utils.alertView(this, getString(R.string.call_info),
                            getString(R.string.call_info_not_available))
                }
            }
        }
        videoLayout.addView(ib)

    }

    override fun onNewIntent(intent: Intent) {
        // Called when MainActivity already exists at the top of current task
        super.onNewIntent(intent)

        Utils.setShowWhenLocked(this, true)
        Utils.setTurnScreenOn(this, true)

        resumeAction = ""
        resumeUri = ""
        if (intent.action == ACTION_CALL) {
            Log.d(TAG, "onNewIntent $ACTION_CALL ${intent.data}")
            if (Call.calls().isNotEmpty() || UserAgent.uas().isEmpty())
                return
            val uri: Uri? = intent.data
            if (uri != null) {
                val uriStr = URLDecoder.decode(uri.toString(), "UTF-8")
                when (uri.scheme) {
                    "sip" -> {
                        var ua = UserAgent.ofDomain(Utils.uriHostPart(uriStr))
                        if (ua == null)
                            ua = BaresipService.uas[0]
                        spinToAor(ua.account.aor)
                        resumeAction = "call"
                        ua.account.resumeUri = uriStr
                    }
                    "tel" -> {
                        val acc = BaresipService.uas[0].account
                        spinToAor(acc.aor)
                        resumeAction = "call"
                        acc.resumeUri = uriStr.replace("tel", "sip") + "@" + acc.aor
                    }
                    else -> {
                        Log.w(TAG, "Unsupported URI scheme ${uri.scheme}")
                        return
                    }
                }
            }
        } else {
            val action = intent.getStringExtra("action")
            Log.d(TAG, "onNewIntent action `$action'")
            if (action != null) {
                intent.removeExtra("action")
                handleIntent(intent, action)
            }
        }
    }

    private fun handleIntent(intent: Intent, action: String?) {
        Log.d(TAG, "Handling intent '$action'")
        when (action) {
            "accounts" -> {
                resumeAction = "accounts"
            }
            "no network" -> {
                Utils.alertView(this, getString(R.string.notice),
                    getString(R.string.no_network))
                return
            }
            "call" -> {
                if (Call.calls().isNotEmpty()) {
                    Toast.makeText(applicationContext, getString(R.string.call_already_active),
                            Toast.LENGTH_SHORT).show()
                    return
                }
                val uap = intent.getStringExtra("uap")!!
                val ua = UserAgent.ofUap(uap)
                if (ua == null) {
                    Log.w(TAG, "handleIntent 'call' did not find ua $uap")
                    return
                }
                if (ua.account.aor != aorSpinner.tag)
                    spinToAor(ua.account.aor)
                resumeAction = action
                ua.account.resumeUri = intent.getStringExtra("peer")!!
            }
            "call show", "call answer" -> {
                val callp = intent.getStringExtra("callp")!!
                val call = Call.ofCallp(callp)
                if (call == null) {
                    Log.w(TAG, "handleIntent '$action' did not find call $callp")
                    return
                }
                val ua = call.ua
                if (ua.account.aor != aorSpinner.tag)
                    spinToAor(ua.account.aor)
                resumeAction = action
                resumeCall = call
            }
            "call missed" -> {
                val uap = intent.getStringExtra("uap")!!
                val ua = UserAgent.ofUap(uap)
                if (ua == null) {
                    Log.w(TAG, "onNewIntent did not find ua $uap")
                    return
                }
                if (ua.account.aor != aorSpinner.tag)
                    spinToAor(ua.account.aor)
                resumeAction = action
            }
            "transfer show", "transfer accept" -> {
                val callp = intent.getStringExtra("callp")!!
                val call = Call.ofCallp(callp)
                if (call == null) {
                    Log.w(TAG, "handleIntent '$action' did not find call $callp")
                    moveTaskToBack(true)
                    return
                }
                resumeAction = action
                resumeCall = call
                resumeUri = intent.getStringExtra("uri")!!
            }
            "message", "message show", "message reply" -> {
                val uap = intent.getStringExtra("uap")!!
                val ua = UserAgent.ofUap(uap)
                if (ua == null) {
                    Log.w(TAG, "onNewIntent did not find ua $uap")
                    return
                }
                if (ua.account.aor != aorSpinner.tag)
                    spinToAor(ua.account.aor)
                resumeAction = action
                resumeUap = uap
                resumeUri = intent.getStringExtra("peer")!!
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val stream = if (am.mode == AudioManager.MODE_RINGTONE)
            AudioManager.STREAM_RING
        else
            AudioManager.STREAM_MUSIC
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                Log.d(TAG, "Adjusting volume $keyCode of stream $stream")
                am.adjustStreamVolume(stream,
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                            AudioManager.ADJUST_LOWER else
                            AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleServiceEvent(event: String, params: ArrayList<String>) {
        if (taskId == -1) {
            Log.d(TAG, "Omit service event '$event' for task -1")
            return
        }
        if (event == "started") {
            val callActionUri = params[0]
            Log.d(TAG, "Handling service event 'started' with '$callActionUri'")
            uaAdapter.notifyDataSetChanged()
            if (callActionUri != "") {
                var ua = UserAgent.ofDomain(Utils.uriHostPart(callActionUri))
                if (ua == null)
                    if (BaresipService.uas.size > 0) {
                        ua = BaresipService.uas[0]
                    } else {
                        Log.w(TAG, "No UAs to make the call to '$callActionUri'")
                        return
                    }
                spinToAor(ua.account.aor)
                ua.account.resumeUri = callActionUri
                callUri.setText(callActionUri)
                callButton.performClick()
            } else {
                if ((aorSpinner.selectedItemPosition == -1) && (UserAgent.uas().size > 0)) {
                    aorSpinner.setSelection(0)
                    aorSpinner.tag = UserAgent.uas()[0].account.aor
                }
            }
            return
        }
        if (event == "stopped") {
            Log.d(TAG, "Handling service event 'stopped' with param '${params[0]}'")
            if (params[0] != "") {
                val titleView = View.inflate(this, R.layout.alert_title, null) as TextView
                titleView.text = getString(R.string.notice)
                with(AlertDialog.Builder(this)) {
                    setCustomTitle(titleView)
                    setMessage(getString(R.string.start_failed))
                    setNeutralButton(getString(R.string.ok)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    show()
                }
            } else {
                quitTimer.cancel()
                finishAndRemoveTask()
                if (restart)
                    reStart()
                else
                    exitProcess(0)
            }
            return
        }

        val uap = params[0]
        val ua = UserAgent.ofUap(uap)
        if (ua == null) {
            Log.w(TAG, "handleServiceEvent '$event' did not find ua $uap")
            return
        }
        val ev = event.split(",")
        Log.d(TAG, "Handling service event '${ev[0]}' for $uap")
        val acc = ua.account
        val aor = ua.account.aor
        for (account_index in UserAgent.uas().indices) {
            if (UserAgent.uas()[account_index].account.aor == aor) {
                when (ev[0]) {
                    "registered", "unregistering" -> {
                        uaAdapter.notifyDataSetChanged()
                    }
                    "registering failed" -> {
                        uaAdapter.notifyDataSetChanged()
                        Toast.makeText(applicationContext,
                                String.format(getString(R.string.registering_failed), aor) +
                                        ": ${ev[1]}",
                                Toast.LENGTH_LONG).show()
                    }
                    "call rejected" -> {
                        if (aor == aorSpinner.tag) {
                            callsButton.setImageResource(R.drawable.calls_missed)
                        }
                    }
                    "call incoming", "call outgoing" -> {
                        val callp = params[1]
                        if (BaresipService.isMainVisible) {
                            if (aor != aorSpinner.tag)
                                spinToAor(aor)
                            showCall(ua, Call.ofCallp(callp))
                        } else {
                            Log.d(TAG, "Reordering to front")
                            val i = Intent(applicationContext, MainActivity::class.java)
                            i.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            i.putExtra("action", "call show")
                            i.putExtra("callp", callp)
                            startActivity(i)
                        }
                    }
                    "call established" -> {
                        if (Call.ofCallp(params[1])!!.videoEnabled()) {
                            am.isSpeakerphoneOn = true
                            speakerButton.setImageResource(R.drawable.speaker_on_button)
                            if (speakerIcon != null) speakerIcon!!.setIcon(R.drawable.speaker_on)
                        }
                        if (aor == aorSpinner.tag) {
                            dtmf.setText("")
                            dtmf.hint = getString(R.string.dtmf)
                            showCall(ua)
                        }
                    }
                    "call update" -> {
                        showCall(ua)
                    }
                    "call video request" -> {
                        val callp = params[1]
                        val dir = params[2].toInt()
                        val call = Call.ofCallp(callp)
                        if (call == null) {
                            Log.w(TAG, "Video request call $callp not found")
                            return
                        }
                        if (!isFinishing && !alerting) {
                            val titleView = View.inflate(this, R.layout.alert_title, null) as TextView
                            titleView.text = getString(R.string.video_request)
                            with(AlertDialog.Builder(this)) {
                                setCustomTitle(titleView)
                                val peerUri = Utils.friendlyUri(call.peerUri, Utils.aorDomain(aor))
                                val msg = when (dir) {
                                    1 -> String.format(getString(R.string.allow_video_recv), peerUri)
                                    2 -> String.format(getString(R.string.allow_video_send), peerUri)
                                    3 -> String.format(getString(R.string.allow_video), peerUri)
                                    else -> ""
                                }
                                setMessage(msg)
                                setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                                    call.videoRequest = dir
                                    videoButton.performClick()
                                    dialog.dismiss()
                                    alerting = false
                                }
                                setNegativeButton(getString(R.string.no)) { dialog, _ ->
                                    dialog.dismiss()
                                    alerting = false
                                }
                                alerting = true
                                show()
                            }
                        }
                    }
                    "call verify" -> {
                        val callp = params[1]
                        val call = Call.ofCallp(callp)
                        if (call == null) {
                            Log.w(TAG, "Call $callp to be verified is not found")
                            return
                        }
                        val titleView = View.inflate(this, R.layout.alert_title, null) as TextView
                        titleView.text = getString(R.string.verify)
                        with(AlertDialog.Builder(this)) {
                            setCustomTitle(titleView)
                            setMessage(String.format(getString(R.string.verify_sas),
                                    ev[1], ev[2]))
                            setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                                val security: Int = if (Api.cmd_exec("zrtp_verify ${ev[3]}") != 0) {
                                    Log.e(TAG, "Command 'zrtp_verify ${ev[3]}' failed")
                                    R.drawable.box_yellow
                                } else {
                                    R.drawable.box_green
                                }
                                call.security = security
                                call.zid = ev[3]
                                if (aor == aorSpinner.tag) {
                                    securityButton.setImageResource(security)
                                    setSecurityButtonTag(securityButton, security)
                                    securityButton.visibility = View.VISIBLE
                                    dialog.dismiss()
                                }
                            }
                            setNegativeButton(getString(R.string.no)) { dialog, _ ->
                                call.security = R.drawable.box_yellow
                                call.zid = ev[3]
                                if (aor == aorSpinner.tag) {
                                    securityButton.setImageResource(R.drawable.box_yellow)
                                    securityButton.tag = "yellow"
                                    securityButton.visibility = View.VISIBLE
                                }
                                dialog.dismiss()
                            }
                            show()
                        }
                    }
                    "call verified", "call secure" -> {
                        val callp = params[1]
                        val call = Call.ofCallp(callp)
                        if (call == null) {
                            Log.w(TAG, "Call $callp that is verified is not found")
                            return
                        }
                        val tag: String = if (call.security == R.drawable.box_yellow)
                            "yellow"
                        else
                            "green"
                        if (aor == aorSpinner.tag) {
                            securityButton.setImageResource(call.security)
                            securityButton.tag = tag
                        }
                    }
                    "call transfer", "transfer show" -> {
                        val callp = params[1]
                        val call = Call.ofCallp(callp)
                        if (call == null) {
                            Log.w(TAG, "Call $callp to be transferred is not found")
                            return
                        }
                        val titleView = View.inflate(this, R.layout.alert_title, null) as TextView
                        titleView.text = getString(R.string.transfer_request)
                        val target = Utils.friendlyUri(ContactsActivity.contactName(ev[1]),
                                Utils.aorDomain(aor))
                        with(AlertDialog.Builder(this)) {
                            setCustomTitle(titleView)
                            setMessage(String.format(getString(R.string.transfer_request_query),
                                    target))
                            setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                                acceptTransfer(ua, call, ev[1])
                                dialog.dismiss()
                            }
                            setNegativeButton(getString(R.string.no)) { dialog, _ ->
                                if (call in Call.calls())
                                    call.notifySipfrag(603, "Decline")
                                dialog.dismiss()
                            }
                            show()
                        }
                    }
                    "transfer accept" -> {
                        val callp = params[1]
                        val call = Call.ofCallp(callp)
                        if (call == null) {
                            Log.w(TAG, "Call $callp to be transferred is not found")
                            return
                        }
                        if (call in Call.calls())
                            Api.ua_hangup(uap, callp, 0, "")
                        call(ua, ev[1], "voice")
                        showCall(ua)
                    }
                    "transfer failed" -> {
                        Toast.makeText(applicationContext,
                                "${getString(R.string.transfer_failed)}: ${ev[1].trim()}",
                                Toast.LENGTH_LONG).show()
                        showCall(ua)
                    }
                    "call closed" -> {
                        if (aor == aorSpinner.tag) {
                            callUri.inputType = InputType.TYPE_CLASS_TEXT +
                                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                            dialpadButton.setImageResource(R.drawable.dialpad_off)
                            dialpadButton.tag = "off"
                            ua.account.resumeUri = ""
                            showCall(ua)
                            if (acc.missedCalls)
                                callsButton.setImageResource(R.drawable.calls_missed)
                        }
                        if (speakerIcon != null) speakerIcon!!.setIcon(R.drawable.speaker_off)
                        speakerButton.setImageResource(R.drawable.speaker_off_button)
                        val param = ev[1].trim()
                        if ((param != "") && (Call.uaCalls(ua, "").size == 0)) {
                            if (param[0].isDigit())
                                Toast.makeText(applicationContext,
                                        "${getString(R.string.call_failed)}: $param",
                                        Toast.LENGTH_LONG).show()
                            else
                                Toast.makeText(applicationContext,
                                        "${getString(R.string.call_closed)}: $param",
                                        Toast.LENGTH_LONG).show()
                        }
                        restoreActivities()
                        if (kgm.isDeviceLocked)
                            Utils.setShowWhenLocked(this, false)
                    }
                    "message", "message show", "message reply" -> {
                        val peer = params[1]
                        val i = Intent(applicationContext, ChatActivity::class.java)
                        i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        val b = Bundle()
                        b.putString("aor", aor)
                        b.putString("peer", peer)
                        b.putBoolean("focus", ev[0] == "message reply")
                        i.putExtras(b)
                        chatRequests.launch(i)
                    }
                    "mwi notify" -> {
                        val lines = ev[1].split("\n")
                        for (line in lines) {
                            if (line.startsWith("Voice-Message:")) {
                                val counts = (line.split(" ")[1]).split("/")
                                acc.vmNew = counts[0].toInt()
                                acc.vmOld = counts[1].toInt()
                                break
                            }
                        }
                        if (aor == aorSpinner.tag) {
                            if (acc.vmNew > 0)
                                voicemailButton.setImageResource(R.drawable.voicemail_new)
                            else
                                voicemailButton.setImageResource(R.drawable.voicemail)
                        }
                    }
                    else -> Log.e(TAG, "Unknown event '${ev[0]}'")
                }
                break
            }
        }
    }

    private fun reStart() {
        Log.d(TAG, "Trigger restart")
        val pm = applicationContext.packageManager
        val intent = pm.getLaunchIntentForPackage(this.packageName)
        this.finishAffinity()
        this.startActivity(intent)
        exitProcess(0)
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menuInflater.inflate(R.menu.main_menu, menu)

        menuInflater.inflate(R.menu.mic_icon, menu)
        micIcon = menu.findItem(R.id.micIcon)
        if (BaresipService.isMicMuted)
            micIcon!!.setIcon(R.drawable.mic_off)
        else
            micIcon!!.setIcon(R.drawable.mic_on)

        menuInflater.inflate(R.menu.speaker_icon, menu)
        speakerIcon = menu.findItem(R.id.speakerIcon)
        if (am.isSpeakerphoneOn)
            speakerIcon!!.setIcon(R.drawable.speaker_on)
        else
            speakerIcon!!.setIcon(R.drawable.speaker_off)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.micIcon -> {
                if (Call.call("connected") != null) {
                    BaresipService.isMicMuted = !BaresipService.isMicMuted
                    if (BaresipService.isMicMuted) {
                        item.setIcon(R.drawable.mic_off)
                        Api.calls_mute(true)
                    } else {
                        item.setIcon(R.drawable.mic_on)
                        Api.calls_mute(false)
                    }
                }
            }

            R.id.speakerIcon -> {
                am.isSpeakerphoneOn = !am.isSpeakerphoneOn
                if (am.isSpeakerphoneOn) {
                    item.setIcon(R.drawable.speaker_on)
                    speakerButton.setImageResource(R.drawable.speaker_on_button)
                } else {
                    item.setIcon(R.drawable.speaker_off)
                    speakerButton.setImageResource(R.drawable.speaker_off_button)
                }
            }

            R.id.config -> {
                configRequest.launch(Intent(this, ConfigActivity::class.java))
            }

            R.id.accounts -> {
                val i = Intent(this, AccountsActivity::class.java)
                val b = Bundle()
                b.putString("aor", aorSpinner.tag.toString())
                i.putExtras(b)
                accountsRequest.launch(i)
            }

            R.id.backup -> {
                when {
                    Build.VERSION.SDK_INT >= 29 -> pickupFileFromDownloads("backup")
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        Log.d(TAG, "Write External Storage permission granted")
                        val path = Utils.downloadsPath("baresip.bs")
                        downloadsOutputStream = FileOutputStream(File(path))
                        askPassword(getString(R.string.encrypt_password))
                    }
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                        defaultLayout.showSnackBar(
                            binding.root,
                            getString(R.string.no_backup),
                            Snackbar.LENGTH_INDEFINITE,
                            getString(R.string.ok)
                        ) {
                            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                    else -> {
                        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
            }

            R.id.restore -> {
                when {
                    Build.VERSION.SDK_INT >= 29 -> pickupFileFromDownloads("restore")
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        Log.d(TAG, "Read External Storage permission granted")
                        val path = Utils.downloadsPath("baresip.bs")
                        downloadsInputStream = FileInputStream(File(path))
                        askPassword(getString(R.string.decrypt_password))
                    }
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                        defaultLayout.showSnackBar(
                            binding.root,
                            getString(R.string.no_restore),
                            Snackbar.LENGTH_INDEFINITE,
                            getString(R.string.ok)
                        ) {
                            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                    else -> {
                        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            }

            R.id.about -> {
                startActivity(Intent(this, AboutActivity::class.java))
            }

            R.id.restart, R.id.quit -> {
                quitRestart(item.itemId == R.id.restart)
            }
        }

        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {

            MIC_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty()) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        if (BaresipService.supportedCameras &&
                                !Utils.checkPermissions(this, arrayOf(Manifest.permission.CAMERA)))
                            requestCallPermission(Manifest.permission.CAMERA)
                    } else {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO))
                            defaultLayout.showSnackBar(
                                    binding.root,
                                    getString(R.string.no_calls),
                                    Snackbar.LENGTH_INDEFINITE,
                                    getString(R.string.ok)
                            ) {
                                requestCallPermission(Manifest.permission.RECORD_AUDIO)
                            }
                    }
                }
            }

            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty())
                    if (grantResults[0] == PackageManager.PERMISSION_DENIED)
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA))
                            defaultLayout.showSnackBar(
                                    binding.root,
                                    getString(R.string.no_video_calls),
                                    Snackbar.LENGTH_INDEFINITE,
                                    getString(R.string.ok)
                            ) {
                                requestCallPermission(Manifest.permission.CAMERA)
                            }
            }

        }
    }

    private fun requestCallPermission(permission: String) {
        ActivityCompat.requestPermissions(this, arrayOf(permission),
                if (permission == Manifest.permission.RECORD_AUDIO)
                    MIC_PERMISSION_REQUEST_CODE
                else
                    CAMERA_PERMISSION_REQUEST_CODE)
    }

    private fun quitRestart(reStart: Boolean) {
        if (stopState == "initial") {
            Log.d(TAG, "quitRestart Restart = $reStart")
            if (BaresipService.isServiceRunning) {
                restart = reStart
                baresipService.action = "Stop"
                startService(baresipService)
                quitTimer.start()
            } else {
                finishAndRemoveTask()
                if (reStart)
                    reStart()
                else
                    exitProcess(0)
            }
        }
    }

    private fun makeTransfer(ua: UserAgent) {
        val layout = LayoutInflater.from(this)
                .inflate(R.layout.call_transfer_dialog, findViewById(android.R.id.content),
                        false)
        val titleView = layout.findViewById(R.id.title) as TextView
        titleView.text = getString(R.string.call_transfer)
        val transferUri = layout.findViewById(R.id.transferUri) as AutoCompleteTextView
        transferUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                Contact.contacts().map { Contact -> Contact.name }))
        transferUri.threshold = 2
        transferUri.requestFocus()
        val builder = AlertDialog.Builder(this)
        with(builder) {
            setView(layout)
            setPositiveButton(R.string.transfer) { dialog, _ ->
                imm.hideSoftInputFromWindow(transferUri.windowToken, 0)
                dialog.dismiss()
                val uriText = transferUri.text.toString().trim()
                if (uriText.isNotEmpty()) {
                    val uri = Utils.uriComplete(
                            ContactsActivity.findContactURI(uriText)
                                    .filterNot { it.isWhitespace() },
                            Utils.aorDomain(ua.account.aor)
                    )
                    if (!Utils.checkSipUri(uri)) {
                        Utils.alertView(this@MainActivity, getString(R.string.notice),
                                String.format(getString(R.string.invalid_sip_uri), uri))
                    } else {
                        if (Call.uaCalls(ua, "").size > 0) {
                            Call.uaCalls(ua, "")[0].transfer(uri)
                            showCall(ua)
                        }
                    }
                }
            }
            setNegativeButton(android.R.string.cancel) { dialog, _ ->
                imm.hideSoftInputFromWindow(transferUri.windowToken, 0)
                dialog.cancel()
            }
        }
        val alertDialog = builder.create()
        // This needs to be done after dialog has been created and before it is shown
        alertDialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        alertDialog.show()
    }

    @RequiresApi(29)
    private fun pickupFileFromDownloads(action: String) {
        when (action) {
            "backup" -> {
                backupRequest.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, "baresip+.bs")
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
                })
            }
            "restore" -> {
                restoreRequest.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
                })
            }
        }
    }

    private fun askPassword(title: String, ua: UserAgent? = null) {
        val layout = LayoutInflater.from(this)
                .inflate(R.layout.password_dialog, findViewById(android.R.id.content),
                        false)
        val titleView = layout.findViewById(R.id.title) as TextView
        titleView.text = title
        if (ua != null) {
            val messageView = layout.findViewById(R.id.message) as TextView
            val message = getString(R.string.account) + " " + Utils.plainAor(activityAor)
            messageView.text = message
        }
        val input = layout.findViewById(R.id.password) as EditText
        input.requestFocus()
        val context = this
        with(AlertDialog.Builder(this, R.style.AlertDialog)) {
            setView(layout)
            setPositiveButton(android.R.string.ok) { dialog, _ ->
                imm.hideSoftInputFromWindow(input.windowToken, 0)
                dialog.dismiss()
                var password = input.text.toString().trim()
                if (!Account.checkAuthPass(password)) {
                    Utils.alertView(context, getString(R.string.notice),
                            String.format(getString(R.string.invalid_authentication_password), password))
                    password = ""
                }
                when (title) {
                    getString(R.string.encrypt_password) ->
                        if (password != "") backup(password)
                    getString(R.string.decrypt_password) ->
                        if (password != "") restore(password)
                    else ->
                        AccountsActivity.setAuthPass(ua!!, password)
                }
            }
            setNegativeButton(android.R.string.cancel) { dialog, _ ->
                imm.hideSoftInputFromWindow(input.windowToken, 0)
                dialog.cancel()
            }
            val dialog = this.create()
            dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.show()
        }
    }

    private fun askPasswords(accounts: MutableList<String>) {
        if (accounts.isNotEmpty()) {
            val account = accounts.removeAt(0)
            val params = account.substringAfter(">")
            if ((Utils.paramValue(params, "auth_user") != "") &&
                    (Utils.paramValue(params, "auth_pass") == "")) {
                val aor = account.substringAfter("<").substringBefore(">")
                val layout = LayoutInflater.from(this)
                        .inflate(R.layout.password_dialog, findViewById(android.R.id.content),
                                false)
                val titleView = layout.findViewById(R.id.title) as TextView
                titleView.text = getString(R.string.authentication_password)
                val messageView = layout.findViewById(R.id.message) as TextView
                val message = getString(R.string.account) + " " + Utils.plainAor(aor)
                messageView.text = message
                val input = layout.findViewById(R.id.password) as EditText
                input.requestFocus()
                val context = this
                with(AlertDialog.Builder(this, R.style.AlertDialog)) {
                    setView(layout)
                    setPositiveButton(android.R.string.ok) { dialog, _ ->
                        imm.hideSoftInputFromWindow(input.windowToken, 0)
                        dialog.dismiss()
                        val password = input.text.toString().trim()
                        if (!Account.checkAuthPass(password)) {
                            Utils.alertView(context, getString(R.string.notice),
                                    String.format(getString(R.string.invalid_authentication_password), password))
                        } else {
                            aorPasswords[aor] = password
                        }
                        askPasswords(accounts)
                    }
                    setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        imm.hideSoftInputFromWindow(input.windowToken, 0)
                        dialog.cancel()
                        aorPasswords[aor] = ""
                        askPasswords(accounts)
                    }
                    val dialog = this.create()
                    dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    dialog.show()
                }
            } else {
                askPasswords(accounts)
            }
        } else {
            startBaresip()
        }
    }

    private fun startBaresip() {
        baresipService.action = "Start"
        startService(baresipService)
        if (atStartup)
            moveTaskToBack(true)
    }

    private fun backup(password: String) {
        val files = arrayListOf("accounts", "calls", "config", "contacts", "messages", "uuid",
                "zrtp_cache.dat", "zrtp_zid", "cert.pem", "ca_cert", "ca_certs.crt")
        File(BaresipService.filesPath).walk().forEach {
            if (it.name.endsWith(".png")) files.add(it.name)
        }
        val zipFile = getString(R.string.app_name_plus) + ".zip"
        val zipFilePath = BaresipService.filesPath + "/$zipFile"
        if (!Utils.zip(files, zipFile)) {
            Log.w(TAG, "Failed to write zip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.backup_failed),
                            downloadsOutputFile))
            return
        }
        val content = Utils.getFileContents(zipFilePath)
        if (content == null) {
            Log.w(TAG, "Failed to read zip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.backup_failed),
                            downloadsOutputFile))
            return
        }
        if (!Utils.encryptToStream(downloadsOutputStream, content, password)) {
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.backup_failed),
                            downloadsOutputFile))
            return
        }
        Utils.alertView(this, getString(R.string.info),
                String.format(getString(R.string.backed_up),
                        downloadsOutputFile))
        Utils.deleteFile(File(zipFilePath))
    }

    private fun restore(password: String) {
        val zipFile = getString(R.string.app_name_plus) + ".zip"
        val zipFilePath = BaresipService.filesPath + "/$zipFile"
        val zipData = Utils.decryptFromStream(downloadsInputStream, password)
        if (zipData == null) {
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.restore_failed),
                            downloadsInputFile))
            return
        }
        if (!Utils.putFileContents(zipFilePath, zipData)) {
            Log.w(TAG, "Failed to write zip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.restore_failed),
                            downloadsInputFile))
            return
        }
        if (!Utils.unZip(zipFilePath)) {
            Log.w(TAG, "Failed to unzip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.restore_failed),
                            downloadsInputFile))
            return
        }
        Utils.deleteFile(File(zipFilePath))
        val titleView = View.inflate(this, R.layout.alert_title, null) as TextView
        titleView.text = getString(R.string.info)
        with(AlertDialog.Builder(this)) {
            setCustomTitle(titleView)
            setMessage(getString(R.string.restored))
            setPositiveButton(getText(R.string.restart)) { dialog, _ ->
                quitRestart(true)
                dialog.dismiss()
            }
            setNegativeButton(getText(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            show()
        }
    }

    private fun spinToAor(aor: String) {
        for (account_index in UserAgent.uas().indices)
            if (UserAgent.uas()[account_index].account.aor == aor) {
                aorSpinner.setSelection(account_index)
                aorSpinner.tag = aor
                return
            }
        if (UserAgent.uas().isNotEmpty()) {
            aorSpinner.setSelection(0)
            aorSpinner.tag = UserAgent.uas()[0].account.aor
        } else {
            aorSpinner.setSelection(-1)
            aorSpinner.tag = ""
        }
    }

    private fun makeCall(kind: String) {
        callUri.setAdapter(null)
        val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
        val aor = ua.account.aor
        if (Call.calls().isEmpty()) {
            val uriText = callUri.text.toString().trim()
            if (uriText.isNotEmpty()) {
                val uri = Utils.uriComplete(
                    ContactsActivity.findContactURI(uriText).filterNot { it.isWhitespace() },
                    Utils.aorDomain(aor)
                )
                if (!Utils.checkSipUri(uri)) {
                    Utils.alertView(this, getString(R.string.notice),
                            String.format(getString(R.string.invalid_sip_uri), uri))
                } else {
                    callUri.isFocusable = false
                    if (!call(ua, uri, kind)) {
                        callButton.visibility = View.VISIBLE
                        callButton.isEnabled = true
                        callVideoButton.visibility = View.VISIBLE
                        callVideoButton.isEnabled = true
                        hangupButton.visibility = View.INVISIBLE
                        hangupButton.isEnabled = false
                    } else {
                        callButton.visibility = View.INVISIBLE
                        callButton.isEnabled = false
                        callVideoButton.visibility = View.INVISIBLE
                        callVideoButton.isEnabled = false
                        hangupButton.visibility = View.VISIBLE
                        hangupButton.isEnabled = true
                    }
                }
            } else {
                val latest = NewCallHistory.aorLatestHistory(aor)
                if (latest != null)
                    callUri.setText(
                        Utils.friendlyUri(
                            ContactsActivity.contactName(latest.peerUri),
                            Utils.aorDomain(ua.account.aor)
                        )
                    )
            }
        }
    }

    private fun call(ua: UserAgent, uri: String, kind: String): Boolean {
        if (!Utils.checkPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO))) {
            Toast.makeText(applicationContext, getString(R.string.no_calls),
                    Toast.LENGTH_LONG).show()
            return false
        }
        if (ua.account.aor != aorSpinner.tag)
            spinToAor(ua.account.aor)
        val videoDir = when {
            kind == "voice" -> Api.SDP_INACTIVE
            Utils.isCameraAvailable(this) -> Api.SDP_SENDRECV
            else -> Api.SDP_RECVONLY
        }
        val callp = Api.ua_call_alloc(ua.uap, "", Api.VIDMODE_ON)
        return if (callp != "") {
            Log.d(TAG, "Adding outgoing $kind call ${ua.uap}/$callp/$uri")
            val call = Call(callp, ua, uri, "out", "outgoing", Utils.dtmfWatcher(callp))
            call.add()
            var err = call.setMediaDirection(Api.SDP_SENDRECV, videoDir)
            if (err == 0) {
                err = call.connect(uri)
                if (err == 0) {
                    showCall(ua)
                    true
                } else {
                    Log.w(TAG, "Call $callp call_connect failed with error $err")
                    false
                }
            } else {
                Log.w(TAG, "Call $callp callSetMediaDirection failed with error $err")
                false
            }
        } else {
            Log.e(TAG, "ua_call_alloc ${ua.uap}/$uri failed")
            false
        }
    }

    private fun acceptTransfer(ua: UserAgent, call: Call, uri: String) {
        val newCallp = Api.ua_call_alloc(ua.uap, call.callp, Api.VIDMODE_OFF)
        if (newCallp != "") {
            Log.d(TAG, "Adding outgoing call ${ua.uap}/$newCallp/$uri")
            val newCall = Call(newCallp, ua, uri, "out", "transferring",
                    Utils.dtmfWatcher(newCallp))
            newCall.add()
            val err = newCall.connect(uri)
            if (err == 0) {
                if (ua.account.aor != aorSpinner.tag)
                    spinToAor(ua.account.aor)
                showCall(ua)
            } else {
                Log.w(TAG, "call_connect $newCallp failed with error $err")
                call.notifySipfrag(500, "Call Error")
            }
        } else {
            Log.w(TAG, "ua_call_alloc ${ua.uap}/${call.callp} failed")
            call.notifySipfrag(500, "Call Error")
        }
    }

    private fun setSecurityButtonTag(button: ImageButton, security: Int) {
        when (security) {
            R.drawable.box_red -> {
                button.tag = "red"
            }
            R.drawable.box_yellow -> {
                button.tag = "yellow"
            }
            R.drawable.box_green -> {
                button.tag = "green"
            }
        }
    }

    private fun showCall(ua: UserAgent, showCall: Call? = null) {
        if (Call.uaCalls(ua, "").size == 0) {
            swipeRefresh.isEnabled = true
            videoLayout.visibility = View.INVISIBLE
            defaultLayout.visibility = View.VISIBLE
            callTitle.text = getString(R.string.outgoing_call_to_dots)
            if (ua.account.resumeUri != "")
                callUri.setText(ua.account.resumeUri)
            else
                callUri.text.clear()
            callUri.hint = getString(R.string.callee)
            callUri.isFocusable = true
            callUri.isFocusableInTouchMode = true
            imm.hideSoftInputFromWindow(callUri.windowToken, 0)
            callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                    Contact.contacts().map { Contact -> Contact.name }))
            securityButton.visibility = View.INVISIBLE
            callButton.visibility = View.VISIBLE
            callButton.isEnabled = true
            callVideoButton.visibility = View.VISIBLE
            callVideoButton.isEnabled = true
            hangupButton.visibility = View.INVISIBLE
            answerButton.visibility = View.INVISIBLE
            answerVideoButton.visibility = View.INVISIBLE
            rejectButton.visibility = View.INVISIBLE
            callControl.visibility = View.INVISIBLE
            dialpadButton.isEnabled = true
            videoButton.visibility = View.INVISIBLE
            if (BaresipService.isMicMuted) {
                BaresipService.isMicMuted = false
                micIcon!!.setIcon(R.drawable.mic_on)
            }
            onHoldNotice.visibility = View.GONE
        } else {
            swipeRefresh.isEnabled = false
            val call = showCall ?: Call.uaCalls(ua, "")[0]
            callUri.isFocusable = false
            when (call.status) {
                "outgoing", "transferring" -> {
                    callTitle.text = getString(R.string.outgoing_call_to_dots)
                    callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(call.peerUri),
                            Utils.aorDomain(ua.account.aor)))
                    videoButton.visibility = View.INVISIBLE
                    securityButton.visibility = View.INVISIBLE
                    callButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.VISIBLE
                    hangupButton.isEnabled = true
                    answerButton.visibility = View.INVISIBLE
                    answerVideoButton.visibility = View.INVISIBLE
                    rejectButton.visibility = View.INVISIBLE
                    callControl.visibility = View.INVISIBLE
                    onHoldNotice.visibility = View.GONE
                    dialpadButton.isEnabled = false
                }
                "incoming" -> {
                    callTitle.text = getString(R.string.incoming_call_from_dots)
                    callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(call.peerUri),
                            Utils.aorDomain(ua.account.aor)))
                    callUri.setAdapter(null)
                    videoButton.visibility = View.INVISIBLE
                    securityButton.visibility = View.INVISIBLE
                    callButton.visibility = View.INVISIBLE
                    callVideoButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.INVISIBLE
                    answerButton.visibility = View.VISIBLE
                    answerButton.isEnabled = true
                    if (call.hasVideo()) {
                        answerVideoButton.visibility = View.VISIBLE
                        answerVideoButton.isEnabled = true
                    } else {
                        answerVideoButton.visibility = View.INVISIBLE
                        answerVideoButton.isEnabled = false
                    }
                    rejectButton.visibility = View.VISIBLE
                    rejectButton.isEnabled = true
                    callControl.visibility = View.INVISIBLE
                    onHoldNotice.visibility = View.GONE
                    dialpadButton.isEnabled = false
                }
                "connected" -> {
                    if (call.videoEnabled()) {
                        if (defaultLayout.visibility == View.VISIBLE) {
                            defaultLayout.visibility = View.INVISIBLE
                            videoLayout.visibility = View.VISIBLE
                        }
                        return
                    }
                    if (defaultLayout.visibility == View.INVISIBLE) {
                        videoLayout.visibility = View.INVISIBLE
                        defaultLayout.visibility = View.VISIBLE
                    }
                    callControl.post {
                        callControl.scrollTo(videoButton.left, videoButton.top)
                    }
                    if (call.referTo != "") {
                        callTitle.text = getString(R.string.transferring_call_to_dots)
                        callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(call.referTo),
                                Utils.aorDomain(ua.account.aor)))
                        transferButton.isEnabled = false
                    } else {
                        if (call.dir == "out")
                            callTitle.text = getString(R.string.outgoing_call_to_dots)
                        else
                            callTitle.text = getString(R.string.incoming_call_from_dots)
                        callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(call.peerUri),
                                Utils.aorDomain(ua.account.aor)))
                        transferButton.isEnabled = true
                    }
                    videoButton.setImageResource(R.drawable.video_on)
                    videoButton.visibility = View.VISIBLE
                    videoButton.isClickable = true
                    if (ua.account.mediaEnc == "") {
                        securityButton.visibility = View.INVISIBLE
                    } else {
                        securityButton.setImageResource(call.security)
                        setSecurityButtonTag(securityButton, call.security)
                        securityButton.visibility = View.VISIBLE
                    }
                    callButton.visibility = View.INVISIBLE
                    callVideoButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.VISIBLE
                    hangupButton.isEnabled = true
                    answerButton.visibility = View.INVISIBLE
                    answerVideoButton.visibility = View.INVISIBLE
                    rejectButton.visibility = View.INVISIBLE
                    if (call.onhold) {
                        holdButton.setImageResource(R.drawable.resume)
                    } else {
                        holdButton.setImageResource(R.drawable.hold)
                    }
                    dialpadButton.setImageResource(R.drawable.dialpad_on)
                    dialpadButton.tag = "on"
                    dialpadButton.isEnabled = false
                    infoButton.isEnabled = true
                    callControl.visibility = View.VISIBLE
                    if (call.held) {
                        imm.hideSoftInputFromWindow(dtmf.windowToken, 0)
                        dtmf.isEnabled = false
                        Handler(Looper.getMainLooper()).postDelayed({
                            onHoldNotice.visibility = View.VISIBLE
                        }, 250)
                    } else {
                        dtmf.isEnabled = true
                        dtmf.requestFocus()
                        onHoldNotice.visibility = View.GONE
                        if (resources.configuration.orientation == ORIENTATION_PORTRAIT)
                            imm.showSoftInput(dtmf, InputMethodManager.SHOW_IMPLICIT)
                        if (dtmfWatcher != null) dtmf.removeTextChangedListener(dtmfWatcher)
                        dtmfWatcher = call.dtmfWatcher
                        dtmf.addTextChangedListener(dtmfWatcher)
                    }
                }
            }
        }
    }

    private fun updateIcons(acc: Account) {
        if (acc.missedCalls)
            callsButton.setImageResource(R.drawable.calls_missed)
        else
            callsButton.setImageResource(R.drawable.calls)
        if (acc.unreadMessages)
            messagesButton.setImageResource(R.drawable.messages_unread)
        else
            messagesButton.setImageResource(R.drawable.messages)
        if (acc.vmUri != "") {
            if (acc.vmNew > 0)
                voicemailButton.setImageResource(R.drawable.voicemail_new)
            else
                voicemailButton.setImageResource(R.drawable.voicemail)
            voicemailButton.visibility = View.VISIBLE
        } else {
            voicemailButton.visibility = View.INVISIBLE
        }
    }

    private fun restoreActivities() {
        if (BaresipService.activities.isEmpty()) return
        Log.d(TAG, "Activity stack ${BaresipService.activities}")
        val activity = BaresipService.activities[0].split(",")
        BaresipService.activities.removeAt(0)
        when (activity[0]) {
            "main" -> {
                if ((Call.calls().size == 0) && (BaresipService.activities.size > 1))
                    restoreActivities()
            }
            "config" -> {
                configRequest.launch(Intent(this, ConfigActivity::class.java))
            }
            "audio" -> {
                startActivity(Intent(this, AudioActivity::class.java))
            }
            "accounts" -> {
                val i = Intent(this, AccountsActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                i.putExtras(b)
                accountsRequest.launch(i)
            }
            "account" -> {
                val i = Intent(this, AccountActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                i.putExtras(b)
                accountsRequest.launch(i)
            }
            "codecs" -> {
                val i = Intent(this, CodecsActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                b.putString("media", activity[2])
                i.putExtras(b)
                startActivity(i)
            }
            "about" -> {
                startActivity(Intent(this, AboutActivity::class.java))
            }
            "contacts" -> {
                val i = Intent(this, ContactsActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                i.putExtras(b)
                contactsRequest.launch(i)

            }
            "contact" -> {
                val i = Intent(this, ContactActivity::class.java)
                val b = Bundle()
                if (activity[1] == "true") {
                    b.putBoolean("new", true)
                    b.putString("uri", activity[2])
                } else {
                    b.putBoolean("new", false)
                    b.putInt("index", activity[2].toInt())
                }
                i.putExtras(b)
                startActivity(i)
            }
            "chats" -> {
                val i = Intent(this, ChatsActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                i.putExtras(b)
                chatRequests.launch(i)
            }
            "chat" -> {
                val i = Intent(this, ChatActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                b.putString("peer", activity[2])
                b.putBoolean("focus", activity[3] == "true")
                i.putExtras(b)
                chatRequests.launch(i)
            }
            "calls" -> {
                val i = Intent(this, CallsActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                i.putExtras(b)
                callsRequest.launch(i)
            }
        }
        return
    }

    private fun saveCallUri() {
        if (UserAgent.uas().isNotEmpty() && aorSpinner.selectedItemPosition >= 0) {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            if (Call.uaCalls(ua, "").size == 0)
                ua.account.resumeUri = callUri.text.toString()
            else
                ua.account.resumeUri = ""
        }
    }

    companion object {

        var accountRequest: ActivityResultLauncher<Intent>? = null
        var activityAor = ""
        // <aor, password> of those accounts that have auth username without auth password
        val aorPasswords = mutableMapOf<String, String>()

    }

    init {
        if (!BaresipService.libraryLoaded) {
            Log.d(TAG, "Loading baresip library")
            System.loadLibrary("baresip")
            BaresipService.libraryLoaded = true
        }
    }

}

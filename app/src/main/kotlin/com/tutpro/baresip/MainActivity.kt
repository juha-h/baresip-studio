package com.tutpro.baresip

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.*
import android.content.Intent.ACTION_CALL
import android.content.pm.PackageManager
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tutpro.baresip.Utils.showSnackBar
import com.tutpro.baresip.databinding.ActivityMainBinding
import java.io.File
import java.net.URLDecoder
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var layout: RelativeLayout
    private lateinit var callTitle: TextView
    private lateinit var callTimer: Chronometer
    private lateinit var callUri: AutoCompleteTextView
    private lateinit var securityButton: ImageButton
    private lateinit var diverter: LinearLayout
    private lateinit var diverterUri: TextView
    private lateinit var callButton: ImageButton
    private lateinit var hangupButton: ImageButton
    private lateinit var answerButton: ImageButton
    private lateinit var rejectButton: ImageButton
    private lateinit var callControl: RelativeLayout
    private lateinit var holdButton: ImageButton
    private lateinit var transferButton: ImageButton
    private lateinit var voicemailButton: ImageButton
    private lateinit var voicemailButtonSpace: Space
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
    private lateinit var screenEventReceiver: BroadcastReceiver
    private lateinit var serviceEventObserver: Observer<Event<Long>>
    private var recIcon: MenuItem? = null
    private var micIcon: MenuItem? = null
    private var speakerIcon: MenuItem? = null
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var accountsRequest: ActivityResultLauncher<Intent>
    private lateinit var chatRequests: ActivityResultLauncher<Intent>
    private lateinit var configRequest: ActivityResultLauncher<Intent>
    private lateinit var backupRequest: ActivityResultLauncher<Intent>
    private lateinit var restoreRequest: ActivityResultLauncher<Intent>
    private lateinit var contactsRequest: ActivityResultLauncher<Intent>
    private lateinit var callsRequest: ActivityResultLauncher<Intent>
    private lateinit var comDevChangedListener: AudioManager.OnCommunicationDeviceChangedListener
    private lateinit var permissions: Array<String>

    private var callHandler: Handler = Handler(Looper.getMainLooper())
    private var callRunnable: Runnable? = null
    private var downloadsInputUri: Uri? = null
    private var downloadsOutputUri: Uri? = null
    private var audioModeChangedListener: AudioManager.OnModeChangedListener? = null

    private lateinit var baresipService: Intent

    private var restart = false
    private var atStartup = false

    private var resumeUri = ""
    private var resumeUap = 0L
    private var resumeCall: Call? = null
    private var resumeAction = ""

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            moveTaskToBack(true)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        binding = ActivityMainBinding.inflate(layoutInflater)

        val extraAction = intent.getStringExtra("action")
        Log.d(TAG, "Main onCreate ${intent.action}/${intent.data}/$extraAction")

        window.addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)

        setContentView(binding.root)

        // Must be done after view has been created
        Utils.setShowWhenLocked(this, true)
        Utils.setTurnScreenOn(this, true)
        Utils.requestDismissKeyguard(this)

        setSupportActionBar(binding.toolbar)

        layout = binding.mainActivityLayout
        aorSpinner = binding.aorSpinner
        callTitle = binding.callTitle
        callTimer = binding.callTimer
        callUri = binding.callUri
        securityButton = binding.securityButton
        diverter = binding.diverter
        diverterUri = binding.diverterUri
        callButton = binding.callButton
        hangupButton = binding.hangupButton
        answerButton = binding.answerButton
        rejectButton = binding.rejectButton
        callControl = binding.callControl
        holdButton = binding.holdButton
        transferButton = binding.transferButton
        dtmf = binding.dtmf
        infoButton = binding.info
        onHoldNotice = binding.onHoldNotice
        voicemailButton = binding.voicemailButton
        voicemailButtonSpace = binding.voicemailButtonSpace
        contactsButton = binding.contactsButton
        messagesButton = binding.messagesButton
        callsButton = binding.callsButton
        dialpadButton = binding.dialpadButton
        swipeRefresh = binding.swipeRefresh

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        am = getSystemService(AUDIO_SERVICE) as AudioManager
        kgm = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        serviceEventObserver = Observer {
            val event = it.getContentIfNotHandled()
            Log.d(TAG, "Observed event $event")
            if (event != null && BaresipService.serviceEvents.isNotEmpty()) {
                val first = BaresipService.serviceEvents.first()
                handleServiceEvent(first.event, first.params)
            }
        }

        BaresipService.serviceEvent.observeForever(serviceEventObserver)

        screenEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(contxt: Context, intent: Intent) {
                if (kgm.isKeyguardLocked) {
                    Log.d(TAG, "Screen on when locked")
                    Utils.setShowWhenLocked(this@MainActivity, Call.inCall())
                }
            }
        }

        this.registerReceiver(screenEventReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
        })

        if (Build.VERSION.SDK_INT >= 31) {
            comDevChangedListener = AudioManager.OnCommunicationDeviceChangedListener { device ->
                if (device != null) {
                    Log.d(TAG, "Com device changed to type ${device.type} in mode ${am.mode}")
                    if (speakerIcon != null) {
                        if (Utils.isSpeakerPhoneOn(am))
                            speakerIcon!!.setIcon(R.drawable.speaker_on)
                        else
                            speakerIcon!!.setIcon(R.drawable.speaker_off)
                    }
                }
            }
            am.addOnCommunicationDeviceChangedListener(mainExecutor, comDevChangedListener)
        }

        uaAdapter = UaSpinnerAdapter(applicationContext, BaresipService.uas)
        aorSpinner.adapter = uaAdapter
        aorSpinner.setSelection(-1)
        aorSpinner.tag = ""
        aorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            // Have to allow NULL view, since sometimes when onItemSelected is called, view is NULL.
            // Haven't found any explanation why this can happen.
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                Log.d(TAG, "aorSpinner selecting $position")
                if (position < BaresipService.uas.size) {
                    val ua = BaresipService.uas[position]
                    val acc = ua.account
                    aorSpinner.tag = acc.aor
                    showCall(ua)
                    updateIcons(acc)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                Log.d(TAG, "Nothing selected")
            }
        }

        val registrationObserver = Observer<Long> { uaAdapter.notifyDataSetChanged() }
        BaresipService.registrationUpdate.observe(this, registrationObserver)

        accountsRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            uaAdapter.notifyDataSetChanged()
            spinToAor(activityAor)
            if (aorSpinner.tag != "")
                updateIcons(Account.ofAor(aorSpinner.tag.toString())!!)
            if (BaresipService.isServiceRunning) {
                baresipService.action = "Update Notification"
                startService(baresipService)
            }
        }

        accountRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            spinToAor(activityAor)
            val ua = UserAgent.ofAor(activityAor)!!
            updateIcons(ua.account)
            if (it.resultCode == Activity.RESULT_OK)
                if (BaresipService.aorPasswords[activityAor] == NO_AUTH_PASS)
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
                        BaresipService.uas[aorSpinner.selectedItemPosition].account.resumeUri =
                                callUri.text.toString()
                        false
                    }
                }
            } else {
                // view.performClick()
                false
            }
        }

        aorSpinner.setOnLongClickListener {
            if (aorSpinner.selectedItemPosition != -1) {
                val ua = UserAgent.ofAor(aorSpinner.tag.toString())
                if (ua != null) {
                    val acc = ua.account
                    if (Api.account_regint(acc.accp) > 0) {
                        Api.account_set_regint(acc.accp, 0)
                        Api.ua_unregister(ua.uap)
                    } else {
                        Api.account_set_regint(acc.accp, acc.configuredRegInt)
                        Api.ua_register(ua.uap)
                    }
                    acc.regint = Api.account_regint(acc.accp)
                    AccountsActivity.saveAccounts()
                }
            }
            true
        }

        callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                Contact.contactNames()))
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
                R.drawable.unlocked -> {
                    Utils.alertView(this, getString(R.string.alert),
                            getString(R.string.call_not_secure))
                }
                R.drawable.locked_yellow -> {
                    Utils.alertView(this, getString(R.string.alert),
                            getString(R.string.peer_not_verified))
                }
                R.drawable.locked_green -> {
                    with(MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)) {
                        setTitle(R.string.info)
                        setMessage(getString(R.string.call_is_secure))
                        setPositiveButton(getString(R.string.unverify)) { dialog, _ ->
                            val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
                            val call = ua.currentCall()
                            if (call != null) {
                                if (Api.cmd_exec("zrtp_unverify " + call.zid) != 0) {
                                    Log.e(TAG, "Command 'zrtp_unverify ${call.zid}' failed")
                                } else {
                                    securityButton.setImageResource(R.drawable.locked_yellow)
                                    securityButton.tag = R.drawable.locked_yellow
                                }
                            }
                            dialog.dismiss()
                        }
                        setNeutralButton(getString(R.string.cancel)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        show()
                    }
                }
            }
        }

        callButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                if (Utils.checkPermissions(this, arrayOf(RECORD_AUDIO)))
                        makeCall()
                else
                    Toast.makeText(applicationContext, R.string.no_calls, Toast.LENGTH_SHORT).show()
            }
        }

        hangupButton.setOnClickListener {
            val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
            if (Build.VERSION.SDK_INT < 31) {
                if (callRunnable != null) {
                    callHandler.removeCallbacks(callRunnable!!)
                    callRunnable = null
                    BaresipService.abandonAudioFocus(applicationContext)
                    am.mode = AudioManager.MODE_NORMAL
                    showCall(ua)
                    return@setOnClickListener
                }
            } else {
                if (audioModeChangedListener != null) {
                    am.removeOnModeChangedListener(audioModeChangedListener!!)
                    audioModeChangedListener = null
                    BaresipService.abandonAudioFocus(applicationContext)
                    am.mode = AudioManager.MODE_NORMAL
                    showCall(ua)
                    return@setOnClickListener
                }
            }
            val aor = ua.account.aor
            val uaCalls = ua.calls()
            if (uaCalls.size > 0) {
                val call = uaCalls[uaCalls.size - 1]
                val callp = call.callp
                Log.d(TAG, "AoR $aor hanging up call $callp with ${callUri.text}")
                hangupButton.isEnabled = false
                if (call.status == "answered")
                    call.rejected = true
                Api.ua_hangup(ua.uap, callp, 0, "")
            }
        }

        answerButton.setOnClickListener {
            val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
            val call = ua.currentCall() ?: return@setOnClickListener
            Log.d(TAG, "AoR ${ua.account.aor} answering call from ${callUri.text}")
            answerButton.isEnabled = false
            rejectButton.isEnabled = false
            val intent = Intent(this@MainActivity, BaresipService::class.java)
            intent.action = "Call Answer"
            intent.putExtra("uap", ua.uap)
            intent.putExtra("callp", call.callp)
            intent.putExtra("video", Api.VIDMODE_OFF)
            startService(intent)
        }

        rejectButton.setOnClickListener {
            val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val call = ua.currentCall()!!
            val callp = call.callp
            Log.d(TAG, "AoR $aor rejecting call $callp from ${callUri.text}")
            answerButton.isEnabled = false
            rejectButton.isEnabled = false
            call.rejected = true
            Api.ua_hangup(ua.uap, callp, 486, "Busy Here")
        }

        holdButton.setOnClickListener {
            val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val call = ua.currentCall()!!
            if (call.onhold) {
                Log.d(TAG, "AoR $aor resuming call ${call.callp} with ${callUri.text}")
                call.resume()
                call.onhold = false
                holdButton.setImageResource(R.drawable.call_hold)
            } else {
                Log.d(TAG, "AoR $aor holding call ${call.callp} with ${callUri.text}")
                call.hold()
                call.onhold = true
                holdButton.setImageResource(R.drawable.resume)
            }
        }

        transferButton.setOnClickListener {
            val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
            val call = ua.currentCall()
            if (call != null ) {
                if (call.onHoldCall != null) {
                    if (!call.executeTransfer())
                        Utils.alertView(this@MainActivity, getString(R.string.notice),
                                String.format(getString(R.string.transfer_failed)))
                } else {
                    makeTransfer(ua)
                }
            }
        }

        infoButton.setOnClickListener {
            val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
            val call = ua.currentCall()
            if (call != null) {
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
                val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
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
                    with(MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)) {
                        setTitle(R.string.voicemail_messages)
                        setMessage(acc.vmMessages(this@MainActivity))
                        setPositiveButton(getString(R.string.listen), dialogClickListener)
                        setNeutralButton(getString(R.string.cancel), dialogClickListener)
                        show()
                    }
                }
            }
        }

        contactsRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                    Contact.contactNames()))
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
            } else {
                callUri.inputType = InputType.TYPE_CLASS_TEXT +
                        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                dialpadButton.setImageResource(R.drawable.dialpad_off)
                dialpadButton.tag = "off"
            }
        }

        configRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if ((it.data != null) && it.data!!.hasExtra("restart")) {
                with(MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)) {
                    setTitle(R.string.restart_request)
                    setMessage(getString(R.string.config_restart))
                    setPositiveButton(getText(R.string.restart)) { dialog, _ ->
                        dialog.dismiss()
                        quitRestart(true)
                    }
                    setNeutralButton(getText(R.string.cancel)) { dialog, _ ->
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
                    downloadsOutputUri = uri
                    askPassword(getString(R.string.encrypt_password))
                }
        }

        restoreRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK)
                it.data?.data?.also { uri ->
                    downloadsInputUri = uri
                    askPassword(getString(R.string.decrypt_password))
                }
        }

        swipeRefresh.setOnTouchListener(object : OnSwipeTouchListener(this@MainActivity) {

            override fun onSwipeLeft() {
                super.onSwipeLeft()
                if (BaresipService.uas.size > 0) {
                    val curPos = aorSpinner.selectedItemPosition
                    val newPos = if (curPos == -1)
                        0
                    else
                        (curPos + 1) % BaresipService.uas.size
                    if (curPos != newPos) {
                        aorSpinner.setSelection(newPos)
                        showCall(BaresipService.uas[newPos])
                    }
                }
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                if (BaresipService.uas.size > 0) {
                    val curPos = aorSpinner.selectedItemPosition
                    val newPos = when (curPos) {
                        -1 -> 0
                        0 -> BaresipService.uas.size - 1
                        else -> curPos - 1
                    }
                    if (curPos != newPos) {
                        aorSpinner.setSelection(newPos)
                        showCall(BaresipService.uas[newPos])
                    }
                }
            }
        })

        swipeRefresh.setOnRefreshListener {
            if (BaresipService.uas.size > 0) {
                if (aorSpinner.selectedItemPosition == -1)
                    aorSpinner.setSelection(0)
                val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
                if (ua.account.regint > 0)
                    Api.ua_register(ua.uap)
            }
            swipeRefresh.isRefreshing = false
        }

        baresipService = Intent(this@MainActivity, BaresipService::class.java)

        atStartup = intent.hasExtra("onStartup")

        if (intent?.action == ACTION_CALL) {
            if (BaresipService.isServiceRunning)
                callAction(intent)
            else
                BaresipService.callActionUri = URLDecoder.decode(intent.data.toString(), "UTF-8")
        }

        permissions = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(POST_NOTIFICATIONS, RECORD_AUDIO, BLUETOOTH_CONNECT)
        else if (Build.VERSION.SDK_INT >= 31)
            arrayOf(RECORD_AUDIO, BLUETOOTH_CONNECT)
        else
            arrayOf(RECORD_AUDIO)

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

        requestPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                val denied = mutableListOf<String>()
                val shouldShow = mutableListOf<String>()
                it.forEach { permission ->
                    if (!permission.value) {
                        denied.add(permission.key)
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                                permission.key))
                            shouldShow.add(permission.key)
                    }
                }
                if (denied.contains(POST_NOTIFICATIONS) &&
                        !shouldShow.contains(POST_NOTIFICATIONS)) {
                    with(MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)) {
                        setTitle(getString(R.string.notice))
                        setMessage(getString(R.string.no_notifications))
                        setPositiveButton(getString(R.string.ok)) { _, _ ->
                            quitRestart(false)
                        }
                        show()
                    }
                } else {
                    if (shouldShow.isNotEmpty())
                        Utils.alertView(this, getString(R.string.permissions_rationale),
                            getString(R.string.audio_permissions)
                        ) { requestPermissionsLauncher.launch(permissions) }
                    else
                        startBaresip()
                }
            }

        if (!BaresipService.isServiceRunning) {
            if (File(filesDir.absolutePath + "/accounts").exists()) {
                val accounts = String(
                    Utils.getFileContents(filesDir.absolutePath + "/accounts")!!,
                    Charsets.UTF_8
                ).lines().toMutableList()
                askPasswords(accounts)
            } else {
                // Baresip is started for the first time
                requestPermissionsLauncher.launch(permissions)
            }
        }

        if (Preferences(applicationContext).displayTheme != AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.setDefaultNightMode(Preferences(applicationContext).displayTheme)
            delegate.applyDayNight()
        }

    } // OnCreate

    override fun onStart() {
        super.onStart()
        Log.e(TAG, "Main onStart")
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
                callUri.setText(BaresipService.uas[aorSpinner.selectedItemPosition].account.resumeUri)
                callButton.performClick()
            }
            "call transfer", "transfer show", "transfer accept" ->
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
                    if (BaresipService.uas.size > 0) {
                        if (aorSpinner.selectedItemPosition == -1) {
                            if (Call.inCall())
                                spinToAor(Call.calls()[0].ua.account.aor)
                            else {
                                aorSpinner.setSelection(0)
                                aorSpinner.tag = BaresipService.uas[0].account.aor
                            }
                        }
                    }
                }
                uaAdapter.notifyDataSetChanged()
                if (BaresipService.uas.size > 0) {
                    val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
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
        callTimer.stop()
        saveCallUri()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "Main onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Main onDestroy")
        this.unregisterReceiver(screenEventReceiver)
        if (Build.VERSION.SDK_INT >= 31)
            am.removeOnCommunicationDeviceChangedListener(comDevChangedListener)
        BaresipService.serviceEvent.removeObserver(serviceEventObserver)
        BaresipService.serviceEvents.clear()
        BaresipService.activities.clear()
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
            callAction(intent)
        } else {
            val action = intent.getStringExtra("action")
            Log.d(TAG, "onNewIntent action `$action'")
            if (action != null) {
                intent.removeExtra("action")
                handleIntent(intent, action)
            }
        }
    }

    private fun callAction(intent: Intent) {
        if (Call.inCall() || BaresipService.uas.size == 0)
            return
        val uri: Uri? = intent.data
        if (uri != null) {
            when (uri.scheme) {
                "sip" -> {
                    val uriStr = URLDecoder.decode(uri.toString(), "UTF-8")
                    var ua = UserAgent.ofDomain(Utils.uriHostPart(uriStr))
                    if (ua == null && BaresipService.uas.size > 0)
                        ua = BaresipService.uas[0]
                    if (ua == null) {
                        Log.w(TAG, "No accounts for '$uriStr'")
                        return
                    }
                    spinToAor(ua.account.aor)
                    resumeAction = "call"
                    ua.account.resumeUri = uriStr
                }
                "tel" -> {
                    val uriStr = URLDecoder.decode(uri.toString(), "UTF-8")
                            .filterNot{setOf('-', ' ', '(', ')').contains(it)}
                    var account: Account? = null
                    for (a in Account.accounts())
                        if (a.telProvider != "") {
                            account = a
                            break
                        }
                    if (account == null) {
                        Log.w(TAG, "No telephony providers for '$uriStr'")
                        return
                    }
                    spinToAor(account.aor)
                    resumeAction = "call"
                    account.resumeUri = uriStr
                }
                else -> {
                    Log.w(TAG, "Unsupported URI scheme ${uri.scheme}")
                    return
                }
            }
        }
    }

    private fun handleIntent(intent: Intent, action: String) {
        Log.d(TAG, "Handling intent '$action'")
        val ev = action.split(",")
        when (ev[0]) {
            "accounts" -> {
                resumeAction = "accounts"
            }
            "no network" -> {
                Utils.alertView(this, getString(R.string.notice),
                    getString(R.string.no_network))
                return
            }
            "call" -> {
                if (Call.inCall()) {
                    Toast.makeText(applicationContext, getString(R.string.call_already_active),
                            Toast.LENGTH_SHORT).show()
                    return
                }
                val uap = intent.getLongExtra("uap", 0L)
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
                val callp = intent.getLongExtra("callp", 0L)
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
                val uap = intent.getLongExtra("uap", 0L)
                val ua = UserAgent.ofUap(uap)
                if (ua == null) {
                    Log.w(TAG, "handleIntent did not find ua $uap")
                    return
                }
                if (ua.account.aor != aorSpinner.tag)
                    spinToAor(ua.account.aor)
                resumeAction = action
            }
            "call transfer", "transfer show", "transfer accept" -> {
                val callp = intent.getLongExtra("callp", 0L)
                val call = Call.ofCallp(callp)
                if (call == null) {
                    Log.w(TAG, "handleIntent '$action' did not find call $callp")
                    moveTaskToBack(true)
                    return
                }
                resumeAction = ev[0]
                resumeCall = call
                resumeUri = if (ev[0] == "call transfer")
                    ev[1]
                else
                    intent.getStringExtra("uri")!!
            }
            "message", "message show", "message reply" -> {
                val uap = intent.getLongExtra("uap", 0L)
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
                am.adjustStreamVolume(stream,
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                            AudioManager.ADJUST_LOWER else
                            AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI)
                Log.d(TAG, "Adjusted volume $keyCode of stream $stream to ${am.getStreamVolume(stream)}")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleServiceEvent(event: String, params: ArrayList<Any>) {

        fun handleNextEvent(logMessage: String? = null) {
            if (logMessage != null)
                Log.w(TAG, logMessage)
            if (BaresipService.serviceEvents.isNotEmpty()) {
                BaresipService.serviceEvents.removeFirst()
                if (BaresipService.serviceEvents.isNotEmpty()) {
                    val e = BaresipService.serviceEvents.first()
                    handleServiceEvent(e.event, e.params)
                }
            }
        }

        if (taskId == -1) {
            handleNextEvent("Omit service event '$event' for task -1")
            return
        }

        if (event == "started") {
            val callActionUri = params[0] as String
            Log.d(TAG, "Handling service event 'started' with '$callActionUri'")
            if (!this::uaAdapter.isInitialized) {
                // Android has restarted baresip when permission has been denied in app settings
                recreate()
                return
            }
            uaAdapter.notifyDataSetChanged()
            if (callActionUri != "") {
                var ua = UserAgent.ofDomain(Utils.uriHostPart(callActionUri))
                if (ua == null)
                    if (BaresipService.uas.size > 0) {
                        ua = BaresipService.uas[0]
                    } else {
                        handleNextEvent("No UAs to make the call to '$callActionUri'")
                        return
                    }
                spinToAor(ua.account.aor)
                ua.account.resumeUri = callActionUri
                callUri.setText(callActionUri)
                callButton.performClick()
            } else {
                if ((aorSpinner.selectedItemPosition == -1) && (BaresipService.uas.size > 0)) {
                    aorSpinner.setSelection(0)
                    aorSpinner.tag = BaresipService.uas[0].account.aor
                }
            }
            handleNextEvent()
            return
        }

        if (event == "stopped") {
            Log.d(TAG, "Handling service event 'stopped' with start error '${params[0]}'")
            if (params[0] != "") {
                Utils.alertView(this, getString(R.string.notice), getString(R.string.start_failed))
            } else {
                finishAndRemoveTask()
                if (restart)
                    reStart()
                else
                    exitProcess(0)
            }
            return
        }

        val uap = params[0] as Long
        val ua = UserAgent.ofUap(uap)
        if (ua == null) {
            handleNextEvent("handleServiceEvent '$event' did not find ua $uap")
            return
        }

        val ev = event.split(",")
        Log.d(TAG, "Handling service event '${ev[0]}' for $uap")
        val acc = ua.account
        val aor = ua.account.aor

        when (ev[0]) {
            "call rejected" -> {
                if (aor == aorSpinner.tag) {
                    callsButton.setImageResource(R.drawable.calls_missed)
                }
            }
            "call incoming", "call outgoing" -> {
                val callp = params[1] as Long
                if (BaresipService.isMainVisible) {
                    uaAdapter.notifyDataSetChanged()
                    if (aor != aorSpinner.tag)
                        spinToAor(aor)
                    showCall(ua, Call.ofCallp(callp))
                } else {
                    Log.d(TAG, "Reordering to front")
                    BaresipService.activities.clear()
                    BaresipService.serviceEvents.clear()
                    val i = Intent(applicationContext, MainActivity::class.java)
                    i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    i.putExtra("action", "call show")
                    i.putExtra("callp", callp)
                    startActivity(i)
                    return
                }
            }
            "call answered" -> {
                showCall(ua)
            }
            "call established" -> {
                if (aor == aorSpinner.tag) {
                    dtmf.setText("")
                    dtmf.hint = getString(R.string.dtmf)
                    showCall(ua)
                }
            }
            "call update" -> {
                showCall(ua)
            }
            "call verify" -> {
                val callp = params[1] as Long
                val call = Call.ofCallp(callp)
                if (call == null) {
                    handleNextEvent("Call $callp to be verified is not found")
                    return
                }
                with(MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)) {
                    setTitle(R.string.verify)
                    setMessage(String.format(getString(R.string.verify_sas), ev[1]))
                    setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                        call.security = if (Api.cmd_exec("zrtp_verify ${ev[2]}") != 0) {
                            Log.e(TAG, "Command 'zrtp_verify ${ev[2]}' failed")
                            R.drawable.locked_yellow
                        } else {
                            R.drawable.locked_green
                        }
                        call.zid = ev[2]
                        if (aor == aorSpinner.tag) {
                            securityButton.tag = call.security
                            securityButton.setImageResource(call.security)
                        }
                        dialog.dismiss()
                    }
                    setNeutralButton(getString(R.string.no)) { dialog, _ ->
                        call.security = R.drawable.locked_yellow
                        call.zid = ev[2]
                        if (aor == aorSpinner.tag) {
                            securityButton.tag = R.drawable.locked_yellow
                            securityButton.setImageResource(R.drawable.locked_yellow)
                        }
                        dialog.dismiss()
                    }
                    show()
                }
            }
            "call verified", "call secure" -> {
                val callp = params[1] as Long
                val call = Call.ofCallp(callp)
                if (call == null) {
                    handleNextEvent("Call $callp that is verified is not found")
                    return
                }
                if (aor == aorSpinner.tag) {
                    securityButton.setImageResource(call.security)
                    securityButton.tag = call.security
                }
            }
            "call transfer", "transfer show" -> {
                val callp = params[1] as Long
                if (!BaresipService.isMainVisible) {
                    Log.d(TAG, "Reordering to front")
                    BaresipService.activities.clear()
                    BaresipService.serviceEvents.clear()
                    val i = Intent(applicationContext, MainActivity::class.java)
                    i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    i.putExtra("action", event)
                    i.putExtra("callp", callp)
                    startActivity(i)
                    return
                }
                val call = Call.ofCallp(callp)!!
                val target = Utils.friendlyUri(this, ev[1], acc)
                with(MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)) {
                    setTitle(R.string.transfer_request)
                    setMessage(String.format(getString(R.string.transfer_request_query),
                            target))
                    setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                        if (call in Call.calls())
                            acceptTransfer(ua, call, ev[1])
                        dialog.dismiss()
                    }
                    setNeutralButton(getString(R.string.no)) { dialog, _ ->
                        if (call in Call.calls())
                            call.notifySipfrag(603, "Decline")
                        dialog.dismiss()
                    }
                    show()
                }
            }
            "transfer accept" -> {
                val callp = params[1] as Long
                val call = Call.ofCallp(callp)
                if (call in Call.calls())
                    Api.ua_hangup(uap, callp, 0, "")
                call(ua, ev[1])
                showCall(ua)
            }
            "transfer failed" -> {
                showCall(ua)
            }
            "call closed" -> {
                uaAdapter.notifyDataSetChanged()
                val call = ua.currentCall()
                if (call != null) {
                    call.resume()
                    startCallTimer(call)
                } else {
                    callTimer.stop()
                }
                if (BaresipService.isRecOn) {
                    Api.module_unload("sndfile")
                    BaresipService.isRecOn = false
                    recIcon!!.setIcon(R.drawable.rec_off)
                }
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
                if ((Build.VERSION.SDK_INT >= 22 && kgm.isDeviceLocked) ||
                        (Build.VERSION.SDK_INT < 22 && kgm.isKeyguardLocked && kgm.isKeyguardSecure))
                    Utils.setShowWhenLocked(this, false)
            }
            "message", "message show", "message reply" -> {
                val i = Intent(applicationContext, ChatActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                val b = Bundle()
                b.putString("aor", aor)
                b.putString("peer", params[1] as String)
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

        handleNextEvent()
    }

    private fun reStart() {
        Log.d(TAG, "Trigger restart")
        val pm = applicationContext.packageManager
        val intent = pm.getLaunchIntentForPackage(this.packageName)
        this.startActivity(intent)
        exitProcess(0)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menuInflater.inflate(R.menu.main_menu, menu)

        menuInflater.inflate(R.menu.rec_icon, menu)
        recIcon = menu.findItem(R.id.recIcon)
        if (BaresipService.isRecOn)
            recIcon!!.setIcon(R.drawable.rec_on)
        else
            recIcon!!.setIcon(R.drawable.rec_off)

        menuInflater.inflate(R.menu.mic_icon, menu)
        micIcon = menu.findItem(R.id.micIcon)
        if (BaresipService.isMicMuted)
            micIcon!!.setIcon(R.drawable.mic_off)
        else
            micIcon!!.setIcon(R.drawable.mic_on)

        menuInflater.inflate(R.menu.speaker_icon, menu)
        speakerIcon = menu.findItem(R.id.speakerIcon)
        if (Utils.isSpeakerPhoneOn(am))
            speakerIcon!!.setIcon(R.drawable.speaker_on)
        else
            speakerIcon!!.setIcon(R.drawable.speaker_off)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.recIcon -> {
                if (Call.call("connected") == null) {
                    BaresipService.isRecOn = !BaresipService.isRecOn
                    if (BaresipService.isRecOn) {
                        item.setIcon(R.drawable.rec_on)
                        Api.module_load("sndfile")
                    } else {
                        item.setIcon(R.drawable.rec_off)
                        Api.module_unload("sndfile")
                    }
                } else {
                    Toast.makeText(
                        applicationContext,
                        R.string.rec_in_call,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

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
                if (Build.VERSION.SDK_INT >= 31)
                    Log.d(
                        TAG, "Toggling speakerphone when dev/mode is " +
                                "${am.communicationDevice!!.type}/${am.mode}"
                    )
                Utils.toggleSpeakerPhone(ContextCompat.getMainExecutor(this), am)
                if (speakerIcon != null) {
                    if (Utils.isSpeakerPhoneOn(am))
                        speakerIcon!!.setIcon(R.drawable.speaker_on)
                    else
                        speakerIcon!!.setIcon(R.drawable.speaker_off)
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
                        WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        Log.d(TAG, "Write External Storage permission granted")
                        val path = Utils.downloadsPath("baresip.bs")
                        downloadsOutputUri = File(path).toUri()
                        askPassword(getString(R.string.encrypt_password))
                    }
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        WRITE_EXTERNAL_STORAGE) -> {
                        layout.showSnackBar(
                            binding.root,
                            getString(R.string.no_backup),
                            Snackbar.LENGTH_INDEFINITE,
                            getString(R.string.ok)
                        ) {
                            requestPermissionLauncher.launch(WRITE_EXTERNAL_STORAGE)
                        }
                    }
                    else -> {
                        requestPermissionLauncher.launch(WRITE_EXTERNAL_STORAGE)
                    }
                }
            }

            R.id.restore -> {
                when {
                    Build.VERSION.SDK_INT >= 29 ->
                        pickupFileFromDownloads("restore")
                    ContextCompat.checkSelfPermission(
                        this,
                        READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        Log.d(TAG, "Read External Storage permission granted")
                        val path = Utils.downloadsPath("baresip.bs")
                        downloadsInputUri = File(path).toUri()
                        askPassword(getString(R.string.decrypt_password))
                    }
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        READ_EXTERNAL_STORAGE) -> {
                        layout.showSnackBar(
                            binding.root,
                            getString(R.string.no_restore),
                            Snackbar.LENGTH_INDEFINITE,
                            getString(R.string.ok)
                        ) {
                            requestPermissionLauncher.launch(READ_EXTERNAL_STORAGE)
                        }
                    }
                    else -> {
                        requestPermissionLauncher.launch(READ_EXTERNAL_STORAGE)
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

    @RequiresApi(29)
    private fun pickupFileFromDownloads(action: String) {
        when (action) {
            "backup" -> {
                backupRequest.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, "baresip.bs")
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

    private fun quitRestart(reStart: Boolean) {
        Log.d(TAG, "quitRestart Restart = $reStart")
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        if (BaresipService.isServiceRunning) {
            restart = reStart
            baresipService.action = "Stop"
            startService(baresipService)
        } else {
            finishAndRemoveTask()
            if (reStart)
                reStart()
            else
                exitProcess(0)
        }
    }

    private fun makeTransfer(ua: UserAgent) {

        val layout = LayoutInflater.from(this)
                .inflate(R.layout.call_transfer_dialog, findViewById(android.R.id.content), false)
        val blind = layout.findViewById(R.id.blind) as CheckBox
        val attended = layout.findViewById(R.id.attended) as CheckBox

        val transferUri = layout.findViewById(R.id.transferUri) as AutoCompleteTextView
        transferUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                Contact.contactNames()))
        transferUri.threshold = 2
        transferUri.requestFocus()

        val builder = MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
        with(builder) {
            setView(layout)
            setPositiveButton(R.string.transfer) { dialog, _ ->
                imm.hideSoftInputFromWindow(transferUri.windowToken, 0)
                dialog.dismiss()
                var uriText = transferUri.text.toString().trim()
                if (uriText.isNotEmpty()) {
                    val uris = Contact.contactUris(uriText)
                    if (uris.size > 1) {
                        val destinationBuilder = MaterialAlertDialogBuilder(
                            this@MainActivity,
                            R.style.AlertDialogTheme
                        )
                        with(destinationBuilder) {
                            setTitle(R.string.choose_destination_uri)
                            setItems(uris.toTypedArray()) { _, which ->
                                uriText = uris[which]
                                transfer(
                                    ua,
                                    if (Utils.isTelNumber(uriText)) "tel:$uriText" else uriText,
                                    attended.isChecked
                                )
                            }
                            setNeutralButton(getString(R.string.cancel)) { _: DialogInterface, _: Int -> }
                            show()
                        }
                    } else {
                        if (uris.size == 1)
                            uriText = uris[0]
                    }
                    transfer(ua, if (Utils.isTelNumber(uriText)) "tel:$uriText" else uriText,
                        attended.isChecked)
                }
            }
            setNeutralButton(android.R.string.cancel) { dialog, _ ->
                imm.hideSoftInputFromWindow(transferUri.windowToken, 0)
                dialog.cancel()
            }
        }
        val alertDialog = builder.create()

        val call = ua.currentCall() ?: return
        val blindOrAttended = layout.findViewById(R.id.blindOrAttended) as RelativeLayout
        if (call.replaces()) {
            blind.setOnClickListener {
                if (blind.isChecked) {
                    attended.isChecked = false
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).text = getString(R.string.transfer)
                }
            }
            attended.setOnClickListener {
                if (attended.isChecked) {
                    blind.isChecked = false
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).text = getString(R.string.call)
                }
            }
            blindOrAttended.visibility = View.VISIBLE
        } else {
            blindOrAttended.visibility = View.GONE
        }

        // This needs to be done after dialog has been created and before it is shown
        alertDialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        alertDialog.show()
    }

    private fun transfer(ua: UserAgent, uriText: String, attended: Boolean) {
        val uri = if (Utils.isTelUri(uriText))
            Utils.telToSip(uriText, ua.account)
        else
            Utils.uriComplete(uriText, ua.account.aor)
        if (!Utils.checkUri(uri)) {
            Utils.alertView(this@MainActivity, getString(R.string.notice),
                String.format(getString(R.string.invalid_sip_or_tel_uri), uri))
        } else {
            val call = ua.currentCall()
            if (call != null) {
                if (attended) {
                    if (call.hold()) {
                        call.referTo = uri
                        call(ua, uri, call)
                    }
                } else {
                    if (!call.transfer(uri)) {
                        Utils.alertView(this@MainActivity, getString(R.string.notice),
                            String.format(getString(R.string.transfer_failed)))
                    }
                }
                showCall(ua)
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
        with (MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)) {
            setView(layout)
            setPositiveButton(android.R.string.ok) { dialog, _ ->
                imm.hideSoftInputFromWindow(input.windowToken, 0)
                dialog.dismiss()
                var password = input.text.toString().trim()
                if (!Account.checkAuthPass(password)) {
                    Toast.makeText(
                        applicationContext,
                        String.format(getString(R.string.invalid_authentication_password), password),
                        Toast.LENGTH_SHORT
                    ).show()
                    password = ""
                }
                when (title) {
                    getString(R.string.encrypt_password) ->
                        if (password != "") backup(password)
                    getString(R.string.decrypt_password) ->
                        if (password != "") restore(password)
                    else ->
                        if (password == "") {
                            askPassword(title, ua!!)
                        } else {
                            Api.account_set_auth_pass(ua!!.account.accp, password)
                            ua.account.authPass =  Api.account_auth_pass(ua.account.accp)
                            BaresipService.aorPasswords[ua.account.aor] = ua.account.authPass
                            if (ua.account.regint == 0)
                                Api.ua_unregister(ua.uap)
                            else
                                Api.ua_register(ua.uap)
                        }
                }
            }
            setNeutralButton(android.R.string.cancel) { dialog, _ ->
                imm.hideSoftInputFromWindow(input.windowToken, 0)
                dialog.cancel()
            }
            val dialog = this.create()
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
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
                with (MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)) {
                    setView(layout)
                    setPositiveButton(android.R.string.ok) { dialog, _ ->
                        imm.hideSoftInputFromWindow(input.windowToken, 0)
                        dialog.dismiss()
                        val password = input.text.toString().trim()
                        if (!Account.checkAuthPass(password)) {
                            Toast.makeText(
                                applicationContext,
                                String.format(getString(R.string.invalid_authentication_password),
                                    password),
                                Toast.LENGTH_SHORT
                            ).show()
                            accounts.add(0, account)
                        } else {
                            BaresipService.aorPasswords[aor] = password
                        }
                        askPasswords(accounts)
                    }
                    setNeutralButton(android.R.string.cancel) { dialog, _ ->
                        imm.hideSoftInputFromWindow(input.windowToken, 0)
                        dialog.cancel()
                        BaresipService.aorPasswords[aor] = NO_AUTH_PASS
                        askPasswords(accounts)
                    }
                    val dialog = this.create()
                    dialog.setCancelable(false)
                    dialog.setCanceledOnTouchOutside(false)
                    dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    dialog.show()
                }
            } else {
                askPasswords(accounts)
            }
        } else {
            requestPermissionsLauncher.launch(permissions)
        }
    }

    private fun startBaresip() {
        baresipService.action = "Start"
        startService(baresipService)
        if (atStartup)
            moveTaskToBack(true)
    }

    private fun backup(password: String) {
        val files = arrayListOf("accounts", "history", "config", "contacts", "messages", "uuid",
                "gzrtp.zid", "cert.pem", "ca_cert", "ca_certs.crt")
        File(BaresipService.filesPath).walk().forEach {
            if (it.name.endsWith(".png"))
                files.add(it.name)
        }
        File("${BaresipService.filesPath}/recordings").walk().forEach {
            if (it.name.startsWith("dump"))
                files.add("recordings/${it.name}")
        }
        val zipFile = getString(R.string.app_name) + ".zip"
        val zipFilePath = BaresipService.filesPath + "/$zipFile"
        if (!Utils.zip(files, zipFile)) {
            Log.w(TAG, "Failed to write zip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.backup_failed),
                            Utils.fileNameOfUri(applicationContext, downloadsOutputUri!!)))
            return
        }
        val content = Utils.getFileContents(zipFilePath)
        if (content == null) {
            Log.w(TAG, "Failed to read zip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.backup_failed),
                            Utils.fileNameOfUri(applicationContext, downloadsOutputUri!!)))
            return
        }
        if (!Utils.encryptToUri(applicationContext, downloadsOutputUri!!, content, password)) {
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.backup_failed),
                            Utils.fileNameOfUri(applicationContext, downloadsOutputUri!!)))
            return
        }
        Utils.alertView(this, getString(R.string.info),
                String.format(getString(R.string.backed_up),
                        Utils.fileNameOfUri(applicationContext, downloadsOutputUri!!)))
        Utils.deleteFile(File(zipFilePath))
    }

    private fun restore(password: String) {
        val zipFile = getString(R.string.app_name) + ".zip"
        val zipFilePath = BaresipService.filesPath + "/$zipFile"
        val zipData = Utils.decryptFromUri(applicationContext, downloadsInputUri!!, password)
        if (zipData == null) {
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.restore_failed),
                            Utils.fileNameOfUri(applicationContext, downloadsInputUri!!)))
            return
        }
        if (!Utils.putFileContents(zipFilePath, zipData)) {
            Log.w(TAG, "Failed to write zip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.restore_failed),
                            Utils.fileNameOfUri(applicationContext, downloadsInputUri!!)))
            return
        }
        if (!Utils.unZip(zipFilePath)) {
            Log.w(TAG, "Failed to unzip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.restore_failed),
                            Utils.fileNameOfUri(applicationContext, downloadsInputUri!!)))
            return
        }
        Utils.deleteFile(File(zipFilePath))
        with(MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)) {
            setTitle(getString(R.string.info))
            setMessage(getString(R.string.restored))
            setPositiveButton(getText(R.string.restart)) { dialog, _ ->
                quitRestart(true)
                dialog.dismiss()
            }
            setNeutralButton(getText(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            show()
        }
    }

    private fun spinToAor(aor: String) {
        for (accountIndex in BaresipService.uas.indices)
            if (BaresipService.uas[accountIndex].account.aor == aor) {
                aorSpinner.setSelection(accountIndex)
                aorSpinner.tag = aor
                return
            }
        if (BaresipService.uas.isNotEmpty()) {
            aorSpinner.setSelection(0)
            aorSpinner.tag = BaresipService.uas[0].account.aor
        } else {
            aorSpinner.setSelection(-1)
            aorSpinner.tag = ""
        }

    }

    private fun call(ua: UserAgent, uri: String, onHoldCall: Call? = null): Boolean {
        if (ua.account.aor != aorSpinner.tag)
            spinToAor(ua.account.aor)
        val callp = ua.callAlloc(0L, Api.VIDMODE_OFF)
        return if (callp != 0L) {
            Log.d(TAG, "Adding outgoing call ${ua.uap}/$callp/$uri")
            val call = Call(callp, ua, uri, "out", "outgoing", Utils.dtmfWatcher(callp))
            call.onHoldCall = onHoldCall
            call.add()
            if (onHoldCall != null)
                onHoldCall.newCall = call
            if (call.connect(uri)) {
                showCall(ua)
                true
            } else {
                Log.w(TAG, "call_connect $callp failed")
                if (onHoldCall != null)
                    onHoldCall.newCall = null
                call.remove()
                call.destroy()
                if (!BaresipService.abandonAudioFocus(applicationContext))
                    Log.e(TAG, "Failed to abandon audio focus")
                showCall(ua)
                false
            }
        } else {
            Log.w(TAG, "callAlloc for ${ua.uap} to $uri failed")
            false
        }
    }

    private fun acceptTransfer(ua: UserAgent, call: Call, uri: String) {
        val newCallp = ua.callAlloc(call.callp, Api.VIDMODE_OFF)
        if (newCallp != 0L) {
            Log.d(TAG, "Adding outgoing call ${ua.uap}/$newCallp/$uri")
            val newCall = Call(newCallp, ua, uri, "out", "transferring",
                    Utils.dtmfWatcher(newCallp))
            newCall.add()
            if (newCall.connect(uri)) {
                if (ua.account.aor != aorSpinner.tag)
                    spinToAor(ua.account.aor)
                showCall(ua)
            } else {
                Log.w(TAG, "call_connect $newCallp failed")
                call.notifySipfrag(500, "Call Error")
            }
        } else {
            Log.w(TAG, "callAlloc for ua ${ua.uap} call ${call.callp} transfer failed")
            call.notifySipfrag(500, "Call Error")
        }
    }

    private fun makeCall(lookForContact: Boolean = true) {
        callUri.setAdapter(null)
        val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
        val aor = ua.account.aor
        if (!Call.inCall()) {
            var uriText = callUri.text.toString().trim()
            if (uriText.isNotEmpty()) {
                if (lookForContact) {
                    val uris = Contact.contactUris(uriText)
                    if (uris.size == 1)
                        uriText = uris[0]
                    else if (uris.size > 1) {
                        val builder = MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                        with(builder) {
                            setTitle(R.string.choose_destination_uri)
                            setItems(uris.toTypedArray()) { _, which ->
                                callUri.setText(uris[which])
                                makeCall(false)
                            }
                            setNeutralButton(getString(R.string.cancel)) { _: DialogInterface, _: Int -> }
                            show()
                        }
                        return
                    }
                }
                if (Utils.isTelNumber(uriText))
                    uriText = "tel:$uriText"
                val uri = if (Utils.isTelUri(uriText)) {
                    if (ua.account.telProvider == "") {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.no_telephony_provider), aor))
                        return
                    }
                    Utils.telToSip(uriText, ua.account)
                } else {
                    Utils.uriComplete(uriText, aor)
                }
                if (!Utils.checkUri(uri)) {
                    Utils.alertView(this, getString(R.string.notice),
                            String.format(getString(R.string.invalid_sip_or_tel_uri), uri))
                } else if (!BaresipService.requestAudioFocus(applicationContext)) {
                    Toast.makeText(applicationContext, R.string.audio_focus_denied,
                            Toast.LENGTH_SHORT).show()
                } else {
                    callUri.isFocusable = false
                    uaAdapter.notifyDataSetChanged()
                    callButton.visibility = View.INVISIBLE
                    callButton.isEnabled = false
                    hangupButton.visibility = View.VISIBLE
                    hangupButton.isEnabled = true
                    if (Build.VERSION.SDK_INT < 31) {
                        callRunnable = Runnable {
                            callRunnable = null
                            if (!call(ua, uri)) {
                                callButton.visibility = View.VISIBLE
                                callButton.isEnabled = true
                                hangupButton.visibility = View.INVISIBLE
                                hangupButton.isEnabled = false
                            }
                        }
                        callHandler.postDelayed(callRunnable!!, 1000)
                    } else {
                        audioModeChangedListener = AudioManager.OnModeChangedListener { mode ->
                            if (mode == AudioManager.MODE_IN_COMMUNICATION) {
                                Log.d(TAG, "Audio mode changed to MODE_IN_COMMUNICATION using " +
                                        "device ${am.communicationDevice!!.type}")
                                if (audioModeChangedListener != null) {
                                    am.removeOnModeChangedListener(audioModeChangedListener!!)
                                    audioModeChangedListener = null
                                }
                                if (!call(ua, uri)) {
                                    callButton.visibility = View.VISIBLE
                                    callButton.isEnabled = true
                                    hangupButton.visibility = View.INVISIBLE
                                    hangupButton.isEnabled = false
                                }
                            } else {
                                Log.d(TAG, "Audio mode changed to mode ${am.mode} using " +
                                    "device ${am.communicationDevice!!.type}")
                            }
                        }
                        if (am.mode == AudioManager.MODE_IN_COMMUNICATION) {
                            if (!call(ua, uri)) {
                                callButton.visibility = View.VISIBLE
                                callButton.isEnabled = true
                                hangupButton.visibility = View.INVISIBLE
                                hangupButton.isEnabled = false
                            }
                        } else {
                            am.addOnModeChangedListener(mainExecutor, audioModeChangedListener!!)
                            Log.d(TAG, "Setting audio mode to MODE_IN_COMMUNICATION")
                            am.mode = AudioManager.MODE_IN_COMMUNICATION
                        }
                    }
                }
            } else {
                val latestPeerUri = CallHistory.aorLatestPeerUri(aor)
                if (latestPeerUri != null)
                    callUri.setText(Utils.friendlyUri(this, latestPeerUri, ua.account))
            }
        }
    }

    private fun showCall(ua: UserAgent, showCall: Call? = null) {
        val call = showCall ?: ua.currentCall()
        if (call == null) {
            swipeRefresh.isEnabled = true
            callTitle.text = getString(R.string.outgoing_call_to_dots)
            callTimer.visibility = View.INVISIBLE
            if (ua.account.resumeUri != "")
                callUri.setText(ua.account.resumeUri)
            else
                callUri.text.clear()
            callUri.hint = getString(R.string.callee)
            callUri.isFocusable = true
            callUri.isFocusableInTouchMode = true
            imm.hideSoftInputFromWindow(callUri.windowToken, 0)
            callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                    Contact.contactNames()))
            securityButton.visibility = View.INVISIBLE
            diverter.visibility = View.GONE
            callButton.visibility = View.VISIBLE
            callButton.isEnabled = true
            hangupButton.visibility = View.INVISIBLE
            answerButton.visibility = View.INVISIBLE
            rejectButton.visibility = View.INVISIBLE
            callControl.visibility = View.INVISIBLE
            dialpadButton.isEnabled = true
            if (BaresipService.isMicMuted) {
                BaresipService.isMicMuted = false
                micIcon!!.setIcon(R.drawable.mic_on)
            }
            onHoldNotice.visibility = View.GONE
        } else {
            swipeRefresh.isEnabled = false
            callUri.isFocusable = false
            when (call.status) {
                "outgoing", "transferring", "answered" -> {
                    callTitle.text = if (call.status == "answered")
                        getString(R.string.incoming_call_from_dots)
                    else
                        getString(R.string.outgoing_call_to_dots)
                    callTimer.visibility = View.INVISIBLE
                    callUri.setText(Utils.friendlyUri(this, call.peerUri, ua.account))
                    securityButton.visibility = View.INVISIBLE
                    diverter.visibility = View.GONE
                    callButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.VISIBLE
                    hangupButton.isEnabled = true
                    answerButton.visibility = View.INVISIBLE
                    rejectButton.visibility = View.INVISIBLE
                    callControl.visibility = View.INVISIBLE
                    onHoldNotice.visibility = View.GONE
                    dialpadButton.isEnabled = false
                }
                "incoming" -> {
                    callTitle.text = getString(R.string.incoming_call_from_dots)
                    callTimer.visibility = View.INVISIBLE
                    callUri.setText(Utils.friendlyUri(this, call.peerUri, ua.account))
                    callUri.setAdapter(null)
                    securityButton.visibility = View.INVISIBLE
                    val uri = call.diverterUri()
                    if (uri != "") {
                        diverterUri.text = Utils.friendlyUri(this, uri, ua.account)
                        diverter.visibility = View.VISIBLE
                    } else {
                        diverter.visibility = View.GONE
                    }
                    callButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.INVISIBLE
                    answerButton.visibility = View.VISIBLE
                    answerButton.isEnabled = true
                    rejectButton.visibility = View.VISIBLE
                    rejectButton.isEnabled = true
                    callControl.visibility = View.INVISIBLE
                    onHoldNotice.visibility = View.GONE
                    dialpadButton.isEnabled = false
                }
                "connected" -> {
                    callControl.post {
                        callControl.scrollTo(holdButton.left, holdButton.top)
                    }
                    if (call.referTo != "") {
                        callTitle.text = getString(R.string.transferring_call_to_dots)
                        callUri.setText(Utils.friendlyUri(this, call.referTo, ua.account))
                        transferButton.isEnabled = false
                    } else {
                        if (call.dir == "out") {
                            callTitle.text = getString(R.string.outgoing_call_to_dots)
                            callUri.setText(Utils.friendlyUri(this, call.peerUri, ua.account))
                        } else {
                            callTitle.text = getString(R.string.incoming_call_from_dots)
                            callUri.setText(Utils.friendlyUri(this, call.peerUri, ua.account))
                        }
                        transferButton.isEnabled = true
                    }
                    if (call.onHoldCall == null)
                        transferButton.setImageResource(R.drawable.call_transfer)
                    else
                        transferButton.setImageResource(R.drawable.call_transfer_execute)
                    startCallTimer(call)
                    callTimer.visibility = View.VISIBLE
                    if (ua.account.mediaEnc == "") {
                        securityButton.visibility = View.INVISIBLE
                    } else {
                        securityButton.tag = call.security
                        securityButton.setImageResource(call.security)
                        securityButton.visibility = View.VISIBLE
                    }
                    callButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.VISIBLE
                    hangupButton.isEnabled = true
                    answerButton.visibility = View.INVISIBLE
                    rejectButton.visibility = View.INVISIBLE
                    if (call.onhold) {
                        holdButton.setImageResource(R.drawable.resume)
                    } else {
                        holdButton.setImageResource(R.drawable.call_hold)
                    }
                    callUri.inputType = InputType.TYPE_CLASS_PHONE
                    dialpadButton.setImageResource(R.drawable.dialpad_on)
                    dialpadButton.tag = "on"
                    dialpadButton.isEnabled = false
                    infoButton.isEnabled = true
                    callControl.visibility = View.VISIBLE
                    Handler(Looper.getMainLooper()).postDelayed({
                        onHoldNotice.visibility = if (call.held) View.VISIBLE else View.GONE
                    }, 100)
                    if (call.held) {
                        imm.hideSoftInputFromWindow(dtmf.windowToken, 0)
                        dtmf.isEnabled = false
                    } else {
                        dtmf.isEnabled = true
                        dtmf.requestFocus()
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
            voicemailButtonSpace.visibility = View.VISIBLE
        } else {
            voicemailButton.visibility = View.GONE
            voicemailButtonSpace.visibility =View.GONE
        }
    }

    private fun restoreActivities() {
        if (BaresipService.activities.isEmpty()) return
        Log.d(TAG, "Activity stack ${BaresipService.activities}")
        val activity = BaresipService.activities[0].split(",")
        BaresipService.activities.removeAt(0)
        when (activity[0]) {
            "main" -> {
                if (!Call.inCall() && (BaresipService.activities.size > 1))
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
            "call_details" -> {
                val i = Intent(this, CallDetailsActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                b.putString("peer", activity[2])
                b.putInt("position", activity[3].toInt())
                i.putExtras(b)
                callsRequest.launch(i)
            }
        }
        return
    }

    private fun saveCallUri() {
        if (BaresipService.uas.isNotEmpty() && aorSpinner.selectedItemPosition >= 0) {
            val ua = BaresipService.uas[aorSpinner.selectedItemPosition]
            if (ua.calls().isEmpty())
                ua.account.resumeUri = callUri.text.toString()
            else
                ua.account.resumeUri = ""
        }
    }

    private fun startCallTimer(call: Call) {
        callTimer.stop()
        callTimer.base = SystemClock.elapsedRealtime() - (call.duration() * 1000L)
        callTimer.start()
    }

    companion object {

        var accountRequest: ActivityResultLauncher<Intent>? = null
        var activityAor = ""

    }

    init {
        if (!BaresipService.libraryLoaded) {
            Log.d(TAG, "Loading baresip library")
            System.loadLibrary("baresip")
            BaresipService.libraryLoaded = true
        }
    }

}

package com.tutpro.baresip

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
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tutpro.baresip.databinding.ActivityMainBinding
import java.io.File
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var layout: RelativeLayout
    private lateinit var callTitle: TextView
    private lateinit var callUri: AutoCompleteTextView
    private lateinit var securityButton: ImageButton
    private lateinit var callButton: ImageButton
    private lateinit var hangupButton: ImageButton
    private lateinit var answerButton: ImageButton
    private lateinit var rejectButton: ImageButton
    private lateinit var holdButton: ImageButton
    private lateinit var transferButton: ImageButton
    private lateinit var voicemailButton: ImageButton
    private lateinit var contactsButton: ImageButton
    private lateinit var messagesButton: ImageButton
    private lateinit var callsButton: ImageButton
    private lateinit var dialpadButton: ImageButton
    private lateinit var dtmf: EditText
    private var dtmfWatcher: TextWatcher? = null
    private lateinit var infoButton: ImageButton
    private lateinit var uaAdapter: UaSpinnerAdapter
    private lateinit var aorSpinner: Spinner
    private lateinit var imm: InputMethodManager
    private lateinit var nm: NotificationManager
    private lateinit var am: AudioManager
    private lateinit var kgm: KeyguardManager
    private lateinit var serviceEventReceiver: BroadcastReceiver
    private lateinit var quitTimer: CountDownTimer
    private lateinit var stopState: String
    private var speakerIcon: MenuItem? = null
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private lateinit var baresipService: Intent

    private var restart = false
    private var atStartup = false

    private var resumeUri = ""
    private var resumeUap = ""
    private var resumeCall: Call? = null
    private var resumeAction = ""

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        val intentAction = intent.getStringExtra("action")

        Log.d(TAG, "MainActivity onCreate ${intent.action}/${intent.data}/$intentAction")

        if (intent?.action == ACTION_CALL && !BaresipService.isServiceRunning)
            BaresipService.callActionUri = URLDecoder.decode(intent.data.toString(), "UTF-8")

        kgm = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        dismissKeyguard()

        window.addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        layout = binding.mainActivityLayout
        aorSpinner = binding.aorSpinner
        callTitle = binding.callTitle
        callUri = binding.callUri
        securityButton = binding.securityButton
        callButton = binding.callButton
        hangupButton = binding.hangupButton
        answerButton = binding.answerButton
        rejectButton = binding.rejectButton
        holdButton = binding.holdButton
        transferButton = binding.transferButton
        dtmf = binding.dtmf
        infoButton = binding.info
        voicemailButton = binding.voicemailButton
        contactsButton = binding.contactsButton
        messagesButton = binding.messagesButton
        callsButton = binding.callsButton
        dialpadButton = binding.dialpadButton
        swipeRefresh = binding.swipeRefresh

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        am = getSystemService(AUDIO_SERVICE) as AudioManager

        serviceEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handleServiceEvent(intent.getStringExtra("event")!!,
                        intent.getStringArrayListExtra("params")!!)
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceEventReceiver,
                IntentFilter("service event"))

        stopState = "initial"
        quitTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                Log.d(TAG, "Seconds remaining: ${millisUntilFinished / 1000}")
            }
            override fun onFinish() {
                when (stopState) {
                    "initial" -> {
                        baresipService.setAction("Stop Force");
                        startService(baresipService)
                        stopState = "force"
                        quitTimer.start()
                    }
                    "force" -> {
                        baresipService.setAction("Kill");
                        startService(baresipService)
                        finishAndRemoveTask()
                        System.exit(0)
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
        aorSpinner.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (aorSpinner.selectedItemPosition == -1) {
                    val i = Intent(this@MainActivity, AccountsActivity::class.java)
                    val b = Bundle()
                    b.putString("aor", "")
                    i.putExtras(b)
                    startActivityForResult(i, ACCOUNTS_CODE)
                    true
                } else {
                    if ((event.x - view.left) < 100) {
                        val i = Intent(this@MainActivity, AccountActivity::class.java)
                        val b = Bundle()
                        b.putString("aor", aorSpinner.tag.toString())
                        i.putExtras(b)
                        startActivityForResult(i, ACCOUNT_CODE)
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
                if (Build.VERSION.SDK_INT >= 27) {
                    kgm.requestDismissKeyguard(this, null)
                }
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        callUri.setOnClickListener { view ->
            if (Build.VERSION.SDK_INT >= 27) {
                kgm.requestDismissKeyguard(this, null)
            }
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
            callUri.setAdapter(null)
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            if (Call.calls().isEmpty()) {
                val uriText = callUri.text.toString().trim()
                if (uriText.isNotEmpty()) {
                    var uri = ContactsActivity.findContactURI(uriText)
                    if (!uri.startsWith("sip:")) {
                        uri = "sip:$uri"
                        if (!uri.contains("@")) {
                            val host = aor.substring(aor.indexOf("@") + 1)
                            uri = "$uri@$host"
                        }
                    }
                    if (!Utils.checkSipUri(uri)) {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.invalid_sip_uri), uri))
                    } else {
                        callUri.isFocusable = false
                        if (am.mode != AudioManager.MODE_IN_COMMUNICATION)
                            am.mode = AudioManager.MODE_IN_COMMUNICATION
                        if (!call(ua, uri, "outgoing")) {
                            am.mode = AudioManager.MODE_NORMAL
                            callButton.visibility = View.VISIBLE
                            callButton.isEnabled = true
                            hangupButton.visibility = View.INVISIBLE
                            hangupButton.isEnabled = false
                        } else {
                            callButton.visibility = View.INVISIBLE
                            callButton.isEnabled = false
                            hangupButton.visibility = View.VISIBLE
                            hangupButton.isEnabled = true
                        }
                    }
                } else {
                    val latest = CallHistory.aorLatestHistory(aor)
                    if (latest != null)
                        callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(latest.peerUri),
                                Utils.aorDomain(ua.account.aor)))
                }
            }
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
            val callp = Call.uaCalls(ua, "in")[0].callp
            Log.d(TAG, "AoR $aor answering call $callp from ${callUri.text}")
            answerButton.isEnabled = false
            rejectButton.isEnabled = false
            Api.ua_answer(ua.uap, callp, Api.VIDMODE_OFF)
        }

        rejectButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val callp = Call.uaCalls(ua, "in")[0].callp
            Log.d(TAG, "AoR $aor rejecting call $callp from ${callUri.text}")
            answerButton.isEnabled = false
            rejectButton.isEnabled = false
            Api.ua_hangup(ua.uap, callp, 486, "Rejected")
        }

        holdButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val call = Call.uaCalls(ua, "")[0]
            if (call.onhold) {
                Log.d(TAG, "AoR $aor resuming call ${call.callp} with ${callUri.text}")
                call.unhold()
                call.onhold = false
                holdButton.setImageResource(R.drawable.pause)
            } else {
                Log.d(TAG, "AoR $aor holding call ${call.callp} with ${callUri.text}")
                call.hold()
                call.onhold = true
                holdButton.setImageResource(R.drawable.play)
            }
        }

        transferButton.setOnClickListener {
            callTransfer(UserAgent.uas()[aorSpinner.selectedItemPosition])
        }

        infoButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val calls = Call.uaCalls(ua, "")
            if (calls.size > 0) {
                val status = calls[0].status()
                val codecs = calls[0].audioCodecs()
                if (status.contains('[') && status.contains(']') &&
                        status.contains('=') && codecs.contains(',')) {
                    val duration = status.split("[")[1].split("]")[0]
                    val rate = status.split('=')[1]
                    val txCodec = codecs.split(',')[0].split("/")
                    val rxCodec = codecs.split(',')[1].split("/")
                    Utils.alertView(this, getString(R.string.call_info),
                            "${getString(R.string.duration)}: $duration\n" +
                                    "${getString(R.string.codecs)}: ${txCodec[0]} ch ${txCodec[2]}/" +
                                    "${rxCodec[0]} ch ${rxCodec[2]}\n" +
                                    "${getString(R.string.rate)}: $rate")
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
                                val callIntent = Intent(this, MainActivity::class.java)
                                callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                callIntent.putExtra("action", "call")
                                callIntent.putExtra("uap", ua.uap)
                                callIntent.putExtra("peer", acc.vmUri)
                                startActivity(callIntent)
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

        contactsButton.setOnClickListener {
            val i = Intent(this@MainActivity, ContactsActivity::class.java)
            val b = Bundle()
            if (aorSpinner.selectedItemPosition >= 0)
                b.putString("aor", aorSpinner.tag.toString())
            else
                b.putString("aor", "")
            i.putExtras(b)
            startActivityForResult(i, CONTACTS_CODE)
        }

        messagesButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val i = Intent(this@MainActivity, ChatsActivity::class.java)
                val b = Bundle()
                b.putString("aor", aorSpinner.tag.toString())
                b.putString("peer", resumeUri)
                i.putExtras(b)
                startActivityForResult(i, CHATS_CODE)
            }
        }

        callsButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val i = Intent(this@MainActivity, CallsActivity::class.java)
                val b = Bundle()
                b.putString("aor", aorSpinner.tag.toString())
                i.putExtras(b)
                startActivityForResult(i, CALLS_CODE)
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

        if (intent.hasExtra("action"))
            // MainActivity was not visible when call, message, or transfer request came in
            handleIntent(intent)
        else
            if (!BaresipService.isServiceRunning)
                if (File(filesDir.absolutePath + "/accounts").exists()) {
                    val accounts = String(Utils.getFileContents(filesDir.absolutePath + "/accounts")!!,
                            Charsets.UTF_8).lines().toMutableList()
                    askPasswords(accounts)
                } else {
                    // Baresip is started for the first time
                    if (!Utils.checkPermission(this, Manifest.permission.RECORD_AUDIO))
                        Utils.requestPermission(this, Manifest.permission.RECORD_AUDIO,
                                RECORD_PERMISSION_REQUEST_CODE)
                    else
                        // Some old devices have granted Mic permission without a need to ask
                        startBaresip()
                }

        intent.removeExtra("action")

        if (Preferences(applicationContext).displayTheme != AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.setDefaultNightMode(Preferences(applicationContext).displayTheme)
            delegate.applyDayNight()
        }

    } // OnCreate

    override fun onNewIntent(intent: Intent) {
        // Called when MainActivity already exists at the top of current task
        super.onNewIntent(intent)
        resumeAction = ""
        resumeUri = ""
        Log.d(TAG, "onNewIntent ${intent.action} ${intent.data}")
        if (intent.action == ACTION_CALL) {
            if (Call.calls().isNotEmpty() || UserAgent.uas().isEmpty()) {
                return
            }
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
            if (intent.getStringExtra("action") != null)
                handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.getStringExtra("action")
        Log.d(TAG, "Handling intent '$action'")
        when (action) {
            "accounts" -> {
                resumeAction = "accounts"
            }
            "call" -> {
                if (!Call.calls().isEmpty()) {
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

    override fun onStart() {
        Log.d(TAG, "Main onStart")
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Main onResume with action '$resumeAction'")
        visible = true
        when (resumeAction) {
            "call show" ->
                handleServiceEvent("call incoming",
                        arrayListOf(resumeCall!!.ua.uap, resumeCall!!.callp))
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
        Log.d(TAG, "Main onPause")
        Utils.addActivity("main")
        visible = false
        saveCallUri()
        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "Main onStop")
        super.onStop()
    }

    override fun recreate() {
        Log.d(TAG, "Main onCreate")
        super.recreate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                Log.d(TAG, "Adjusting volume $keyCode of stream ${volumeControlStream}")
                am.adjustStreamVolume(volumeControlStream,
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
                    System.exit(0)
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
                    "call ringing", "call progress" -> {
                        volumeControlStream = AudioManager.STREAM_MUSIC
                    }
                    "call rejected" -> {
                        if (aor == aorSpinner.tag) {
                            callsButton.setImageResource(R.drawable.calls_missed)
                        }
                    }
                    "call incoming" -> {
                        val callp = params[1]
                        if (!Utils.checkPermission(this, Manifest.permission.RECORD_AUDIO)) {
                            Api.ua_hangup(uap, callp, 486, "Busy Here")
                            return
                        }
                        if (visible) {
                            if (aor != aorSpinner.tag)
                                spinToAor(aor)
                            showCall(ua)
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
                        volumeControlStream = AudioManager.STREAM_MUSIC
                        if (aor == aorSpinner.tag) {
                            dtmf.setText("")
                            dtmf.hint = getString(R.string.dtmf)
                            showCall(ua)
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
                                val security: Int
                                if (Api.cmd_exec("zrtp_verify ${ev[3]}") != 0) {
                                    Log.e(TAG, "Command 'zrtp_verify ${ev[3]}' failed")
                                    security = R.drawable.box_yellow
                                } else {
                                    security = R.drawable.box_green
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
                        val tag: String
                        if (call.security == R.drawable.box_yellow)
                            tag = "yellow"
                        else
                            tag = "green"
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
                                if (call in Call.calls())
                                    Api.ua_hangup(uap, callp, 0, "")
                                call(ua, ev[1], "outgoing")
                                showCall(ua)
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
                        call(ua, ev[1], "outgoing")
                        showCall(ua)
                    }
                    "refer failed" -> {
                        Toast.makeText(applicationContext,
                                "${getString(R.string.transfer_failed)}: ${ev[1].trim()}",
                                Toast.LENGTH_LONG).show()
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
                        volumeControlStream = AudioManager.STREAM_MUSIC
                        val param = ev[1].trim()
                        if ((param != "") && (Call.uaCalls(ua, "").size == 0)) {
                            if (param.get(0).isDigit())
                                Toast.makeText(applicationContext,
                                        "${getString(R.string.call_failed)}: $param",
                                        Toast.LENGTH_LONG).show()
                            else
                                Toast.makeText(applicationContext,
                                        "${getString(R.string.call_closed)}: $param",
                                        Toast.LENGTH_LONG).show()
                        }
                        restoreActivities()
                    }
                    "message", "message show", "message reply" -> {
                        val peer = params[1]
                        val i = Intent(applicationContext, ChatActivity::class.java)
                        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        val b = Bundle()
                        b.putString("aor", aor)
                        b.putString("peer", peer)
                        b.putBoolean("focus", ev[0] == "message reply")
                        i.putExtras(b)
                        startActivityForResult(i, CHAT_CODE)
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
        val intent = pm.getLaunchIntentForPackage(this.getPackageName())
        this.finishAffinity()
        this.startActivity(intent)
        System.exit(0)
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        Log.d(TAG, "Main onDestroy")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceEventReceiver)
        BaresipService.activities.clear()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menuInflater.inflate(R.menu.speaker_icon, menu)
        speakerIcon = menu.findItem(R.id.speakerIcon)
        if (am.isSpeakerphoneOn)
            speakerIcon!!.setIcon(R.drawable.speaker_on)
        else
            speakerIcon!!.setIcon(R.drawable.speaker_off)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        val i: Intent

        when (item.itemId) {

            R.id.speakerIcon -> {
                am.isSpeakerphoneOn = !am.isSpeakerphoneOn
                if (am.isSpeakerphoneOn)
                    item.setIcon(R.drawable.speaker_on)
                else
                    item.setIcon(R.drawable.speaker_off)
            }

            R.id.config -> {
                i = Intent(this, ConfigActivity::class.java)
                startActivityForResult(i, CONFIG_CODE)
            }

            R.id.accounts -> {
                i = Intent(this, AccountsActivity::class.java)
                val b = Bundle()
                b.putString("aor", aorSpinner.tag.toString())
                i.putExtras(b)
                startActivityForResult(i, ACCOUNTS_CODE)
            }

            R.id.backup -> {
                if (Utils.requestPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                BACKUP_PERMISSION_REQUEST_CODE))
                    askPassword(getString(R.string.encrypt_password))
            }

            R.id.restore -> {
                if (Utils.requestPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE,
                                RESTORE_PERMISSION_REQUEST_CODE))
                    askPassword(getString(R.string.decrypt_password))
            }

            R.id.about -> {
                i = Intent(this, AboutActivity::class.java)
                startActivityForResult(i, ABOUT_CODE)
            }

            R.id.restart, R.id.quit -> {
                quitRestart(item.itemId == R.id.restart)
            }
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d(TAG, "onActivity result $requestCode $resultCode")

        when (requestCode) {

            ACCOUNTS_CODE -> {
                uaAdapter.notifyDataSetChanged()
                spinToAor(activityAor)
                if (aorSpinner.tag != "")
                    updateIcons(Account.ofAor(aorSpinner.tag.toString())!!)
                if (BaresipService.isServiceRunning) {
                    baresipService.setAction("UpdateNotification")
                    startService(baresipService)
                }
            }

            ACCOUNT_CODE -> {
                spinToAor(activityAor)
                val ua = UserAgent.ofAor(activityAor)!!
                updateIcons(ua.account)
                if (resultCode == Activity.RESULT_OK)
                    if (aorPasswords.containsKey(activityAor) && aorPasswords[activityAor] == "")
                        askPassword(getString(R.string.authentication_password), ua)
            }

            CONTACTS_CODE -> {
                callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                        Contact.contacts().map { Contact -> Contact.name }))
            }

            CONFIG_CODE -> {
                if ((data != null) && data.hasExtra("restart")) {
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

            CALLS_CODE -> {
                spinToAor(activityAor)
                callsButton.setImageResource(R.drawable.calls)
            }

            CHATS_CODE, CHAT_CODE -> {
                spinToAor(activityAor)
                updateIcons(Account.ofAor(activityAor)!!)
            }

            ABOUT_CODE -> {
            }

        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {

            RECORD_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.size > 0) && (grantResults[0] != PackageManager.PERMISSION_GRANTED))
                    Utils.alertView(this, getString(R.string.notice),
                            getString(R.string.no_calls), ::startBaresip)
                else
                    startBaresip()
            }

            BACKUP_PERMISSION_REQUEST_CODE ->
                if ((grantResults.size > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
                    askPassword(getString(R.string.encrypt_password))

            RESTORE_PERMISSION_REQUEST_CODE ->
                if ((grantResults.size > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
                    askPassword(getString(R.string.decrypt_password))

        }
    }

    private fun quitRestart(reStart: Boolean) {
        if (stopState == "initial") {
            Log.d(TAG, "quitRestart Restart = $reStart")
            if (BaresipService.isServiceRunning) {
                restart = reStart
                baresipService.setAction("Stop");
                startService(baresipService)
                quitTimer.start()
            } else {
                finishAndRemoveTask()
                if (reStart)
                    reStart()
                else
                    System.exit(0)
            }
        }
    }

    private fun callTransfer(ua: UserAgent) {
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
                    var uri = ContactsActivity.findContactURI(uriText)
                    if (!uri.startsWith("sip:")) {
                        uri = "sip:$uri"
                        if (!uri.contains("@")) {
                            val aor = ua.account.aor
                            val host = aor.substring(aor.indexOf("@") + 1)
                            uri = "$uri@$host"
                        }
                    }
                    if (!Utils.checkSipUri(uri))
                        Utils.alertView(this@MainActivity, getString(R.string.notice),
                                String.format(getString(R.string.invalid_sip_uri), uri))
                    else {
                        if (Call.uaCalls(ua, "").size > 0) {
                            Call.uaCalls(ua, "")[0].refer(uri)
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
        val checkBox = layout.findViewById(R.id.checkbox) as CheckBox
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked)
                input.transformationMethod = HideReturnsTransformationMethod()
            else
                input.transformationMethod = PasswordTransformationMethod()
        }
        val context = this
        val builder = AlertDialog.Builder(this, R.style.AlertDialog)
        with(builder) {
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
            val dialog = builder.create()
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
                val checkBox = layout.findViewById(R.id.checkbox) as CheckBox
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked)
                        input.transformationMethod = HideReturnsTransformationMethod()
                    else
                        input.transformationMethod = PasswordTransformationMethod()
                }
                val context = this
                val builder = AlertDialog.Builder(this, R.style.AlertDialog)
                with(builder) {
                    setView(layout)
                    setPositiveButton(android.R.string.ok) { dialog, _ ->
                        imm.hideSoftInputFromWindow(input.windowToken, 0)
                        dialog.dismiss()
                        val password = input.text.toString().trim()
                        if (!Account.checkAuthPass(password)) {
                            Utils.alertView(context, getString(R.string.notice),
                                    String.format(getString(R.string.invalid_authentication_password), password))
                        } else {
                            aorPasswords.put(aor, password)
                        }
                        askPasswords(accounts)
                    }
                    setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        imm.hideSoftInputFromWindow(input.windowToken, 0)
                        dialog.cancel()
                        aorPasswords.put(aor, "")
                        askPasswords(accounts)
                    }
                    val dialog = builder.create()
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
        baresipService.setAction("Start")
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
        val bsFile = getString(R.string.app_name) + ".bs"
        val backupFilePath = BaresipService.downloadsPath + "/$bsFile"
        val zipFile = getString(R.string.app_name) + ".zip"
        val zipFilePath = BaresipService.filesPath + "/$zipFile"
        if (!Utils.zip(files, zipFile)) {
            Log.w(TAG, "Failed to write zip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.backup_failed), bsFile))
            return
        }
        val content = Utils.getFileContents(zipFilePath)
        if (content == null) {
            Log.w(TAG, "Failed to read zip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.backup_failed), bsFile))
            return
        }
        if (!Utils.encryptToFile(backupFilePath, content, password)) {
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.backup_failed), bsFile))
            return
        }
        Utils.alertView(this, getString(R.string.info),
                String.format(getString(R.string.backed_up), bsFile))
        Utils.deleteFile(File(zipFilePath))
    }

    private fun restore(password: String) {
        val bsFile = getString(R.string.app_name) + ".bs"
        val backupFilePath = BaresipService.downloadsPath + "/$bsFile"
        val zipFile = getString(R.string.app_name) + ".zip"
        val zipFilePath = BaresipService.filesPath + "/$zipFile"
        val zipData = Utils.decryptFromFile(backupFilePath, password)
        if (zipData == null) {
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.restore_failed), bsFile))
            return
        }
        if (!Utils.putFileContents(zipFilePath, zipData)) {
            Log.w(TAG, "Failed to write zip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.restore_failed), bsFile))
            return
        }
        if (!Utils.unZip(zipFilePath)) {
            Log.w(TAG, "Failed to unzip file '$zipFile'")
            Utils.alertView(this, getString(R.string.error),
                    String.format(getString(R.string.restore_failed), bsFile))
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

    private fun call(ua: UserAgent, uri: String, status: String): Boolean {
        if (!Utils.checkPermission(this, Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(applicationContext, getString(R.string.no_calls),
                    Toast.LENGTH_LONG).show()
            return false
        }
        if (ua.account.aor != aorSpinner.tag)
            spinToAor(ua.account.aor)
        val callp = Api.ua_connect(ua.uap, uri, Api.VIDMODE_OFF)
        if (callp != "") {
            Log.d(TAG, "Adding outgoing call ${ua.uap}/$callp/$uri")
            Call(callp, ua, uri, "out", status, Utils.dtmfWatcher(callp)).add()
            showCall(ua)
            return true
        } else {
            Log.w(TAG, "ua_connect ${ua.uap}/$uri failed")
            return false
        }
    }

    // Currently transfer is implemented by first closing existing call and the making the new one
    private fun transfer(ua: UserAgent, call: Call, uri: String) {
        val newCallp = Api.ua_call_alloc(ua.uap, call.callp, Api.VIDMODE_OFF)
        if (newCallp != "") {
            Log.d(TAG, "Adding outgoing call ${ua.uap}/$newCallp/$uri")
            val newCall = Call(newCallp, ua, uri, "out", "transferring",
                    Utils.dtmfWatcher(newCallp))
            newCall.add()
            Api.ua_hangup(ua.uap, call.callp, 0, "")
            // Api.call_stop_audio(call.callp)
            val err = newCall.connect(uri)
            if (err == 0) {
                newCall.startAudio()
                if (ua.account.aor != aorSpinner.tag)
                    spinToAor(ua.account.aor)
                showCall(ua)
            } else {
                call.startAudio()
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

    private fun showCall(ua: UserAgent) {
        if (Call.uaCalls(ua, "").size == 0) {
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
            hangupButton.visibility = View.INVISIBLE
            answerButton.visibility = View.INVISIBLE
            rejectButton.visibility = View.INVISIBLE
            holdButton.visibility = View.INVISIBLE
            transferButton.visibility = View.INVISIBLE
            dtmf.visibility = View.INVISIBLE
            dialpadButton.isEnabled = true
            infoButton.visibility = View.INVISIBLE
            volumeControlStream = AudioManager.STREAM_MUSIC
        } else {
            val call = Call.uaCalls(ua, "")[0]
            callUri.isFocusable = false
            imm.hideSoftInputFromWindow(callUri.windowToken, 0)
            when (call.status) {
                "outgoing" -> {
                    callTitle.text = getString(R.string.outgoing_call_to_dots)
                    callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(call.peerUri),
                            Utils.aorDomain(ua.account.aor)))
                    securityButton.visibility = View.INVISIBLE
                    callButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.VISIBLE
                    hangupButton.isEnabled = true
                    answerButton.visibility = View.INVISIBLE
                    rejectButton.visibility = View.INVISIBLE
                    holdButton.visibility = View.INVISIBLE
                    transferButton.visibility = View.INVISIBLE
                    dtmf.visibility = View.INVISIBLE
                    dialpadButton.isEnabled = false
                    infoButton.visibility = View.INVISIBLE
                    volumeControlStream = AudioManager.STREAM_MUSIC
                }
                "incoming" -> {
                    callTitle.text = getString(R.string.incoming_call_from_dots)
                    callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(call.peerUri),
                            Utils.aorDomain(ua.account.aor)))
                    callUri.setAdapter(null)
                    securityButton.visibility = View.INVISIBLE
                    callButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.INVISIBLE
                    answerButton.visibility = View.VISIBLE
                    answerButton.isEnabled = true
                    rejectButton.visibility = View.VISIBLE
                    rejectButton.isEnabled = true
                    holdButton.visibility = View.INVISIBLE
                    transferButton.visibility = View.INVISIBLE
                    dtmf.visibility = View.INVISIBLE
                    dialpadButton.isEnabled = false
                    infoButton.visibility = View.INVISIBLE
                    volumeControlStream = AudioManager.STREAM_RING
                }
                "connected" -> {
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
                    if (ua.account.mediaEnc == "") {
                        securityButton.visibility = View.INVISIBLE
                    } else {
                        securityButton.setImageResource(call.security)
                        setSecurityButtonTag(securityButton, call.security)
                        securityButton.visibility = View.VISIBLE
                    }
                    callButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.VISIBLE
                    hangupButton.isEnabled = true
                    answerButton.visibility = View.INVISIBLE
                    rejectButton.visibility = View.INVISIBLE
                    if (call.onhold) {
                        holdButton.setImageResource(R.drawable.play)
                    } else {
                        holdButton.setImageResource(R.drawable.pause)
                    }
                    holdButton.visibility = View.VISIBLE
                    transferButton.visibility = View.VISIBLE
                    dtmf.visibility = View.VISIBLE
                    dtmf.isEnabled = true
                    dtmf.requestFocus()
                    if (resources.configuration.orientation == ORIENTATION_PORTRAIT)
                        imm.showSoftInput(dtmf, InputMethodManager.SHOW_IMPLICIT)
                    if (dtmfWatcher != null) dtmf.removeTextChangedListener(dtmfWatcher)
                    dtmfWatcher = call.dtmfWatcher
                    dtmf.addTextChangedListener(dtmfWatcher)
                    callUri.inputType = InputType.TYPE_CLASS_PHONE
                    dialpadButton.setImageResource(R.drawable.dialpad_on)
                    dialpadButton.tag = "on"
                    dialpadButton.isEnabled = false
                    infoButton.visibility = View.VISIBLE
                    infoButton.isEnabled = true
                    volumeControlStream = AudioManager.STREAM_MUSIC
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
                val i = Intent(this, ConfigActivity::class.java)
                startActivityForResult(i, CONFIG_CODE)
            }
            "audio" -> {
                val i = Intent(this, AudioActivity::class.java)
                startActivity(i)
            }
            "accounts" -> {
                val i = Intent(this, AccountsActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                i.putExtras(b)
                startActivityForResult(i, ACCOUNTS_CODE)
            }
            "account" -> {
                val i = Intent(this, AccountActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                i.putExtras(b)
                startActivityForResult(i, ACCOUNT_CODE)
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
                val i = Intent(this, AboutActivity::class.java)
                startActivityForResult(i, ABOUT_CODE)
            }
            "contacts" -> {
                val i = Intent(this, ContactsActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                i.putExtras(b)
                startActivityForResult(i, CONTACTS_CODE)

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
                startActivityForResult(i, CONTACT_CODE)
            }
            "chats" -> {
                val i = Intent(this, ChatsActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                i.putExtras(b)
                startActivityForResult(i, CHATS_CODE)
            }
            "chat" -> {
                val i = Intent(this, ChatActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                b.putString("peer", activity[2])
                b.putBoolean("focus", activity[3] == "true")
                i.putExtras(b)
                startActivityForResult(i, CHAT_CODE)
            }
            "calls" -> {
                val i = Intent(this, CallsActivity::class.java)
                val b = Bundle()
                b.putString("aor", activity[1])
                i.putExtras(b)
                startActivityForResult(i, CALLS_CODE)
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

    @Suppress("DEPRECATION")
    private fun dismissKeyguard() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            kgm.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
    }

    companion object {

        var visible = false
        var activityAor = ""

        // <aor, password> of those accounts that have auth username without auth password
        val aorPasswords = mutableMapOf<String, String>()

        private const val TAG = "Baresip"

        const val ACCOUNTS_CODE = 1
        const val CONTACTS_CODE = 2
        const val CONFIG_CODE = 3
        const val CALLS_CODE = 4
        const val ABOUT_CODE = 5
        const val ACCOUNT_CODE = 6
        const val CONTACT_CODE = 7
        const val CHATS_CODE = 8
        const val CHAT_CODE = 9

        const val BACKUP_PERMISSION_REQUEST_CODE = 1
        const val RESTORE_PERMISSION_REQUEST_CODE = 2
        const val RECORD_PERMISSION_REQUEST_CODE = 3
        const val CONTACTS_PERMISSION_REQUEST_CODE = 4

    }

    init {
        if (!BaresipService.libraryLoaded) {
            Log.d(TAG, "Loading baresip library")
            System.loadLibrary("baresip")
            BaresipService.libraryLoaded = true
        }
    }

}

package com.tutpro.baresip

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.support.v4.content.LocalBroadcastManager
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import android.text.TextWatcher
import android.widget.*
import android.view.*

import java.io.File
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    internal lateinit var layout: RelativeLayout
    internal lateinit var baresipService: Intent
    internal lateinit var callTitle: TextView
    internal lateinit var callUri: AutoCompleteTextView
    internal lateinit var securityButton: ImageButton
    internal lateinit var callButton: ImageButton
    internal lateinit var hangupButton: ImageButton
    internal lateinit var answerButton: ImageButton
    internal lateinit var rejectButton: ImageButton
    internal lateinit var holdButton: ImageButton
    internal lateinit var voicemailButton: ImageButton
    internal lateinit var contactsButton: ImageButton
    internal lateinit var messagesButton: ImageButton
    internal lateinit var callsButton: ImageButton
    internal lateinit var dialpadButton: ImageButton
    internal lateinit var dtmf: EditText
    internal var dtmfWatcher: TextWatcher? = null
    internal lateinit var infoButton: ImageButton
    internal lateinit var uaAdapter: UaSpinnerAdapter
    internal lateinit var aorSpinner: Spinner
    internal lateinit var imm: InputMethodManager
    internal lateinit var nm: NotificationManager
    internal lateinit var kgm: KeyguardManager
    internal lateinit var serviceEventReceiver: BroadcastReceiver
    internal lateinit var quitTimer: CountDownTimer
    internal lateinit var stopState: String
    internal lateinit var speakerIcon: MenuItem

    internal var restart = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val intentAction = intent.getStringExtra("action")
        Log.d("Baresip", "Main created with action '$intentAction'")

        kgm = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            kgm.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)

        setContentView(R.layout.activity_main)

        layout = findViewById(R.id.mainActivityLayout) as RelativeLayout
        aorSpinner = findViewById(R.id.AoRList)
        callTitle = findViewById(R.id.callTitle) as TextView
        callUri = findViewById(R.id.callUri) as AutoCompleteTextView
        securityButton = findViewById(R.id.securityButton) as ImageButton
        callButton = findViewById(R.id.callButton) as ImageButton
        hangupButton = findViewById(R.id.hangupButton) as ImageButton
        answerButton = findViewById(R.id.answerButton) as ImageButton
        rejectButton = findViewById(R.id.rejectButton) as ImageButton
        holdButton = findViewById(R.id.holdButton) as ImageButton
        dtmf = findViewById(R.id.dtmf) as EditText
        infoButton = findViewById(R.id.info) as ImageButton
        voicemailButton = findViewById(R.id.voicemailButton) as ImageButton
        contactsButton = findViewById(R.id.contactsButton) as ImageButton
        messagesButton = findViewById(R.id.messagesButton) as ImageButton
        callsButton = findViewById(R.id.callsButton) as ImageButton
        dialpadButton = findViewById(R.id.dialpadButton) as ImageButton

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        serviceEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handleServiceEvent(intent.getStringExtra("event"),
                        intent.getStringArrayListExtra("params"))
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceEventReceiver,
                IntentFilter("service event"))

        stopState = "initial"
        quitTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                Log.d("Baresip", "Seconds remaining: ${millisUntilFinished / 1000}")
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
        aorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            // Have to allow NULL view, since sometimes when onItemSelected is called, view is NULL.
            // Haven't found any explanation why this can happen.
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                Log.d("Baresip", "aorSpinner selecting $position")
                val acc = UserAgent.uas()[position].account
                val aor = acc.aor
                val ua = UserAgent.uas()[position]
                Log.d("Baresip", "Setting $aor current")
                Api.uag_current_set(UserAgent.uas()[position].uap)
                showCall(ua)
                updateIcons(acc)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                Log.d("Baresip", "Nothing selected")
            }
        }
        aorSpinner.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (aorSpinner.selectedItemPosition == -1) {
                    val i = Intent(this, AccountsActivity::class.java)
                    startActivityForResult(i, ACCOUNTS_CODE)
                    true
                } else {
                    if ((event.x - view.left) < 100) {
                        val i = Intent(this, AccountActivity::class.java)
                        val b = Bundle()
                        b.putString("accp", UserAgent.uas()[aorSpinner.selectedItemPosition].account.accp)
                        i.putExtras(b)
                        startActivityForResult(i, ACCOUNT_CODE)
                        true
                    } else {
                        false
                    }
                }
            } else {
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
                    val unverifyDialog = AlertDialog.Builder(this)
                    unverifyDialog.setTitle(getString(R.string.info))
                    unverifyDialog.setMessage(getString(R.string.call_is_secure))
                    unverifyDialog.setPositiveButton(getString(R.string.unverify)) { dialog, _ ->
                        val calls = Call.uaCalls(UserAgent.uas()[aorSpinner.selectedItemPosition],
                                "")
                        if (calls.size > 0) {
                            if (Api.cmd_exec("zrtp_unverify " + calls[0].zid) != 0) {
                                Log.e("Baresip",
                                        "Command 'zrtp_unverify ${calls[0].zid}' failed")
                            } else {
                                securityButton.setImageResource(R.drawable.box_yellow)
                                securityButton.tag = "yellow"
                            }
                        }
                        dialog.dismiss()
                    }
                    unverifyDialog.setNegativeButton(getString(R.string.no)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    unverifyDialog.create().show()
                }
            }
        }

        callButton.setOnClickListener {
            callUri.setAdapter(null)
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            if (Call.calls().isEmpty()) {
                val uriText = callUri.text.toString().trim()
                if (uriText.length > 0) {
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
                        // Set audio mode to MODE_IN_COMMUNICATION and wait 2.5 sec before
                        // placing to call in order to avoid missing audio from callee due to
                        // a bug in many Android devices.
                        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        if (am.mode != AudioManager.MODE_IN_COMMUNICATION)
                            am.mode = AudioManager.MODE_IN_COMMUNICATION
                        callButton.visibility = View.INVISIBLE
                        callButton.isEnabled = false
                        hangupButton.visibility = View.VISIBLE
                        hangupButton.isEnabled = false
                        Handler().postDelayed({
                            hangupButton.isEnabled = true
                            if (!call(ua, uri, "outgoing")) {
                                am.mode = AudioManager.MODE_NORMAL
                                callButton.visibility = View.VISIBLE
                                callButton.isEnabled = true
                                hangupButton.visibility = View.INVISIBLE
                                hangupButton.isEnabled = false
                            }
                        }, 2500)
                    }
                } else {
                    val latest = CallHistory.aorLatestHistory(aor)
                    if (latest != null)
                        callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(latest.peerURI),
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
                Log.d("Baresip", "AoR $aor hanging up call $callp with ${callUri.text}")
                hangupButton.isEnabled = false
                Api.ua_hangup(ua.uap, callp, 0, "")
            }
        }

        answerButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val callp = Call.uaCalls(ua, "in")[0].callp
            Log.d("Baresip", "AoR $aor answering call $callp from ${callUri.text}")
            answerButton.isEnabled = false
            rejectButton.isEnabled = false
            Api.ua_answer(ua.uap, callp)
        }

        rejectButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val callp = Call.uaCalls(ua, "in")[0].callp
            Log.d("Baresip", "AoR $aor rejecting call $callp from ${callUri.text}")
            answerButton.isEnabled = false
            rejectButton.isEnabled = false
            Api.ua_hangup(ua.uap, callp, 486, "Rejected")
        }

        holdButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val call = Call.uaCalls(ua, "")[0]
            if (call.onhold) {
                Log.d("Baresip", "AoR $aor resuming call ${call.callp} with ${callUri.text}")
                Api.call_unhold(call.callp)
                call.onhold = false
                holdButton.setImageResource(R.drawable.pause)
            } else {
                Log.d("Baresip", "AoR $aor holding call ${call.callp} with ${callUri.text}")
                Api.call_hold(call.callp)
                call.onhold = true
                holdButton.setImageResource(R.drawable.play)
            }
        }

        infoButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val calls = Call.uaCalls(ua, "")
            if (calls.size > 0) {
                val status = Api.call_status(calls[0].callp)
                val codecs = Api.call_audio_codecs(calls[0].callp)
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
                    val builder = AlertDialog.Builder(this@MainActivity,
                            R.style.Theme_AppCompat)
                    builder.setTitle(getString(R.string.voicemail_messages))
                    builder.setMessage(acc.vmMessages(this))
                            .setPositiveButton(getString(R.string.listen), dialogClickListener)
                            .setNegativeButton(getString(R.string.cancel), dialogClickListener)
                            .show()
                }
            }
        }

        contactsButton.setOnClickListener {
            val i = Intent(this@MainActivity, ContactsActivity::class.java)
            val b = Bundle()
            if (aorSpinner.selectedItemPosition >= 0)
                b.putString("aor", UserAgent.uas()[aorSpinner.selectedItemPosition].account.aor)
            else
                b.putString("aor", "")
            i.putExtras(b)
            startActivityForResult(i, CONTACTS_CODE)
        }

        messagesButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val i = Intent(this@MainActivity, ChatsActivity::class.java)
                val b = Bundle()
                b.putString("aor", UserAgent.uas()[aorSpinner.selectedItemPosition].account.aor)
                i.putExtras(b)
                startActivityForResult(i, CHATS_CODE)
            }
        }

        callsButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val i = Intent(this@MainActivity, CallsActivity::class.java)
                val b = Bundle()
                b.putString("aor", UserAgent.uas()[aorSpinner.selectedItemPosition].account.aor)
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

        baresipService = Intent(this@MainActivity, BaresipService::class.java)
        if (!BaresipService.isServiceRunning) {
            baresipService.setAction("Start")
            startService(baresipService)
            Utils.requestPermission(this, Manifest.permission.RECORD_AUDIO,
                    RECORD_PERMISSION_REQUEST_CODE)
        }

        if (intent.hasExtra("onStartup"))
            moveTaskToBack(true)

        if (intent.hasExtra("action"))
            // MainActivity was not visible when call, message, or transfer request came in
            handleIntent(intent)

    } // OnCreate

    override fun onNewIntent(intent: Intent) {
        // Called when MainActivity already exists at the top of current task
        super.onNewIntent(intent)
        val action = intent.getStringExtra("action")
        Log.d("Baresip", "onNewIntent action '$action'")
        if (action != null) handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.getStringExtra("action")
        Log.d("Baresip", "Handling intent '$action'")
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
                val uap = intent.getStringExtra("uap")
                val ua = UserAgent.find(uap)
                if (ua == null) {
                    Log.w("Baresip", "handleIntent 'call' did not find ua $uap")
                    return
                }
                if ((aorSpinner.selectedItemPosition == -1) ||
                        (ua != UserAgent.uas()[aorSpinner.selectedItemPosition]))
                    spinToAor(ua.account.aor)
                resumeAction = action
                resumeUri = intent.getStringExtra("peer")
            }
            "call show", "call answer" -> {
                val callp = intent.getStringExtra("callp")
                val call = Call.find(callp)
                if (call == null) {
                    Log.w("Baresip", "handleIntent '$action' did not find call $callp")
                    return
                }
                val ua = call.ua
                if ((aorSpinner.selectedItemPosition == -1) ||
                        (ua != UserAgent.uas()[aorSpinner.selectedItemPosition]))
                    spinToAor(ua.account.aor)
                resumeAction = action
                resumeCall = call
            }
            "transfer show", "transfer accept" -> {
                val callp = intent.getStringExtra("callp")
                val call = Call.find(callp)
                if (call == null) {
                    Log.w("Baresip", "handleIntent '$action' did not find call $callp")
                    moveTaskToBack(true)
                    return
                }
                resumeAction = action
                resumeCall = call
                resumeUri = intent.getStringExtra("uri")
            }
            "message", "message show", "message reply" -> {
                val uap = intent.getStringExtra("uap")
                val ua = UserAgent.find(uap)
                if (ua == null) {
                    Log.w("Baresip", "onNewIntent did not find ua $uap")
                    return
                }
                if ((aorSpinner.selectedItemPosition == -1) ||
                        (ua != UserAgent.uas()[aorSpinner.selectedItemPosition]))
                    spinToAor(ua.account.aor)
                resumeAction = action
                resumeUap = uap
                resumeUri = intent.getStringExtra("peer")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("Baresip", "Main resumed with action '$resumeAction'")
        visible = true
        when (resumeAction) {
            "accounts" -> {
                val i = Intent(this, AccountsActivity::class.java)
                startActivityForResult(i, ACCOUNTS_CODE)
            }
            "call show" ->
                handleServiceEvent("call incoming",
                        arrayListOf(resumeCall!!.ua.uap, resumeCall!!.callp))
            "call answer" -> {
                answerButton.performClick()
                showCall(resumeCall!!.ua)
            }
            "call reject" ->
                rejectButton.performClick()
            "call" -> {
                callUri.setText(resumeUri)
                callButton.performClick()
            }
            "transfer show", "transfer accept" ->
                handleServiceEvent("$resumeAction,$resumeUri",
                        arrayListOf(resumeCall!!.ua.uap, resumeCall!!.callp))
            "message" -> {
                messagesButton.performClick()
            }
            "message show", "message reply" ->
                handleServiceEvent(resumeAction, arrayListOf(resumeUap, resumeUri))
            else -> {
                if (!BaresipService.activities.isEmpty()) restoreActivities()
                if (UserAgent.uas().size > 0) {
                    val currentPosition = aorSpinner.selectedItemPosition
                    if (currentPosition == -1) {
                        if (Call.calls().size > 0)
                            spinToAor(Call.calls()[0].ua.account.aor)
                        else
                            aorSpinner.setSelection(0)
                    } else {
                        aorSpinner.setSelection(currentPosition)
                    }
                    uaAdapter.notifyDataSetChanged()
                    showCall(UserAgent.uas()[aorSpinner.selectedItemPosition])
                }
            }
        }
        resumeAction = ""
        resumeUri = ""
    }

    override fun onPause() {
        Log.d("Baresip", "Main paused")
        Utils.addActivity("main")
        visible = false
        super.onPause()
    }

    private fun handleServiceEvent(event: String, params: ArrayList<String>) {
        if (taskId == -1) {
            Log.d("Baresip", "Omit service event '$event' for task -1")
            return
        }
        if (event == "stopped") {
            Log.d("Baresip", "Handling service event 'stopped' with param '${params[0]}'")
            if (params[0] != "") {
                val alertDialog = AlertDialog.Builder(this).create()
                alertDialog.setTitle(getString(R.string.notice))
                alertDialog.setMessage(getString(R.string.start_failed))
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok)
                ) { dialog, _ ->
                    dialog.dismiss()
                    //quitTimer.cancel()
                    //finishAndRemoveTask()
                    //System.exit(0)
                }
                alertDialog.show()
                return
            } else {
                quitTimer.cancel()
                finishAndRemoveTask()
                if (restart) reStart()
                System.exit(0)
                return
            }
        }
        val uap = params[0]
        val ua = UserAgent.find(uap)
        if (ua == null) {
            Log.w("Baresip", "handleServiceEvent '$event' did not find ua $uap")
            return
        }
        val ev = event.split(",")
        Log.d("Baresip", "Handling service event '${ev[0]}' for $uap")
        val aor = ua.account.aor
        val acc = ua.account
        for (account_index in UserAgent.uas().indices) {
            if (UserAgent.uas()[account_index].account.aor == aor) {
                when (ev[0]) {
                    "ua added" -> {
                        uaAdapter.notifyDataSetChanged()
                        if (aorSpinner.selectedItemPosition == -1) aorSpinner.setSelection(0)
                    }
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
                        volumeControlStream = AudioManager.STREAM_VOICE_CALL
                    }
                    "call rejected" -> {
                        if (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                            callsButton.setImageResource(R.drawable.calls_missed)
                        }
                    }
                    "call incoming" -> {
                        val callp = params[1]
                        if (!Utils.checkPermission(this, Manifest.permission.RECORD_AUDIO)) {
                            Api.ua_hangup(uap, callp, 486, "Busy Here")
                            return
                        }
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.w("Baresip", "Incoming call $callp not found")
                            return
                        }
                        volumeControlStream = AudioManager.STREAM_RING
                        if (visible) {
                            if ((aorSpinner.selectedItemPosition == -1) ||
                                    (ua != UserAgent.uas()[aorSpinner.selectedItemPosition]))
                                aorSpinner.setSelection(account_index)
                            showCall(ua)
                        } else {
                            Log.d("Baresip", "Reordering to front")
                            val i = Intent(applicationContext, MainActivity::class.java)
                            i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            i.putExtra("action", "call show")
                            i.putExtra("callp", callp)
                            startActivity(i)
                        }
                    }
                    "call established" -> {
                        val callp = params[1]
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.w("Baresip", "Established call $callp not found")
                            return
                        }
                        volumeControlStream = AudioManager.STREAM_VOICE_CALL
                        if (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                            dtmf.setText("")
                            dtmf.hint = getString(R.string.dtmf)
                            showCall(ua)
                        }
                    }
                    "call verify" -> {
                        val callp = params[1]
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.w("Baresip", "Call $callp to be verified is not found")
                            return
                        }
                        val verifyDialog = AlertDialog.Builder(this@MainActivity)
                        verifyDialog.setTitle(getString(R.string.verify))
                        verifyDialog.setMessage(String.format(getString(R.string.verify_sas),
                                ev[1], ev[2]))
                        verifyDialog.setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                            val security: Int
                            if (Api.cmd_exec("zrtp_verify ${ev[3]}") != 0) {
                                Log.e("Baresip", "Command 'zrtp_verify ${ev[3]}' failed")
                                security = R.drawable.box_yellow
                            } else {
                                security = R.drawable.box_green
                            }
                            call.security = security
                            call.zid = ev[3]
                            if (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                                securityButton.setImageResource(security)
                                setSecurityButtonTag(securityButton, security)
                                securityButton.visibility = View.VISIBLE
                                dialog.dismiss()
                            }
                        }
                        verifyDialog.setNegativeButton(getString(R.string.no)) { dialog, _ ->
                            call.security = R.drawable.box_yellow
                            call.zid = ev[3]
                            if (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                                securityButton.setImageResource(R.drawable.box_yellow)
                                securityButton.tag = "yellow"
                                securityButton.visibility = View.VISIBLE
                            }
                            dialog.dismiss()
                        }
                        if (!isFinishing()) verifyDialog.create().show()
                    }
                    "call verified", "call secure" -> {
                        val callp = params[1]
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.w("Baresip", "Call $callp that is verified is not found")
                            return
                        }
                        val tag: String
                        if (call.security == R.drawable.box_yellow)
                            tag = "yellow"
                        else
                            tag = "green"
                        if (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                            securityButton.setImageResource(call.security)
                            securityButton.tag = tag
                        }
                    }
                    "call transfer", "transfer show" -> {
                        val callp = params[1]
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.w("Baresip", "Call $callp to be transferred is not found")
                            return
                        }
                        val transferDialog = AlertDialog.Builder(this)
                        val target = Utils.friendlyUri(ContactsActivity.contactName(ev[1]),
                                Utils.aorDomain(aor))
                        transferDialog.setMessage(String.format(getString(R.string.transfer_query),
                                target))
                        transferDialog.setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                            if (call in Call.calls())
                                Api.ua_hangup(uap, callp, 0, "")
                            call(ua, ev[1], "transferring")
                            showCall(ua)
                            dialog.dismiss()
                        }
                        transferDialog.setNegativeButton(getString(R.string.no)) { dialog, _ ->
                            if (call in Call.calls())
                                Api.call_notify_sipfrag(callp, 603, "Decline")
                            dialog.dismiss()
                        }
                        transferDialog.create().show()
                    }
                    "transfer accept" -> {
                        val callp = params[1]
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.w("Baresip", "Call $callp to be transferred is not found")
                            return
                        }
                        if (call in Call.calls())
                            Api.ua_hangup(uap, callp, 0, "")
                        call(ua, ev[1], "transferring")
                        showCall(ua)
                    }
                    "call closed" -> {
                        if (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                            showCall(ua)
                            if (ua.account.missedCalls)
                                callsButton.setImageResource(R.drawable.calls_missed)
                        }
                        speakerIcon.setIcon(R.drawable.speaker_off)
                        volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
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
                        if (!BaresipService.activities.isEmpty()) restoreActivities()
                    }
                    "message show", "message reply" -> {
                        val peer = params[1]
                        Log.d("Baresip", "Message for $aor from $peer")
                        Log.d("Baresip", "Activity stack ${BaresipService.activities.toString()}")
                        if ((aorSpinner.selectedItemPosition == -1) ||
                                (ua != UserAgent.uas()[aorSpinner.selectedItemPosition]))
                            aorSpinner.setSelection(account_index)
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
                        if ((aorSpinner.selectedItemPosition >= 0) &&
                                (ua == UserAgent.uas()[aorSpinner.selectedItemPosition])) {
                            if (acc.vmNew > 0)
                                voicemailButton.setImageResource(R.drawable.voicemail_new)
                            else
                                voicemailButton.setImageResource(R.drawable.voicemail)
                        }
                    }
                    else -> Log.e("Baresip", "Unknown event '${ev[0]}'")
                }
                break
            }
        }
    }

    private fun reStart() {
        Log.d("Baresip", "Trigger restart")
        val restartActivity = Intent(applicationContext, MainActivity::class.java)
        val restartIntent = PendingIntent.getActivity(applicationContext,
                RESTART_REQUEST_CODE, restartActivity, PendingIntent.FLAG_CANCEL_CURRENT)
        val am = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC,System.currentTimeMillis() + 1000, restartIntent)
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        Log.d("Baresip", "Destroyed")
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menuInflater.inflate(R.menu.speaker_icon, menu)
        speakerIcon = menu.findItem(R.id.speakerIcon)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val i: Intent
        when (item.itemId) {
            R.id.speakerIcon -> {
                if (BaresipService.speakerPhone)
                    item.setIcon(R.drawable.speaker_off)
                else
                    item.setIcon(R.drawable.speaker_on)
                if (Call.calls().size > 0) {
                    baresipService.putExtra("enable", BaresipService.speakerPhone)
                    baresipService.setAction("ProximitySensing")
                    startService(baresipService)
                }
                baresipService.setAction("ToggleSpeaker")
                startService(baresipService)
            }
            R.id.config -> {
                i = Intent(this, ConfigActivity::class.java)
                startActivityForResult(i, CONFIG_CODE)
            }
            R.id.accounts -> {
                i = Intent(this, AccountsActivity::class.java)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {

            RECORD_PERMISSION_REQUEST_CODE ->
                if ((grantResults.size > 0) && (grantResults[0] != PackageManager.PERMISSION_GRANTED))
                    Toast.makeText(applicationContext, getString(R.string.no_calls),
                            Toast.LENGTH_LONG).show()

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
            Log.d("Baresip", "quitRestart Restart = $restart")
            if (BaresipService.isServiceRunning) {
                restart = reStart
                baresipService.setAction("Stop");
                startService(baresipService)
                quitTimer.start()
            } else {
                finishAndRemoveTask()
                if (reStart) reStart()
                System.exit(0)
            }
        }
    }

    private fun askPassword(title: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        val viewInflated = LayoutInflater.from(this)
                .inflate(R.layout.password_dialog, findViewById(android.R.id.content) as ViewGroup,
                        false)
        val input = viewInflated.findViewById(R.id.password) as EditText
        builder.setView(viewInflated)
        builder.setPositiveButton(android.R.string.ok) { dialog, _ ->
            dialog.dismiss()
            val password = input.text.toString()
            if (password != "") {
                if (title == getString(R.string.encrypt_password))
                    backup(password)
                else
                    restore(password)
            }
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun backup(password: String) {
        val files = arrayListOf("accounts", "calls", "config", "contacts", "messages", "uuid",
                "zrtp_cache.dat", "zrtp_zid", "cert.pem", "ca_cert", "ca_certs.crt")
        File(BaresipService.filesPath).walk().forEach {
            if (it.name.endsWith(".png")) files.add(it.name)
        }
        val backupFilePath = BaresipService.downloadsPath + "/baresip.bs"
        val zipFilePath = BaresipService.filesPath + "/baresip.zip"
        if (!Utils.zip(files, "baresip.zip")) {
            Log.w("Baresip", "Failed to write zip file 'baresip.zip")
            Utils.alertView(this, getString(R.string.error), getString(R.string.backup_failed))
            return
        }
        val content = Utils.getFileContents(zipFilePath)
        if (content == null) {
            Log.w("Baresip", "Failed to read zip file 'baresip.zip")
            Utils.alertView(this, getString(R.string.error), getString(R.string.backup_failed))
            return
        }
        if (!Utils.encryptToFile(backupFilePath, content, password)) {
            Utils.alertView(this, getString(R.string.error), getString(R.string.backup_failed))
            return
        }
        Utils.alertView(this, getString(R.string.info), getString(R.string.backed_up))
        Utils.deleteFile(File(zipFilePath))
    }

    private fun restore(password: String) {
        val backupFilePath = BaresipService.downloadsPath + "/baresip.bs"
        val zipFilePath = BaresipService.filesPath + "/baresip.zip"
        val zipData = Utils.decryptFromFile(backupFilePath, password)
        if (zipData == null) {
            Utils.alertView(this, getString(R.string.error), getString(R.string.restore_failed))
            return
        }
        if (!Utils.putFileContents(zipFilePath, zipData)) {
            Log.w("Baresip", "Failed to write zip file 'baresip.zip")
            Utils.alertView(this, getString(R.string.error), getString(R.string.restore_failed))
            return
        }
        if (!Utils.unZip(zipFilePath)) {
            Log.w("Baresip", "Failed to unzip file 'baresip.zip")
            Utils.alertView(this, getString(R.string.error), getString(R.string.restore_failed))
            return
        }
        Utils.deleteFile(File(zipFilePath))
        val restartDialog = AlertDialog.Builder(this)
        restartDialog.setMessage(getString(R.string.restored))
        restartDialog.setPositiveButton(getText(R.string.restart)) { dialog, _ ->
            quitRestart(true)
            dialog.dismiss()
        }
        restartDialog.setNegativeButton(getText(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        restartDialog.create().show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        when (requestCode) {

            ACCOUNTS_CODE -> {
                uaAdapter.notifyDataSetChanged()
                if (UserAgent.uas().size > 0) {
                    if ((aorSpinner.selectedItemPosition == -1) ||
                            (aorSpinner.selectedItemPosition >= UserAgent.uas().size))
                        aorSpinner.setSelection(0)
                    else
                        updateIcons(UserAgent.uas()[aorSpinner.selectedItemPosition].account)
                } else {
                    aorSpinner.setSelection(-1)
                }
                baresipService.setAction("UpdateNotification")
                startService(baresipService)
            }

            ACCOUNT_CODE -> {
                updateIcons(UserAgent.uas()[aorSpinner.selectedItemPosition].account)
            }

            CONTACTS_CODE -> {
                callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                        Contact.contacts().map{Contact -> Contact.name}))
            }

            CONFIG_CODE -> {
                if ((data != null) && data.hasExtra("restart")) {
                    val restartDialog = AlertDialog.Builder(this)
                    restartDialog.setMessage(getString(R.string.config_restart))
                    restartDialog.setPositiveButton(getText(R.string.restart)) { dialog, _ ->
                        dialog.dismiss()
                        quitRestart(true)
                    }
                    restartDialog.setNegativeButton(getText(R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    restartDialog.create().show()
                }
            }

            CALLS_CODE -> {
                val acc = UserAgent.uas()[aorSpinner.selectedItemPosition].account
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        val peerUri = data.getStringExtra("peer_uri")
                        callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(peerUri),
                                Utils.aorDomain(Utils.aorDomain(acc.aor))))
                    }
                }
                if (resultCode == RESULT_CANCELED) {
                    if (CallHistory.aorHistorySize(acc.aor) == 0) {
                        holdButton.visibility = View.INVISIBLE
                    }
                }
                callsButton.setImageResource(R.drawable.calls)
            }

            CHATS_CODE, CHAT_CODE -> {
                updateIcons(UserAgent.uas()[aorSpinner.selectedItemPosition].account)
            }

            ABOUT_CODE -> {
            }
        }
    }

    private fun spinToAor(aor: String) {
        for (account_index in UserAgent.uas().indices)
            if (UserAgent.uas()[account_index].account.aor == aor) {
                aorSpinner.setSelection(account_index)
                break
            }
    }

    private fun call(ua: UserAgent, uri: String, status: String): Boolean {
        if (!Utils.checkPermission(this, Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(applicationContext, getString(R.string.no_calls),
                    Toast.LENGTH_LONG).show()
            return false
        }
        if (ua != UserAgent.uas()[aorSpinner.selectedItemPosition]) spinToAor(ua.account.aor)
        val callp = Api.ua_connect(ua.uap, uri)
        if (callp != "") {
            Log.d("Baresip", "Adding outgoing call ${ua.uap}/$callp/$uri")
            Call.calls().add(Call(callp, ua, uri, "out", status, Utils.dtmfWatcher(callp)))
            showCall(ua)
            return true
        } else {
            Log.e("Baresip", "ua_connect ${ua.uap}/$uri failed")
            return false
        }
    }

    // Currently transfer is implemented by first closing existing call and the making the new one
    private fun transfer(ua: UserAgent, call: Call, uri: String) {
        val newCallp = Api.ua_call_alloc(ua.uap, call.callp)
        if (newCallp != "") {
            Log.d("Baresip", "Adding outgoing call ${ua.uap}/$newCallp/$uri")
            val newCall = Call(newCallp, ua, uri, "out", "transferring",
                    Utils.dtmfWatcher(newCallp))
            Call.calls().add(newCall)
            Api.ua_hangup(ua.uap, call.callp, 0, "")
            // Api.call_stop_audio(call.callp)
            val err = Api.call_connect(newCallp, uri)
            if (err == 0) {
                Api.call_start_audio(newCallp)
                if (ua != UserAgent.uas()[aorSpinner.selectedItemPosition])
                    spinToAor(ua.account.aor)
                showCall(ua)
            } else {
                Api.call_start_audio(call.callp)
                Log.e("Baresip", "call_connect $newCallp failed with error $err")
                Api.call_notify_sipfrag(call.callp, 500, "Call Error")
            }
        } else {
            Log.e("Baresip", "ua_call_alloc ${ua.uap}/${call.callp} failed")
            Api.call_notify_sipfrag(call.callp, 500, "Call Error")
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
            callUri.text.clear()
            callUri.hint = getString(R.string.callee)
            callUri.isFocusable = true
            callUri.isFocusableInTouchMode = true
            imm.hideSoftInputFromWindow(callUri.windowToken, 0)
            callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                    Contact.contacts().map{Contact -> Contact.name}))
            securityButton.visibility = View.INVISIBLE
            callButton.visibility = View.VISIBLE
            callButton.isEnabled = true
            hangupButton.visibility = View.INVISIBLE
            answerButton.visibility = View.INVISIBLE
            rejectButton.visibility = View.INVISIBLE
            holdButton.visibility = View.INVISIBLE
            dtmf.visibility = View.INVISIBLE
            infoButton.visibility = View.INVISIBLE
            baresipService.putExtra("enable", false)
            baresipService.setAction("ProximitySensing")
            startService(baresipService)
        } else {
            val callsOut = Call.uaCalls(ua, "out")
            val callsIn = Call.uaCalls(ua, "in")
            val call: Call
            if (callsOut.size > 0) {
                call = callsOut[callsOut.size - 1]
                if (call.status == "transferring")
                    callTitle.text = getString(R.string.transferring_call_to_dots)
                else
                    callTitle.text = getString(R.string.outgoing_call_to_dots)
            } else {
                callTitle.text = getString(R.string.incoming_call_from_dots)
                callUri.setAdapter(null)
                call = callsIn[callsIn.size - 1]
            }
            callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(call.peerURI),
                    Utils.aorDomain(ua.account.aor)))
            callUri.isFocusable = false
            imm.hideSoftInputFromWindow(callUri.windowToken, 0)
            baresipService.putExtra("enable", !BaresipService.speakerPhone)
            baresipService.setAction("ProximitySensing")
            startService(baresipService)
            when (call.status) {
                "outgoing", "transferring" -> {
                    securityButton.visibility = View.INVISIBLE
                    callButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.VISIBLE
                    hangupButton.isEnabled = true
                    answerButton.visibility = View.INVISIBLE
                    rejectButton.visibility = View.INVISIBLE
                    holdButton.visibility = View.INVISIBLE
                    dtmf.visibility = View.INVISIBLE
                    infoButton.visibility = View.INVISIBLE
                }
                "incoming" -> {
                    securityButton.visibility = View.INVISIBLE
                    callButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.INVISIBLE
                    answerButton.visibility = View.VISIBLE
                    answerButton.isEnabled = true
                    rejectButton.visibility = View.VISIBLE
                    rejectButton.isEnabled = true
                    holdButton.visibility = View.INVISIBLE
                    dtmf.visibility = View.INVISIBLE
                    infoButton.visibility = View.INVISIBLE
                }
                "connected" -> {
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
                    dtmf.visibility = View.VISIBLE
                    dtmf.isEnabled = true
                    dtmf.requestFocus()
                    if (dtmfWatcher != null) dtmf.removeTextChangedListener(dtmfWatcher)
                    dtmfWatcher = call.dtmfWatcher
                    dtmf.addTextChangedListener(dtmfWatcher)
                    infoButton.visibility = View.VISIBLE
                    infoButton.isEnabled = true
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
        Log.d("Baresip", "Activity stack ${BaresipService.activities.toString()}")
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
            "accounts" -> {
                val i = Intent(this, AccountsActivity::class.java)
                startActivityForResult(i, ACCOUNTS_CODE)
            }
            "account" -> {
                val i = Intent(this, AccountActivity::class.java)
                val b = Bundle()
                b.putString("accp", activity[1])
                i.putExtras(b)
                startActivityForResult(i, ACCOUNT_CODE)
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
                b.putString("focus", activity[3])
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

    companion object {

        var visible = false

        var resumeAction = ""
        var resumeUap = ""
        var resumeCall: Call? = null
        var resumeUri = ""

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
        const val RESTART_REQUEST_CODE = 4

    }

    init {
        if (!BaresipService.libraryLoaded) {
            Log.d("Baresip", "Loading baresip library")
            System.loadLibrary("baresip")
            BaresipService.libraryLoaded = true
        }
    }

}

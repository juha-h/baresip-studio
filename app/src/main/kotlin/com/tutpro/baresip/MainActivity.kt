package com.tutpro.baresip

import android.Manifest
import android.app.NotificationManager
import android.content.*
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.*
import android.os.CountDownTimer
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.view.menu.ActionMenuItemView
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import android.text.TextWatcher
import android.widget.RelativeLayout
import android.widget.*
import android.view.*

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
    internal lateinit var uaAdapter: UaSpinnerAdapter
    internal lateinit var aorSpinner: Spinner
    internal lateinit var am: AudioManager
    internal lateinit var rt: Ringtone
    internal lateinit var imm: InputMethodManager
    internal lateinit var nm: NotificationManager
    internal lateinit var serviceEventReceiver: BroadcastReceiver
    internal lateinit var quitTimer: CountDownTimer
    internal lateinit var stopState: String

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        Log.d("Baresip", "onCreate with intent action " +
                intent.getStringExtra("action"))

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)

        setContentView(R.layout.activity_main)

        layout = findViewById(R.id.mainActivityLayout) as RelativeLayout
        callTitle = findViewById(R.id.callTitle) as TextView
        callUri = findViewById(R.id.callUri) as AutoCompleteTextView
        securityButton = findViewById(R.id.securityButton) as ImageButton
        callButton = findViewById(R.id.callButton) as ImageButton
        hangupButton = findViewById(R.id.hangupButton) as ImageButton
        answerButton = findViewById(R.id.answerButton) as ImageButton
        rejectButton = findViewById(R.id.rejectButton) as ImageButton
        holdButton = findViewById(R.id.holdButton) as ImageButton
        dtmf = findViewById(R.id.dtmf) as EditText
        voicemailButton = findViewById(R.id.voicemailButton) as ImageButton
        contactsButton = findViewById(R.id.contactsButton) as ImageButton
        messagesButton = findViewById(R.id.messagesButton) as ImageButton
        callsButton = findViewById(R.id.callsButton) as ImageButton
        dialpadButton = findViewById(R.id.dialpadButton) as ImageButton

        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val rtUri = RingtoneManager.getActualDefaultRingtoneUri(applicationContext,
                RingtoneManager.TYPE_RINGTONE)
        rt = RingtoneManager.getRingtone(applicationContext, rtUri)
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
        quitTimer = object: CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                Log.d("Baresip", "seconds remaining: ${millisUntilFinished/1000}")
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
                    }
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("Baresip", "Baresip does not have RECORD_AUDIO permission")
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION)
        }

        aorSpinner = findViewById(R.id.AoRList)
        uaAdapter = UaSpinnerAdapter(applicationContext, UserAgent.uas(), UserAgent.status())
        aorSpinner.adapter = uaAdapter
        aorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                Log.d("Baresip", "aorSpinner selecting $position")
                val acc = UserAgent.uas()[position].account
                val aor = acc.aor
                val ua = UserAgent.uas()[position]
                Log.d("Baresip", "Setting $aor current")
                Api.uag_current_set(UserAgent.uas()[position].uap)
                showCall(ua)
                if (acc.vmUri != "") {
                    if (acc.vmNew > 0)
                        voicemailButton.setImageResource(R.drawable.voicemail_new)
                    else
                        voicemailButton.setImageResource(R.drawable.voicemail)
                    voicemailButton.visibility = View.VISIBLE
                } else {
                    voicemailButton.visibility = View.INVISIBLE
                }
                if (acc.missedCalls)
                    callsButton.setImageResource(R.drawable.calls_missed)
                else
                    callsButton.setImageResource(R.drawable.calls)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                Log.d("Baresip", "Nothing selected")
            }
        }

        aorSpinner.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (aorSpinner.selectedItemPosition == -1) {
                    val i = Intent(this, AccountsActivity::class.java)
                    val b = Bundle()
                    b.putString("accp", "")
                    i.putExtras(b)
                    startActivityForResult(i, ACCOUNTS_CODE)
                    true
                } else {
                    if ((event.x - view.left) < 100) {
                        val i = Intent(this, AccountsActivity::class.java)
                        val b = Bundle()
                        b.putString("accp",
                                UserAgent.uas()[aorSpinner.selectedItemPosition].account.accp)
                        i.putExtras(b)
                        startActivityForResult(i, ACCOUNTS_CODE)
                        true
                    } else {
                        false
                    }
                }
            } else {
                false
            }
        }

        ContactsActivity.restoreContacts(applicationContext.filesDir.absolutePath)
        callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                Contact.contacts().map{Contact -> Contact.name}))
        callUri.threshold = 2

        securityButton.setOnClickListener {
            when (securityButton.tag) {
                "red" -> {
                    Utils.alertView(this, "Alert", "This call is NOT secure!")
                }
                "yellow" -> {
                    Utils.alertView(this, "Alert",
                            "This call is SECURE, but peer is NOT verified!")
                }
                "green" -> {
                    val unverifyDialog = AlertDialog.Builder(this)
                    unverifyDialog.setTitle("Info")
                    unverifyDialog.setMessage("This call is SECURE and peer is VERIFIED! " +
                            "Do you want to unverify the peer?")
                    unverifyDialog.setPositiveButton("Unverify") { dialog, _ ->
                        val calls = Call.uaCalls(UserAgent.uas()[aorSpinner.selectedItemPosition],
                                "")
                        if (calls.size > 0) {
                            if (Api.cmd_exec("zrtp_unverify " + calls[0].zid) != 0) {
                                Log.w("Baresip",
                                        "Command 'zrtp_unverify ${calls[0].zid}' failed")
                            } else {
                                securityButton.setImageResource(R.drawable.box_yellow)
                                securityButton.tag = "yellow"
                            }
                        }
                        dialog.dismiss()
                    }
                    unverifyDialog.setNegativeButton("No") { dialog, _ ->
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
                    if (!Utils.checkSipUri(uri))
                        Utils.alertView(this,"Notice","Invalid SIP URI '$uri'")
                    else
                        call(ua, uri)
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
            val callp = Call.uaCalls(ua,"in")[0].callp
            Log.d("Baresip", "AoR $aor answering call $callp from ${callUri.text}")
            answerButton.isEnabled = false
            Api.ua_answer(ua.uap, callp)
        }

        rejectButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val callp = Call.uaCalls(ua,"in")[0].callp
            Log.d("Baresip", "AoR $aor rejecting call $callp from ${callUri.text}")
            rejectButton.isEnabled = false
            Api.ua_hangup(ua.uap, callp, 486, "Rejected")
        }

        holdButton.setOnClickListener {
            val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val call = Call.uaCalls(ua,"")[0]
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

        dtmf.tag = ArrayList<TextWatcher>()

        voicemailButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val ua = UserAgent.uas()[aorSpinner.selectedItemPosition]
                val acc = ua.account
                if (acc.vmUri != "") {
                    val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                        when (which) {
                            DialogInterface.BUTTON_NEGATIVE -> {
                                val callIntent = Intent(this, MainActivity::class.java)
                                callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                callIntent.putExtra("action", "call")
                                callIntent.putExtra("uap", ua.uap)
                                callIntent.putExtra("peer", acc.vmUri)
                                startActivity(callIntent)
                            }
                            DialogInterface.BUTTON_POSITIVE -> {
                            }
                        }
                    }
                    val builder = AlertDialog.Builder(this@MainActivity,
                            R.style.Theme_AppCompat)
                    builder.setTitle("Voicemail Messages")
                    builder.setMessage(acc.vmMessage())
                            .setPositiveButton("Cancel", dialogClickListener)
                            .setNegativeButton("Check", dialogClickListener)
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

        ChatsActivity.restoreMessages(applicationContext.filesDir.absolutePath)
        messagesButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val i = Intent(this@MainActivity, ChatsActivity::class.java)
                val b = Bundle()
                b.putString("aor", UserAgent.uas()[aorSpinner.selectedItemPosition].account.aor)
                b.putString("peer", "")
                b.putBoolean("focus", false)
                i.putExtras(b)
                startActivityForResult(i, MESSAGES_CODE)
            }
        }

        CallHistory.restore(applicationContext.filesDir.absolutePath)
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
        }

        if (intent.hasExtra("onStartup"))
            moveTaskToBack(true)

        if (intent.hasExtra("action")) {
            // MainActivity was not visible when call, message, or transfer request came in
            handleIntent(intent)
        }
    }

    private fun handleServiceEvent(event: String, params: ArrayList<String>) {
        if (event == "stopped") {
            Log.d("Baresip", "Handling service event 'stopped'")
            quitTimer.cancel()
            finishAndRemoveTask()
            System.exit(0)
            return
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
                    "registered", "registering failed", "unregistering" -> {
                        uaAdapter.notifyDataSetChanged()
                    }
                    "call ringing" -> {
                    }
                    "call rejected" -> {
                        if (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                            callsButton.setImageResource(R.drawable.calls_missed)
                        }
                    }
                    "call incoming" -> {
                        val callp = params[1]
                        if (ContextCompat.checkSelfPermission(applicationContext,
                                        Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_DENIED) {
                            Toast.makeText(applicationContext,
                                    "You have not granted microphone permission.",
                                    Toast.LENGTH_SHORT).show()
                            Api.ua_hangup(uap, callp, 486, "Busy Here")
                            return
                        }
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.e("Baresip", "Incoming call $callp not found")
                            return
                        }
                        if (ua != UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                            aorSpinner.setSelection(account_index)
                        } else {
                            callTitle.text = "Incoming call from ..."
                            callUri.setAdapter(null)
                            callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(call.peerURI),
                                    Utils.aorDomain(ua.account.aor)))
                            securityButton.visibility = View.INVISIBLE
                            callButton.visibility = View.INVISIBLE
                            hangupButton.visibility = View.INVISIBLE
                            answerButton.visibility = View.VISIBLE
                            answerButton.isEnabled = true
                            rejectButton.visibility = View.VISIBLE
                            rejectButton.isEnabled = true
                            holdButton.visibility = View.INVISIBLE
                            dtmf.visibility = View.INVISIBLE
                        }
                        if (Utils.isVisible()) {
                                Log.d("Baresip", "Baresip is visible")
                                val i = Intent(applicationContext, MainActivity::class.java)
                                i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                startActivity(i)
                        }
                    }
                    "call established" -> {
                        val callp = params[1]
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.e("Baresip", "Established call $callp not found")
                            return
                        }
                        if (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                            if (call.dir == "in")
                                callTitle.text = "Incoming call from ..."
                            else
                                callTitle.text = "Outgoing call to ..."
                            if (acc.mediaenc == "") {
                                securityButton.visibility = View.INVISIBLE
                            } else {
                                securityButton.setImageResource(R.drawable.box_red)
                                securityButton.tag = "red"
                                securityButton.visibility = View.VISIBLE
                            }
                            callButton.visibility = View.INVISIBLE
                            hangupButton.visibility = View.VISIBLE
                            hangupButton.isEnabled = true
                            answerButton.visibility = View.INVISIBLE
                            rejectButton.visibility = View.INVISIBLE
                            holdButton.setImageResource(R.drawable.pause)
                            holdButton.visibility = View.VISIBLE
                            dtmf.setText("")
                            dtmf.hint = "DTMF"
                            dtmf.visibility = View.VISIBLE
                            dtmf.requestFocus()
                        }
                        (dtmf.tag as ArrayList<TextWatcher>).add(call.dtmfWatcher!!)
                        dtmf.addTextChangedListener(call.dtmfWatcher)
                    }
                    "call verify" -> {
                        val callp = params[1]
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.e("Baresip", "Call $callp to be verified is not found")
                            return
                        }
                        val verifyDialog = AlertDialog.Builder(this@MainActivity)
                        verifyDialog.setTitle("Verify")
                        verifyDialog.setMessage("Do you want to verify SAS <${ev[1]}> <${ev[2]}>?")
                        verifyDialog.setPositiveButton("Yes") { dialog, _ ->
                            val security: Int
                            if (Api.cmd_exec("zrtp_verify ${ev[3]}") != 0) {
                                Log.w("Baresip", "Command 'zrtp_verify ${ev[3]}' failed")
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
                        verifyDialog.setNegativeButton("No") { dialog, _ ->
                            call.security = R.drawable.box_yellow
                            call.zid = ev[3]
                            if (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                                securityButton.setImageResource(R.drawable.box_yellow)
                                securityButton.tag = "yellow"
                                securityButton.visibility = View.VISIBLE
                            }
                            dialog.dismiss()
                        }
                        verifyDialog.create().show()
                    }
                    "call verified", "call secure" -> {
                        val callp = params[1]
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.e("Baresip", "Call $callp that is verified is not found")
                            return
                        }
                        val tag: String
                        if (ev[0] == "call secure") {
                            call.security = R.drawable.box_yellow
                            tag = "yellow"
                        } else {
                            call.security = R.drawable.box_green
                            tag = "green"
                            call.zid = ev[1]
                        }
                        if (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                            securityButton.setImageResource(call.security)
                            securityButton.tag = tag
                            securityButton.visibility = View.VISIBLE
                        }
                    }
                    "call transfer" -> {
                        val callp = params[1]
                        val call = Call.find(callp)
                        if (call == null) {
                            Log.e("Baresip", "Call $callp to be transferred is not found")
                            return
                        }
                        val transferDialog = AlertDialog.Builder(this)
                        val target = Utils.friendlyUri(ContactsActivity.contactName(ev[1]),
                                Utils.aorDomain(aor))
                        transferDialog.setMessage("Do you accept to transfer call to $target?")
                        transferDialog.setPositiveButton("Yes") { dialog, _ ->
                            if (call in Call.calls())
                                transfer(ua, call, ev[1])
                            else
                                call(ua, ev[1])
                            dialog.dismiss()
                        }
                        transferDialog.setNegativeButton("No") { dialog, _ ->
                            if (call in Call.calls())
                                Api.call_notify_sipfrag(callp, 603, "Decline")
                            dialog.dismiss()
                        }
                        transferDialog.create().show()
                    }
                    "call closed" -> {
                        val watchers = dtmf.tag as ArrayList<TextWatcher>
                        if (watchers.size > 0) {
                            dtmf.removeTextChangedListener(watchers[0])
                            watchers.removeAt(0)
                        }
                        if (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]) {
                            showCall(ua)
                            if (ua.account.missedCalls)
                                callsButton.setImageResource(R.drawable.calls_missed)
                        }
                        val speakerIcon = findViewById(R.id.speakerIcon) as ActionMenuItemView
                        speakerIcon.setBackgroundColor(ContextCompat.getColor(applicationContext,
                                    R.color.colorPrimary))
                    }
                    "message" -> {
                        val peerUri = params[1]
                        val msgText = params[2]
                        val time = params[3]
                        Log.d("Baresip", "Incoming message $aor/$peerUri/$msgText")
                        Message.add(Message(aor, peerUri, R.drawable.arrow_down_green, msgText,
                                time.toLong(), true))
                        if (Utils.isVisible()) {
                            if (ua != UserAgent.uas()[aorSpinner.selectedItemPosition])
                                aorSpinner.setSelection(account_index)
                            val i = Intent(applicationContext, ChatsActivity::class.java)
                            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            val b = Bundle()
                            b.putString("aor", ua.account.aor)
                            b.putString("peer", peerUri)
                            b.putBoolean("focus", false)
                            i.putExtras(b)
                            startActivity(i)
                        }
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
                                (ua == UserAgent.uas()[aorSpinner.selectedItemPosition]))
                            if (acc.vmNew > 0)
                                voicemailButton.setImageResource(R.drawable.voicemail_new)
                            else
                                voicemailButton.setImageResource(R.drawable.voicemail)
                    }
                    else -> Log.w("Baresip", "Unknown event '${ev[0]}'")
                }
                break
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.getStringExtra("action")
        Log.d("Baresip", "onNewIntent action '$action'")
        if (action != null)
            handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.getStringExtra("action")
        Log.d("Baresip", "Handling intent '$action'")
        when (action) {
            "call" -> {
                if (!Call.calls().isEmpty()) {
                    Toast.makeText(applicationContext, "You already have an active call!",
                            Toast.LENGTH_SHORT).show()
                    return
                }
                val uap = intent.getStringExtra("uap")
                val ua = UserAgent.find(uap)
                if (ua == null) {
                    Log.e("Baresip", "handleIntent 'call' did not find ua $uap")
                    return
                }
                if (ua != UserAgent.uas()[aorSpinner.selectedItemPosition])
                    spinToAor(ua.account.aor)
                makeCall = intent.getStringExtra("peer")
            }
            "answer" -> {
                answerCall = intent.getStringExtra("callp")
            }
            "reject" -> {
                rejectCall = intent.getStringExtra("callp")
                moveTaskToBack(true)
            }
            "transfer" -> {
                val callp = intent.getStringExtra("callp")
                val call = Call.find(callp)
                val uap = intent.getStringExtra("uap")
                val ua = UserAgent.find(uap)
                if (ua == null) {
                    Log.e("Baresip", "Ua $uap of call transfer target does not exist")
                    moveTaskToBack(true)
                    return
                }
                if (call == null)
                    call(ua, intent.getStringExtra("uri"))
                else
                    transfer(ua, call, intent.getStringExtra("uri"))
            }
            "deny" -> {
                val callp = intent.getStringExtra("callp")
                val call = Call.find(callp)
                if (call == null)
                    Log.d("Baresip", "Call $callp denied transfer does not exist anymore")
                else
                    Api.call_notify_sipfrag(callp, 603, "Decline")
                moveTaskToBack(true)
            }
            "reply", "save", "delete" -> {
                val uap = intent.getStringExtra("uap")
                val ua = UserAgent.find(uap)
                if (ua == null) {
                    Log.e("Baresip", "onNewIntent did not find ua $uap")
                    return
                }
                val aor = ua.account.aor
                when (action) {
                    "reply" -> {
                        val i = Intent(this@MainActivity, ChatsActivity::class.java)
                        val b = Bundle()
                        if (ua != UserAgent.uas()[aorSpinner.selectedItemPosition])
                            spinToAor(aor)
                        b.putString("aor", aor)
                        b.putString("peer", intent.getStringExtra("peer"))
                        b.putBoolean("focus", true)
                        i.putExtras(b)
                        startActivityForResult(i, MESSAGES_CODE)
                    }
                    "save" -> {
                        ChatsActivity.saveUaMessage(ua.account.aor,
                                intent.getStringExtra("time").toLong(),
                                applicationContext.filesDir.absolutePath)
                        moveTaskToBack(true)
                    }
                    "delete" -> {
                        ChatsActivity.deleteUaMessage(ua.account.aor,
                                intent.getStringExtra("time").toLong(),
                                applicationContext.filesDir.absolutePath)
                        moveTaskToBack(true)
                    }
                }
                nm.cancel(BaresipService.MESSAGE_NOTIFICATION_ID)
            }
            "message" -> {
                val ua = UserAgent.find(intent.getStringExtra("uap"))!!
                val i = Intent(applicationContext, ChatsActivity::class.java)
                val b = Bundle()
                b.putString("aor", ua.account.aor)
                b.putString("peer", intent.getStringExtra("peer"))
                b.putBoolean("focus", true)
                i.putExtras(b)
                startActivity(i)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Log.d("Baresip", "Paused")
        visible = false
    }

    override fun onResume() {
        super.onResume()
        Log.d("Baresip", "Resumed")
        imm.hideSoftInputFromWindow(callUri.windowToken, 0)
        visible = true
        if (answerCall != "") {
            answerCall = ""
            answerButton.performClick()
        }
        if (rejectCall != "") {
            rejectCall = ""
            rejectButton.performClick()
        }
        if (makeCall != "") {
            callUri.setText(makeCall)
            makeCall = ""
            callButton.performClick()
        }
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        Log.d("Baresip", "Destroyed")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceEventReceiver)
        if (BaresipService.isServiceRunning) {
            baresipService.setAction("Kill")
            startService(baresipService)
        }
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menuInflater.inflate(R.menu.speaker_icon, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val i: Intent
        when (item.itemId) {
            R.id.speakerIcon -> {
                am.isSpeakerphoneOn = !am.isSpeakerphoneOn
                val speakerIcon = findViewById(R.id.speakerIcon) as ActionMenuItemView
                if (am.isSpeakerphoneOn)
                    speakerIcon.setBackgroundColor(Color.rgb(0x04, 0xb4, 0x04))
                else
                    speakerIcon.setBackgroundColor(ContextCompat.getColor(applicationContext,
                            R.color.colorPrimary))
                return true
            }
            R.id.accounts -> {
                i = Intent(this, AccountsActivity::class.java)
                val b = Bundle()
                b.putString("accp", "")
                i.putExtras(b)
                startActivityForResult(i, ACCOUNTS_CODE)
                return true
            }
            R.id.config -> {
                i = Intent(this, EditConfigActivity::class.java)
                startActivityForResult(i, EDIT_CONFIG_CODE)
                return true
            }
            R.id.about -> {
                Api.cmd_exec("audio_debug")
                i = Intent(this, AboutActivity::class.java)
                startActivityForResult(i, ABOUT_CODE)
                return true
            }
            R.id.quit -> {
                if (stopState == "initial") {
                    Log.d("Baresip", "Quiting")
                    if (BaresipService.isServiceRunning) {
                        baresipService.setAction("Stop");
                        startService(baresipService)
                        quitTimer.start()
                    } else {
                        finishAndRemoveTask()
                        System.exit(0)
                    }
                }
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        when (requestCode) {

            ACCOUNTS_CODE -> {
                uaAdapter.notifyDataSetChanged()
                if (UserAgent.uas().size > 0) {
                    if ((aorSpinner.selectedItemPosition == -1) ||
                            (aorSpinner.selectedItemPosition >= UserAgent.uas().size))
                            aorSpinner.setSelection(0)
                    val acc = UserAgent.uas()[aorSpinner.selectedItemPosition].account
                    if (acc.vmUri != "") {
                        if (acc.vmNew > 0)
                            voicemailButton.setImageResource(R.drawable.voicemail_new)
                        else
                            voicemailButton.setImageResource(R.drawable.voicemail)
                        voicemailButton.visibility = View.VISIBLE
                    } else {
                        voicemailButton.visibility = View.INVISIBLE
                    }
                } else {
                    aorSpinner.setSelection(-1)
                }
                baresipService.setAction("UpdateNotification")
                startService(baresipService)
            }

            ACCOUNT_CODE -> {
                val acc = UserAgent.uas()[aorSpinner.selectedItemPosition].account
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

            CONTACTS_CODE -> {
                callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                        Contact.contacts().map{Contact -> Contact.name}))
            }

            EDIT_CONFIG_CODE -> {
                if (resultCode == RESULT_OK) {
                    Utils.alertView(this, "Notice",
                            "You need to restart baresip in order to activate saved config!")
                    Api.reload_config()
                }
                if (resultCode == RESULT_CANCELED) {
                    Log.d("Baresip", "Edit config canceled")
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
                    Log.d("Baresip", "History canceled")
                    if (CallHistory.aorHistorySize(acc.aor) == 0) {
                        holdButton.visibility = View.INVISIBLE
                    }
                }
                callsButton.setImageResource(R.drawable.calls)
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

    private fun call(ua: UserAgent, uri: String) {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(applicationContext,
                    "You have not granted microphone permission.", Toast.LENGTH_SHORT).show()
            return
        }
        if (ua != UserAgent.uas()[aorSpinner.selectedItemPosition])
            spinToAor(ua.account.aor)
        callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(uri),
                Utils.aorDomain(Utils.aorDomain(ua.account.aor))))
        callButton.visibility = View.INVISIBLE
        val callp = Api.ua_connect(ua.uap, uri)
        if (callp != "") {
            Log.d("Baresip", "Adding outgoing call ${ua.uap}/$callp/$uri")
            Call.calls().add(Call(callp, ua, uri, "out", "outgoing",
                    Utils.dtmfWatcher(callp)))
            imm.hideSoftInputFromWindow(callUri.windowToken, 0)
            securityButton.visibility = View.INVISIBLE
            callButton.visibility = View.INVISIBLE
            hangupButton.visibility = View.VISIBLE
            hangupButton.isEnabled = true
            answerButton.visibility = View.INVISIBLE
            rejectButton.visibility = View.INVISIBLE
            holdButton.visibility = View.INVISIBLE
            dtmf.visibility = View.INVISIBLE
        } else {
            Log.w("Baresip", "ua_connect ${ua.uap}/$uri failed")
            callButton.visibility = View.VISIBLE
        }
    }

    private fun transfer(ua: UserAgent, call: Call, uri: String) {
        val newCallp = Api.ua_call_alloc(ua.uap, call.callp)
        if (newCallp != "") {
            Log.d("Baresip", "Adding outgoing call ${ua.uap}/$newCallp/$uri")
            val newCall = Call(newCallp, ua, uri, "out", "transferring",
                    Utils.dtmfWatcher(newCallp))
            Call.calls().add(newCall)
            Api.call_stop_audio(call.callp)
            Api.call_start_audio(newCallp)
            val err = Api.call_connect(newCallp, uri)
            if (err == 0) {
                if (ua != UserAgent.uas()[aorSpinner.selectedItemPosition])
                    spinToAor(ua.account.aor)
                showCall(ua)
            } else {
                Api.call_start_audio(call.callp)
                Log.w("Baresip", "call_connect $newCallp failed with error $err")
                Api.call_notify_sipfrag(call.callp, 500, "Call Error")
            }
        } else {
            Log.w("Baresip", "ua_call_alloc ${ua.uap}/${call.callp} failed")
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
            callTitle.text = "Outgoing call to ..."
            callUri.text.clear()
            callUri.hint = "Callee"
            callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                    Contact.contacts().map{Contact -> Contact.name}))
            imm.hideSoftInputFromWindow(callUri.windowToken, 0)
            securityButton.visibility = View.INVISIBLE
            callButton.visibility = View.VISIBLE
            hangupButton.visibility = View.INVISIBLE
            answerButton.visibility = View.INVISIBLE
            rejectButton.visibility = View.INVISIBLE
            holdButton.visibility = View.INVISIBLE
            dtmf.visibility = View.INVISIBLE
        } else {
            val callsOut = Call.uaCalls(ua, "out")
            val callsIn = Call.uaCalls(ua, "in")
            val call: Call
            if (callsOut.size > 0) {
                call = callsOut[callsOut.size - 1]
                if (call.status == "transferring")
                    callTitle.text = "Transferring call to ..."
                else
                    callTitle.text = "Outgoing call to ..."
            } else {
                callTitle.text = "Incoming call from ..."
                call = callsIn[callsIn.size - 1]
            }
            callUri.setText(Utils.friendlyUri(ContactsActivity.contactName(call.peerURI),
                    Utils.aorDomain(ua.account.aor)))
            when (call.status) {
                "outgoing", "transferring" -> {
                    securityButton.visibility = View.INVISIBLE
                    callButton.visibility = View.INVISIBLE
                    hangupButton.visibility = View.VISIBLE
                    answerButton.visibility = View.INVISIBLE
                    rejectButton.visibility = View.INVISIBLE
                    holdButton.visibility = View.INVISIBLE
                    dtmf.visibility = View.INVISIBLE
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
                    if (answerCall == call.callp) {
                        answerCall = ""
                        answerButton.performClick()
                    }
                    if (rejectCall == call.callp) {
                        rejectCall = ""
                        rejectButton.performClick()
                        moveTaskToBack(true)
                    }
                }
                "connected" -> {
                    securityButton.setImageResource(call.security)
                    setSecurityButtonTag(securityButton, call.security)
                    if ((ua.account.mediaenc == "zrtp") || (ua.account.mediaenc == "dtls_srtpf"))
                        securityButton.visibility = View.VISIBLE
                    else
                        securityButton.visibility = View.INVISIBLE
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
                    dtmf.requestFocus()
                }
            }
        }
    }

    companion object {

        var visible = true
        var makeCall = ""
        var answerCall = ""
        var rejectCall = ""

        const val ACCOUNTS_CODE = 1
        const val CONTACTS_CODE = 2
        const val EDIT_CONFIG_CODE = 3
        const val CALLS_CODE = 4
        const val ABOUT_CODE = 5
        const val ACCOUNT_CODE = 6
        const val CONTACT_CODE = 7
        const val MESSAGES_CODE = 8
        const val MESSAGE_CODE = 9
        const val RECORD_AUDIO_PERMISSION = 1

    }

    init {
        if (!BaresipService.libraryLoaded) {
            Log.d("Baresip", "Loading baresip library")
            System.loadLibrary("baresip")
            BaresipService.libraryLoaded = true
        }
    }

}

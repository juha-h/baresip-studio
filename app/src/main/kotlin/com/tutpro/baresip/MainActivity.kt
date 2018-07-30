package com.tutpro.baresip

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.view.menu.ActionMenuItemView
import android.view.inputmethod.InputMethodManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.RelativeLayout.LayoutParams
import android.widget.RelativeLayout
import android.widget.*
import android.view.*
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    lateinit var appContext: Context
    internal lateinit var layout: RelativeLayout
    internal lateinit var baresipService: Intent
    internal lateinit var callee: AutoCompleteTextView
    internal lateinit var securityButton: ImageButton
    internal lateinit var callButton: ImageButton
    internal lateinit var holdButton: ImageButton
    internal lateinit var contactsButton: ImageButton
    internal lateinit var messagesButton: ImageButton
    internal lateinit var callsButton: ImageButton
    internal lateinit var dtmf: EditText
    internal lateinit var uaAdapter: UaSpinnerAdapter
    internal lateinit var aorSpinner: Spinner
    internal lateinit var am: AudioManager
    internal lateinit var rt: Ringtone
    internal var rtTimer: Timer? = null
    internal lateinit var imm: InputMethodManager
    internal lateinit var nm: NotificationManager
    internal lateinit var serviceEventReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        Log.d("Baresip", "At onCreate")

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)

        setContentView(R.layout.activity_main)

        filesPath = applicationContext.filesDir.absolutePath

        appContext = applicationContext
        layout = findViewById(R.id.mainActivityLayout) as RelativeLayout
        callee = findViewById(R.id.callee) as AutoCompleteTextView
        securityButton = findViewById(R.id.securityButton) as ImageButton
        callButton = findViewById(R.id.callButton) as ImageButton
        holdButton = findViewById(R.id.holdButton) as ImageButton
        contactsButton = findViewById(R.id.contactsButton) as ImageButton
        messagesButton = findViewById(R.id.messagesButton) as ImageButton
        callsButton = findViewById(R.id.callsButton) as ImageButton
        dtmf = findViewById(R.id.dtmf) as EditText

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

        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("Baresip", "Baresip does not have RECORD_AUDIO permission")
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION)
        }

        aorSpinner = findViewById(R.id.AoRList)
        uaAdapter = UaSpinnerAdapter(applicationContext, uas, images)
        aorSpinner.adapter = uaAdapter
        aorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                Log.d("Baresip", "aorSpinner selecting $position")
                val acc = uas[position].account
                val aor = acc.aor
                val ua = uas[position]
                Log.d("Baresip", "Setting $aor current")
                uag_current_set(uas[position].uap)
                val callsOut = Call.uaCalls(calls, ua, "out")
                if (callsOut.size == 0) {
                    callee.text.clear()
                    callee.hint = "Callee"
                    callButton.tag = "Call"
                    callButton.setImageResource(R.drawable.call_green)
                    securityButton.visibility = View.INVISIBLE
                    dtmf.visibility = View.INVISIBLE
                } else {
                    callee.setText(callsOut[0].peerURI)
                    callButton.tag = callsOut[0].status
                    if (callsOut[0].status == "Hangup") {
                        if (callsOut[0].hold) {
                            holdButton.setImageResource(R.drawable.play)
                        } else {
                            holdButton.setImageResource(R.drawable.pause)
                        }
                        holdButton.visibility = View.VISIBLE
                        securityButton.setImageResource(callsOut[0].security)
                        setSecurityButtonTag(securityButton, callsOut[0].security)
                        if ((acc.mediaenc == "zrtp") || (acc.mediaenc == "dtls_srtpf"))
                            securityButton.visibility = View.VISIBLE
                        else
                            securityButton.visibility = View.INVISIBLE
                        dtmf.visibility = View.VISIBLE
                        dtmf.requestFocus()
                    } else {
                        holdButton.visibility = View.INVISIBLE
                        securityButton.visibility = View.INVISIBLE
                        dtmf.visibility = View.INVISIBLE
                    }
                }
                val view_count = layout.childCount
                // Log.d("Baresip", "View count is $view_count")
                if (view_count > 6)
                    layout.removeViews(6, view_count - 6)
                for (c in Call.uaCalls(calls, ua, "in"))
                    for (call_index in Call.uaCalls(calls, ua, "in").indices) {
                        val startIndex = (call_index + 1) * 10
                        addCallViews(ua, c, startIndex)
                        if (answerCall == c.callp) {
                            answerCall = ""
                            val answerButton = layout.findViewById(startIndex + 5) as ImageButton
                            answerButton.performClick()
                        }
                        if (rejectCall == c.callp) {
                            rejectCall = ""
                            val rejectButton = layout.findViewById(startIndex + 6) as ImageButton
                            rejectButton.performClick()
                            moveTaskToBack(true)
                        }
                    }
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
                        b.putString("accp", uas[aorSpinner.selectedItemPosition].account.accp)
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
                        val outCalls = Call.uaCalls(calls, uas[aorSpinner.selectedItemPosition], "out")
                        if (outCalls.size > 0) {
                            if (Api.cmd_exec("zrtp_unverify " + outCalls[0].zid) != 0) {
                                Log.w("Baresip",
                                        "Command 'zrtp_unverify ${outCalls[0].zid}' failed")
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

        callButton.tag = "Call"
        callButton.setOnClickListener {
            val ua = uas[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            when (callButton.tag) {
                "Call" -> {
                    if (!ONE_CALL_ONLY || calls.isEmpty()) {
                        val calleeText = (findViewById(R.id.callee) as EditText).text.toString()
                                .trim()
                        if (calleeText.length > 0) {
                            var uri = ContactsActivity.findContactURI(calleeText)
                            if (!uri.startsWith("sip:")) uri = "sip:$uri"
                            if (!uri.contains("@")) {
                                val host = aor.substring(aor.indexOf("@") + 1)
                                uri = "$uri@$host"
                            }
                            call(ua, uri)
                        } else {
                            val latest = CallHistory.aorLatestHistory(history, aor)
                            if (latest != null)
                                if (Utils.uriHostPart(latest.peerURI) == Utils.uriHostPart(aor))
                                    callee.setText(Utils.uriUserPart(latest.peerURI))
                                else
                                    callee.setText(latest.peerURI)
                        }
                    }
                }
                "Cancel" -> {
                    val callp = Call.calls(calls, "out")[0].callp
                    Log.d("Baresip", "Canceling AoR $aor call $callp to " +
                            (findViewById(R.id.callee) as EditText).text)
                    ua_hangup(ua.uap, callp, 486, "Rejected")
                }
                "Hangup" -> {
                    val callp = Call.calls(calls, "out")[0].callp
                    Log.d("Baresip", "Hanging up AoR $aor call $callp to " +
                            (findViewById(R.id.callee) as EditText).text)
                    callButton.isEnabled = false
                    ua_hangup(ua.uap, callp, 0, "")
                }
            }
        }

        holdButton.tag = "Hold"
        holdButton.visibility = View.INVISIBLE
        holdButton.setOnClickListener {
            when (holdButton.tag) {
                "Hold" -> {
                    holdCallAt(Call.calls(calls, "out")[0], holdButton)
                }
                "Resume" -> {
                    Log.d("Baresip", "Resuming " + (findViewById(R.id.callee) as EditText).text)
                    val call = Call.calls(calls, "out")[0]
                    call_unhold(call.callp)
                    call.hold = false
                    holdButton.tag = "Hold"
                    holdButton.setImageResource(R.drawable.pause)
                }
            }
        }

        contactsButton.setOnClickListener {
            val i = Intent(this@MainActivity, ContactsActivity::class.java)
            startActivityForResult(i, CONTACTS_CODE)
        }

        messagesButton.setOnClickListener {
            val i = Intent(this@MainActivity, MessagesActivity::class.java)
            val b = Bundle()
            b.putString("aor", uas[aorSpinner.selectedItemPosition].account.aor)
            i.putExtras(b)
            startActivityForResult(i, MESSAGES_CODE)
        }

        callsButton.setOnClickListener {
            val i = Intent(this@MainActivity, CallsActivity::class.java)
            val b = Bundle()
            b.putString("aor", uas[aorSpinner.selectedItemPosition].account.aor)
            i.putExtras(b)
            startActivityForResult(i, HISTORY_CODE)
        }

        baresipService = Intent(this@MainActivity, BaresipService::class.java)
        if (!BaresipService.IS_SERVICE_RUNNING) {
            baresipService.setAction("Start")
            startService(baresipService)
        }

        callee.threshold = 2
        callee.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                ContactsActivity.contacts.map { Contact -> Contact.name }))

        if (intent.hasExtra("onStartup"))
            moveTaskToBack(true)
    }

    private fun handleServiceEvent(event: String, params: ArrayList<String>) {
        if (event == "exit") {
            if (BaresipService.IS_SERVICE_RUNNING) {
                baresipService.setAction("Stop");
                startService(baresipService)
            }
            Toast.makeText(getApplicationContext(),
                    "Baresip has stopped! Check your network connectivity.",
                    Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val uap = params[0]
        val ua = UserAgent.find(uas, uap)
        if (ua == null) {
            Log.w("Baresip", "handleServiceEvent '$event' did not find ua $uap")
            return
        }
        val ev = event.split(",")
        Log.d("Baresip", "Handling service event ${ev[0]} for $uap")
        val aor = ua.account.aor
        val acc = ua.account
        for (account_index in uas.indices) {
            if (uas[account_index].account.aor == aor) {
                when (ev[0]) {
                    "ua added" -> {
                        uaAdapter.notifyDataSetChanged()
                        if (aorSpinner.selectedItemPosition == -1) aorSpinner.setSelection(0)
                    }
                    "registered", "registering failed", "unregistering" -> {
                        uaAdapter.notifyDataSetChanged()
                    }
                    "call incoming" -> {
                        val callp = params[1]
                        val peer_uri = Api.call_peeruri(callp)
                        val new_call = Call(callp, ua, peer_uri, "in", "Answer", null)
                        if (ONE_CALL_ONLY && (calls.size > 0)) {
                            Log.d("Baresip", "Auto-rejecting incoming call $uap/$callp/$peer_uri")
                            ua_hangup(uap, callp, 486, "Busy Here")
                            if (CallHistory.aorHistory(history, aor) > HISTORY_SIZE)
                                CallHistory.aorRemoveHistory(history, aor)
                            history.add(CallHistory(aor, peer_uri, "in", false))
                        } else {
                            Log.d("Baresip", "Incoming call $uap/$callp/$peer_uri")
                            calls.add(new_call)
                            if (ua != uas[aorSpinner.selectedItemPosition])
                                aorSpinner.setSelection(account_index)
                            else
                                addCallViews(ua, new_call, Call.uaCalls(calls, ua, "in").size * 10)
                            startRinging()
                            if (Utils.isVisible()) {
                                Log.d("Baresip", "Baresip is visible")
                                val i = Intent(applicationContext, MainActivity::class.java)
                                i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                startActivity(i)
                            }
                        }
                    }
                    "call established" -> {
                        val callp = params[1]
                        val call = Call.find(calls, callp)
                        if (call == null) {
                            Log.e("Baresip", "Established call $callp not found")
                            return
                        }
                        if (call.dir == "out") {
                            Log.d("Baresip", "Outbound call $callp established")
                            call.status = "Hangup"
                            call.hold = false
                            call.security = R.drawable.box_red
                            if (ua == uas[aorSpinner.selectedItemPosition]) {
                                callButton.tag = "Hangup"
                                callButton.setImageResource(R.drawable.hangup)
                                holdButton.tag = "Hold"
                                holdButton.setImageResource(R.drawable.pause)
                                holdButton.visibility = View.VISIBLE
                                if (acc.mediaenc == "") {
                                    securityButton.visibility = View.INVISIBLE
                                } else {
                                    securityButton.setImageResource(R.drawable.box_red)
                                    securityButton.tag = "red"
                                    securityButton.visibility = View.VISIBLE
                                }
                                dtmf.setText("")
                                dtmf.hint = "DTMF"
                                dtmf.visibility = View.VISIBLE
                                dtmf.requestFocus()
                                dtmf.addTextChangedListener(call.dtmfWatcher)
                            }
                            history.add(CallHistory(aor, call.peerURI, "out", true))
                            call.hasHistory = true
                        } else {
                            Log.d("Baresip", "Inbound call $callp established")
                            call.status = "Hangup"
                            call.security = R.drawable.box_red
                            if (ua == uas[aorSpinner.selectedItemPosition]) {
                                if (acc.mediaenc != "") {
                                    val inIndex = Call.uaCallIndex(calls, ua, call, "in")
                                    val view_id = (inIndex + 1) * 10 + 3
                                    val securityButton = findViewById(view_id) as ImageButton
                                    securityButton.setImageResource(R.drawable.box_red)
                                    securityButton.tag = "red"
                                    securityButton.visibility = View.VISIBLE
                                    val rejectButton = findViewById(view_id + 3) as ImageButton
                                    rejectButton.tag = "Hold"
                                    rejectButton.setImageResource(R.drawable.pause)
                                }
                            }
                            history.add(CallHistory(aor, call.peerURI, "in", true))
                            call.hasHistory = true
                        }
                        if (rtTimer != null) {
                            rtTimer!!.cancel()
                            rtTimer = null
                            if (rt.isPlaying) rt.stop()
                        }
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                        am.isSpeakerphoneOn = false
                        // am.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                        //        (am.getStreamMaxVolume(STREAM_VOICE_CALL) / 2) + 1,
                        //        AudioManager.STREAM_VOICE_CALL)
                        if (CallHistory.aorHistory(history, aor) > HISTORY_SIZE)
                            CallHistory.aorRemoveHistory(history, aor)
                    }
                    "call verify" -> {
                        val callp = params[1]
                        val call = Call.find(calls, callp)
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
                            if (call.dir == "out") {
                                securityButton.setImageResource(security)
                                setSecurityButtonTag(securityButton, security)
                                securityButton.visibility = View.VISIBLE
                            } else {
                                if (ua == uas[aorSpinner.selectedItemPosition]) {
                                    val view_id = (Call.uaCallIndex(calls, ua, call, "in") + 1) * 10 + 3
                                    val securityButton = layout.findViewById(view_id) as ImageButton
                                    securityButton.setImageResource(security)
                                    setSecurityButtonTag(securityButton, security)
                                    securityButton.visibility = View.VISIBLE
                                }
                            }
                            dialog.dismiss()
                        }
                        verifyDialog.setNegativeButton("No") { dialog, _ ->
                            call.security = R.drawable.box_yellow
                            call.zid = ev[3]
                            if (call.dir == "out") {
                                securityButton.setImageResource(R.drawable.box_yellow)
                                securityButton.tag = "yellow"
                                securityButton.visibility = View.VISIBLE
                            } else {
                                if (ua == uas[aorSpinner.selectedItemPosition]) {
                                    val view_id = (Call.uaCallIndex(calls, ua, call, "in") + 1) * 10 + 3
                                    val securityButton = layout.findViewById(view_id) as ImageButton
                                    securityButton.setImageResource(R.drawable.box_yellow)
                                    securityButton.tag = "yellow"
                                    securityButton.visibility = View.VISIBLE
                                }
                            }
                            dialog.dismiss()
                        }
                        verifyDialog.create().show()
                    }
                    "call verified", "call secure" -> {
                        val callp = params[1]
                        val call = Call.find(calls, callp)
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
                        if (call.dir == "out") {
                            if (ua == uas[aorSpinner.selectedItemPosition]) {
                                securityButton.setImageResource(call.security)
                                securityButton.tag = tag
                                securityButton.visibility = View.VISIBLE
                            }
                        } else {
                            if (ua == uas[aorSpinner.selectedItemPosition]) {
                                val view_id = (Call.uaCallIndex(calls, ua, call, "in") + 1) * 10 + 3
                                val securityButton = layout.findViewById(view_id) as ImageButton
                                securityButton.setImageResource(call.security)
                                securityButton.tag = tag
                                securityButton.visibility = View.VISIBLE
                            }
                        }
                    }
                    "call closed" -> {
                        val callp = params[1]
                        val call = Call.find(calls, callp)
                        if (call == null) {
                            Log.d("Baresip", "Call $callp that is closed is not found")
                            return
                        }
                        if (call.dir == "in") {
                            Log.d("Baresip", "Removing inbound call ${uap}/${callp}/" +
                                    call.peerURI)
                            val inIndex = Call.uaCallIndex(calls, ua, call, "in")
                            val view_id = (inIndex + 1) * 10
                            val remove_count = Call.uaCalls(calls, ua, "in").size - inIndex
                            calls.remove(call)
                            if (ua == uas[aorSpinner.selectedItemPosition]) {
                                if (callButton.tag == "Call") {
                                    callee.setOnKeyListener(null)
                                }
                                val caller_heading = layout.findViewById(view_id) as TextView?
                                if (caller_heading != null) {
                                    val view_index = layout.indexOfChild(caller_heading)
                                    layout.removeViews(view_index, 3 * remove_count)
                                    val callsIn = Call.uaCalls(calls, ua, "in")
                                    for (i in inIndex until callsIn.size) {
                                        addCallViews(ua, callsIn[i], (i + 1) * 10)
                                    }
                                }
                            }
                            if (!call.hasHistory) {
                                if (CallHistory.aorHistory(history, aor) > HISTORY_SIZE)
                                    CallHistory.aorRemoveHistory(history, aor)
                                history.add(CallHistory(aor, call.peerURI, "in", false))
                            }
                            stopRinging()
                        } else {
                            Log.d("Baresip", "Removing outgoing call $uap/$callp/" +
                                    call.peerURI)
                            if (ua == uas[aorSpinner.selectedItemPosition]) {
                                callButton.tag = "Call"
                                callButton.setImageResource(R.drawable.call_green)
                                callButton.isEnabled = true
                                callee.setText("")
                                callee.hint = "Callee"
                                holdButton.visibility = View.INVISIBLE
                                if (currentFocus == dtmf) {
                                    imm.hideSoftInputFromWindow(dtmf.windowToken, 0)
                                }
                                securityButton.visibility = View.INVISIBLE
                                dtmf.removeTextChangedListener(call.dtmfWatcher)
                                dtmf.visibility = View.INVISIBLE
                            }
                            calls.remove(call)
                            if (!call.hasHistory) {
                                if (CallHistory.aorHistory(history, aor) > HISTORY_SIZE)
                                    CallHistory.aorRemoveHistory(history, aor)
                                history.add(CallHistory(aor, call.peerURI, "out",
                                        false))
                            }
                        }
                        if (calls.size == 0) am.mode = AudioManager.MODE_NORMAL
                    }
                    "message" -> {
                        val peer_uri = params[1]
                        val msg = params[2]
                        val time = params[3]
                        val new_message = Message(aor, peer_uri, R.drawable.arrow_down_green, msg,
                                time.toLong(), true)
                        Log.d("Baresip", "Incoming message $aor/$peer_uri/$msg")
                        MessagesActivity.addMessage(new_message)
                        if (Utils.isVisible()) {
                            if (ua != uas[aorSpinner.selectedItemPosition])
                                aorSpinner.setSelection(account_index)
                            val i = Intent(applicationContext, MessagesActivity::class.java)
                            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            val b = Bundle()
                            b.putString("aor", ua.account.aor)
                            i.putExtras(b)
                            startActivity(i)
                        }
                    }
                    else -> Log.w("Baresip", "Unknown event '${ev[0]}'")
                }
                break
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        val action = intent.getStringExtra("action")
        Log.d("Baresip", "Got onNewIntent action $action")
        when (action) {
            "call" -> {
                if (!calls.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "You already have an active call!",
                            Toast.LENGTH_SHORT).show()
                    return
                }
                val uap = intent.getStringExtra("uap")
                val ua = UserAgent.find(MainActivity.uas, uap)
                if (ua == null) {
                    Log.e("Baresip", "onNewIntent did not find ua $uap")
                    return
                }
                val aor = ua.account.aor
                if (ua != uas[aorSpinner.selectedItemPosition]) {
                    for (account_index in uas.indices) {
                        if (uas[account_index].account.aor == aor) {
                            aorSpinner.setSelection(account_index)
                            break
                        }
                    }
                }
                makeCall = intent.getStringExtra("peer")
            }
            "answer" -> {
                answerCall = intent.getStringExtra("callp")
            }
            "reject" -> {
                rejectCall = intent.getStringExtra("callp")
                moveTaskToBack(true)
            }
            "reply", "archive", "delete" -> {
                val uap = intent.getStringExtra("uap")
                val ua = UserAgent.find(MainActivity.uas, uap)
                if (ua == null) {
                    Log.e("Baresip", "onNewIntent did not find ua $uap")
                    return
                }
                val aor = ua.account.aor
                when (action) {
                    "reply" -> {
                        val i = Intent(this@MainActivity, MessagesActivity::class.java)
                        val b = Bundle()
                        if (ua != uas[aorSpinner.selectedItemPosition]) {
                            for (account_index in uas.indices) {
                                if (uas[account_index].account.aor == aor) {
                                    aorSpinner.setSelection(account_index)
                                    break
                                }
                            }
                        }
                        b.putString("aor", aor)
                        b.putString("peer", intent.getStringExtra("peer"))
                        b.putBoolean("reply", true)
                        i.putExtras(b)
                        startActivityForResult(i, MESSAGES_CODE)
                    }
                    "archive" -> {
                        MessagesActivity.archiveUaMessage(ua.account.aor,
                                intent.getStringExtra("time").toLong())
                        moveTaskToBack(true)
                    }
                    "delete" -> {
                        MessagesActivity.deleteUaMessage(ua.account.aor,
                                intent.getStringExtra("time").toLong())
                        moveTaskToBack(true)
                    }
                }
                nm.cancel(BaresipService.MESSAGE_NOTIFICATION_ID)
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
        // Log.d("Baresip", "Resumed")
        imm.hideSoftInputFromWindow(callee.windowToken, 0)
        visible = true
        if ((answerCall != "") && (layout.childCount > 6)) {
            answerCall = ""
            /* if multiple calls, right answer button needs to be searched */
            val answerButton = layout.findViewById(10 + 5) as ImageButton
            answerButton.performClick()
        }
        if ((rejectCall != "") && (layout.childCount > 6)) {
            rejectCall = ""
            /* if multiple calls, right reject button needs to be searched */
            val rejectButton = layout.findViewById(10 + 6) as ImageButton
            rejectButton.performClick()
        }
        if (makeCall != "") {
            callee.setText(makeCall)
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
        if (BaresipService.IS_SERVICE_RUNNING) {
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
                startActivityForResult(i, ACCOUNTS_CODE)
                return true
            }
            R.id.contacts -> {
                i = Intent(this, ContactsActivity::class.java)
                startActivityForResult(i, CONTACTS_CODE)
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
                Log.d("Baresip", "Quiting")
                if (BaresipService.IS_SERVICE_RUNNING) {
                    baresipService.setAction("Stop");
                    startService(baresipService)
                }
                finish()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        when (requestCode) {

            ACCOUNTS_CODE -> {
                uaAdapter.notifyDataSetChanged()
                baresipService.setAction("UpdateNotification")
                startService(baresipService)
            }

            CONTACTS_CODE -> {
                callee.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                        ContactsActivity.contacts.map{Contact -> Contact.name}))
            }

            EDIT_CONFIG_CODE -> {
                if (resultCode == RESULT_OK) {
                    Utils.alertView(this, "Notice",
                            "You need to restart baresip in order to activate saved config!")
                    reload_config()
                }
                if (resultCode == RESULT_CANCELED) {
                    Log.d("Baresip", "Edit config canceled")
                }
            }

            HISTORY_CODE -> {
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        (findViewById(R.id.callee) as EditText).setText(data.getStringExtra("peer_uri"))
                    }
                }
                if (resultCode == RESULT_CANCELED) {
                    Log.d("Baresip", "History canceled")
                    if (CallHistory.aorHistory(history, uas[aorSpinner.selectedItemPosition].account.aor) == 0) {
                        holdButton.visibility = View.INVISIBLE
                    }
                }
            }

            ABOUT_CODE -> {
            }
        }
    }

    private fun call(ua: UserAgent, uri: String) {
        (findViewById(R.id.callee) as EditText).setText(uri)
        val call = ua_connect(ua.uap, uri)
        if (call != "") {
            Log.d("Baresip", "Adding outgoing call ${ua.uap}/$call/$uri")
            val dtmfWatcher = object : TextWatcher {
                override fun beforeTextChanged(sequence: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(sequence: CharSequence, start: Int, before: Int, count: Int) {
                    val digit = sequence.subSequence(start, start + count).toString()
                    Log.d("Baresip", "Got DTMF digit '" + digit + "'")
                    if (digit.isNotEmpty()) call_send_digit(call, digit[0])
                }
                override fun afterTextChanged(sequence: Editable) {
                    call_send_digit(call, 4.toChar())
                }
            }
            calls.add(Call(call, ua, uri, "out", "Cancel", dtmfWatcher))
            callButton.tag = "Cancel"
            callButton.setImageResource(R.drawable.hangup)
            holdButton.visibility = View.INVISIBLE
        } else {
            Log.w("Baresip", "ua_connect ${ua.uap}/$uri failed")
        }
    }

    private fun addCallViews(ua: UserAgent, call: Call, id: Int) {
        Log.d("Baresip", "Adding incoming views for UA ${ua.uap} Call ${call.callp}")
        val acc = ua.account
        val caller_heading = TextView(appContext)
        caller_heading.text = "Incoming call from ..."
        caller_heading.setTextColor(Color.BLACK)
        caller_heading.textSize = 20f
        caller_heading.setPadding(10, 20, 0, 0)
        caller_heading.id = id
        val heading_params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        if (id == 10)
            heading_params.addRule(RelativeLayout.BELOW, callButton.id)
        else
            heading_params.addRule(RelativeLayout.BELOW, id - 10 + 4)
        caller_heading.layoutParams = heading_params
        // Log.d("Baresip", "Adding incoming call heading at ${caller_heading.id}")
        layout.addView(caller_heading)

        val caller_row = LinearLayout(applicationContext)
        caller_row.id = caller_heading.id + 1
        caller_row.setOrientation(LinearLayout.HORIZONTAL)
        val caller_row_params = LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        caller_row_params.addRule(RelativeLayout.BELOW, id)
        caller_row.layoutParams = caller_row_params

        val caller_uri = TextView(applicationContext)
        caller_uri.text = ContactsActivity.contactName(call.peerURI)
        caller_uri.setTextColor(Color.BLUE)
        caller_uri.textSize = 20f
        caller_uri.setPadding(10, 10, 0, 10)
        caller_uri.id = caller_row.id + 1
        val caller_uri_params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        caller_uri.layoutParams = caller_uri_params
        // Log.d("Baresip", "Adding caller uri at ${caller_uri.id}")
        caller_row.addView(caller_uri)

        val security_button = ImageButton(applicationContext)
        security_button.id = caller_uri.id + 1
        val dp24px = ((24 * applicationContext.resources.displayMetrics.density) + 0.5).toInt()
        val security_button_params = LinearLayout.LayoutParams(dp24px, dp24px, 0.0f)
        security_button_params.gravity = Gravity.CENTER_VERTICAL
        security_button.layoutParams = security_button_params
        if ((call.security != 0) && (acc.mediaenc != "")) {
            security_button.setImageResource(call.security)
            setSecurityButtonTag(securityButton, call.security)
            security_button.visibility = View.VISIBLE
        } else {
            security_button.visibility = View.INVISIBLE
        }
        security_button.setOnClickListener {
            when (security_button.tag) {
                "red" -> {
                    Utils.alertView(this, "Alert", "This call is NOT secure!")
                }
                "yellow" -> {
                    Utils.alertView(this, "Alert",
                            "This call is SECURE, but peer is NOT verified!")
                }
                "green" -> {
                    val unverifyDialog = AlertDialog.Builder(this)
                    unverifyDialog.setMessage("This call is SECURE and peer is VERIFIED! " +
                            "Do you want to unverify the peer?")
                    unverifyDialog.setPositiveButton("Unverify") { dialog, _ ->
                        if (Api.cmd_exec("zrtp_unverify " + call.zid) != 0) {
                            Log.w("Baresip", "Command 'zrtp_unverify ${call.zid}' failed")
                        } else {
                            security_button.setImageResource(R.drawable.box_yellow)
                            security_button.tag = "yellow"
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
        // Log.d("Baresip", "Adding security button at ${security_button.id}")
        caller_row.addView(security_button)

        // Log.d("Baresip", "Adding caller row at ${caller_row.id}")
        layout.addView(caller_row)

        val answer_row = LinearLayout(applicationContext)
        answer_row.id = security_button.id + 1
        answer_row.setOrientation(LinearLayout.HORIZONTAL)
        val answer_row_params = LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        answer_row_params.addRule(RelativeLayout.BELOW, caller_row.id)
        answer_row.layoutParams = answer_row_params

        val answer_button = ImageButton(applicationContext)
        answer_button.tag = call.status
        if (call.status == "Answer")
            answer_button.setImageResource(R.drawable.call_green)
        else
            answer_button.setImageResource(R.drawable.hangup)
        answer_button.background = null
        answer_button.id = answer_row.id + 1
        answer_button.setOnClickListener { v ->
            when ((v as ImageButton).tag) {
                "Answer" -> {
                    Log.d("Baresip", "UA ${ua.uap} accepting incoming call ${call.callp}")
                    ua_answer(ua.uap, call.callp)
                    if (call.dir == "in") {
                        call.status = "Hangup"
                        call.hold = false
                        answer_button.tag = "Hangup"
                        answer_button.setImageResource(R.drawable.hangup)
                        val reject_button = layout.findViewById(answer_button.id + 1) as ImageButton
                        reject_button.tag = "Hold"
                        reject_button.setImageResource(R.drawable.pause)
                    }
                }
                "Hangup" -> {
                    Log.d("Baresip", "UA ${call.ua.uap} hanging up call ${call.callp}")
                    answer_button.isEnabled = false
                    ua_hangup(call.ua.uap, call.callp, 200, "OK")
                }
                else -> Log.e("Baresip", "Invalid answer button tag: " + v.tag)
            }
        }
        val answer_button_params = LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT)
        answer_button_params.setMargins(3, 10, 0, 0)
        answer_button.layoutParams = answer_button_params
        answer_button.setPadding(0, 0, 0, 0)
        // Log.d("Baresip", "Adding answer button at ${answer_button.id}")
        answer_row.addView(answer_button)

        val reject_button = ImageButton(appContext)
        if (call.status == "Answer") {
            reject_button.tag = "Reject"
            reject_button.setImageResource(R.drawable.hangup)
        } else {
            if (call.hold) {
                reject_button.tag = "Resume"
                reject_button.setImageResource(R.drawable.play)
            } else {
                reject_button.tag = "Hold"
                reject_button.setImageResource(R.drawable.pause)
            }
        }
        reject_button.background = null
        reject_button.id = answer_button.id + 1
        reject_button.setOnClickListener { v ->
            when ((v as ImageButton).tag) {
                "Reject" -> {
                    Log.d("Baresip", "UA ${call.ua} rejecting incoming call ${call.callp}")
                    reject_button.isEnabled = false
                    answer_button.isEnabled = false
                    ua_hangup(call.ua.uap, call.callp, 486, "Rejected")
                }
                "Hold" -> {
                    holdCallAt(call, reject_button)
                }
                "Resume" -> {
                    call_unhold(call.callp)
                    v.tag = "Hold"
                    reject_button.setImageResource(R.drawable.pause)
                    call.hold = false
                }
            }
        }
        val reject_button_params = RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT)
        reject_button_params.topMargin = 10
        reject_button_params.addRule(RelativeLayout.ALIGN_PARENT_END)
        reject_button.layoutParams = reject_button_params
        reject_button.setPadding(50, 0, 0, 0)
        reject_button.background = null
        // Log.d("Baresip", "Adding reject button at ${reject_button.id}")
        answer_row.addView(reject_button)

        // Log.d("Baresip", "Adding answer row at ${answer_row.id}")
        layout.addView(answer_row)
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

    private fun holdCallAt(call: Call, button: ImageButton) {
        Log.d("Baresip", "Holding call with ${call.peerURI} at ${button.id}")
        call_hold(call.callp)
        call.hold = true
        button.tag = "Resume"
        button.setImageResource(R.drawable.play)
    }

    private fun startRinging() {
        am.mode = AudioManager.MODE_RINGTONE
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

    private fun stopRinging() {
        if (rtTimer != null) {
            rtTimer!!.cancel()
            rtTimer = null
            if (rt.isPlaying) rt.stop()
        }
    }

    external fun uag_current(): String
    external fun uag_current_set(uap: String)
    external fun ua_connect(uap: String, peer_uri: String): String
    external fun ua_answer(uap: String, callp: String)
    external fun ua_hold_answer(uap: String, callp: String): Int
    external fun ua_hangup(uap: String, callp: String, code: Int, reason: String)
    external fun call_hold(callp: String): Int
    external fun call_unhold(callp: String): Int
    external fun call_send_digit(callp: String, digit: Char): Int
    external fun reload_config(): Int

    companion object {

        var uas = ArrayList<UserAgent>()
        internal var images = ArrayList<Int>()
        var history: ArrayList<CallHistory> = ArrayList()
        internal var calls = ArrayList<Call>()
        internal var messages = ArrayList<Message>()
        var filesPath = ""
        var visible = true
        var makeCall = ""
        var answerCall = ""
        var rejectCall = ""

        const val ACCOUNTS_CODE = 1
        const val CONTACTS_CODE = 2
        const val EDIT_CONFIG_CODE = 3
        const val HISTORY_CODE = 4
        const val ABOUT_CODE = 5
        const val ACCOUNT_CODE = 6
        const val CONTACT_CODE = 7
        const val MESSAGES_CODE = 8
        const val MESSAGE_CODE = 9

        const val RECORD_AUDIO_PERMISSION = 1
        const val HISTORY_SIZE = 100
        const val MESSAGE_HISTORY_SIZE = 100
        const val ONE_CALL_ONLY = true

    }
}

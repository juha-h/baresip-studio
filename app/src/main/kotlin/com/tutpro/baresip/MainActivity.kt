package com.tutpro.baresip

import android.Manifest
import android.annotation.TargetApi
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
import android.os.Build
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.view.menu.ActionMenuItemView
import android.view.inputmethod.InputMethodManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.RelativeLayout
import android.widget.*
import android.view.*
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    lateinit var appContext: Context
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
    internal var rtTimer: Timer? = null
    internal lateinit var imm: InputMethodManager
    internal lateinit var nm: NotificationManager
    internal lateinit var serviceEventReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        Log.d("Baresip", "onCreate with intent action " +
                intent.getStringExtra("action"))

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)

        setContentView(R.layout.activity_main)

        filesPath = applicationContext.filesDir.absolutePath

        appContext = applicationContext
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

        callUri.threshold = 2
        ContactsActivity.restoreContacts(applicationContext.filesDir.path)
        callUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                ContactsActivity.contacts.map{Contact -> Contact.name}))

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
                        val calls = Call.uaCalls(calls, uas[aorSpinner.selectedItemPosition], "")
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
            val ua = uas[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            if (!ONE_CALL_ONLY || calls.isEmpty()) {
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
                    val latest = CallHistory.aorLatestHistory(history, aor)
                    if (latest != null)
                        if (Utils.uriHostPart(latest.peerURI) == Utils.uriHostPart(aor))
                            callUri.setText(Utils.uriUserPart(latest.peerURI))
                        else
                            callUri.setText(latest.peerURI)
                }
            }
        }

        hangupButton.setOnClickListener {
            val ua = uas[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val uaCalls = Call.uaCalls(calls, ua, "")
            if (uaCalls.size > 0) {
                val callp = uaCalls[uaCalls.size - 1].callp
                Log.d("Baresip", "AoR $aor hanging up call $callp with ${callUri.text}")
                hangupButton.isEnabled = false
                ua_hangup(ua.uap, callp, 0, "")
            }
        }

        answerButton.setOnClickListener {
            val ua = uas[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val callp = Call.uaCalls(calls, ua,"in")[0].callp
            Log.d("Baresip", "AoR $aor answering call $callp from ${callUri.text}")
            answerButton.isEnabled = false
            ua_answer(ua.uap, callp)
        }

        rejectButton.setOnClickListener {
            val ua = uas[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val callp = Call.uaCalls(calls, ua,"in")[0].callp
            Log.d("Baresip", "AoR $aor rejecting call $callp from ${callUri.text}")
            rejectButton.isEnabled = false
            ua_hangup(ua.uap, callp, 486, "Rejected")
        }

        holdButton.setOnClickListener {
            val ua = uas[aorSpinner.selectedItemPosition]
            val aor = ua.account.aor
            val call = Call.uaCalls(calls, ua,"")[0]
            if (call.onhold) {
                Log.d("Baresip", "AoR $aor resuming call ${call.callp} with ${callUri.text}")
                call_unhold(call.callp)
                call.onhold = false
                holdButton.setImageResource(R.drawable.pause)
            } else {
                Log.d("Baresip", "AoR $aor holding call ${call.callp} with ${callUri.text}")
                call_hold(call.callp)
                call.onhold = true
                holdButton.setImageResource(R.drawable.play)
            }
        }

        voicemailButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val ua = uas[aorSpinner.selectedItemPosition]
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
                b.putString("aor", uas[aorSpinner.selectedItemPosition].account.aor)
            else
                b.putString("aor", "")
            i.putExtras(b)
            startActivityForResult(i, CONTACTS_CODE)
        }

        ChatsActivity.restoreMessages(applicationContext.filesDir.path)
        messagesButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val i = Intent(this@MainActivity, ChatsActivity::class.java)
                val b = Bundle()
                b.putString("aor", uas[aorSpinner.selectedItemPosition].account.aor)
                b.putString("peer", "")
                i.putExtras(b)
                startActivityForResult(i, MESSAGES_CODE)
            }
        }

        CallsActivity.restoreHistory(applicationContext.filesDir.path)
        callsButton.setOnClickListener {
            if (aorSpinner.selectedItemPosition >= 0) {
                val i = Intent(this@MainActivity, CallsActivity::class.java)
                val b = Bundle()
                b.putString("aor", uas[aorSpinner.selectedItemPosition].account.aor)
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
        if (!BaresipService.IS_SERVICE_RUNNING) {
            baresipService.setAction("Start")
            startService(baresipService)
        }

        if (intent.hasExtra("onStartup"))
            moveTaskToBack(true)

        if (intent.hasExtra("action")) {
            // MainActivity was not running when call or message came in
            handleIntent(intent)
        }
    }

    private fun handleServiceEvent(event: String, params: ArrayList<String>) {
        if (event == "stopped") {
            Log.d("Baresip", "Handling service event 'stopped'")
            finishAndRemoveTask()
            System.exit(0)
            return
        }
        val uap = params[0]
        val ua = UserAgent.find(uas, uap)
        if (ua == null) {
            Log.w("Baresip", "handleServiceEvent '$event' did not find ua $uap")
            return
        }
        val ev = event.split(",")
        Log.d("Baresip", "Handling service event '${ev[0]}' for $uap")
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
                    "call ringing" -> {
                    }
                    "call incoming" -> {
                        val callp = params[1]
                        if (ContextCompat.checkSelfPermission(applicationContext,
                                        Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_DENIED) {
                            Toast.makeText(applicationContext,
                                    "You have not granted microphone permission.",
                                    Toast.LENGTH_SHORT).show()
                            ua_hangup(uap, callp, 486, "Busy Here")
                            return
                        }
                        val peer_uri = Api.call_peeruri(callp)
                        val new_call = Call(callp, ua, peer_uri, "in", "incoming",
                                dtmfWatcher(callp))
                        if (ONE_CALL_ONLY && (calls.size > 0)) {
                            Log.d("Baresip", "Auto-rejecting incoming call $uap/$callp/$peer_uri")
                            ua_hangup(uap, callp, 486, "Busy Here")
                            if (CallHistory.aorHistory(history, aor) > HISTORY_SIZE)
                                CallHistory.aorRemoveHistory(history, aor)
                            history.add(CallHistory(aor, peer_uri, "in", false))
                            CallsActivity.saveHistory()
                            acc.missedCalls = true
                            if (ua == uas[aorSpinner.selectedItemPosition]) {
                                callsButton.setImageResource(R.drawable.calls_missed)
                            }
                        } else {
                            Log.d("Baresip", "Incoming call $uap/$callp/$peer_uri")
                            calls.add(new_call)
                            if (ua != uas[aorSpinner.selectedItemPosition]) {
                                aorSpinner.setSelection(account_index)
                            } else {
                                callTitle.text = "Incoming call from ..."
                                callUri.setText(new_call.peerURI)
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
                        Log.d("Baresip", "AoR $aor call $callp established")
                        call.status = "connected"
                        call.onhold = false
                        call.security = R.drawable.box_red
                        if (ua == uas[aorSpinner.selectedItemPosition]) {
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
                            dtmf.addTextChangedListener(call.dtmfWatcher)
                        }
                        if (CallHistory.aorHistory(history, aor) > HISTORY_SIZE)
                            CallHistory.aorRemoveHistory(history, aor)
                        history.add(CallHistory(aor, call.peerURI, call.dir, true))
                        CallsActivity.saveHistory()
                        call.hasHistory = true
                        if (rtTimer != null) {
                            rtTimer!!.cancel()
                            rtTimer = null
                            if (rt.isPlaying) rt.stop()
                        }
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                        requestAudioFocus( AudioManager.STREAM_VOICE_CALL)
                        am.isSpeakerphoneOn = false
                        // am.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                        //        (am.getStreamMaxVolume(STREAM_VOICE_CALL) / 2) + 1,
                        //        AudioManager.STREAM_VOICE_CALL)
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
                            if (ua == uas[aorSpinner.selectedItemPosition]) {
                                securityButton.setImageResource(security)
                                setSecurityButtonTag(securityButton, security)
                                securityButton.visibility = View.VISIBLE
                                dialog.dismiss()
                            }
                        }
                        verifyDialog.setNegativeButton("No") { dialog, _ ->
                            call.security = R.drawable.box_yellow
                            call.zid = ev[3]
                            if (ua == uas[aorSpinner.selectedItemPosition]) {
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
                        if (ua == uas[aorSpinner.selectedItemPosition]) {
                            securityButton.setImageResource(call.security)
                            securityButton.tag = tag
                            securityButton.visibility = View.VISIBLE
                        }
                    }
                    "call transfer" -> {
                        val callp = params[1]
                        val call = Call.find(calls, callp)
                        if (call == null) {
                            Log.e("Baresip", "Call $callp to be transfered is not found")
                            return
                        }
                        val transferDialog = AlertDialog.Builder(this)
                        transferDialog.setMessage("Do you want to transfer this call to ${ev[1]}?")
                        transferDialog.setPositiveButton("Yes") { dialog, _ ->
                            if (call in calls)
                                transfer(ua, call, ev[1])
                            else
                                call(ua, ev[1])
                            dialog.dismiss()
                        }
                        transferDialog.setNegativeButton("No") { dialog, _ ->
                            if (call in calls)
                                call_notify_sipfrag(callp, 603, "Decline")
                            dialog.dismiss()
                        }
                        transferDialog.create().show()
                    }
                    "transfer failed" -> {
                        val callp = params[1]
                        Log.d("Baresip", "AoR $aor hanging up call $callp with ${ev[1]}")
                        ua_hangup(ua.uap, callp, 0, "")
                    }
                    "call closed" -> {
                        val callp = params[1]
                        val call = Call.find(calls, callp)
                        if (call == null) {
                            Log.d("Baresip", "Call $callp that is closed is not found")
                            return
                        }
                        Log.d("Baresip", "Removing call ${uap}/${callp}/" + call.peerURI)
                        stopRinging()
                        if (ua == uas[aorSpinner.selectedItemPosition]) {
                            dtmf.removeTextChangedListener(call.dtmfWatcher)
                        }
                        calls.remove(call)
                        if (ua == uas[aorSpinner.selectedItemPosition]) {
                            showCall(ua)
                        }
                        if (!call.hasHistory) {
                            if (CallHistory.aorHistory(history, aor) > HISTORY_SIZE)
                                CallHistory.aorRemoveHistory(history, aor)
                            history.add(CallHistory(aor, call.peerURI, call.dir,false))
                            CallsActivity.saveHistory()
                            if (call.dir == "in") {
                                acc.missedCalls = true
                                if (ua == uas[aorSpinner.selectedItemPosition])
                                    callsButton.setImageResource(R.drawable.calls_missed)
                            }
                        }
                        if (calls.size == 0) {
                            am.mode = AudioManager.MODE_NORMAL
                        } else {
                            val uaCalls = Call.uaCalls(calls, ua, "")
                            if (uaCalls.size > 0) call_start_audio(uaCalls[uaCalls.size - 1].callp)
                        }
                        if (am.isSpeakerphoneOn) {
                            am.isSpeakerphoneOn = false
                            val speakerIcon = findViewById(R.id.speakerIcon) as ActionMenuItemView
                            speakerIcon.setBackgroundColor(ContextCompat.getColor(applicationContext,
                                    R.color.colorPrimary))
                        }
                        if (audioFocused) {
                            abandonAudioFocus()
                        }
                    }
                    "message" -> {
                        val peerUri = params[1]
                        val msgText = params[2]
                        val time = params[3]
                        val newMsg = Message(aor, peerUri, R.drawable.arrow_down_green, msgText,
                                time.toLong(), true)
                        Log.d("Baresip", "Incoming message $aor/$peerUri/$msgText")
                        messages.add(newMsg)
                        ChatsActivity.saveMessages()
                        if (Utils.isVisible()) {
                            if (ua != uas[aorSpinner.selectedItemPosition])
                                aorSpinner.setSelection(account_index)
                            val i = Intent(applicationContext, ChatsActivity::class.java)
                            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            val b = Bundle()
                            b.putString("aor", ua.account.aor)
                            b.putString("peer", peerUri)
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
                                (ua == uas[aorSpinner.selectedItemPosition]))
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
                if (!calls.isEmpty()) {
                    Toast.makeText(applicationContext, "You already have an active call!",
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
                        val i = Intent(this@MainActivity, ChatActivity::class.java)
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
                        startActivityForResult(i, MESSAGE_CODE)
                    }
                    "archive" -> {
                        ChatsActivity.archiveUaMessage(ua.account.aor,
                                intent.getStringExtra("time").toLong())
                        moveTaskToBack(true)
                    }
                    "delete" -> {
                        ChatsActivity.deleteUaMessage(ua.account.aor,
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
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        when (requestCode) {

            ACCOUNTS_CODE -> {
                uaAdapter.notifyDataSetChanged()
                if (uas.size > 0) {
                    if ((aorSpinner.selectedItemPosition == -1) ||
                            (aorSpinner.selectedItemPosition >= uas.size))
                            aorSpinner.setSelection(0)
                    val acc = uas[aorSpinner.selectedItemPosition].account
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
                val acc = uas[aorSpinner.selectedItemPosition].account
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

            CALLS_CODE -> {
                val acc = uas[aorSpinner.selectedItemPosition].account
                if (resultCode == RESULT_OK) {
                    if (data != null)
                        callUri.setText(data.getStringExtra("peer_uri"))
                }
                if (resultCode == RESULT_CANCELED) {
                    Log.d("Baresip", "History canceled")
                    if (CallHistory.aorHistory(history, acc.aor) == 0) {
                        holdButton.visibility = View.INVISIBLE
                    }
                }
                callsButton.setImageResource(R.drawable.calls)
            }

            ABOUT_CODE -> {
            }
        }
    }

    private fun call(ua: UserAgent, uri: String) {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(applicationContext,
                    "You have not granted microphone permission.", Toast.LENGTH_SHORT).show()
            return
        }
        callUri.setText(uri)
        callButton.visibility = View.INVISIBLE
        val callp = ua_connect(ua.uap, uri)
        if (callp != "") {
            Log.d("Baresip", "Adding outgoing call ${ua.uap}/$callp/$uri")
            calls.add(Call(callp, ua, uri, "out", "outgoing", dtmfWatcher(callp)))
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
        val newCallp = ua_call_alloc(ua.uap, call.callp)
        if (newCallp != "") {
            Log.d("Baresip", "Adding outgoing call ${ua.uap}/$newCallp/$uri")
            val newCall = Call(newCallp, ua, uri, "out", "transferring",
                    dtmfWatcher(newCallp))
            calls.add(newCall)
            call_stop_audio(call.callp)
            call_start_audio(newCallp)
            val err = call_connect(newCallp, uri)
            if (err == 0) {
                if (ua == uas[aorSpinner.selectedItemPosition]) showCall(ua)
            } else {
                call_start_audio(call.callp)
                Log.w("Baresip", "call_connect $newCallp failed with error $err")
                call_notify_sipfrag(call.callp, 500, "Call Error")
            }
        } else {
            Log.w("Baresip", "ua_call_alloc ${ua.uap}/${call.callp} failed")
            call_notify_sipfrag(call.callp, 500, "Call Error")
        }
    }

    private fun dtmfWatcher(callp: String): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(sequence: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(sequence: CharSequence, start: Int, before: Int, count: Int) {
                val text = sequence.subSequence(start, start + count).toString()
                if (text.length > 0) {
                    val digit = text[0]
                    Log.d("Baresip", "Got DTMF digit '$digit'")
                    if (((digit >= '0') && (digit <= '9')) || (digit == '*') || (digit == '#'))
                        call_send_digit(callp, digit)
                }
            }
            override fun afterTextChanged(sequence: Editable) {
                // KEYCODE_REL
                // call_send_digit(callp, 4.toChar())
            }
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
        if (Call.uaCalls(calls, ua, "").size == 0) {
            callTitle.text = "Outgoing call to ..."
            callUri.text.clear()
            callUri.hint = "Callee"
            securityButton.visibility = View.INVISIBLE
            callButton.visibility = View.VISIBLE
            hangupButton.visibility = View.INVISIBLE
            answerButton.visibility = View.INVISIBLE
            rejectButton.visibility = View.INVISIBLE
            holdButton.visibility = View.INVISIBLE
            dtmf.visibility = View.INVISIBLE
        } else {
            val callsOut = Call.uaCalls(calls, ua, "out")
            val callsIn = Call.uaCalls(calls, ua, "in")
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
            callUri.setText(call.peerURI)
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

    private fun requestAudioFocus(streamType: Int) {
        val res: Int
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && (audioFocusRequest == null)) {
            val playbackAttributes = AudioAttributes.Builder()
                    .setLegacyStreamType(streamType)
                    .build()
            @TargetApi(26)
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .build()
            @TargetApi(26)
            res = am.requestAudioFocus(audioFocusRequest)
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d("Baresip", "Audio focus granted")
            } else {
                Log.d("Baresip", "Audio focus denied")
                audioFocusRequest = null
            }
        } else {
            res = am.requestAudioFocus(null, streamType,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d("Baresip", "Audio focus granted")
                audioFocused = true
            } else {
                Log.d("Baresip", "Audio focus denied")
                audioFocused = false
            }
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                am.abandonAudioFocusRequest(audioFocusRequest)
                audioFocusRequest = null
            }
        } else {
            if (audioFocused) {
                am.abandonAudioFocus(null)
                audioFocused = false
            }
        }
    }

    private fun startRinging() {
        am.mode = AudioManager.MODE_RINGTONE
        requestAudioFocus(AudioManager.STREAM_RING)
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

    external fun uag_current_set(uap: String)
    external fun ua_connect(uap: String, peer_uri: String): String
    external fun ua_call_alloc(uap: String, xcallp: String): String
    external fun ua_answer(uap: String, callp: String)
    external fun ua_hangup(uap: String, callp: String, code: Int, reason: String)
    external fun call_hold(callp: String): Int
    external fun call_unhold(callp: String): Int
    external fun call_send_digit(callp: String, digit: Char): Int
    external fun call_connect(callp: String, peer_uri: String): Int
    external fun call_notify_sipfrag(callp: String, code: Int, reason: String)
    external fun call_start_audio(callp: String)
    external fun call_stop_audio(callp: String)
    external fun reload_config(): Int

    companion object {

        var uas = ArrayList<UserAgent>()
        internal var images = ArrayList<Int>()
        var history: ArrayList<CallHistory> = ArrayList()
        internal var calls = ArrayList<Call>()
        internal var messages = ArrayList<Message>()
        internal var audioFocused = false
        internal var audioFocusRequest: AudioFocusRequest? = null
        var filesPath = ""
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
        const val HISTORY_SIZE = 100
        const val MESSAGE_HISTORY_SIZE = 100
        const val ONE_CALL_ONLY = true

    }

    init {
        Log.d("Baresip", "Loading baresip library")
        System.loadLibrary("baresip")
    }
}

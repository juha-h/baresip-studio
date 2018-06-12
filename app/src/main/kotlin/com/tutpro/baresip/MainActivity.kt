package com.tutpro.baresip

import android.Manifest
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.Keep
import android.widget.*
import android.widget.RelativeLayout.LayoutParams
import android.widget.AutoCompleteTextView
import android.view.*
import android.util.Log
import android.widget.RelativeLayout
import android.media.AudioManager
import android.net.NetworkInfo
import android.support.v7.app.NotificationCompat
import android.support.v7.view.menu.ActionMenuItemView
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

import java.util.*
import java.io.*

class MainActivity : AppCompatActivity() {

    lateinit var appContext: Context
    internal lateinit var layout: RelativeLayout
    internal lateinit var callee: AutoCompleteTextView
    internal lateinit var securityButton: ImageButton
    internal lateinit var callButton: ImageButton
    internal lateinit var holdButton: ImageButton
    internal lateinit var historyButton: ImageButton
    internal lateinit var dtmf: EditText
    internal lateinit var uaAdapter: UaSpinnerAdapter
    internal lateinit var aorSpinner: Spinner
    internal lateinit var calleeAdapter: ArrayAdapter<String>
    internal lateinit var am: AudioManager
    internal lateinit var imm: InputMethodManager
    internal lateinit var nm: NotificationManager
    internal lateinit var nb: NotificationCompat.Builder
    internal lateinit var receiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)
        setContentView(R.layout.activity_main)

        appContext = applicationContext
        layout = findViewById(R.id.mainActivityLayout) as RelativeLayout
        callee = findViewById(R.id.callee) as AutoCompleteTextView
        securityButton = findViewById(R.id.securityButton) as ImageButton
        callButton = findViewById(R.id.callButton) as ImageButton
        holdButton = findViewById(R.id.holdButton) as ImageButton
        historyButton = findViewById(R.id.historyButton) as ImageButton
        dtmf = findViewById(R.id.dtmf) as EditText
        filesPath = applicationContext.filesDir.absolutePath

        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        nb = NotificationCompat.Builder(this)
        nb.setVisibility(VISIBILITY_PUBLIC).setOngoing(true).setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(
                        PendingIntent.getActivity(this, NOTIFICATION_ID,
                                Intent(this, MainActivity::class.java)
                                        .setAction(Intent.ACTION_MAIN)
                                        .addCategory(Intent.CATEGORY_LAUNCHER),
                                        PendingIntent.FLAG_UPDATE_CURRENT))
                .setContent(RemoteViews(getPackageName(), R.layout.notification))

        val netFilter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val intentExtras = intent.extras
                val info = intentExtras.getParcelable<NetworkInfo>("networkInfo")
                Log.d("Baresip", "Got event $info")
                if (running) UserAgent.register(uas)
            }
        }
        registerReceiver(receiver, netFilter)

        aorSpinner = findViewById(R.id.AoRList) as Spinner
        uaAdapter = UaSpinnerAdapter(applicationContext, uas, images)
        aorSpinner.adapter = uaAdapter
        aorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val acc = uas[position].account
                val aor = acc.aor
                val ua = uas[position]
                Log.i("Baresip", "Setting $aor current")
                uag_current_set(uas[position].uap)
                val callsOut = Call.uaCalls(calls, ua, "out")
                if (callsOut.size == 0) {
                    callee.text.clear()
                    callee.hint = "Callee"
                    callButton.tag = "Call"
                    callButton.setImageResource(R.drawable.call)
                    if (History.aorHistory(history, aor) > 0) {
                        historyButton.visibility = View.VISIBLE
                    } else {
                        historyButton.visibility = View.INVISIBLE
                    }
                    securityButton.visibility = View.INVISIBLE
                    dtmf.visibility = View.INVISIBLE
                } else {
                    callee.setText(callsOut[0].peerURI)
                    callButton.tag = callsOut[0].status
                    if (callsOut[0].status == "Hangup") {
                        if (callsOut[0].hold) {
                            holdButton.setImageResource(R.drawable.resume)
                        } else {
                            holdButton.setImageResource(R.drawable.hold)
                        }
                        holdButton.visibility = View.VISIBLE
                        securityButton.setImageResource(callsOut[0].security)
                        setSecurityButtonTag(securityButton, callsOut[0].security)
                        if (acc.mediaenc == "")
                            securityButton.visibility = View.INVISIBLE
                        else
                            securityButton.visibility = View.VISIBLE
                        dtmf.visibility = View.VISIBLE
                        dtmf.requestFocus()
                    } else {
                        holdButton.visibility = View.INVISIBLE
                        securityButton.visibility = View.INVISIBLE
                        dtmf.visibility = View.INVISIBLE
                    }
                }
                val `in` = Call.uaCalls(calls, ua, "in")
                val view_count = layout.childCount
                Log.d("Baresip", "View count is $view_count")
                if (view_count > 7)
                    layout.removeViews(7, view_count - 7)
                for (c in `in`)
                    for (call_index in Call.uaCalls(calls, ua,"in").indices)
                        addCallViews(ua, c, (call_index + 1) * 10)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                Log.i("Baresip", "Nothing selected")
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

        val assets = arrayOf("accounts", "contacts", "config", "busy.wav", "callwaiting.wav",
                "error.wav", "message.wav", "notfound.wav", "ring.wav", "ringback.wav")
        val path = applicationContext.filesDir.path
        Log.d("Baresip", "path is: $path")
        var file = File(path)
        if (!file.exists()) {
            Log.d("Baresip", "Creating baresip directory")
            try {
                File(path).mkdirs()
            } catch (e: Error) {
                Log.e("Baresip", "Failed to create directory: " + e.toString())
            }

        }
        for (a in assets) {
            file = File("$path/$a")
            if (!file.exists()) {
                Log.d("Baresip", "Copying asset $a")
                copyAssetToFile(applicationContext, a, "$path/$a")
            } else {
                Log.d("Baresip", "Asset $a already copied")
            }
        }

        file = File(path, "history")
        try {
            val fis = FileInputStream(file)
            val ois = ObjectInputStream(fis)
            @SuppressWarnings("unchecked")
            history = ois.readObject() as ArrayList<History>
            Log.d("Baresip", "Restored History of ${history.size} entries")
            ois.close()
            fis.close()
        } catch (e: Exception) {
            Log.w("Baresip", "InputStream exception: - " + e.toString())
        }

        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d("Baresip", "Baresip does not have RECORD_AUDIO permission")
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION)
        }

        ContactsActivity.generateContacts(applicationContext.filesDir.absolutePath +
                "/contacts")

        callee.threshold = 2
        calleeAdapter = ArrayAdapter(this,
                android.R.layout.select_dialog_item, ContactsActivity.contactNames)
        callee.setAdapter(calleeAdapter)

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
                            if (Utils.cmd_exec("zrtp_unverify " + outCalls[0].zid) != 0) {
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
                    if (ONE_CALL_ONLY && calls.isEmpty()) {
                        val calleeText = (findViewById(R.id.callee) as EditText).text.toString()
                        if (calleeText.length > 0) {
                            var uri = ContactsActivity.findContactURI(calleeText)
                            if (!uri.startsWith("sip:")) uri = "sip:$uri"
                            if (!uri.contains("@")) {
                                val host = aor.substring(aor.indexOf("@") + 1)
                                uri = "$uri@$host"
                            }
                            call(ua, uri)
                        } else {
                            val latest = History.aorLatestHistory(history, aor)
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
                    Log.i("Baresip", "Canceling AoR $aor call $callp to " +
                            (findViewById(R.id.callee) as EditText).text)
                    ua_hangup(ua.uap, callp, 486, "Rejected")
                }
                "Hangup" -> {
                    val callp = Call.calls(calls, "out")[0].callp
                    Log.i("Baresip", "Hanging up AoR $aor call $callp to " +
                            (findViewById(R.id.callee) as EditText).text)
                    ua_hangup(ua.uap, callp,0, "")
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
                    Log.i("Baresip", "Resuming " + (findViewById(R.id.callee) as EditText).text)
                    val call = Call.calls(calls, "out")[0]
                    call_unhold(call.callp)
                    call.hold = false
                    holdButton.tag= "Hold"
                    holdButton.setImageResource(R.drawable.hold)
                }
            }
        }

        historyButton.visibility = View.INVISIBLE
        historyButton.setOnClickListener {
            val i = Intent(this@MainActivity, HistoryActivity::class.java)
            val b = Bundle()
            b.putString("aor", uas[aorSpinner.selectedItemPosition].account.aor)
            i.putExtras(b)
            startActivityForResult(i, HISTORY_CODE)
        }

        if (!running) {
            Log.i("Baresip", "Starting Baresip with path $path")
            if (!Utils.isConnected(this)) {
                Toast.makeText(this, "No network connection!",
                        Toast.LENGTH_LONG).show()
                unregisterReceiver(receiver)
                finish()
                // System.exit(0)
            } else {
                nm.notify(NOTIFICATION_ID, nb.build())
                Thread(Runnable { baresipStart(path) }).start()
                running = true
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("Baresip", "Paused")
    }

    override fun onResume() {
        super.onResume()
        Log.d("Baresip", "Resumed")
        imm.hideSoftInputFromWindow(callee.windowToken, 0)
        nm.cancel(INCOMING_ID)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d("Baresip", "Screen orientation change to landscape")
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d("Baresip", "Screen orientation change to portrait")
        }
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            RECORD_AUDIO_PERMISSION -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Baresip", "RECORD_AUDIO permission granted")
                } else {
                    Log.d("Baresip", "RECORD_AUDIO permission NOT granted")
                }
                return
            }
            else -> Log.e("Baresip", "Unknown permissions request code: $requestCode")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        inflater.inflate(R.menu.speaker_icon, menu)
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
                Utils.cmd_exec("audio_debug")
                i = Intent(this, AboutActivity::class.java)
                startActivityForResult(i, ABOUT_CODE)
                return true
            }
            R.id.quit -> {
                if (running) {
                    Log.d("Baresip", "Stopping")
                    val path = applicationContext.filesDir.path
                    Log.d("Baresip", "Saving history to $path")
                    val file = File(path, "history")
                    try {
                        val fos = FileOutputStream(file)
                        val oos = ObjectOutputStream(fos)
                        oos.writeObject(history)
                        oos.close()
                        fos.close()
                    } catch (e: IOException) {
                        Log.w("Baresip", "OutputStream exception: " + e.toString())
                        e.printStackTrace()
                    }
                    baresipStop()
                    nm.cancelAll()
                    running = false
                }
                unregisterReceiver(receiver)
                finish()
                System.exit(0)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        when (requestCode) {

            ACCOUNTS_CODE -> {
                uaAdapter.notifyDataSetChanged()
                updateNotification()
            }

            CONTACTS_CODE -> {
                calleeAdapter.notifyDataSetChanged()
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
                    if (History.aorHistory(history, uas[aorSpinner.selectedItemPosition].account.aor) == 0) {
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
        Log.i("Baresip", "Calling $ua.uap/$uri")
        val call = ua_connect(ua.uap, uri)
        if (call != "") {
            Log.i("Baresip", "Adding outgoing call $ua.uap / $call / $uri")
            val dtmfWatcher = object: TextWatcher {
                override fun beforeTextChanged(sequence: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(sequence: CharSequence, start: Int, before: Int, count: Int) {
                    val digit = sequence.subSequence(start, start+count).toString()
                    Log.d("Baresip", "Got DTMF digit '" + digit + "'")
                    if (digit.isNotEmpty()) call_send_digit(call, digit[0])
                }
                override fun afterTextChanged(sequence: Editable) {
                    call_send_digit(call, 4.toChar())
                }
            }
            calls.add(Call(call, ua, uri, "out","Cancel", dtmfWatcher))
            callButton.tag = "Cancel"
            callButton.setImageResource(R.drawable.hangup)
            holdButton.visibility = View.INVISIBLE
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
        val heading_params = LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT)
        if (id == 10)
            heading_params.addRule(RelativeLayout.BELOW, callButton.id)
        else
            heading_params.addRule(RelativeLayout.BELOW, id - 10 + 4)
        caller_heading.layoutParams = heading_params
        Log.d("Baresip", "Adding incoming call heading at ${caller_heading.id}")
        layout.addView(caller_heading)

        val caller_row = LinearLayout(appContext)
        caller_row.id = caller_heading.id + 1
        caller_row.setOrientation(LinearLayout.HORIZONTAL)
        val caller_row_params = LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        caller_row_params.addRule(RelativeLayout.BELOW, id)
        caller_row.layoutParams = caller_row_params

        val caller_uri = TextView(appContext)
        caller_uri.text = call.peerURI
        caller_uri.setTextColor(Color.BLUE)
        caller_uri.textSize = 20f
        caller_uri.setPadding(10, 10, 0, 10)
        caller_uri.id = caller_row.id + 1
        val caller_uri_params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        caller_uri.layoutParams = caller_uri_params
        Log.d("Baresip", "Adding caller uri at ${caller_uri.id}")
        caller_row.addView(caller_uri)

        val security_button = ImageButton(appContext)
        security_button.id = caller_uri.id + 1
        val dp24px = ((24 * appContext.resources.displayMetrics.density) + 0.5).toInt()
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
                        if (Utils.cmd_exec("zrtp_unverify " + call.zid) != 0) {
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
        Log.d("Baresip", "Adding security button at ${security_button.id}")
        caller_row.addView(security_button)

        Log.d("Baresip", "Adding caller row at ${caller_row.id}")
        layout.addView(caller_row)

        val answer_row = LinearLayout(appContext)
        answer_row.id = security_button.id + 1
        answer_row.setOrientation(LinearLayout.HORIZONTAL)
        val answer_row_params = LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        answer_row_params.addRule(RelativeLayout.BELOW, caller_row.id)
        answer_row.layoutParams = answer_row_params

        val answer_button = ImageButton(appContext)
        answer_button.tag = call.status
        if (call.status == "Answer")
            answer_button.setImageResource(R.drawable.call)
        else
            answer_button.setImageResource(R.drawable.hangup)
        answer_button.id = answer_row.id + 1
        answer_button.setOnClickListener { v ->
            when ((v as ImageButton).tag) {
                "Answer" -> {
                    Log.i("Baresip", "UA ${ua.uap} accepting incoming call ${call.callp}")
                    ua_answer(ua.uap, call.callp)
                    if (call.dir == "in") {
                        this@MainActivity.runOnUiThread {
                            call.status = "Hangup"
                            call.hold = false
                            answer_button.tag = "Hangup"
                            answer_button.setImageResource(R.drawable.hangup)
                            val reject_button = layout.findViewById(answer_button.id + 1) as ImageButton
                            reject_button.tag = "Hold"
                            reject_button.setImageResource(R.drawable.hold)
                        }
                    }
                }
                "Hangup" -> {
                    Log.i("Baresip", "UA ${call.ua.uap} hanging up call ${call.callp}")
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
        Log.d("Baresip", "Adding answer button at ${answer_button.id}")
        answer_row.addView(answer_button)

        val reject_button = ImageButton(appContext)
        if (call.status == "Answer") {
            reject_button.tag = "Reject"
            reject_button.setImageResource(R.drawable.hangup)
        } else {
            if (call.hold) {
                reject_button.tag = "Resume"
                reject_button.setImageResource(R.drawable.resume)
            } else {
                reject_button.tag = "Hold"
                reject_button.setImageResource(R.drawable.hold)
            }
        }
        reject_button.id = answer_button.id + 1
        reject_button.setOnClickListener { v ->
            when ((v as ImageButton).tag) {
                "Reject" -> {
                    Log.i("Baresip", "UA ${call.ua} rejecting incoming call ${call.callp}")
                    ua_hangup(call.ua.uap, call.callp, 486, "Rejected")
                }
                "Hold" -> {
                    holdCallAt(call, reject_button)
                }
                "Resume" -> {
                    call_unhold(call.callp)
                    v.tag = "Hold"
                    reject_button.setImageResource(R.drawable.hold)
                    call.hold = false
                }
            }
        }
        val reject_button_params = LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT)
        reject_button_params.setMargins(40, 10, 0, 0)
        reject_button.layoutParams = reject_button_params
        reject_button.setPadding(0, 0, 0, 0)
        Log.d("Baresip", "Adding reject button at ${reject_button.id}")
        answer_row.addView(reject_button)

        Log.d("Baresip", "Adding answer row at ${answer_row.id}")
        layout.addView(answer_row)
    }

    fun updateNotification() {
        val contentView = RemoteViews(getPackageName(), R.layout.notification)
        for (i: Int in 0 .. 5)  {
            val resID = resources.getIdentifier("status$i", "id", packageName)
            if (i < images.size) {
                contentView.setImageViewResource(resID, images[i])
                contentView.setViewVisibility(resID, View.VISIBLE)
            } else {
                contentView.setViewVisibility(resID, View.INVISIBLE)
            }
        }
        if (images.size > 4)
            contentView.setViewVisibility(R.id.etc, View.VISIBLE)
        else
            contentView.setViewVisibility(R.id.etc, View.INVISIBLE)
        nb.setContent(contentView)
        nm.notify(NOTIFICATION_ID, nb.build())
    }

    @Keep
    fun addUA(uap: String) {
        val ua = UserAgent(uap)
        uas.add(ua)
        if (UserAgent.ua_isregistered(uap)) {
            Log.d("Baresip", "Ua $ua is registered")
            images.add(R.drawable.dot_green)
        } else {
            Log.d("Baresip", "Ua $ua is NOT registered")
            images.add(R.drawable.dot_yellow)
            ua.register()
        }
        this@MainActivity.runOnUiThread {
            uaAdapter.notifyDataSetChanged()
            if (aorSpinner.selectedItemPosition == -1) aorSpinner.setSelection(0)
            updateNotification()
        }
    }

    @Keep
    private fun updateStatus(event: String, uap: String, callp: String) {

        val ua = UserAgent.find(uas, uap)
        if (ua == null) {
            Log.e("Baresip", "Update status did not find ua $uap")
            return
        }
        val aor = ua.account.aor
        val acc = ua.account
        val ev = event.split(",")

        Log.d("Baresip", "Handling event ${ev[0]} for $uap/$callp/$aor")

        for (account_index in uas.indices) {
            if (uas[account_index].account.aor == aor) {
                Log.d("Baresip", "Found AoR at index $account_index")
                when (ev[0]) {
                    "registering", "unregistering" -> {
                    }
                    "registered" -> {
                        Log.d("Baresip", "Setting status to green")
                        this@MainActivity.runOnUiThread {
                            images[account_index] = R.drawable.dot_green
                            uaAdapter.notifyDataSetChanged()
                            updateNotification()
                        }
                    }
                    "registering failed" -> {
                        Log.d("Baresip", "Setting status to red")
                        this@MainActivity.runOnUiThread {
                            images[account_index] = R.drawable.dot_red
                            uaAdapter.notifyDataSetChanged()
                            updateNotification()
                        }
                    }
                    "call ringing" -> {
                    }
                    "call incoming" -> {
                        val peer_uri = call_peeruri(callp)
                        val new_call = Call(callp, ua, peer_uri, "in", "Answer", null)
                        if (ONE_CALL_ONLY && (calls.size > 0)) {
                            Log.d("Baresip", "Auto-rejecting incoming call $uap/$callp/$peer_uri")
                            ua_hangup(uap, callp, 486, "Busy Here")
                            history.add(History(aor, peer_uri, "in", false))
                            new_call.hasHistory = true
                        } else {
                            Log.d("Baresip", "Incoming call $uap/$callp/$peer_uri")
                            calls.add(new_call)
                            this@MainActivity.runOnUiThread {
                                if (ua != uas[aorSpinner.selectedItemPosition])
                                    aorSpinner.setSelection(account_index)
                                else
                                    addCallViews(ua, new_call, Call.uaCalls(calls, ua, "in").size * 10)
                                am.mode = AudioManager.MODE_RINGTONE
                                if (Utils.foregrounded()) {
                                    val i = Intent(this, MainActivity::class.java)
                                    i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                    startActivity(i)
                                } else {
                                    showNotification(peer_uri)
                                }
                            }
                        }
                    }
                    "call established" -> {
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
                            this@MainActivity.runOnUiThread {
                                if (ua == uas[aorSpinner.selectedItemPosition]) {
                                    callButton.tag = "Hangup"
                                    callButton.setImageResource(R.drawable.hangup)
                                    holdButton.tag = "Hold"
                                    holdButton.setImageResource(R.drawable.hold)
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
                            }
                            history.add(History(aor, call.peerURI, "out", true))
                            call.hasHistory = true
                        } else {
                            Log.d("Baresip", "Inbound call $callp established")
                            call.status = "Hangup"
                            call.security = R.drawable.box_red
                            this@MainActivity.runOnUiThread {
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
                                        rejectButton.setImageResource(R.drawable.hold)
                                    }
                                }
                            }
                            history.add(History(aor, call.peerURI, "in", true))
                            call.hasHistory = true
                        }
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                        am.isSpeakerphoneOn = false
                        // am.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                        //        (am.getStreamMaxVolume(STREAM_VOICE_CALL) / 2) + 1,
                        //        AudioManager.STREAM_VOICE_CALL)
                        if (History.aorHistory(history, aor) > HISTORY_SIZE)
                            History.aorRemoveHistory(history, aor)
                    }
                    "call verify" -> {
                        val call = Call.find(calls, callp)
                        if (call == null) {
                            Log.e("Baresip", "Call $callp to be verified is not found")
                            return
                        }
                        this@MainActivity.runOnUiThread {
                            val verifyDialog = AlertDialog.Builder(this)
                            verifyDialog.setTitle("Verify")
                            verifyDialog.setMessage("Do you want to verify SAS <${ev[1]}> <${ev[2]}>?")
                            verifyDialog.setPositiveButton("Yes") { dialog, _ ->
                                val security: Int
                                if (Utils.cmd_exec("zrtp_verify ${ev[3]}") != 0) {
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
                                        val view_id = (Call.uaCallIndex(calls, ua, call,"in") + 1) * 10 + 3
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
                                        val view_id = (Call.uaCallIndex(calls, ua, call,"in") + 1) * 10 + 3
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
                    }
                    "call verified" -> {
                        val call = Call.find(calls, callp)
                        if (call == null) {
                            Log.e("Baresip", "Call $callp that is verified is not found")
                            return
                        }
                        call.security = R.drawable.box_green
                        call.zid = ev[1]
                        if (call.dir == "out") {
                            this@MainActivity.runOnUiThread {
                                if (ua == uas[aorSpinner.selectedItemPosition]) {
                                    securityButton.setImageResource(R.drawable.box_green)
                                    securityButton.tag = "green"
                                    securityButton.visibility = View.VISIBLE
                                }
                            }
                        } else {
                            this@MainActivity.runOnUiThread {
                                if (ua == uas[aorSpinner.selectedItemPosition]) {
                                    val view_id = (Call.uaCallIndex(calls, ua, call, "in") + 1) * 10 + 3
                                    val securityButton = layout.findViewById(view_id) as ImageButton
                                    securityButton.setImageResource(R.drawable.box_green)
                                    securityButton.tag = "green"
                                    securityButton.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                    "call closed" -> {
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
                            val remove_count = Call.uaCalls(calls, ua,"in").size - inIndex
                            calls.remove(call)
                            this@MainActivity.runOnUiThread {
                                if (ua == uas[aorSpinner.selectedItemPosition]) {
                                    if (callButton.tag == "Call") {
                                        callee.setOnKeyListener(null)
                                    }
                                    val caller_heading = layout.findViewById(view_id)
                                    val view_index = layout.indexOfChild(caller_heading)
                                    layout.removeViews(view_index, 3 * remove_count)
                                    val callsIn = Call.uaCalls(calls, ua,"in")
                                    for (i in inIndex until callsIn.size) {
                                        this@MainActivity.addCallViews(ua, callsIn[i], (i + 1) * 10)
                                    }
                                    historyButton.visibility = View.VISIBLE
                                }
                                nm.cancel(INCOMING_ID)
                            }
                            if (!call.hasHistory) {
                                if (History.aorHistory(history, aor) > HISTORY_SIZE)
                                    History.aorRemoveHistory(history, aor)
                                history.add(History(aor, call.peerURI,"in", false))
                            }
                        } else {
                            Log.d("Baresip", "Removing outgoing call $uap/$callp/" +
                                        call.peerURI)
                                this@MainActivity.runOnUiThread {
                                    if (ua == uas[aorSpinner.selectedItemPosition]) {
                                        callButton.tag = "Call"
                                        callButton.setImageResource(R.drawable.call)
                                        callButton.isEnabled = true
                                        callee.setText("")
                                        callee.hint = "Callee"
                                        holdButton.visibility = View.INVISIBLE
                                        if (this.currentFocus == dtmf) {
                                            imm.hideSoftInputFromWindow(dtmf.windowToken, 0)
                                        }
                                        securityButton.visibility = View.INVISIBLE
                                        dtmf.removeTextChangedListener(call.dtmfWatcher)
                                        dtmf.visibility = View.INVISIBLE
                                        historyButton.visibility = View.VISIBLE
                                    }
                                    calls.remove(call)
                                }
                                if (!call.hasHistory) {
                                    if (History.aorHistory(history, aor) > HISTORY_SIZE)
                                        History.aorRemoveHistory(history, aor)
                                    history.add(History(aor, call.peerURI, "out",
                                            false))
                                }
                        }
                        if (calls.size == 0) am.mode = AudioManager.MODE_NORMAL
                    }
                    else -> Log.d("Baresip", "Unknown event '${ev[0]}'")
                }
                break
            }
        }
    }

    private fun copyAssetToFile(context: Context, asset: String, path: String) {
        try {
            val `is` = context.assets.open(asset)
            val os = FileOutputStream(path)
            val buffer = ByteArray(512)
            var byteRead: Int = `is`.read(buffer)
            while (byteRead  != -1) {
                os.write(buffer, 0, byteRead)
                byteRead = `is`.read(buffer)
            }
        } catch (e: IOException) {
            Log.e("Baresip", "Failed to read asset " + asset + ": " +
                    e.toString())
        }

    }

    private fun setSecurityButtonTag(button: ImageButton, security: Int) {
        when (security) {
            R.drawable.box_red -> { button.tag = "red" }
            R.drawable.box_yellow -> { button.tag = "yellow" }
            R.drawable.box_green -> { button.tag = "green" }
        }
    }

    private fun holdCallAt(call: Call, button: ImageButton) {
        Log.i("Baresip", "Holding call with ${call.peerURI} at ${button.id}")
        call_hold(call.callp)
        call.hold = true
        button.tag= "Resume"
        button.setImageResource(R.drawable.resume)
    }

    private fun showNotification(peerUri: String) {
        Log.d("Baresip", "Showing notification")
        val nb = NotificationCompat.Builder(this)
            .setVisibility(VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Incoming call from")
                .setContentText("$peerUri")
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(
                        PendingIntent.getActivity(this, INCOMING_ID,
                                Intent(this, MainActivity::class.java)
                                        .setAction(Intent.ACTION_MAIN)
                                        .addCategory(Intent.CATEGORY_LAUNCHER),
                                PendingIntent.FLAG_UPDATE_CURRENT))
        nm.notify(INCOMING_ID, nb.build())
    }

    external fun baresipStart(path: String)
    external fun baresipStop()
    external fun uag_current(): String
    external fun uag_current_set(uap: String)
    external fun ua_connect(uap: String, peer_uri: String): String
    external fun ua_answer(uap: String, callp: String)
    external fun ua_hold_answer(uap: String, callp: String): Int
    external fun ua_hangup(uap: String, callp: String, code: Int, reason: String)
    external fun call_peeruri(callp: String): String
    external fun call_hold(callp: String): Int
    external fun call_unhold(callp: String): Int
    external fun call_send_digit(callp: String, digit: Char): Int
    external fun reload_config(): Int

    companion object {

        internal var running: Boolean = false
        var uas = ArrayList<UserAgent>()
        internal var images = ArrayList<Int>()
        internal var calls = ArrayList<Call>()
        var history: ArrayList<History> = ArrayList()
        var filesPath = ""

        const val ACCOUNTS_CODE = 1
        const val CONTACTS_CODE = 2
        const val EDIT_CONFIG_CODE = 3
        const val HISTORY_CODE = 4
        const val ABOUT_CODE = 5
        const val ACCOUNT_CODE = 6
        const val CONTACT_CODE = 7

        const val RECORD_AUDIO_PERMISSION = 1
        const val HISTORY_SIZE = 100
        const val NOTIFICATION_ID = 10
        const val INCOMING_ID = 11

        const val ONE_CALL_ONLY = true

        external fun contacts_remove()
        external fun contact_add(contact: String)

        init {
            System.loadLibrary("baresip")
        }
    }

}

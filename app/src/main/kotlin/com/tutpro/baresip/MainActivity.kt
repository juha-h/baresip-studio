package com.tutpro.baresip

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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

import java.util.*
import java.io.*
import android.widget.RelativeLayout
import android.media.AudioManager
import android.media.AudioManager.STREAM_VOICE_CALL
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

class MainActivity : AppCompatActivity() {

    internal lateinit var appContext: Context
    internal lateinit var layout: RelativeLayout
    internal lateinit var callee: AutoCompleteTextView
    internal lateinit var securityButton: ImageButton
    internal lateinit var callButton: Button
    internal lateinit var holdButton: Button
    internal lateinit var dtmf: EditText
    internal lateinit var accountAdapter: AccountSpinnerAdapter
    internal lateinit var aorSpinner: Spinner
    internal lateinit var am: AudioManager
    internal lateinit var imm: InputMethodManager
    internal lateinit var nm: NotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appContext = applicationContext
        layout = findViewById(R.id.mainActivityLayout) as RelativeLayout
        callee = findViewById(R.id.callee) as AutoCompleteTextView
        securityButton = findViewById(R.id.securityButton) as ImageButton
        callButton = findViewById(R.id.callButton) as Button
        holdButton = findViewById(R.id.holdButton) as Button
        dtmf = findViewById(R.id.dtmf) as EditText

        am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        aorSpinner = findViewById(R.id.AoRList) as Spinner
        accountAdapter = AccountSpinnerAdapter(applicationContext, aors, images)
        aorSpinner.adapter = accountAdapter
        aorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val aor = aors[position]
                val ua = aor_ua(aor)
                Log.i("Baresip", "Setting $aor current")
                val out = Call.uaCalls(callsOut, ua)
                if (out.size == 0) {
                    callee.text.clear()
                    callee.hint = "Callee"
                    callButton.text = "Call"
                    if (HistoryActivity.aorHistory(aor) > 0) {
                        holdButton.text = "History"
                        holdButton.visibility = View.VISIBLE
                    } else {
                        holdButton.visibility = View.INVISIBLE
                    }
                    securityButton.visibility = View.INVISIBLE
                    dtmf.visibility = View.INVISIBLE
                } else {
                    callee.setText(out[0].peerURI)
                    callButton.text = out[0].status
                    if (out[0].status == "Hangup") {
                        if (out[0].hold) {
                            holdButton.text = "Unhold"
                        } else {
                            holdButton.text = "Hold"
                        }
                        holdButton.visibility = View.VISIBLE
                        securityButton.setImageResource(out[0].security)
                        Utils.setSecurityButtonTag(securityButton, out[0].security)
                        if (ua_mediaenc(ua) == "")
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
                val `in` = Call.uaCalls(callsIn, aor_ua(aor))
                val view_count = layout.childCount
                Log.d("Baresip", "View count is $view_count")
                if (view_count > 7) {
                    // remove all incoming call views
                    layout.removeViews(7, view_count - 7)
                }
                for (c in `in`) {
                    for (call_index in callsIn.indices) {
                        addCallViews(ua, c, (call_index + 1) * 10)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                Log.i("Baresip", "Nothing selected")
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
                Utils.copyAssetToFile(applicationContext, a, "$path/$a")
            } else {
                Log.d("Baresip", "Asset $a already copied")
            }
        }

        file = File(path, "history")
        try {
            val fis = FileInputStream(file)
            val ois = ObjectInputStream(fis)
            @SuppressWarnings("unchecked")
            HistoryActivity.History = ois.readObject() as ArrayList<History>
            Log.d("Baresip", "Restored History of " + HistoryActivity.History.size + " entries")
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

        EditContactsActivity.updateContactsAndNames(applicationContext.filesDir.absolutePath +
                "/contacts")

        callee.threshold = 2
        callee.setAdapter<ArrayAdapter<String>>(ArrayAdapter(this,
                android.R.layout.select_dialog_item, EditContactsActivity.Names))

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
                        val out = Call.uaCalls(callsOut, aor_ua(aors[aorSpinner.selectedItemPosition]))
                        if (out.size > 0) {
                            if (cmd_exec("zrtp_unverify " + out[0].zid) != 0) {
                                Log.w("Baresip",
                                        "Command 'zrtp_unverify ${out[0].zid}' failed")
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

        callButton.text = "Call"
        callButton.setOnClickListener {
            Log.d("Baresip", "AoR at position " + aors[aorSpinner.selectedItemPosition])
            val aor = aors[aorSpinner.selectedItemPosition]
            when (callButton.text.toString()) {
                "Call" -> {
                    call(aor, (findViewById(R.id.callee) as EditText).text.toString())
                }
                "Cancel" -> {
                    Log.i("Baresip", "Canceling UA " + aor_ua(aor) + " call " +
                            callsOut[0].call + " to " +
                            (findViewById(R.id.callee) as EditText).text)
                    ua_hangup(aor_ua(aor), callsOut[0].call, 486, "Rejected")
                }
                "Hangup" -> {
                    Log.i("Baresip", "Hanging up UA " + aor_ua(aor) + " call " +
                            callsOut[0].call + " to " +
                            (findViewById(R.id.callee) as EditText).text)
                    ua_hangup(aor_ua(aor), callsOut[0].call,0, "")
                }
            }
        }

        val holdButton = findViewById(R.id.holdButton) as Button
        holdButton.setOnClickListener {
            when (holdButton.text.toString()) {
                "Hold" -> {
                    Log.i("Baresip", "Holding up " + (findViewById(R.id.callee) as EditText).text)
                    call_hold(callsOut[0].call)
                    callsOut[0].hold = true
                    holdButton.text = "Unhold"
                }
                "Unhold" -> {
                    Log.i("Baresip", "Unholding " + (findViewById(R.id.callee) as EditText).text)
                    call_unhold(callsOut[0].call)
                    callsOut[0].hold = false
                    holdButton.text = "Hold"
                }
                "History" -> {
                    val i = Intent(this@MainActivity, HistoryActivity::class.java)
                    val b = Bundle()
                    b.putString("aor", aors[aorSpinner.selectedItemPosition])
                    i.putExtras(b)
                    startActivityForResult(i, HISTORY_CODE)
                }
            }
        }

        if (!running) {
            Log.i("Baresip", "Starting Baresip with path $path")
            Thread(Runnable { baresipStart(path) }).start()
            addStatusBarIcon(10)
            running = true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d("Baresip", "Screen orientation change to landscape")
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d("Baresip", "Screen orientation change to portrait")
        }
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
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val i: Intent
        when (item.itemId) {
            R.id.accounts -> {
                i = Intent(this, EditAccountsActivity::class.java)
                startActivityForResult(i, EDIT_ACCOUNTS_CODE)
                return true
            }
            R.id.contacts -> {
                i = Intent(this, EditContactsActivity::class.java)
                startActivityForResult(i, EDIT_CONTACTS_CODE)
                return true
            }
            R.id.config -> {
                i = Intent(this, EditConfigActivity::class.java)
                startActivityForResult(i, EDIT_CONFIG_CODE)
                return true
            }
            R.id.about -> {
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
                        oos.writeObject(HistoryActivity.History)
                        oos.close()
                        fos.close()
                    } catch (e: IOException) {
                        Log.w("Baresip", "OutputStream exception: " + e.toString())
                        e.printStackTrace()
                    }

                    baresipStop()
                    nm.cancel(10)
                    running = false
                }
                finish()
                System.exit(0)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == EDIT_ACCOUNTS_CODE) {
            if (resultCode == RESULT_OK) {
                Utils.alertView(this, "Notice",
                        "You need to restart baresip in order to activate saved accounts!")
            }
            if (resultCode == RESULT_CANCELED) {
                Log.d("Baresip", "Edit accounts canceled")
            }
        }
        if (requestCode == EDIT_CONFIG_CODE) {
            if (resultCode == RESULT_OK) {
                Utils.alertView(this, "Notice",
                        "You need to restart baresip in order to activate saved config!")
                reload_config()
            }
            if (resultCode == RESULT_CANCELED) {
                Log.d("Baresip", "Edit config canceled")
            }
        }
        if (requestCode == HISTORY_CODE) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    (findViewById(R.id.callee) as EditText).setText(data.getStringExtra("peer_uri"))
                }
            }
            if (resultCode == RESULT_CANCELED) {
                Log.d("Baresip", "History canceled")
                if (HistoryActivity.aorHistory(aors[aorSpinner.selectedItemPosition]) == 0) {
                    (findViewById(R.id.holdButton) as Button).visibility = View.INVISIBLE
                }
            }
        }
        if (requestCode == EDIT_CONTACTS_CODE || requestCode == ABOUT_CODE) {
            Log.d("Baresip", "Back arrow or Cancel pressed at request: $requestCode")
        }
    }

    private fun call(aor: String, callee: String) {
        if (callee.length == 0) return
        var uri = EditContactsActivity.findContactURI(callee)
        if (!uri.startsWith("sip:")) uri = "sip:$uri"
        if (!uri.contains("@")) {
            val host = aor.substring(aor.indexOf("@") + 1)
            uri = "$uri@$host"
        }
        (findViewById(R.id.callee) as EditText).setText(uri)
        val ua = aor_ua(aor)
        Log.i("Baresip", "Calling $ua / $uri")
        val call = ua_connect(ua, uri)
        if (call != "") {
            Log.i("Baresip", "Adding outgoing call $ua / $call / $uri")
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
            callsOut.add(Call(ua, call, uri, "Cancel", dtmfWatcher))
            (findViewById(R.id.callButton) as Button).text = "Cancel"
            (findViewById(R.id.holdButton) as Button).visibility = View.INVISIBLE
        }
    }

    private fun addCallViews(ua: String, call: Call, id: Int) {
        Log.d("Baresip", "Creating new Incoming textview at $id")

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
        layout.addView(caller_heading)

        val caller_row = LinearLayout(appContext)
        caller_row.id = id + 1
        caller_row.setOrientation(LinearLayout.HORIZONTAL)
        val caller_row_params = LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        caller_row_params.addRule(RelativeLayout.BELOW, id)
        caller_row.layoutParams = caller_row_params

        val caller_uri = TextView(appContext)
        caller_uri.text = callsIn[callsIn.size - 1].peerURI
        caller_uri.setTextColor(Color.BLUE)
        caller_uri.textSize = 20f
        caller_uri.setPadding(10, 10, 0, 10)
        Log.d("Baresip", "Creating new caller textview at " + (id + 2))
        caller_uri.id = id + 2
        val caller_uri_params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        caller_uri.layoutParams = caller_uri_params
        caller_row.addView(caller_uri)

        val security_button = ImageButton(appContext)
        security_button.id = id + 3
        val dp24px = ((24 * appContext.resources.displayMetrics.density) + 0.5).toInt()
        val security_button_params = LinearLayout.LayoutParams(dp24px, dp24px, 0.0f)
        security_button_params.gravity = Gravity.CENTER_VERTICAL
        security_button.layoutParams = security_button_params
        if ((call.security != 0) && (ua_mediaenc(ua) != "")) {
            security_button.setImageResource(call.security)
            Utils.setSecurityButtonTag(securityButton, call.security)
            security_button.visibility = View.VISIBLE
        } else {
            security_button.visibility = View.INVISIBLE
        }
        Utils.setSecurityButtonOnClickListener(this, security_button, call)
        caller_row.addView(security_button)

        layout.addView(caller_row)

        val answer_button = Button(appContext)
        answer_button.text = call.status
        answer_button.setBackgroundResource(android.R.drawable.btn_default)
        answer_button.setTextColor(Color.BLACK)
        Log.d("Baresip", "Creating new answer button at " + (id + 4))
        answer_button.id = id + 4
        answer_button.setOnClickListener { v ->
            val call_ua = call.ua
            val call_call = call.call
            when ((v as Button).text.toString()) {
                "Answer" -> {
                    Log.i("Baresip", "UA " + call_ua + " accepting incoming call " +
                            call_call)
                    ua_answer(call_ua, call_call)
                    val final_in_index = Call.callIndex(callsIn, call_ua, call_call)
                    if (final_in_index >= 0) {
                        Log.d("Baresip", "Updating Hangup and Hold")
                        callsIn[final_in_index].status = "Hangup"
                        callsIn[final_in_index].hold = false
                        this@MainActivity.runOnUiThread {
                            val answer_id = (final_in_index + 1) * 10 + 4
                            val answer_but = layout.findViewById(answer_id) as Button
                            answer_but.text = "Hangup"
                            val reject_button = layout.findViewById(answer_id + 1) as Button
                            reject_button.text = "Hold"
                        }
                    }
                }
                "Hangup" -> {
                    Log.i("Baresip", "UA " + call_ua + " hanging up call " +
                            call_call)
                    ua_hangup(call_ua, call_call, 200, "OK")
                }
                else -> Log.e("Baresip", "Invalid answer button text: " + v.text.toString())
            }
        }
        val answer_button_params = LayoutParams(200, LayoutParams.WRAP_CONTENT)
        answer_button_params.addRule(RelativeLayout.BELOW, id + 1)
        answer_button_params.setMargins(3, 10, 0, 0)
        answer_button.layoutParams = answer_button_params
        layout.addView(answer_button)

        val reject_button = Button(appContext)
        if (call.status == "Answer") {
            reject_button.text = "Reject"
        } else {
            if (call.hold) {
                reject_button.text = "Unhold"
            } else {
                reject_button.text = "Hold"
            }
        }
        reject_button.setBackgroundResource(android.R.drawable.btn_default)
        reject_button.setTextColor(Color.BLACK)
        Log.d("Baresip", "Creating new reject button at " + (id + 5))
        reject_button.id = id + 5
        reject_button.setOnClickListener { v ->
            when ((v as Button).text.toString()) {
                "Reject" -> {
                    Log.i("Baresip", "UA " + call.ua +
                            " rejecting incoming call " + call.call)
                    ua_hangup(call.ua, call.call, 486, "Rejected")
                }
                "Hold" -> {
                    call_hold(call.call)
                    v.text = "Unhold"
                    call.hold = true
                }
                "Unhold" -> {
                    call_unhold(call.call)
                    v.text = "Hold"
                    call.hold = false
                }
            }
        }
        val reject_button_params = LayoutParams(200,
                LayoutParams.WRAP_CONTENT)
        reject_button_params.addRule(RelativeLayout.BELOW, id + 1)
        reject_button_params.setMargins(225, 10, 0, 0)
        reject_button.layoutParams = reject_button_params
        layout.addView(reject_button)
    }

    private fun addStatusBarIcon(id: Int) {
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Baresip is running")
                .setOngoing(true).build()
        nm.notify(id, notification)
    }

    @Keep
    fun addAccount(ua: String) {
        val aor = ua_aor(ua)
        Log.d("Baresip", "Adding account $ua with AoR $aor")
        accounts.add(Account(ua, aor, ua_mediaenc(ua),""))
        aors.add(aor)
        images.add(R.drawable.dot_yellow)
        this@MainActivity.runOnUiThread { accountAdapter.notifyDataSetChanged() }
    }

    @Keep
    private fun updateStatus(event: String, ua: String, call: String) {

        val aor = ua_aor(ua)
        val ev = event.split(",")

        Log.d("Baresip", "Handling event " + ev[0] + " for " + ua + "/" + call + "/" +
                aor)

        for (account_index in accounts.indices) {
            if (accounts[account_index].aoR == aor) {
                Log.d("Baresip", "Found AoR at index $account_index")
                when (ev[0]) {
                    "registering", "unregistering" -> {
                    }
                    "registered" -> {
                        Log.d("Baresip", "Setting status to green")
                        accounts[account_index].status = "OK"
                        aors[account_index] = aor
                        images[account_index] = R.drawable.dot_green
                        this@MainActivity.runOnUiThread { accountAdapter.notifyDataSetChanged() }
                    }
                    "registering failed" -> {
                        Log.d("Baresip", "Setting status to red")
                        accounts[account_index].status = "FAIL"
                        aors[account_index] = aor
                        images[account_index] = R.drawable.dot_red
                        this@MainActivity.runOnUiThread { accountAdapter.notifyDataSetChanged() }
                    }
                    "call ringing" -> {
                    }
                    "call established" -> {
                        val out_index = Call.callIndex(callsOut, ua, call)
                        if (out_index != -1) {
                            Log.d("Baresip", "Outbound call $call established")
                            callsOut[out_index].status = "Hangup"
                            callsOut[out_index].hold = false
                            callsOut[out_index].security = R.drawable.box_red
                            this@MainActivity.runOnUiThread {
                                if (ua == aor_ua(aors[aorSpinner.selectedItemPosition])) {
                                    callButton.text = "Hangup"
                                    holdButton.text = "Hold"
                                    holdButton.visibility = View.VISIBLE
                                    if (ua_mediaenc(ua) == "") {
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
                                    dtmf.addTextChangedListener(callsOut[out_index].dtmfWatcher)
                                }
                            }
                            HistoryActivity.History.add(History(ua, call, aor, call_peeruri(call),
                                    "out", true))
                        } else {
                            val in_index = Call.callIndex(callsIn, ua, call)
                            if (in_index != -1) {
                                Log.d("Baresip", "Inbound call $call established")
                                callsIn[in_index].status = "Answer"
                                callsIn[in_index].security = R.drawable.box_red
                                this@MainActivity.runOnUiThread {
                                    if (ua == aor_ua(aors[aorSpinner.selectedItemPosition])) {
                                        if (ua_mediaenc(ua) != "") {
                                            var view_id = (in_index + 1) * 10 + 3
                                            val securityButton = findViewById(view_id) as ImageButton
                                            securityButton.setImageResource(R.drawable.box_red)
                                            securityButton.tag = "red"
                                            securityButton.visibility = View.VISIBLE
                                            view_id = (in_index + 1) * 10 + 5
                                            val rejectButton = findViewById(view_id) as Button
                                            rejectButton.text = "Hold"
                                        }
                                    }
                                }
                                HistoryActivity.History.add(History(ua, call, aor, call_peeruri(call),
                                        "in", true))
                            } else {
                                Log.e("Baresip", "Unknown call $ua/$call established")
                            }
                        }
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                        am.isSpeakerphoneOn = false
                        am.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                                (am.getStreamMaxVolume(STREAM_VOICE_CALL) / 2) + 1,
                                AudioManager.STREAM_VOICE_CALL)
                        if (HistoryActivity.aorHistory(aor) > HISTORY_SIZE)
                            HistoryActivity.aorRemoveHistory(aor)
                    }
                    "call incoming" -> {
                        val peer_uri = call_peeruri(call)
                        Log.d("Baresip", "Incoming call " + ua + "/" + call + "/" +
                                peer_uri)
                        val new_call = Call(ua, call, peer_uri, "Answer", null)
                        callsIn.add(new_call)
                        this@MainActivity.runOnUiThread {
                            if (ua == aor_ua(aors[aorSpinner.selectedItemPosition])) {
                                addCallViews(ua, new_call, callsIn.size * 10)
                            }
                        }
                        am.mode = AudioManager.MODE_RINGTONE
                        am.isSpeakerphoneOn = true
                    }
                    "call verify" -> {
                        this@MainActivity.runOnUiThread {
                            val verifyDialog = AlertDialog.Builder(this)
                            verifyDialog.setTitle("Verify")
                            verifyDialog.setMessage("Do you want to verify SAS <${ev[1]}> <${ev[2]}>?")
                            verifyDialog.setPositiveButton("Yes") { dialog, _ ->
                                val security: Int
                                if (cmd_exec("zrtp_verify ${ev[3]}") != 0) {
                                    Log.w("Baresip", "Command 'zrtp_verify ${ev[3]}' failed")
                                    security = R.drawable.box_yellow
                                } else {
                                    security = R.drawable.box_green
                                }
                                var index = Call.callIndex(callsOut, ua, call)
                                if (index != -1) {
                                    callsOut[index].security = security
                                    callsOut[index].zid = ev[3]
                                    securityButton.setImageResource(security)
                                    Utils.setSecurityButtonTag(securityButton, security)
                                    securityButton.visibility = View.VISIBLE
                                } else {
                                    index = Call.callIndex(callsIn, ua, call)
                                    if (index != -1) {
                                        callsIn[index].security = security
                                        callsIn[index].zid = ev[3]
                                        if (ua == aor_ua(aors[aorSpinner.selectedItemPosition])) {
                                            val view_id = (index + 1) * 10 + 3
                                            val securityButton = layout.findViewById(view_id) as ImageButton
                                            securityButton.setImageResource(security)
                                            Utils.setSecurityButtonTag(securityButton, security)
                                            securityButton.visibility = View.VISIBLE
                                        }
                                    }
                                }
                                dialog.dismiss()
                            }
                            verifyDialog.setNegativeButton("No") { dialog, _ ->
                                var index = Call.callIndex(callsOut, ua, call)
                                if (index >= 0) {
                                    callsOut[index].security = R.drawable.box_yellow
                                    callsOut[index].zid = ev[3]
                                    securityButton.setImageResource(R.drawable.box_yellow)
                                    securityButton.tag = "yellow"
                                    securityButton.visibility = View.VISIBLE
                                } else {
                                    index = Call.callIndex(callsIn, ua, call)
                                    if (index != -1) {
                                        callsIn[index].security = R.drawable.box_yellow
                                        callsIn[index].zid = ev[3]
                                        if (ua == aor_ua(aors[aorSpinner.selectedItemPosition])) {
                                            val view_id = (index + 1) * 10 + 3
                                            val securityButton = layout.findViewById(view_id) as ImageButton
                                            securityButton.setImageResource(R.drawable.box_yellow)
                                            securityButton.tag = "yellow"
                                            securityButton.visibility = View.VISIBLE
                                        }
                                    } else {
                                        Log.e("Baresip", "Unknown call $ua/$call to verify")
                                    }
                                }
                                dialog.dismiss()
                            }
                            verifyDialog.create().show()
                        }
                    }
                    "call verified" -> {
                        val out_index = Call.callIndex(callsOut, ua, call)
                        if (out_index != -1) {
                            callsOut[out_index].security = R.drawable.box_green
                            callsOut[out_index].zid = ev[1]
                            this@MainActivity.runOnUiThread {
                                if (ua == aor_ua(aors[aorSpinner.selectedItemPosition])) {
                                    securityButton.setImageResource(R.drawable.box_green)
                                    securityButton.tag = "green"
                                    securityButton.visibility = View.VISIBLE
                                }
                            }
                        } else {
                            val in_index = Call.callIndex(callsIn, ua, call)
                            if (in_index != -1) {
                                callsIn[in_index].security = R.drawable.box_green
                                callsIn[in_index].zid = ev[1]
                                this@MainActivity.runOnUiThread {
                                    if (ua == aor_ua(aors[aorSpinner.selectedItemPosition])) {
                                        val view_id = (in_index + 1) * 10 + 3
                                        val securityButton = layout.findViewById(view_id) as ImageButton
                                        securityButton.setImageResource(R.drawable.box_green)
                                        securityButton.tag = "green"
                                        securityButton.visibility = View.VISIBLE
                                    }
                                }
                            } else {
                                Log.e("Baresip", "Unknown call $ua/$call verified")
                            }
                        }
                    }
                    "call closed" -> {
                        val in_index = Call.callIndex(callsIn, ua, call)
                        if (in_index != -1) {
                            Log.d("Baresip", "Removing inbound call " + ua + "/" +
                                    call + "/" + callsIn[in_index].peerURI)
                            val view_id = (in_index + 1) * 10
                            val remove_count = callsIn.size - in_index
                            callsIn.removeAt(in_index)
                            this@MainActivity.runOnUiThread {
                                if (ua == aor_ua(aors[aorSpinner.selectedItemPosition])) {
                                    if (callButton.text == "Call") {
                                        holdButton.text = "History"
                                        holdButton.visibility = View.VISIBLE
                                        callee.setOnKeyListener(null)
                                    }
                                    val caller_heading = layout.findViewById(view_id)
                                    val view_index = layout.indexOfChild(caller_heading)
                                    layout.removeViews(view_index, 4 * remove_count)
                                    for (i in in_index until callsIn.size) {
                                        this@MainActivity.addCallViews(ua, callsIn[i], (i + 1) * 10)
                                    }
                                }
                            }
                            if (!HistoryActivity.callHasHistory(ua, call)) {
                                if (HistoryActivity.aorHistory(aor) > HISTORY_SIZE)
                                    HistoryActivity.aorRemoveHistory(aor)
                                HistoryActivity.History.add(History(ua, call, aor, call_peeruri(call),
                                        "in", false))
                            }
                        } else {
                            val out_index = Call.callIndex(callsOut, ua, call)
                            if (out_index != -1) {
                                Log.d("Baresip", "Removing outgoing call " + ua + "/" +
                                        call + "/" + callsOut[out_index].peerURI)
                                this@MainActivity.runOnUiThread {
                                    if (ua == aor_ua(aors[aorSpinner.selectedItemPosition])) {
                                        callButton.text = "Call"
                                        callButton.isEnabled = true
                                        callee.setText("")
                                        callee.hint = "Callee"
                                        holdButton.text = "History"
                                        holdButton.visibility = View.VISIBLE
                                        if (this.currentFocus == dtmf) {
                                            imm.hideSoftInputFromWindow(dtmf.getWindowToken(), 0)
                                        }
                                        securityButton.visibility = View.INVISIBLE
                                        dtmf.removeTextChangedListener(callsOut[out_index].dtmfWatcher)
                                        dtmf.visibility = View.INVISIBLE
                                    }
                                    callsOut.removeAt(out_index)
                                }
                                if (!HistoryActivity.callHasHistory(ua, call)) {
                                    if (HistoryActivity.aorHistory(aor) > HISTORY_SIZE)
                                        HistoryActivity.aorRemoveHistory(aor)
                                    HistoryActivity.History.add(History(ua, call, aor, call_peeruri(call),
                                            "out", false))
                                }
                            } else {
                                Log.e("Baresip", "Unknown call " + ua + "/" + call +
                                        " closed")
                            }
                        }
                        am.mode = AudioManager.MODE_NORMAL
                    }
                    else -> Log.d("Baresip", "Unknown event '${ev[0]}'")
                }
            }
        }
    }

    external fun baresipStart(path: String)
    external fun baresipStop()
    external fun call_peeruri(call: String): String
    external fun aor_ua(aor: String): String
    external fun ua_aor(ua: String): String
    external fun ua_mediaenc(ua: String): String
    external fun ua_connect(ua: String, peer_uri: String): String
    external fun ua_answer(ua: String, call: String)
    external fun ua_hangup(ua: String, call: String, code: Int, reason: String)
    external fun call_hold(call: String): Int
    external fun call_unhold(call: String): Int
    external fun call_send_digit(call: String, digit: Char): Int
    external fun cmd_exec(cmd: String): Int
    external fun reload_config(): Int

    companion object {

        internal var running: Boolean = false
        internal var accounts = ArrayList<Account>()
        internal var aors = ArrayList<String>()
        internal var images = ArrayList<Int>()
        internal var callsIn = ArrayList<Call>()
        internal var callsOut = ArrayList<Call>()

        const val RECORD_AUDIO_PERMISSION = 1
        const val EDIT_ACCOUNTS_CODE = 1
        const val EDIT_CONTACTS_CODE = 2
        const val EDIT_CONFIG_CODE = 3
        const val HISTORY_CODE = 4
        const val ABOUT_CODE = 5

        const val HISTORY_SIZE = 100

        external fun contacts_remove()
        external fun contact_add(contact: String)

        init {
            System.loadLibrary("baresip")
        }
    }

}

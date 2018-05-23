package com.tutpro.baresip

import android.Manifest
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.widget.RelativeLayout
import android.media.AudioManager
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
    internal lateinit var callButton: Button
    internal lateinit var holdButton: Button
    internal lateinit var dtmf: EditText
    internal lateinit var uaAdapter: UaSpinnerAdapter
    internal lateinit var aorSpinner: Spinner
    internal lateinit var am: AudioManager
    internal lateinit var imm: InputMethodManager
    internal lateinit var nm: NotificationManager
    internal lateinit var nb: NotificationCompat.Builder

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
                                        .addCategory(Intent.CATEGORY_LAUNCHER)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        PendingIntent.FLAG_UPDATE_CURRENT))
                .setContent(RemoteViews(getPackageName(), R.layout.notification))

        aorSpinner = findViewById(R.id.AoRList) as Spinner
        uaAdapter = UaSpinnerAdapter(applicationContext, uas, images)
        aorSpinner.adapter = uaAdapter
        aorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val acc = uas[position].account
                val aor = acc.aor
                val ua = uas[position]
                Log.i("Baresip", "Setting $aor current")
                val callsOut = Call.uaCalls(calls, ua, "out")
                if (callsOut.size == 0) {
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
                    callee.setText(callsOut[0].peerURI)
                    callButton.text = callsOut[0].status
                    if (callsOut[0].status == "Hangup") {
                        if (callsOut[0].hold) {
                            holdButton.text = "Unhold"
                        } else {
                            holdButton.text = "Hold"
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
                if (view_count > 7) {
                    // remove all incoming call views
                    layout.removeViews(7, view_count - 7)
                }
                for (c in `in`) {
                    for (call_index in Call.calls(calls, "in").indices) {
                        addCallViews(ua, c, (call_index + 1) * 10)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                Log.i("Baresip", "Nothing selected")
            }
        }

        aorSpinner.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
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

        callButton.text = "Call"
        callButton.setOnClickListener {
            val ua = uas[aorSpinner.selectedItemPosition]
            val aor = ua.aor
            when (callButton.text.toString()) {
                "Call" -> {
                    val callee = (findViewById(R.id.callee) as EditText).text.toString()
                    if (callee.length > 0) {
                        var uri = EditContactsActivity.findContactURI(callee)
                        if (!uri.startsWith("sip:")) uri = "sip:$uri"
                        if (!uri.contains("@")) {
                            val host = aor.substring(aor.indexOf("@") + 1)
                            uri = "$uri@$host"
                        }
                        call(ua, uri)
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

        val holdButton = findViewById(R.id.holdButton) as Button
        holdButton.setOnClickListener {
            when (holdButton.text.toString()) {
                "Hold" -> {
                    Log.i("Baresip", "Holding up " + (findViewById(R.id.callee) as EditText).text)
                    val call = Call.calls(calls, "out")[0]
                    call_hold(call.callp)
                    call.hold = true
                    holdButton.text = "Unhold"
                }
                "Unhold" -> {
                    Log.i("Baresip", "Unholding " + (findViewById(R.id.callee) as EditText).text)
                    val call = Call.calls(calls, "out")[0]
                    call_unhold(call.callp)
                    call.hold = false
                    holdButton.text = "Hold"
                }
                "History" -> {
                    val i = Intent(this@MainActivity, HistoryActivity::class.java)
                    val b = Bundle()
                    b.putString("aor", uas[aorSpinner.selectedItemPosition].aor)
                    i.putExtras(b)
                    startActivityForResult(i, HISTORY_CODE)
                }
            }
        }

        if (!running) {
            Log.i("Baresip", "Starting Baresip with path $path")
            nm.notify(NOTIFICATION_ID, nb.build())
            Thread(Runnable { baresipStart(path) }).start()
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
                    // speakerIcon.setIcon(ContextCompat.getDrawable(this, R.drawable.speaker_on))
                    speakerIcon.setBackgroundColor(Color.rgb(0x04, 0xb4, 0x04))
                else
                    // speakerIcon.setBackgroundColor(resources.getColor(R.color.colorPrimary, applicationContext.theme))
                    speakerIcon.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                return true
            }
            R.id.accounts -> {
                i = Intent(this, AccountsActivity::class.java)
                startActivityForResult(i, ACCOUNTS_CODE)
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

        when (requestCode) {

            ACCOUNTS_CODE -> {
                if (resultCode == RESULT_OK)
                    Log.d("Baresip", "Edit accounts OK")
                if (resultCode == RESULT_CANCELED)
                    Log.d("Baresip", "Edit accounts canceled")
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
                    if (HistoryActivity.aorHistory(uas[aorSpinner.selectedItemPosition].aor) == 0) {
                        (findViewById(R.id.holdButton) as Button).visibility = View.INVISIBLE
                    }
                }
            }

            EDIT_CONTACTS_CODE, ABOUT_CODE -> {
                Log.d("Baresip", "Back arrow or Cancel pressed at request: $requestCode")
            }
        }
    }

    private fun call(ua: UserAgent, uri: String) {
        (findViewById(R.id.callee) as EditText).setText(uri)
        Log.i("Baresip", "Calling $ua.uap / $uri")
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
            (findViewById(R.id.callButton) as Button).text = "Cancel"
            (findViewById(R.id.holdButton) as Button).visibility = View.INVISIBLE
        }
    }

    private fun addCallViews(ua: UserAgent, call: Call, id: Int) {
        Log.d("Baresip", "Creating new Incoming textview at $id")

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
        layout.addView(caller_heading)

        val caller_row = LinearLayout(appContext)
        caller_row.id = id + 1
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
        caller_row.addView(security_button)

        layout.addView(caller_row)

        val answer_button = Button(appContext)
        answer_button.text = call.status
        answer_button.setBackgroundResource(android.R.drawable.btn_default)
        answer_button.setTextColor(Color.BLACK)
        Log.d("Baresip", "Creating new answer button at " + (id + 4))
        answer_button.id = id + 4
        answer_button.setOnClickListener { v ->
            when ((v as Button).text.toString()) {
                "Answer" -> {
                    Log.i("Baresip", "UA ${call.ua.uap} accepting incoming call ${call.callp}")
                    ua_answer(call.ua.uap, call.callp)
                    if (call.dir == "in") {
                        Log.d("Baresip", "Updating Hangup and Hold")
                        call.status = "Hangup"
                        call.hold = false
                        this@MainActivity.runOnUiThread {
                            val answer_id = (Call.index(calls, call,"in") + 1) * 10 + 4
                            val answer_but = layout.findViewById(answer_id) as Button
                            answer_but.text = "Hangup"
                            val reject_button = layout.findViewById(answer_id + 1) as Button
                            reject_button.text = "Hold"
                        }
                    }
                }
                "Hangup" -> {
                    Log.i("Baresip", "UA ${call.ua.uap} hanging up call ${call.callp}")
                    ua_hangup(call.ua.uap, call.callp, 200, "OK")
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
                            " rejecting incoming call " + call.callp)
                    ua_hangup(call.ua.uap, call.callp, 486, "Rejected")
                }
                "Hold" -> {
                    call_hold(call.callp)
                    v.text = "Unhold"
                    call.hold = true
                }
                "Unhold" -> {
                    call_unhold(call.callp)
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

    private fun updateNotification() {
        val contentView = RemoteViews(getPackageName(), R.layout.notification)
        for (i: Int in 0 .. 3)  {
            val resID = resources.getIdentifier("status$i", "id", packageName);
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
        nb.setContent(contentView);
        nm.notify(NOTIFICATION_ID, nb.build())
    }

    @Keep
    fun addAccount(ua: String) {
        val userAgent = UserAgent(ua)
        uas.add(userAgent)
        images.add(R.drawable.dot_yellow)
        this@MainActivity.runOnUiThread {
            uaAdapter.notifyDataSetChanged()
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
        val aor = ua.aor
        val acc = ua.account
        val ev = event.split(",")

        Log.d("Baresip", "Handling event ${ev[0]} for $uap/$callp/$aor")

        for (account_index in uas.indices) {
            if (uas[account_index].aor == aor) {
                Log.d("Baresip", "Found AoR at index $account_index")
                when (ev[0]) {
                    "registering", "unregistering" -> {
                    }
                    "registered" -> {
                        Log.d("Baresip", "Setting status to green")
                        images[account_index] = R.drawable.dot_green
                        this@MainActivity.runOnUiThread {
                            uaAdapter.notifyDataSetChanged()
                            updateNotification()
                        }
                    }
                    "registering failed" -> {
                        Log.d("Baresip", "Setting status to red")
                        images[account_index] = R.drawable.dot_red
                        this@MainActivity.runOnUiThread {
                            uaAdapter.notifyDataSetChanged()
                            updateNotification()
                        }
                    }
                    "call ringing" -> {
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
                                    callButton.text = "Hangup"
                                    holdButton.text = "Hold"
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
                            HistoryActivity.History.add(History(aor, call.peerURI, "out",
                                    true))
                            call.hasHistory = true
                        } else {
                            Log.d("Baresip", "Inbound call $callp established")
                            call.status = "Answer"
                            call.security = R.drawable.box_red
                            this@MainActivity.runOnUiThread {
                                if (ua == uas[aorSpinner.selectedItemPosition]) {
                                    if (acc.mediaenc != "") {
                                        val inIndex = Call.index(calls, call, "in")
                                        var view_id = (inIndex + 1) * 10 + 3
                                        val securityButton = findViewById(view_id) as ImageButton
                                        securityButton.setImageResource(R.drawable.box_red)
                                        securityButton.tag = "red"
                                        securityButton.visibility = View.VISIBLE
                                        view_id = (inIndex + 1) * 10 + 5
                                        val rejectButton = findViewById(view_id) as Button
                                        rejectButton.text = "Hold"
                                    }
                                }
                            }
                            HistoryActivity.History.add(History(aor, call.peerURI, "in",
                                    true))
                            call.hasHistory = true
                        }
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                        am.isSpeakerphoneOn = false
                        // am.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                        //        (am.getStreamMaxVolume(STREAM_VOICE_CALL) / 2) + 1,
                        //        AudioManager.STREAM_VOICE_CALL)
                        if (HistoryActivity.aorHistory(aor) > HISTORY_SIZE)
                            HistoryActivity.aorRemoveHistory(aor)
                    }
                    "call incoming" -> {
                        val peer_uri = call_peeruri(callp)
                        Log.d("Baresip", "Incoming call $uap/$callp/$peer_uri")
                        val new_call = Call(callp, ua, peer_uri, "in", "Answer", null)
                        calls.add(new_call)
                        this@MainActivity.runOnUiThread {
                            if (ua == uas[aorSpinner.selectedItemPosition]) {
                                addCallViews(ua, new_call, Call.calls(calls, "in").size * 10)
                            }
                        }
                        am.mode = AudioManager.MODE_RINGTONE
                        // am.isSpeakerphoneOn = true
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
                                        val view_id = (Call.index(calls, call, "in") + 1) * 10 + 3
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
                                        val view_id = (Call.index(calls, call, "in") + 1) * 10 + 3
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
                                    val view_id = (Call.index(calls, call, "in") + 1) * 10 + 3
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
                            Log.e("Baresip", "Call $callp that is closed is not found")
                            return
                        }
                        if (call.dir == "in") {
                            Log.d("Baresip", "Removing inbound call $uap / $callp / " +
                                    call.peerURI)
                            val inIndex = Call.index(calls, call, "in")
                            val view_id = (inIndex + 1) * 10
                            val remove_count = Call.calls(calls, "in").size - inIndex
                            calls.removeAt(Call.index(calls, call, ""))
                            this@MainActivity.runOnUiThread {
                                if (ua == uas[aorSpinner.selectedItemPosition]) {
                                    if (callButton.text == "Call") {
                                        holdButton.text = "History"
                                        holdButton.visibility = View.VISIBLE
                                        callee.setOnKeyListener(null)
                                    }
                                    val caller_heading = layout.findViewById(view_id)
                                    val view_index = layout.indexOfChild(caller_heading)
                                    layout.removeViews(view_index, 4 * remove_count)
                                    val callsIn = Call.calls(calls, "in")
                                    for (i in inIndex until callsIn.size) {
                                        this@MainActivity.addCallViews(ua, callsIn[i], (i + 1) * 10)
                                    }
                                }
                            }
                            if (!call.hasHistory) {
                                if (HistoryActivity.aorHistory(aor) > HISTORY_SIZE)
                                    HistoryActivity.aorRemoveHistory(aor)
                                HistoryActivity.History.add(History(aor, call.peerURI,
                                        "in", false))
                            }
                        } else {
                            Log.d("Baresip", "Removing outgoing call $uap / $callp / " +
                                        call.peerURI)
                                this@MainActivity.runOnUiThread {
                                    if (ua == uas[aorSpinner.selectedItemPosition]) {
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
                                        dtmf.removeTextChangedListener(call.dtmfWatcher)
                                        dtmf.visibility = View.INVISIBLE
                                    }
                                    calls.removeAt(Call.index(calls, call, ""))
                                }
                                if (!call.hasHistory) {
                                    if (HistoryActivity.aorHistory(aor) > HISTORY_SIZE)
                                        HistoryActivity.aorRemoveHistory(aor)
                                    HistoryActivity.History.add(History(aor, call.peerURI,
                                            "out", false))
                                }
                        }
                        if (calls.size == 0) am.mode = AudioManager.MODE_NORMAL
                    }
                    else -> Log.d("Baresip", "Unknown event '${ev[0]}'")
                }
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

    external fun baresipStart(path: String)
    external fun baresipStop()
    external fun ua_connect(ua: String, peer_uri: String): String
    external fun ua_answer(ua: String, call: String)
    external fun ua_hangup(ua: String, call: String, code: Int, reason: String)
    external fun call_peeruri(call: String): String
    external fun call_hold(call: String): Int
    external fun call_unhold(call: String): Int
    external fun call_send_digit(call: String, digit: Char): Int
    external fun reload_config(): Int

    companion object {

        internal var running: Boolean = false
        var uas = ArrayList<UserAgent>()
        internal var images = ArrayList<Int>()
        internal var calls = ArrayList<Call>()
        var filesPath = ""

        const val ACCOUNTS_CODE = 1
        const val EDIT_CONTACTS_CODE = 2
        const val EDIT_CONFIG_CODE = 3
        const val HISTORY_CODE = 4
        const val ABOUT_CODE = 5
        const val ACCOUNT_CODE = 6

        const val RECORD_AUDIO_PERMISSION = 1
        const val HISTORY_SIZE = 100
        const val NOTIFICATION_ID = 10

        external fun contacts_remove()
        external fun contact_add(contact: String)

        init {
            System.loadLibrary("baresip")
        }
    }

}

package com.tutpro.baresip

import android.Manifest
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
import android.widget.*
import android.widget.RelativeLayout.LayoutParams
import android.widget.AutoCompleteTextView
import android.view.*
import android.util.Log

import java.util.*
import java.io.*
import android.widget.RelativeLayout

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainActivityContext = applicationContext
        layout = findViewById(R.id.mainActivityLayout) as RelativeLayout
        callee = findViewById(R.id.callee) as AutoCompleteTextView
        callButton = findViewById(R.id.callButton) as Button
        holdButton = findViewById(R.id.holdButton) as Button

        AoRSpinner = findViewById(R.id.AoRList) as Spinner

        Log.i("Baresip", "Setting AccountAdapter")
        AccountAdapter = AccountSpinnerAdapter(applicationContext, AoRs, Images)
        AoRSpinner.adapter = AccountAdapter
        AoRSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val aor = AoRs[position]
                val callee = findViewById(R.id.callee) as AutoCompleteTextView
                val callButton = findViewById(R.id.callButton) as Button
                val holdButton = findViewById(R.id.holdButton) as Button
                Log.i("Baresip", "Setting $aor current")
                val out = uaCalls(Out, aor_ua(aor))
                if (out.size == 0) {
                    callee.text.clear()
                    callee.hint = "Callee"
                    callButton.text = "Call"
                    if (aorHasHistory(aor)) {
                        holdButton.text = "History"
                        holdButton.visibility = View.VISIBLE
                    } else {
                        holdButton.visibility = View.INVISIBLE
                    }
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
                    } else {
                        holdButton.visibility = View.INVISIBLE
                    }
                }
                val `in` = uaCalls(In, aor_ua(aor))
                val view_count = layout.childCount
                Log.d("Baresip", "View count is $view_count")
                if (view_count > 5) {
                    layout.removeViews(5, view_count - 5)
                }
                for (c in `in`) {
                    for (call_index in In.indices) {
                        addCallViews(c, (call_index + 1) * 10)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                Log.i("Baresip", "Nothing selected")
            }
        }

        val assets = arrayOf("accounts", "contacts", "config", "busy.wav", "callwaiting.wav", "error.wav", "message.wav", "notfound.wav", "ring.wav", "ringback.wav")
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
                copyAssetToFile(a, "$path/$a")
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d("Baresip", "Baresip does not have RECORD_AUDIO permission")
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION)
        }

        if (!running) {
            Log.i("Baresip", "Starting Baresip with path $path")
            Thread(Runnable { baresipStart(path) }).start()
            running = true
        }

        EditContactsActivity.updateContactsAndNames(applicationContext.filesDir.absolutePath + "/contacts")
        CalleeAdapter = ArrayAdapter(this, android.R.layout.select_dialog_item, EditContactsActivity.Names)
        callee.threshold = 2
        callee.setAdapter<ArrayAdapter<String>>(CalleeAdapter)

        callButton.text = "Call"
        callButton.setOnClickListener {
            Log.d("Baresip", "AoR at position is " + AoRs[AoRSpinner.selectedItemPosition])
            val aor = AoRs[AoRSpinner.selectedItemPosition]
            if (callButton.text.toString() == "Call") {
                call(aor, (findViewById(R.id.callee) as EditText).text.toString())
            } else {
                Log.i("Baresip", "Canceling UA " + aor_ua(aor) + " call " +
                        Out[0].call + " to " +
                        (findViewById(R.id.callee) as EditText).text)
                ua_hangup(aor_ua(aor), Out[0].call, 486, "Rejected")
            }
        }

        val holdButton = findViewById(R.id.holdButton) as Button
        holdButton.setOnClickListener {
            when (holdButton.text.toString()) {
                "Hold" -> {
                    Log.i("Baresip", "Holding up " + (findViewById(R.id.callee) as EditText).text)
                    call_hold(Out[0].call)
                    Out[0].hold = true
                    holdButton.text = "Unhold"
                }
                "Unhold" -> {
                    Log.i("Baresip", "Unholding " + (findViewById(R.id.callee) as EditText).text)
                    call_unhold(Out[0].call)
                    Out[0].hold = false
                    holdButton.text = "Hold"
                }
                "History" -> {
                    val i = Intent(this@MainActivity, HistoryActivity::class.java)
                    val b = Bundle()
                    b.putString("aor", AoRs[AoRSpinner.selectedItemPosition])
                    i.putExtras(b)
                    startActivityForResult(i, HISTORY_CODE)
                }
            }
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
                val alertDialog = AlertDialog.Builder(this).create()
                alertDialog.setTitle("Alert")
                alertDialog.setMessage("You need to restart baresip in order to activate saved accounts!")
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK"
                ) { dialog, _ -> dialog.dismiss() }
                alertDialog.show()
            }
            if (resultCode == RESULT_CANCELED) {
                Log.d("Baresip", "Edit accounts canceled")
            }
        }
        if (requestCode == EDIT_CONFIG_CODE) {
            if (resultCode == RESULT_OK) {
                Utils.alertView(this,
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
                if (!aorHasHistory(AoRs[AoRSpinner.selectedItemPosition])) {
                    (findViewById(R.id.holdButton) as Button).visibility = View.INVISIBLE
                }
            }
        }
        if (requestCode == EDIT_CONTACTS_CODE || requestCode == ABOUT_CODE) {
            Log.d("Baresip", "Back arrow or Cancel pressed at request: $requestCode")
        }
    }

    fun addAccount(ua: String) {
        val aor = ua_aor(ua)
        Log.d("Baresip", "Adding account $ua with AoR $aor")
        Accounts.add(Account(ua, aor, ""))
        AoRs.add(aor)
        Images.add(R.drawable.yellow)
        runOnUiThread { AccountAdapter.notifyDataSetChanged() }
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
            Out.add(Call(ua, call, uri, "Cancel"))
            (findViewById(R.id.callButton) as Button).text = "Cancel"
            (findViewById(R.id.holdButton) as Button).visibility = View.INVISIBLE
        }
    }

    private fun copyAssetToFile(asset: String, path: String) {
        try {
            val assetManager = assets
            val `is` = assetManager.open(asset)
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

    private fun addCallViews(call: Call, id: Int) {
        Log.d("Baresip", "Creating new Incoming textview at $id")

        val caller_heading = TextView(mainActivityContext)

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
            heading_params.addRule(RelativeLayout.BELOW, id - 10 + 3)
        caller_heading.layoutParams = heading_params
        layout.addView(caller_heading)

        val caller_uri = TextView(mainActivityContext)
        caller_uri.text = In[In.size - 1].peerURI
        caller_uri.setTextColor(Color.GREEN)
        caller_uri.textSize = 20f
        caller_uri.setPadding(10, 10, 0, 10)
        Log.d("Baresip", "Creating new caller textview at " + (id + 1))
        caller_uri.id = id + 1
        val caller_uri_params = LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT)
        caller_uri_params.addRule(RelativeLayout.BELOW, id)
        caller_uri.layoutParams = caller_uri_params
        layout.addView(caller_uri)

        val answer_button = Button(mainActivityContext)
        answer_button.text = call.status
        answer_button.setBackgroundResource(android.R.drawable.btn_default)
        answer_button.setTextColor(Color.BLACK)
        Log.d("Baresip", "Creating new answer button at " + (id + 2))
        answer_button.id = id + 2
        answer_button.setOnClickListener { v ->
            val call_ua = call.ua
            val call_call = call.call
            when ((v as Button).text.toString()) {
                "Answer" -> {
                    Log.i("Baresip", "UA " + call_ua + " accepting incoming call " +
                            call_call)
                    ua_answer(call_ua, call_call)
                    val final_in_index = callIndex(In, call_ua, call_call)
                    if (final_in_index >= 0) {
                        Log.d("Baresip", "Updating Hangup and Hold")
                        In[final_in_index].status = "Hangup"
                        In[final_in_index].hold = false
                        runOnUiThread {
                            val answer_id = (final_in_index + 1) * 10 + 2
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
        val answer_button_params = LayoutParams(200,
                LayoutParams.WRAP_CONTENT)
        answer_button_params.addRule(RelativeLayout.BELOW, id + 1)
        answer_button_params.setMargins(3, 10, 0, 0)
        answer_button.layoutParams = answer_button_params
        layout.addView(answer_button)

        val reject_button = Button(mainActivityContext)
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
        Log.d("Baresip", "Creating new reject button at " + (id + 3))
        reject_button.id = id + 3
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

    private fun callIndex(calls: ArrayList<Call>, ua: String, call: String): Int {
        for (i in calls.indices) {
            if (calls[i].ua.equals(ua) && calls[i].call == call)
                return i
        }
        return -1
    }

    private fun uaCalls(calls: ArrayList<Call>, ua: String): ArrayList<Call> {
        val result = ArrayList<Call>()
        for (i in calls.indices) {
            if (calls[i].ua == ua) result.add(calls[i])
        }
        return result
    }

    private fun aorHasHistory(aor: String): Boolean {
        for (h in HistoryActivity.History) {
            if (h.aor == aor) return true
        }
        return false
    }

    private fun callHasHistory(ua: String, call: String): Boolean {
        for (h in HistoryActivity.History) {
            if (h.ua == ua && h.call == call) return true
        }
        return false
    }

    private fun updateStatus(event: String, ua: String, call: String) {
        val aor = ua_aor(ua)
        var call_index: Int

        Log.d("Baresip", "Handling event " + event + " for " + ua + "/" + call + "/" +
                aor)

        for (account_index in Accounts.indices) {
            if (Accounts[account_index].aoR == aor) {
                Log.d("Baresip", "Found AoR at index $account_index")
                when (event) {
                    "registering", "unregistering" -> {
                    }
                    "registered" -> {
                        Log.d("Baresip", "Setting status to green")
                        Accounts[account_index].status = "OK"
                        AoRs[account_index] = aor
                        Images[account_index] = R.drawable.green
                        runOnUiThread { AccountAdapter.notifyDataSetChanged() }
                    }
                    "registering failed" -> {
                        Log.d("Baresip", "Setting status to red")
                        Accounts[account_index].status = "FAIL"
                        AoRs[account_index] = aor
                        Images[account_index] = R.drawable.red
                        runOnUiThread { AccountAdapter.notifyDataSetChanged() }
                    }
                    "call ringing" -> {
                    }
                    "call established" -> {
                        val out_index = callIndex(Out, ua, call)
                        if (out_index >= 0) {
                            Log.d("Baresip", "Outbound call " + call + " established")
                            Out[out_index].status = "Hangup"
                            Out[out_index].hold = false
                            runOnUiThread {
                                if (ua == aor_ua(AoRs[AoRSpinner.selectedItemPosition])) {
                                    callButton.text = "Hangup"
                                    holdButton.text = "Hold"
                                    holdButton.visibility = View.VISIBLE
                                }
                            }
                            HistoryActivity.History.add(History(ua, call, aor, call_peeruri(call),
                                    "out", true))
                        } else {
                            Log.d("Baresip", "Inbound call " + call + " established")
                            HistoryActivity.History.add(History(ua, call, aor, call_peeruri(call),
                                    "in", true))
                        }
                    }
                    "call incoming" -> {
                        val peer_uri = call_peeruri(call)
                        Log.d("Baresip", "Incoming call " + ua + "/" + call + "/" +
                                peer_uri)
                        val new_call = Call(ua, call, peer_uri, "Answer")
                        In.add(new_call)
                        this@MainActivity.runOnUiThread {
                            if (ua == aor_ua(AoRs[AoRSpinner.selectedItemPosition])) {
                                addCallViews(new_call, In.size * 10)
                            }
                        }
                    }
                    "call closed" -> {
                        call_index = callIndex(In, ua, call)
                        if (call_index != -1) {
                            Log.d("Baresip", "Removing inbound call " + ua + "/" +
                                    call + "/" + In[call_index].peerURI)
                            val view_id = (call_index + 1) * 10
                            val remove_count = In.size - call_index
                            In.removeAt(call_index)
                                this@MainActivity.runOnUiThread {
                                    if (ua == aor_ua(AoRs[AoRSpinner.selectedItemPosition])) {
                                        if (callButton.text == "Call") {
                                            holdButton.text = "History"
                                            holdButton.visibility = View.VISIBLE
                                        }
                                        val caller_heading = layout.findViewById(view_id)
                                        val view_index = layout.indexOfChild(caller_heading)
                                        Log.d("Baresip", "Index of caller heading is $view_index")
                                        layout.removeViews(view_index, 4 * remove_count)
                                        for (i in call_index until In.size) {
                                            this@MainActivity.addCallViews(In[i], (i + 1) * 10)
                                        }
                                    }
                                }
                            if (!callHasHistory(ua, call)) {
                                HistoryActivity.History.add(History(ua, call, aor, call_peeruri(call),
                                        "in", false))
                            }
                        } else {
                            call_index = callIndex(Out, ua, call)
                            if (call_index != -1) {
                                Log.d("Baresip", "Removing outgoing call " + ua + "/" +
                                        call + "/" + Out[call_index].peerURI)
                                Out.removeAt(call_index)
                                runOnUiThread {
                                    if (ua == aor_ua(AoRs[AoRSpinner.selectedItemPosition])) {
                                        callButton.text = "Call"
                                        callButton.isEnabled = true
                                        callee.setText("")
                                        callee.hint = "Callee"
                                        holdButton.text = "History"
                                        holdButton.visibility = View.VISIBLE
                                    }
                                }
                                if (!callHasHistory(ua, call)) {
                                    HistoryActivity.History.add(History(ua, call, aor, call_peeruri(call),
                                            "out", false))
                                }
                            } else {
                                Log.e("Baresip", "Unknown call " + ua + "/" + call +
                                        " closed")
                            }
                        }
                    }
                    else -> Log.d("Baresip", "Unknown event '$event'")
                }
            }
        }
    }

    external fun baresipStart(path: String)
    external fun baresipStop()
    external fun call_peeruri(call: String): String
    external fun ua_aor(ua: String): String
    external fun aor_ua(aor: String): String
    external fun ua_connect(ua: String, peer_uri: String): String
    external fun ua_answer(ua: String, call: String)
    external fun call_hold(call: String): Int?
    external fun call_unhold(call: String): Int?
    external fun ua_hangup(ua: String, call: String, code: Int, reason: String)
    external fun reload_config(): Int

    companion object {

        internal lateinit var mainActivityContext: Context
        internal lateinit var layout: RelativeLayout
        internal lateinit var callee: AutoCompleteTextView
        internal lateinit var callButton: Button
        internal lateinit var holdButton: Button
        internal lateinit var AccountAdapter: AccountSpinnerAdapter
        internal lateinit var AoRSpinner: Spinner

        internal var running: Boolean = false
        internal var Accounts = ArrayList<Account>()
        internal var AoRs = ArrayList<String>()
        internal var Images = ArrayList<Int>()
        internal var CalleeAdapter: ArrayAdapter<String>? = null
        internal var In = ArrayList<Call>()
        internal var Out = ArrayList<Call>()

        const val RECORD_AUDIO_PERMISSION = 1
        const val EDIT_ACCOUNTS_CODE = 1
        const val EDIT_CONTACTS_CODE = 2
        const val EDIT_CONFIG_CODE = 3
        const val HISTORY_CODE = 4
        const val ABOUT_CODE = 5

        external fun contacts_remove()
        external fun contact_add(contact: String)

        init {
            System.loadLibrary("baresip")
        }
    }

}

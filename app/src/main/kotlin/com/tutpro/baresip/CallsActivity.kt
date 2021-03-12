package com.tutpro.baresip

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.text.format.DateUtils.isToday
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.TextView
import com.tutpro.baresip.databinding.ActivityCallsBinding

import java.util.ArrayList
import java.text.DateFormat

class CallsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallsBinding
    private lateinit var account: Account
    private lateinit var clAdapter: CallListAdapter

    private var uaHistory = ArrayList<CallRow>()
    private var aor = ""
    private var lastClick: Long = 0

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityCallsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aor = intent.getStringExtra("aor")!!
        Utils.addActivity("calls,$aor")

        val ua = UserAgent.ofAor(aor)!!
        account = ua.account

        val headerView = binding.account
        val headerText = "${getString(R.string.account)} ${aor.split(":")[1]}"
        headerView.text = headerText

        val listView = binding.calls
        aorGenerateHistory(aor)
        clAdapter = CallListAdapter(this, uaHistory)
        listView.adapter = clAdapter
        listView.isLongClickable = true

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val peerUri = uaHistory[pos].peerUri
            var peerName = ContactsActivity.contactName(peerUri)
            if (peerName.startsWith("sip:"))
                peerName = Utils.friendlyUri(peerName, Utils.aorDomain(aor))
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE -> {
                        BaresipService.activities.remove("calls,$aor")
                        MainActivity.activityAor = aor
                        returnResult()
                        val i = Intent(this@CallsActivity, MainActivity::class.java)
                        i.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        if (which == DialogInterface.BUTTON_NEGATIVE)
                            i.putExtra("action", "call")
                        else
                            i.putExtra("action", "message")
                        i.putExtra("uap", ua.uap)
                        i.putExtra("peer", peerUri)
                        startActivity(i)
                    }
                    DialogInterface.BUTTON_NEUTRAL -> {
                    }
                }
            }
            if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                lastClick = SystemClock.elapsedRealtime()
                with (AlertDialog.Builder(this@CallsActivity, R.style.Theme_AppCompat)) {
                    setMessage(String.format(getString(R.string.calls_call_message_question),
                            peerName))
                    setNeutralButton(getString(R.string.cancel), dialogClickListener)
                    setNegativeButton(getString(R.string.call), dialogClickListener)
                    setPositiveButton(getString(R.string.send_message), dialogClickListener)
                    show()
                }
            }
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val peerUri = uaHistory[pos].peerUri
            val peerName = ContactsActivity.contactName(peerUri)
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_NEGATIVE -> {
                        val i = Intent(this, ContactActivity::class.java)
                        val b = Bundle()
                        b.putBoolean("new", true)
                        b.putString("uri", peerUri)
                        i.putExtras(b)
                        startActivityForResult(i, MainActivity.CONTACT_CODE)
                    }
                    DialogInterface.BUTTON_POSITIVE -> {
                        removeUaHistoryAt(pos)
                        CallHistory.save()
                        clAdapter.notifyDataSetChanged()
                    }
                    DialogInterface.BUTTON_NEUTRAL -> {
                    }
                }
            }
            val callText: String
            if (uaHistory[pos].directions.size > 1)
                callText = getString(R.string.calls_calls)
            else
                callText = getString(R.string.calls_call)
            val builder = AlertDialog.Builder(this@CallsActivity, R.style.Theme_AppCompat)
            if (peerName.startsWith("sip:"))
                with (builder) {
                    setMessage(String.format(getString(R.string.calls_add_delete_question),
                            Utils.friendlyUri(peerName, Utils.aorDomain(aor)), callText))
                    setNeutralButton(getString(R.string.cancel), dialogClickListener)
                    setNegativeButton(getString(R.string.add_contact), dialogClickListener)
                    setPositiveButton(String.format(getString(R.string.delete), callText), dialogClickListener)
                    show()
                }
            else
                with (builder) {
                    setMessage(String.format(getString(R.string.calls_delete_question),
                            Utils.friendlyUri(peerName, Utils.aorDomain(aor)), callText))
                    setNeutralButton(getString(R.string.cancel), dialogClickListener)
                    setPositiveButton(String.format(getString(R.string.delete), callText), dialogClickListener)
                    show()
                }
            true
        }

        ua.account.missedCalls = false
        invalidateOptionsMenu()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("calls,$aor") == -1)
            return true

        when (item.itemId) {

            R.id.delete_history -> {
                val titleView = View.inflate(this, R.layout.alert_title, null) as TextView
                titleView.text = getString(R.string.confirmation)
                with (AlertDialog.Builder(this@CallsActivity)) {
                    setCustomTitle(titleView)
                    setMessage(String.format(getString(R.string.delete_history_alert),
                            aor.substringAfter(":")))
                    setPositiveButton(getText(R.string.delete)) { dialog, _ ->
                        CallHistory.clear(aor)
                        CallHistory.save()
                        aorGenerateHistory(aor)
                        clAdapter.notifyDataSetChanged()
                        dialog.dismiss()
                    }
                    setNegativeButton(getText(R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    show()
                }
                return true
            }

            R.id.history_on_off -> {
                account.callHistory = !account.callHistory
                invalidateOptionsMenu()
                AccountsActivity.saveAccounts()
                return true
            }

            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        BaresipService.activities.remove("calls,$aor")
        returnResult()
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
    }

    private fun returnResult() {
        val i = Intent()
        setResult(Activity.RESULT_CANCELED, i)
        finish()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {

        if (account.callHistory)
            menu.findItem(R.id.history_on_off).title = getString(R.string.disable_history)
        else
            menu.findItem(R.id.history_on_off).title = getString(R.string.enable_history)
        return super.onPrepareOptionsMenu(menu)

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menuInflater.inflate(R.menu.calls_menu, menu)
        return true

    }

    private fun aorGenerateHistory(aor: String) {
        uaHistory.clear()
        for (i in BaresipService.callHistory.indices.reversed()) {
            val h = BaresipService.callHistory[i]
            if (h.aor == aor) {
                var direction: Int
                if (h.direction == "in")
                    if (h.connected)
                        direction = R.drawable.arrow_down_green
                    else
                        direction = R.drawable.arrow_down_red
                else
                    if (h.connected)
                        direction = R.drawable.arrow_up_green
                    else
                        direction = R.drawable.arrow_up_red
                if (uaHistory.isNotEmpty() && (uaHistory.last().peerUri == h.peerUri)) {
                    uaHistory.last().directions.add(direction)
                    uaHistory.last().indexes.add(i)
                } else {
                    val fmt: DateFormat
                    if (isToday(h.time.timeInMillis))
                        fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
                    else
                        fmt = DateFormat.getDateInstance(DateFormat.SHORT)
                    val time = fmt.format(h.time.time)
                    uaHistory.add(CallRow(h.aor, h.peerUri, direction, time, i))
                }
            }
        }
    }

    private fun removeUaHistoryAt(i: Int) {
        for (index in uaHistory[i].indexes)
            BaresipService.callHistory.removeAt(index)
        uaHistory.removeAt(i)
    }

}

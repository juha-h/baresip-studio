package com.tutpro.baresip

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.GregorianCalendar

class CallsActivity : AppCompatActivity() {

    internal var uaHistory = ArrayList<CallRow>()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_calls)

        val aor = intent.getStringExtra("aor")!!
        val ua = Account.findUa(aor)!!

        val headerView = findViewById(R.id.account) as TextView
        val headerText = "${getString(R.string.account)} ${aor.substringAfter(":")}"
        headerView.text = headerText

        val listView = findViewById(R.id.calls) as ListView
        aorGenerateHistory(aor)
        val adapter = CallListAdapter(this, uaHistory)
        listView.adapter = adapter
        listView.isLongClickable = true

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val peerUri = uaHistory[pos].peerURI
            var peerName = ContactsActivity.contactName(peerUri)
            if (peerName.startsWith("sip:"))
                peerName = Utils.friendlyUri(peerName, Utils.aorDomain(aor))
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE -> {
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
            val builder = AlertDialog.Builder(this@CallsActivity, R.style.Theme_AppCompat)
            builder.setMessage(String.format(getString(R.string.calls_call_message_question),
                    peerName))
                    .setNeutralButton(getString(R.string.cancel), dialogClickListener)
                    .setNegativeButton(getString(R.string.call), dialogClickListener)
                    .setPositiveButton(getString(R.string.send_message), dialogClickListener)
                    .show()
        }
        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val peerUri = uaHistory[pos].peerURI
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
                        if (uaHistory.size == 0) {
                            val i = Intent()
                            setResult(Activity.RESULT_CANCELED, i)
                            finish()
                        }
                        adapter.notifyDataSetChanged()
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
                builder.setMessage(String.format(getString(R.string.calls_add_delete_question),
                        Utils.friendlyUri(peerName, Utils.aorDomain(aor)), callText))
                        .setNeutralButton(getString(R.string.cancel), dialogClickListener)
                        .setNegativeButton(getString(R.string.add_contact), dialogClickListener)
                        .setPositiveButton(String.format(getString(R.string.delete), callText),
                                dialogClickListener)
                        .show()
            else
                builder.setMessage(String.format(getString(R.string.calls_delete_question),
                        Utils.friendlyUri(peerName, Utils.aorDomain(aor)), callText))
                        .setNeutralButton(getString(R.string.cancel), dialogClickListener)
                        .setPositiveButton(String.format(getString(R.string.delete), callText),
                                dialogClickListener)
                        .show()
            true
        }

        ua.account.missedCalls = false
    }

    override fun onPause() {
        CallHistory.save(applicationContext.filesDir.absolutePath)
        super.onPause()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Log.d("Baresip", "Back array was pressed at Calls")
                val i = Intent()
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }
        }
        return true
    }

    private fun aorGenerateHistory(aor: String) {
        uaHistory.clear()
        for (i in CallHistory.history().indices.reversed()) {
            val h = CallHistory.history()[i]
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
                if (uaHistory.isNotEmpty() && (uaHistory.last().peerURI == h.peerURI)) {
                    uaHistory.last().directions.add(direction)
                    uaHistory.last().indexes.add(i)
                } else {
                    val time: String
                    if (isToday(h.time)) {
                        val fmt = SimpleDateFormat("HH:mm")
                        time = fmt.format(h.time.time)
                    } else {
                        val fmt = SimpleDateFormat("dd.MM")
                        time = fmt.format(h.time.time)
                    }
                    uaHistory.add(CallRow(h.aor, h.peerURI, direction, time, i))
                }
            }
        }
    }

    private fun removeUaHistoryAt(i: Int) {
        for (index in uaHistory[i].indexes)
            CallHistory.history().removeAt(index)
        uaHistory.removeAt(i)
    }

    private fun isToday(time: GregorianCalendar): Boolean {
        val now = GregorianCalendar()
        return now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
                now.get(Calendar.MONTH) == time.get(Calendar.MONTH) &&
                now.get(Calendar.DAY_OF_MONTH) == time.get(Calendar.DAY_OF_MONTH)
    }

}

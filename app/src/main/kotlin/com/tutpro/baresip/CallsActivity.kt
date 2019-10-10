package com.tutpro.baresip

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.GregorianCalendar

class CallsActivity : AppCompatActivity() {

    internal lateinit var account: Account
    internal lateinit var clAdapter: CallListAdapter

    internal var uaHistory = ArrayList<CallRow>()
    internal var aor = ""

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_calls)

        aor = intent.getStringExtra("aor")!!
        BaresipService.activities.add(0, "calls,$aor")
        val ua = Account.findUa(aor)!!
        account = ua.account

        val headerView = findViewById(R.id.account) as TextView
        val headerText = "${getString(R.string.account)} ${aor.substringAfter(":")}"
        headerView.text = headerText

        val listView = findViewById(R.id.calls) as ListView
        aorGenerateHistory(aor)
        clAdapter = CallListAdapter(this, uaHistory)
        listView.adapter = clAdapter
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
        invalidateOptionsMenu()
    }

    override fun onPause() {

        CallHistory.save()
        super.onPause()

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.delete_history -> {
                val deleteDialog = AlertDialog.Builder(this@CallsActivity)
                deleteDialog.setMessage(String.format(getString(R.string.delete_history_alert),
                        aor.substringAfter(":")))
                deleteDialog.setPositiveButton(getText(R.string.delete)) { dialog, _ ->
                    CallHistory.clear(aor)
                    CallHistory.save()
                    aorGenerateHistory(aor)
                    clAdapter.notifyDataSetChanged()
                    dialog.dismiss()
                }
                deleteDialog.setNegativeButton(getText(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                deleteDialog.create().show()
            }

            R.id.history_on_off -> {
                account.callHistory = !account.callHistory
                invalidateOptionsMenu()
                AccountsActivity.saveAccounts()
            }

            android.R.id.home -> {
                BaresipService.activities.removeAt(0)
                val i = Intent()
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }
        }

        return true
    }

    override fun onBackPressed() {

        BaresipService.activities.removeAt(0)
        super.onBackPressed()

    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {

        if (account.callHistory)
            menu.findItem(R.id.history_on_off).setTitle(getString(R.string.disable_history))
        else
            menu.findItem(R.id.history_on_off).setTitle(getString(R.string.enable_history))
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
            BaresipService.callHistory.removeAt(index)
        uaHistory.removeAt(i)
    }

    private fun isToday(time: GregorianCalendar): Boolean {
        val now = GregorianCalendar()
        return now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
                now.get(Calendar.MONTH) == time.get(Calendar.MONTH) &&
                now.get(Calendar.DAY_OF_MONTH) == time.get(Calendar.DAY_OF_MONTH)
    }

}

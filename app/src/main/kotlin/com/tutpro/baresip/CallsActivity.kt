package com.tutpro.baresip

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ListView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.GregorianCalendar

class CallsActivity : AppCompatActivity() {

    internal var uaHistory = ArrayList<CallRow>()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calls)

        val listView = findViewById(R.id.calls) as ListView

        val aor = intent.extras.getString("aor")
        val ua = Account.findUa(aor)!!

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
                    DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_NEGATIVE -> {
                        val i = Intent(this@CallsActivity, MainActivity::class.java)
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        if (which == DialogInterface.BUTTON_NEUTRAL)
                            i.putExtra("action", "call")
                        else
                            i.putExtra("action", "message")
                        i.putExtra("uap", ua.uap)
                        i.putExtra("peer", peerUri)
                        startActivity(i)
                    }
                    DialogInterface.BUTTON_POSITIVE -> {
                    }
                }
            }
            val builder = AlertDialog.Builder(this@CallsActivity, R.style.Theme_AppCompat)
            builder.setMessage("Do you want to call or send message to '$peerName'?")
                    .setNeutralButton("Call", dialogClickListener)
                    .setNegativeButton("Send Message", dialogClickListener)
                    .setPositiveButton("Cancel", dialogClickListener)
                    .show()
        }
        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val peerUri = uaHistory[pos].peerURI
            var peerName = ContactsActivity.contactName(peerUri)
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_NEUTRAL -> {
                        val i = Intent(this, ContactActivity::class.java)
                        val b = Bundle()
                        b.putBoolean("new", true)
                        b.putString("uri", peerUri)
                        i.putExtras(b)
                        startActivityForResult(i, MainActivity.CONTACT_CODE)
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                        removeUaHistoryAt(pos)
                        if (uaHistory.size == 0) {
                            val i = Intent()
                            setResult(Activity.RESULT_CANCELED, i)
                            finish()
                        }
                        adapter.notifyDataSetChanged()
                    }
                    DialogInterface.BUTTON_POSITIVE -> {
                    }
                }
            }
            val callText: String
            if (uaHistory[pos].directions.size > 1) callText = "Calls" else callText = "Call"
            val builder = AlertDialog.Builder(this@CallsActivity, R.style.Theme_AppCompat)
            if (peerName.startsWith("sip:"))
                builder.setMessage("Do you want to add '" +
                        "${Utils.friendlyUri(peerName, Utils.aorDomain(aor))}' to contacts " +
                        "or delete ${callText.toLowerCase()} from history?")
                        .setPositiveButton("Cancel", dialogClickListener)
                        .setNegativeButton("Delete $callText", dialogClickListener)
                        .setNeutralButton("Add Contact", dialogClickListener)
                        .show()
            else
                builder.setMessage("Do you want to delete ${callText.toLowerCase()} from history?")
                        .setPositiveButton("Cancel", dialogClickListener)
                        .setNegativeButton("Delete $callText", dialogClickListener)
                        .show()
            true
        }

        ua!!.account.missedCalls = false
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
        for (i in MainActivity.history.indices.reversed()) {
            val h = MainActivity.history[i]
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
            MainActivity.history.removeAt(index)
        uaHistory.removeAt(i)
    }

    private fun isToday(time: GregorianCalendar): Boolean {
        val now = GregorianCalendar()
        return now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
                now.get(Calendar.MONTH) == time.get(Calendar.MONTH) &&
                now.get(Calendar.DAY_OF_MONTH) == time.get(Calendar.DAY_OF_MONTH)
    }

    companion object {

        fun saveHistory() {
            val file = File(MainActivity.filesPath, "history")
            try {
                val fos = FileOutputStream(file)
                val oos = ObjectOutputStream(fos)
                oos.writeObject(MainActivity.history)
                oos.close()
                fos.close()
            } catch (e: IOException) {
                Log.w("Baresip", "OutputStream exception: " + e.toString())
                e.printStackTrace()
            }
        }

        fun restoreHistory(path: String) {
            val file = File(path + "/history")
            if (file.exists()) {
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    @SuppressWarnings("unchecked")
                    MainActivity.history = ois.readObject() as ArrayList<CallHistory>
                    Log.d("Baresip", "Restored History of ${MainActivity.history.size} entries")
                    ois.close()
                    fis.close()
                } catch (e: Exception) {
                    Log.w("Baresip", "InputStream exception: - " + e.toString())
                }
            }

        }
    }
}

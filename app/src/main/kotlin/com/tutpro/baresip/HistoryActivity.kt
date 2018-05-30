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

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.GregorianCalendar

class HistoryActivity : AppCompatActivity() {

    internal var uaHistory = ArrayList<HistoryRow>()
    internal var aor: String = ""

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val listview = findViewById(R.id.history) as ListView

        val b = intent.extras
        aor = b.getString("aor")
        aorGenerateHistory(aor)

        val adapter = HistoryListAdapter(this, uaHistory)
        listview.adapter = adapter
        listview.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val i = Intent()
            i.putExtra("peer_uri", uaHistory[position].peerURI)
            setResult(Activity.RESULT_OK, i)
            finish()
        }

        listview.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        removeUaHistoryAt(pos)
                        if (uaHistory.size == 0) {
                            val i = Intent()
                            setResult(Activity.RESULT_CANCELED, i)
                            finish()
                        }
                        adapter.notifyDataSetChanged()
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
            val builder = AlertDialog.Builder(this@HistoryActivity,
                    R.style.Theme_AppCompat)
            builder.setMessage("Do you want to delete " +
                    uaHistory[pos].peerURI + " call history?")
                    .setPositiveButton("Yes", dialogClickListener)
                    .setNegativeButton("No", dialogClickListener).show()
            true
        }

        listview.isLongClickable = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Log.d("Baresip", "Back array was pressed at History")
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
                var peer_uri = h.peerURI
                if (Utils.uriHostPart(peer_uri) == Utils.uriHostPart(aor)) {
                    peer_uri = Utils.uriUserPart(peer_uri)
                }
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
                if (uaHistory.isNotEmpty() && (uaHistory.last().peerURI == peer_uri)) {
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
                    uaHistory.add(HistoryRow(peer_uri, direction, time, i))
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

}

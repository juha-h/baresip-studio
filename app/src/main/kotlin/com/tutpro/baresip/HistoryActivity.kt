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
    internal var posAtHistory = ArrayList<Int>()
    internal var aor: String = ""

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val listview = findViewById(R.id.history) as ListView

        val b = intent.extras
        aor = b.getString("aor")
        generate_ua_history(aor)

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
                        History.removeAt(posAtHistory[pos])
                        generate_ua_history(aor)
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
                    History[pos].peerURI + "?")
                    .setPositiveButton("Yes", dialogClickListener)
                    .setNegativeButton("No", dialogClickListener).show()
            true
        }

        listview.isLongClickable = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Log.d("Baresip", "Back array was pressed")
                val i = Intent()
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }
        }
        return true
    }

    private fun generate_ua_history(aor: String) {
        uaHistory.clear()
        posAtHistory.clear()
        for (i in History.indices.reversed()) {
            val h = History[i]
            if (h.aor == aor) {
                var peer_uri = h.peerURI
                if (Utils.uriHostPart(peer_uri) == Utils.uriHostPart(aor)) {
                    peer_uri = Utils.uriUserPart(peer_uri)
                }
                val time: String
                if (isToday(h.time)) {
                    val fmt = SimpleDateFormat("HH:mm")
                    time = fmt.format(h.time.time)
                } else {
                    val fmt = SimpleDateFormat("MMM dd")
                    time = fmt.format(h.time.time)
                }
                if (h.direction == "in") {
                    if (h.connected) {
                        uaHistory.add(HistoryRow(peer_uri, R.drawable.arrow_down_green, time))
                    } else {
                        uaHistory.add(HistoryRow(peer_uri, R.drawable.arrow_down_red, time))
                    }
                } else {
                    if (h.connected) {
                        uaHistory.add(HistoryRow(peer_uri, R.drawable.arrow_up_green, time))
                    } else {
                        uaHistory.add(HistoryRow(peer_uri, R.drawable.arrow_up_red, time))
                    }
                }
                posAtHistory.add(i)
            }
        }
    }

    private fun isToday(time: GregorianCalendar): Boolean {
        val now = GregorianCalendar()
        return now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
                now.get(Calendar.MONTH) == time.get(Calendar.MONTH) &&
                now.get(Calendar.DAY_OF_MONTH) == time.get(Calendar.DAY_OF_MONTH)
    }

    companion object {

        var History: ArrayList<History> = ArrayList()

        fun aorHistory(aor: String): Int {
            var size = 0;
            for (h in History) {
                if (h.aor == aor) size++
            }
            return size
        }

        fun callHasHistory(ua: String, call: String): Boolean {
            for (h in History) {
                if (h.ua == ua && h.call == call) return true
            }
            return false
        }

        fun aorRemoveHistory(aor: String) {
            for (h in History) {
                if (h.aor == aor) {
                    History.remove(h)
                    return
                }
            }
        }

    }

}

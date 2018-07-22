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
import android.widget.ImageButton
import android.widget.ListView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.*

class MessagesActivity: AppCompatActivity() {

    internal lateinit var aor: String
    internal lateinit var mlAdapter: MessageListAdapter
    internal lateinit var plusButton: ImageButton

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        val listView = findViewById(R.id.message) as ListView
        plusButton = findViewById(R.id.plusButton) as ImageButton

        aor = intent.extras.getString("aor")
        uaMessages = uaMessages(aor)

        mlAdapter = MessageListAdapter(this, uaMessages)
        listView.adapter = mlAdapter
        listView.isLongClickable = true

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val i = Intent(this@MessagesActivity, MessageActivity::class.java)
            val b = Bundle()
            b.putString("aor", aor)
            b.putString("peer", uaMessages[pos].peerURI)
            i.putExtras(b)
            startActivityForResult(i, MainActivity.MESSAGE_CODE)
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_NEGATIVE -> {
                        MainActivity.messages.remove(uaMessages[pos])
                        uaMessages.removeAt(pos)
                        if (uaMessages.size == 0) {
                            val i = Intent()
                            setResult(Activity.RESULT_CANCELED, i)
                            finish()
                        }
                        mlAdapter.notifyDataSetChanged()
                    }
                    DialogInterface.BUTTON_POSITIVE -> {
                    }
                }
            }

            val builder = AlertDialog.Builder(this@MessagesActivity,
                    R.style.Theme_AppCompat)
            builder.setMessage("Delete message from " +
                    ContactsActivity.contactName(uaMessages[pos].peerURI) + "?")
                    .setPositiveButton("Cancel", dialogClickListener)
                    .setNegativeButton("Delete", dialogClickListener)
                    .show()
            true
        }

        plusButton.setOnClickListener {
            val i = Intent(this@MessagesActivity, MessageActivity::class.java)
            val b = Bundle()
            b.putString("aor", aor)
            b.putString("peer", "")
            i.putExtras(b)
            startActivityForResult(i, MainActivity.MESSAGE_CODE)
        }

        val peer = intent.extras.getString("peer")
        if (peer != null) {
            val i = Intent(this@MessagesActivity, MessageActivity::class.java)
            val b = Bundle()
            b.putString("aor", aor)
            b.putString("peer", peer)
            i.putExtras(b)
            startActivityForResult(i, MainActivity.MESSAGE_CODE)
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Log.d("Baresip", "Back array was pressed at Messages")
                val i = Intent()
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            MainActivity.MESSAGE_CODE -> {
                if (resultCode == RESULT_OK) {
                    mlAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun uaMessages(aor: String) : ArrayList<Message> {
        val res = ArrayList<Message>()
        for (m in MainActivity.messages.reversed()) {
            if (m.aor == aor) {
                res.add(m)
            }
        }
        return res
    }

    companion object {

        var uaMessages = ArrayList<Message>()

        fun archiveUaMessage(aor: String, time: Long) {
            for (i in MainActivity.messages.indices.reversed())
                if ((MainActivity.messages[i].aor == aor) &&
                        (MainActivity.messages[i].timeStamp == time)) {
                    MainActivity.messages[i].new = false
                    return
                }
        }

        fun deleteUaMessage(aor: String, time: Long) {
            for (i in MainActivity.messages.indices.reversed())
                if ((MainActivity.messages[i].aor == aor) &&
                        (MainActivity.messages[i].timeStamp == time)) {
                    MainActivity.messages.removeAt(i)
                    return
                }
        }

        fun saveMessages() {
            val file = File(MainActivity.filesPath, "messages")
            try {
                val fos = FileOutputStream(file)
                val oos = ObjectOutputStream(fos)
                oos.writeObject(MainActivity.messages)
                oos.close()
                fos.close()
            } catch (e: IOException) {
                Log.w("Baresip", "OutputStream exception: " + e.toString())
                e.printStackTrace()
            }
        }
    }
}
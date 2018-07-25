package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
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
    internal lateinit var serviceEventReceiver: BroadcastReceiver

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
            b.putBoolean("reply", uaMessages[pos].direction == R.drawable.arrow_down_green)
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
            b.putBoolean("reply", true)
            i.putExtras(b)
            startActivityForResult(i, MainActivity.MESSAGE_CODE)
        }

        serviceEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handleMessageResponse(intent.getIntExtra("response code", 0),
                        intent.getStringExtra("time"))
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceEventReceiver,
                IntentFilter("message response"))

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Log.d("Baresip", "Back array was pressed at Messages")
                saveMessages()
                val i = Intent()
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d("Baresip", "onActivityResult at Messages")
        when (requestCode) {
            MainActivity.MESSAGE_CODE -> {
                if (resultCode == RESULT_OK) {
                    mlAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun handleMessageResponse(responseCode: Int, time: String) {
        if (responseCode < 300)
            updateMessageDirection(time.toLong(), R.drawable.arrow_up_green)
        else
            updateMessageDirection(time.toLong(), R.drawable.arrow_up_red)
        mlAdapter.notifyDataSetChanged()
    }

    fun updateMessageDirection(timeStamp: Long, direction: Int) {
        for (m in uaMessages.reversed())
            if (m.timeStamp == timeStamp) {
                m.direction = direction
                return
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

        fun addMessage(message: Message) {
            if ((MainActivity.messages.filter{it.aor == message.aor}).size >=
                    MainActivity.MESSAGE_HISTORY_SIZE)
                for (i in MainActivity.messages.indices)
                    if (MainActivity.messages[i].aor == message.aor) {
                        MainActivity.messages.removeAt(i)
                        break
                    }
            MainActivity.messages.add(message)
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
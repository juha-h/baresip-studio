package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*

class ChatActivity : AppCompatActivity() {

    internal lateinit var chatMessages: ArrayList<Message>
    internal lateinit var mlAdapter: MessageListAdapter
    internal lateinit var newMessage: EditText
    internal lateinit var sendButton: ImageButton
    internal lateinit var imm: InputMethodManager
    internal lateinit var aor: String
    internal lateinit var peerUri: String
    internal lateinit var ua: UserAgent
    internal lateinit var messageResponseReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chat)

        aor = intent.extras.getString("aor")
        peerUri = intent.extras.getString("peer")
        val focus = intent.extras.getBoolean("focus")

        val userAgent = Account.findUa(aor)
        if (userAgent == null) {
            Log.e("Baresip", "MessageActivity did not find ua of $aor")
            val i = Intent()
            setResult(Activity.RESULT_CANCELED, i)
            finish()
        } else {
            ua = userAgent
        }

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        this@ChatActivity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        var chatPeer = ContactsActivity.contactName(peerUri)
        if (chatPeer.startsWith("sip:")) {
            if (Utils.checkTelNo(Utils.uriUserPart(peerUri)))
                chatPeer = Utils.uriUserPart(peerUri)
            else
                chatPeer = chatPeer.substring(4)
        }
        setTitle("Chat with $chatPeer")

        val headerView = findViewById(R.id.account) as TextView
        val headerText = "Account ${aor.substringAfter(":")}"
        headerView.text = headerText

        val listView = findViewById(R.id.messages) as ListView
        chatMessages = uaPeerMessages(aor, peerUri)
        mlAdapter = MessageListAdapter(this, chatMessages)
        listView.adapter = mlAdapter
        listView.isLongClickable = true
        val footerView = View(applicationContext)
        listView.addFooterView(footerView)
        listView.smoothScrollToPosition(mlAdapter.count)

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
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
                        Message.messages().remove(chatMessages[pos])
                        chatMessages.removeAt(pos)
                        mlAdapter.notifyDataSetChanged()
                        if (chatMessages.size == 0) {
                            listView.removeFooterView(footerView)
                        }
                    }
                    DialogInterface.BUTTON_POSITIVE -> {
                    }
                }
            }
            val builder = AlertDialog.Builder(this@ChatActivity, R.style.Theme_AppCompat)
            if (chatPeer.contains("@") || Utils.checkTelNo(chatPeer))
                builder.setMessage("Do you want to delete message or add peer $chatPeer to contacts?")
                        .setPositiveButton("Cancel", dialogClickListener)
                        .setNegativeButton("Delete Message", dialogClickListener)
                        .setNeutralButton("Add Contact", dialogClickListener)
                        .show()
            else
                builder.setMessage("Do you want to delete message?")
                        .setPositiveButton("Cancel", dialogClickListener)
                        .setNegativeButton("Delete Message", dialogClickListener)
                        .show()
            true
        }

        newMessage = findViewById(R.id.text) as EditText
        if (focus) newMessage.requestFocus()

        sendButton = findViewById(R.id.sendButton) as ImageButton
        sendButton.setOnClickListener {
            val msgText = newMessage.text.toString()
            if (msgText.length > 0) {
                imm.hideSoftInputFromWindow(newMessage.windowToken, 0)
                val time = System.currentTimeMillis()
                val msg = Message(aor, peerUri, R.drawable.arrow_up_yellow, msgText, time,true)
                Message.add(msg)
                chatMessages.add(msg)
                mlAdapter.notifyDataSetChanged()
                if (Api.message_send(ua.uap, peerUri, msgText, time.toString()) != 0)
                    Toast.makeText(getApplicationContext(), "Sending of message failed!",
                            Toast.LENGTH_SHORT).show()
                else
                    newMessage.text.clear()
            }
        }

        messageResponseReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handleMessageResponse(intent.getIntExtra("response code", 0),
                        intent.getStringExtra("time"))
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(messageResponseReceiver,
                IntentFilter("message response"))

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.call_icon, menu)
        return true
    }

    override fun onPause() {
        ChatsActivity.saveMessages(applicationContext.filesDir.absolutePath)
        super.onPause()
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageResponseReceiver)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                for (m in chatMessages) if (m.new) m.new = false
                imm.hideSoftInputFromWindow(newMessage.windowToken, 0)
                val i = Intent()
                setResult(Activity.RESULT_OK, i)
            }
            R.id.callIcon -> {
                val intent = Intent(this, MainActivity::class.java)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.putExtra("action", "call")
                intent.putExtra("uap", ua.uap)
                intent.putExtra("peer", peerUri)
                startActivity(intent)
            }
        }
        finish()
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if ((requestCode == MainActivity.CONTACT_CODE) && (resultCode == Activity.RESULT_OK)) {
            setTitle("Chat with ${data.getStringExtra("name")}")
        }
    }

    private fun uaPeerMessages(aor: String, peerUri: String): ArrayList<Message> {
        val res = ArrayList<Message>()
        for (m in Message.messages())
            if ((m.aor == aor) && (m.peerUri == peerUri)) res.add(m)
        return res
    }

    private fun handleMessageResponse(responseCode: Int, time: String) {
        val timeStamp = time.toLong()
        for (m in chatMessages.reversed())
            if (m.timeStamp == timeStamp) {
                if (responseCode < 300)
                    m.direction = R.drawable.arrow_up_green
                else
                    m.direction = R.drawable.arrow_up_red
                mlAdapter.notifyDataSetChanged()
                return
            }
    }

}

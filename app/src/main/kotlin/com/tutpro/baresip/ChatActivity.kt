package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*

class ChatActivity : AppCompatActivity() {

    internal lateinit var chatMessages: ArrayList<Message>
    internal lateinit var mlAdapter: MessageListAdapter
    internal lateinit var listView: ListView
    internal lateinit var newMessage: EditText
    internal lateinit var sendButton: ImageButton
    internal lateinit var imm: InputMethodManager
    internal lateinit var aor: String
    internal lateinit var peerUri: String
    internal lateinit var ua: UserAgent
    internal lateinit var messageResponseReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        Log.d("Baresip", "Chat created")

        setContentView(R.layout.activity_chat)

        aor = intent.getStringExtra("aor")!!
        peerUri = intent.getStringExtra("peer")!!
        val focus = intent.getBooleanExtra("focus", false)

        val userAgent = Account.findUa(aor)
        if (userAgent == null) {
            Log.w("Baresip", "MessageActivity did not find ua of $aor")
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
            if (Utils.checkTelNo(Utils.uriUserPart(peerUri)) ||
                    (Utils.uriHostPart(peerUri) == Utils.aorDomain(aor)))
                chatPeer = Utils.uriUserPart(peerUri)
            else
                chatPeer = chatPeer.substring(4)
        }
        setTitle("${getString(R.string.chat_with)} $chatPeer")

        val headerView = findViewById(R.id.account) as TextView
        val headerText = "${getString(R.string.account)} ${aor.substringAfter(":")}"
        headerView.text = headerText

        listView = findViewById(R.id.messages) as ListView
        chatMessages = uaPeerMessages(aor, peerUri)
        mlAdapter = MessageListAdapter(this, chatMessages)
        listView.adapter = mlAdapter
        listView.isLongClickable = true
        val footerView = View(applicationContext)
        listView.addFooterView(footerView)
        listView.smoothScrollToPosition(mlAdapter.count - 1)

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
                        Message.saveMessages(applicationContext.filesDir.absolutePath)
                    }
                    DialogInterface.BUTTON_POSITIVE -> {
                    }
                }
            }
            val builder = AlertDialog.Builder(this@ChatActivity, R.style.Theme_AppCompat)
            if (ContactsActivity.contactName(peerUri) == peerUri)
                builder.setMessage(String.format(getString(R.string.long_message_question),
                        chatPeer))
                        .setPositiveButton(getString(R.string.cancel), dialogClickListener)
                        .setNegativeButton(getString(R.string.delete), dialogClickListener)
                        .setNeutralButton(getString(R.string.add_contact), dialogClickListener)
                        .show()
            else
                builder.setMessage(getText(R.string.short_message_question))
                        .setPositiveButton(getString(R.string.cancel), dialogClickListener)
                        .setNegativeButton(getString(R.string.delete), dialogClickListener)
                        .show()
            true
        }

        newMessage = findViewById(R.id.text) as EditText
        newMessage.setOnFocusChangeListener(View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            }
        })

        if (focus) newMessage.requestFocus()

        sendButton = findViewById(R.id.sendButton) as ImageButton
        sendButton.setOnClickListener {
            val msgText = newMessage.text.toString()
            if (msgText.length > 0) {
                imm.hideSoftInputFromWindow(newMessage.windowToken, 0)
                val time = System.currentTimeMillis()
                val msg = Message(aor, peerUri, msgText, time, R.drawable.arrow_up_yellow,
                        0, "", true)
                Message.add(msg)
                chatMessages.add(msg)
                if (Api.message_send(ua.uap, peerUri, msgText, time.toString()) != 0) {
                    Toast.makeText(getApplicationContext(), "${getString(R.string.message_failed)}!",
                            Toast.LENGTH_SHORT).show()
                    msg.direction = R.drawable.arrow_up_red
                    msg.responseReason = getString(R.string.message_failed)
                } else {
                    newMessage.text.clear()
                    BaresipService.chatTexts.remove("$aor::$peerUri")
                }
                mlAdapter.notifyDataSetChanged()
            }
        }

        messageResponseReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handleMessageResponse(intent.getIntExtra("response code", 0),
                        intent.getStringExtra("response reason"),
                        intent.getStringExtra("time"))
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(messageResponseReceiver,
                IntentFilter("message response"))

        ua.account.unreadMessages = false

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.call_icon, menu)
        return true
    }

    override fun onPause() {
        if (newMessage.text.toString() != "") {
            Log.d("Baresip", "Saving newMessage ${newMessage.text} for $aor::$peerUri")
            BaresipService.chatTexts.put("$aor::$peerUri", newMessage.text.toString())
        }
        super.onPause()
    }

    /*override fun onStop() {
        super.onStop()
        Log.d("Baresip", "Chat Stopped")
    }*/

    override fun onResume() {
        super.onResume()
        Log.d("Baresip", "Chat resumed")
        val chatText = BaresipService.chatTexts.get("$aor::$peerUri")
        if (chatText != null) {
            Log.d("Baresip", "Restoring newMessage ${newMessage.text} for $aor::$peerUri")
            newMessage.setText(chatText)
            newMessage.requestFocus()
            BaresipService.chatTexts.remove("$aor::$peerUri")
        }
        mlAdapter.clear()
        chatMessages = uaPeerMessages(aor, peerUri)
        mlAdapter = MessageListAdapter(this, chatMessages)
        listView.adapter = mlAdapter
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageResponseReceiver)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                var save = false
                for (m in chatMessages) {
                    if (m.new) {
                        m.new = false
                        save = true
                    }
                }
                if (save) Message.saveMessages(applicationContext.filesDir.absolutePath)
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

    private fun uaPeerMessages(aor: String, peerUri: String): ArrayList<Message> {
        val res = ArrayList<Message>()
        for (m in Message.messages())
            if ((m.aor == aor) && (m.peerUri == peerUri)) res.add(m)
        return res
    }

    private fun handleMessageResponse(responseCode: Int, responseReason: String, time: String) {
        val timeStamp = time.toLong()
        for (m in chatMessages.reversed())
            if (m.timeStamp == timeStamp) {
                if (responseCode < 300) {
                    m.direction = R.drawable.arrow_up_green
                } else {
                    m.direction = R.drawable.arrow_up_red
                    m.responseCode = responseCode
                    m.responseReason = responseReason
                }
                mlAdapter.notifyDataSetChanged()
                return
            }
    }

}

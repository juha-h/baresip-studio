package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.widget.*

import java.util.*

class ChatsActivity: AppCompatActivity() {

    internal lateinit var uaMessages: ArrayList<Message>
    internal lateinit var aor: String
    internal lateinit var listView: ListView
    internal lateinit var clAdapter: ChatListAdapter
    internal lateinit var peerUri: AutoCompleteTextView
    internal lateinit var plusButton: ImageButton

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        Log.d("Baresip", "Chats created")

        setContentView(R.layout.activity_chats)

        filesPath = applicationContext.filesDir.absolutePath

        listView = findViewById(R.id.chats) as ListView
        plusButton = findViewById(R.id.plusButton) as ImageButton

        aor = intent.extras!!.getString("aor")!!

        val headerView = findViewById(R.id.account) as TextView
        val headerText = "Account ${aor.substringAfter(":")}"
        headerView.text = headerText

        uaMessages = uaMessages(aor)
        clAdapter = ChatListAdapter(this, uaMessages)
        listView.adapter = clAdapter
        listView.isLongClickable = true

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val i = Intent(this, ChatActivity::class.java)
            val b = Bundle()
            b.putString("aor", aor)
            b.putString("peer", uaMessages[pos].peerUri)
            i.putExtras(b)
            startActivityForResult(i, MainActivity.MESSAGE_CODE)
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_NEUTRAL -> {
                        val i = Intent(this, ContactActivity::class.java)
                        val b = Bundle()
                        b.putBoolean("new", true)
                        b.putString("uri", uaMessages[pos].peerUri)
                        i.putExtras(b)
                        startActivityForResult(i, MainActivity.CONTACT_CODE)
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                        val peerUri = uaMessages[pos].peerUri
                        val msgs = ArrayList<Message>()
                        for (m in Message.messages())
                            if ((m.aor != aor) || (m.peerUri != peerUri))
                                msgs.add(m)
                            else
                                clAdapter.remove(m)
                        clAdapter.notifyDataSetChanged()
                        BaresipService.messages = msgs
                        uaMessages = uaMessages(aor)
                    }
                    DialogInterface.BUTTON_POSITIVE -> {
                    }
                }
            }

            val builder = AlertDialog.Builder(this@ChatsActivity, R.style.Theme_AppCompat)
            val peer = ContactsActivity.contactName(uaMessages[pos].peerUri)
            if (peer.startsWith("sip:"))
                builder.setMessage("Do you want to delete chat with peer " +
                        "${Utils.friendlyUri(peer, Utils.aorDomain(aor))} or add peer to contacts?")
                        .setPositiveButton("Cancel", dialogClickListener)
                        .setNegativeButton("Delete Chat", dialogClickListener)
                        .setNeutralButton("Add Contact", dialogClickListener)
                        .show()
            else
                builder.setMessage("Do you want to delete chat with '$peer'?")
                        .setPositiveButton("Cancel", dialogClickListener)
                        .setNegativeButton("Delete Chat", dialogClickListener)
                        .show()
            true
        }

        peerUri = findViewById(R.id.peerUri) as AutoCompleteTextView
        peerUri.threshold = 2
        peerUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                Contact.contacts().map{Contact -> Contact.name}))

        plusButton.setOnClickListener {
            val uriText = peerUri.text.toString().trim()
            if (uriText.length > 0) {
                var uri = ContactsActivity.findContactURI(uriText)
                if (!uri.startsWith("sip:")) {
                    uri = "sip:$uri"
                    if (!uri.contains("@")) {
                        val host = aor.substring(aor.indexOf("@") + 1)
                        uri = "$uri@$host"
                    }
                }
                if (!Utils.checkSipUri(uri)) {
                    Utils.alertView(this, "Notice", "Invalid SIP URI '$uri'")
                } else {
                    peerUri.text.clear()
                    peerUri.isCursorVisible = false
                    val i = Intent(this@ChatsActivity, ChatActivity::class.java)
                    val b = Bundle()
                    b.putString("aor", aor)
                    b.putString("peer", uri)
                    i.putExtras(b)
                    startActivityForResult(i, MainActivity.MESSAGE_CODE)
                }
            }
        }

        val peer = intent.getStringExtra("peer")
        if (peer != "") {
            val i = Intent(this, ChatActivity::class.java)
            val b = Bundle()
            b.putString("aor", aor)
            b.putString("peer", peer)
            b.putBoolean("focus", intent.getBooleanExtra("focus", false))
            i.putExtras(b)
            startActivityForResult(i, MainActivity.MESSAGE_CODE)
        }

    }

    override fun onPause() {
        Message.saveMessages(applicationContext.filesDir.absolutePath)
        super.onPause()
    }

    /*override fun onStop() {
        super.onStop()
        Log.d("Baresip", "Chats stopped")
    }*/

    override fun onResume() {
        super.onResume()
        // Log.d("Baresip", "Chats resumed")
        clAdapter.clear()
        uaMessages = uaMessages(aor)
        clAdapter = ChatListAdapter(this, uaMessages)
        listView.adapter = clAdapter
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Log.d("Baresip", "Back array was pressed at Chats")
                val i = Intent()
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if ((requestCode == MainActivity.MESSAGE_CODE) && (resultCode == Activity.RESULT_OK)) {
            clAdapter.clear()
            uaMessages = uaMessages(aor)
            clAdapter = ChatListAdapter(this, uaMessages)
            listView.adapter = clAdapter
        }
    }

    private fun uaMessages(aor: String) : ArrayList<Message> {
        val res = ArrayList<Message>()
        for (m in Message.messages().reversed()) {
            if (m.aor != aor) continue
            var found = false
            for (r in res)
                if (r.peerUri == m.peerUri) {
                    found = true
                    break
                }
            if (!found) res.add(m)
        }
        return res
    }

    companion object {

        var filesPath = ""

        fun saveUaMessage(aor: String, time: Long, path: String) {
            for (i in Message.messages().indices.reversed())
                if ((Message.messages()[i].aor == aor) &&
                        (Message.messages()[i].timeStamp == time)) {
                    Message.messages()[i].new = false
                    Message.saveMessages(path)
                    return
                }
        }

        fun deleteUaMessage(aor: String, time: Long, path: String) {
            for (i in Message.messages().indices.reversed())
                if ((Message.messages()[i].aor == aor) &&
                        (Message.messages()[i].timeStamp == time)) {
                    Message.messages().removeAt(i)
                    Message.saveMessages(path)
                    return
                }
        }

    }
}

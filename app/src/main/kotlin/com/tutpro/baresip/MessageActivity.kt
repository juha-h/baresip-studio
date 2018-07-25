package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.*

class MessageActivity : AppCompatActivity() {

    internal lateinit var receiver: AutoCompleteTextView
    internal lateinit var message: EditText
    internal lateinit var sendButton: ImageButton
    internal lateinit var imm: InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_message)

        val aor = intent.extras.getString("aor")
        val peerURI = intent.extras.getString("peer")
        val reply = intent.extras.getBoolean("reply")

        val ua = Account.findUa(aor)
        if (ua == null) {
            Log.e("Baresip", "MessageActivity did not find ua of $aor")
            val i = Intent()
            setResult(Activity.RESULT_CANCELED, i)
            finish()
        }

        val title = findViewById(R.id.messageToTitle) as TextView
        message = findViewById(R.id.text) as EditText
        receiver = findViewById(R.id.receiver) as AutoCompleteTextView

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (reply)
            title.text = "Message reply to ..."
        else
            title.text = "Message to ..."
        if (peerURI == "") {
            receiver.threshold = 2
            receiver.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                    ContactsActivity.contacts.map { Contact -> Contact.name }))
            receiver.requestFocus()
        } else {
            receiver.setText(ContactsActivity.contactName(peerURI), false)
            message.requestFocus()
        }
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)

        sendButton = findViewById(R.id.sendButton) as ImageButton
        sendButton.setOnClickListener {
            val receiverText = receiver.text.toString()
            val msg = message.text.toString()
            if (receiverText.length > 0) {
                var uri = ContactsActivity.findContactURI(receiverText)
                if (!uri.startsWith("sip:")) uri = "sip:$uri"
                if (!uri.contains("@")) {
                    val host = aor.substring(aor.indexOf("@") + 1)
                    uri = "$uri@$host"
                }
                if (msg.length > 0) {
                    imm.hideSoftInputFromWindow(receiver.getWindowToken(), 0)
                    imm.hideSoftInputFromWindow(message.getWindowToken(), 0)
                    val time = System.currentTimeMillis()
                    val res = message_send(ua!!.uap, uri, msg, time.toString())
                    if (res != 0) {
                        Toast.makeText(getApplicationContext(), "Sending of message failed!",
                                Toast.LENGTH_SHORT).show()
                    } else {
                        val new_message = Message(aor, uri, R.drawable.arrow_up_yellow, msg, time,
                                false)
                        MessagesActivity.addMessage(new_message)
                        MessagesActivity.uaMessages.add(0, new_message)
                        val i = Intent()
                        setResult(Activity.RESULT_OK, i)
                        finish()
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                imm.hideSoftInputFromWindow(receiver.getWindowToken(), 0)
                imm.hideSoftInputFromWindow(message.getWindowToken(), 0)
                val i = Intent()
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }
        }
        return true
    }

    external fun message_send(uap: String, peer_uri: String, message: String, time: String) : Int

}

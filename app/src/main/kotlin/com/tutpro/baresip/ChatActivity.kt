package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.tutpro.baresip.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatMessages: ArrayList<Message>
    private lateinit var mlAdapter: MessageListAdapter
    private lateinit var listView: ListView
    private lateinit var newMessage: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var imm: InputMethodManager
    private lateinit var aor: String
    private lateinit var peerUri: String
    private lateinit var ua: UserAgent

    private var focus = false
    private var lastCall: Long = 0

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        aor = intent.getStringExtra("aor")!!
        peerUri = intent.getStringExtra("peer")!!
        focus = intent.getBooleanExtra("focus", false)

        if (BaresipService.activities.first().startsWith("chat,$aor,$peerUri")) {
            returnResult(Activity.RESULT_CANCELED)
            return
        } else {
            Utils.addActivity("chat,$aor,$peerUri,$focus")
        }

        val userAgent = UserAgent.ofAor(aor)
        if (userAgent == null) {
            Log.w(TAG, "MessageActivity did not find ua of $aor")
            MainActivity.activityAor = aor
            returnResult(Activity.RESULT_CANCELED)
            return
        } else {
            ua = userAgent
        }

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        this@ChatActivity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        val chatPeer = Utils.friendlyUri(this, peerUri, userAgent.account, true)

        title = String.format(getString(R.string.chat_with), chatPeer)

        val headerView = binding.account
        val headerText = getString(R.string.account) + " " +
                if (ua.account.nickName != "")
                    ua.account.nickName
                else
                    aor.split(":")[1]
        headerView.text = headerText

        listView = binding.messages

        chatMessages = uaPeerMessages(aor, peerUri)
        mlAdapter = MessageListAdapter(this, peerUri, chatPeer, chatMessages)

        val messagesObserver = Observer<Long> {
            chatMessages.clear()
            chatMessages.addAll(uaPeerMessages(aor, peerUri))
            mlAdapter.notifyDataSetChanged()
        }
        BaresipService.messageUpdate.observe(this, messagesObserver)

        listView.adapter = mlAdapter
        //listView.isLongClickable = true
        val footerView = View(applicationContext)
        listView.addFooterView(footerView)
        listView.smoothScrollToPosition(mlAdapter.count - 1)

        newMessage = binding.text
        newMessage.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            }
        }

        if (focus) newMessage.requestFocus()

        sendButton = binding.sendButton
        sendButton.setOnClickListener {
            val msgText = newMessage.text.toString()
            if (msgText.isNotEmpty()) {
                imm.hideSoftInputFromWindow(newMessage.windowToken, 0)
                val time = System.currentTimeMillis()
                val msg = Message(aor, peerUri, msgText, time, MESSAGE_UP_WAIT, 0, "", true)
                msg.add()
                var msgUri = ""
                chatMessages.add(msg)
                if (Utils.isTelUri(peerUri))
                    if (ua.account.telProvider == "") {
                        Utils.alertView(this, getString(R.string.notice),
                            String.format(getString(R.string.no_telephony_provider),
                                Utils.plainAor(aor)))
                    } else {
                        msgUri = Utils.telToSip(peerUri, ua.account)
                    }
                else
                    msgUri = peerUri
                if (msgUri != "")
                    if (Api.message_send(ua.uap, msgUri, msgText, time.toString()) != 0) {
                        Toast.makeText(applicationContext, "${getString(R.string.message_failed)}!",
                                Toast.LENGTH_SHORT).show()
                        msg.direction = MESSAGE_UP_FAIL
                        msg.responseReason = getString(R.string.message_failed)
                    } else {
                        newMessage.text.clear()
                        BaresipService.chatTexts.remove("$aor::$peerUri")
                    }
                mlAdapter.notifyDataSetChanged()
            }
        }

        ua.account.unreadMessages = false

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.call_icon, menu)
        return true
    }

    override fun onResume() {
        super.onResume()
        val chatText = BaresipService.chatTexts["$aor::$peerUri"]
        if (chatText != null) {
            Log.d(TAG, "Restoring newMessage ${newMessage.text} for $aor::$peerUri")
            newMessage.setText(chatText)
            newMessage.requestFocus()
            BaresipService.chatTexts.remove("$aor::$peerUri")
        }
        chatMessages.clear()
        chatMessages.addAll(uaPeerMessages(aor, peerUri))
        mlAdapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        if (newMessage.text.toString() != "") {
            Log.d(TAG, "Saving newMessage ${newMessage.text} for $aor::$peerUri")
            BaresipService.chatTexts["$aor::$peerUri"] = newMessage.text.toString()
        }
        MainActivity.activityAor = aor
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if ((BaresipService.activities.indexOf("chat,$aor,$peerUri,false") == -1) &&
                (BaresipService.activities.indexOf("chat,$aor,$peerUri,true") == -1))
                return true

        when (item.itemId) {

            R.id.callIcon -> {
                if (SystemClock.elapsedRealtime() - lastCall > 1000) {
                    lastCall = SystemClock.elapsedRealtime()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    intent.putExtra("action", "call")
                    intent.putExtra("uap", ua.uap)
                    intent.putExtra("peer", peerUri)
                    startActivity(intent)
                    finish()
                    return true
                }
            }

            android.R.id.home -> {
                goBack()
                return true
            }

        }

        return super.onOptionsItemSelected(item)

    }

    private fun goBack() {

        var save = false
        for (m in chatMessages) {
            if (m.new) {
                m.new = false
                save = true
            }
        }
        if (save) Message.save()

        imm.hideSoftInputFromWindow(newMessage.windowToken, 0)

        BaresipService.activities.remove("chat,$aor,$peerUri,false")
        BaresipService.activities.remove("chat,$aor,$peerUri,true")
        returnResult(Activity.RESULT_OK)

    }

    private fun returnResult(code: Int) {
        setResult(code, Intent())
        finish()
    }

    private fun uaPeerMessages(aor: String, peerUri: String): ArrayList<Message> {
        val res = ArrayList<Message>()
        for (m in Message.messages())
            if ((m.aor == aor) && (m.peerUri == peerUri)) res.add(m)
        return res
    }

}

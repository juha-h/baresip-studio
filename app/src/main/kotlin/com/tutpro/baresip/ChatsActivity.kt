package com.tutpro.baresip

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tutpro.baresip.databinding.ActivityChatsBinding

class ChatsActivity: AppCompatActivity() {

    private lateinit var binding: ActivityChatsBinding
    private lateinit var uaMessages: ArrayList<Message>
    private lateinit var listView: ListView
    private lateinit var clAdapter: ChatListAdapter
    internal lateinit var peerUri: AutoCompleteTextView
    private lateinit var plusButton: ImageButton
    internal lateinit var aor: String
    internal lateinit var account: Account
    private lateinit var chatRequest: ActivityResultLauncher<Intent>
    private var scrollPosition = -1

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityChatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        listView = binding.chats
        plusButton = binding.plusButton

        aor = intent.extras!!.getString("aor")!!
        Utils.addActivity("chats,$aor")
        account = UserAgent.ofAor(aor)!!.account

        val headerView = binding.account
        val headerText = getString(R.string.account) + " " +
                if (account.nickName != "")
                    account.nickName
                else
                    aor.split(":")[1]
        headerView.text = headerText

        uaMessages = uaMessages(aor)
        clAdapter = ChatListAdapter(this, account, uaMessages)
        listView.adapter = clAdapter
        listView.isLongClickable = true

        chatRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    clAdapter.clear()
                    uaMessages = uaMessages(aor)
                    clAdapter = ChatListAdapter(this, account, uaMessages)
                    listView.adapter = clAdapter
                }
            }

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            scrollPosition = pos
            val i = Intent(this, ChatActivity::class.java)
            val b = Bundle()
            b.putString("aor", aor)
            b.putString("peer", uaMessages[pos].peerUri)
            i.putExtras(b)
            chatRequest.launch(i)
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        val i = Intent(this, ContactActivity::class.java)
                        val b = Bundle()
                        b.putBoolean("new", true)
                        b.putString("uri", uaMessages[pos].peerUri)
                        i.putExtras(b)
                        startActivity(i)
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                        val peerUri = uaMessages[pos].peerUri
                        val messages = ArrayList<Message>()
                        for (m in Message.messages())
                            if ((m.aor != aor) || (m.peerUri != peerUri))
                                messages.add(m)
                            else
                                clAdapter.remove(m)
                        clAdapter.notifyDataSetChanged()
                        BaresipService.messages = messages
                        Message.save()
                        uaMessages = uaMessages(aor)
                    }
                    DialogInterface.BUTTON_NEUTRAL -> {
                    }
                }
            }

            val builder = MaterialAlertDialogBuilder(this@ChatsActivity, R.style.AlertDialogTheme)
            val peer = Utils.friendlyUri(this@ChatsActivity, uaMessages[pos].peerUri, account)
            if (!Contact.nameExists(peer, false))
                with (builder) {
                    setTitle(R.string.confirmation)
                    setMessage(String.format(getString(R.string.long_chat_question),
                            peer))
                    setNeutralButton(getText(R.string.cancel), dialogClickListener)
                    setNegativeButton(getText(R.string.delete), dialogClickListener)
                    setPositiveButton(getText(R.string.add_contact), dialogClickListener)
                    show()
                }
            else
                with (builder) {
                    setTitle(R.string.confirmation)
                    setMessage(String.format(getString(R.string.short_chat_question), peer))
                    setNeutralButton(getText(R.string.cancel), dialogClickListener)
                    setNegativeButton(getText(R.string.delete), dialogClickListener)
                    show()
                }
            true
        }

        peerUri = binding.peer
        peerUri.threshold = 2
        peerUri.setAdapter(ArrayAdapter(this, android.R.layout.select_dialog_item,
                Contact.contactNames()))

        plusButton.setOnClickListener {
            makeChat(true)
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    }

    private fun makeChat(lookForContact: Boolean = true) {
        var uriText = peerUri.text.toString().trim()
        if (uriText.isNotEmpty()) {
            if (lookForContact) {
                val uris = Contact.contactUris(uriText)
                if (uris.size == 1)
                    uriText = uris[0]
                else if (uris.size > 1) {
                    val builder = MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                    with(builder) {
                        setTitle("Choose Destination")
                        setItems(uris.toTypedArray()) { _, which ->
                            peerUri.setText(uris[which])
                            makeChat(false)
                        }
                        setNeutralButton(getString(R.string.cancel)) { _: DialogInterface, _: Int -> }
                        show()
                    }
                    return
                }
            }
            if (Utils.isTelNumber(uriText))
                uriText = "tel:$uriText"
            val uri = if (Utils.isTelUri(uriText)) {
                if (account.telProvider == "") {
                    Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.no_telephony_provider), account.aor))
                    return
                }
                Utils.telToSip(uriText, account)
            } else {
                Utils.uriComplete(uriText, aor)
            }
            if (!Utils.checkUri(uri)) {
                Utils.alertView(this, getString(R.string.notice),
                    String.format(getString(R.string.invalid_sip_or_tel_uri), uri))
            } else {
                peerUri.text.clear()
                peerUri.isCursorVisible = false
                val i = Intent(this@ChatsActivity, ChatActivity::class.java)
                val b = Bundle()
                b.putString("aor", aor)
                b.putString("peer", uri)
                i.putExtras(b)
                chatRequest.launch(i)
            }
        }
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        clAdapter.clear()
        uaMessages = uaMessages(aor)
        clAdapter = ChatListAdapter(this, account, uaMessages)
        listView.adapter = clAdapter
        if (uaMessages.isNotEmpty()) {
            if (scrollPosition >= 0) {
                listView.setSelection(scrollPosition)
                scrollPosition = -1
            } else {
                listView.setSelection(0)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chats_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.delete_chats -> {
                with (MaterialAlertDialogBuilder(this@ChatsActivity, R.style.AlertDialogTheme)) {
                    setTitle(R.string.confirmation)
                    setMessage(String.format(getString(R.string.delete_chats_alert),
                            aor.substringAfter(":")))
                    setPositiveButton(getText(R.string.delete)) { dialog, _ ->
                        Message.clear(aor)
                        Message.save()
                        uaMessages.clear()
                        clAdapter.notifyDataSetChanged()
                        account.unreadMessages = false
                        dialog.dismiss()
                    }
                    setNeutralButton(getText(R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    show()
                }
                return true
            }

            android.R.id.home -> {
                goBack()
                return true
            }

        }

        return super.onOptionsItemSelected(item)
    }

    private fun goBack() {
        BaresipService.activities.remove("chats,$aor")
        returnResult()
    }

    private fun returnResult() {
        setResult(Activity.RESULT_CANCELED, Intent())
        finish()
    }

    private fun uaMessages(aor: String) : ArrayList<Message> {
        val res = ArrayList<Message>()
        account.unreadMessages = false
        for (m in Message.messages().reversed()) {
            if (m.aor != aor) continue
            var found = false
            for (r in res)
                if (r.peerUri == m.peerUri) {
                    found = true
                    break
                }
            if (!found) {
                res.add(m)
                if (m.new)
                    account.unreadMessages = true
            }
        }
        return res
    }

    companion object {

        fun saveUaMessage(aor: String, time: Long) {
            for (i in Message.messages().indices.reversed())
                if ((Message.messages()[i].aor == aor) &&
                        (Message.messages()[i].timeStamp == time)) {
                    Message.messages()[i].new = false
                    Message.save()
                    return
                }
        }

        fun deleteUaMessage(aor: String, time: Long) {
            for (i in Message.messages().indices.reversed())
                if ((Message.messages()[i].aor == aor) &&
                        (Message.messages()[i].timeStamp == time)) {
                    Message.messages().removeAt(i)
                    Message.save()
                    return
                }
        }

    }
}

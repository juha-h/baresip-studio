package com.tutpro.baresip

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tutpro.baresip.databinding.ActivityCallsBinding

class CallsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallsBinding
    private lateinit var account: Account
    private lateinit var clAdapter: CallListAdapter

    private var aor = ""
    private var lastClick: Long = 0

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityCallsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aor = intent.getStringExtra("aor")!!
        Utils.addActivity("calls,$aor")

        val ua = UserAgent.ofAor(aor)!!
        account = ua.account

        val headerView = binding.account
        val headerText = getString(R.string.account) + " " +
                if (account.nickName != "")
                    account.nickName
                else
                    aor.split(":")[1]
        headerView.text = headerText

        val listView = binding.calls
        aorGenerateHistory(aor)
        clAdapter = CallListAdapter(this, account, uaHistory)
        listView.adapter = clAdapter
        listView.isLongClickable = true

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val peerUri = uaHistory[pos].peerUri
            val peerName = Utils.friendlyUri(this, peerUri, account)
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE -> {
                        BaresipService.activities.remove("calls,$aor")
                        MainActivity.activityAor = aor
                        returnResult()
                        val i = Intent(this@CallsActivity, MainActivity::class.java)
                        i.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        if (which == DialogInterface.BUTTON_NEGATIVE)
                            i.putExtra("action", "call")
                        else
                            i.putExtra("action", "message")
                        i.putExtra("uap", ua.uap)
                        i.putExtra("peer", peerUri)
                        startActivity(i)
                    }

                    DialogInterface.BUTTON_NEUTRAL -> {
                    }
                }
            }
            if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                lastClick = SystemClock.elapsedRealtime()
                with(MaterialAlertDialogBuilder(this@CallsActivity, R.style.AlertDialogTheme)) {
                    setTitle(R.string.confirmation)
                    setMessage(
                        String.format(
                            getString(R.string.calls_call_message_question),
                            peerName
                        )
                    )
                    setNeutralButton(getString(R.string.cancel), dialogClickListener)
                    setNegativeButton(getString(R.string.call), dialogClickListener)
                    setPositiveButton(getString(R.string.send_message), dialogClickListener)
                    show()
                }
            }
        }

        val contactRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    clAdapter.notifyDataSetChanged()
                }
            }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val peerUri = uaHistory[pos].peerUri
            val peerName = Utils.friendlyUri(this, peerUri, account)
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_NEGATIVE -> {
                        val i = Intent(this, ContactActivity::class.java)
                        val b = Bundle()
                        b.putBoolean("new", true)
                        b.putString("uri", peerUri)
                        i.putExtras(b)
                        contactRequest.launch(i)
                    }

                    DialogInterface.BUTTON_POSITIVE -> {
                        removeUaHistoryAt(pos)
                        CallHistory.save()
                        clAdapter.notifyDataSetChanged()
                    }

                    DialogInterface.BUTTON_NEUTRAL -> {
                    }
                }
            }
            val callText: String = if (uaHistory[pos].details.size > 1)
                getString(R.string.calls_calls)
            else
                getString(R.string.calls_call)
            val builder = MaterialAlertDialogBuilder(this@CallsActivity, R.style.AlertDialogTheme)
            if (!Contact.nameExists(peerName, false))
                with(builder) {
                    setTitle(R.string.confirmation)
                    setMessage(
                        String.format(
                            getString(R.string.calls_add_delete_question),
                            peerName, callText
                        )
                    )
                    setNeutralButton(getString(R.string.cancel), dialogClickListener)
                    setPositiveButton(
                        String.format(getString(R.string.delete), callText),
                        dialogClickListener
                    )
                    setNegativeButton(getString(R.string.add_contact), dialogClickListener)
                    show()
                }
            else
                with(builder) {
                    setTitle(R.string.confirmation)
                    setMessage(
                        String.format(
                            getString(R.string.calls_delete_question),
                            peerName, callText
                        )
                    )
                    setNeutralButton(getString(R.string.cancel), dialogClickListener)
                    setPositiveButton(
                        String.format(getString(R.string.delete), callText),
                        dialogClickListener
                    )
                    show()
                }
            true
        }

        ua.account.missedCalls = false
        invalidateOptionsMenu()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("calls,$aor") == -1)
            return true

        when (item.itemId) {

            R.id.delete_history -> {
                with (MaterialAlertDialogBuilder(this@CallsActivity, R.style.AlertDialogTheme)) {
                    setTitle(R.string.confirmation)
                    setMessage(String.format(getString(R.string.delete_history_alert),
                            aor.substringAfter(":")))
                    setPositiveButton(getText(R.string.delete)) { dialog, _ ->
                        CallHistory.clear(aor)
                        CallHistory.save()
                        aorGenerateHistory(aor)
                        clAdapter.notifyDataSetChanged()
                        dialog.dismiss()
                    }
                    setNeutralButton(getText(R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    show()
                }
                return true
            }

            R.id.history_on_off -> {
                account.callHistory = !account.callHistory
                invalidateOptionsMenu()
                AccountsActivity.saveAccounts()
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
        BaresipService.activities.remove("calls,$aor")
        returnResult()
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
    }

    private fun returnResult() {
        setResult(Activity.RESULT_CANCELED, Intent())
        finish()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {

        if (account.callHistory)
            menu.findItem(R.id.history_on_off).title = getString(R.string.disable_history)
        else
            menu.findItem(R.id.history_on_off).title = getString(R.string.enable_history)
        return super.onPrepareOptionsMenu(menu)

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.calls_menu, menu)
        return true
    }

    private fun aorGenerateHistory(aor: String) {
        uaHistory.clear()
        for (i in BaresipService.callHistory.indices.reversed()) {
            val h = BaresipService.callHistory[i]
            if (h.aor == aor) {
                val direction: Int = if (h.direction == "in")
                    if (h.startTime != null)
                        R.drawable.call_down_green
                    else
                        R.drawable.call_down_red
                else
                    if (h.startTime != null)
                        R.drawable.call_up_green
                    else
                        R.drawable.call_up_red
                if (uaHistory.isNotEmpty() && (uaHistory.last().peerUri == h.peerUri))
                    uaHistory.last().details.add(CallRow.Details(direction, h.startTime,
                        h.stopTime, h.recording))
                else
                    uaHistory.add(CallRow(h.aor, h.peerUri, direction, h.startTime,
                        h.stopTime, h.recording))
            }
        }
    }

    private fun removeUaHistoryAt(i: Int) {
        for (details in uaHistory[i].details) {
            if (details.recording[0] != "")
                CallHistory.deleteRecording(details.recording)
            BaresipService.callHistory.removeAll {
                it.startTime == details.startTime && it.stopTime == details.stopTime
            }
        }
        CallHistory.deleteRecording(uaHistory[i].recording)
        uaHistory.removeAt(i)
    }

    companion object {
        var uaHistory = ArrayList<CallRow>()
    }

}

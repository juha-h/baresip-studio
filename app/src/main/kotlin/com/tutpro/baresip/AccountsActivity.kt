package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.widget.*
import java.io.File

import java.util.ArrayList

class AccountsActivity : AppCompatActivity() {

    internal var aor: String = ""

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

        val listview = findViewById(R.id.accounts) as ListView
        generateAccounts()
        val alAdapter = AccountListAdapter(this, accounts)
        listview.adapter = alAdapter

        val addAccountButton = findViewById(R.id.addAccount) as ImageButton
        val newAorView = findViewById(R.id.newAor) as EditText
        addAccountButton.setOnClickListener{
            var aor = newAorView.text.toString()
            if (!aor.startsWith("sip:")) aor = "sip:$aor"
            if (!checkSipUri(aor)) {
                Log.e("Baresip", "Invalid SIP URI $aor")
                Utils.alertView(this, "Notice",
                        "Invalid SIP URI: $aor")
            } else if (Account.exists(MainActivity.uas, aor)) {
                Log.e("Baresip", "Account $aor already exists")
            } else {
                if (UserAgent.ua_alloc("<$aor>;regq=0.5;pubint=0;regint=600") != 0) {
                    Log.e("Baresip", "Failed to allocate UA $aor")
                    Utils.alertView(this, "Notice",
                            "Failed to allocate new account. Check your network connection.")
                } else {
                    SystemClock.sleep(200);
                    newAorView.setText("")
                    newAorView.setHint("SIP URI user@domain")
                    newAorView.clearFocus()
                    saveAccounts()
                    recreate()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Log.d("Baresip", "Back array was pressed at Accounts")
                val i = Intent()
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }
        }
        return true
    }

    private fun checkSipUri(uri: String): Boolean {
        if (!uri.startsWith("sip:")) return false
        val userDomain = uri.replace("sip:", "").split("@")
        if (userDomain.size != 2) return false
        if (!Utils.checkUserID(userDomain[0]) && !Utils.checkTelNo(userDomain[0]))
            return false
        return Utils.checkDomain(userDomain[1]) || Utils.checkIP(userDomain[1])
    }

    companion object {

        var accounts = ArrayList<AccountRow>()
        var posAtAccounts = ArrayList<Int>()

        fun generateAccounts() {
            accounts.clear()
            posAtAccounts.clear()
            var i = 0;
            for (ua in MainActivity.uas) {
                accounts.add(AccountRow(ua.aor.replace("sip:", ""), R.drawable.action_remove))
                posAtAccounts.add(i)
                i++
            }
        }

        fun saveAccounts() {
            var accounts = ""
            for (a in Account.accounts()) accounts = accounts + a.print() + "\n"
            val path = MainActivity.filesPath + "/accounts"
            Utils.putFileContents(File(path), accounts)
//            Log.d("Baresip", "Saved accounts '${accounts}' to '$path")
        }
    }

}

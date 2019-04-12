package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.widget.*

import java.io.File
import java.util.ArrayList

class AccountsActivity : AppCompatActivity() {

    internal lateinit var alAdapter: AccountListAdapter

    internal var aor = ""

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

        filesPath = applicationContext.filesDir.absolutePath

        val listView = findViewById(R.id.accounts) as ListView
        generateAccounts()
        alAdapter = AccountListAdapter(this, accounts)
        listView.adapter = alAdapter

        val addAccountButton = findViewById(R.id.addAccount) as ImageButton
        val newAorView = findViewById(R.id.newAor) as EditText
        addAccountButton.setOnClickListener{
            var aor = newAorView.text.toString().trim()
            if (!aor.startsWith("sip:")) aor = "sip:$aor"
            if (!Utils.checkAorUri(aor)) {
                Log.d("Baresip", "Invalid SIP Address of Record $aor")
                Utils.alertView(this, "Notice",
                        "Invalid SIP Address of Record: $aor")
            } else if (Account.exists(aor)) {
                Log.d("Baresip", "Account $aor already exists")
                Utils.alertView(this, "Notice",
                        "Account $aor already exists")
            } else {
                val ua = UserAgent.uaAlloc("<$aor>;stunserver=\"stun:stun.l.google.com:19302\";regq=0.5;pubint=0;regint=0")
                if (ua == null) {
                    Log.e("Baresip", "Failed to allocate UA for $aor")
                    Utils.alertView(this, "Notice",
                            "Failed to allocate new account.")
                } else {
                    newAorView.setText("")
                    newAorView.hint = "SIP URI user@domain"
                    newAorView.clearFocus()
                    UserAgent.add(ua, R.drawable.dot_yellow)
                    generateAccounts()
                    alAdapter.notifyDataSetChanged()
                    saveAccounts()
                    val i = Intent(this, AccountActivity::class.java)
                    val b = Bundle()
                    b.putString("accp", ua.account.accp)
                    i.putExtras(b)
                    startActivity(i)
                }
            }
        }

        val accp = intent.getStringExtra("accp")
        if (accp != "") {
            val i = Intent(this, AccountActivity::class.java)
            val b = Bundle()
            b.putString("accp", accp)
            i.putExtras(b)
            startActivity(i)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Log.d("Baresip", "Back array was pressed at Accounts")
                val i = Intent()
                setResult(Activity.RESULT_OK, i)
                finish()
            }
        }
        return true
    }

    companion object {

        internal var filesPath = ""

        var accounts = ArrayList<AccountRow>()

        fun generateAccounts() {
            accounts.clear()
            for (ua in UserAgent.uas())
                accounts.add(AccountRow(ua.account.aor.replace("sip:", ""),
                        R.drawable.action_remove))
        }

        fun saveAccounts() {
            var accounts = ""
            for (a in Account.accounts()) accounts = accounts + a.print() + "\n"
            Utils.putFileContents(File(filesPath + "/accounts"), accounts)
            // Log.d("Baresip", "Saved accounts '${accounts}' to '${filesPath}/accounts'")
        }
    }

}

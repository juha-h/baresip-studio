package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.*

import java.util.ArrayList

class AccountsActivity : AppCompatActivity() {

    internal lateinit var alAdapter: AccountListAdapter

    internal var aor = ""

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

        Utils.addActivity("accounts")

        val listView = findViewById(R.id.accounts) as ListView
        generateAccounts()
        alAdapter = AccountListAdapter(this, accounts)
        listView.adapter = alAdapter

        val addAccountButton = findViewById(R.id.addAccount) as ImageButton
        val newAorView = findViewById(R.id.newAor) as EditText
        addAccountButton.setOnClickListener {
            val aor = newAorView.text.toString().trim()
            if (!Utils.checkAor(aor)) {
                Log.d("Baresip", "Invalid Address of Record $aor")
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.invalid_aor), aor))
            } else if (Account.exists(aor)) {
                Log.d("Baresip", "Account $aor already exists")
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.account_exists), aor.split(":")[0]))
            } else {
                val ua = UserAgent.uaAlloc("<sip:$aor>;stunserver=\"stun:stun.l.google.com:19302\";regq=0.5;pubint=0;regint=0")
                if (ua == null) {
                    Log.e("Baresip", "Failed to allocate UA for $aor")
                    Utils.alertView(this, getString(R.string.notice),
                            getString(R.string.account_allocation_failure))
                } else {
                    Log.d("Baresip", "Allocated UA ${ua.uap} for ${ua.account.uri}")
                    newAorView.setText("")
                    newAorView.hint = getString(R.string.user_domain)
                    newAorView.clearFocus()
                    UserAgent.add(ua, R.drawable.dot_white)
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

    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.help -> {
                Utils.alertView(this@AccountsActivity, getString(R.string.new_account),
                        getString(R.string.accounts_help))
            }

            android.R.id.home -> {
                Log.d("Baresip", "Back array was pressed at Accounts")
                BaresipService.activities.removeAt(0)
                val i = Intent()
                setResult(RESULT_OK, i)
                finish()
            }
        }

        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menuInflater.inflate(R.menu.accounts_menu, menu)
        return true

    }

    override fun onBackPressed() {

        BaresipService.activities.removeAt(0)
        val i = Intent()
        setResult(Activity.RESULT_OK, i)
        finish()
        super.onBackPressed()

    }

    companion object {

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
            Utils.putFileContents(BaresipService.filesPath + "/accounts", accounts.toByteArray())
            // Log.d("Baresip", "Saved accounts '${accounts}' to '${BaresipService.filesPath}/accounts'")
        }

        fun noAccounts(): Boolean {
            val contents = Utils.getFileContents(BaresipService.filesPath + "/accounts")
            return contents == null || contents.size == 0
        }

    }

}

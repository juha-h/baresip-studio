package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.*

import com.tutpro.baresip.Account.Companion.checkAuthPass
import com.tutpro.baresip.MainActivity.Companion.ACCOUNT_CODE
import com.tutpro.baresip.MainActivity.Companion.aorPasswords

import java.util.ArrayList

class AccountsActivity : AppCompatActivity() {

    internal lateinit var alAdapter: AccountListAdapter

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
                val laddr = "sip:$aor"
                val ua = UserAgent.uaAlloc("<$laddr>;stunserver=\"stun:stun.l.google.com:19302\";regq=0.5;pubint=0;regint=0;mwi=no")
                if (ua == null) {
                    Log.e("Baresip", "Failed to allocate UA for $aor")
                    Utils.alertView(this, getString(R.string.notice),
                            getString(R.string.account_allocation_failure))
                } else {
                    // Api.account_debug(ua.account.accp)
                    Log.d("Baresip", "Allocated UA ${ua.uap} for ${account_luri(ua.account.accp)}")
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
                    startActivityForResult(i, ACCOUNT_CODE)
                }
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ACCOUNT_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val aor = Account.find(data!!.getStringExtra("accp"))!!.aor
                    val ua = UserAgent.uas()[UserAgent.findAorIndex(aor)!!]
                    if (aorPasswords.containsKey(aor) && aorPasswords[aor] == "")
                        askPassword(String.format(getString(R.string.account_password),
                                Utils.plainAor(aor)), ua)
                }
            }
        }
    }

    private fun askPassword(title: String, ua: UserAgent) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        val viewInflated = LayoutInflater.from(this)
                .inflate(R.layout.password_dialog, findViewById(android.R.id.content) as ViewGroup,
                        false)
        val input = viewInflated.findViewById(R.id.password) as EditText
        val checkBox = viewInflated.findViewById(R.id.checkbox) as CheckBox
        checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked)
                input.transformationMethod = HideReturnsTransformationMethod()
            else
                input.transformationMethod = PasswordTransformationMethod()
        }
        builder.setView(viewInflated)
        builder.setPositiveButton(android.R.string.ok) { dialog, _ ->
            dialog.dismiss()
            var password = input.text.toString().trim()
            if (!checkAuthPass(password)) {
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.invalid_authentication_password), password))
                password = ""
            }
            setAuthPass(ua, password)
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("accounts") == -1)
            return true

        when (item.itemId) {
            R.id.help -> {
                Utils.alertView(this@AccountsActivity, getString(R.string.new_account),
                        getString(R.string.accounts_help))
            }
            android.R.id.home -> {
                BaresipService.activities.remove("accounts")
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
        BaresipService.activities.remove("accounts")
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
            var count = 0
            for (a in Account.accounts()) {
                accounts = accounts + a.print() + "\n"
                count++
            }
            Utils.putFileContents(BaresipService.filesPath + "/accounts", accounts.toByteArray())
            Log.d("Baresip", "Saved $count account(s) to '${BaresipService.filesPath}/accounts'")
            // Log.d("Baresip", "Saved accounts '${accounts}' to '${BaresipService.filesPath}/accounts'")
        }

        fun noAccounts(): Boolean {
            val contents = Utils.getFileContents(BaresipService.filesPath + "/accounts")
            return contents == null || contents.size == 0
        }

        fun setAuthPass(ua: UserAgent, ap: String) {
            val acc = ua.account
            if (account_set_auth_pass(acc.accp, ap) == 0) {
                acc.authPass = account_auth_pass(acc.accp)
                aorPasswords[acc.aor] = ap
                if ((ap != "") && (acc.regint > 0))
                    Api.ua_register(ua.uap)
            } else {
                Log.e("Baresip", "Setting of auth pass failed")
            }
        }
    }

}

package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import com.tutpro.baresip.databinding.ActivityAccountsBinding

import java.util.ArrayList

class AccountsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountsBinding
    private lateinit var alAdapter: AccountListAdapter
    internal lateinit var aor: String

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aor = intent.getStringExtra("aor")!!
        Utils.addActivity("accounts,$aor")

        val listView = binding.accounts
        generateAccounts()
        alAdapter = AccountListAdapter(this, accounts)
        listView.adapter = alAdapter

        val addAccountButton = binding.addAccount
        val newAorView = binding.newAor
        addAccountButton.setOnClickListener {
            val aor = newAorView.text.toString().trim()
            if (!Utils.checkAor(aor)) {
                Log.d("Baresip", "Invalid Address of Record $aor")
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.invalid_aor), aor))
                return@setOnClickListener
            }
            if (Account.ofAor("sip:$aor") != null) {
                Log.d("Baresip", "Account $aor already exists")
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.account_exists), aor.split(":")[0]))
                return@setOnClickListener
            }
            val laddr = "sip:$aor"
            val ua = UserAgent.uaAlloc("<$laddr>;stunserver=\"stun:stun.l.google.com:19302\";regq=0.5;pubint=0;regint=0;mwi=no")
            if (ua == null) {
                Log.e("Baresip", "Failed to allocate UA for $aor")
                Utils.alertView(this, getString(R.string.notice),
                        getString(R.string.account_allocation_failure))
                return@setOnClickListener
            }
            // Api.account_debug(ua.account.accp)
            Log.d("Baresip", "Allocated UA ${ua.uap} for ${Api.account_luri(ua.account.accp)}")
            newAorView.setText("")
            newAorView.hint = getString(R.string.user_domain)
            newAorView.clearFocus()
            ua.add(R.drawable.dot_white)
            generateAccounts()
            alAdapter.notifyDataSetChanged()
            saveAccounts()
            val i = Intent(this, AccountActivity::class.java)
            val b = Bundle()
            b.putString("aor", ua.account.aor)
            b.putBoolean("new", true)
            i.putExtras(b)
            startActivityForResult(i, MainActivity.ACCOUNT_CODE)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            MainActivity.ACCOUNT_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val aor = data!!.getStringExtra("aor")!!
                    val ua = UserAgent.ofAor(aor)!!
                    if (MainActivity.aorPasswords.containsKey(aor) &&
                            MainActivity.aorPasswords[aor] == "")
                        askPassword(ua)
                }
            }
        }

    }

    private fun askPassword(ua: UserAgent) {
        val layout = LayoutInflater.from(this)
                .inflate(R.layout.password_dialog, findViewById(android.R.id.content),
                        false)
        val titleView = layout.findViewById(R.id.title) as TextView
        titleView.text = getString(R.string.authentication_password)
        val messageView = layout.findViewById(R.id.message) as TextView
        val message = getString(R.string.account) + " " + Utils.plainAor(ua.account.aor)
        messageView.text = message
        val input = layout.findViewById(R.id.password) as EditText
        val checkBox = layout.findViewById(R.id.checkbox) as CheckBox
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked)
                input.transformationMethod = HideReturnsTransformationMethod()
            else
                input.transformationMethod = PasswordTransformationMethod()
        }
        with (AlertDialog.Builder(this)) {
            setView(layout)
            setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                var password = input.text.toString().trim()
                if (!Account.checkAuthPass(password)) {
                    Utils.alertView(this@AccountsActivity, getString(R.string.notice),
                            String.format(getString(R.string.invalid_authentication_password), password))
                    password = ""
                }
                setAuthPass(ua, password)
            }
            setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("accounts,$aor") == -1)
            return true

        when (item.itemId) {

            R.id.help -> {
                Utils.alertView(this@AccountsActivity, getString(R.string.new_account),
                        getString(R.string.accounts_help))
                return true
            }

            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }

        return super.onOptionsItemSelected(item)

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.accounts_menu, menu)
        return true
    }

    override fun onBackPressed() {
        BaresipService.activities.remove("accounts,$aor")
        val i = Intent()
        setResult(Activity.RESULT_CANCELED, i)
        finish()
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
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
            // Log.d("Baresip", "Saved accounts '${accounts}' to '${BaresipService.filesPath}/accounts'")
        }

        fun noAccounts(): Boolean {
            val contents = Utils.getFileContents(BaresipService.filesPath + "/accounts")
            return contents == null || contents.size == 0
        }

        fun setAuthPass(ua: UserAgent, ap: String) {
            val acc = ua.account
            if (Api.account_set_auth_pass(acc.accp, ap) == 0) {
                acc.authPass = Api.account_auth_pass(acc.accp)
                MainActivity.aorPasswords[acc.aor] = ap
                if ((ap != "") && (acc.regint > 0))
                    Api.ua_register(ua.uap)
            } else {
                Log.e("Baresip", "Setting of auth pass failed")
            }
        }
    }

}

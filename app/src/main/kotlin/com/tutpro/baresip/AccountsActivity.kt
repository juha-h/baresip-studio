package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import android.app.AlertDialog
import android.view.ViewGroup
import android.view.LayoutInflater

import java.io.File
import java.util.ArrayList

class AccountsActivity : AppCompatActivity() {

    internal lateinit var alAdapter: AccountListAdapter
    internal var aor = ""
    internal var password = ""

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
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.invalid_aor), aor))
            } else if (Account.exists(aor)) {
                Log.d("Baresip", "Account $aor already exists")
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.account_exists), aor))
            } else {
                val ua = UserAgent.uaAlloc("<$aor>;stunserver=\"stun:stun.l.google.com:19302\";regq=0.5;pubint=0;regint=0")
                if (ua == null) {
                    Log.e("Baresip", "Failed to allocate UA for $aor")
                    Utils.alertView(this, getString(R.string.notice),
                            getString(R.string.account_allocation_failure))
                } else {
                    newAorView.setText("")
                    newAorView.hint = getString(R.string.sip_uri_user_domain)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.accounts_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.export_accounts -> {
                if (Utils.requestPermission(this,
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    askPassword(getString(R.string.encrypt_password))
            }
            R.id.import_accounts -> {
                if (Utils.requestPermission(this,
                                android.Manifest.permission.READ_EXTERNAL_STORAGE))
                    askPassword(getString(R.string.decrypt_password))

            }
            android.R.id.home -> {
                Log.d("Baresip", "Back array was pressed at Accounts")
                val i = Intent()
                setResult(Activity.RESULT_OK, i)
                finish()
            }
        }
        return true
    }

    private fun askPassword(title: String) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        val viewInflated = LayoutInflater.from(this)
                .inflate(R.layout.password_dialog, findViewById(android.R.id.content) as ViewGroup,
                        false)
        val input = viewInflated.findViewById(R.id.password) as EditText
        builder.setView(viewInflated)
        builder.setPositiveButton(android.R.string.ok) { dialog, _ ->
            dialog.dismiss()
            password = input.text.toString()
            if (password != "") {
                if (title == getString(R.string.encrypt_password)) {
                    if (exportAccounts(dir, password))
                        Utils.alertView(this, getString(R.string.info),
                                getString(R.string.exported_accounts))
                    else
                        Utils.alertView(this, getString(R.string.error),
                                getString(R.string.export_accounts_error))
                } else {
                    if (importAccounts(dir, password))
                        Utils.alertView(this, getString(R.string.info),
                                getString(R.string.imported_accounts))
                    else
                        Utils.alertView(this, getString(R.string.error),
                                getString(R.string.import_accounts_error))
                }
            }
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
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

        fun exportAccounts(path: File, password: String): Boolean {
            var accounts = ""
            for (a in Account.accounts())
                accounts = accounts + a.print() + "\n"
            return Utils.putFileContents(File(path, "accounts.bs"),
                    Utils.encrypt(accounts, password))
        }

        fun importAccounts(path: File, password: String): Boolean {
            val content = Utils.getFileContents(File(path, "accounts.bs"))
            if (content == "Failed") return false
            val accounts = Utils.decrypt(content, password)
            if (accounts == "") return false
            return Utils.putFileContents(File(filesPath + "/accounts"), accounts)
        }
    }

}

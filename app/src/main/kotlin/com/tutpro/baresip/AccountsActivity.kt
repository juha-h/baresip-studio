package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.tutpro.baresip.databinding.ActivityAccountsBinding

class AccountsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountsBinding
    private lateinit var alAdapter: AccountListAdapter
    internal lateinit var aor: String

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

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

        val accountRequest =
           registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

        val addAccountButton = binding.addAccount
        val newAorView = binding.newAor
        addAccountButton.setOnClickListener {
            val aor = newAorView.text.toString().trim()
            if (!Utils.checkAor("sip:$aor")) {
                Log.d(TAG, "Invalid Address of Record $aor")
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.invalid_aor), aor))
                return@setOnClickListener
            }
            if (Account.ofAor("sip:$aor") != null) {
                Log.d(TAG, "Account $aor already exists")
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.account_exists), aor.split(":")[0]))
                return@setOnClickListener
            }
            val laddr = "sip:$aor"
            val ua = UserAgent.uaAlloc("<$laddr>;stunserver=\"stun:stun.l.google.com:19302\";regq=0.5;pubint=0;regint=0;mwi=no")
            if (ua == null) {
                Log.e(TAG, "Failed to allocate UA for $aor")
                Utils.alertView(this, getString(R.string.notice),
                        getString(R.string.account_allocation_failure))
                return@setOnClickListener
            }
            // Api.account_debug(ua.account.accp)
            Log.d(TAG, "Allocated UA ${ua.uap} for ${Api.account_luri(ua.account.accp)}")
            newAorView.setText("")
            newAorView.hint = getString(R.string.user_domain)
            newAorView.clearFocus()
            generateAccounts()
            alAdapter.notifyDataSetChanged()
            saveAccounts()
            val i = Intent(this, AccountActivity::class.java)
            val b = Bundle()
            b.putString("aor", ua.account.aor)
            b.putBoolean("new", true)
            i.putExtras(b)
            accountRequest.launch(i)
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

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
                goBack()
                return true
            }
        }

        return super.onOptionsItemSelected(item)

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.accounts_menu, menu)
        return true
    }

    private fun goBack() {
        BaresipService.activities.remove("accounts,$aor")
        setResult(Activity.RESULT_CANCELED, Intent())
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
            for (ua in BaresipService.uas)
                accounts.add(AccountRow(ua.account.aor.replace("sip:", ""),
                        R.drawable.delete))
        }

        fun saveAccounts() {
            var accounts = ""
            for (a in Account.accounts()) accounts = accounts + a.print() + "\n"
            Utils.putFileContents(BaresipService.filesPath + "/accounts", accounts.toByteArray(Charsets.UTF_8))
            // Log.d(TAG, "Saved accounts '${accounts}' to '${BaresipService.filesPath}/accounts'")
        }

        fun noAccounts(): Boolean {
            val contents = Utils.getFileContents(BaresipService.filesPath + "/accounts")
            return contents == null || contents.isEmpty()
        }

    }

}

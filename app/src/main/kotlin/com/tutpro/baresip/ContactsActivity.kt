package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import com.tutpro.baresip.databinding.ActivityContactsBinding

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var clAdapter: ContactListAdapter
    private lateinit var aor: String
    private var lastClick: Long = 0

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aor = intent.getStringExtra("aor")!!
        Utils.addActivity("contacts,$aor")

        val listView = binding.contacts
        clAdapter = ContactListAdapter(this, Contact.contacts(), aor)
        listView.adapter = clAdapter
        listView.isLongClickable = true

        val contactRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK)
                    clAdapter.notifyDataSetChanged()
            }

        val plusButton = binding.plusButton
        plusButton.setOnClickListener {
            if (Contact.contacts().size >= Contact.CONTACTS_SIZE) {
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.contacts_exceeded),
                                Contact.CONTACTS_SIZE))
            } else {
                if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                    lastClick = SystemClock.elapsedRealtime()
                    val intent = Intent(this, ContactActivity::class.java)
                    val b = Bundle()
                    b.putBoolean("new", true)
                    b.putString("uri", "")
                    intent.putExtras(b)
                    contactRequest.launch(intent)
                }
            }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.swap_contacts_icon, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onResume() {

        super.onResume()

        clAdapter.notifyDataSetChanged()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.swapIcon -> {
                if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                    lastClick = SystemClock.elapsedRealtime()
                    BaresipService.activities.remove("contacts,$aor")
                    val intent = Intent(this, AndroidContactsActivity::class.java)
                    intent.putExtra("aor", aor)
                    startActivity(intent)
                    finish()
                    return true
                }
            }

            android.R.id.home -> {
                BaresipService.activities.remove("contacts,$aor")
                setResult(Activity.RESULT_OK, Intent())
                finish()
            }
        }

        return true

    }

    override fun onBackPressed() {

        BaresipService.activities.remove("contacts,$aor")
        setResult(Activity.RESULT_OK, Intent())
        finish()
        
    }

    companion object {

        fun findContactUri(name: String): String? {
            for (c in Contact.contacts())
                if (c.name.equals(name, ignoreCase = true))
                    return c.uri.removePrefix("<")
                            .replaceAfter(">", "")
                            .replace(">", "")
            return null
        }

        fun findContact(uri: String): Contact? {
            for (c in Contact.contacts()) {
                if (Utils.uriMatch(c.uri, uri))
                    return c
            }
            return null
        }

        fun nameExists(name: String, ignoreCase: Boolean): Boolean {
            for (c in Contact.contacts())
                if (c.name.equals(name, ignoreCase = ignoreCase)) return true
            return false
        }

        // Return contact name of uri or uri itself if contact with uri is not found
        fun contactName(uri: String): String {
            val userPart = Utils.uriUserPart(uri)
            return if (Utils.isTelNumber(userPart))
                findContact("tel:$userPart")?.name ?: uri
            else
                findContact(uri)?.name ?: uri
        }

    }
}

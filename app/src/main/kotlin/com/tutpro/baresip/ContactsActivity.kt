package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
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

        val plusButton = binding.plusButton
        plusButton.setOnClickListener {
            if (Contact.contacts().size >= Contact.CONTACTS_SIZE) {
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.contacts_exceeded),
                                Contact.CONTACTS_SIZE))
            } else {
                if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                    lastClick = SystemClock.elapsedRealtime()
                    val i = Intent(this, ContactActivity::class.java)
                    val b = Bundle()
                    b.putBoolean("new", true)
                    b.putString("uri", "")
                    i.putExtras(b)
                    startActivityForResult(i, MainActivity.CONTACT_CODE)
                }
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) clAdapter.notifyDataSetChanged()

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            android.R.id.home -> {
                BaresipService.activities.remove("contacts,$aor")
                val i = Intent()
                setResult(Activity.RESULT_OK, i)
                finish()
            }
        }

        return true

    }

    override fun onBackPressed() {

        BaresipService.activities.remove("contacts,$aor")
        val i = Intent()
        setResult(Activity.RESULT_OK, i)
        finish()
        
    }

    companion object {

        fun findContactURI(name: String): String {
            for (c in Contact.contacts())
                if (c.name == name)
                    return c.uri.removePrefix("<")
                            .replaceAfter(">", "")
                            .replace(">", "")
            return name
        }

        fun findContact(uri: String): Contact? {
            for (c in Contact.contacts())
                if ((Utils.uriUserPart(c.uri) == Utils.uriUserPart(uri)) &&
                        (Utils.uriHostPart(c.uri) == Utils.uriHostPart(uri)))
                    return c
            return null
        }

        fun nameExists(name: String, ignoreCase: Boolean): Boolean {
            for (c in Contact.contacts())
                if (c.name.equals(name, ignoreCase = ignoreCase)) return true
            return false
        }

        fun contactName(uri: String): String {
            val uriUser = Utils.uriUserPart(uri)
            val uriHost = Utils.uriHostPart(uri)
            for (c in Contact.contacts()) {
                val contactUser = Utils.uriUserPart(c.uri)
                if ((contactUser == uriUser) &&
                        (Utils.isE164Number(contactUser) ||
                                (Utils.uriHostPart(c.uri) == uriHost)))
                    return c.name
            }
            return uri
        }
    }
}

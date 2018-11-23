package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.ListView

import java.io.File

class ContactsActivity : AppCompatActivity() {

    internal lateinit var clAdapter: ContactListAdapter

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        filesPath = applicationContext.filesDir.absolutePath

        val listView = findViewById(R.id.contacts) as ListView

        val aor = intent.extras.getString("aor")
        clAdapter = ContactListAdapter(this, Contact.contacts(), aor)
        listView.adapter = clAdapter
        listView.isLongClickable = true

        val plusButton = findViewById(R.id.plusButton) as ImageButton
        plusButton.setOnClickListener {
            if (Contact.contacts().size >= Contact.CONTACTS_SIZE) {
                Utils.alertView(this, "Notice",
                        "Your maximum number of contacts " +
                                "(${Contact.CONTACTS_SIZE}) has been exceeded.")
            } else {
                val i = Intent(this, ContactActivity::class.java)
                val b = Bundle()
                b.putBoolean("new", true)
                b.putString("uri", "")
                i.putExtras(b)
                startActivityForResult(i, MainActivity.CONTACT_CODE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) clAdapter.notifyDataSetChanged()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Log.d("Baresip", "Back array was pressed at Contacts")
                val i = Intent()
                setResult(Activity.RESULT_OK, i)
                finish()
            }
        }
        return true
    }

    override fun onBackPressed() {
        Log.d("Baresip", "Back button was pressed at Contacts")
        val i = Intent()
        setResult(Activity.RESULT_OK, i)
        finish()
    }

    companion object {

        internal var filesPath = ""

        fun saveContacts(path: String = filesPath) {
            var contents = ""
            for (c in Contact.contacts())
                contents += "\"${c.name}\" ${c.uri}\n"
            Utils.putFileContents(File(path + "/contacts"), contents)
        }

        fun restoreContacts(path: String = filesPath) {
            val content = Utils.getFileContents(File(path + "/contacts"))
            Api.contacts_remove()
            Contact.contacts().clear()
            content.lines().forEach {
                val parts = it.split("\"")
                if (parts.size == 3) {
                    val name = parts[1]
                    val uri = parts[2].trim()
                    Api.contact_add("\"$name\" $uri")
                    Contact.contacts().add(Contact(name, uri))
                }
            }
        }

        fun findContactURI(name: String): String {
            for (c in Contact.contacts())
                if (c.name == name)
                    return c.uri.removePrefix("<")
                            .replaceAfter(">", "")
                            .replace(">", "")
            return name
        }

        fun nameExists(name: String): Boolean {
            for (c in Contact.contacts())
                if (c.name.equals(name, ignoreCase = true)) return true
            return false
        }

        fun contactName(uri: String): String {
            for (c in Contact.contacts())
                if ((Utils.uriUserPart(c.uri) == Utils.uriUserPart(uri)) &&
                        (Utils.uriHostPart(c.uri) == Utils.uriHostPart(uri)))
                    return c.name
            return uri
        }
    }
}

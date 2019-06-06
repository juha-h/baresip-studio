package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.ListView

import java.io.File

class ContactsActivity : AppCompatActivity() {

    internal lateinit var clAdapter: ContactListAdapter

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        val listView = findViewById(R.id.contacts) as ListView

        val aor = intent.getStringExtra("aor")!!
        clAdapter = ContactListAdapter(this, Contact.contacts(), aor)
        listView.adapter = clAdapter
        listView.isLongClickable = true

        val plusButton = findViewById(R.id.plusButton) as ImageButton
        plusButton.setOnClickListener {
            if (Contact.contacts().size >= Contact.CONTACTS_SIZE) {
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.contacts_exceeded),
                                Contact.CONTACTS_SIZE))
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.contacts_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        when (item.itemId) {
            R.id.export_contacts -> {
                if (saveContacts(dir, "contacts.bs"))
                    Utils.alertView(this, "",
                            getString(R.string.exported_contacts))
                else
                    Utils.alertView(this,getString(R.string.error),
                            getString(R.string.export_error))
            }
            R.id.import_contacts -> {
                if (restoreContacts(dir, "contacts.bs")) {
                    Utils.alertView(this, "",
                            getString(R.string.imported_contacts))
                    clAdapter.notifyDataSetChanged()
                    saveContacts(applicationContext.filesDir, "contacts")
                } else
                    Utils.alertView(this,getString(R.string.error),
                            getString(R.string.import_error))
            }
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

        fun saveContacts(path: File, file: String): Boolean {
            var contents = ""
            for (c in Contact.contacts())
                contents += "\"${c.name}\" <${c.uri}>\n"
            return Utils.putFileContents(File(path, file), contents)
        }

        fun restoreContacts(path: File, file: String): Boolean {
            val content = Utils.getFileContents(File(path, file))
            if (content == "Failed") return false
            Api.contacts_remove()
            Contact.contacts().clear()
            content.lines().forEach {
                val parts = it.split("\"")
                if (parts.size == 3) {
                    val name = parts[1]
                    var uri = parts[2].trim()
                    if (uri.startsWith("<"))
                        uri = uri.substringAfter("<").substringBefore(">")
                    // Currently no need to make baresip aware of the contact
                    // Api.contact_add("\"$name\" $uri")
                    Contact.contacts().add(Contact(name, uri))
                }
            }
            return true
        }

        fun findContactURI(name: String): String {
            for (c in Contact.contacts())
                if (c.name == name)
                    return c.uri.removePrefix("<")
                            .replaceAfter(">", "")
                            .replace(">", "")
            return name
        }

        fun nameExists(name: String, ignoreCase: Boolean): Boolean {
            for (c in Contact.contacts())
                if (c.name.equals(name, ignoreCase = ignoreCase)) return true
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

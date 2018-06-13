package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.widget.*

import java.io.File
import java.util.ArrayList

class ContactsActivity : AppCompatActivity() {

    internal lateinit var layout: LinearLayout
    internal var name: String = ""
    lateinit var clAdapter: ContactListAdapter

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        val listview = findViewById(R.id.contacts) as ListView
        Log.d("Baresip", "Got ${contacts.size} contacts")
        clAdapter = ContactListAdapter(this, contacts)
        listview.adapter = clAdapter

        val addContactButton = findViewById(R.id.addContact) as ImageButton
        val newNameView = findViewById(R.id.newName) as EditText
        addContactButton.setOnClickListener{
            val name = newNameView.text.toString().trim()
            if (!Utils.checkName(name)) {
                Log.e("Baresip", "Invalid contact name $name")
                Utils.alertView(this, "Notice",
                        "Invalid contact name: $name")
            } else if (nameExists(name)) {
                Utils.alertView(this, "Notice",
                        "Contact $name already exists")
            } else {
                newNameView.setText("")
                newNameView.hint = "Contact name"
                newNameView.clearFocus()
                val i = Intent(this, ContactActivity::class.java)
                val b = Bundle()
                b.putBoolean("new", true)
                b.putString("name", name)
                b.putString("uri", "")
                i.putExtras(b)
                startActivityForResult(i, MainActivity.CONTACT_CODE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            Log.d("Baresip", "Notifying clAdapter")
            clAdapter.notifyDataSetChanged()
        }
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

        var contacts = ArrayList<Contact>()

        fun generateContacts(path: String) {
            val content = Utils.getFileContents(File(path))
            MainActivity.contacts_remove()
            contacts.clear()
            content.lines().forEach {
                val parts = it.split("\"")
                if (parts.size == 3) {
                    val name = parts[1]
                    val uri = parts[2].trim()
                    MainActivity.contact_add("\"$name\" $uri")
                    contacts.add(Contact(name, uri))
                }
            }
        }

        fun saveContacts() {
            var contents = ""
            for (c in contacts)
                    contents += "\"${c.name}\" ${c.uri}\n"
            val path = MainActivity.filesPath + "/contacts"
            Utils.putFileContents(File(path), contents)
            Log.d("Baresip", "Saved contacts '${contents}' to '$path")
        }

        fun findContactURI(name: String): String {
            for (c in contacts)
                if (c.name == name)
                    return c.uri.removePrefix("<")
                            .replaceAfter(">", "")
                            .replace(">", "")
            return name
        }

        fun nameExists(name: String): Boolean {
            for (c in contacts)
                if (c.name.equals(name, ignoreCase = true)) return true
            return false
        }
    }

}

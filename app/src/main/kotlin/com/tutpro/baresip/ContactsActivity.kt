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
        Log.d("Baresip", "Got ${contactNames.size} contacts")
        clAdapter = ContactListAdapter(this, contactNames)
        listview.adapter = clAdapter

        val addContactButton = findViewById(R.id.addContact) as ImageButton
        val newNameView = findViewById(R.id.newName) as EditText
        addContactButton.setOnClickListener{
            val name = newNameView.text.toString().trim()
            if (!Utils.checkName(name)) {
                Log.e("Baresip", "Invalid contact name $name")
                Utils.alertView(this, "Notice",
                        "Invalid contact name: $name")
            } else if (contactNames.contains(name)) {
                Log.e("Baresip", "Contact name $name already exists")
                Utils.alertView(this, "Notice",
                        "Contact $name already exists")
            } else {
                newNameView.setText("")
                newNameView.hint = "Contact name"
                newNameView.clearFocus()
                contactNames.add(name)
                contactURIs.add("")
                posAtContacts.add(contactNames.size)
                clAdapter.notifyDataSetChanged()
                saveContacts()
                val i = Intent(this, ContactActivity::class.java)
                val b = Bundle()
                b.putInt("index", contactNames.size - 1)
                i.putExtras(b)
                startActivityForResult(i, MainActivity.CONTACT_CODE)
            }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            Log.d("Baresip", "Notifying clAdapter")
            clAdapter.notifyDataSetChanged()
        }
    }

    companion object {

        var contactNames = ArrayList<String>()
        var contactURIs = ArrayList<String>()
        var posAtContacts = ArrayList<Int>()

        fun generateContacts(path: String) {
            val content = Utils.getFileContents(File(path))
            MainActivity.contacts_remove()
            contactNames.clear()
            contactURIs.clear()
            posAtContacts.clear()
            var i = 0
            content.lines().forEach {
                val parts = it.split("\"")
                if (parts.size == 3) {
                    val name = parts[1]
                    var uri = parts[2].trim()
                    MainActivity.contact_add("\"$name\" $uri")
                    contactNames.add(name)
                    contactURIs.add(uri)
                    posAtContacts.add(i++)
                }
            }
        }

        fun saveContacts() {
            var contents = ""
            for (i in contactNames.indices)
                    contents += "\"${contactNames[i]}\" ${contactURIs[i]}\n"
            val path = MainActivity.filesPath + "/contacts"
            Utils.putFileContents(File(path), contents)
            Log.d("Baresip", "Saved contacts '${contents}' to '$path")
        }

        fun findContactURI(name: String): String {
            for (i in contactNames.indices)
                if (contactNames[i] == name)
                    return contactURIs[i].removePrefix("<")
                            .replaceAfter(">", "")
                            .replace(">", "")
            return name
        }
    }

}

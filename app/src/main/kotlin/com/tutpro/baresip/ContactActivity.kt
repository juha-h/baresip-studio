package com.tutpro.baresip

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*

class ContactActivity : AppCompatActivity() {

    lateinit var nameView: EditText
    lateinit var uriView: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact)

        new = intent.extras.getBoolean("new")
        val name: String
        val uri: String
        if (new) {
            name = intent.extras.getString("name")
            uri = intent.extras.getString("uri")
        } else {
            index = intent.extras.getInt("index")
            name = ContactsActivity.contacts[index].name
            uri = ContactsActivity.contacts[index].uri
        }

        setTitle(name)

        nameView = findViewById(R.id.Name) as EditText
        nameView.setText(name)
        if (new) nameView.setSelection(nameView.text.length)

        uriView = findViewById(R.id.Uri) as EditText
        uriView.setText(uri)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        val i = Intent(this, MainActivity::class.java)

        if (item.itemId == R.id.checkIcon) {

            val newName = nameView.text.toString().trim()
            if (!Utils.checkName(newName)) {
                Utils.alertView(this, "Notice",
                        "Invalid contact name: $newName")
                return false
            }
            if ((new || (ContactsActivity.contacts[index].name != newName)) &&
                    ContactsActivity.nameExists(newName)) {
                Utils.alertView(this, "Notice",
                        "Contact $newName already exists")
                return false
            }

            var newUri = uriView.text.toString().trim()
            if (!newUri.startsWith("<")) {
                if (!newUri.startsWith("sip:")) newUri = "sip:$newUri"
                if (!Utils.checkUri(newUri)) {
                    Utils.alertView(this, "Notice","Invalid contact URI: $newUri")
                    return false
                }
            }

            if (new) {
                ContactsActivity.contacts.add(Contact(newName, newUri))
            } else {
                ContactsActivity.contacts[index].uri = newUri
                ContactsActivity.contacts[index].name = newName
            }
            ContactsActivity.contacts.sortBy { Contact -> Contact.name }
            ContactsActivity.saveContacts()

            setResult(RESULT_OK, i)
            finish()
            return true

        } else if (item.itemId == android.R.id.home) {

            Log.d("Baresip", "Back array was pressed at Contact")
            setResult(RESULT_CANCELED, i)
            finish()
            return true

        } else return super.onOptionsItemSelected(item)
    }

    companion object {
        internal var index = 0
        internal var new = false
    }

}

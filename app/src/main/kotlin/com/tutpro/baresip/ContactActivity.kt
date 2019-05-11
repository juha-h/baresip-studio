package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.*

class ContactActivity : AppCompatActivity() {

    lateinit var nameView: EditText
    lateinit var uriView: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact)

        nameView = findViewById(R.id.Name) as EditText
        uriView = findViewById(R.id.Uri) as EditText

        val uri = intent.getStringExtra("uri")
        new = intent.getBooleanExtra("new", false)

        if (new) {
            setTitle(getString(R.string.new_contact))
            nameView.setText("")
            nameView.hint = getString(R.string.contact_name)
            nameView.setSelection(nameView.text.length)
            if (uri == "") {
                uriView.setText("")
                uriView.hint = getString(R.string.sip_uri)
            } else {
                uriView.setText(uri)
            }
        } else {
            index = intent.getIntExtra("index", 0)
            val name = Contact.contacts()[index].name
            setTitle(name)
            nameView.setText(name)
            uriView.setText(Contact.contacts()[index].uri)
        }
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
                Utils.alertView(this, getString(R.string.error),
                        String.format(getString(R.string.invalid_contact), newName))
                return false
            }
            if ((new || (Contact.contacts()[index].name != newName)) &&
                    ContactsActivity.nameExists(newName)) {
                Utils.alertView(this, getString(R.string.error),
                        String.format(getString(R.string.contact_already_exists), newName))
                return false
            }

            var newUri = uriView.text.toString().trim()
            if (!newUri.startsWith("<")) {
                if (!newUri.startsWith("sip:")) newUri = "sip:$newUri"
                if (!Utils.checkAor(newUri.substringAfter("sip:"))) {
                    Utils.alertView(this, getString(R.string.error),
                            String.format(getString(R.string.invalid_contact_uri), newUri))
                    return false
                }
            }

            if (new) {
                if (Contact.contacts().size >= Contact.CONTACTS_SIZE) {
                    Utils.alertView(this, getString(R.string.notice),
                            String.format(getString(R.string.contacts_exceeded),
                                    Contact.CONTACTS_SIZE))
                    return true
                } else {
                    Contact.contacts().add(Contact(newName, newUri))
                }
            } else {
                Contact.contacts()[index].uri = newUri
                Contact.contacts()[index].name = newName
            }

            Contact.contacts().sortBy { Contact -> Contact.name }
            ContactsActivity.saveContacts(applicationContext.filesDir)

            i.putExtra("name", newName)
            setResult(Activity.RESULT_OK, i)
            finish()
            return true

        } else if (item.itemId == android.R.id.home) {

            Log.d("Baresip", "Back array was pressed at Contact")
            setResult(Activity.RESULT_CANCELED, i)
            finish()
            return true

        } else return super.onOptionsItemSelected(item)
    }

    companion object {

        internal var index = 0
        internal var new = false

    }

}

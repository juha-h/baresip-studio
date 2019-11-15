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

    internal var index = 0
    internal var new = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact)

        nameView = findViewById(R.id.Name) as EditText
        uriView = findViewById(R.id.Uri) as EditText

        new = intent.getBooleanExtra("new", false)
        val uOrI: String

        if (new) {
            setTitle(getString(R.string.new_contact))
            nameView.setText("")
            nameView.hint = getString(R.string.contact_name)
            nameView.setSelection(nameView.text.length)
            val uri = intent.getStringExtra("uri")
            if (uri == "") {
                uriView.setText("")
                uriView.hint = getString(R.string.sip_uri)
            } else {
                uriView.setText(uri)
            }
            uOrI = uri
        } else {
            index = intent.getIntExtra("index", 0)
            val name = Contact.contacts()[index].name
            setTitle(name)
            nameView.setText(name)
            uriView.setText(Contact.contacts()[index].uri)
            uOrI = index.toString()
        }

        BaresipService.activities.add(0, "contact,$new,$uOrI")

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        super.onCreateOptionsMenu(menu)

        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)
        return true

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        val i = Intent(this, MainActivity::class.java)

        when (item.itemId) {

            R.id.checkIcon -> {

                var newName = nameView.text.toString().trim()
                var newUri = uriView.text.toString().trim()

                if (newName == "") newName = newUri

                if (!Utils.checkName(newName)) {
                    Utils.alertView(this, getString(R.string.error),
                            String.format(getString(R.string.invalid_contact), newName))
                    return false
                }
                val alert: Boolean
                if (new)
                    alert = ContactsActivity.nameExists(newName, true)
                else
                    alert = (Contact.contacts()[index].name != newName) &&
                            ContactsActivity.nameExists(newName, false)
                if (alert) {
                    Utils.alertView(this, getString(R.string.error),
                            String.format(getString(R.string.contact_already_exists), newName))
                    return false
                }

                if (!newUri.startsWith("sip:")) {
                    if (newUri.contains("@") || (BaresipService.uas.size != 1)) {
                        newUri = "sip:$newUri"
                    } else {
                        newUri = "sip:$newUri@${Utils.aorDomain(BaresipService.uas[0].account.aor)}"
                    }
                }
                if (!Utils.checkSipUri(newUri)) {
                    Utils.alertView(this, getString(R.string.error),
                            String.format(getString(R.string.invalid_contact_uri), newUri))
                    return false
                }

                if (new) {
                    if (Contact.contacts().size >= Contact.CONTACTS_SIZE) {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.contacts_exceeded),
                                        Contact.CONTACTS_SIZE))
                        BaresipService.activities.removeAt(0)
                        return true
                    } else {
                        Contact.contacts().add(Contact(newName, newUri))
                    }
                } else {
                    Contact.contacts()[index].uri = newUri
                    Contact.contacts()[index].name = newName
                }

                Contact.contacts().sortBy { Contact -> Contact.name }
                Contact.save()

                i.putExtra("name", newName)
                setResult(Activity.RESULT_OK, i)
                finish()
            }

            android.R.id.home -> {

                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }

        }

        BaresipService.activities.removeAt(0)
        return true

    }

    override fun onBackPressed() {

        BaresipService.activities.removeAt(0)
        super.onBackPressed()

    }

}

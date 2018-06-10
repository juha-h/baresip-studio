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

        index = intent.extras.getInt("index")
        val name = ContactsActivity.contactNames[index]
        val uri = ContactsActivity.contactURIs[index]
        setTitle(name)

        nameView = findViewById(R.id.Name) as EditText
        nameView.setText(name)

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

            val name = nameView.text.toString().trim()
            if (!Utils.checkName(name)) {
                Utils.alertView(this, "Notice",
                        "Invalid contact name: $name")
                return false
            }

            var uri = uriView.text.toString().trim()
            if (!uri.startsWith("<")) {
                if (!uri.startsWith("sip:")) uri = "sip:$uri"
                if (!Utils.checkUri(uri)) {
                    Utils.alertView(this, "Notice","Invalid contact URI: $uri")
                    return false
                }
            }

            ContactsActivity.contactNames[index] = name
            ContactsActivity.contactURIs[index] = uri
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
    }

}

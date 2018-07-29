package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.ListView

import java.io.File
import java.util.ArrayList

class ContactsActivity : AppCompatActivity() {

    internal lateinit var clAdapter: ContactListAdapter

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        val listView = findViewById(R.id.contacts) as ListView
        Log.d("Baresip", "Got ${contacts.size} contacts")
        clAdapter = ContactListAdapter(this, contacts)
        listView.adapter = clAdapter

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_NEGATIVE -> {
                        ContactsActivity.contacts.removeAt(pos)
                        ContactsActivity.saveContacts()
                        clAdapter.notifyDataSetChanged()
                    }
                    DialogInterface.BUTTON_POSITIVE -> {
                    }
                }
            }
            val builder = AlertDialog.Builder(this@ContactsActivity,
                    R.style.Theme_AppCompat)
            builder.setMessage("Do you want to delete ${contacts[pos].name}?")
                    .setPositiveButton("Cancel", dialogClickListener)
                    .setNegativeButton("Delete Contact", dialogClickListener)
                    .show()
            true
        }

        val plusButton = findViewById(R.id.plusButton) as ImageButton
        plusButton.setOnClickListener {
            val i = Intent(this, ContactActivity::class.java)
            val b = Bundle()
            b.putBoolean("new", true)
            b.putString("uri", "")
            i.putExtras(b)
            startActivityForResult(i, MainActivity.CONTACT_CODE)
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
            contacts_remove()
            contacts.clear()
            content.lines().forEach {
                val parts = it.split("\"")
                if (parts.size == 3) {
                    val name = parts[1]
                    val uri = parts[2].trim()
                    contact_add("\"$name\" $uri")
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

        fun contactName(uri: String): String {
            for (c in contacts)
                if ((Utils.uriUserPart(c.uri) == Utils.uriUserPart(uri)) &&
                        (Utils.uriHostPart(c.uri) == Utils.uriHostPart(uri)))
                    return c.name
            return uri
        }

        external fun contacts_remove()
        external fun contact_add(contact: String)

    }

}

package com.tutpro.baresip

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView

import java.io.File
import java.util.ArrayList

class EditContactsActivity : AppCompatActivity() {

    private var editText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_contacts)
        editText = findViewById(R.id.editText) as EditText
        val path = applicationContext.filesDir.absolutePath + "/contacts"
        val file = File(path)
        val content = Utils.getFileContents(file)
        Log.d("Baresip", "Contacts length is: " + content.length)
        editText!!.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.textsize))
        editText!!.setText(content, TextView.BufferType.EDITABLE)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.edit_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val i = Intent(this, MainActivity::class.java)
        when (item.itemId) {
            R.id.save -> {
                val path = applicationContext.filesDir.absolutePath + "/contacts"
                val file = File(path)
                Utils.putFileContents(file, editText!!.text.toString())
                Log.d("Baresip", "Updated contacts file")
                MainActivity.contacts_remove()
                updateContactsAndNames(path)
                // MainActivity.CalleeAdapter.notifyDataSetChanged(); does not work
                setResult(RESULT_OK, i)
                finish()
                return true
            }
            R.id.cancel, android.R.id.home -> {
                i.putExtra("action", "cancel")
                setResult(RESULT_OK, i)
                finish()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    companion object {
        var Contacts: ArrayList<Contact> = java.util.ArrayList()
        var Names: ArrayList<String> = java.util.ArrayList()

        fun updateContactsAndNames(path: String) {
            val file = File(path)
            val lines = Utils.getFileContents(file).split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var name: String
            var uri: String
            Contacts.clear()
            Names.clear()
            for (line in lines) {
                line.trim { it <= ' ' }
                if (line.startsWith("#") || line.length == 0) continue
                val parts = line.split("\"".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (parts.size != 3) {
                    Log.e("Baresip", "Invalid contacts line: $line")
                    continue
                }
                name = parts[1]
                if (name.length < 2) {
                    Log.e("Baresip", "Too short contact display name: $name")
                    continue
                }
                uri = parts[2].trim { it <= ' ' }
                if (!uri.startsWith("<") || !uri.contains(">")) {
                    Log.e("Baresip", "Invalid contact uri: $uri")
                    continue
                }
                MainActivity.contact_add("\"$name\" $uri")
                if (uri.indexOf(";access") > 0) continue
                uri = uri.substring(1, uri.indexOf(">"))
                Log.d("Baresip", "Adding contact name/uri: $name/$uri")
                Contacts.add(Contact(name, uri))
                Names.add(name)
            }
        }

        fun findContactURI(name: String): String {
            for (c in Contacts) {
                if (c.name == name) return c.uri
            }
            return name
        }
    }

}


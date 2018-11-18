package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView

import java.util.*

class ContactListAdapter(private val cxt: Context, private val rows: ArrayList<Contact>,
                         private val aor: String) :
        ArrayAdapter<Contact>(cxt, R.layout.contact_row, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = rows[position]
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView = inflater.inflate(R.layout.contact_row, parent, false)
        val nameView = rowView.findViewById(R.id.contactName) as TextView
        nameView.text = row.name
        nameView.textSize = 20f
        nameView.setPadding(6, 6, 0, 6)
        if (aor != "") {
            nameView.setOnClickListener { _ ->
                val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                    when (which) {
                        DialogInterface.BUTTON_NEUTRAL -> {
                            val intent = Intent(cxt, MainActivity::class.java)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            intent.putExtra("action", "call")
                            intent.putExtra("uap", Account.findUa(aor)!!.uap)
                            intent.putExtra("peer", ContactsActivity.contacts[position].uri)
                            (cxt as Activity).startActivity(intent)
                        }
                        DialogInterface.BUTTON_NEGATIVE -> {
                            val intent = Intent(cxt, ChatsActivity::class.java)
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            val b = Bundle()
                            b.putString("aor", aor)
                            b.putString("peer", ContactsActivity.contacts[position].uri)
                            intent.putExtras(b)
                            (cxt as Activity).startActivity(intent)
                        }
                        DialogInterface.BUTTON_POSITIVE -> {
                        }
                    }
                }
                val builder = AlertDialog.Builder(cxt, R.style.Theme_AppCompat)
                builder.setMessage("Do you want to call or send message to " +
                        "${ContactsActivity.contacts[position].name}?")
                        .setNeutralButton("Call", dialogClickListener)
                        .setNegativeButton("Send Message", dialogClickListener)
                        .setPositiveButton("Cancel", dialogClickListener)
                        .show()
            }
        }
        nameView.setOnLongClickListener { _ ->
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_NEGATIVE -> {
                        ContactsActivity.contacts.removeAt(position)
                        ContactsActivity.saveContacts()
                        this.notifyDataSetChanged()
                    }
                    DialogInterface.BUTTON_POSITIVE -> {
                    }
                }
            }
            val builder = AlertDialog.Builder(cxt, R.style.Theme_AppCompat)
            builder.setMessage("Do you want to delete ${ContactsActivity.contacts[position].name}?")
                    .setPositiveButton("Cancel", dialogClickListener)
                    .setNegativeButton("Delete Contact", dialogClickListener)
                    .show()
            true
        }
        val actionView = rowView.findViewById(R.id.action) as ImageButton
        actionView.setOnClickListener { _ ->
            val i = Intent(cxt, ContactActivity::class.java)
            val b = Bundle()
            b.putBoolean("new", false)
            b.putInt("index", position)
            i.putExtras(b)
            (cxt as Activity).startActivityForResult(i, MainActivity.CONTACT_CODE)
        }
        return rowView
    }
}

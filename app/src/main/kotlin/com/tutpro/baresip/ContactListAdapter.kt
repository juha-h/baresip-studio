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

    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val row = rows[pos]
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
                        DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE -> {
                            val i = Intent(cxt, MainActivity::class.java)
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            if (which == DialogInterface.BUTTON_NEGATIVE)
                                i.putExtra("action", "call")
                            else
                                i.putExtra("action", "message")
                            val ua = Account.findUa(aor)
                            if (ua == null) {
                                Log.w("Baresip", "onClickListener did not find AoR $aor")
                            } else {
                                i.putExtra("uap", ua.uap)
                                i.putExtra("peer", Contact.contacts()[pos].uri)
                                (cxt as Activity).startActivity(i)
                            }
                        }
                        DialogInterface.BUTTON_NEUTRAL -> {
                        }
                    }
                }
                val builder = AlertDialog.Builder(cxt, R.style.Theme_AppCompat)
                builder.setMessage(String.format(cxt.getString(R.string.contact_action_question),
                        Contact.contacts()[pos].name))
                        .setNeutralButton(cxt.getText(R.string.cancel), dialogClickListener)
                        .setNegativeButton(cxt.getText(R.string.call), dialogClickListener)
                        .setPositiveButton(cxt.getText(R.string.send_message), dialogClickListener)
                        .show()
            }
        }
        nameView.setOnLongClickListener { _ ->
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        Contact.contacts().removeAt(pos)
                        ContactsActivity.saveContacts(cxt.applicationContext.filesDir)
                        this.notifyDataSetChanged()
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
            val builder = AlertDialog.Builder(cxt, R.style.Theme_AppCompat)
            builder.setMessage(String.format(cxt.getString(R.string.contact_delete_question),
                    Contact.contacts()[pos].name))
                    .setNegativeButton(cxt.getText(R.string.cancel), dialogClickListener)
                    .setPositiveButton(cxt.getText(R.string.delete), dialogClickListener)
                    .show()
            true
        }
        val actionView = rowView.findViewById(R.id.edit) as ImageButton
        actionView.setOnClickListener { _ ->
            val i = Intent(cxt, ContactActivity::class.java)
            val b = Bundle()
            b.putBoolean("new", false)
            b.putInt("index", pos)
            i.putExtras(b)
            (cxt as Activity).startActivityForResult(i, MainActivity.CONTACT_CODE)
        }
        return rowView
    }
}

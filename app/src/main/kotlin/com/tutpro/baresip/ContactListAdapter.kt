package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView

import java.util.*

class ContactListAdapter(private val cxt: Context, private val rows: ArrayList<String>) :
        ArrayAdapter<String>(cxt, R.layout.contact_row, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = rows[position]
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView = inflater.inflate(R.layout.contact_row, parent, false)
        val nameView = rowView.findViewById(R.id.contactName) as TextView
        nameView.text = row
        nameView.textSize = 20f
        nameView.setPadding(6, 6, 0, 6)
        nameView.setOnClickListener { _ ->
            val i = Intent(cxt, ContactActivity::class.java)
            val b = Bundle()
            b.putInt("index", position)
            i.putExtras(b)
            (cxt as Activity).startActivityForResult(i, MainActivity.CONTACT_CODE)
        }
        val actionView = rowView.findViewById(R.id.contactAction) as ImageButton
        actionView.setImageResource(R.drawable.action_remove)
        actionView.setOnClickListener { _ ->
            Log.d("Baresip", "Delete button clicked")
            val deleteDialog = AlertDialog.Builder(cxt)
            deleteDialog.setMessage("Do you want to delete contact ${row}")
            deleteDialog.setPositiveButton("Delete") { dialog, _ ->
                ContactsActivity.contactNames.removeAt(position)
                ContactsActivity.contactURIs.removeAt(position)
                ContactsActivity.posAtContacts.removeAt(position)
                ContactsActivity.saveContacts()
                this.notifyDataSetChanged()
                dialog.dismiss()
            }
            deleteDialog.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            deleteDialog.create().show()
        }
        return rowView
    }
}

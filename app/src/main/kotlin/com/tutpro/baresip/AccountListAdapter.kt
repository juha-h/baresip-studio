package com.tutpro.baresip

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

class AccountListAdapter(private val cxt: Context, private val rows: ArrayList<AccountRow>) :
        ArrayAdapter<AccountRow>(cxt, R.layout.account_row, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = rows[position]
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView = inflater.inflate(R.layout.account_row, parent, false)
        val aorView = rowView.findViewById(R.id.aor) as TextView
        aorView.text = row.aor
        aorView.textSize = 20f
        aorView.setPadding(6, 6, 0, 6)
        aorView.setOnClickListener { _ ->
            val i = Intent(cxt, AccountActivity::class.java)
            val b = Bundle()
            b.putString("accp", MainActivity.uas[position].account.accp)
            i.putExtras(b)
            cxt.startActivity(i)
        }
        val actionView = rowView.findViewById(R.id.action) as ImageButton
        actionView.setImageResource(row.action)
        actionView.setOnClickListener { _ ->
            Log.d("Baresip", "Delete button clicked")
            val deleteDialog = AlertDialog.Builder(cxt)
            deleteDialog.setMessage("Do you want to delete account ${MainActivity.uas[position].account.aor}?")
            deleteDialog.setPositiveButton("Delete") { dialog, _ ->
                MainActivity.uas[position].destroy()
                MainActivity.uas.remove(MainActivity.uas[position])
                MainActivity.images.removeAt(position)
                AccountsActivity.saveAccounts()
                AccountsActivity.generateAccounts()
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

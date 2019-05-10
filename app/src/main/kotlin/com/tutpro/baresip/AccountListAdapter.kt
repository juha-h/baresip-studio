package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.support.v7.app.AlertDialog
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
            b.putString("accp", UserAgent.uas()[position].account.accp)
            i.putExtras(b)
            (cxt as Activity).startActivity(i)
        }
        val ua = UserAgent.uas()[position]
        val actionView = rowView.findViewById(R.id.action) as ImageButton
        actionView.setImageResource(row.action)
        actionView.setOnClickListener { _ ->
            Log.d("Baresip", "Delete button clicked")
            val deleteDialog = AlertDialog.Builder(cxt)
            deleteDialog.setMessage(String.format(cxt.getString(R.string.delete_account),
                    ua.account.aor))
            deleteDialog.setPositiveButton(cxt.getText(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            deleteDialog.setNegativeButton(cxt.getText(R.string.delete)) { dialog, _ ->
                Api.ua_destroy(ua.uap)
                UserAgent.remove(ua)
                AccountsActivity.generateAccounts()
                AccountsActivity.saveAccounts()
                this.notifyDataSetChanged()
                dialog.dismiss()
            }
            deleteDialog.create().show()
        }
        return rowView
    }

}

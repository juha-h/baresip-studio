package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
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
        aorView.text = row.aor.split(":")[0]
        aorView.textSize = 20f
        aorView.setPadding(6, 6, 0, 6)
        aorView.setOnClickListener {
            val i = Intent(cxt, AccountActivity::class.java)
            val b = Bundle()
            b.putString("aor", UserAgent.uas()[position].account.aor)
            i.putExtras(b)
            (cxt as Activity).startActivityForResult(i, MainActivity.ACCOUNT_CODE)
        }
        val ua = UserAgent.uas()[position]
        val actionView = rowView.findViewById(R.id.action) as ImageButton
        actionView.setImageResource(row.action)
        actionView.setOnClickListener {
            val titleView = View.inflate(cxt, R.layout.alert_title, null) as TextView
            titleView.text = cxt.getString(R.string.confirmation)
            with (AlertDialog.Builder(cxt, R.style.AlertDialog)) {
                setCustomTitle(titleView)
                setMessage(String.format(cxt.getString(R.string.delete_account),
                        aorView.text))
                setPositiveButton(cxt.getText(R.string.delete)) { dialog, _ ->
                    Api.ua_destroy(ua.uap)
                    CallHistory.clear(ua.account.aor)
                    Message.clear(ua.account.aor)
                    ua.remove()
                    AccountsActivity.generateAccounts()
                    AccountsActivity.saveAccounts()
                    this@AccountListAdapter.notifyDataSetChanged()
                    dialog.dismiss()
                }
                setNegativeButton(cxt.getText(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                show()
            }
        }
        return rowView
    }

}

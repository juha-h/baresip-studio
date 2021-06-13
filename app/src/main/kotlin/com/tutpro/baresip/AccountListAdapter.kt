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

    private val layoutInflater = LayoutInflater.from(context)

    private class ViewHolder(view: View?) {
        val aorView = view?.findViewById(R.id.aor) as TextView
        val actionView = view?.findViewById(R.id.action) as ImageButton
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {

        val viewHolder: ViewHolder
        val rowView: View
        val ua = UserAgent.uas()[position]

        if (view == null) {
            rowView = layoutInflater.inflate(R.layout.account_row, parent, false)
            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            rowView = view
            viewHolder = rowView.tag as ViewHolder
        }

        val row = rows[position]
        viewHolder.aorView.text = row.aor.split(":")[0]
        viewHolder.aorView.textSize = 20f
        viewHolder.aorView.setPadding(6, 6, 0, 6)
        viewHolder.actionView.setImageResource(row.action)

        viewHolder.aorView.setOnClickListener {
            val i = Intent(cxt, AccountActivity::class.java)
            val b = Bundle()
            b.putString("aor", UserAgent.uas()[position].account.aor)
            i.putExtras(b)
            (cxt as MainActivity).accountRequest.launch(i)
        }

        viewHolder.actionView.setOnClickListener {
            val titleView = View.inflate(cxt, R.layout.alert_title, null) as TextView
            titleView.text = cxt.getString(R.string.confirmation)
            with (AlertDialog.Builder(cxt, R.style.AlertDialog)) {
                setCustomTitle(titleView)
                setMessage(String.format(cxt.getString(R.string.delete_account),
                        viewHolder.aorView.text))
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

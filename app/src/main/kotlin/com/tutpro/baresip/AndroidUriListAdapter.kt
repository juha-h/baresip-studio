package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView

class AndroidUriListAdapter(private val ctx: Context, private val rows: ArrayList<String>, val aor: String) :
        ArrayAdapter<String>(ctx, R.layout.android_uri_row, rows) {

    private val layoutInflater = LayoutInflater.from(context)

    private class ViewHolder(view: View?) {
        val uriView = view?.findViewById(R.id.uri) as TextView
        val messageView = view?.findViewById(R.id.message) as ImageButton
        val callView = view?.findViewById(R.id.call) as ImageButton
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {

        val viewHolder: ViewHolder
        val rowView: View

        if (view == null) {
            rowView = layoutInflater.inflate(R.layout.android_uri_row, parent, false)
            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            rowView = view
            viewHolder = rowView.tag as ViewHolder
        }

        val uri = rows[position]

        viewHolder.uriView.text = uri.substringAfter(":")

        viewHolder.messageView.setOnClickListener {
            val i = Intent(ctx, MainActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            i.putExtra("action", "message")
            val ua = UserAgent.ofAor(aor)
            if (ua == null) {
                Log.w(TAG, "onClickListener did not find AoR $aor")
            } else {
                BaresipService.activities.clear()
                i.putExtra("uap", ua.uap)
                i.putExtra("peer", uri)
                (ctx as Activity).startActivity(i)
            }
        }

        viewHolder.callView.setOnClickListener {
            val i = Intent(ctx, MainActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            i.putExtra("action", "call")
            val ua = UserAgent.ofAor(aor)
            if (ua == null) {
                Log.w(TAG, "onClickListener did not find AoR $aor")
            } else {
                BaresipService.activities.clear()
                i.putExtra("uap", ua.uap)
                i.putExtra("peer", uri)
                (ctx as Activity).startActivity(i)
            }
        }

        return rowView
    }
}

package com.tutpro.baresip

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class CallListAdapter(private val ctx: Context, private val account: Account,
                      private val rows: ArrayList<CallRow>) :
        ArrayAdapter<CallRow>(ctx, R.layout.call_row, rows) {

    private val layoutInflater = LayoutInflater.from(context)

    private class ViewHolder(view: View?) {
        val textAvatarView = view?.findViewById(R.id.TextAvatar) as TextView
        val imageAvatarView = view?.findViewById(R.id.ImageAvatar) as ImageView
        val directionsView = view?.findViewById(R.id.directions) as LinearLayout
        val etcView = view?.findViewById(R.id.etc) as TextView
        val peerURIView = view?.findViewById(R.id.peer_uri) as TextView
        val timeView = view?.findViewById(R.id.time) as TextView
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {

        val viewHolder: ViewHolder
        val rowView: View

        if (view == null) {
            rowView = layoutInflater.inflate(R.layout.call_row, parent, false)
            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            rowView = view
            viewHolder = rowView.tag as ViewHolder
        }

        val callRow = rows[position]

        Utils.setAvatar(ctx, viewHolder.imageAvatarView, viewHolder.textAvatarView, callRow.peerUri)

        viewHolder.directionsView.removeAllViews()
        var count = 1
        for (d in callRow.details) {
            if (d.recording[0] != "")
                viewHolder.timeView.typeface = Typeface.DEFAULT_BOLD
            if (count > 3) {
                viewHolder.etcView.text = "..."
                continue
            }
            val dirView = ImageView(ctx)
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            dirView.layoutParams = params
            dirView.setPadding(0, 5, 0, 0)
            dirView.setImageResource(d.direction)
            viewHolder.directionsView.addView(dirView)
            count++
        }
        if (count <= 3)
            viewHolder.etcView.text = ""

        viewHolder.peerURIView.text = Utils.friendlyUri(ctx, callRow.peerUri, account)
        viewHolder.timeView.text = Utils.relativeTime(ctx, callRow.stopTime)

        viewHolder.timeView.setOnClickListener {
            val i = Intent(ctx, CallDetailsActivity::class.java)
            val b = Bundle()
            b.putString("aor", account.aor)
            b.putString("peer", viewHolder.peerURIView.text!!.toString())
            b.putInt("position", position)
            i.putExtras(b)
            ctx.startActivity(i)
        }

        return rowView
    }

}

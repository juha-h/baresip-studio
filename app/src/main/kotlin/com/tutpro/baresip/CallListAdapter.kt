package com.tutpro.baresip

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import java.util.*

class CallListAdapter(private val cxt: Context, private val rows: ArrayList<CallRow>) :
        ArrayAdapter<CallRow>(cxt, R.layout.call_row, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val callRow = rows[position]
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView = inflater.inflate(R.layout.call_row, parent, false)
        val avatarView = rowView.findViewById(R.id.avatar) as TextView
        val avatarBackground = avatarView.background as GradientDrawable
        val contact = ContactsActivity.findContact(callRow.peerUri)
        val peer: String
        if (contact != null) {
            peer = contact.name
            avatarBackground.setColor(contact.color)
        } else {
            if (Utils.uriHostPart(callRow.peerUri) == Utils.uriHostPart(callRow.aor))
                peer = Utils.uriUserPart(callRow.peerUri)
            else
                peer = Utils.uriAor(callRow.peerUri)
            avatarBackground.setColor(Utils.randomColor())
        }
        avatarView.text = "${peer[0]}"
        val directions = rowView.findViewById(R.id.directions) as LinearLayout
        var count = 1
        for (d in callRow.directions) {
            if (count > 3) {
                val etc = rowView.findViewById(R.id.etc) as TextView
                etc.text = "..."
                break
            }
            val dirView = ImageView(cxt)
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            dirView.layoutParams = params
            dirView.setPadding(0, 5, 0, 0)
            dirView.setImageResource(d)
            directions.addView(dirView)
            count++
        }
        val peerURIView = rowView.findViewById(R.id.peer_uri) as TextView
        val contactName = ContactsActivity.contactName(callRow.peerUri)
        if (contactName.startsWith("sip:"))
            peerURIView.text = Utils.friendlyUri(contactName, Utils.aorDomain(callRow.aor))
        else
            peerURIView.text = contactName
        val timeView = rowView.findViewById(R.id.time) as TextView
        timeView.text = callRow.time
        return rowView
    }

}

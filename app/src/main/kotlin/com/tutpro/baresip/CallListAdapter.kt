package com.tutpro.baresip

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.cardview.widget.CardView
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
        val textAvatarView = rowView.findViewById(R.id.TextAvatar) as TextView
        val cardAvatarView = rowView.findViewById(R.id.CardAvatar) as CardView
        val cardImageAvatarView = rowView.findViewById(R.id.CardImageAvatar) as ImageView
        val contact = ContactsActivity.findContact(callRow.peerUri)
        if (contact != null) {
            val avatarImage = contact.avatarImage
            if (avatarImage != null) {
                textAvatarView.visibility = View.GONE
                cardAvatarView.visibility = View.VISIBLE
                cardImageAvatarView.setImageBitmap(avatarImage)
            } else {
                textAvatarView.visibility = View.VISIBLE
                cardAvatarView.visibility = View.GONE
                (textAvatarView.background as GradientDrawable).setColor(contact.color)
                textAvatarView.text = "${contact.name[0]}"
            }
        } else {
            textAvatarView.visibility = View.VISIBLE
            cardAvatarView.visibility = View.GONE
            textAvatarView.setBackgroundResource(R.drawable.person_image)
        }
        val directions = rowView.findViewById(R.id.directions) as LinearLayout
        directions.removeAllViews()
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

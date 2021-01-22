package com.tutpro.baresip.plus

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.cardview.widget.CardView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*

import java.util.*

class CallListAdapter(private val cxt: Context, private val rows: ArrayList<CallRow>) :
        ArrayAdapter<CallRow>(cxt, R.layout.call_row, rows) {

    private val layoutInflater = LayoutInflater.from(context)

    private class ViewHolder(view: View?) {
        val textAvatarView = view?.findViewById(R.id.TextAvatar) as TextView
        val cardAvatarView = view?.findViewById(R.id.CardAvatar) as CardView
        val cardImageAvatarView = view?.findViewById(R.id.CardImageAvatar) as ImageView
        val directionsView = view?.findViewById(R.id.directions) as LinearLayout
        val etcView = view?.findViewById(R.id.etc) as TextView
        val peerUriView = view?.findViewById(R.id.peer_uri) as TextView
        val timeView = view?.findViewById(R.id.time) as TextView
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {

        val viewHolder: ViewHolder
        val rowView: View

        if (view == null) {
            rowView = layoutInflater.inflate(R.layout.call_row, parent, false)
            viewHolder = CallListAdapter.ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            rowView = view
            viewHolder = rowView.tag as ViewHolder
        }

        val callRow = rows[position]

        val contact = ContactsActivity.findContact(callRow.peerUri)
        if (contact != null) {
            val avatarImage = contact.avatarImage
            if (avatarImage != null) {
                viewHolder.textAvatarView.visibility = View.GONE
                viewHolder.cardAvatarView.visibility = View.VISIBLE
                viewHolder.cardImageAvatarView.setImageBitmap(avatarImage)
            } else {
                viewHolder.textAvatarView.visibility = View.VISIBLE
                viewHolder.cardAvatarView.visibility = View.GONE
                (viewHolder.textAvatarView.background as GradientDrawable).setColor(contact.color)
                viewHolder.textAvatarView.text = "${contact.name[0]}"
            }
        } else {
            viewHolder.textAvatarView.visibility = View.VISIBLE
            viewHolder.cardAvatarView.visibility = View.GONE
            viewHolder.textAvatarView.setBackgroundResource(R.drawable.person_image)
        }

        var count = 1
        for (d in callRow.directions) {
            if (count > 3) {
                viewHolder.etcView.text = "..."
                break
            }
            val dirView = ImageView(cxt)
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            dirView.layoutParams = params
            dirView.setPadding(0, 5, 0, 0)
            dirView.setImageResource(d)
            viewHolder.directionsView.addView(dirView)
            count++
        }

        val contactName = ContactsActivity.contactName(callRow.peerUri)
        if (contactName.startsWith("sip:"))
            viewHolder.peerUriView.text = Utils.friendlyUri(contactName, Utils.aorDomain(callRow.aor))
        else
            viewHolder.peerUriView.text = contactName

        viewHolder.timeView.text = callRow.time

        return rowView
    }

}

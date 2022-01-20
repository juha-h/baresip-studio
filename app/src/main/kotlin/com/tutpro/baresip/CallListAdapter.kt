package com.tutpro.baresip

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Bitmap
import android.graphics.Canvas

import java.util.*

class CallListAdapter(private val ctx: Context, private val aor: String, private val rows: ArrayList<CallRow>) :
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

        val contact = ContactsActivity.findContact(callRow.peerUri)
        if (contact != null) {
            val avatarImage = contact.avatarImage
            if (avatarImage != null) {
                viewHolder.imageAvatarView.setImageBitmap(avatarImage)
            } else {
                viewHolder.textAvatarView.background.setTint(contact.color)
                if (contact.name.isNotEmpty())
                    viewHolder.textAvatarView.text = "${contact.name[0]}"
                else
                    viewHolder.textAvatarView.text = ""
                viewHolder.imageAvatarView.setImageBitmap(getBitmapFromView(viewHolder.textAvatarView))
            }
        } else {
            val bitmap = BitmapFactory.decodeResource(ctx.resources, R.drawable.person_image)
            viewHolder.imageAvatarView.setImageBitmap(bitmap)
        }

        viewHolder.directionsView.removeAllViews()
        var count = 1
        for (d in callRow.details) {
            if (count > 3) {
                viewHolder.etcView.text = "..."
                break
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

        val contactName = ContactsActivity.contactName(callRow.peerUri)
        if (contactName.startsWith("sip:"))
            viewHolder.peerURIView.text = Utils.friendlyUri(contactName, Utils.aorDomain(callRow.aor))
        else
            viewHolder.peerURIView.text = contactName

        viewHolder.timeView.text = Utils.relativeTime(ctx, callRow.stopTime)

        viewHolder.timeView.setOnClickListener {
            val i = Intent(ctx, CallDetailsActivity::class.java)
            val b = Bundle()
            b.putString("aor", aor)
            b.putString("peer", viewHolder.peerURIView.text!!.toString())
            b.putInt("position", position)
            i.putExtras(b)
            ctx.startActivity(i)
        }

        return rowView
    }

    private fun getBitmapFromView(view: View): Bitmap? {
        val bitmap = Bitmap.createBitmap(view.layoutParams.width, view.layoutParams.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.layout(0, 0, view.layoutParams.width, view.layoutParams.height)
        view.draw(canvas)
        return bitmap
    }

}

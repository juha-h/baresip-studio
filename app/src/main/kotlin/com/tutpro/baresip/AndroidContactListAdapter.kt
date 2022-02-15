package com.tutpro.baresip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity

class AndroidContactListAdapter(private val ctx: Context,
                                private val rows: ArrayList<AndroidContact>,
                                private val aor: String) :
        ArrayAdapter<AndroidContact>(ctx, R.layout.android_contact_row, rows) {

    private val layoutInflater = LayoutInflater.from(context)
    private var lastClick: Long = 0

    private class ViewHolder(view: View?) {
        val textAvatarView = view?.findViewById(R.id.TextAvatar) as TextView
        val imageAvatarView = view?.findViewById(R.id.ImageAvatar) as ImageView
        val nameView = view?.findViewById(R.id.contactName) as TextView
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {

        val viewHolder: ViewHolder
        val rowView: View

        if (view == null) {
            rowView = layoutInflater.inflate(R.layout.android_contact_row, parent, false)
            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            rowView = view
            viewHolder = rowView.tag as ViewHolder
        }

        val contact = rows[position]

        val thumbNailUri = contact.thumbnailUri
        if (thumbNailUri != null) {
            viewHolder.imageAvatarView.setImageURI(thumbNailUri)
        } else {
            viewHolder.textAvatarView.background.setTint(contact.color)
            if (contact.name.isNotEmpty())
                viewHolder.textAvatarView.text = "${contact.name[0]}"
            else
                viewHolder.textAvatarView.text = ""
            viewHolder.imageAvatarView.setImageBitmap(Utils.bitmapFromView(viewHolder.textAvatarView))
        }

        viewHolder.nameView.text = contact.name
        viewHolder.nameView.textSize = 20f
        viewHolder.nameView.setPadding(6, 6, 0, 6)

        viewHolder.nameView.setOnClickListener {
            if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                lastClick = SystemClock.elapsedRealtime()
                val i = Intent(ctx, AndroidContactActivity::class.java)
                val b = Bundle()
                b.putString("aor", aor)
                b.putInt("index", position)
                i.putExtras(b)
                startActivity(ctx, i, null)
            }
        }

        return rowView
    }
}

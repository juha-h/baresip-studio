package com.tutpro.baresip.plus

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity

import java.io.File
import java.io.IOException
import java.util.*

class ContactListAdapter(private val ctx: Context, private val rows: ArrayList<Contact>,
                         private val aor: String) :
        ArrayAdapter<Contact>(ctx, R.layout.contact_row, rows) {

    private val layoutInflater = LayoutInflater.from(context)
    private var lastClick: Long = 0

    private class ViewHolder(view: View?) {
        val textAvatarView = view?.findViewById(R.id.TextAvatar) as TextView
        val cardAvatarView = view?.findViewById(R.id.CardAvatar) as CardView
        val imageAvatarView = view?.findViewById(R.id.ImageAvatar) as ImageView
        val nameView = view?.findViewById(R.id.contactName) as TextView
        val actionView = view?.findViewById(R.id.edit) as ImageButton
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {

        val viewHolder: ViewHolder
        val rowView: View

        if (view == null) {
            rowView = layoutInflater.inflate(R.layout.contact_row, parent, false)
            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            rowView = view
            viewHolder = rowView.tag as ViewHolder
        }

        val contact = rows[position]

        val avatarImage = contact.avatarImage
        if (avatarImage != null) {
            viewHolder.textAvatarView.visibility = View.GONE
            viewHolder.cardAvatarView.visibility = View.VISIBLE
            viewHolder.imageAvatarView.visibility = View.VISIBLE
            viewHolder.imageAvatarView.setImageBitmap(avatarImage)
        } else {
            viewHolder.textAvatarView.visibility = View.VISIBLE
            viewHolder.cardAvatarView.visibility = View.GONE
            viewHolder.imageAvatarView.visibility = View.GONE
            viewHolder.textAvatarView.background.setTint(contact.color)
            if (contact.name.isNotEmpty())
                viewHolder.textAvatarView.text = "${contact.name[0]}"
            else
                viewHolder.textAvatarView.text = ""
        }

        viewHolder.nameView.text = contact.name
        viewHolder.nameView.textSize = 20f
        viewHolder.nameView.setPadding(6, 6, 0, 6)

        if (aor != "") {
            viewHolder.nameView.setOnClickListener {
                val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                    when (which) {
                        DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE -> {
                            val i = Intent(ctx, MainActivity::class.java)
                            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            if (which == DialogInterface.BUTTON_NEGATIVE)
                                i.putExtra("action", "call")
                            else
                                i.putExtra("action", "message")
                            val ua = UserAgent.ofAor(aor)
                            if (ua == null) {
                                Log.w(TAG, "onClickListener did not find AoR $aor")
                            } else {
                                BaresipService.activities.clear()
                                i.putExtra("uap", ua.uap)
                                i.putExtra("peer", Contact.contacts()[position].uri)
                                (ctx as Activity).startActivity(i)
                            }
                        }
                        DialogInterface.BUTTON_NEUTRAL -> {
                        }
                    }
                }
                if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                    lastClick = SystemClock.elapsedRealtime()
                    with (AlertDialog.Builder(ctx, R.style.Theme_AppCompat)) {
                        setMessage(String.format(ctx.getString(R.string.contact_action_question),
                                Contact.contacts()[position].name))
                        setNeutralButton(ctx.getText(R.string.cancel), dialogClickListener)
                        setNegativeButton(ctx.getText(R.string.call), dialogClickListener)
                        setPositiveButton(ctx.getText(R.string.send_message), dialogClickListener)
                        show()
                    }
                }
            }
        }

        viewHolder.nameView.setOnLongClickListener {
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        val id = contact.id
                        val avatarFile = File(BaresipService.filesPath, "$id.img")
                        if (avatarFile.exists()) {
                            try {
                                avatarFile.delete()
                            } catch (e: IOException) {
                                Log.e(TAG, "Could not delete file '$id.img")
                            }
                        }
                        if (contact.androidContact &&
                                Utils.checkPermissions(ctx, arrayOf(Manifest.permission.WRITE_CONTACTS)))
                            ContactActivity.deleteAndroidContact(ctx, contact)
                        Contact.contacts().removeAt(position)
                        Contact.save()
                        this.notifyDataSetChanged()
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
            val titleView = View.inflate(ctx, R.layout.alert_title, null) as TextView
            titleView.text = ctx.getString(R.string.confirmation)
            with (AlertDialog.Builder(ctx)) {
                setCustomTitle(titleView)
                setMessage(String.format(ctx.getString(R.string.contact_delete_question),
                        Contact.contacts()[position].name))
                setNegativeButton(ctx.getText(R.string.cancel), dialogClickListener)
                setPositiveButton(ctx.getText(R.string.delete), dialogClickListener)
                show()
            }
            true
        }

        viewHolder.actionView.setOnClickListener {
            if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                lastClick = SystemClock.elapsedRealtime()
                val i = Intent(ctx, ContactActivity::class.java)
                val b = Bundle()
                b.putBoolean("new", false)
                b.putInt("index", position)
                i.putExtras(b)
                startActivity(ctx, i, null)
            }
        }

        return rowView
    }
}

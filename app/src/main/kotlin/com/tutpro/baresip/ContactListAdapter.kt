package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.IOException

class ContactListAdapter(private val ctx: Context, private val rows: ArrayList<Contact>,
                         private val aor: String) :
        ArrayAdapter<Contact>(ctx, R.layout.contact_row, rows) {

    private val layoutInflater = LayoutInflater.from(context)
    private var lastClick: Long = 0

    private class ViewHolder(view: View?) {
        val textAvatarView = view?.findViewById(R.id.TextAvatar) as TextView
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

        if (contact is Contact.BaresipContact) {

            val avatarImage = contact.avatarImage
            if (avatarImage != null) {
                viewHolder.imageAvatarView.setImageBitmap(avatarImage)
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
                                    i.putExtra("peer", contact.uri)
                                    (ctx as Activity).startActivity(i)
                                }
                            }
                            DialogInterface.BUTTON_NEUTRAL -> {
                            }
                        }
                    }
                    if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                        lastClick = SystemClock.elapsedRealtime()
                        with(MaterialAlertDialogBuilder(ctx, R.style.AlertDialogTheme)) {
                            setTitle(R.string.confirmation)
                            setMessage(String.format(ctx.getString(R.string.contact_action_question),
                                    contact.name))
                            setNeutralButton(ctx.getText(R.string.cancel), dialogClickListener)
                            setNegativeButton(ctx.getText(R.string.call), dialogClickListener)
                            setPositiveButton(ctx.getText(R.string.send_message), dialogClickListener)
                            show()
                        }
                    }
                }
            }

            viewHolder.actionView.visibility = View.VISIBLE
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

        }

        if (contact is Contact.AndroidContact) {

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

            viewHolder.actionView.visibility = View.GONE

        }

        viewHolder.nameView.setOnLongClickListener {
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        when (contact) {
                            is Contact.BaresipContact -> {
                                val id = contact.id
                                val avatarFile = File(BaresipService.filesPath, "$id.img")
                                if (avatarFile.exists()) {
                                    try {
                                        avatarFile.delete()
                                    } catch (e: IOException) {
                                        Log.e(TAG, "Could not delete file '$id.img")
                                    }
                                }
                                Contact.removeBaresipContact(contact)
                                Contact.contactsUpdate()
                                this.notifyDataSetChanged()
                            }
                            is Contact.AndroidContact -> {
                                deleteAndroidContact(ctx, contact.name)
                            }
                        }
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
            with(MaterialAlertDialogBuilder(ctx, R.style.AlertDialogTheme)) {
                setTitle(R.string.confirmation)
                setMessage(String.format(ctx.getString(R.string.contact_delete_question),
                        when (contact) {
                            is Contact.BaresipContact -> contact.name
                            is Contact.AndroidContact -> contact.name
                        }))
                setNeutralButton(ctx.getText(R.string.cancel), dialogClickListener)
                setPositiveButton(ctx.getText(R.string.delete), dialogClickListener)
                show()
            }
            true
        }

        return rowView
    }

    private fun deleteAndroidContact(ctx: Context, name: String): Int {
        return ctx.contentResolver.delete(ContactsContract.RawContacts.CONTENT_URI,
                ContactsContract.Contacts.DISPLAY_NAME + "='" + name + "'",
                null)
    }

}

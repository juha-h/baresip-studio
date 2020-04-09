package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.GradientDrawable
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

import java.io.File
import java.io.IOException
import java.util.*

class ContactListAdapter(private val cxt: Context, private val rows: ArrayList<Contact>,
                         private val aor: String) :
        ArrayAdapter<Contact>(cxt, R.layout.contact_row, rows) {

    private var lastClick: Long = 0

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val contact = rows[position]
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView = inflater.inflate(R.layout.contact_row, parent, false)
        val textAvatarView = rowView.findViewById(R.id.TextAvatar) as TextView
        val cardAvatarView = rowView.findViewById(R.id.CardAvatar) as CardView
        val imageAvatarView = rowView.findViewById(R.id.ImageAvatar) as ImageView
        val avatarImage = contact.avatarImage
        if (avatarImage != null) {
            textAvatarView.visibility = View.GONE
            cardAvatarView.visibility = View.VISIBLE
            imageAvatarView.visibility = View.VISIBLE
            imageAvatarView.setImageBitmap(avatarImage)
        } else {
            textAvatarView.visibility = View.VISIBLE
            cardAvatarView.visibility = View.GONE
            imageAvatarView.visibility = View.GONE
            (textAvatarView.background as GradientDrawable).setColor(contact.color)
            if (contact.name.isNotEmpty())
                textAvatarView.text = "${contact.name[0]}"
            else
                textAvatarView.text = ""
        }
        val nameView = rowView.findViewById(R.id.contactName) as TextView
        nameView.text = contact.name
        nameView.textSize = 20f
        nameView.setPadding(6, 6, 0, 6)
        if (aor != "") {
            nameView.setOnClickListener { _ ->
                val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                    when (which) {
                        DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE -> {
                            val i = Intent(cxt, MainActivity::class.java)
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            if (which == DialogInterface.BUTTON_NEGATIVE)
                                i.putExtra("action", "call")
                            else
                                i.putExtra("action", "message")
                            val ua = Account.findUa(aor)
                            if (ua == null) {
                                Log.w("Baresip", "onClickListener did not find AoR $aor")
                            } else {
                                BaresipService.activities.clear()
                                i.putExtra("uap", ua.uap)
                                i.putExtra("peer", Contact.contacts()[position].uri)
                                (cxt as Activity).startActivity(i)
                            }
                        }
                        DialogInterface.BUTTON_NEUTRAL -> {
                        }
                    }
                }
                if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                    lastClick = SystemClock.elapsedRealtime()
                    val builder = AlertDialog.Builder(cxt, R.style.Theme_AppCompat)
                    builder.setMessage(String.format(cxt.getString(R.string.contact_action_question),
                            Contact.contacts()[position].name))
                            .setNeutralButton(cxt.getText(R.string.cancel), dialogClickListener)
                            .setNegativeButton(cxt.getText(R.string.call), dialogClickListener)
                            .setPositiveButton(cxt.getText(R.string.send_message), dialogClickListener)
                            .show()
                }
            }
        }
        nameView.setOnLongClickListener { _ ->
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        val id = contact.id
                        val avatarFile = File(BaresipService.filesPath, "$id.img")
                        if (avatarFile.exists()) {
                            try {
                                avatarFile.delete()
                            } catch (e: IOException) {
                                Log.e("Baresip", "Could not delete file '$id.img")
                            }
                        }
                        Contact.contacts().removeAt(position)
                        Contact.save()
                        this.notifyDataSetChanged()
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
            val builder = AlertDialog.Builder(cxt, R.style.Theme_AppCompat)
            builder.setMessage(String.format(cxt.getString(R.string.contact_delete_question),
                    Contact.contacts()[position].name))
                    .setNegativeButton(cxt.getText(R.string.cancel), dialogClickListener)
                    .setPositiveButton(cxt.getText(R.string.delete), dialogClickListener)
                    .show()
            true
        }
        val actionView = rowView.findViewById(R.id.edit) as ImageButton
        actionView.setOnClickListener { _ ->
            if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                lastClick = SystemClock.elapsedRealtime()
                val i = Intent(cxt, ContactActivity::class.java)
                val b = Bundle()
                b.putBoolean("new", false)
                b.putInt("index", position)
                i.putExtras(b)
                (cxt as Activity).startActivityForResult(i, MainActivity.CONTACT_CODE)
            }
        }
        return rowView
    }
}

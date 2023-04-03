package com.tutpro.baresip

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.DateUtils.isToday
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.*

class MessageListAdapter(private val ctx: Context, private val peerUri: String,
                         private val chatPeer: String, private val rows: ArrayList<Message>) :
        ArrayAdapter<Message>(ctx, R.layout.message, rows) {

    private val layoutInflater = LayoutInflater.from(context)

    private class ViewHolder(view: View?) {
        val layoutView = view?.findViewById(R.id.message) as LinearLayout
        val infoView = view?.findViewById(R.id.info) as TextView
        val textView = view?.findViewById(R.id.text) as TextView
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {

        val viewHolder: ViewHolder
        val rowView: View

        if (view == null) {
            rowView = layoutInflater.inflate(R.layout.message, parent, false)
            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            rowView = view
            viewHolder = rowView.tag as ViewHolder
        }

        val listener = View.OnClickListener {
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        val i = Intent(ctx, ContactActivity::class.java)
                        val b = Bundle()
                        b.putBoolean("new", true)
                        b.putString("uri", peerUri)
                        i.putExtras(b)
                        ctx.startActivity(i)
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                        Message.messages().remove(rows[position])
                        Message.save()
                        rows.removeAt(position)
                        this.notifyDataSetChanged()
                        //if (rows.size == 0) {
                        //    listView.removeFooterView(footerView)
                        //}
                    }
                    DialogInterface.BUTTON_NEUTRAL -> {
                    }
                }
            }
            val builder = MaterialAlertDialogBuilder(ctx, R.style.AlertDialogTheme)
            if (chatPeer == peerUri)
                with (builder) {
                    setTitle(R.string.confirmation)
                    setMessage(String.format(ctx.getString(R.string.long_message_question),
                            peerUri))
                    setNeutralButton(ctx.getString(R.string.cancel), dialogClickListener)
                    setNegativeButton(ctx.getString(R.string.delete), dialogClickListener)
                    setPositiveButton(ctx.getString(R.string.add_contact), dialogClickListener)
                    show()
                }
            else
                with (builder) {
                    setTitle(R.string.confirmation)
                    setMessage(ctx.getText(R.string.short_message_question))
                    setNeutralButton(ctx.getString(R.string.cancel), dialogClickListener)
                    setNegativeButton(ctx.getString(R.string.delete), dialogClickListener)
                    show()
                }
        }

        viewHolder.layoutView.setOnClickListener(listener)
        viewHolder.infoView.setOnClickListener(listener)
        viewHolder.textView.setOnClickListener(listener)

        val message = rows[position]

        val down = message.direction == MESSAGE_DOWN
        val lp = viewHolder.layoutView.layoutParams as LinearLayout.LayoutParams
        val peer: String = if (down) {
            lp.setMargins(0, 10, 75, 10)
            viewHolder.layoutView.setBackgroundResource(R.drawable.message_in_bg)
            if (chatPeer.startsWith("sip:") &&
                    (Utils.uriHostPart(message.peerUri) == Utils.uriHostPart(message.aor)))
                Utils.uriUserPart(message.peerUri)
            else
                chatPeer
        } else {
            lp.setMargins(75, 10, 0, 10)
            viewHolder.layoutView.setBackgroundResource(R.drawable.message_out_bg)
            ctx.getString(R.string.you)
        }

        viewHolder.layoutView.layoutParams = lp

        var info: String
        val cal = GregorianCalendar()
        cal.timeInMillis = message.timeStamp
        val fmt: DateFormat = if (isToday(message.timeStamp))
            DateFormat.getTimeInstance(DateFormat.SHORT)
        else
            DateFormat.getDateInstance(DateFormat.SHORT)
        info = fmt.format(cal.time)
        if (info.length < 6) info = "${ctx.getString(R.string.today)} $info"
        info = "$info - $peer"
        if (message.direction == MESSAGE_UP_FAIL) {
            info = if (message.responseCode != 0)
                "$info - ${ctx.getString(R.string.message_failed)}: " + "${message.responseCode} ${message.responseReason}"
            else
                "$info - ${ctx.getString(R.string.sending_failed)}"
            viewHolder.infoView.setTextColor(ContextCompat.getColor(ctx, R.color.colorAccent))
        }
        viewHolder.infoView.text = info

        viewHolder.textView.text = message.message
        if (message.new)
            viewHolder.textView.setTypeface(null, Typeface.BOLD)

        return rowView
    }

}

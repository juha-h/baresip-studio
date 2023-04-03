package com.tutpro.baresip

import android.content.Context
import android.graphics.Typeface
import android.text.format.DateUtils.isToday
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.*

class ChatListAdapter(private val ctx: Context, private val account: Account, private val rows: ArrayList<Message>) :
        ArrayAdapter<Message>(ctx, R.layout.message, rows) {

    private val layoutInflater = LayoutInflater.from(context)

    private class ViewHolder(view: View?) {
        val textAvatarView = view?.findViewById(R.id.TextAvatar) as TextView
        val imageAvatarView = view?.findViewById(R.id.ImageAvatar) as ImageView
        val layoutView = view?.findViewById(R.id.chat) as LinearLayout
        val peerView = view?.findViewById(R.id.peer) as TextView
        val infoView = view?.findViewById(R.id.info) as TextView
        val textView = view?.findViewById(R.id.text) as TextView
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {

        val viewHolder: ViewHolder
        val rowView: View

        if (view == null) {
            rowView = layoutInflater.inflate(R.layout.chat_row, parent, false)
            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            rowView = view
            viewHolder = rowView.tag as ViewHolder
        }

        val message = rows[position]

        viewHolder.peerView.text = Utils.friendlyUri(ctx, message.peerUri, account)
        Utils.setAvatar(ctx, viewHolder.imageAvatarView, viewHolder.textAvatarView,
            if (Contact.contactName(message.peerUri) == message.peerUri)
                Utils.e164Uri(message.peerUri, account.countryCode)
            else
                message.peerUri)

        if (message.direction == MESSAGE_DOWN)
            viewHolder.layoutView.setBackgroundResource(R.drawable.message_in_bg)
        else
            viewHolder.layoutView.setBackgroundResource(R.drawable.message_out_bg)

        val cal = GregorianCalendar()
        cal.timeInMillis = message.timeStamp
        val fmt: DateFormat = if (isToday(message.timeStamp))
            DateFormat.getTimeInstance(DateFormat.SHORT)
        else
            DateFormat.getDateInstance(DateFormat.SHORT)
        viewHolder.infoView.text = fmt.format(cal.time)
        if (message.direction == MESSAGE_UP_FAIL) {
            val info: String = if (message.responseCode != 0)
                "${viewHolder.infoView.text} - ${ctx.getString(R.string.message_failed)}: " +
                        "${message.responseCode} ${message.responseReason}"
            else
                "${viewHolder.infoView.text} - ${ctx.getString(R.string.sending_failed)}"
            viewHolder.infoView.text = info
            viewHolder.infoView.setTextColor(ContextCompat.getColor(ctx, R.color.colorAccent))
        }

        viewHolder.textView.text = message.message
        if (message.new)
            viewHolder.textView.setTypeface(null, Typeface.BOLD)

        return rowView
    }

}

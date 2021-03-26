package com.tutpro.baresip

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import android.text.format.DateUtils.isToday
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*

import java.text.DateFormat
import java.util.*

class MessageListAdapter(private val ctx: Context, private val rows: ArrayList<Message>) :
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

        val message = rows[position]

        val down = (message.direction == R.drawable.arrow_down_green) ||
            (message.direction == R.drawable.arrow_down_red)
        val lp = viewHolder.layoutView.layoutParams as LinearLayout.LayoutParams
        val peer: String = if (down) {
            lp.setMargins(0, 10, 75, 10)
            viewHolder.layoutView.setBackgroundResource(R.drawable.message_in_bg)
            val contactName = ContactsActivity.contactName(message.peerUri)
            if (contactName.startsWith("sip:") &&
                    (Utils.uriHostPart(message.peerUri) == Utils.uriHostPart(message.aor)))
                Utils.uriUserPart(message.peerUri)
            else
                contactName
        } else {
            lp.setMargins(75, 10, 0, 10)
            viewHolder.layoutView.setBackgroundResource(R.drawable.message_out_bg)
            ctx.getString(R.string.you)
        }

        viewHolder.layoutView.layoutParams = lp

        var info: String
        val cal = GregorianCalendar()
        cal.timeInMillis = message.timeStamp
        val fmt: DateFormat
        if (isToday(message.timeStamp))
            fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        else
            fmt = DateFormat.getDateInstance(DateFormat.SHORT)
        info = fmt.format(cal.time)
        if (info.length < 6) info = "${ctx.getString(R.string.today)} $info"
        info = "$info - $peer"
        if (message.direction == R.drawable.arrow_up_red) {
            if (message.responseCode != 0)
                info = "$info - ${ctx.getString(R.string.message_failed)}: " + "${message.responseCode} ${message.responseReason}"
            else
                info = "$info - ${ctx.getString(R.string.sending_failed)}"
            viewHolder.infoView.setTextColor(ContextCompat.getColor(ctx, R.color.colorAccent))
        }
        viewHolder.infoView.text = info

        viewHolder.textView.text = message.message
        if (message.new)
            viewHolder.textView.setTypeface(null, Typeface.BOLD)

        return rowView
    }

}

package com.tutpro.baresip

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.support.v4.content.ContextCompat
import android.text.format.DateUtils.isToday
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView

import java.util.*
import java.text.DateFormat

class ChatListAdapter(private val cxt: Context, private var rows: ArrayList<Message>) :
        ArrayAdapter<Message>(cxt, R.layout.message, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val message = rows[position]
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView = inflater.inflate(R.layout.chat_row, parent, false)
        val avatarView = rowView.findViewById(R.id.avatar) as TextView
        val avatarBackground = avatarView.background as GradientDrawable
        val layout = rowView.findViewById(R.id.chat) as LinearLayout
        val lp = layout.layoutParams as RelativeLayout.LayoutParams
        lp.setMargins(10, 10, 10, 10)
        val peer: String
        val contact = ContactsActivity.findContact(message.peerUri)
        if (contact != null) {
            peer = contact.name
            avatarBackground.setColor(contact.color)
        } else {
            if (Utils.uriHostPart(message.peerUri) == Utils.uriHostPart(message.aor))
                peer = Utils.uriUserPart(message.peerUri)
            else
                peer = Utils.uriAor(message.peerUri)
            avatarBackground.setColor(Color.RED)
        }
        avatarView.text = "${peer[0]}"
        if (message.direction == R.drawable.arrow_down_green) {
            layout.setBackgroundResource(R.drawable.message_in_bg)
        } else {
            layout.setBackgroundResource(R.drawable.message_out_bg)
        }
        val peerView = rowView.findViewById(R.id.peer) as TextView
        peerView.setText(peer)
        val infoView = rowView.findViewById(R.id.info) as TextView
        val cal = GregorianCalendar()
        cal.timeInMillis = message.timeStamp
        val fmt: DateFormat
        if (isToday(message.timeStamp))
            fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        else
            fmt = DateFormat.getDateInstance(DateFormat.SHORT)
        infoView.text = fmt.format(cal.time)
        if (message.direction == R.drawable.arrow_up_red) {
            val info: String
            if (message.responseCode != 0)
                info = "${infoView.text} - ${cxt.getString(R.string.message_failed)}: " +
                    "${message.responseCode} ${message.responseReason}"
            else
                info = "${infoView.text} - ${cxt.getString(R.string.sending_failed)}"
            infoView.text = info
            infoView.setTextColor(ContextCompat.getColor(cxt, R.color.colorAccent))
        }
        val textView = rowView.findViewById(R.id.text) as TextView
        textView.text = message.message
        if (message.new)
            textView.setTypeface(null, Typeface.BOLD)
        return rowView
    }

}

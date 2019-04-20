package com.tutpro.baresip

import android.content.Context
import android.graphics.Typeface
import android.support.v4.content.ContextCompat
import android.text.format.DateUtils.isToday
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*

import java.text.SimpleDateFormat
import java.util.*


class MessageListAdapter(private val cxt: Context, private val rows: ArrayList<Message>) :
        ArrayAdapter<Message>(cxt, R.layout.message, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val message = rows[position]
        val up = (message.direction == R.drawable.arrow_up_green) ||
            (message.direction == R.drawable.arrow_up_red)
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val messageView = inflater.inflate(R.layout.message, parent, false)
        val layout = messageView.findViewById(R.id.message) as LinearLayout
        val lp = layout.layoutParams as LinearLayout.LayoutParams
        val peer: String
        if (up) {
            lp.setMargins(75, 10, 0, 10)
            layout.setBackgroundResource(R.drawable.you_bg)
            peer = "You"
        } else {
            lp.setMargins(0, 10, 75, 10)
            layout.setBackgroundResource(R.drawable.peer_bg)
            val contactName = ContactsActivity.contactName(message.peerUri)
            if (contactName.startsWith("sip:") &&
                    (Utils.uriHostPart(message.peerUri) == Utils.uriHostPart(message.aor)))
                peer = Utils.uriUserPart(message.peerUri)
            else
                peer = contactName
        }
        layout.layoutParams = lp
        val infoView = messageView.findViewById(R.id.info) as TextView
        var info: String
        val cal = GregorianCalendar()
        cal.timeInMillis = message.timeStamp
        if (isToday(message.timeStamp)) {
            val fmt = SimpleDateFormat("HH:mm")
            info = fmt.format(cal.time)
        } else {
            val fmt = SimpleDateFormat("dd/MM, HH:mm")
            info = fmt.format(cal.time)
        }
        if (info.length < 6) info = "Today $info"
        infoView.text = "$info - $peer"
        if (message.direction == R.drawable.arrow_up_red) {
            if (message.responseCode != 0)
                infoView.text = "${infoView.text} - Failed: ${message.responseCode} " +
                    "${message.responseReason}"
            else
                infoView.text = "${infoView.text} - Sending of message failed!"
            infoView.setTextColor(ContextCompat.getColor(cxt, R.color.colorAccent))
        }
        val textView = messageView.findViewById(R.id.text) as TextView
        textView.text = message.message
        if (message.new)
            textView.setTypeface(null, Typeface.BOLD)
        return messageView
    }

}

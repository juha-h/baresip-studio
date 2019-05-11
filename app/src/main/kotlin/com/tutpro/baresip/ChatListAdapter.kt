package com.tutpro.baresip

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.support.v4.content.ContextCompat
import android.text.format.DateUtils.isToday
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView

import java.text.SimpleDateFormat
import java.util.*

class ChatListAdapter(private val cxt: Context, private var rows: ArrayList<Message>) :
        ArrayAdapter<Message>(cxt, R.layout.message, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val message = rows[position]
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val chatView = inflater.inflate(R.layout.chat, parent, false)
        val layout = chatView.findViewById(R.id.chat) as LinearLayout
        val lp = layout.layoutParams as LinearLayout.LayoutParams
        lp.setMargins(10, 10, 10, 10)
        val peer: String
        val sender: String
        val contactName = ContactsActivity.contactName(message.peerUri)
        if (contactName.startsWith("sip:") &&
                (Utils.uriHostPart(message.peerUri) == Utils.uriHostPart(message.aor)))
            peer = Utils.uriUserPart(message.peerUri)
        else
            peer = contactName
        if (message.direction == R.drawable.arrow_down_green) {
            layout.setBackgroundResource(R.drawable.peer_bg)
            sender = peer
        } else {
            layout.setBackgroundResource(R.drawable.you_bg)
            sender = cxt.getString(R.string.you)
        }
        val peerView = chatView.findViewById(R.id.peer) as TextView
        peerView.setText(peer)
        val infoView = chatView.findViewById(R.id.info) as TextView
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
        if (info.length < 6) info = "${cxt.getString(R.string.today)} $info"
        infoView.text = "$info - $sender"
        if (message.direction == R.drawable.arrow_up_red) {
            if (message.responseCode != 0)
                infoView.text = "${infoView.text} - ${cxt.getString(R.string.message_failed)}: " +
                        "${message.responseCode} ${message.responseReason}"
            else
                infoView.text = "${infoView.text} - ${cxt.getString(R.string.sending_failed)}"
            infoView.setTextColor(ContextCompat.getColor(cxt, R.color.colorAccent))
        }
        val textView = chatView.findViewById(R.id.text) as TextView
        textView.text = message.message
        if (message.new)
            textView.setTypeface(null, Typeface.BOLD)
        return chatView
    }

}

package com.tutpro.baresip

import android.content.Context
import android.graphics.Typeface
import android.text.format.DateUtils.isToday
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class MessageListAdapter(private val cxt: Context, private val rows: ArrayList<Message>) :
        ArrayAdapter<Message>(cxt, R.layout.message, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val message = rows[position]
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val messageView = inflater.inflate(R.layout.message, parent, false)
        val directionView = messageView.findViewById(R.id.direction) as ImageView
        directionView.setImageResource(message.direction)
        val peerView = messageView.findViewById(R.id.peer) as TextView
        peerView.text = ContactsActivity.contactName(message.peerURI)
        val timeView = messageView.findViewById(R.id.time) as TextView
        val time: String
        val cal = GregorianCalendar()
        cal.timeInMillis = message.timeStamp
        if (isToday(message.timeStamp)) {
            val fmt = SimpleDateFormat("HH:mm")
            time = fmt.format(cal.time)
        } else {
            val fmt = SimpleDateFormat("dd.MM")
            time = fmt.format(cal.time)
        }
        timeView.text = time
        val textView = messageView.findViewById(R.id.text) as TextView
        textView.text = message.message
        if (message.new) {
            textView.setTypeface(null, Typeface.BOLD)
            message.new = false
        }
        return messageView
    }

}

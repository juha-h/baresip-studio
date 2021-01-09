package com.tutpro.baresip.plus

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
            if (Utils.darkTheme(ctx))
                layout.setBackgroundResource(R.drawable.message_out_dark_bg)
            else
                 layout.setBackgroundResource(R.drawable.message_out_bg)
            peer = ctx.getString(R.string.you)
        } else {
            lp.setMargins(0, 10, 75, 10)
            if (Utils.darkTheme(ctx))
                layout.setBackgroundResource(R.drawable.message_in_dark_bg)
            else
                layout.setBackgroundResource(R.drawable.message_in_bg)
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
            infoView.setTextColor(ContextCompat.getColor(ctx, R.color.colorAccent))
        }
        infoView.text = info
        val textView = messageView.findViewById(R.id.text) as TextView
        textView.text = message.message
        if (message.new)
            textView.setTypeface(null, Typeface.BOLD)
        return messageView
    }

}

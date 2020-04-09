package com.tutpro.baresip

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.cardview.widget.CardView
import android.text.format.DateUtils.isToday
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*

import java.util.*
import java.text.DateFormat

class ChatListAdapter(private val cxt: Context, private var rows: ArrayList<Message>) :
        ArrayAdapter<Message>(cxt, R.layout.message, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val message = rows[position]
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView = inflater.inflate(R.layout.chat_row, parent, false)
        val textAvatarView = rowView.findViewById(R.id.TextAvatar) as TextView
        val cardAvatarView = rowView.findViewById(R.id.CardAvatar) as CardView
        val cardImageAvatarView = rowView.findViewById(R.id.ImageAvatar) as ImageView
        val layout = rowView.findViewById(R.id.chat) as LinearLayout
        val contact = ContactsActivity.findContact(message.peerUri)
        val peer: String
        if (contact != null) {
            peer = contact.name
            val avatarImage = contact.avatarImage
            if (avatarImage != null) {
                textAvatarView.visibility = View.GONE
                cardAvatarView.visibility = View.VISIBLE
                cardImageAvatarView.setImageBitmap(avatarImage)
            } else {
                textAvatarView.visibility = View.VISIBLE
                cardAvatarView.visibility = View.GONE
                (textAvatarView.background as GradientDrawable).setColor(contact.color)
                textAvatarView.text = "${peer[0]}"
            }
        } else {
            peer = Utils.friendlyUri(message.peerUri, message.aor)
            textAvatarView.visibility = View.VISIBLE
            cardAvatarView.visibility = View.GONE
            textAvatarView.setBackgroundResource(R.drawable.person_image)
        }
        if ((message.direction == R.drawable.arrow_down_green) ||
                (message.direction == R.drawable.arrow_down_red)) {
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

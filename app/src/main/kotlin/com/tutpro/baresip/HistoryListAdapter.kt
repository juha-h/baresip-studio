package com.tutpro.baresip

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.*

class HistoryListAdapter(private val cxt: Context, private val rows: ArrayList<HistoryRow>) :
        ArrayAdapter<HistoryRow>(cxt, R.layout.history_row, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = rows[position]
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView = inflater.inflate(R.layout.history_row, parent, false)
        val directions = rowView.findViewById(R.id.directions) as LinearLayout
        var count = 1
        for (d in row.directions) {
            if (count > 3) {
                val etc = rowView.findViewById(R.id.etc) as TextView
                etc.text = "..."
                break
            }
            val dirView = ImageView(cxt)
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            dirView.layoutParams = params
            dirView.setPadding(0, 5, 0, 0)
            dirView.setImageResource(d)
            directions.addView(dirView)
            count++
        }
        val peerURIView = rowView.findViewById(R.id.peer_uri) as TextView
        peerURIView.text = row.peerURI
        val timeView = rowView.findViewById(R.id.time) as TextView
        timeView.text = row.time
        return rowView
    }

}

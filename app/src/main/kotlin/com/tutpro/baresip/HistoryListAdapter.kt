package com.tutpro.baresip

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

import java.util.ArrayList

class HistoryListAdapter(private val cxt: Context, private val rows: ArrayList<HistoryRow>) :
        ArrayAdapter<HistoryRow>(cxt, R.layout.history_row, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = rows[position]
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView = inflater.inflate(R.layout.history_row, parent, false)
        val directionView = rowView.findViewById(R.id.direction) as ImageView
        directionView.setImageResource(row.direction)
        val peerURIView = rowView.findViewById(R.id.peer_uri) as TextView
        peerURIView.text = row.peerURI
        val timeView = rowView.findViewById(R.id.time) as TextView
        timeView.text = row.time
        return rowView
    }

}
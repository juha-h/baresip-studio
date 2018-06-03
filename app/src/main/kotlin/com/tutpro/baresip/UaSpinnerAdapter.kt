package com.tutpro.baresip

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

import java.util.ArrayList

class UaSpinnerAdapter(private val cxt: Context, private val uas: ArrayList<UserAgent>,
                            private val images: ArrayList<Int>) :
        ArrayAdapter<Int>(cxt, android.R.layout.simple_spinner_item, images) {

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getImageForPosition(position, parent)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getImageForPosition(position, parent)
    }

    private fun getImageForPosition(position: Int, parent: ViewGroup): View {
        val inflater = cxt.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val row = inflater.inflate(R.layout.account_spinner, parent, false)
        val textView = row.findViewById(R.id.spinnerText) as TextView
        textView.text = uas[position].account.aor.replace("sip:", "")
        textView.textSize = 17f
        val imageView = row.findViewById(R.id.spinnerImage) as ImageView
        imageView.setImageResource(images[position])
        return row
    }

}

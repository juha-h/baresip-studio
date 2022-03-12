package com.tutpro.baresip.plus

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

import java.util.ArrayList

class UaSpinnerAdapter(cxt: Context, private val uas: ArrayList<UserAgent>,
                       private val images: ArrayList<Int>) :
        ArrayAdapter<Int>(cxt, android.R.layout.simple_spinner_item, images) {

    private val layoutInflater = LayoutInflater.from(context)

    private class ViewHolder(view: View?) {
        val textView = view?.findViewById(R.id.spinnerText) as TextView
        val imageView = view?.findViewById(R.id.spinnerImage) as ImageView
    }

    override fun getDropDownView(position: Int, view: View?, parent: ViewGroup): View {
        return getImageForPosition(position, view, parent)
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        return getImageForPosition(position, view, parent)
    }

    private fun getImageForPosition(position: Int, view: View?, parent: ViewGroup): View {

        val viewHolder: ViewHolder
        val rowView: View

        if (view == null) {
            rowView = layoutInflater.inflate(R.layout.account_spinner, parent, false)
            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            rowView = view
            viewHolder = rowView.tag as ViewHolder
        }

        val ua = uas[position]
        viewHolder.textView.text = ua.account.aor.split(":")[1]
        viewHolder.textView.textSize = 17f
        if (UserAgent.uas().size > 1 && ua.calls().isNotEmpty())
            viewHolder.textView.setTypeface(null, Typeface.BOLD)
        viewHolder.imageView.setImageResource(images[position])

        return rowView
    }

}

package com.tutpro.baresip

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.media.MediaPlayer
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.text.SimpleDateFormat

import java.util.*

class CallDetailsAdapter(private val ctx: Context, private val rows: ArrayList<CallRow.Details>,
        private val decPlayer: MediaPlayer, private val encPlayer: MediaPlayer) :
            ArrayAdapter<CallRow.Details>(ctx, R.layout.call_detail_row, rows) {

    private val layoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    private class ViewHolder(view: View?) {
        val directionView = view?.findViewById(R.id.direction) as ImageView
        val timeView = view?.findViewById(R.id.time) as TextView
        val durationView = view?.findViewById(R.id.duration) as TextView
    }

    @SuppressLint("SimpleDateFormat")
    override fun getView(position: Int, view: View?, parent: ViewGroup): View {

        val viewHolder: ViewHolder
        val rowView: View

        if (view == null) {
            rowView = layoutInflater.inflate(R.layout.call_detail_row, parent, false)
            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            rowView = view
            viewHolder = rowView.tag as ViewHolder
        }

        val direction = rows[position].direction
        viewHolder.directionView.setImageResource(direction)

        val startTime = rows[position].startTime
        val stopTime = rows[position].stopTime

        val stopText = if (DateUtils.isToday(stopTime.timeInMillis)) {
            val fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM)
            ctx.getString(R.string.today) + " " + fmt.format(stopTime.time)
        } else {
            val fmt = DateFormat.getDateTimeInstance()
            fmt.format(stopTime.time)
        }
        if (startTime == GregorianCalendar(0, 0, 0)) {
            viewHolder.timeView.text = stopText
            viewHolder.durationView.text = "?"
        } else {
            if (startTime == null) {
                viewHolder.timeView.text = stopText
                viewHolder.durationView.text = ""
            } else {
                val startText = if (DateUtils.isToday(startTime.timeInMillis)) {
                    val fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM)
                    ctx.getString(R.string.today) + " " + fmt.format(startTime.time)
                } else {
                    val fmt = DateFormat.getDateTimeInstance()
                    fmt.format(startTime.time)
                }
                viewHolder.timeView.text = startText
                val duration = (stopTime.time.time - startTime.time.time) / 1000
                viewHolder.durationView.text = DateUtils.formatElapsedTime(duration)
                val recording = rows[position].recording
                if (recording[0] != "") {
                    viewHolder.durationView.typeface = Typeface.DEFAULT_BOLD
                    viewHolder.durationView.setOnClickListener {
                        if (!decPlayer.isPlaying && !encPlayer.isPlaying) {
                            decPlayer.reset()
                            encPlayer.reset()
                            Utils.playRecording(ctx, viewHolder.durationView, recording, decPlayer, encPlayer)
                        } else if (decPlayer.isPlaying && encPlayer.isPlaying) {
                            decPlayer.stop()
                            encPlayer.stop()
                            viewHolder.durationView.setTextColor(ContextCompat.getColor(ctx, R.color.colorItemText))
                        }
                    }
                }
            }
        }

        return rowView
    }
}

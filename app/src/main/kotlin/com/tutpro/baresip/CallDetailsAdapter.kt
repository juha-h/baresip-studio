package com.tutpro.baresip

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.text.DateFormat

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
                            playRecording(ctx, viewHolder.durationView, recording)
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

    private fun playRecording(ctx: Context, textView: TextView, recording: Array<String>) {
        Log.d(TAG, "Playing recordings ${recording[0]} and ${recording[1]}")
        decPlayer.apply {
            setAudioAttributes(
                    AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            )
            setOnPreparedListener {
                encPlayer.apply {
                    setAudioAttributes(
                            AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                    )
                    setOnPreparedListener {
                        it.start()
                        decPlayer.start()
                        textView.setTextColor(ContextCompat.getColor(ctx, R.color.colorAccent))
                        Log.d(TAG, "Started players")
                    }
                    setOnCompletionListener {
                        Log.d(TAG, "Stopping encPlayer")
                        it.stop()
                        textView.setTextColor(ContextCompat.getColor(ctx, R.color.colorItemText))
                    }
                    try {
                        val file = recording[0]
                        val encFile = File(file).copyTo(File(BaresipService.filesPath + "/tmp/encode.wav"), true)
                        val encUri = encFile.toUri()
                        setDataSource(ctx, encUri)
                        prepareAsync()
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "encPlayer IllegalArgumentException: $e")
                    } catch (e: IOException) {
                        Log.e(TAG, "encPlayer IOException: $e")
                    } catch (e: Exception) {
                        Log.e(TAG, "encPlayer Exception: $e")
                    }
                }
            }
            setOnCompletionListener {
                Log.d(TAG, "Stopping decPlayer")
                it.stop()
            }
            try {
                val file = recording[1]
                val decFile = File(file).copyTo(File(BaresipService.filesPath + "/tmp/decode.wav"), true)
                val decUri = decFile.toUri()
                setDataSource(ctx, decUri)
                prepareAsync()
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "decPlayer IllegalArgumentException: $e")
            } catch (e: IOException) {
                Log.e(TAG, "decPlayer IOException: $e")
            } catch (e: Exception) {
                Log.e(TAG, "decPlayer Exception: $e")
            }
        }
        return
    }

}

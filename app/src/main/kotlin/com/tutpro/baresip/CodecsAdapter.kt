package com.tutpro.baresip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class CodecsAdapter(private val codecs: ArrayList<Codec>):
        RecyclerView.Adapter<CodecsAdapter.CodecViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CodecViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.codec, parent, false)
        return CodecViewHolder(v)
    }

    override fun getItemCount(): Int {
        return codecs.size
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val codec: Codec = codecs[fromPosition]
        codec.enabled = codecs[toPosition].enabled
        codecs.removeAt(fromPosition)
        codecs.add(toPosition, codec)
    }

    fun enableItem(position: Int) {
        val codec: Codec = codecs[position]
        codec.enabled = true
        codecs.removeAt(position)
        codecs.add(0, codec)
    }

    fun disableItem(position: Int) {
        val codec: Codec = codecs[position]
        if (codec.enabled) {
            codec.enabled = false
            codecs.removeAt(position)
            codecs.add(codec)
        }
    }

    override fun onBindViewHolder(holder: CodecViewHolder, position: Int) {
        val codec = codecs[position]
        holder.codecName.text = codec.name
        holder.codecName.alpha = if (codec.enabled) 1.0f else 0.5f
        holder.reorderImage.setImageResource(
                if (codec.enabled) R.drawable.reorder else android.R.color.transparent
        )
    }

    class CodecViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        var codecName: TextView = itemView.findViewById(R.id.codecName)
        var reorderImage: ImageView = itemView.findViewById(R.id.reorderImage)
    }
}

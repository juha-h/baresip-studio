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

    private var codecListener: OnItemClickListener? = null

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        codecListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CodecViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.codec, parent, false)
        return CodecViewHolder(v, codecListener!!)
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

    fun removeItem(position: Int) {
        val codec: Codec = codecs[position]
        if (codec.enabled) {
            codec.enabled = false
            codecs.removeAt(position)
            codecs.add(codec)
        }
    }

    fun enableItem(position: Int) {
        val codec: Codec = codecs[position]
        codec.enabled = true
        codecs.removeAt(position)
        codecs.add(0, codec)
    }

    override fun onBindViewHolder(holder: CodecViewHolder, position: Int) {
        val codec = codecs[position]
        holder.codecName.text = codec.name
        holder.codecName.alpha = if (codec.enabled) 1.0f else 0.5f
        holder.actionImage.setImageResource(
                if (codec.enabled) R.drawable.reorder else R.drawable.add
        )
    }

    class CodecViewHolder(itemView: View, listener: OnItemClickListener):
            RecyclerView.ViewHolder(itemView) {
        var codecName: TextView = itemView.findViewById(R.id.codecName)
        var actionImage: ImageView = itemView.findViewById(R.id.actionImage)
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(position)
                }
            }
        }
    }
}

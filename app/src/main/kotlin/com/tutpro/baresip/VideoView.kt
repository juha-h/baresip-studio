package com.tutpro.baresip

import android.content.Context
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

class VideoView(val context: Context) {

    external fun set_surface(surface: Surface?)
    external fun on_start()
    external fun on_resume()
    external fun on_pause()
    external fun on_stop()

    val surfaceHolderCallback: SurfaceHolderCallback?
    val surfaceView: View

    init {
        surfaceHolderCallback = SurfaceHolderCallback()
        surfaceView = View(surfaceHolderCallback, context)
    }

    inner class View: SurfaceView {
        constructor(callback: SurfaceHolder.Callback, context: Context) : super(context) {
            holder.addCallback(callback)
        }

        constructor(callback: SurfaceHolder.Callback, context: Context, attrs: AttributeSet) :
                super(context, attrs) {
            holder.addCallback(callback)
        }

        constructor(callback: SurfaceHolder.Callback, context: Context, attrs: AttributeSet, defStyle: Int) :
                super(context, attrs, defStyle) {
            holder.addCallback(callback)
        }

        constructor(callback: SurfaceHolder.Callback, context: Context, attrs: AttributeSet, defStyle: Int, defStyleRes: Int) :
                super(context, attrs, defStyle, defStyleRes) {
            holder.addCallback(callback)
        }
    }

    inner class SurfaceHolderCallback: SurfaceHolder.Callback {

        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d("Baresip", "Surface created")
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            Log.d("Baresip", "Surface changed")
            set_surface(holder.surface)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d("Baresip", "Surface destroyed")
            set_surface(null)
        }

    }
}

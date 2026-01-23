package com.tutpro.baresip.plus

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

    private val surfaceHolderCallback: SurfaceHolderCallback?
    val surfaceView: View
    var afterCreate = false

    init {
        surfaceHolderCallback = SurfaceHolderCallback()
        surfaceView = View(surfaceHolderCallback, context)
    }

    class View @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : SurfaceView(context, attrs, defStyleAttr) {
        constructor(callback: SurfaceHolder.Callback, context: Context) : this(context) {
            holder.addCallback(callback)
        }
    }

    inner class SurfaceHolderCallback: SurfaceHolder.Callback {

        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, "Surface created")
            afterCreate = true
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            Log.d(TAG, "Surface changed")
            set_surface(holder.surface)
            if (afterCreate) {
                for (call in Call.calls())
                    if (call.hasVideo()) {
                        if (call.startVideoDisplay() != 0)
                            Log.e(TAG, "Failed to start video display")
                        break
                    }
                afterCreate = false
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "Surface destroyed")
            for (call in Call.calls())
                if (call.hasVideo())
                    call.stopVideoDisplay()
            set_surface(null)
        }

    }
}

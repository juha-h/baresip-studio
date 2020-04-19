package com.tutpro.baresip

import android.content.Context
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

class VideoView(val context: Context) {

    /* external fun nativeOnStart()
    external fun nativeOnResume()
    external fun nativeOnPause()
    external fun nativeOnStop() */
    external fun set_surface(surface: Surface?)

    val surfaceHolderCallback: SurfaceHolderCallback?
    val surfaceView: View?

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

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            set_surface(holder.surface)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            set_surface(null)
        }

    }
}

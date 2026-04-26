package com.tutpro.baresip

import android.app.Application
import android.os.DeadObjectException
import android.util.Log

class BaresipApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isImeDeadObjectException(throwable)) {
                Log.e(TAG, "Caught DeadObjectException from IME: $throwable")
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        if (!BaresipService.libraryLoaded) {
            Log.i(TAG, "Loading baresip library")
            System.loadLibrary("baresip")
            BaresipService.libraryLoaded = true
        }
    }

    private fun isImeDeadObjectException(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause is DeadObjectException) {
                if (cause.stackTrace.any { it.methodName == "updateCursorAnchorInfo" }) {
                    return true
                }
            }
            cause = cause.cause
        }
        return false
    }
}

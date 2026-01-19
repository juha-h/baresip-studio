package com.tutpro.baresip

import android.app.Application

class BaresipApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (!BaresipService.libraryLoaded) {
            Log.i(TAG, "Loading baresip library")
            System.loadLibrary("baresip")
            BaresipService.libraryLoaded = true
        }
    }
}

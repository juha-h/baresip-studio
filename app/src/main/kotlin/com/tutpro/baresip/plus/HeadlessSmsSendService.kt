package com.tutpro.baresip.plus

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Skeletal headless SMS send service required for Default SMS App eligibility.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

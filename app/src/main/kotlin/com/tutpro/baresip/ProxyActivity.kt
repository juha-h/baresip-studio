package com.tutpro.baresip

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ProxyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        Utils.setShowWhenLocked(this, true)
        Utils.setTurnScreenOn(this, true)

        Log.d(TAG, "ProxyActivity onCreate")

        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtras(getIntent())
        startActivity(intent)

        finish()

    }

}



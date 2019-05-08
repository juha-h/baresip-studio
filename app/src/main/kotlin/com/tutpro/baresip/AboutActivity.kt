package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.widget.TextView

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        val aboutText = findViewById(R.id.aboutText) as TextView
        aboutText.text = String.format(getText(R.string.about_text).toString(),
                BuildConfig.VERSION_NAME)
        aboutText.setTextColor(Color.BLACK)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                val i = Intent()
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }
        }
        return true
    }
}


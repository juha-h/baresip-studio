package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.widget.TextView

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        val aboutText = findViewById<TextView>(R.id.aboutText)
        val text = String.format(getString(R.string.about_text), BuildConfig.VERSION_NAME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            aboutText.text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
        else
            aboutText.text = Html.fromHtml(text)
        aboutText.setMovementMethod(LinkMovementMethod.getInstance())
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


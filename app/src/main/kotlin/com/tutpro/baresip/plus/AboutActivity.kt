package com.tutpro.baresip.plus

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.text.HtmlCompat
import androidx.appcompat.app.AppCompatActivity
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.widget.TextView

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        Utils.addActivity("about")

        val aboutText = findViewById<TextView>(R.id.aboutText)
        val text = String.format(getString(R.string.about_text), BuildConfig.VERSION_NAME)
        aboutText.text = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
        aboutText.setMovementMethod(LinkMovementMethod.getInstance())

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            android.R.id.home -> {
                BaresipService.activities.remove("about")
                val i = Intent()
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }
        }

        return true
    }

    override fun onBackPressed() {

        BaresipService.activities.remove("about")
        super.onBackPressed()

    }

}


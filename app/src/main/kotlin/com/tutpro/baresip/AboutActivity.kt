package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.text.HtmlCompat
import androidx.appcompat.app.AppCompatActivity
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import com.tutpro.baresip.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Utils.addActivity("about")

        val text = String.format(getString(R.string.about_text),
                BuildConfig.VERSION_NAME)
        binding.aboutText.text = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.aboutText.movementMethod = LinkMovementMethod.getInstance()

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            android.R.id.home -> {
                BaresipService.activities.remove("about")
                setResult(Activity.RESULT_CANCELED, Intent())
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


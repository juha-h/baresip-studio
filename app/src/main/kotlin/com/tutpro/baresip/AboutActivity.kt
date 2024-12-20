package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.text.HtmlCompat
import androidx.appcompat.app.AppCompatActivity
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.tutpro.baresip.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v: View, insets: WindowInsetsCompat ->
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            if (Build.VERSION.SDK_INT >= 35)
                binding.aboutText.updatePadding(top = 172)
            WindowInsetsCompat.CONSUMED
        }

        if (!Utils.isDarkTheme(this))
            WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        Utils.addActivity("about")

        val text = String.format(getString(R.string.about_text),
                BuildConfig.VERSION_NAME)
        binding.aboutText.text = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.aboutText.movementMethod = LinkMovementMethod.getInstance()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            android.R.id.home ->
                goBack()
        }

        return true
    }

    private fun goBack() {
        BaresipService.activities.remove("about")
        setResult(Activity.RESULT_CANCELED, Intent())
        finish()
    }

}


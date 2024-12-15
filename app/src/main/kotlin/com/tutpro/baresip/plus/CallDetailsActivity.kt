package com.tutpro.baresip.plus

import android.media.MediaPlayer
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.tutpro.baresip.plus.databinding.ActivityCallDetailsBinding

class CallDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallDetailsBinding
    private lateinit var aor: String
    private lateinit var peer: String
    private val decPlayer = MediaPlayer()
    private val encPlayer = MediaPlayer()
    private var position = 0

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_call_details)

        binding = ActivityCallDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v: View, insets: WindowInsetsCompat ->
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            if (Build.VERSION.SDK_INT >= 35)
                binding.CallDetailsView.updatePadding(top = systemBars.top + 56)
            WindowInsetsCompat.CONSUMED
        }

        if (!Utils.isDarkTheme(this))
            WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        aor = intent.getStringExtra("aor")!!
        peer = intent.getStringExtra("peer")!!
        position = intent.getIntExtra("position", 0)

        Utils.addActivity("call_details,$aor,$peer,$position")

        val headerView = binding.peer
        val headerText =  "${getString(R.string.peer)} $peer"
        headerView.text = headerText

        val listView = binding.calls
        listView.adapter = CallDetailsAdapter(this, CallsActivity.uaHistory[position].details,
                decPlayer, encPlayer)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("call_details,$aor,$peer,$position") == -1)
            return true

        when (item.itemId) {
            android.R.id.home -> {
                goBack()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun goBack() {
        BaresipService.activities.remove("call_details,$aor,$peer,$position")
        decPlayer.stop()
        decPlayer.release()
        encPlayer.stop()
        encPlayer.release()
        finish()
    }

}
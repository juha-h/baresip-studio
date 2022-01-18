package com.tutpro.baresip

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import com.tutpro.baresip.databinding.ActivityCallDetailsBinding

class CallDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallDetailsBinding
    private lateinit var aor: String
    private lateinit var peer: String
    private var position = 0

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_details)

        binding = ActivityCallDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aor = intent.getStringExtra("aor")!!
        peer = intent.getStringExtra("peer")!!
        position = intent.getIntExtra("position", 0)

        Utils.addActivity("call_details,$aor,$peer,$position")

        val headerView = binding.peer
        val headerText =  "${getString(R.string.peer)} $peer"
        headerView.text = headerText

        val listView = binding.calls
        listView.adapter = CallDetailsAdapter(this, CallsActivity.uaHistory[position].details)

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
                onBackPressed()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        BaresipService.activities.remove("call_details,$aor,$peer,$position")
        finish()
        super.onBackPressed()
    }

}
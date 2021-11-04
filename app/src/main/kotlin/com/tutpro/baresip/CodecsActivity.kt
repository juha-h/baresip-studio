package com.tutpro.baresip

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.LinearLayout.LayoutParams
import androidx.appcompat.app.AppCompatActivity

import com.tutpro.baresip.databinding.ActivityCodecsBinding

class CodecsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodecsBinding
    private lateinit var acc: Account
    private lateinit var ua: UserAgent
    private var aor = ""
    private var newCodecs = ArrayList<String>()
    private var media = ""

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityCodecsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        aor = intent.getStringExtra("aor")!!
        media = intent.getStringExtra("media")!!

        Utils.addActivity("codecs,$aor,$media")

        ua = UserAgent.ofAor(aor)!!
        acc = ua.account

        val codecs: ArrayList<String>
        val accCodecs: ArrayList<String>

        val title = binding.CodecsTitle

        if (media == "audio") {
            title.text = getString(R.string.audio_codecs)
            codecs = ArrayList(Api.audio_codecs().split(","))
            accCodecs = acc.audioCodec
        } else {
            title.text = getString(R.string.video_codecs)
            codecs = ArrayList(Api.video_codecs().split(",").distinct())
            accCodecs = acc.videoCodec
        }

        newCodecs.addAll(accCodecs)

        while (newCodecs.size < codecs.size) newCodecs.add("-")

        val layout = binding.SpinnerTable
        val spinnerList = Array(codecs.size) { ArrayList<String>() }
        for (i in codecs.indices) {
            val spinner = Spinner(applicationContext)
            spinner.id = i + 100
            spinner.layoutParams = TableRow.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT)
            spinner.layoutParams.height = 75
            layout.addView(spinner)
            if (accCodecs.size > i) {
                val codec = accCodecs[i]
                spinnerList[i].add(codec)
                spinnerList[i].add("-")
                for (c in codecs) if (c != codec) spinnerList[i].add(c)
            } else {
                spinnerList[i].addAll(codecs)
                spinnerList[i].add(0, "-")
            }
            val codecSpinner = findViewById<Spinner>(spinner.id)
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
                    spinnerList[i])
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            codecSpinner.adapter = adapter
            codecSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    newCodecs[parent.id - 100] = parent.selectedItem.toString()
                }
                override fun onNothingSelected(parent: AdapterView<*>) {
                }
            }
        }

        binding.CodecsTitle.setOnClickListener {
            if (media == "audio")
                Utils.alertView(this, getString(R.string.audio_codecs),
                    getString(R.string.audio_codecs_help))
            else
                Utils.alertView(this, getString(R.string.video_codecs),
                    getString(R.string.video_codecs_help))
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)
        return true

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("codecs,$aor,$media") == -1)
            return true

        when (item.itemId) {

            R.id.checkIcon -> {

                var save = false
                val mc = ArrayList(LinkedHashSet<String>(newCodecs.filter { it != "-" } as ArrayList<String>))
                val mcList = Utils.implode(mc, ",")

                if (media == "audio")
                    if (mc != acc.audioCodec) {
                        if (Api.account_set_audio_codecs(acc.accp, mcList) == 0) {
                            Log.d(TAG, "New audio codecs '$mcList'")
                            acc.audioCodec = mc
                            save = true
                        } else {
                            Log.e(TAG, "Setting of audio codecs '$mcList' failed")
                        }
                    }

                if (media == "video")
                    if (mc != acc.videoCodec) {
                        if (Api.account_set_video_codecs(acc.accp, mcList) == 0) {
                            Log.d(TAG, "New video codecs '$mcList'")
                            acc.videoCodec = mc
                            save = true
                        } else {
                            Log.e(TAG, "Setting of video codecs '$mcList' failed")
                        }
                    }

                if (save) {
                    AccountsActivity.saveAccounts()
                    if (Api.ua_update_account(ua.uap) != 0)
                        Log.e(TAG, "Failed to update UA ${ua.uap} with AoR $aor")
                }

                if ((acc.regint > 0) && !((acc.authUser != "") && (acc.authPass == "")))
                    Api.ua_register(ua.uap)

                BaresipService.activities.remove("codecs,$aor,$media")
                finish()
                return true
            }

            android.R.id.home -> {
                onBackPressed()
                return true
            }

        }

        return super.onOptionsItemSelected(item)

    }

    override fun onBackPressed() {
        BaresipService.activities.remove("codecs,$aor,$media")
        finish()
        super.onBackPressed()
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
    }

}

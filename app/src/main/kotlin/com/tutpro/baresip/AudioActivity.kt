package com.tutpro.baresip

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tutpro.baresip.databinding.ActivityAudioBinding

class AudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioBinding
    private lateinit var opusBitRate: EditText
    private lateinit var opusPacketLoss: EditText
    private lateinit var aec: CheckBox
    private lateinit var audioDelay: EditText

    private var save = false
    private var reload = false
    private var callVolume = BaresipService.callVolume
    private var oldAudioModules = mutableMapOf<String, Boolean>()
    private var oldOpusBitrate = ""
    private var oldOpusPacketLoss = ""
    private var oldAec = false
    private var oldAudioDelay = BaresipService.audioDelay.toString()
    private val audioModules = listOf("opus", "amr", "g722", "g7221", "g726", "g729", "g711")

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        Utils.addActivity("audio")

        val callVolSpinner = binding.VolumeSpinner
        val volKeys = arrayListOf("None", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        val volVals = arrayListOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val curVal = callVolume
        val curKey = volKeys[curVal]
        volKeys.removeAt(curVal)
        volVals.removeAt(curVal)
        volKeys.add(0, curKey)
        volVals.add(0, curVal)
        val callVolAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            volKeys
        )
        callVolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        callVolSpinner.adapter = callVolAdapter
        callVolSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
            ) {
                callVolume = volVals[volKeys.indexOf(parent.selectedItem.toString())]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        val audioModulesList = binding.AudioModulesList
        var id = 1000
        for (module in audioModules) {
            val rl = RelativeLayout(this)
            val rlParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT)
            rlParams.marginStart = 16
            rl.layoutParams = rlParams
            val tv = TextView(this)
            tv.id = id++
            val tvParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT)
            tvParams.addRule(RelativeLayout.ALIGN_PARENT_START)
            tvParams.addRule(RelativeLayout.CENTER_VERTICAL)
            tvParams.addRule(RelativeLayout.START_OF, id)
            tv.layoutParams = tvParams
            tv.text = String.format(getString(R.string.bullet_item), module)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            tv.setTextColor(ContextCompat.getColor(this, R.color.colorItemText))
            rl.addView(tv)
            val cb = CheckBox(this)
            cb.id = id++
            val cbParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT)
            cbParams.addRule(RelativeLayout.ALIGN_PARENT_END)
            cbParams.addRule(RelativeLayout.CENTER_VERTICAL)
            cb.layoutParams = cbParams
            cb.gravity = Gravity.END
            cb.isChecked = Config.variable("module $module.so").size > 0
            oldAudioModules[module] = cb.isChecked
            rl.addView(cb)
            audioModulesList.addView(rl)
        }

        opusBitRate = binding.OpusBitRate
        val obCv = Config.variable("opus_bitrate")
        oldOpusBitrate = if (obCv.size == 0) "28000" else obCv[0]
        opusBitRate.setText(oldOpusBitrate)

        opusPacketLoss = binding.OpusPacketLoss
        val oplCv = Config.variable("opus_packet_loss")
        oldOpusPacketLoss = if (oplCv.size == 0) "0" else oplCv[0]
        opusPacketLoss.setText(oldOpusPacketLoss)

        aec = binding.Aec
        val aecCv = Config.variable("module")
        oldAec = aecCv.contains("webrtc_aecm.so")
        aec.isChecked = oldAec

        audioDelay = binding.AudioDelay
        audioDelay.setText(oldAudioDelay)

        bindTitles()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)
        return true

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("audio") == -1)
            return true

        when (item.itemId) {

            R.id.checkIcon -> {

                if (BaresipService.callVolume != callVolume) {
                    BaresipService.callVolume = callVolume
                    Config.replaceVariable("call_volume", callVolume.toString())
                    save = true
                }

                var id = 1001
                for (module in audioModules) {
                    val box = findViewById<CheckBox>(id++)
                    if (box.isChecked && !oldAudioModules[module]!!) {
                        if (Api.module_load("$module.so") != 0) {
                            Utils.alertView(this, getString(R.string.error),
                                    "${getString(R.string.failed_to_load_module)}: $module.so")
                            return false
                        }
                        Config.addModuleLine("module $module.so")
                        save = true
                    }
                    if (!box.isChecked && oldAudioModules[module]!!) {
                        Api.module_unload("$module.so")
                        Config.removeLine("module $module.so")
                        for (ua in BaresipService.uas) ua.account.removeAudioCodecs(module)
                        AccountsActivity.saveAccounts()
                        save = true
                    }
                    id++
                }

                val opusBitRate = opusBitRate.text.toString().trim()
                if (opusBitRate != oldOpusBitrate) {
                    if (!checkOpusBitRate(opusBitRate)) {
                        Utils.alertView(this, getString(R.string.notice),
                                "${getString(R.string.invalid_opus_bitrate)}: $opusBitRate.")
                        return false
                    }
                    Config.removeVariable("opus_bitrate")
                    Config.addLine("opus_bitrate $opusBitRate")
                    reload = true
                    save = true
                }

                val opusPacketLoss = opusPacketLoss.text.toString().trim()
                if (opusPacketLoss != oldOpusPacketLoss) {
                    if (!checkOpusPacketLoss(opusPacketLoss)) {
                        Utils.alertView(this, getString(R.string.notice),
                                "${getString(R.string.invalid_opus_packet_loss)}: $opusPacketLoss")
                        return false
                    }
                    Config.removeVariable("opus_inbandfec")
                    Config.removeVariable("opus_packet_loss")
                    if (opusPacketLoss != "0") {
                        Config.addLine("opus_inbandfec yes")
                        Config.addLine("opus_packet_loss $opusPacketLoss")
                    }
                    reload = true
                    save = true
                }

                if (aec.isChecked != oldAec) {
                    Config.removeLine("module webrtc_aecm.so")
                    if (aec.isChecked) {
                        Config.addModuleLine("module webrtc_aecm.so")
                        if (Api.module_load("webrtc_aecm.so") != 0) {
                            Utils.alertView(this, getString(R.string.error),
                                    getString(R.string.failed_to_load_module))
                            aec.isChecked = false
                            return false
                        }
                    } else {
                        Api.module_unload("webrtc_aecm.so")
                    }
                    save = true
                }

                val audioDelay = audioDelay.text.toString().trim()
                if (audioDelay != oldAudioDelay) {
                    if (!checkAudioDelay(audioDelay)) {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.invalid_audio_delay), audioDelay))
                        return false
                    }
                    Config.removeVariable("audio_delay")
                    Config.addLine("audio_delay $audioDelay")
                    BaresipService.audioDelay = audioDelay.toLong()
                    save = true
                }

                if (save) Config.save()

                if (reload) Api.reload_config()

                BaresipService.activities.remove("audio")
                finish()
                return true
            }

            android.R.id.home -> {
                goBack()
                return true
            }

        }

        return super.onOptionsItemSelected(item)

    }

    private fun goBack() {
        BaresipService.activities.remove("audio")
        finish()
    }

    private fun bindTitles() {
        binding.VolumeTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.default_call_volume),
                    getString(R.string.default_call_volume_help))
        }
        binding.AudioModulesTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.audio_modules_title),
                    getString(R.string.audio_modules_help))
        }
        binding.OpusBitRateTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.opus_bit_rate),
                    getString(R.string.opus_bit_rate_help))
        }
        binding.OpusPacketLossTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.opus_packet_loss),
                    getString(R.string.opus_packet_loss_help))
        }
        binding.AecTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.aec),
                    getString(R.string.aec_help))
        }
        binding.AudioDelayTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.audio_delay),
                    getString(R.string.audio_delay_help))
        }
    }

    private fun checkOpusBitRate(opusBitRate: String): Boolean {
        val number = opusBitRate.toIntOrNull() ?: return false
        return (number >= 6000) && (number <= 510000)
    }

    private fun checkOpusPacketLoss(opusPacketLoss: String): Boolean {
        val number = opusPacketLoss.toIntOrNull() ?: return false
        return (number >= 0) && (number <= 100)
    }

    private fun checkAudioDelay(audioDelay: String): Boolean {
        val number = audioDelay.toIntOrNull() ?: return false
        return (number >= 100) && (number <= 3000)
    }
}

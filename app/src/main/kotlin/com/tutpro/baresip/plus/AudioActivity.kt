package com.tutpro.baresip.plus

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*

class AudioActivity : AppCompatActivity() {

    internal lateinit var opusBitRate: EditText
    internal lateinit var opusPacketLoss: EditText
    internal lateinit var aec: CheckBox
    internal lateinit var extentedFilter: CheckBox

    private var save = false
    private var reload = false
    private var oldAudioModules = mutableMapOf<String, Boolean>()
    private var oldOpusBitrate = ""
    private var oldOpusPacketLoss = ""
    private var oldAec = false
    private var oldExtendedFilter = false
    private val audioModules = listOf("opus", "amr", "ilbc", "g722", "g7221", "g726", "g711")

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val audioModulesList = findViewById(R.id.AudioModulesList) as LinearLayout
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
            tv.text = "\u2022 $module"
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            tv.setTextColor(Color.BLACK)
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
            oldAudioModules.put(module, cb.isChecked)
            rl.addView(cb)
            audioModulesList.addView(rl)
        }

        opusBitRate = findViewById(R.id.OpusBitRate) as EditText
        val obCv = Config.variable("opus_bitrate")
        oldOpusBitrate = if (obCv.size == 0) "28000" else obCv[0]
        opusBitRate.setText(oldOpusBitrate)

        opusPacketLoss = findViewById(R.id.OpusPacketLoss) as EditText
        val oplCv = Config.variable("opus_packet_loss")
        oldOpusPacketLoss = if (oplCv.size == 0) "0" else oplCv[0]
        opusPacketLoss.setText(oldOpusPacketLoss)

        aec = findViewById(R.id.Aec) as CheckBox
        val aecCv = Config.variable("module")
        oldAec = aecCv.contains("webrtc_aec.so")
        aec.isChecked = oldAec

        extentedFilter = findViewById(R.id.ExtendedFilter) as CheckBox
        oldExtendedFilter = Config.variable("webrtc_aec_extended_filter")[0] == "yes"
        extentedFilter.isChecked = oldExtendedFilter

        Utils.addActivity("audio")

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

                var id = 1001
                for (module in audioModules) {
                    val box = findViewById<CheckBox>(id++)
                    if (box.isChecked && !oldAudioModules[module]!!) {
                        if (Api.module_load("$module.so") != 0) {
                            Utils.alertView(this, getString(R.string.error),
                                    "${getString(R.string.failed_to_load_module)}: $module.so")
                            return false
                        }
                        Config.addLine("module $module.so")
                        save = true
                    }
                    if (!box.isChecked && oldAudioModules[module]!!) {
                        Api.module_unload("$module.so")
                        Config.removeLine("module $module.so")
                        for (ua in UserAgent.uas()) ua.account.removeAudioCodecs(module)
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
                    Config.removeLine("module webrtc_aec.so")
                    if (aec.isChecked) {
                        Config.addLine("module webrtc_aec.so")
                        if (Api.module_load("webrtc_aec.so") != 0) {
                            Utils.alertView(this, getString(R.string.error),
                                    getString(R.string.failed_to_load_module))
                            aec.isChecked = false
                            return false
                        }
                    } else {
                        Api.module_unload("webrtc_aec.so")
                    }
                    save = true
                }

                if (extentedFilter.isChecked != oldExtendedFilter) {
                    Config.removeVariable("webrtc_aec_extended_filter")
                    if (extentedFilter.isChecked)
                        Config.addLine("webrtc_aec_extended_filter yes")
                    else
                        Config.addLine("webrtc_aec_extended_filter no")
                    reload = true
                    save = true
                }

                if (save) Config.save()

                if (reload) Api.reload_config()

                BaresipService.activities.remove("audio")
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
        BaresipService.activities.remove("audio")
        finish()
        super.onBackPressed()
    }

    fun onClick(v: View) {
        when (v) {
            findViewById(R.id.AudioModulesTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.audio_modules_title),
                        getString(R.string.audio_modules_help))
            }
            findViewById(R.id.OpusBitRateTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.opus_bit_rate),
                        getString(R.string.opus_bit_rate_help))
            }
            findViewById(R.id.OpusPacketLossTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.opus_packet_loss),
                        getString(R.string.opus_packet_loss_help))
            }
            findViewById(R.id.AecTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.aec),
                        getString(R.string.aec_help))
            }
            findViewById(R.id.ExtendedFilterTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.aec_extented_filter),
                        getString(R.string.aec_extented_filter_help))
            }
        }
    }

    private fun checkOpusBitRate(opusBitRate: String): Boolean {
        val number = opusBitRate.toIntOrNull()
        if (number == null) return false
        return (number >= 6000) && (number <= 510000)
    }

    private fun checkOpusPacketLoss(opusPacketLoss: String): Boolean {
        val number = opusPacketLoss.toIntOrNull()
        if (number == null) return false
        return (number >= 0) && (number <= 100)
    }

}

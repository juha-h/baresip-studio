package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.RelativeLayout.LayoutParams
import android.widget.AdapterView

class ConfigActivity : AppCompatActivity() {

    internal lateinit var autoStart: CheckBox
    internal lateinit var listenAddr: EditText
    internal lateinit var dnsServers: EditText
    internal lateinit var certificateFile: CheckBox
    internal lateinit var caFile: CheckBox
    internal lateinit var aec: CheckBox
    internal lateinit var opusBitRate: EditText
    internal lateinit var opusPacketLoss: EditText
    internal lateinit var debug: CheckBox
    internal lateinit var reset: CheckBox

    private var oldAutoStart = ""
    private var oldListenAddr = ""
    private var oldDnsServers = ""
    private var oldCertificateFile = false
    private var oldCAFile = false
    private var oldAudioModules = mutableMapOf<String, Boolean>()
    private var oldAec = false
    private var oldOpusBitrate = ""
    private var oldOpusPacketLoss = ""
    private var oldLogLevel = ""
    private var callVolume = BaresipService.callVolume
    private var videoSize = ""
    private var oldVideoSize = Config.variable("video_size")[0]
    private var save = false
    private var restart = false
    private var reload = false
    private val audioModules = listOf("opus", "amr", "ilbc", "g722", "g7221", "g726", "g711")
    private var menu: Menu? = null

    private val READ_CERT_PERMISSION_CODE = 1
    private val READ_CA_PERMISSION_CODE = 2

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        Utils.addActivity("config")

        autoStart = findViewById(R.id.AutoStart) as CheckBox
        val asCv = Config.variable("auto_start")
        oldAutoStart = if (asCv.size == 0) "no" else asCv[0]
        autoStart.isChecked = oldAutoStart == "yes"

        listenAddr = findViewById(R.id.ListenAddress) as EditText
        val laCv = Config.variable("sip_listen")
        oldListenAddr = if (laCv.size == 0) "" else laCv[0]
        listenAddr.setText(oldListenAddr)

        dnsServers = findViewById(R.id.DnsServers) as EditText
        val ddCv = Config.variable("dyn_dns")
        if (ddCv[0] == "yes") {
            oldDnsServers = ""
        } else {
            val dsCv = Config.variable("dns_server")
            var dsTv = ""
            for (ds in dsCv) dsTv += ", $ds"
            oldDnsServers = dsTv.trimStart(',').trimStart(' ')
        }
        dnsServers.setText(oldDnsServers)

        certificateFile = findViewById(R.id.CertificateFile) as CheckBox
        oldCertificateFile = Config.variable("sip_certificate").isNotEmpty()
        certificateFile.isChecked = oldCertificateFile

        caFile = findViewById(R.id.CAFile) as CheckBox
        oldCAFile = Config.variable("sip_cafile").isNotEmpty()
        caFile.isChecked = oldCAFile

        val audioModulesList = findViewById(R.id.AudioModulesList) as LinearLayout
        var id = 1000
        for (module in audioModules) {
            val rl = RelativeLayout(this)
            val rlParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            rlParams.marginStart = 16
            rl.layoutParams = rlParams
            val tv = TextView(this)
            tv.id = id++
            val tvParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
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
            val cbParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            cbParams.addRule(RelativeLayout.ALIGN_PARENT_END)
            cbParams.addRule(RelativeLayout.CENTER_VERTICAL)
            cb.layoutParams = cbParams
            cb.gravity = Gravity.END
            cb.isChecked = Config.variable("module $module.so").size > 0
            oldAudioModules.put(module, cb.isChecked)
            rl.addView(cb)
            audioModulesList.addView(rl)
        }

        aec = findViewById(R.id.Aec) as CheckBox
        val aecCv = Config.variable("module")
        oldAec = aecCv.contains("webrtc_aec.so")
        aec.isChecked = oldAec

        opusBitRate = findViewById(R.id.OpusBitRate) as EditText
        val obCv = Config.variable("opus_bitrate")
        oldOpusBitrate = if (obCv.size == 0) "28000" else obCv[0]
        opusBitRate.setText(oldOpusBitrate)

        opusPacketLoss = findViewById(R.id.OpusPacketLoss) as EditText
        val oplCv = Config.variable("opus_packet_loss")
        oldOpusPacketLoss = if (oplCv.size == 0) "0" else oplCv[0]
        opusPacketLoss.setText(oldOpusPacketLoss)

        val callVolSpinner = findViewById(R.id.VolumeSpinner) as Spinner
        val volKeys = arrayListOf("None", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        val volVals = arrayListOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val curVal = callVolume
        val curKey = volKeys[curVal]
        volKeys.removeAt(curVal)
        volVals.removeAt(curVal)
        volKeys.add(0, curKey)
        volVals.add(0, curVal)
        val callVolAdapter = ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,
                volKeys)
        callVolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        callVolSpinner.adapter = callVolAdapter
        callVolSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                callVolume = volVals[volKeys.indexOf(parent.selectedItem.toString())]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        Log.d("Baresip", "Old video_size $oldVideoSize")
        val videoSizeSpinner = findViewById(R.id.VideoSizeSpinner) as Spinner
        val sizes = arrayListOf("640x360", "640x480", "800x600", "960x720", "1024x768", "1280x720")
        sizes.removeIf{it == oldVideoSize}
        sizes.add(0, oldVideoSize)
        Log.d("Baresip", "sizes = $sizes")
        val videoSizeAdapter = ArrayAdapter(this,android.R.layout.simple_spinner_item,
                sizes)
        videoSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        videoSizeSpinner.adapter = videoSizeAdapter
        videoSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                videoSize = parent.selectedItem.toString()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                videoSize = oldVideoSize
            }
        }

        debug = findViewById(R.id.Debug) as CheckBox
        val dbCv = Config.variable("log_level")
        if (dbCv.size == 0)
            oldLogLevel = "2"
        else
            oldLogLevel = dbCv[0]
        debug.isChecked =  oldLogLevel == "0"

        reset = findViewById(R.id.Reset) as CheckBox
        reset.isChecked = false

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        super.onCreateOptionsMenu(menu)

        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)

        this.menu = menu

        return true

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("config") == -1) return true

        when (item.itemId) {

            R.id.checkIcon -> {

                var autoStartString = "no"
                if (autoStart.isChecked) autoStartString = "yes"
                if (oldAutoStart != autoStartString) {
                    Config.replaceVariable("auto_start", autoStartString)
                    save = true
                    restart = false
                }

                val listenAddr = listenAddr.text.toString().trim()
                if (listenAddr != oldListenAddr) {
                    if ((listenAddr != "") && !Utils.checkIpPort(listenAddr)) {
                        Utils.alertView(this, getString(R.string.notice),
                                "${getString(R.string.invalid_listen_address)}: $listenAddr")
                        return false
                    }
                    Config.removeVariable("sip_listen")
                    if (listenAddr != "") Config.addLine("sip_listen $listenAddr")
                    save = true
                    restart = true
                }

                val dnsServers = addMissingPorts(dnsServers.text.toString().trim().toLowerCase())
                if (dnsServers != oldDnsServers) {
                    if (!checkDnsServers(dnsServers)) {
                        Utils.alertView(this, getString(R.string.notice),
                                "${getString(R.string.invalid_dns_servers)}: $dnsServers")
                        return false
                    }
                    Config.removeVariable("dyn_dns")
                    Config.removeVariable("dns_server")
                    if (dnsServers.isNotEmpty()) {
                        for (server in dnsServers.split(","))
                            Config.addLine("dns_server $server")
                        Config.addLine("dyn_dns no")
                        if (Api.net_use_nameserver(dnsServers) != 0) {
                            Utils.alertView(this, getString(R.string.error),
                                    "${getString(R.string.failed_to_set_dns_servers)}: $dnsServers")
                            return false
                        }
                    } else {
                        Config.addLine("dyn_dns yes")
                        Config.updateDnsServers(BaresipService.dnsServers)
                    }
                    Api.net_debug()
                    save = true
                }

                if (certificateFile.isChecked != oldCertificateFile) {
                    if (certificateFile.isChecked) {
                        if (!Utils.requestPermission(this,
                                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                        READ_CERT_PERMISSION_CODE))
                            return false
                        val content = Utils.getFileContents(BaresipService.downloadsPath + "/cert.pem")
                        if (content == null) {
                            Utils.alertView(this, getString(R.string.error),
                                    getString(R.string.read_cert_error))
                            certificateFile.isChecked = false
                            return false
                        }
                        Utils.putFileContents(BaresipService.filesPath + "/cert.pem", content)
                        Config.removeVariable("sip_certificate")
                        Config.addLine("sip_certificate ${BaresipService.filesPath}/cert.pem")
                    } else {
                        Config.removeVariable("sip_certificate")
                    }
                    save = true
                    restart = true
                }

                if (caFile.isChecked != oldCAFile) {
                    if (caFile.isChecked) {
                        if (!Utils.requestPermission(this,
                                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                        READ_CA_PERMISSION_CODE))
                            return false
                        val content = Utils.getFileContents(BaresipService.downloadsPath +
                                "/ca_certs.crt")
                        if (content == null) {
                            Utils.alertView(this, getString(R.string.error),
                                    getString(R.string.read_ca_certs_error))
                            caFile.isChecked = false
                            return false
                        }
                        Utils.putFileContents(BaresipService.filesPath + "/ca_certs.crt",
                                content)
                        Config.removeVariable("sip_cafile")
                        Config.addLine("sip_cafile ${BaresipService.filesPath}/ca_certs.crt")
                    } else {
                        Config.removeVariable("sip_cafile")
                    }
                    save = true
                    restart = true
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

                val opusBitRate = opusBitRate.text.toString().trim()
                if (opusBitRate != oldOpusBitrate) {
                    if (!checkOpusBitRate(opusBitRate)) {
                        Utils.alertView(this, getString(R.string.notice),
                                "${getString(R.string.invalid_opus_bitrate)}: $opusBitRate.")
                        return false
                    }
                    Config.removeVariable("opus_bitrate")
                    Config.addLine("opus_bitrate $opusBitRate")
                    save = true
                    restart = true
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
                    save = true
                    restart = true
                }

                if (BaresipService.callVolume != callVolume) {
                    BaresipService.callVolume = callVolume
                    Config.replaceVariable("call_volume", callVolume.toString())
                    save = true
                }

                if (oldVideoSize != videoSize) {
                    Config.replaceVariable("video_size", videoSize)
                    save = true
                    reload = true
                }

                var logLevelString = "2"
                if (debug.isChecked) logLevelString = "0"
                if (oldLogLevel != logLevelString) {
                    Config.replaceVariable("log_level", logLevelString)
                    Api.log_level_set(logLevelString.toInt())
                    Log.logLevelSet(logLevelString.toInt())
                    save = true
                }

                if (reset.isChecked) {
                    Config.reset(this)
                    save = false
                    restart = true
                }

                if (save) Config.save()

                BaresipService.activities.remove("config")
                val intent = Intent(this, MainActivity::class.java)
                if (restart)
                    intent.putExtra("restart", true)
                else
                    if (reload)
                        if (Api.reload_config() != 0)
                            Log.e("Baresip", "Reload of config failed")
                setResult(RESULT_OK, intent)
                finish()

            }

            android.R.id.home -> {

                BaresipService.activities.remove("config")
                val intent = Intent(this, MainActivity::class.java)
                setResult(Activity.RESULT_CANCELED, intent)
                finish()

            }
        }

        return true

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            READ_CERT_PERMISSION_CODE ->
                if (grantResults.isNotEmpty() && (grantResults[0] ==
                                PackageManager.PERMISSION_GRANTED))
                    menu!!.performIdentifierAction(R.id.checkIcon, 0)
                else
                    certificateFile.isChecked = false
            READ_CA_PERMISSION_CODE ->
                if ((grantResults.size > 0) && (grantResults[0] ==
                                PackageManager.PERMISSION_GRANTED))
                    menu!!.performIdentifierAction(R.id.checkIcon, 0)
                else
                    caFile.isChecked = false
        }

    }

    override fun onBackPressed() {

        BaresipService.activities.remove("config")
        val intent = Intent(this, MainActivity::class.java)
        setResult(Activity.RESULT_CANCELED, intent)
        finish()
        super.onBackPressed()

    }

    fun onClick(v: View) {
        when (v) {
            findViewById(R.id.AutoStartTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.start_automatically),
                        getString(R.string.start_automatically_help))
            }
            findViewById(R.id.ListenAddressTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.listen_address),
                        getString(R.string.listen_address_help))
            }
            findViewById(R.id.DnsServersTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.dns_servers),
                        getString(R.string.dns_servers_help))
            }
            findViewById(R.id.CertificateFileTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.tls_certificate_file),
                        getString(R.string.tls_certificate_file_help))
            }
            findViewById(R.id.CAFileTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.tls_ca_file),
                        getString(R.string.tls_ca_file_help))
            }
            findViewById(R.id.AudioModulesTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.audio_modules_title),
                        getString(R.string.audio_modules_help))
            }
            findViewById(R.id.AecTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.aec),
                        getString(R.string.aec_help))
            }
            findViewById(R.id.OpusBitRateTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.opus_bit_rate),
                        getString(R.string.opus_bit_rate_help))
            }
            findViewById(R.id.OpusPacketLossTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.opus_packet_loss),
                        getString(R.string.opus_packet_loss_help))
            }
            findViewById(R.id.VolumeTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.default_call_volume),
                        getString(R.string.default_call_volume_help))
            }
            findViewById(R.id.DebugTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.debug), getString(R.string.debug_help))
            }
            findViewById(R.id.ResetTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.reset_config),
                        getString(R.string.reset_config_help))
            }
        }
    }

    private fun checkDnsServers(dnsServers: String): Boolean {
        if (dnsServers.length == 0) return true
        for (server in dnsServers.split(","))
            if (!Utils.checkIpPort(server.trim())) return false
        return true
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

    private fun addMissingPorts(addressList: String): String {
        if (addressList == "") return ""
        var result = ""
        for (addr in addressList.split(","))
            if (Utils.checkIpPort(addr)) {
                result = "$result,$addr"
            } else {
                if (Utils.checkIpV4(addr))
                    result = "$result,$addr:53"
                else
                    result = "$result,[$addr]:53"
            }
        return result.substring(1)
    }

}


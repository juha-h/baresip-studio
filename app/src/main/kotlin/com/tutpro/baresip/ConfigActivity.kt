package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*

class ConfigActivity : AppCompatActivity() {

    internal lateinit var autoStart: CheckBox
    internal lateinit var listenAddr: EditText
    internal lateinit var preferIPv6: CheckBox
    internal lateinit var dnsServers: EditText
    internal lateinit var opusBitRate: EditText
    internal lateinit var iceLite: CheckBox
    internal lateinit var debug: CheckBox
    internal lateinit var reset: CheckBox

    private var oldAutoStart = ""
    private var oldListenAddr = ""
    private var oldPreferIPv6 = ""
    private var oldDnsServers = ""
    private var oldOpusBitrate = ""
    private var oldIceMode = ""
    private var oldLogLevel = ""
    private var callVolume = BaresipService.callVolume
    private var save = false
    private var restart = false
    private var config = ""

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        autoStart = findViewById(R.id.AutoStart) as CheckBox
        val asCv = Config.variable("auto_start")
        oldAutoStart = if (asCv.size == 0) "no" else asCv[0]
        autoStart.isChecked = oldAutoStart == "yes"

        listenAddr = findViewById(R.id.ListenAddress) as EditText
        val laCv = Config.variable("sip_listen")
        oldListenAddr = if (laCv.size == 0) "" else laCv[0]
        listenAddr.setText(oldListenAddr)

        preferIPv6 = findViewById(R.id.PreferIPv6) as CheckBox
        val piCv = Config.variable("prefer_ipv6")
        oldPreferIPv6 = if (piCv.size == 0) "no" else piCv[0]
        preferIPv6.isChecked = oldPreferIPv6 == "yes"

        dnsServers = findViewById(R.id.DnsServers) as EditText
        val dsCv = Config.variable("dns_server")
        var dsTv = ""
        for (ds in dsCv) dsTv += ", $ds"
        oldDnsServers = dsTv.trimStart(',').trimStart(' ')
        dnsServers.setText(oldDnsServers)

        opusBitRate = findViewById(R.id.OpusBitRate) as EditText
        val obCv = Config.variable("opus_bitrate")
        oldOpusBitrate = if (obCv.size == 0) "28000" else obCv[0]
        opusBitRate.setText(oldOpusBitrate)

        iceLite = findViewById(R.id.IceLite) as CheckBox
        val imCv = Config.variable("ice_mode")
        oldIceMode = if (imCv.size == 0) "full" else imCv[0]
        iceLite.isChecked = oldIceMode == "lite"

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
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        val intent = Intent(this, MainActivity::class.java)

        if (item.itemId == R.id.checkIcon) {

            var autoStartString = "no"
            if (autoStart.isChecked) autoStartString = "yes"
            if (oldAutoStart != autoStartString) {
                Config.replace("auto_start", autoStartString)
                save = true
                restart = false
            }

            val listenAddr = listenAddr.text.toString().trim()
            if (listenAddr != oldListenAddr) {
                if ((listenAddr != "") && !Utils.checkIpPort(listenAddr)) {
                    Utils.alertView(this, "Notice",
                            "Invalid Listen Address '$listenAddr'")
                    return false
                }
                Config.remove("sip_listen")
                if (listenAddr != "") Config.add("sip_listen", listenAddr)
                save = true
                restart = true
            }

            var preferIPv6String = "no"
            if (preferIPv6.isChecked) preferIPv6String = "yes"
            if (oldPreferIPv6 != preferIPv6String) {
                Config.replace("prefer_ipv6", preferIPv6String)
                save = true
                restart = true
            }

            val dnsServers = dnsServers.text.toString().trim().toLowerCase()
            if (dnsServers != oldDnsServers) {
                if (!checkDnsServers(dnsServers)) {
                    Utils.alertView(this, "Notice", "Invalid DNS Servers: $dnsServers")
                    return false
                }
                Config.remove("dns_server")
                for (server in dnsServers.split(","))
                    Config.add("dns_server", server)
                save = true
                restart = true
            }

            val opusBitRate = opusBitRate.text.toString().trim()
            if (opusBitRate != oldOpusBitrate) {
                if (!checkOpusBitRate(opusBitRate)) {
                    Utils.alertView(this, "Notice", "Invalid Opus Bit Rate: $opusBitRate")
                    return false
                }
                Config.replace("opus_bitrate", opusBitRate)
                save = true
                restart = true
            }

            var iceModeString = "full"
            if (iceLite.isChecked) iceModeString = "lite"
            if (oldIceMode != iceModeString) {
                Config.replace("ice_mode", iceModeString)
                save = true
                restart = true
            }

            if (BaresipService.callVolume != callVolume) {
                BaresipService.callVolume = callVolume
                Config.replace("call_volume", callVolume.toString())
                save = true
            }

            var logLevelString = "2"
            if (debug.isChecked) logLevelString = "0"
            if (oldLogLevel != logLevelString) {
                Config.replace("log_level", logLevelString)
                Api.log_level_set(logLevelString.toInt())
                Log.logLevelSet(logLevelString.toInt())
                save = true
            }

            if (reset.isChecked) {
                Config.reset()
                save = false
                restart = true
            }

            if (save) Config.save()

            intent.putExtra("restart", restart )
            setResult(RESULT_OK, intent)
            finish()
            return true

        } else if (item.itemId == android.R.id.home) {

            Log.d("Baresip", "Back array was pressed at Config")
            setResult(Activity.RESULT_CANCELED, intent)
            finish()
            return true

        } else return super.onOptionsItemSelected(item)
    }

    fun onClick(v: View) {
        when (v) {
            findViewById(R.id.AutoStartTitle) as TextView-> {
                Utils.alertView(this, "Start Automatically", getString(R.string.autoStart))
            }
            findViewById(R.id.ListenAddressTitle) as TextView-> {
                Utils.alertView(this, "Listen Address", getString(R.string.listenAddress))
            }
            findViewById(R.id.PreferIPv6Title) as TextView-> {
                Utils.alertView(this, "Prefer IPv6", getString(R.string.preferIPv6))
            }
            findViewById(R.id.DnsServersTitle) as TextView -> {
                Utils.alertView(this, "DNS Servers", getString(R.string.dnsServers))
            }
            findViewById(R.id.OpusBitRateTitle) as TextView-> {
                Utils.alertView(this, "Opus Bit Rate", getString(R.string.opusBitRate))
            }
            findViewById(R.id.IceLiteTitle) as TextView-> {
                Utils.alertView(this, "ICE Lite Mode", getString(R.string.iceLite))
            }
            findViewById(R.id.VolumeTitle) as TextView-> {
                Utils.alertView(this, "Call Volume", getString(R.string.callVolume))
            }
            findViewById(R.id.DebugTitle) as TextView-> {
                Utils.alertView(this, "Debug", getString(R.string.debug))
            }
            findViewById(R.id.ResetTitle) as TextView-> {
                Utils.alertView(this, "Reset", getString(R.string.reset))
            }
        }
    }

    private fun checkDnsServers(dnsServers: String): Boolean {
        if (dnsServers.length == 0) return true
        for (server in dnsServers.split(","))
            if (!Utils.checkHostPort(server.trim(), true)) return false
        return true
    }

    private fun checkOpusBitRate(opusBitRate: String): Boolean {
        val number = opusBitRate.toIntOrNull()
        if (number == null) return false
        return (number >=6000) && (number <= 510000)
    }

}


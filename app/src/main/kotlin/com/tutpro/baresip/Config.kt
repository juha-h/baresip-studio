package com.tutpro.baresip

import android.Manifest
import android.content.Context
import java.io.File
import java.net.InetAddress
import java.nio.charset.StandardCharsets

object Config {

    private val configPath = BaresipService.filesPath + "/config"
    private lateinit var config: String
    private lateinit var lines: List<String>
    private lateinit var previousConfig: String
    private lateinit var previousLines: List<String>

    fun initialize(ctx: Context) {

        config = ctx.assets.open("config.static").bufferedReader().use { it.readText() }
        if (!File(configPath).exists()) {
            for (module in AudioActivity.audioModules)
                config = "${config}module ${module}.so\n"
            config = "${config}module webrtc_aecm.so\n"
            previousConfig = config
        } else {
            previousConfig = String(Utils.getFileContents(configPath)!!, StandardCharsets.ISO_8859_1)
        }
        previousLines = previousConfig.split("\n")

        val logLevel = previousVariable("log_level")
        if (logLevel == "") {
            config = "${config}log_level 2\n"
            Log.logLevel = Log.LogLevel.WARN
        } else {
            config = "${config}log_level $logLevel\n"
            BaresipService.logLevel = logLevel.toInt()
            Log.logLevelSet(BaresipService.logLevel)
        }

        val autoStart = previousVariable("auto_start")
        config = if (autoStart != "")
            "${config}auto_start $autoStart\n"
        else
            "${config}auto_start no\n"

        val sipListen = previousVariable("sip_listen")
        if (sipListen != "")
            config = "${config}sip_listen $sipListen\n"

        val addressFamily = previousVariable("net_af")
        if (addressFamily != "") {
            config = "${config}net_af $addressFamily\n"
            BaresipService.addressFamily = addressFamily
        }

        val sipCertificate = previousVariable("sip_certificate")
        if (sipCertificate != "")
            config = "${config}sip_certificate $sipCertificate\n"

        val sipVerifyServer = previousVariable("sip_verify_server")
        if (sipVerifyServer != "")
            config = "${config}sip_verify_server $sipVerifyServer\n"

        val sipCaFile = previousVariable("sip_cafile")
        if (sipCaFile != "")
            config = "${config}sip_cafile $sipCaFile\n"

        val dynamicDns = previousVariable("dyn_dns")
        if (dynamicDns == "no") {
            config = "${config}dyn_dns no\n"
            for (server in previousVariables("dns_server"))
                config = "${config}dns_server $server\n"
        } else {
            config = "${config}dyn_dns yes\n"
            for (dnsServer in BaresipService.dnsServers)
                config = if (Utils.checkIpV4(dnsServer.hostAddress!!))
                    "${config}dns_server ${dnsServer.hostAddress}:53\n"
                else
                    "${config}dns_server [${dnsServer.hostAddress}]:53\n"
            BaresipService.dynDns = true
        }

        var contactsMode = previousVariable("contacts_mode").lowercase()
        if (contactsMode != "") {
            if (contactsMode != "baresip" &&
                    !Utils.checkPermissions(ctx, arrayOf(Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS)))
                contactsMode = "baresip"
        } else {
            contactsMode = "baresip"
        }
        config = "${config}contacts_mode $contactsMode\n"
        BaresipService.contactsMode = contactsMode

        config = "${config}snd_path ${BaresipService.filesPath}/recordings\n"

        val callVolume = previousVariable("call_volume")
        if (callVolume != "") {
            config = "${config}call_volume $callVolume\n"
            BaresipService.callVolume = callVolume.toInt()
        } else {
            config = "${config}call_volume ${BaresipService.callVolume}\n"
        }

        val previousModules = previousVariables("module")
        for (module in AudioActivity.audioModules)
            if ("${module}.so" in previousModules)
                config = "${config}module ${module}.so\n"

        if ("webrtc_aecm.so" in previousModules)
            config = "${config}module webrtc_aecm.so\n"

        val opusBitRate = previousVariable("opus_bitrate")
        config = if (opusBitRate == "")
            "${config}opus_bit_rate 28000\n"
        else
            "${config}opus_bit_rate $opusBitRate\n"

        val opusPacketLoss = previousVariable("opus_packet_loss")
        config = if (opusPacketLoss == "")
            "${config}opus_packet_loss 1\n"
        else
            "${config}opus_packet_loss $opusPacketLoss\n"

        val audioDelay = previousVariable("audio_delay")
        if (audioDelay != "") {
            config = "${config}audio_delay $audioDelay\n"
            BaresipService.audioDelay = audioDelay.toLong()
        } else {
            config = "${config}audio_delay ${BaresipService.audioDelay}\n"
        }

        save()
        BaresipService.isConfigInitialized = true

    }

    private fun previousVariable(name: String): String {
        for (line in previousLines) {
            val nameValue = line.split(" ")
            if (nameValue.size == 2 && nameValue[0] == name)
                return nameValue[1].trim()
        }
        return ""
    }

    private fun previousVariables(name: String): ArrayList<String> {
        val result = ArrayList<String>()
        for (line in previousLines) {
            val nameValue = line.split(" ")
            if (nameValue.size == 2 && nameValue[0] == name)
                result.add(nameValue[1].trim())
        }
        return result
    }

    fun variable(name: String): String {
        for (line in lines) {
            val nameValue = line.split(" ")
            if (nameValue.size == 2 && nameValue[0] == name)
                return nameValue[1].trim()
        }
        return ""
    }

    fun variables(name: String): ArrayList<String> {
        val result = ArrayList<String>()
        for (line in lines) {
            val nameValue = line.split(" ")
            if (nameValue.size == 2 && nameValue[0] == name)
                result.add(nameValue[1].trim())
        }
        return result
    }

    fun addVariable(name: String, value: String) {
        config += "$name $value\n"
    }

    fun removeVariable(variable: String) {
        config = Utils.removeLinesStartingWithString(config, "$variable ")
    }

    fun removeVariableValue(variable: String, value: String) {
        config = Utils.removeLinesStartingWithString(config, "$variable $value")
    }

    fun replaceVariable(variable: String, value: String) {
        removeVariable(variable)
        if (value != "")
            addVariable(variable, value)
    }

    fun reset() {
        Utils.deleteFile(File(configPath))
    }

    fun save() {
        Utils.putFileContents(configPath, config.toByteArray())
        lines = config.split("\n")
        Log.d(TAG, "Saved new config '$config'")
        // Api.reload_config()
    }

    fun updateDnsServers(dnsServers: List<InetAddress>): Int {
        var servers = ""
        for (dnsServer in dnsServers) {
            if (dnsServer.hostAddress == null) continue
            var address = dnsServer.hostAddress!!.removePrefix("/")
            address = if (Utils.checkIpV4(address))
                "${address}:53"
            else
                "[${address}]:53"
            servers = if (servers == "")
                address
            else
                "${servers},${address}"
        }
        return Api.net_use_nameserver(servers)
    }

}

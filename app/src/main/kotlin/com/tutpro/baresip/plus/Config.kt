package com.tutpro.baresip.plus

import android.Manifest
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatDelegate
import java.io.File
import java.net.InetAddress
import java.nio.charset.StandardCharsets

object Config {

    private val configPath = BaresipService.filesPath + "/config"
    private lateinit var config: String
    private lateinit var previousConfig: String
    private lateinit var previousLines: List<String>
    var videoSizes = mutableListOf<String>()

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

        val caBundlePath = "${BaresipService.filesPath}/ca_bundle.crt"
        val caBundleFile = File(caBundlePath)
        val caFilePath = "${BaresipService.filesPath}/ca_certs.crt"
        val caFile = File(caFilePath)
        if (caFile.exists())
            caFile.copyTo(caBundleFile, true)
        else
            caBundleFile.writeBytes(byteArrayOf())
        Log.d(TAG, "Size of caFile = ${caBundleFile.length()}")
        val cacertsPath = "/system/etc/security/cacerts"
        val cacertsDir = File(cacertsPath)
        var caCount = 0
        if (cacertsDir.exists()) {
            cacertsDir.walk().forEach {
                if (it.isFile) {
                    caBundleFile.appendBytes(
                        it.readBytes()
                            .toString(Charsets.UTF_8)
                            .substringBefore("Certificate:")
                            .toByteArray(Charsets.UTF_8)
                    )
                    caCount++
                }
            }
            Log.d(TAG, "Added $caCount ca certificates from $cacertsPath")
        } else {
            Log.w(TAG, "Directory $cacertsDir does not exist!")
        }
        Log.d(TAG, "Size of caBundleFile = ${caBundleFile.length()}")
        config = "${config}sip_cafile $caBundlePath\n"

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

        val darkTheme = previousVariable("dark_theme")
        Preferences(ctx).displayTheme = if (darkTheme == "yes") {
            config = "${config}dark_theme yes\n"
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
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

        val speakerPhone = previousVariable("speaker_phone")
        if (speakerPhone != "") {
            config = "${config}speaker_phone $speakerPhone\n"
            BaresipService.speakerPhone = speakerPhone == "yes"
        } else {
            config = "${config}speaker_phone no\n"
        }

        val previousModules = previousVariables("module")
        for (module in AudioActivity.audioModules)
            if ("${module}.so" in previousModules)
                config = "${config}module ${module}.so\n"

        if ("webrtc_aecm.so" in previousModules)
            config = "${config}module webrtc_aecm.so\n"

        if (Utils.supportedCameras(ctx).isNotEmpty()) {
            config = "${config}module avformat.so\n"
            config = "${config}module selfview.so\n"
            config = "${config}video_selfview pip\n"
        }

        val opusBitRate = previousVariable("opus_bitrate")
        config = if (opusBitRate == "")
            "${config}opus_bitrate 28000\n"
        else
            "${config}opus_bitrate $opusBitRate\n"

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

        val cameraManager: CameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val defaultSizes = arrayListOf("320x240", "640x480", "720x480", "960x720", "1280x720",  "1920x1080")
        for (id in cameraManager.cameraIdList) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(id)
            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT)
                continue
            val streamConfigurationMap = cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
                ?: continue
            val sizes = streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888)
            for (size in sizes)
                if (size.toString() in defaultSizes)
                    videoSizes.add(size.toString())
        }

        val videoFps = previousVariable("video_fps")
        config = if (videoFps != "" && videoFps != "15.0")
            "${config}video_fps $videoFps\n"
        else
            "${config}video_fps 15\n"

        val videoSize = previousVariable("video_size")
        config = if (videoSize !in videoSizes) {
            if ("1280x720" in videoSizes)
                "${config}video_size 1280x720\n"
            else
                "${config}video_size 640x480\n"
        } else {
            "${config}video_size $videoSize\n"
        }

        val toneCountry = previousVariable("tone_country")
        if (toneCountry != "")
            BaresipService.toneCountry = toneCountry
        config = "${config}tone_country ${BaresipService.toneCountry}\n"

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
        for (line in config.split("\n")) {
            val nameValue = line.split(" ")
            if (nameValue.size == 2 && nameValue[0] == name)
                return nameValue[1].trim()
        }
        return ""
    }

    fun variables(name: String): ArrayList<String> {
        val result = ArrayList<String>()
        for (line in config.split("\n")) {
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

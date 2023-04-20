package com.tutpro.baresip

import android.Manifest
import android.content.Context
import java.net.InetAddress
import java.nio.charset.StandardCharsets

object Config {

    private val configPath = BaresipService.filesPath + "/config"
    private var config = String(Utils.getFileContents(configPath)!!, StandardCharsets.ISO_8859_1)

    fun initialize(ctx: Context) {

        config = config.replace("module_tmp uuid.so", "module uuid.so")

        config = config.replace("module_tmp account.so", "module_app account.so")

        config = config.replace("webrtc_aec.so", "webrtc_aecm.so")

        config = config.replace("module zrtp.so", "module gzrtp.so")

        removeLine("module_app contact.so")

        if (!config.contains("module gsm.so")) {
            config = "${config}module gsm.so\n"
        }

        if (config.contains("rtp_stats no"))
            replaceVariable("rtp_stats", "yes")

        if (!config.contains(Regex("ausrc_format s16"))) {
            config = "${config}ausrc_format s16\nauplay_format s16\nauenc_format s16\naudec_format s16\nmodule webrtc_aecm.so\n"
        }

        if (!config.contains("opus_application")) {
            config = "${config}opus_application voip\n"
        }

        if (!config.contains("opus_samplerate")) {
            config = "${config}opus_samplerate 16000\n"
            val accountsPath = BaresipService.filesPath + "/accounts"
            var accounts = String(Utils.getFileContents(accountsPath)!!, StandardCharsets.ISO_8859_1)
            accounts = accounts.replace("opus/48000/1", "opus/16000/1")
            Utils.putFileContents(accountsPath, accounts.toByteArray())
        }

        if (!config.contains("opus_stereo")) {
            config = "${config}opus_stereo no\n"
        }

        if (!config.contains("opus_sprop_stereo")) {
            config = "${config}opus_sprop_stereo no\n"
        }

        if (!config.contains("log_level")) {
            config = "${config}log_level 2\n"
            Log.logLevel = Log.LogLevel.WARN
            BaresipService.logLevel = 2
        } else {
            val ll = variable("log_level")[0].toInt()
            replaceVariable("log_level", "$ll")
            Log.logLevelSet(ll)
            BaresipService.logLevel = ll
        }

        if (!config.contains("call_volume")) {
            config = "${config}call_volume 0\n"
        } else {
            BaresipService.callVolume = variable("call_volume")[0].toInt()
        }

        if (config.contains("audio_delay")) {
            BaresipService.audioDelay = variable("audio_delay")[0].toLong()
        }

        if (config.contains("net_af"))
            BaresipService.addressFamily = variable("net_af")[0]

        if (!config.contains("dyn_dns")) {
            config = "${config}dyn_dns no\n"
        } else {
            if (config.contains(Regex("dyn_dns\\s+yes"))) {
                removeVariable("dns_server")
                for (dnsServer in BaresipService.dnsServers)
                    config = if (Utils.checkIpV4(dnsServer.hostAddress!!))
                        "${config}dns_server ${dnsServer.hostAddress}:53\n"
                    else
                        "${config}dns_server [${dnsServer.hostAddress}]:53\n"
                BaresipService.dynDns = true
            }
        }

        if (!config.contains("audio_buffer_mode")) {
            config = "${config}audio_buffer_mode adaptive\n"
            config = "${config}audio_buffer 40-200\n"
        }

        if (!config.contains("jitter_buffer_type")) {
            config = "${config}jitter_buffer_type adaptive\n"
        } else {
            removeVariable("jitter_buffer_wish")
        }
        
        if (config.contains("contacts_mode")) {
            BaresipService.contactsMode = variable("contacts_mode")[0].lowercase()
            if (BaresipService.contactsMode != "baresip" &&
                    !Utils.checkPermissions(ctx, arrayOf(Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS))) {
                BaresipService.contactsMode = "baresip"
                replaceVariable("contacts_mode", "baresip")
            }
        } else {
            BaresipService.contactsMode = "baresip"
        }

        if (!config.contains("dtls_srtp_use_ec"))
            config = "${config}dtls_srtp_use_ec prime256v1\n"

        replaceVariable("snd_path", "${BaresipService.filesPath}/recordings")

        Utils.putFileContents(configPath, config.toByteArray())
        BaresipService.isConfigInitialized = true
        Log.i(TAG, "Initialized config to '$config'")

    }

    fun variable(name: String): ArrayList<String> {
        val result = ArrayList<String>()
        val lines = config.split("\n")
        for (line in lines) {
            if (line.startsWith(name))
                result.add((line.substring(name.length).trim()).split("# \t")[0])
        }
        return result
    }

    fun addLine(line: String) {
        config += "$line\n"
    }

    fun removeLine(line: String) {
        config = Utils.removeLinesStartingWithString(config, line)
    }

    fun addModuleLine(line: String) {
        // Make sure it goes before first 'module_tmp'
        config = config.replace("module opensles.so", "$line\nmodule opensles.so")
    }

    fun removeVariable(variable: String) {
        config = Utils.removeLinesStartingWithString(config, "$variable ")
    }

    fun replaceVariable(variable: String, value: String) {
        removeVariable(variable)
        addLine("$variable $value")
    }

    fun reset(ctx: Context) {
        Utils.copyAssetToFile(ctx, "config", configPath)
    }

    fun save() {
        var result = ""
        for (line in config.split("\n"))
            if (line.isNotEmpty())
                result = result + line + '\n'
        config = result
        Utils.putFileContents(configPath, config.toByteArray())
        Log.d(TAG, "Saved new config '$result'")
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

package com.tutpro.baresip

import android.content.Context

import java.net.InetAddress
import java.nio.charset.StandardCharsets

object Config {

    private val configPath = BaresipService.filesPath + "/config"
    private var config = String(Utils.getFileContents(configPath)!!, StandardCharsets.ISO_8859_1)

    fun initialize(dnsServers: List<InetAddress>) {

        Log.d("Baresip", "Config is '$config'")

        if (!config.contains("zrtp_hash")) {
            config = "${config}zrtp_hash yes\n"
        }

        if (!config.contains(Regex("ausrc_format s16"))) {
            config = "${config}ausrc_format s16\nauplay_format s16\nauenc_format s16\naudec_format s16\nmodule webrtc_aec.so\n"
        }

        if (config.contains(Regex("#module_app[ ]+mwi.so"))) {
            config = config.replace(Regex("#module_app[ ]+mwi.so"),
                    "module_app mwi.so")
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
            Api.log_level_set(2)
            Log.logLevel = Log.LogLevel.WARN
        } else {
            val ll = variable("log_level")[0].toInt()
            Api.log_level_set(ll)
            Log.logLevelSet(ll)
        }

        val preferIpV6 = variable("prefer_ipv6")
        if (preferIpV6.size == 0) {
            BaresipService.preferIpV6 = false
        } else {
            BaresipService.preferIpV6 = preferIpV6[0] == "yes"
        }

        if (!config.contains("call_volume")) {
            config = "${config}call_volume 0\n"
        } else {
            BaresipService.callVolume = variable("call_volume")[0].toInt()
        }

        if (!config.contains("dyn_dns")) {
            config = "${config}dyn_dns no\n"
        } else {
            if (config.contains(Regex("dyn_dns[ ]+yes"))) {
                for (dnsServer in dnsServers)
                    config = "${config}dns_server ${dnsServer.hostAddress}:53\n"
                BaresipService.dynDns = true
            }
        }

        Log.e("Baresip", "Initialized config to '$config'")
        Utils.putFileContents(configPath, config.toByteArray())

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
            if (line.length > 0)
                result = result + line + '\n'
        config = result
        Utils.putFileContents(configPath, config.toByteArray())
        Log.d("Baresip", "New config '$result'")
        // Api.reload_config()
    }

    fun updateDnsServers(dnsServers: List<InetAddress>): Int {
        var servers = ""
        for (dnsServer in dnsServers) {
            var address = dnsServer.hostAddress
            if (Utils.checkIpV4(address))
                address = "${address}:53"
            else
                address = "[${address}]:53"
            if (servers == "")
                servers = address
            else
                servers = "${servers},${address}"
        }
        return Api.net_use_nameserver(servers)
    }


    fun updateNetAddress(ipAddress: String, prefer: Boolean): Int {
        return Api.net_set_address(ipAddress, prefer)
    }

}

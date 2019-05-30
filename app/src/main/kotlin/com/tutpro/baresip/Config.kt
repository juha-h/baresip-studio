package com.tutpro.baresip

import android.content.Context
import java.io.File
import java.net.InetAddress

object Config {

    private val path = BaresipService.filesPath + "/config"
    private val file = File(path)
    private var config = Utils.getFileContents(file)

    fun initialize(dnsServers: List<InetAddress>) {
        var write = false
        if (!config.contains("zrtp_hash")) {
            config = "${config}zrtp_hash yes\n"
            write = true
        }
        if (!config.contains(Regex("ausrc_format s16"))) {
            config = "${config}ausrc_format s16\nauplay_format s16\nauenc_format s16\naudec_format s16\nmodule webrtc_aec.so\n"
            write = true
        }
        if (!config.contains(Regex("module[ ]+g7221.so"))) {
            config = "${config}module g7221.so\n"
            write = true
        }
        if (!config.contains(Regex("module[ ]+ilbc.so"))) {
            config = "${config}module ilbc.so\n"
            write = true
        }
        if (config.contains(Regex("#module_app[ ]+mwi.so"))) {
            config = config.replace(Regex("#module_app[ ]+mwi.so"),
                    "module_app mwi.so")
            write = true
        }
        if (!config.contains("opus_application")) {
            config = "${config}opus_application voip\n"
            write = true
        }
        if (!config.contains("log_level")) {
            config = "${config}log_level 2\n"
            Api.log_level_set(2)
            Log.logLevel = Log.LogLevel.WARN
            write = true
        } else {
            val ll = variable("log_level")[0].toInt()
            Api.log_level_set(ll)
            Log.logLevelSet(ll)
        }
        if (!config.contains("prefer_ipv6")) {
            config = "prefer_ipv6 no\n${config}"
            write = true
        }
        if (!config.contains("call_volume")) {
            config = "${config}call_volume 0\n"
            write = true
        } else {
            BaresipService.callVolume = variable("call_volume")[0].toInt()
        }
        if (!config.contains("dyn_dns")) {
            config = "${config}dyn_dns no\n"
            write = true
        } else {
            if (config.contains(Regex("dyn_dns[ ]+yes"))) {
                for (dnsServer in dnsServers)
                    config = "${config}dns_server ${dnsServer.hostAddress}:53\n"
                BaresipService.dynDns = true
                write = true
            }
        }
        if (write) {
            Log.e("Baresip", "Writing '$config'")
            Utils.putFileContents(file, config)
        }
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

    fun add(variable: String, value: String) {
        config += "$variable $value\n"
    }

    fun remove(variable: String) {
        config = Utils.removeLinesStartingWithName(config, variable)
        Utils.putFileContents(file, config)
    }

    fun replace(variable: String, value: String) {
        remove(variable)
        add(variable, value)
    }

    fun reset(ctx: Context) {
        Utils.copyAssetToFile(ctx, "config", path)
    }

    fun save() {
        var result = ""
        for (line in config.split("\n"))
            if (line.length > 0)
                result = result + line + '\n'
        config = result
        Utils.putFileContents(file, config)
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
        return Api.dnsc_srv_set(servers)
    }
}

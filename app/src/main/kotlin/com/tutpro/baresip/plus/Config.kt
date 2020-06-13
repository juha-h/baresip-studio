package com.tutpro.baresip.plus

import android.content.Context

import java.net.InetAddress
import java.nio.charset.StandardCharsets

object Config {

    private val configPath = BaresipService.filesPath + "/config"
    private var config = String(Utils.getFileContents(configPath)!!, StandardCharsets.ISO_8859_1)

    fun initialize() {

        Log.d("Baresip", "Config is '$config'")

        if (BaresipService.cameraAvailable) {
            if (!config.contains("module avformat.so"))
                 config = "${config}module avformat.so\nmodule selfview.so\n"
        } else {
            removeLine("module avformat.so")
            removeLine("module selfview.so")
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

        if (!config.contains("dyn_dns")) {
            config = "${config}dyn_dns no\n"
        } else {
            if (config.contains(Regex("dyn_dns[ ]+yes"))) {
                removeVariable("dns_server")
                for (dnsServer in BaresipService.dnsServers)
                    if (Utils.checkIpV4(dnsServer.hostAddress))
                        config = "${config}dns_server ${dnsServer.hostAddress}:53\n"
                    else
                        config = "${config}dns_server [${dnsServer.hostAddress}]:53\n"
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

}

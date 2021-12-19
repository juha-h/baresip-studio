package com.tutpro.baresip.plus

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

        if (config.contains("rtp_stats no"))
            replaceVariable("rtp_stats", "yes")

        if (Utils.supportedCameras(ctx).isNotEmpty()) {
            if (!config.contains("module avformat.so")) {
                addModuleLine("module avformat.so")
                addModuleLine("module selfview.so")
            }
        } else {
            removeLine("module avformat.so")
            removeLine("module selfview.so")
        }

        if (config.contains("module av1.so"))
            removeLine("module av1.so")

        if (!config.contains("module snapshot.so"))
            addModuleLine("module snapshot.so")

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
                    config = if (Utils.checkIpV4(dnsServer.hostAddress!!))
                        "${config}dns_server ${dnsServer.hostAddress}:53\n"
                    else
                        "${config}dns_server [${dnsServer.hostAddress}]:53\n"
                BaresipService.dynDns = true
            }
        }

        if (!config.contains("jitter_buffer_type")) {
            config = "${config}jitter_buffer_type adaptive\n"
            config = "${config}jitter_buffer_wish 6\n"
        }

        removeLine("avcodec")

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

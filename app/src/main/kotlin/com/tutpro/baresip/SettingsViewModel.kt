package com.tutpro.baresip

import android.app.role.RoleManager
import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Context.ROLE_SERVICE
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class SettingsViewModel: ViewModel() {
    
    val autoStart = MutableStateFlow(false)
    val listenAddress = MutableStateFlow("")
    val addressFamily = MutableStateFlow("")

    val transportProtocols = MutableStateFlow("")
    var oldTransportProtocols = ""

    val dnsServers = MutableStateFlow("")
    var oldDnsServers = ""

    val tlsCertificateFile = MutableStateFlow(false)
    val verifyServer = MutableStateFlow(false)
    val caFile = MutableStateFlow(false)
    val userAgent = MutableStateFlow("")
    val contactsMode = MutableStateFlow("")
    val ringtoneUri = MutableStateFlow("")
    val batteryOptimizations = MutableStateFlow(false)
    val darkTheme = MutableStateFlow(false)
    val dynamicColors = MutableStateFlow(false)
    val colorblind = MutableStateFlow(false)
    val proximitySensing = MutableStateFlow(false)
    val defaultDialer = MutableStateFlow(false)
    val debug = MutableStateFlow(false)
    val sipTrace = MutableStateFlow(false)

    private var isLoaded = false

    fun loadSettings(ctx: Context) {
        if (isLoaded) return else isLoaded = true

        oldTransportProtocols = Config.variable("sip_transports")
        oldDnsServers = if (Config.variable("dyn_dns") == "yes")
            ""
        else {
            val servers = Config.variables("dns_server")
            var serverList = ""
            for (server in servers)
                serverList += ", $server"
            serverList.trimStart(',').trimStart(' ')
        }

        autoStart.value = Config.variable("auto_start") == "yes"

        listenAddress.value = Config.variable("sip_listen")

        val familyValues = listOf("",  "ipv4", "ipv6")
        val itemPosition =
            mutableIntStateOf(familyValues.indexOf(Config.variable("net_af").lowercase()))
        addressFamily.value = familyValues[itemPosition.intValue]

        transportProtocols.value = oldTransportProtocols

        dnsServers.value = oldDnsServers

        tlsCertificateFile.value = File(BaresipService.filesPath + "/cert.pem").exists()

        verifyServer.value = Config.variable("sip_verify_server") == "yes"

        caFile.value = File(BaresipService.filesPath + "/ca_certs.crt").exists()

        userAgent.value = Config.variable("user_agent")

        contactsMode.value = Config.variable("contacts_mode").lowercase()

        ringtoneUri.value = if (Preferences(ctx).ringtoneUri == "")
            RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE).toString()
        else
            Preferences(ctx).ringtoneUri!!

        val powerManager = ctx.getSystemService(POWER_SERVICE) as PowerManager
        batteryOptimizations.value = !powerManager.isIgnoringBatteryOptimizations(ctx.packageName)

        darkTheme.value = Preferences(ctx).displayTheme == AppCompatDelegate.MODE_NIGHT_YES

        dynamicColors.value = BaresipService.dynamicColors.value

        colorblind.value = Config.variable("colorblind") == "yes"

        proximitySensing.value = Config.variable("proximity_sensing") == "yes"

        if (Build.VERSION.SDK_INT >= 29) {
            val roleManager = ctx.getSystemService(ROLE_SERVICE) as RoleManager
            defaultDialer.value = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        }

        debug.value = Config.variable("log_level") == "0"

        sipTrace.value = BaresipService.sipTrace
    }

}
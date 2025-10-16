package com.tutpro.baresip

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class AccountViewModel: ViewModel() {

    val nickName = MutableStateFlow("")
    val displayName = MutableStateFlow("")
    val authUser = MutableStateFlow("")
    val authPass = MutableStateFlow("")
    val outbound1 = MutableStateFlow("")
    val outbound2 = MutableStateFlow("")
    val register = MutableStateFlow(false)
    val regInt = MutableStateFlow("")
    val mediaEnc = MutableStateFlow("")
    val mediaNat = MutableStateFlow("")
    val stunServer = MutableStateFlow("")
    val stunUser = MutableStateFlow("")
    val stunPass = MutableStateFlow("")
    val rtcpMux = MutableStateFlow(false)
    val rel100 = MutableStateFlow(false)
    val dtmfMode = MutableStateFlow(0)
    val answerMode = MutableStateFlow(0)
    val autoRedirect = MutableStateFlow(false)
    val vmUri = MutableStateFlow("")
    val countryCode = MutableStateFlow("")
    val telProvider = MutableStateFlow("")
    val defaultAccount = MutableStateFlow(false)
    val numericKeypad = MutableStateFlow(false)

    private var isLoaded = false

    fun loadAccount(acc: Account) {
        if (isLoaded) return else isLoaded = true
        nickName.value = acc.nickName
        displayName.value = acc.displayName
        authUser.value = acc.authUser
        authPass.value = if (BaresipService.aorPasswords[acc.aor] == null && acc.authPass != NO_AUTH_PASS)
            acc.authPass
        else
            ""
        outbound1.value = if (acc.outbound.isNotEmpty()) acc.outbound[0] else ""
        outbound2.value = if (acc.outbound.size > 1) acc.outbound[1] else ""
        register.value = acc.regint > 0
        regInt.value = acc.configuredRegInt.toString()
        mediaEnc.value = acc.mediaEnc
        mediaNat.value = acc.mediaNat
        stunServer.value = acc.stunServer
        stunUser.value = acc.stunUser
        stunPass.value = acc.stunPass
        rtcpMux.value = acc.rtcpMux
        rel100.value = acc.rel100Mode == Api.REL100_ENABLED
        dtmfMode.value = acc.dtmfMode
        answerMode.value = acc.answerMode
        autoRedirect.value = acc.autoRedirect
        vmUri.value = acc.vmUri
        countryCode.value = acc.countryCode
        telProvider.value = acc.telProvider
        numericKeypad.value = acc.numericKeypad
        defaultAccount.value = UserAgent.findAorIndex(acc.aor)!! == 0
    }
}

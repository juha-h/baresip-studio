package com.tutpro.baresip.plus

import android.content.Context
import android.media.RingtoneManager
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class AudioViewModel : ViewModel() {

    val speakerPhone = MutableStateFlow(false)
    val callVolume = MutableStateFlow(0)
    val micGain = MutableStateFlow("")
    val audioModules = MutableStateFlow(mutableMapOf<String, Boolean>())
    val opusBitrate = MutableStateFlow("")
    val opusPacketLoss = MutableStateFlow("")
    val audioDelay = MutableStateFlow("")
    val toneCountry = MutableStateFlow("")
    val ringtoneUri = MutableStateFlow("")

    private var isLoaded = false
    var oldSpeakerPhone = false
    var oldCallVolume = 0
    var oldMicGain = ""
    var oldAudioModules = ArrayList<String>()
    var oldOpusBitrate = ""
    var oldOpusPacketLoss = ""
    var oldAudioDelay = ""
    var oldToneCountry = ""
    var oldRingtoneUri = ""

    fun loadSettings(ctx: Context) {
        if (isLoaded || !Config.isInitialized()) return else isLoaded = true

        oldSpeakerPhone = Config.variable("speaker_phone") == "yes"
        speakerPhone.value = oldSpeakerPhone

        oldCallVolume = BaresipService.callVolume
        callVolume.value = oldCallVolume

        if (!BaresipService.agcAvailable) {
            oldMicGain = Config.variable("augain")
            micGain.value = oldMicGain
        }

        oldAudioModules = Config.variables("module")
        val modulesMap = mutableMapOf<String, Boolean>()
        for (module in Config.audioModules)
            modulesMap[module] = oldAudioModules.contains("${module}.so")

        audioModules.value = modulesMap

        oldOpusBitrate = Config.variable("opus_bitrate")
        opusBitrate.value = oldOpusBitrate

        oldOpusPacketLoss = Config.variable("opus_packet_loss")
        opusPacketLoss.value = oldOpusPacketLoss

        oldAudioDelay = Config.variable("audio_delay")
        if (oldAudioDelay == "") oldAudioDelay = BaresipService.audioDelay.toString()
        audioDelay.value = oldAudioDelay

        oldToneCountry = BaresipService.toneCountry
        toneCountry.value = oldToneCountry

        oldRingtoneUri = Preferences(ctx).ringtoneUri ?: ""
        ringtoneUri.value = oldRingtoneUri
    }

    fun saveSettings(ctx: Context): Result {
        var restart = false
        var save = false

        if (Preferences(ctx).ringtoneUri != ringtoneUri.value) {
            Preferences(ctx).ringtoneUri = ringtoneUri.value
            BaresipService.rt = RingtoneManager.getRingtone(ctx, ringtoneUri.value.toUri())
        }

        if (BaresipService.toneCountry != toneCountry.value) {
            BaresipService.toneCountry = toneCountry.value
            Config.replaceVariable("tone_country", toneCountry.value)
            save = true
        }

        if (BaresipService.callVolume != callVolume.value) {
            BaresipService.callVolume = callVolume.value
            Config.replaceVariable("call_volume", callVolume.value.toString())
            save = true
        }

        if (!BaresipService.agcAvailable) {
            var gain = micGain.value.trim()
            if (gain.isNotEmpty() && !gain.contains(".")) gain = "$gain.0"
            if (gain != oldMicGain) {
                if (!checkMicGain(gain)) return Result.ERROR
                if (gain == "1.0") {
                    Api.module_unload("augain")
                    Config.removeVariableValue("module", "augain.so")
                    Config.replaceVariable("augain", "1.0")
                }
                else {
                    if (oldMicGain == "1.0") {
                        if (Api.module_load("augain") != 0) return Result.ERROR
                        Config.addVariable("module", "augain.so")
                    }
                    Config.replaceVariable("augain", gain)
                    Api.cmd_exec("augain $gain")
                }
                save = true
            }
        }

        if (speakerPhone.value != oldSpeakerPhone) {
            Config.replaceVariable("speaker_phone", if (speakerPhone.value) "yes" else "no")
            BaresipService.speakerPhoneAuto = speakerPhone.value
            save = true
        }

        for (module in Config.audioModules) {
            val enabled = audioModules.value[module] ?: false
            if (enabled != oldAudioModules.contains("${module}.so")) {
                if (enabled) {
                    if (Api.module_load("${module}.so") != 0) return Result.ERROR
                    Config.addVariable("module", "${module}.so")
                }
                else {
                    Api.module_unload("${module}.so")
                    Config.removeVariableValue("module", "${module}.so")
                    for (ua in BaresipService.uas.value)
                        ua.account.removeAudioCodecs(module)
                    Account.saveAccounts()
                }
                save = true
            }
        }

        if (opusBitrate.value != oldOpusBitrate) {
            if (!checkOpusBitRate(opusBitrate.value)) return Result.ERROR
            Config.replaceVariable("opus_bitrate", opusBitrate.value)
            restart = true
            save = true
        }

        if (opusPacketLoss.value != oldOpusPacketLoss) {
            if (!checkOpusPacketLoss(opusPacketLoss.value)) return Result.ERROR
            Config.replaceVariable("opus_packet_loss", opusPacketLoss.value)
            restart = true
            save = true
        }

        val delay = audioDelay.value.trim()
        if (delay != oldAudioDelay) {
            if (!checkAudioDelay(delay)) return Result.ERROR
            Config.replaceVariable("audio_delay", delay)
            BaresipService.audioDelay = delay.toLong()
            save = true
        }

        if (save) Config.save()

        return if (restart) Result.RESTART else Result.OK
    }

    private fun checkMicGain(micGain: String): Boolean {
        val number = micGain.toDoubleOrNull() ?: return false
        return number >= 1.0
    }

    private fun checkOpusBitRate(opusBitRate: String): Boolean {
        val number = opusBitRate.toIntOrNull() ?: return false
        return (number >= 6000) && (number <= 510000)
    }

    private fun checkOpusPacketLoss(opusPacketLoss: String): Boolean {
        val number = opusPacketLoss.toIntOrNull() ?: return false
        return (number >= 0) && (number <= 100)
    }

    private fun checkAudioDelay(audioDelay: String): Boolean {
        val number = audioDelay.toIntOrNull() ?: return false
        return (number >= 100) && (number <= 3000)
    }
}

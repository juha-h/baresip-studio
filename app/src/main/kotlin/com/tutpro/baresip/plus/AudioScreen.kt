package com.tutpro.baresip.plus

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar

enum class Result {
    OK, ERROR, RESTART
}

fun NavGraphBuilder.audioScreenRoute(
    navController: NavController,
) {
    composable("audio") {
        val ctx = LocalContext.current
        AudioScreen(
            onBack = {
                navController.navigateUp()
            },
            checkOnClick = {
                when (checkOnClick(ctx)) {
                    Result.OK -> navController.navigateUp()
                    Result.RESTART -> {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("audio_settings_result", true)
                        navController.navigateUp()
                    }
                    Result.ERROR -> {}
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioScreen(
    onBack: () -> Unit,
    checkOnClick: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.audio_settings),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    actions = {
                        IconButton(onClick = checkOnClick) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Check"
                            )
                        }
                    },
                )
            }
        }
    ) { contentPadding ->
        AudioContent(contentPadding)
    }
}

private var newCallVolume = BaresipService.callVolume
private var oldMicGain = ""
private var newMicGain = ""
private var oldSpeakerPhone = BaresipService.speakerPhone
private var newSpeakerPhone = oldSpeakerPhone
private var oldAudioModules = ArrayList<String>()
private var newAudioModules = mutableMapOf<String, Boolean>()
private var oldOpusBitrate = ""
private var newOpusBitrate = oldOpusBitrate
private var oldOpusPacketLoss = ""
private var newOpusPacketLoss = oldOpusPacketLoss
private var newAudioDelay = BaresipService.audioDelay.toString()
private var newToneCountry = BaresipService.toneCountry

private var save = false

private val alertTitle = mutableStateOf("")
private val alertMessage = mutableStateOf("")
private val showAlert = mutableStateOf(false)

@Composable
private fun AudioContent(contentPadding: PaddingValues) {

    oldAudioModules = Config.variables("module")
    oldOpusBitrate = Config.variable("opus_bitrate")
    oldOpusPacketLoss = Config.variable("opus_packet_loss")
    if (!BaresipService.agcAvailable)
        oldMicGain = Config.variable("augain")

    if (showAlert.value) {
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            positiveButtonText = stringResource(R.string.ok),
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 4.dp)
            .verticalScrollbar(scrollState)
            .verticalScroll(state = scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CallVolume()
        MicGain()
        SpeakerPhone()
        AudioModules()
        OpusBitRate()
        OpusPacketLoss()
        AudioDelay()
        ToneCountry()
    }
}

@Composable
private fun CallVolume() {
    Row(
        Modifier.fillMaxWidth().padding(end=10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val defaultCallVolumeTitle = stringResource(R.string.default_call_volume)
        val defaultCallVolumeHelp = stringResource(R.string.default_call_volume_help)
        Text(text = defaultCallVolumeTitle,
            modifier = Modifier.weight(1f)
                .clickable {
                    alertTitle.value = defaultCallVolumeTitle
                    alertMessage.value = defaultCallVolumeHelp
                    showAlert.value = true
                },
            fontSize = 18.sp)
        val isDropDownExpanded = remember {
            mutableStateOf(false)
        }
        val volNames = listOf("--",  "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        val volValues = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val itemPosition = remember {
            mutableIntStateOf(volValues.indexOf(BaresipService.callVolume))
        }
        Box {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    isDropDownExpanded.value = true
                }
            ) {
                Text(text = volNames[itemPosition.intValue])
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
            DropdownMenu(
                expanded = isDropDownExpanded.value,
                onDismissRequest = {
                    isDropDownExpanded.value = false
                }) {
                volNames.forEachIndexed { index, vol ->
                    DropdownMenuItem(text = {
                        Text(text = vol)
                    },
                        onClick = {
                            isDropDownExpanded.value = false
                            itemPosition.intValue = index
                            newCallVolume = volValues[index]
                        })
                    if (index < 10)
                        HorizontalDivider(thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun MicGain() {
    if (!BaresipService.agcAvailable)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val microphoneGainTitle = stringResource(R.string.microphone_gain)
            val microphoneGainHelp = stringResource(R.string.microphone_gain_help)
            var micGain by remember { mutableStateOf(oldMicGain) }
            newMicGain = micGain
            OutlinedTextField(
                value = micGain,
                placeholder = { Text(microphoneGainTitle) },
                onValueChange = {
                    micGain = it
                    newMicGain = micGain
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = microphoneGainTitle
                        alertMessage.value = microphoneGainHelp
                        showAlert.value = true
                    },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                label = { Text(microphoneGainTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
}

@Composable
private fun SpeakerPhone() {
    Row(
        Modifier.fillMaxWidth().padding(end=10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val speakerPhoneTitle = stringResource(R.string.speaker_phone)
        val speakerPhoneHelp = stringResource(R.string.speaker_phone_help)
        Text(text = speakerPhoneTitle,
            modifier = Modifier.weight(1f)
                .clickable {
                    alertTitle.value = speakerPhoneTitle
                    alertMessage.value = speakerPhoneHelp
                    showAlert.value = true
                },
            fontSize = 18.sp)
        var speakerPhone by remember { mutableStateOf(BaresipService.speakerPhone) }
        Switch(
            checked = speakerPhone,
            onCheckedChange = {
                speakerPhone = it
                newSpeakerPhone = speakerPhone
            }
        )
    }
}

@Composable
private fun AudioModules() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        val audioModulesTitle = stringResource(R.string.audio_modules_title)
        val audioModulesHelp = stringResource(R.string.audio_modules_help)
        Text(text = audioModulesTitle,
            fontSize = 18.sp,
            modifier = Modifier.clickable {
                alertTitle.value = audioModulesTitle
                alertMessage.value = audioModulesHelp
                showAlert.value = true
            })
        for (module in Config.audioModules) {
            Row(horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 18.dp, end = 10.dp)
            ) {
                Text(text = String.format(stringResource(R.string.bullet_item), module), fontSize = 18.sp)
                Spacer(modifier = Modifier.weight(1f))
                var checked by remember { mutableStateOf(oldAudioModules.contains("${module}.so")) }
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        newAudioModules[module] = checked
                    }
                )
            }
        }
    }
}

@Composable
private fun OpusBitRate() {
    Row(
        Modifier.fillMaxWidth().padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val opusBitRateTitle = stringResource(R.string.opus_bit_rate)
        val opusBitRateHelp = stringResource(R.string.opus_bit_rate_help)
        var opusBitrate by remember { mutableStateOf(oldOpusBitrate) }
        newOpusBitrate = opusBitrate
        OutlinedTextField(
            value = opusBitrate,
            placeholder = { Text(opusBitRateTitle) },
            onValueChange = {
                opusBitrate = it
                newOpusBitrate = opusBitrate
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    alertTitle.value = opusBitRateTitle
                    alertMessage.value = opusBitRateHelp
                    showAlert.value = true
                },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
            label = { Text(opusBitRateTitle) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
    }
}

@Composable
private fun OpusPacketLoss() {
    Row(
        Modifier.fillMaxWidth().padding(end = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val opusPacketLossTitle = stringResource(R.string.opus_packet_loss)
        val opusPacketLossHelp = stringResource(R.string.opus_packet_loss_help)
        var opusPacketLoss by remember { mutableStateOf(oldOpusPacketLoss) }
        newOpusPacketLoss = opusPacketLoss
        OutlinedTextField(
            value = opusPacketLoss,
            placeholder = { Text(opusPacketLossTitle) },
            onValueChange = {
                opusPacketLoss = it
                newOpusPacketLoss = opusPacketLoss
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    alertTitle.value = opusPacketLossTitle
                    alertMessage.value = opusPacketLossHelp
                    showAlert.value = true
                },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
            label = { Text(opusPacketLossTitle) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
    }
}

@Composable
private fun AudioDelay() {
    Row(
        Modifier.fillMaxWidth().padding(end = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val audioDelayTitle = stringResource(R.string.audio_delay)
        val audioDelayHelp = stringResource(R.string.audio_delay_help)
        var audioDelay by remember { mutableStateOf(BaresipService.audioDelay.toString()) }
        newAudioDelay = audioDelay
        OutlinedTextField(
            value = audioDelay,
            placeholder = { Text(audioDelayTitle) },
            onValueChange = {
                audioDelay = it
                newAudioDelay = audioDelay
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    alertTitle.value = audioDelayTitle
                    alertMessage.value = audioDelayHelp
                    showAlert.value = true
                },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
            label = { Text(audioDelayTitle) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
    }
}

@Composable
private fun ToneCountry() {
    Row(
        Modifier.fillMaxWidth().padding(end=10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val toneCountryTitle = stringResource(R.string.tone_country)
        val toneCountryHelp = stringResource(R.string.tone_country_help)
        Text(text = toneCountryTitle,
            modifier = Modifier.weight(1f)
                .clickable {
                    alertTitle.value = toneCountryTitle
                    alertMessage.value = toneCountryHelp
                    showAlert.value = true
                },
            fontSize = 18.sp)
        val isDropDownExpanded = remember {
            mutableStateOf(false)
        }
        val countryNames = arrayListOf("BG", "BR", "DE", "CZ", "ES", "FI", "FR", "GB", "JP", "NO", "NZ", "SE", "RU", "US")
        val countryValues = arrayListOf("bg", "br", "de", "cz", "es", "fi", "fr", "uk", "jp", "no", "nz", "se", "ru", "us")
        val itemPosition = remember {
            mutableIntStateOf(countryValues.indexOf(BaresipService.toneCountry))
        }
        Box {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    isDropDownExpanded.value = true
                }
            ) {
                Text(text = countryNames[itemPosition.intValue])
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
            DropdownMenu(
                expanded = isDropDownExpanded.value,
                onDismissRequest = {
                    isDropDownExpanded.value = false
                }) {
                countryNames.forEachIndexed { index, name ->
                    DropdownMenuItem(text = {
                        Text(text = name)
                    },
                        onClick = {
                            isDropDownExpanded.value = false
                            itemPosition.intValue = index
                            newToneCountry = countryValues[index]
                        })
                    if (index < 10)
                        HorizontalDivider(thickness = 1.dp)
                }
            }
        }
    }
}

private fun checkOnClick(ctx: Context): Result {

    var restart = false

    if (BaresipService.callVolume != newCallVolume) {
        BaresipService.callVolume = newCallVolume
        Config.replaceVariable("call_volume", newCallVolume.toString())
        save = true
    }

    if (!BaresipService.agcAvailable) {
        var gain = newMicGain.trim()
        if (!gain.contains("."))
            gain = "$gain.0"
        if (gain != oldMicGain) {
            if (!checkMicGain(gain)) {
                alertTitle.value = ctx.getString(R.string.notice)
                alertMessage.value = "${ctx.getString(R.string.invalid_microphone_gain)}: $gain."
                showAlert.value = true
                return Result.ERROR
            }
            if (gain == "1.0") {
                Api.module_unload("augain")
                Config.removeVariableValue("module", "augain.so")
                Config.replaceVariable("augain", "1.0")
            } else {
                if (oldMicGain == "1.0") {
                    if (Api.module_load("augain") != 0) {
                        alertTitle.value = ctx.getString(R.string.error)
                        alertMessage.value = ctx.getString(R.string.failed_to_load_module) + ": augain.so"
                        showAlert.value = true
                        return Result.ERROR
                    }
                    Config.addVariable("module", "augain.so")
                }
                Config.replaceVariable("augain", gain)
                Api.cmd_exec("augain $gain")
            }
            save = true
        }
    }

    if (newSpeakerPhone != BaresipService.speakerPhone) {
        BaresipService.speakerPhone = newSpeakerPhone
        Config.replaceVariable("speaker_phone",
            if (BaresipService.speakerPhone) "yes" else "no")
        save = true
    }

    for (module in Config.audioModules) {
        if (newAudioModules[module] != null) {
            if (newAudioModules[module]!!) {
                if (!oldAudioModules.contains("${module}.so")) {
                    if (Api.module_load("${module}.so") != 0) {
                        alertTitle.value = ctx.getString(R.string.error)
                        alertMessage.value = "${ctx.getString(R.string.failed_to_load_module)}: ${module}.so"
                        showAlert.value = true
                        return Result.ERROR
                    }
                    Config.addVariable("module", "${module}.so")
                    save = true
                }
            } else if (oldAudioModules.contains("${module}.so")) {
                Api.module_unload("${module}.so")
                Config.removeVariableValue("module", "${module}.so")
                for (ua in BaresipService.uas.value)
                    ua.account.removeAudioCodecs(module)
                Account.saveAccounts()
                save = true
            }
        }
    }

    if (newOpusBitrate != oldOpusBitrate) {
        if (!checkOpusBitRate(newOpusBitrate)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = "${ctx.getString(R.string.invalid_opus_bitrate)}: $newOpusBitrate."
            showAlert.value = true
            return Result.ERROR
        }
        Config.replaceVariable("opus_bitrate", newOpusBitrate)
        restart = true
        save = true
    }

    if (newOpusPacketLoss != oldOpusPacketLoss) {
        if (!checkOpusPacketLoss(newOpusPacketLoss)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = "${ctx.getString(R.string.invalid_opus_packet_loss)}: $newOpusPacketLoss"
            showAlert.value = true
            return Result.ERROR
        }
        Config.replaceVariable("opus_packet_loss", newOpusPacketLoss)
        restart = true
        save = true
    }

    val audioDelay = newAudioDelay.trim()
    if (audioDelay != BaresipService.audioDelay.toString()) {
        if (!checkAudioDelay(audioDelay)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = String.format(ctx.getString(R.string.invalid_audio_delay), audioDelay)
            showAlert.value = true
            return Result.ERROR
        }
        Config.replaceVariable("audio_delay", audioDelay)
        BaresipService.audioDelay = audioDelay.toLong()
        save = true
    }

    if (BaresipService.toneCountry != newToneCountry) {
        BaresipService.toneCountry = newToneCountry
        Config.replaceVariable("tone_country", newToneCountry)
        save = true
    }

    if (save) Config.save()

    return if (restart) Result.RESTART else Result.OK
}

private fun checkMicGain(micGain: String): Boolean {
    val number =
        try {
            micGain.toDouble()
        } catch (_: NumberFormatException) {
            return false
        }
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


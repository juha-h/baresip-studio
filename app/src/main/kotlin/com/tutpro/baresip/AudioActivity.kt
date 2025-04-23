package com.tutpro.baresip

import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.LabelText
import com.tutpro.baresip.CustomElements.verticalScrollbar

class AudioActivity : ComponentActivity() {

    private var save = false
    private var restart = false
    private var oldCallVolume = BaresipService.callVolume
    private var newCallVolume = oldCallVolume
    private var oldMicGain = ""
    private var newMicGain = ""
    private var newSpeakerPhone = BaresipService.speakerPhone
    private val modules = Config.variables("module")
    private var newAudioModules = mutableMapOf<String, Boolean>()
    private var oldOpusBitrate = Config.variable("opus_bitrate")
    private var newOpusBitrate = oldOpusBitrate
    private var oldOpusPacketLoss = Config.variable("opus_packet_loss")
    private var newOpusPacketLoss = oldOpusPacketLoss
    private var newAudioDelay = BaresipService.audioDelay.toString()
    private var newToneCountry = BaresipService.toneCountry
    private var arrowTint = Color.Unspecified

    private val alertTitle = mutableStateOf("")
    private val alertMessage = mutableStateOf("")
    private val showAlert = mutableStateOf(false)

    private var backInvokedCallback: OnBackInvokedCallback? = null
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    @RequiresApi(33)
    private fun registerBackInvokedCallback() {
        backInvokedCallback = OnBackInvokedCallback { goBack() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            backInvokedCallback!!
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33)
            registerBackInvokedCallback()
        else {
            onBackPressedCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goBack()
                }
            }
            onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        }

        val title = String.format(getString(R.string.configuration))

        Utils.addActivity("audio")

        if (!BaresipService.agcAvailable)
            oldMicGain = Config.variable("augain")

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    AudioScreen(title) { goBack() }
                }
            }
        }
    }

    @Composable
    fun AudioScreen(title: String, navigateBack: () -> Unit) {
        Scaffold(
            modifier = Modifier
                .fillMaxHeight()
                .imePadding()
                .safeDrawingPadding(),
            containerColor = LocalCustomColors.current.background,
            topBar = { TopAppBar(title, navigateBack) },
            content = { contentPadding ->
                AudioContent(contentPadding)
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopAppBar(title: String, navigateBack: () -> Unit) {
        androidx.compose.material3.TopAppBar(
            title = {
                Text(
                    text = title,
                    color = LocalCustomColors.current.light,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.mediumTopAppBarColors(
                containerColor = LocalCustomColors.current.primary
            ),
            navigationIcon = {
                IconButton(onClick = navigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = LocalCustomColors.current.light
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    checkOnClick()
                }) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        tint = LocalCustomColors.current.light,
                        contentDescription = "Check"
                    )
                }
            }
        )
    }

    @Composable
    fun AudioContent(contentPadding: PaddingValues) {

        arrowTint = if (BaresipService.darkTheme.value)
            LocalCustomColors.current.grayLight
        else
            LocalCustomColors.current.black

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
                .imePadding()
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
            Text(text = stringResource(R.string.default_call_volume),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.default_call_volume)
                        alertMessage.value = getString(R.string.default_call_volume_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val volNames = listOf("--",  "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
            val volValues = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            val itemPosition = remember {
                mutableIntStateOf(volValues.indexOf(oldCallVolume))
            }
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = volNames[itemPosition.intValue],
                        color = LocalCustomColors.current.itemText)
                    CustomElements.DrawDrawable(R.drawable.arrow_drop_down,
                        tint = arrowTint)
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
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = LocalCustomColors.current.itemText
                            )
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
                var micGain by remember { mutableStateOf(oldMicGain) }
                newMicGain = micGain
                OutlinedTextField(
                    value = micGain,
                    placeholder = { Text(stringResource(R.string.microphone_gain)) },
                    onValueChange = {
                        micGain = it
                        newMicGain = micGain
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            alertTitle.value = getString(R.string.microphone_gain)
                            alertMessage.value = getString(R.string.microphone_gain_help)
                            showAlert.value = true
                        },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 18.sp, color = LocalCustomColors.current.itemText
                    ),
                    label = { LabelText(stringResource(R.string.microphone_gain)) },
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
            Text(text = stringResource(R.string.speaker_phone),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.speaker_phone)
                        alertMessage.value = getString(R.string.speaker_phone_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
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
            Text(text = stringResource(R.string.audio_modules_title),
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp,
                modifier = Modifier.clickable {
                    alertTitle.value = getString(R.string.audio_modules_title)
                    alertMessage.value = getString(R.string.audio_modules_help)
                    showAlert.value = true
                })
            for (module in audioModules) {
                Row(horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 18.dp, end = 10.dp)
                ) {
                    Text(text = String.format(getString(R.string.bullet_item), module),
                        color = LocalCustomColors.current.itemText,
                        fontSize = 18.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    var checked by remember { mutableStateOf(modules.contains("${module}.so")) }
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
            var opusBitrate by remember { mutableStateOf(oldOpusBitrate) }
            newOpusBitrate = opusBitrate
            OutlinedTextField(
                value = opusBitrate,
                placeholder = { Text(stringResource(R.string.opus_bit_rate)) },
                onValueChange = {
                    opusBitrate = it
                    newOpusBitrate = opusBitrate
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = getString(R.string.opus_bit_rate)
                        alertMessage.value = getString(R.string.opus_bit_rate_help)
                        showAlert.value = true
                    },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.opus_bit_rate)) },
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
            var opusPacketLoss by remember { mutableStateOf(oldOpusPacketLoss) }
            newOpusPacketLoss = opusPacketLoss
            OutlinedTextField(
                value = opusPacketLoss,
                placeholder = { Text(stringResource(R.string.opus_packet_loss)) },
                onValueChange = {
                    opusPacketLoss = it
                    newOpusPacketLoss = opusPacketLoss
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = getString(R.string.opus_packet_loss)
                        alertMessage.value = getString(R.string.opus_packet_loss_help)
                        showAlert.value = true
                    },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.opus_packet_loss)) },
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
            var audioDelay by remember { mutableStateOf(BaresipService.audioDelay.toString()) }
            newAudioDelay = audioDelay
            OutlinedTextField(
                value = audioDelay,
                placeholder = { Text(getString(R.string.audio_delay)) },
                onValueChange = {
                    audioDelay = it
                    newAudioDelay = audioDelay
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = getString(R.string.audio_delay)
                        alertMessage.value = getString(R.string.audio_delay_help)
                        showAlert.value = true
                    },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.audio_delay)) },
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
            Text(text = stringResource(R.string.tone_country),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.tone_country)
                        alertMessage.value = getString(R.string.tone_country_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
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
                    Text(text = countryNames[itemPosition.intValue],
                        color = LocalCustomColors.current.itemText)
                    CustomElements.DrawDrawable(R.drawable.arrow_drop_down,
                        tint = arrowTint)
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
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = LocalCustomColors.current.itemText
                            )
                    }
                }
            }
        }
    }

    private fun checkOnClick() {

        if (BaresipService.activities.indexOf("audio") == -1)
            return

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
                    alertTitle.value = getString(R.string.notice)
                    alertMessage.value = "${getString(R.string.invalid_microphone_gain)}: $gain."
                    showAlert.value = true
                    return
                }
                if (gain == "1.0") {
                    Api.module_unload("augain")
                    Config.removeVariableValue("module", "augain.so")
                    Config.replaceVariable("augain", "1.0")
                } else {
                    if (oldMicGain == "1.0") {
                        if (Api.module_load("augain") != 0) {
                            alertTitle.value = getString(R.string.error)
                            alertMessage.value = getString(R.string.failed_to_load_module) + ": augain.so"
                            showAlert.value = true
                            return
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

        for (module in audioModules) {
            if (newAudioModules[module] != null) {
                if (newAudioModules[module]!!) {
                    if (!modules.contains("${module}.so")) {
                        if (Api.module_load("${module}.so") != 0) {
                            alertTitle.value = getString(R.string.error)
                            alertMessage.value = "${getString(R.string.failed_to_load_module)}: ${module}.so"
                            showAlert.value = true
                            return
                        }
                        Config.addVariable("module", "${module}.so")
                        save = true
                    }
                } else if (modules.contains("${module}.so")) {
                    Api.module_unload("${module}.so")
                    Config.removeVariableValue("module", "${module}.so")
                    for (ua in BaresipService.uas.value)
                        ua.account.removeAudioCodecs(module)
                    AccountsActivity.saveAccounts()
                    save = true
                }
            }
        }

        if (newOpusBitrate != oldOpusBitrate) {
            if (!checkOpusBitRate(newOpusBitrate)) {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = "${getString(R.string.invalid_opus_bitrate)}: $newOpusBitrate."
                showAlert.value = true
                return
            }
            Config.replaceVariable("opus_bitrate", newOpusBitrate)
            restart = true
            save = true
        }

        if (newOpusPacketLoss != oldOpusPacketLoss) {
            if (!checkOpusPacketLoss(newOpusPacketLoss)) {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = "${getString(R.string.invalid_opus_packet_loss)}: $newOpusPacketLoss"
                showAlert.value = true
                return
            }
            Config.replaceVariable("opus_packet_loss", newOpusPacketLoss)
            restart = true
            save = true
        }

        val audioDelay = newAudioDelay.trim()
        if (audioDelay != BaresipService.audioDelay.toString()) {
            if (!checkAudioDelay(audioDelay)) {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_audio_delay), audioDelay)
                showAlert.value = true
                return
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

        if (save)
            Config.save()

        setResult(if (restart) RESULT_OK else RESULT_CANCELED)

        BaresipService.activities.remove("audio")
        finish()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (backInvokedCallback != null)
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backInvokedCallback!!)
        }
        else
            onBackPressedCallback.remove()
        super.onDestroy()
    }

    private fun goBack() {
        BaresipService.activities.remove("audio")
        finish()
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

    companion object {
        val audioModules = listOf("opus", "amr", "g722", "g7221", "g726", "g729", "codec2", "g711")
    }

}

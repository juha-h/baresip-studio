package com.tutpro.baresip.plus

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build.VERSION
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar

enum class Result {
    OK, ERROR, RESTART
}

fun NavGraphBuilder.audioScreenRoute(navController: NavController) {
    composable("audio") {
        val ctx = LocalContext.current
        val audioViewModel = viewModel<AudioViewModel>()
        AudioScreen(
            viewModel = audioViewModel,
            onBack = { navController.navigateUp() },
            checkOnClick = {
                when (audioViewModel.saveSettings(ctx)) {
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
    viewModel: AudioViewModel,
    onBack: () -> Unit,
    checkOnClick: () -> Unit
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.loadSettings(ctx)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                Spacer(Modifier.statusBarsPadding())
                TopAppBar(
                    title = {
                        Text(text = stringResource(R.string.audio_settings), fontWeight = FontWeight.Bold)
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
                            Icon(imageVector = Icons.Filled.Check, contentDescription = "Check")
                        }
                    },
                )
            }
        }
    ) {
        contentPadding -> AudioContent(viewModel, contentPadding)
    }
}

private val alertTitle = mutableStateOf("")
private val alertMessage = mutableStateOf("")
private val showAlert = mutableStateOf(false)

@Composable
private fun AudioContent(viewModel: AudioViewModel, contentPadding: PaddingValues) {

    if (showAlert.value)
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            lastButtonText = stringResource(R.string.ok),
        )

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 4.dp)
            .verticalScrollbar(scrollState)
            .verticalScroll(state = scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ringtone(viewModel)
        ToneCountry(viewModel)
        SpeakerPhone(viewModel)
        CallVolume(viewModel)
        MicGain(viewModel)
        AudioModules(viewModel)
        OpusBitRate(viewModel)
        OpusPacketLoss(viewModel)
        AudioDelay(viewModel)
    }
}

@Composable
private fun Ringtone(viewModel: AudioViewModel) {
    val ringToneTitle = stringResource(R.string.ringtone)
    val selectRingToneMessage = stringResource(R.string.select_ringtone)
    val ringtoneUri by viewModel.ringtoneUri.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = if (VERSION.SDK_INT >= 33)
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                viewModel.ringtoneUri.value = uri.toString()
            }
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = ringToneTitle,
            modifier = Modifier
                .weight(1f)
                .clickable {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    intent.putExtra(
                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                        RingtoneManager.TYPE_RINGTONE
                    )
                    intent.putExtra(
                        RingtoneManager.EXTRA_RINGTONE_TITLE,
                        selectRingToneMessage
                    )
                    intent.putExtra(
                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        ringtoneUri.toUri()
                    )
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    launcher.launch(intent)
                },
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun ToneCountry(viewModel: AudioViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val toneCountryTitle = stringResource(R.string.tone_country)
        val toneCountryHelp = stringResource(R.string.tone_country_help)
        Text(
            text = toneCountryTitle,
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = toneCountryTitle
                    alertMessage.value = toneCountryHelp
                    showAlert.value = true
                },
            fontSize = 18.sp
        )
        val currentToneCountry by viewModel.toneCountry.collectAsState()
        val isDropDownExpanded = remember { mutableStateOf(false) }
        val countryNames = arrayListOf("BG", "BR", "DE", "CZ", "ES", "FI", "FR", "GB", "JP", "NO", "NZ", "SE", "RU", "US")
        val countryValues = arrayListOf("bg", "br", "de", "cz", "es", "fi", "fr", "uk", "jp", "no", "nz", "se", "ru", "us")
        val itemPosition = countryValues.indexOf(currentToneCountry).let { if (it != -1) it else countryValues.indexOf("us") }

        Box {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { isDropDownExpanded.value = true }
            ) {
                Text(text = countryNames[itemPosition])
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
            DropdownMenu(
                expanded = isDropDownExpanded.value,
                onDismissRequest = { isDropDownExpanded.value = false }
            ) {
                countryNames.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { Text(text = name) },
                        onClick = {
                            isDropDownExpanded.value = false
                            viewModel.toneCountry.value = countryValues[index]
                        }
                    )
                    if (index < 10)
                        HorizontalDivider(thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun SpeakerPhone(viewModel: AudioViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(end=10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val speakerPhoneTitle = stringResource(R.string.speaker_phone)
        val speakerPhoneHelp = stringResource(R.string.speaker_phone_help)
        Text(text = speakerPhoneTitle,
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = speakerPhoneTitle
                    alertMessage.value = speakerPhoneHelp
                    showAlert.value = true
                },
            fontSize = 18.sp
        )
        val speakerPhone by viewModel.speakerPhone.collectAsState()
        Switch(
            checked = speakerPhone,
            onCheckedChange = {
                viewModel.speakerPhone.value = it
            }
        )
    }
}

@Composable
private fun CallVolume(viewModel: AudioViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(end=10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val defaultCallVolumeTitle = stringResource(R.string.default_call_volume)
        val defaultCallVolumeHelp = stringResource(R.string.default_call_volume_help)
        Text(
            text = defaultCallVolumeTitle,
            modifier = Modifier.weight(1f)
                .clickable {
                    alertTitle.value = defaultCallVolumeTitle
                    alertMessage.value = defaultCallVolumeHelp
                    showAlert.value = true
                },
            fontSize = 18.sp
        )
        val currentCallVolume by viewModel.callVolume.collectAsState()
        val isDropDownExpanded = remember { mutableStateOf(false) }
        val volNames = listOf("--",  "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        val volValues = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val itemPosition = volValues.indexOf(currentCallVolume).let { if (it != -1) it else 0 }

        Box {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { isDropDownExpanded.value = true }
            ) {
                Text(text = volNames[itemPosition])
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
            DropdownMenu(
                expanded = isDropDownExpanded.value,
                onDismissRequest = { isDropDownExpanded.value = false }
            ) {
                volNames.forEachIndexed { index, vol ->
                    DropdownMenuItem(
                        text = { Text(text = vol) },
                        onClick = {
                            isDropDownExpanded.value = false
                            viewModel.callVolume.value = volValues[index]
                        })
                    if (index < 10)
                        HorizontalDivider(thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun MicGain(viewModel: AudioViewModel) {
    if (!BaresipService.agcAvailable)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val microphoneGainTitle = stringResource(R.string.microphone_gain)
            val microphoneGainHelp = stringResource(R.string.microphone_gain_help)
            val micGain by viewModel.micGain.collectAsState()
            OutlinedTextField(
                value = micGain,
                placeholder = { Text(microphoneGainTitle) },
                onValueChange = { viewModel.micGain.value = it },
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
private fun AudioModules(viewModel: AudioViewModel) {
    val audioModules by viewModel.audioModules.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        val audioModulesTitle = stringResource(R.string.audio_modules_title)
        val audioModulesHelp = stringResource(R.string.audio_modules_help)
        Text(
            text = audioModulesTitle,
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
                val checked = audioModules[module] ?: false
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        val newMap = audioModules.toMutableMap()
                        newMap[module] = it
                        viewModel.audioModules.value = newMap
                    }
                )
            }
        }
    }
}

@Composable
private fun OpusBitRate(viewModel: AudioViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val opusBitRateTitle = stringResource(R.string.opus_bit_rate)
        val opusBitRateHelp = stringResource(R.string.opus_bit_rate_help)
        val opusBitrate by viewModel.opusBitrate.collectAsState()
        OutlinedTextField(
            value = opusBitrate,
            placeholder = { Text(opusBitRateTitle) },
            onValueChange = { viewModel.opusBitrate.value = it },
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
private fun OpusPacketLoss(viewModel: AudioViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(end = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val opusPacketLossTitle = stringResource(R.string.opus_packet_loss)
        val opusPacketLossHelp = stringResource(R.string.opus_packet_loss_help)
        val opusPacketLoss by viewModel.opusPacketLoss.collectAsState()
        OutlinedTextField(
            value = opusPacketLoss,
            placeholder = { Text(opusPacketLossTitle) },
            onValueChange = { viewModel.opusPacketLoss.value = it },
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
private fun AudioDelay(viewModel: AudioViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(end = 10.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val audioDelayTitle = stringResource(R.string.audio_delay)
        val audioDelayHelp = stringResource(R.string.audio_delay_help)
        val currentAudioDelay by viewModel.audioDelay.collectAsState()
        OutlinedTextField(
            value = currentAudioDelay,
            placeholder = { Text(audioDelayTitle) },
            onValueChange = { viewModel.audioDelay.value = it },
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

package com.tutpro.baresip

import android.Manifest
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Context.ROLE_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.LabelText
import com.tutpro.baresip.CustomElements.verticalScrollbar
import com.tutpro.baresip.Utils.copyInputStreamToFile
import java.io.File
import java.io.FileInputStream
import java.util.Locale

private var restart = false
private val showRestartDialog = mutableStateOf(false)
private var save = false

private var oldBatteryOptimizations = false
private var oldDarkTheme = false
private var oldDefaultDialer = false

fun NavGraphBuilder.settingsScreenRoute(
    navController: NavController,
    onRestartApp: () -> Unit
) {
    composable("settings") {
        val ctx = LocalContext.current

        val powerManager = ctx.getSystemService(POWER_SERVICE) as PowerManager
        oldBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(ctx.packageName) == false

        oldDarkTheme = Preferences(ctx).displayTheme == AppCompatDelegate.MODE_NIGHT_YES

        if (VERSION.SDK_INT >= 29) {
            val roleManager = ctx.getSystemService(ROLE_SERVICE) as RoleManager
            oldDefaultDialer = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        }

        SettingsScreen(
            navController = navController,
            onBack = { navController.popBackStack() },
            checkOnClick = {
                checkOnClick(ctx)
                if (restart)
                    showRestartDialog.value = true
                else
                    navController.popBackStack()
            },
            onRestartApp = onRestartApp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    navController: NavController,
    onBack: () -> Unit,
    checkOnClick: () -> Unit,
    onRestartApp: () -> Unit
) {
    val activity = LocalActivity.current
    val viewModel: ViewModel = viewModel(activity as ComponentActivity)
    val audioResult by viewModel.audioSettingsResult
    LaunchedEffect(audioResult) {
        audioResult?.let { result ->
            Log.d("SettingsScreen", "Got result from AudioSettings: $result")
            restart = restart || result
            viewModel.clearAudioSettingsResult()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = LocalCustomColors.current.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalCustomColors.current.background)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.configuration),
                            color = LocalCustomColors.current.light,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = LocalCustomColors.current.light
                            )
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    actions = {
                        IconButton(onClick = checkOnClick) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                tint = LocalCustomColors.current.light,
                                contentDescription = "Check"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.mediumTopAppBarColors(
                        containerColor = LocalCustomColors.current.primary
                    )
                )
            }
        }
    ) { contentPadding ->

        if (showRestartDialog.value) {
            AlertDialog(
                showDialog = showRestartDialog,
                title = stringResource(R.string.restart_request),
                message = stringResource(R.string.config_restart),
                positiveButtonText = stringResource(R.string.restart),
                onPositiveClicked = {
                    onRestartApp()
                },
                negativeButtonText = stringResource(R.string.cancel),
                onNegativeClicked = {
                    navController.popBackStack()
                }
            )
        }

        SettingsContent(contentPadding, navController, activity, onRestartApp)
    }
}

private val dialogTitle = mutableStateOf("")
private val dialogMessage = mutableStateOf("")
private val positiveText = mutableStateOf("")
private val onPositiveClicked = mutableStateOf({})
private val negativeText = mutableStateOf("")
private val onNegativeClicked = mutableStateOf({})
private val showDialog = mutableStateOf(false)

private val alertTitle = mutableStateOf("")
private val alertMessage = mutableStateOf("")
private val showAlert = mutableStateOf(false)

private var newAutoStart = false
private var newListenAddr = ""
private var newAddressFamily = ""

private var oldDnsServers = ""
private var newDnsServers = ""

private var newTlsCertificateFile = false

private var newVerifyServer = false

private var newCaFile = false

private var newUserAgent = ""

private var oldContactsMode = ""
private var newContactsMode = ""

private var newRingtoneUri = ""

private var newBatteryOptimizations = false

private var newDarkTheme = false

private var newColorblind = false

private var newDefaultDialer = false

private var newDebug = false

private var newSipTrace = false

@Composable
private fun SettingsContent(
    contentPadding: PaddingValues,
    navController: NavController,
    activity: Activity,
    onRestartApp: () -> Unit
) {

    if (showAlert.value) {
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            positiveButtonText = stringResource(R.string.ok),
        )
    }

    if (showDialog.value)
        AlertDialog(
            showDialog = showDialog,
            title = dialogTitle.value,
            message = dialogMessage.value,
            positiveButtonText = positiveText.value,
            onPositiveClicked = onPositiveClicked.value,
            negativeButtonText = negativeText.value,
            onNegativeClicked = onNegativeClicked.value,
        )

    val ctx = LocalContext.current

    if (Config.variable("auto_start") == "yes" &&
            !isAppearOnTopPermissionGranted(LocalContext.current)) {
        Config.replaceVariable("auto_start", "no")
        save = true
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 8.dp)
            .verticalScrollbar(scrollState)
            .verticalScroll(state = scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StartAutomatically()
        ListenAddress()
        AddressFamily()
        DnsServers()
        TlsCertificateFile(activity)
        VerifyServer()
        CaFile(activity)
        UserAgent()
        AudioSettings(navController)
        Contacts(activity)
        Ringtone(ctx)
        BatteryOptimizations()
        DarkTheme()
        ColorBlind()
        if (VERSION.SDK_INT >= 29)
            DefaultDialer()
        Debug()
        SipTrace()
        Reset(onRestartApp)
    }
}

@Composable
private fun StartAutomatically() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.start_automatically),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.start_automatically)
                    alertMessage.value = ctx.getString(R.string.start_automatically_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        var startAutomatically by remember { mutableStateOf(Config.variable("auto_start") == "yes") }
        newAutoStart = startAutomatically
        Switch(
            checked = startAutomatically,
            onCheckedChange = {
                if (it) {
                    if (!isAppearOnTopPermissionGranted(ctx)) {
                        dialogTitle.value = ctx.getString(R.string.notice)
                        dialogMessage.value = ctx.getString(R.string.appear_on_top_permission)
                        positiveText.value = ctx.getString(R.string.ok)
                        onPositiveClicked.value = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            ctx.startActivity(intent)
                        }
                        negativeText.value = ctx.getString(R.string.cancel)
                        onNegativeClicked.value = {
                            negativeText.value = ""
                        }
                        showDialog.value = true
                        startAutomatically = false
                    }
                    else
                        startAutomatically = true
                }
                else
                    startAutomatically = false
                newAutoStart = startAutomatically
            }
        )
    }
}

@Composable
private fun ListenAddress() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        val ctx = LocalContext.current
        var listenAddr by remember { mutableStateOf(Config.variable("sip_listen")) }
        newListenAddr = listenAddr
        OutlinedTextField(
            value = listenAddr,
            placeholder = { Text(stringResource(R.string._0_0_0_0_5060)) },
            onValueChange = {
                listenAddr = it
                newListenAddr = listenAddr
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    alertTitle.value = ctx.getString(R.string.listen_address)
                    alertMessage.value = ctx.getString(R.string.listen_address_help)
                    showAlert.value = true
                },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 18.sp, color = LocalCustomColors.current.itemText),
            label = { LabelText(stringResource(R.string.listen_address)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
    }
}

@Composable
private fun AddressFamily() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.address_family),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.address_family)
                    alertMessage.value = ctx.getString(R.string.address_family_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        val isDropDownExpanded = remember {
            mutableStateOf(false)
        }
        val familyNames = listOf("--",  "IPv4", "IPv6")
        val familyValues = listOf("",  "ipv4", "ipv6")
        val itemPosition = remember {
            mutableIntStateOf(familyValues.indexOf(Config.variable("net_af").lowercase()))
        }
        newAddressFamily = familyValues[itemPosition.intValue]
        Box {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    isDropDownExpanded.value = true
                }
            ) {
                Text(text = familyNames[itemPosition.intValue],
                    color = LocalCustomColors.current.itemText)
                CustomElements.DrawDrawable(R.drawable.arrow_drop_down,
                    tint = LocalCustomColors.current.itemText)
            }
            DropdownMenu(
                expanded = isDropDownExpanded.value,
                onDismissRequest = {
                    isDropDownExpanded.value = false
                }) {
                familyNames.forEachIndexed { index, family ->
                    DropdownMenuItem(text = {
                        Text(text = family)
                    },
                        onClick = {
                            isDropDownExpanded.value = false
                            itemPosition.intValue = index
                            newAddressFamily = familyValues[index]
                        })
                    if (index < 2)
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
private fun DnsServers() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        oldDnsServers = if (Config.variable("dyn_dns") == "yes")
            ""
        else {
            val servers = Config.variables("dns_server")
            var serverList = ""
            for (server in servers)
                serverList += ", $server"
            serverList.trimStart(',').trimStart(' ')
        }
        var dnsServers by remember { mutableStateOf(oldDnsServers) }
        newDnsServers = dnsServers
        OutlinedTextField(
            value = dnsServers,
            onValueChange = {
                dnsServers = it
                newDnsServers = dnsServers
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    alertTitle.value = ctx.getString(R.string.dns_servers)
                    alertMessage.value = ctx.getString(R.string.dns_servers_help)
                    showAlert.value = true
                },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 18.sp, color = LocalCustomColors.current.itemText
            ),
            label = { LabelText(stringResource(R.string.dns_servers)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
    }
}

@Composable
private fun TlsCertificateFile(activity: Activity) {

    val ctx = LocalContext.current

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    val certificateRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val certPath = BaresipService.filesPath + "/cert.pem"
        val certFile = File(certPath)
        if (it.resultCode == RESULT_OK) {
            it.data?.data?.also { uri ->
                try {
                    val inputStream = ctx.contentResolver.openInputStream(uri) as FileInputStream
                    certFile.copyInputStreamToFile(inputStream)
                    inputStream.close()
                    Config.replaceVariable("sip_certificate", certPath)
                    newTlsCertificateFile = true
                    save = true
                    restart = true
                } catch (e: Error) {
                    alertTitle.value = ctx.getString(R.string.error)
                    alertMessage.value = ctx.getString(R.string.read_cert_error) + ": " + e.message
                    showAlert.value = true
                    newTlsCertificateFile = false
                }
            }
        }
        else
            newTlsCertificateFile = false
        if (!newTlsCertificateFile)
            Utils.deleteFile(certFile)
    }

    val showAlertDialog = remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.tls_certificate_file),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.tls_certificate_file)
                    alertMessage.value = ctx.getString(R.string.tls_certificate_file_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        var tlsCertificateFile by remember { mutableStateOf(
            File(BaresipService.filesPath + "/cert.pem").exists()
        ) }
        newTlsCertificateFile = tlsCertificateFile
        Switch(
            checked = tlsCertificateFile,
            onCheckedChange = {
                tlsCertificateFile = it
                newTlsCertificateFile = tlsCertificateFile
                if (it)
                    if (VERSION.SDK_INT < 29) {
                        tlsCertificateFile = false
                        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
                        when {
                            ContextCompat.checkSelfPermission(ctx, permission) ==
                                    PackageManager.PERMISSION_GRANTED -> {
                                Log.d(TAG, "Read External Storage permission granted")
                                val downloadsPath = Utils.downloadsPath("cert.pem")
                                val content = Utils.getFileContents(downloadsPath)
                                if (content == null) {
                                    alertTitle.value = ctx.getString(R.string.error)
                                    alertMessage.value = ctx.getString(R.string.read_cert_error)
                                    showAlert.value = true
                                    return@Switch
                                }
                                val certPath = BaresipService.filesPath + "/cert.pem"
                                Utils.putFileContents(certPath, content)
                                Config.replaceVariable("sip_certificate", certPath)
                                tlsCertificateFile = true
                                save = true
                                restart = true
                            }
                            shouldShowRequestPermissionRationale(activity, permission) ->
                                showAlertDialog.value = true
                            else ->
                                requestPermissionLauncher.launch(permission)
                        }
                    }
                    else
                        Utils.selectInputFile(certificateRequest)
                else {
                    Config.removeVariable("sip_certificate")
                    Utils.deleteFile(File(BaresipService.filesPath + "/cert.pem"))
                    save = true
                    restart = true
                }
            }
        )
    }

    if (showAlertDialog.value)
        AlertDialog(
            showDialog = showAlertDialog,
            title = stringResource(R.string.notice),
            message = stringResource(R.string.no_read_permission),
            positiveButtonText = stringResource(R.string.ok),
            onPositiveClicked = { requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) },
            negativeButtonText = "",
            onNegativeClicked = {},
        )
}

@Composable
private fun VerifyServer() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.verify_server),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.verify_server)
                    alertMessage.value = ctx.getString(R.string.verify_server_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        var verifyServer by remember { mutableStateOf(Config.variable("sip_verify_server") == "yes") }
        newVerifyServer = verifyServer
        Switch(
            checked = verifyServer,
            onCheckedChange = {
                verifyServer = it
                newVerifyServer = verifyServer
            }
        )
    }
}

@Composable
private fun CaFile(activity: Activity) {

    val ctx = LocalContext.current

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    val caCertsRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val caCertsFile = File(BaresipService.filesPath + "/ca_certs.crt")
        if (it.resultCode == RESULT_OK)
            it.data?.data?.also { uri ->
                try {
                    val inputStream = ctx.contentResolver.openInputStream(uri) as FileInputStream
                    caCertsFile.copyInputStreamToFile(inputStream)
                    inputStream.close()
                    restart = true
                } catch (e: Error) {
                    alertTitle.value = ctx.getString(R.string.error)
                    alertMessage.value = ctx.getString(R.string.read_ca_certs_error) + ": " + e.message
                    showAlert.value = true
                    newCaFile = false
                }
            }
        else
            newCaFile = false
        if (!newCaFile) caCertsFile.delete()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.tls_ca_file),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.tls_ca_file)
                    alertMessage.value = ctx.getString(R.string.tls_ca_file_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        var caFile by remember { mutableStateOf(
            File(BaresipService.filesPath + "/ca_certs.crt").exists()
        ) }
        Switch(
            checked = caFile,
            onCheckedChange = {
                caFile = it
                newCaFile = caFile
                if (it) {
                    if (VERSION.SDK_INT < 29) {
                        caFile = false
                        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
                        when {
                            ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED -> {
                                Log.d(TAG, "Read External Storage permission granted")
                                val downloadsPath = Utils.downloadsPath("ca_certs.crt")
                                val content = Utils.getFileContents(downloadsPath)
                                if (content == null) {
                                    alertTitle.value = ctx.getString(R.string.error)
                                    alertMessage.value = ctx.getString(R.string.read_ca_certs_error)
                                    showAlert.value = true
                                    return@Switch
                                }
                                File(BaresipService.filesPath + "/ca_certs.crt").writeBytes(content)
                                caFile = true
                                restart = true
                            }
                            shouldShowRequestPermissionRationale(activity, permission) -> {
                                dialogTitle.value = ctx.getString(R.string.notice)
                                dialogMessage.value = ctx.getString(R.string.no_read_permission)
                                positiveText.value = ctx.getString(R.string.ok)
                                onPositiveClicked.value = {
                                    requestPermissionLauncher.launch(permission)
                                }
                                negativeText.value = ""
                                showDialog.value = true
                            }
                            else ->
                                requestPermissionLauncher.launch(permission)
                        }
                    }
                    else
                        Utils.selectInputFile(caCertsRequest)
                }
                else {
                    Utils.deleteFile(File(BaresipService.filesPath + "/ca_certs.crt"))
                    restart = true
                }
            }
        )
    }
}

@Composable
private fun UserAgent() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        var userAgent by remember { mutableStateOf(Config.variable("user_agent")) }
        newUserAgent = userAgent
        OutlinedTextField(
            value = userAgent,
            placeholder = { Text(stringResource(R.string.user_agent)) },
            onValueChange = {
                userAgent = it
                newUserAgent = userAgent
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    alertTitle.value = ctx.getString(R.string.user_agent)
                    alertMessage.value = ctx.getString(R.string.user_agent_help)
                    showAlert.value = true
                },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 18.sp, color = LocalCustomColors.current.itemText),
            label = { LabelText(stringResource(R.string.user_agent)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
    }
}

@Composable
private fun AudioSettings(navController: NavController) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = stringResource(R.string.audio_settings),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    navController.navigate("audio")
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp,
            fontWeight = FontWeight. Bold
        )
    }
}

@Composable
private fun Ringtone(ctx: Context) {
    newRingtoneUri = if (Preferences(ctx).ringtoneUri == "")
        RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE).toString()
    else
        Preferences(ctx).ringtoneUri!!
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = if (VERSION.SDK_INT >= 33)
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null)
                newRingtoneUri = uri.toString()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = stringResource(R.string.ringtone),
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
                        ctx.getString(R.string.select_ringtone)
                    )
                    intent.putExtra(
                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        newRingtoneUri.toUri()
                    )
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    launcher.launch(intent)
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BatteryOptimizations() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        val batterySettingsLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val powerManager = ctx.getSystemService(POWER_SERVICE) as PowerManager
            newBatteryOptimizations = !powerManager.isIgnoringBatteryOptimizations(ctx.packageName)
        }
        Text(text = stringResource(R.string.battery_optimizations),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.battery_optimizations)
                    alertMessage.value = ctx.getString(R.string.battery_optimizations_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        var battery by remember { mutableStateOf(oldBatteryOptimizations) }
        newBatteryOptimizations = battery
        Switch(
            checked = battery,
            onCheckedChange = {
                battery = it
                newBatteryOptimizations = battery
                batterySettingsLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        )
    }
}
@Composable
private fun Contacts(activity: Activity) {
    val showAlertDialog = remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.contacts),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.contacts)
                    alertMessage.value = ctx.getString(R.string.contacts_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        val isDropDownExpanded = remember {
            mutableStateOf(false)
        }
        val contactNames = listOf(
            "baresip",
            "Android",
            ctx.getString(R.string.both)
        )
        val contactValues = listOf("baresip",  "android", "both")
        oldContactsMode = Config.variable("contacts_mode").lowercase()
        val itemPosition = remember {
            mutableIntStateOf(contactValues.indexOf(oldContactsMode))
        }
        newContactsMode = contactValues[itemPosition.intValue]
        val requestPermissionsLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) {}
        val contactsPermissions = arrayOf(Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS)
        Box {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    isDropDownExpanded.value = true
                }
            ) {
                Text(text = contactNames[itemPosition.intValue],
                    color = LocalCustomColors.current.itemText)
                CustomElements.DrawDrawable(R.drawable.arrow_drop_down,
                    tint = LocalCustomColors.current.itemText)
            }
            DropdownMenu(
                expanded = isDropDownExpanded.value,
                onDismissRequest = {
                    isDropDownExpanded.value = false
                }) {
                contactNames.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { Text(text = name) },
                        onClick = {
                            isDropDownExpanded.value = false
                            val mode = contactValues[index]
                            if (mode != "baresip" && !Utils.checkPermissions(ctx, contactsPermissions)) {
                                dialogTitle.value = ctx.getString(R.string.consent_request)
                                dialogMessage.value = ctx.getString(R.string.contacts_consent)
                                positiveText.value = ctx.getString(R.string.accept)
                                onPositiveClicked.value = {
                                    showDialog.value = false
                                    newContactsMode = mode
                                    if (ContextCompat.checkSelfPermission(
                                            ctx,
                                            Manifest.permission.READ_CONTACTS
                                        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                                            ctx,
                                            Manifest.permission.WRITE_CONTACTS
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        Log.d(TAG, "Contacts permissions already granted")
                                    }
                                    else {
                                        if (shouldShowRequestPermissionRationale(
                                                activity, Manifest.permission.READ_CONTACTS
                                            ) ||
                                            shouldShowRequestPermissionRationale(
                                                activity, Manifest.permission.WRITE_CONTACTS
                                            )
                                        )
                                            showAlertDialog.value = true
                                        else
                                            requestPermissionsLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_CONTACTS,
                                                    Manifest.permission.WRITE_CONTACTS
                                                )
                                            )
                                    }
                                }
                                negativeText.value = ctx.getString(R.string.deny)
                                onNegativeClicked.value = {
                                    itemPosition.intValue = contactValues.indexOf(oldContactsMode)
                                    negativeText.value = ""
                                }
                                showDialog.value = true
                            }
                            else {
                                itemPosition.intValue = index
                                newContactsMode = contactValues[index]
                            }
                        })
                    if (index < 2)
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = LocalCustomColors.current.itemText
                        )
                }
            }
        }
        if (showAlertDialog.value)
            AlertDialog(
                showDialog = showAlertDialog,
                title = stringResource(R.string.notice),
                message = stringResource(R.string.no_android_contacts),
                positiveButtonText = stringResource(R.string.ok),
                onPositiveClicked = { requestPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS
                    )
                )},
                negativeButtonText = "",
                onNegativeClicked = {},
            )
    }
}

@Composable
private fun DarkTheme() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.dark_theme),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.dark_theme)
                    alertMessage.value = ctx.getString(R.string.dark_theme_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        var darkTheme by remember { mutableStateOf(oldDarkTheme) }
        newDarkTheme = darkTheme
        Switch(
            checked = darkTheme,
            onCheckedChange = {
                darkTheme = it
                newDarkTheme = darkTheme
            }
        )
    }
}
@Composable
private fun ColorBlind() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.colorblind),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.colorblind)
                    alertMessage.value = ctx.getString(R.string.colorblind_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        var colorblind by remember { mutableStateOf(Config.variable("colorblind") == "yes") }
        newColorblind = colorblind
        Switch(
            checked = colorblind,
            onCheckedChange = {
                colorblind = it
                newColorblind = colorblind
            }
        )
    }
}

@RequiresApi(29)
@Composable
private fun DefaultDialer() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.default_phone_app),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.default_phone_app)
                    alertMessage.value = ctx.getString(R.string.default_phone_app_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        val roleManager = ctx.getSystemService(ROLE_SERVICE) as RoleManager
        val dialerRoleRequest = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d(TAG, "dialerRoleRequest result: $result")
            newDefaultDialer = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        }
        var defaultDialer by remember { mutableStateOf(oldDefaultDialer) }
        newDefaultDialer = defaultDialer
        Switch(
            checked = defaultDialer,
            onCheckedChange = {
                defaultDialer = it
                newDefaultDialer = defaultDialer
                if (it) {
                    if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                        alertTitle.value = ctx.getString(R.string.alert)
                        alertMessage.value = ctx.getString(R.string.dialer_role_not_available)
                        showAlert.value = true
                    }
                    else
                        if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER))
                            dialerRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                } else {
                    try {
                        dialerRoleRequest.launch(Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"))
                    } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "ActivityNotFound exception: ${e.message}")
                    }
                }
            }
        )
    }
}

@Composable
private fun Debug() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.debug),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.debug)
                    alertMessage.value = ctx.getString(R.string.debug_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        var db by remember { mutableStateOf(Config.variable("log_level") == "0") }
        newDebug = db
        Switch(
            checked = db,
            onCheckedChange = {
                db = it
                newDebug = db
            }
        )
    }
}

@Composable
private fun SipTrace() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.sip_trace),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.sip_trace)
                    alertMessage.value = ctx.getString(R.string.sip_trace_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        var sipTrace by remember { mutableStateOf(BaresipService.sipTrace) }
        newSipTrace = sipTrace
        Switch(
            checked = sipTrace,
            onCheckedChange = {
                sipTrace = it
                newSipTrace = sipTrace
            }
        )
    }
}

@Composable
private fun Reset(onRestartApp: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val ctx = LocalContext.current
        Text(text = stringResource(R.string.reset_config),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.reset_config)
                    alertMessage.value = ctx.getString(R.string.reset_config_help)
                    showAlert.value = true
                },
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp)
        var reset by remember { mutableStateOf(false) }
        Switch(
            checked = reset,
            onCheckedChange = {
                dialogTitle.value = ctx.getString(R.string.confirmation)
                dialogMessage.value = ctx.getString(R.string.reset_config_alert)
                positiveText.value = ctx.getString(R.string.reset)
                onPositiveClicked.value = {
                    Config.reset()
                    onRestartApp()
                }
                negativeText.value = ctx.getString(R.string.cancel)
                onNegativeClicked.value = {
                    reset = false
                    negativeText.value = ""
                }
                showDialog.value = true
            }
        )
    }
}

private fun checkOnClick(ctx: Context) {

    if ((Config.variable("auto_start") == "yes") != newAutoStart) {
        Config.replaceVariable(
            "auto_start",
            if (newAutoStart) "yes" else "no"
        )
        save = true
    }

    val listenAddr = newListenAddr.trim()
    if (listenAddr != Config.variable("sip_listen")) {
        if ((listenAddr != "") && !Utils.checkIpPort(listenAddr)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = "${ctx.getString(R.string.invalid_listen_address)}: $listenAddr"
            showAlert.value = true
            return
        }
        Config.replaceVariable("sip_listen", listenAddr)
        save = true
        restart = true
    }

    if (Config.variable("net_af").lowercase() != newAddressFamily) {
        Config.replaceVariable("net_af", newAddressFamily)
        save = true
        restart = true
    }

    var dnsServers = newDnsServers.lowercase(Locale.ROOT).replace(" ", "")
    dnsServers = addMissingPorts(dnsServers)
    if (dnsServers != oldDnsServers.replace(" ", "")) {
        if (!checkDnsServers(dnsServers)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = "${ctx.getString(R.string.invalid_dns_servers)}: $dnsServers"
            showAlert.value = true
            return
        }
        Config.removeVariable("dns_server")
        if (dnsServers.isNotEmpty()) {
            for (server in dnsServers.split(","))
                Config.addVariable("dns_server", server)
            Config.replaceVariable("dyn_dns", "no")
            if (Api.net_use_nameserver(dnsServers) != 0) {
                alertTitle.value = ctx.getString(R.string.notice)
                alertMessage.value = "${ctx.getString(R.string.failed_to_set_dns_servers)}: $dnsServers"
                showAlert.value = true
                return
            }
        } else {
            Config.replaceVariable("dyn_dns", "yes")
            Config.updateDnsServers(BaresipService.dnsServers)
        }
        // Api.net_dns_debug()
        save = true
    }

    if ((Config.variable("sip_verify_server") == "yes") != newVerifyServer) {
        Config.replaceVariable("sip_verify_server", if (newVerifyServer) "yes" else "no")
        Api.config_verify_server_set(newVerifyServer)
        save = true
    }

    newUserAgent = newUserAgent.trim()
    if (newUserAgent != Config.variable("user_agent")) {
        if ((newUserAgent != "") && !Utils.checkServerVal(newUserAgent)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = "${ctx.getString(R.string.invalid_user_agent)}: $newUserAgent"
            showAlert.value = true
            return
        }
        if (newUserAgent != "")
            Config.replaceVariable("user_agent", newUserAgent)
        else
            Config.removeVariable("user_agent")
        save = true
        restart = true
    }

    Preferences(ctx).ringtoneUri = newRingtoneUri
    BaresipService.rt = RingtoneManager.getRingtone(ctx, newRingtoneUri.toUri())

    if (oldContactsMode != newContactsMode) {
        Config.replaceVariable("contacts_mode", newContactsMode)
        BaresipService.contactsMode = newContactsMode
        val baresipService = Intent(ctx, BaresipService::class.java)
        when (newContactsMode) {
            "baresip" -> {
                BaresipService.androidContacts.value = listOf()
                Contact.restoreBaresipContacts()
                baresipService.action = "Stop Content Observer"
            }
            "android" -> {
                BaresipService.baresipContacts.value = mutableListOf()
                Contact.loadAndroidContacts(ctx)
                baresipService.action = "Start Content Observer"
            }
            "both" -> {
                Contact.restoreBaresipContacts()
                Contact.loadAndroidContacts(ctx)
                baresipService.action = "Start Content Observer"
            }
        }
        Contact.contactsUpdate()
        ctx.startService(baresipService)
        save = true
    }

    val newDisplayTheme = if (newDarkTheme)
        AppCompatDelegate.MODE_NIGHT_YES
    else
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    if (oldDarkTheme != newDarkTheme) {
        Preferences(ctx).displayTheme = newDisplayTheme
        BaresipService.darkTheme.value = newDarkTheme
        AppCompatDelegate.setDefaultNightMode(newDisplayTheme)
        Config.replaceVariable("dark_theme",
            if (newDarkTheme) "yes" else "no")
        save = true
    }

    if ((Config.variable("colorblind") == "yes") != newColorblind) {
        Config.replaceVariable(
            "colorblind",
            if (newColorblind) "yes" else "no"
        )
        BaresipService.colorblind = newColorblind
        UserAgent.updateColorblindStatus()
        val baresipService = Intent(ctx, BaresipService::class.java)
        baresipService.action = "Update Notification"
        ctx.startService(baresipService)
        save = true
    }

    if ((Config.variable("log_level") == "0") != newDebug) {
        val logLevelString = if (newDebug) "0" else "2"
        Config.replaceVariable("log_level", logLevelString)
        Api.log_level_set(logLevelString.toInt())
        Log.logLevelSet(logLevelString.toInt())
        save = true
    }

    if (BaresipService.sipTrace != newSipTrace) {
        BaresipService.sipTrace = newSipTrace
        Api.uag_enable_sip_trace(newSipTrace)
    }

    if (save) Config.save()
}

private fun isAppearOnTopPermissionGranted(ctx: Context): Boolean {
    return Settings.canDrawOverlays(ctx)
}

private fun addMissingPorts(addressList: String): String {
    if (addressList == "") return ""
    var result = ""
    for (addr in addressList.split(","))
        result = if (Utils.checkIpPort(addr)) {
            "$result,$addr"
        } else {
            if (Utils.checkIpV4(addr))
                "$result,$addr:53"
            else
                "$result,[$addr]:53"
        }
    return result.substring(1)
}

private fun checkDnsServers(dnsServers: String): Boolean {
    if (dnsServers.isEmpty()) return true
    for (server in dnsServers.split(","))
        if (!Utils.checkIpPort(server.trim())) return false
    return true
}

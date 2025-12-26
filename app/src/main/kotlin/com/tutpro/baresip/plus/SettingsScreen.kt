package com.tutpro.baresip.plus

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.livedata.observeAsState
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
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar
import com.tutpro.baresip.plus.Utils.copyInputStreamToFile
import java.io.File
import java.io.FileInputStream
import java.util.Locale

private var restart = false
private val showRestartDialog = mutableStateOf(false)
private var save = false

fun NavGraphBuilder.settingsScreenRoute(
    navController: NavController,
    onRestartApp: () -> Unit
) {
    composable("settings") {
        val ctx = LocalContext.current
        val viewModel = viewModel<SettingsViewModel>()
        SettingsScreen(
            ctx = ctx,
            navController = navController,
            settingsViewModel = viewModel,
            onBack = { navController.popBackStack() },
            checkOnClick = {
                if (checkOnClick(ctx, viewModel)) {
                    if (restart)
                        showRestartDialog.value = true
                    else
                        navController.popBackStack()
                }
            },
            onRestartApp = onRestartApp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    ctx: Context,
    navController: NavController,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    checkOnClick: () -> Unit,
    onRestartApp: () -> Unit
) {
    val activity = LocalActivity.current
    var areSettingsLoaded by remember { mutableStateOf(false) }

    val audioResult = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<Boolean>("audio_settings_result")
        ?.observeAsState()

    LaunchedEffect(audioResult?.value) {
        if (audioResult?.value == true) {
            Log.d(TAG, "Got result from AudioSettings: true")
            restart = true
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.remove<Boolean>("audio_settings_result")
        }
    }

    LaunchedEffect(null) {
        settingsViewModel.loadSettings(ctx)
        areSettingsLoaded = true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.configuration),
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

        if (areSettingsLoaded && activity != null)
            SettingsContent(settingsViewModel, contentPadding, navController, activity, onRestartApp)
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

@Composable
private fun SettingsContent(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    navController: NavController,
    activity: Activity,
    onRestartApp: () -> Unit
) {

    val alertTitleText = stringResource(R.string.alert)
    val errorTitleText = stringResource(R.string.error)
    val noticeTitleText = stringResource(R.string.notice)
    val confirmationText = stringResource(R.string.confirmation)
    val okButtonText = stringResource(R.string.ok)
    val cancelButtonText = stringResource(R.string.cancel)

    if (showAlert.value) {
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            positiveButtonText = okButtonText,
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

    @Composable
    fun StartAutomatically() {
        val startAutomaticallyTitle = stringResource(R.string.start_automatically)
        val startAutomaticallyHelp = stringResource(R.string.start_automatically_help)
        val appearOnTopPermissionMessage = stringResource(R.string.appear_on_top_permission)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val ctx = LocalContext.current
            Text(text = startAutomaticallyTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = startAutomaticallyTitle
                        alertMessage.value = startAutomaticallyHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val startAutomatically by viewModel.autoStart.collectAsState()
            Switch(
                checked = startAutomatically,
                onCheckedChange = {
                    if (it) {
                        if (!isAppearOnTopPermissionGranted(ctx)) {
                            dialogTitle.value = noticeTitleText
                            dialogMessage.value = appearOnTopPermissionMessage
                            positiveText.value = okButtonText
                            onPositiveClicked.value = {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                ctx.startActivity(intent)
                            }
                            negativeText.value = cancelButtonText
                            onNegativeClicked.value = {
                                negativeText.value = ""
                            }
                            showDialog.value = true
                            viewModel.autoStart.value = false
                        }
                        else
                            viewModel.autoStart.value = true
                    }
                    else
                        viewModel.autoStart.value = false
                }
            )
        }
    }

    @Composable
    fun AddressFamily() {
        val addressFamilyTitle = stringResource(R.string.address_family)
        val addressFamilyHelp = stringResource(R.string.address_family_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val addressFamily by viewModel.addressFamily.collectAsState()
            Text(text = addressFamilyTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = addressFamilyTitle
                        alertMessage.value = addressFamilyHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val familyNames = listOf("--",  "IPv4", "IPv6")
            val familyValues = listOf("",  "ipv4", "ipv6")
            val itemPosition = remember { mutableIntStateOf(familyValues.indexOf(addressFamily)) }
            Box {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = familyNames[itemPosition.intValue])
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                    //CustomElements.DrawDrawable(R.drawable.arrow_drop_down)
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = {
                        isDropDownExpanded.value = false
                    }) {
                    familyNames.forEachIndexed { index, family ->
                        DropdownMenuItem(
                            text = { Text(text = family) },
                            onClick = {
                                isDropDownExpanded.value = false
                                itemPosition.intValue = index
                                viewModel.addressFamily.value = familyValues[index]
                            })
                        if (index < 2)
                            HorizontalDivider(thickness = 1.dp)
                    }
                }
            }
        }
    }

    @Composable
    fun ListenAddress() {
        val listenAddressTitle = stringResource(R.string.listen_address)
        val listenAddressHelp = stringResource(R.string.listen_address_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            val listenAddress by viewModel.listenAddress.collectAsState()
            OutlinedTextField(
                value = listenAddress,
                placeholder = { Text(stringResource(R.string._0_0_0_0_5060)) },
                onValueChange = {
                    viewModel.listenAddress.value = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = listenAddressTitle
                        alertMessage.value = listenAddressHelp
                        showAlert.value = true
                    },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                label = { Text(listenAddressTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun TransportProtocols() {
        val transportProtocolsTitle = stringResource(R.string.transport_protocols)
        val transportProtocolsHelp = stringResource(R.string.transport_protocols_help)
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val transportProtocols by viewModel.transportProtocols.collectAsState()
            OutlinedTextField(
                value = transportProtocols,
                onValueChange = {
                    viewModel.transportProtocols.value = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = transportProtocolsTitle
                        alertMessage.value = transportProtocolsHelp
                        showAlert.value = true
                    },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                label = { Text(transportProtocolsTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun DnsServers() {
        val dnsServersTitle = stringResource(R.string.dns_servers)
        val dnsServersHelp = stringResource(R.string.dns_servers_help)
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val dnsServers by viewModel.dnsServers.collectAsState()
            OutlinedTextField(
                value = dnsServers,
                onValueChange = {
                    viewModel.dnsServers.value = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = dnsServersTitle
                        alertMessage.value = dnsServersHelp
                        showAlert.value = true
                    },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                label = { Text(dnsServersTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun TlsCertificateFile(activity: Activity) {

        val ctx = LocalContext.current

        val readCertError = stringResource(R.string.read_cert_error)
        val tlsCertificateFileTitle = stringResource(R.string.tls_certificate_file)
        val tlsCertificateFileHelp = stringResource(R.string.tls_certificate_file_help)

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
                        viewModel.tlsCertificateFile.value = true
                        save = true
                        restart = true
                    } catch (e: Error) {
                        alertTitle.value = errorTitleText
                        alertMessage.value = readCertError + ": " + e.message
                        showAlert.value = true
                        viewModel.tlsCertificateFile.value = false
                    }
                }
            }
            else
                viewModel.tlsCertificateFile.value = false
            if (!viewModel.tlsCertificateFile.value)
                Utils.deleteFile(certFile)
        }

        val showAlertDialog = remember { mutableStateOf(false) }

        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val ctx = LocalContext.current
            Text(text = tlsCertificateFileTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = tlsCertificateFileTitle
                        alertMessage.value = tlsCertificateFileHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val tlsCertificateFile by viewModel.tlsCertificateFile.collectAsState()
            Switch(
                checked = tlsCertificateFile,
                onCheckedChange = {
                    viewModel.tlsCertificateFile.value = it
                    if (it)
                        if (VERSION.SDK_INT < 29) {
                            viewModel.tlsCertificateFile.value = false
                            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
                            when {
                                ContextCompat.checkSelfPermission(ctx, permission) ==
                                        PackageManager.PERMISSION_GRANTED -> {
                                    Log.d(TAG, "Read External Storage permission granted")
                                    val downloadsPath = Utils.downloadsPath("cert.pem")
                                    val content = Utils.getFileContents(downloadsPath)
                                    if (content == null) {
                                        alertTitle.value = errorTitleText
                                        alertMessage.value = readCertError
                                        showAlert.value = true
                                        return@Switch
                                    }
                                    val certPath = BaresipService.filesPath + "/cert.pem"
                                    Utils.putFileContents(certPath, content)
                                    Config.replaceVariable("sip_certificate", certPath)
                                    viewModel.tlsCertificateFile.value = true
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
    fun VerifyServer() {
        val verifyServerTitle = stringResource(R.string.verify_server)
        val verifyServerHelp = stringResource(R.string.verify_server_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = verifyServerTitle,
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = verifyServerTitle
                        alertMessage.value = verifyServerHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val verifyServer by viewModel.verifyServer.collectAsState()
            Switch(
                checked = verifyServer,
                onCheckedChange = { viewModel.verifyServer.value = it }
            )
        }
    }

    @Composable
    fun CaFile(activity: Activity) {

        val ctx = LocalContext.current

        val tlsCaFileTitle = stringResource(R.string.tls_ca_file)
        val tlsCaFileHelp = stringResource(R.string.tls_ca_file_help)
        val readCaCertsError = stringResource(R.string.read_ca_certs_error)
        val noReadPermissionMessage = stringResource(R.string.no_read_permission)

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
                        alertTitle.value = errorTitleText
                        alertMessage.value = readCaCertsError + ": " + e.message
                        showAlert.value = true
                        viewModel.caFile.value = false
                    }
                }
            else
                viewModel.caFile.value = false
            if (!viewModel.caFile.value) caCertsFile.delete()
        }

        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val ctx = LocalContext.current
            Text(text = tlsCaFileTitle,
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = tlsCaFileTitle
                        alertMessage.value = tlsCaFileHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val caFile by viewModel.caFile.collectAsState()
            Switch(
                checked = caFile,
                onCheckedChange = {
                    viewModel.caFile.value = it
                    if (it) {
                        if (VERSION.SDK_INT < 29) {
                            viewModel.caFile.value = false
                            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
                            when {
                                ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED -> {
                                    Log.d(TAG, "Read External Storage permission granted")
                                    val downloadsPath = Utils.downloadsPath("ca_certs.crt")
                                    val content = Utils.getFileContents(downloadsPath)
                                    if (content == null) {
                                        alertTitle.value = errorTitleText
                                        alertMessage.value = readCaCertsError
                                        showAlert.value = true
                                        return@Switch
                                    }
                                    File(BaresipService.filesPath + "/ca_certs.crt").writeBytes(content)
                                    viewModel.caFile.value = true
                                    restart = true
                                }
                                shouldShowRequestPermissionRationale(activity, permission) -> {
                                    dialogTitle.value = noticeTitleText
                                    dialogMessage.value = noReadPermissionMessage
                                    positiveText.value = okButtonText
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
    fun UserAgent() {
        val userAgentTitle = stringResource(R.string.user_agent)
        val userAgentHelp = stringResource(R.string.user_agent_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val userAgent by viewModel.userAgent.collectAsState()
            OutlinedTextField(
                value = userAgent,
                placeholder = { Text(userAgentTitle) },
                onValueChange = { viewModel.userAgent.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = userAgentTitle
                        alertMessage.value = userAgentHelp
                        showAlert.value = true
                    },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                label = { Text(userAgentTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun AudioSettings(navController: NavController) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = stringResource(R.string.audio_settings),
                modifier = Modifier.weight(1f)
                    .clickable { navController.navigate("audio") },
                fontSize = 18.sp,
                fontWeight = FontWeight. Bold
            )
        }
    }

    @Composable
    fun Contacts(activity: Activity) {
        val contactsTitle = stringResource(R.string.contacts)
        val contactsHelp = stringResource(R.string.contacts_help)
        val consentRequestTitle = stringResource(R.string.consent_request)
        val contactsConsentMessage = stringResource(R.string.contacts_consent)
        val positiveButtonText = stringResource(R.string.accept)
        val negativeButtonText = stringResource(R.string.deny)
        val both = stringResource(R.string.both)
        val noAndroidContactsMessage = stringResource(R.string.no_android_contacts)
        val showAlertDialog = remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val ctx = LocalContext.current
            Text(text = contactsTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = contactsTitle
                        alertMessage.value = contactsHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val contactNames = listOf(
                "baresip",
                "Android",
                both
            )
            val contactsMode by viewModel.contactsMode.collectAsState()
            val contactValues = listOf("baresip",  "android", "both")
            val itemPosition = remember {
                mutableIntStateOf(contactValues.indexOf(contactsMode))
            }
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
                    Text(text = contactNames[itemPosition.intValue])
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
                    contactNames.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(text = name) },
                            onClick = {
                                isDropDownExpanded.value = false
                                val mode = contactValues[index]
                                if (mode != "baresip" && !Utils.checkPermissions(ctx, contactsPermissions)) {
                                    dialogTitle.value = consentRequestTitle
                                    dialogMessage.value = contactsConsentMessage
                                    positiveText.value = positiveButtonText
                                    onPositiveClicked.value = {
                                        showDialog.value = false
                                        viewModel.contactsMode.value = mode
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
                                    negativeText.value = negativeButtonText
                                    onNegativeClicked.value = {
                                        itemPosition.intValue = contactValues.indexOf(contactsMode)
                                        negativeText.value = ""
                                    }
                                    showDialog.value = true
                                }
                                else {
                                    itemPosition.intValue = index
                                    viewModel.contactsMode.value = contactValues[index]
                                }
                            })
                        if (index < 2)
                            HorizontalDivider(thickness = 1.dp)
                    }
                }
            }
            if (showAlertDialog.value)
                AlertDialog(
                    showDialog = showAlertDialog,
                    title = noticeTitleText,
                    message = noAndroidContactsMessage,
                    positiveButtonText = okButtonText,
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
    fun Ringtone() {
        val ringToneTitle = stringResource(R.string.ringtone)
        val selectRingToneMessage = stringResource(R.string.select_ringtone)
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
                    viewModel.ringtoneUri.value = uri.toString()
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
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
                            viewModel.ringtoneUri.value.toUri()
                        )
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        launcher.launch(intent)
                    },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    fun VideoSize() {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val videoSizeTitle = stringResource(R.string.video_size)
            val videoSizeHelp = stringResource(R.string.video_size_help)
            Text(text = videoSizeTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = videoSizeTitle
                        alertMessage.value = videoSizeHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp)
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val videoSize by viewModel.videoSize.collectAsState()
            val frameSizes = mutableListOf<String>()
            frameSizes.addAll(Config.videoSizes)
            val sizeCount = frameSizes.size
            val itemPosition = remember {
                mutableIntStateOf(frameSizes.indexOf(videoSize))
            }
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = frameSizes[itemPosition.intValue])
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
                    }
                ) {
                    frameSizes.forEachIndexed { index, size ->
                        DropdownMenuItem(
                            text = { Text(text = size) },
                            onClick = {
                                isDropDownExpanded.value = false
                                itemPosition.intValue = index
                                viewModel.videoSize.value = frameSizes[index]
                            }
                        )
                        if (index < sizeCount - 1)
                            HorizontalDivider(thickness = 1.dp)
                    }
                }
            }
        }
    }

    @Composable
    fun VideoFps() {
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val videoFps by viewModel.videoFps.collectAsState()
            val videoFpsTitle = stringResource(R.string.video_fps)
            val videoFpsHelp = stringResource(R.string.video_fps_help)
            OutlinedTextField(
                value = videoFps,
                placeholder = { Text(videoFpsTitle) },
                onValueChange = {
                    viewModel.videoFps.value = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = videoFpsTitle
                        alertMessage.value = videoFpsHelp
                        showAlert.value = true
                    },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                label = { Text(videoFpsTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun BatteryOptimizations() {
        val batteryOptimizationsTitle = stringResource(R.string.battery_optimizations)
        val batteryOptimizationsHelp = stringResource(R.string.battery_optimizations_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val ctx = LocalContext.current
            val batterySettingsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { _ ->
                val powerManager = ctx.getSystemService(POWER_SERVICE) as PowerManager
                viewModel.batteryOptimizations.value =
                    !powerManager.isIgnoringBatteryOptimizations(ctx.packageName)
            }
            Text(text = batteryOptimizationsTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = batteryOptimizationsTitle
                        alertMessage.value = batteryOptimizationsHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val battery by viewModel.batteryOptimizations.collectAsState()
            Switch(
                checked = battery,
                onCheckedChange = {
                    viewModel.batteryOptimizations.value = it
                    batterySettingsLauncher.launch(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            )
        }
    }

    @Composable
    fun DarkTheme() {
        val darkThemeTitle = stringResource(R.string.dark_theme)
        val darkThemeHelp = stringResource(R.string.dark_theme_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = darkThemeTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = darkThemeTitle
                        alertMessage.value = darkThemeHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val darkTheme by viewModel.darkTheme.collectAsState()
            Switch(
                checked = darkTheme,
                onCheckedChange = { viewModel.darkTheme.value = it }
            )
        }
    }

    @Composable
    fun DynamicColors() {
        val dynamicColorsTitle = stringResource(R.string.dynamic_colors)
        val dynamicColorsHelp = stringResource(R.string.dynamic_colors_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = dynamicColorsTitle,
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = dynamicColorsTitle
                        alertMessage.value = dynamicColorsHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val dynamicColors by viewModel.dynamicColors.collectAsState()
            Switch(
                checked = dynamicColors,
                onCheckedChange = { viewModel.dynamicColors.value = it }
            )
        }
    }

    @Composable
    fun ColorBlind() {
        val colorBlindTitle = stringResource(R.string.colorblind)
        val colorBlindHelp = stringResource(R.string.colorblind_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = colorBlindTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = colorBlindTitle
                        alertMessage.value = colorBlindHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val colorblind by viewModel.colorblind.collectAsState()
            Switch(
                checked = colorblind,
                onCheckedChange = { viewModel.colorblind.value = it }
            )
        }
    }
    @Composable
    fun ProximitySensing() {
        val proximitySensingTitle = stringResource(R.string.proximity_sensing)
        val proximitySensingHelp = stringResource(R.string.proximity_sensing_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = proximitySensingTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = proximitySensingTitle
                        alertMessage.value = proximitySensingHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val proximitySensing by viewModel.proximitySensing.collectAsState()
            Switch(
                checked = proximitySensing,
                onCheckedChange = { viewModel.proximitySensing.value = it }
            )
        }
    }

    @RequiresApi(29)
    @Composable
    fun DefaultDialer() {
        val defaultPhoneAppTitle = stringResource(R.string.default_phone_app)
        val defaultPhoneAppHelp = stringResource(R.string.default_phone_app_help)
        val dialerRoleNotAvailableMessage = stringResource(R.string.dialer_role_not_available)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val ctx = LocalContext.current
            Text(text = defaultPhoneAppTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = defaultPhoneAppTitle
                        alertMessage.value = defaultPhoneAppHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val defaultDialer by viewModel.defaultDialer.collectAsState()
            val roleManager = ctx.getSystemService(ROLE_SERVICE) as RoleManager
            val dialerRoleRequest = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                Log.d(TAG, "dialerRoleRequest result: $result")
                viewModel.defaultDialer.value = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            }
            Switch(
                checked = defaultDialer,
                onCheckedChange = {
                    viewModel.defaultDialer.value = it
                    if (it) {
                        if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                            alertTitle.value = alertTitleText
                            alertMessage.value = dialerRoleNotAvailableMessage
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
    fun Debug() {
        val debugTitle = stringResource(R.string.debug)
        val debugHelp = stringResource(R.string.debug_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = debugTitle,
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = debugTitle
                        alertMessage.value = debugHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val debug by viewModel.debug.collectAsState()
            Switch(
                checked = debug,
                onCheckedChange = { viewModel.debug.value = it }
            )
        }
    }

    @Composable
    fun SipTrace() {
        val sipTraceTitle = stringResource(R.string.sip_trace)
        val sipTraceHelp = stringResource(R.string.sip_trace_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = sipTraceTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = sipTraceTitle
                        alertMessage.value = sipTraceHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val sipTrace by viewModel.sipTrace.collectAsState()
            Switch(
                checked = sipTrace,
                onCheckedChange = { viewModel.sipTrace.value = it }
            )
        }
    }

    @Composable
    fun Reset(onRestartApp: () -> Unit) {
        val resetConfigTitle = stringResource(R.string.reset_config)
        val resetConfigHelp = stringResource(R.string.reset_config_help)
        val resetConfigAlert = stringResource(R.string.reset_config_alert)
        val resetButtonText = stringResource(R.string.reset)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = resetConfigTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = resetConfigTitle
                        alertMessage.value = resetConfigHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            var reset by remember { mutableStateOf(false) }
            Switch(
                checked = reset,
                onCheckedChange = {
                    dialogTitle.value = confirmationText
                    dialogMessage.value = resetConfigAlert
                    positiveText.value = resetButtonText
                    onPositiveClicked.value = {
                        Config.reset()
                        onRestartApp()
                    }
                    negativeText.value = cancelButtonText
                    onNegativeClicked.value = {
                        reset = false
                        negativeText.value = ""
                    }
                    showDialog.value = true
                }
            )
        }
    }

    if (Config.variable("auto_start") == "yes" &&
            !isAppearOnTopPermissionGranted(LocalContext.current)) {
        Config.replaceVariable("auto_start", "no")
        save = true
    }

    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxWidth()
            .padding(contentPadding)
            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 8.dp)
            .verticalScrollbar(
                state = lazyListState,
                width = 4.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { StartAutomatically() }
        item { AddressFamily() }
        item { ListenAddress() }
        item { TransportProtocols() }
        item { DnsServers() }
        item { TlsCertificateFile(activity) }
        item { VerifyServer() }
        item { CaFile(activity) }
        item { UserAgent() }
        item { AudioSettings(navController) }
        item { Contacts(activity) }
        item { Ringtone() }
        item { VideoSize() }
        item { VideoFps() }
        item { BatteryOptimizations() }
        item { DarkTheme() }
        if (VERSION.SDK_INT >= 31) {
            item { DynamicColors() }
        }
        item { ColorBlind() }
        item { ProximitySensing() }
        if (VERSION.SDK_INT >= 29) {
            item { DefaultDialer() }
        }
        item { Debug() }
        item { SipTrace() }
        item { Reset(onRestartApp) }
    }
}

private fun checkOnClick(ctx: Context, viewModel: SettingsViewModel): Boolean {

    if ((Config.variable("auto_start") == "yes") != viewModel.autoStart.value) {
        Config.replaceVariable(
            "auto_start",
            if (viewModel.autoStart.value) "yes" else "no"
        )
        save = true
    }

    val listenAddr = viewModel.listenAddress.value.trim()
    if (listenAddr != Config.variable("sip_listen")) {
        if ((listenAddr != "") && !Utils.checkIpPort(listenAddr)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = "${ctx.getString(R.string.invalid_listen_address)}: $listenAddr"
            showAlert.value = true
            return false
        }
        Config.replaceVariable("sip_listen", listenAddr)
        save = true
        restart = true
    }

    if (Config.variable("net_af").lowercase() != viewModel.addressFamily.value) {
        Config.replaceVariable("net_af", viewModel.addressFamily.value)
        save = true
        restart = true
    }

    val transportProtocols = viewModel.transportProtocols.value
        .lowercase(Locale.ROOT).replace(" ", "")
    if (transportProtocols != viewModel.oldTransportProtocols) {
        if (!checkTransportProtocols(transportProtocols)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = "${ctx.getString(R.string.invalid_transport_protocols)}: " +
                    transportProtocols
            showAlert.value = true
            return false
        }
        Config.removeVariable("sip_transports")
        if (transportProtocols.isNotEmpty())
            Config.replaceVariable("sip_transports", transportProtocols)
        save = true
        restart = true
    }

    val dnsServers = addMissingPorts(viewModel.dnsServers.value
        .lowercase(Locale.ROOT).replace(" ", ""))
    if (dnsServers != viewModel.oldDnsServers) {
        if (!checkDnsServers(dnsServers)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = "${ctx.getString(R.string.invalid_dns_servers)}: $dnsServers"
            showAlert.value = true
            return false
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
                return false
            }
        } else {
            Config.replaceVariable("dyn_dns", "yes")
            Config.updateDnsServers(BaresipService.dnsServers)
        }
        // Api.net_dns_debug()
        save = true
    }

    if ((Config.variable("sip_verify_server") == "yes") != viewModel.verifyServer.value) {
        Config.replaceVariable("sip_verify_server",
            if (viewModel.verifyServer.value) "yes" else "no")
        Api.config_verify_server_set(viewModel.verifyServer.value)
        save = true
    }

    val userAgent = viewModel.userAgent.value.trim()
    if (userAgent != Config.variable("user_agent")) {
        if (userAgent != "" && !Utils.checkServerVal(userAgent)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = "${ctx.getString(R.string.invalid_user_agent)}: " +
                    userAgent
            showAlert.value = true
            return false
        }
        if (userAgent != "")
            Config.replaceVariable("user_agent", userAgent)
        else
            Config.removeVariable("user_agent")
        save = true
        restart = true
    }

    val videoSize = viewModel.videoSize.value.trim()
    if (videoSize != Config.variable("video_size")) {
        Config.replaceVariable("video_size", videoSize)
        Api.config_video_frame_size_set(videoSize.substringBefore("x").toInt(),
            videoSize.substringAfter("x").toInt())
        save = true
    }

    val videoFps = viewModel.videoFps.value.trim()
    if (videoFps != Config.variable("video_fps")) {
        val fps = videoFps.toIntOrNull()
        if (fps == null || fps < 10 || fps > 30) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = String.format(ctx.getString(R.string.invalid_fps), fps ?: 0)
            showAlert.value = true
            return false
        }
        Config.replaceVariable("video_fps", videoFps)
        Api.config_video_fps_set(fps)
        save = true
    }

    val ringtoneUri = viewModel.ringtoneUri.value
    Preferences(ctx).ringtoneUri = ringtoneUri
    BaresipService.rt = RingtoneManager.getRingtone(ctx, ringtoneUri.toUri())

    val contactsMode = viewModel.contactsMode.value
    if (Config.variable("contacts_mode").lowercase() != contactsMode) {
        Config.replaceVariable("contacts_mode", contactsMode)
        BaresipService.contactsMode = contactsMode
        val baresipService = Intent(ctx, BaresipService::class.java)
        when (contactsMode) {
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

    val darkTheme = viewModel.darkTheme.value
    val newDisplayTheme = if (darkTheme)
        AppCompatDelegate.MODE_NIGHT_YES
    else
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    if (Preferences(ctx).displayTheme == AppCompatDelegate.MODE_NIGHT_YES != darkTheme) {
        Preferences(ctx).displayTheme = newDisplayTheme
        BaresipService.darkTheme.value = darkTheme
        AppCompatDelegate.setDefaultNightMode(newDisplayTheme)
        Config.replaceVariable("dark_theme",
            if (darkTheme) "yes" else "no")
        save = true
    }

    val dynamicColors = viewModel.dynamicColors.value
    if (BaresipService.dynamicColors.value != dynamicColors) {
        BaresipService.dynamicColors.value = dynamicColors
        Config.replaceVariable("dynamic_colors",
            if (dynamicColors) "yes" else "no")
        save = true
    }

    val colorblind = viewModel.colorblind.value
    if ((Config.variable("colorblind") == "yes") != colorblind) {
        Config.replaceVariable(
            "colorblind",
            if (colorblind) "yes" else "no"
        )
        BaresipService.colorblind = colorblind
        UserAgent.updateColorblindStatus()
        val baresipService = Intent(ctx, BaresipService::class.java)
        baresipService.action = "Update Notification"
        ctx.startService(baresipService)
        save = true
    }

    val proximitySensing = viewModel.proximitySensing.value
    if ((Config.variable("proximity_sensing") == "yes") != proximitySensing) {
        Config.replaceVariable(
            "proximity_sensing",
            if (proximitySensing) "yes" else "no"
        )
        BaresipService.proximitySensing = proximitySensing
        save = true
    }

    val debug = viewModel.debug.value
    if ((Config.variable("log_level") == "0") != debug) {
        val logLevelString = if (debug) "0" else "2"
        Config.replaceVariable("log_level", logLevelString)
        Api.log_level_set(logLevelString.toInt())
        Log.logLevelSet(logLevelString.toInt())
        save = true
    }

    val sipTrace = viewModel.sipTrace.value
    if (BaresipService.sipTrace != sipTrace) {
        BaresipService.sipTrace = sipTrace
        Api.uag_enable_sip_trace(sipTrace)
    }

    if (save) Config.save()

    return true
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

private fun checkTransportProtocols(transportProtocols: String): Boolean {
    if (transportProtocols.isEmpty())
        return true
    for (protocol in transportProtocols.split(","))
        if (protocol !in listOf("udp", "tcp", "tls", "ws", "wss"))
            return false
    return true
}

private fun checkDnsServers(dnsServers: String): Boolean {
    if (dnsServers.isEmpty()) return true
    for (server in dnsServers.split(","))
        if (!Utils.checkIpPort(server.trim())) return false
    return true
}

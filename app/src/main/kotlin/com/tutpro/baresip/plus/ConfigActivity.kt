package com.tutpro.baresip.plus

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.LabelText
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar
import com.tutpro.baresip.plus.Utils.copyInputStreamToFile
import java.io.File
import java.io.FileInputStream
import java.util.Locale

class ConfigActivity : ComponentActivity() {

    private lateinit var baresipService: Intent
    private var oldAutoStart = false
    private var newAutoStart = false
    private var oldDarkTheme = false
    private var newDarkTheme = false
    private var oldListenAddr = ""
    private var newListenAddr = ""
    private var oldAddressFamily = ""
    private var newAddressFamily = ""
    private var oldDnsServers = ""
    private var newDnsServers = ""
    private var oldTlsCertificateFile = false
    private var newTlsCertificateFile = false
    private var oldVerifyServer = false
    private var newVerifyServer = false
    private var oldCaFile = false
    private var newCaFile = false
    private var oldUserAgent = ""
    private var newUserAgent = ""
    private var oldRingtoneUri = ""
    private var newRingtoneUri = ""
    private var oldVideoSize = Config.variable("video_size")
    private var newVideoSize = oldVideoSize
    private var oldVideoFps = Config.variable("video_fps")
    private var newVideoFps = ""
    private var oldBatteryOptimizations = false
    private var newBatteryOptimizations = false
    private var oldDefaultDialer = false
    private var newDefaultDialer = false
    private var oldContactsMode = ""
    private var newContactsMode = ""
    private var oldDebug = false
    private var newDebug = false
    private var oldSipTrace = false
    private var newSipTrace = false

    private lateinit var roleManager: RoleManager
    private lateinit var powerManager: PowerManager
    private lateinit var dialerRoleRequest: ActivityResultLauncher<Intent>
    private lateinit var androidSettingsRequest: ActivityResultLauncher<Intent>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    private var save = false
    private var restart = false
    private var audioRestart = false

    private val alertTitle = mutableStateOf("")
    private val alertMessage = mutableStateOf("")
    private val showAlert = mutableStateOf(false)

    private val dialogTitle = mutableStateOf("")
    private val dialogMessage = mutableStateOf("")
    private val positiveText = mutableStateOf("")
    private val onPositiveClicked = mutableStateOf({})
    private val negativeText = mutableStateOf("")
    private val onNegativeClicked = mutableStateOf({})
    private val showDialog = mutableStateOf(false)

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

    private val certificateRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val certPath = BaresipService.filesPath + "/cert.pem"
            val certFile = File(certPath)
            if (it.resultCode == RESULT_OK) {
                it.data?.data?.also { uri ->
                    try {
                        val inputStream =
                            applicationContext.contentResolver.openInputStream(uri)
                                    as FileInputStream
                        certFile.copyInputStreamToFile(inputStream)
                        inputStream.close()
                        Config.replaceVariable("sip_certificate", certPath)
                        save = true
                        restart = true
                    } catch (e: Error) {
                        alertTitle.value = getString(R.string.error)
                        alertMessage.value = getString(R.string.read_cert_error) + ": " + e.message
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

    private val caCertsRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val caCertsFile = File(BaresipService.filesPath + "/ca_certs.crt")
            if (it.resultCode == RESULT_OK)
                it.data?.data?.also { uri ->
                    try {
                        val inputStream =
                            applicationContext.contentResolver.openInputStream(uri)
                                    as FileInputStream
                        caCertsFile.copyInputStreamToFile(inputStream)
                        inputStream.close()
                        restart = true
                    } catch (e: Error) {
                        alertTitle.value = getString(R.string.error)
                        alertMessage.value = getString(R.string.read_ca_certs_error) + ": " + e.message
                        showAlert.value = true
                        newCaFile = false
                    }
                }
            else
                newCaFile = false
            if (!newCaFile)
                caCertsFile.delete()
        }

    private val audioRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        audioRestart = it.resultCode == RESULT_OK
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (VERSION.SDK_INT >= 33)
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

        Utils.addActivity("config")

        baresipService = Intent(this@ConfigActivity, BaresipService::class.java)

        oldAutoStart = Config.variable("auto_start") == "yes"
        if (oldAutoStart && !isAppearOnTopPermissionGranted(this)) {
            Config.replaceVariable("auto_start", "no")
            oldAutoStart = false
            save = true
        }
        newAutoStart = oldAutoStart

        oldListenAddr = Config.variable("sip_listen")

        oldAddressFamily = Config.variable("net_af").lowercase()
        newAddressFamily = oldAddressFamily

        val dynamicDns = Config.variable("dyn_dns")
        if (dynamicDns == "yes") {
            oldDnsServers = ""
        } else {
            val servers = Config.variables("dns_server")
            var serverList = ""
            for (server in servers)
                serverList += ", $server"
            oldDnsServers = serverList.trimStart(',').trimStart(' ')
        }

        val certFile = File(BaresipService.filesPath + "/cert.pem")
        oldTlsCertificateFile = certFile.exists()

        oldVerifyServer = Config.variable("sip_verify_server") == "yes"
        newVerifyServer = oldVerifyServer

        val caCertsFile = File(BaresipService.filesPath + "/ca_certs.crt")
        oldCaFile = caCertsFile.exists()

        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        oldBatteryOptimizations = powerManager
            .isIgnoringBatteryOptimizations(packageName) == false
        newBatteryOptimizations = oldBatteryOptimizations

        androidSettingsRequest = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            newBatteryOptimizations = powerManager
                .isIgnoringBatteryOptimizations(packageName) == false
        }

        dialerRoleRequest = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            Log.d(TAG, "dialerRoleRequest succeeded: " +
                    "${it.resultCode == RESULT_OK}")
            if (VERSION.SDK_INT >= 29)
                newDefaultDialer = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            }

        if (VERSION.SDK_INT >= 29) {
            roleManager = getSystemService(ROLE_SERVICE) as RoleManager
            oldDefaultDialer = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        }

        oldContactsMode = Config.variable("contacts_mode").lowercase()
        newContactsMode = oldContactsMode

        oldDarkTheme = Preferences(applicationContext).displayTheme ==
                AppCompatDelegate.MODE_NIGHT_YES
        newDarkTheme = oldDarkTheme

        oldDebug = Config.variable("log_level") == "0"
        newDebug = oldDebug

        oldSipTrace = BaresipService.sipTrace
        newSipTrace = oldSipTrace

        requestPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    ConfigScreen(this, title) { goBack() }
                }
            }
        }
    }

    @Composable
    fun ConfigScreen(ctx: Context, title: String, navigateBack: () -> Unit) {
        Scaffold(
            modifier = Modifier
                .fillMaxHeight()
                .imePadding()
                .safeDrawingPadding(),
            containerColor = LocalCustomColors.current.background,
            topBar = { TopAppBar(title, navigateBack) },
            content = { contentPadding ->
                ConfigContent(ctx, contentPadding)
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopAppBar(title: String, navigateBack: () -> Unit) {
        TopAppBar(
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
    fun ConfigContent(ctx: Context, contentPadding: PaddingValues) {

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
            StartAutomatically(ctx)
            ListenAddress()
            AddressFamily()
            DnsServers()
            TlsCertificateFile(ctx)
            VerifyServer()
            CaFile(ctx)
            UserAgent()
            AudioSettings(ctx)
            Ringtone()
            VideoSize()
            VideoFps()
            BatteryOptimizations()
            if (VERSION.SDK_INT >= 29)
                DefaultDialer()
            Contacts(ctx)
            DarkTheme()
            Debug()
            SipTrace()
            Reset()
        }
    }

    @Composable
    private fun StartAutomatically(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.start_automatically),
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.start_automatically)
                        alertMessage.value = getString(R.string.start_automatically_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            var startAutomatically by remember { mutableStateOf(oldAutoStart) }
            Switch(
                checked = startAutomatically,
                onCheckedChange = {
                    if (it) {
                        if (!isAppearOnTopPermissionGranted(ctx)) {
                            dialogTitle.value = getString(R.string.notice)
                            dialogMessage.value = getString(R.string.appear_on_top_permission)
                            positiveText.value = getString(R.string.ok)
                            onPositiveClicked.value = {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                startActivity(intent)
                            }
                            negativeText.value = getString(R.string.cancel)
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
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            var listenAddr by remember { mutableStateOf(oldListenAddr) }
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
                        alertTitle.value = getString(R.string.listen_address)
                        alertMessage.value = getString(R.string.listen_address_help)
                        showAlert.value = true
                    },
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
            Modifier.fillMaxWidth().padding(top = 12.dp).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.address_family),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.address_family)
                        alertMessage.value = getString(R.string.address_family_help)
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
                mutableIntStateOf(familyValues.indexOf(oldAddressFamily))
            }
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
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
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
                        alertTitle.value = getString(R.string.dns_servers)
                        alertMessage.value = getString(R.string.dns_servers_help)
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
    private fun TlsCertificateFile(ctx: Context) {
        val showAlertDialog = remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.tls_certificate_file),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.tls_certificate_file)
                        alertMessage.value = getString(R.string.tls_certificate_file_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            var tlsCertificateFile by remember { mutableStateOf(oldTlsCertificateFile) }
            Switch(
                checked = tlsCertificateFile,
                onCheckedChange = {
                    tlsCertificateFile = it
                    newTlsCertificateFile = tlsCertificateFile
                    if (it)
                        if (VERSION.SDK_INT < 29) {
                            tlsCertificateFile = false
                            when {
                                ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    Log.d(TAG, "Read External Storage permission granted")
                                    val downloadsPath = Utils.downloadsPath("cert.pem")
                                    val content = Utils.getFileContents(downloadsPath)
                                    if (content == null) {
                                        alertTitle.value = getString(R.string.error)
                                        alertMessage.value = getString(R.string.read_cert_error)
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
                                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                                    showAlertDialog.value = true
                                }
                                else ->
                                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
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
                title = getString(R.string.notice),
                message = getString(R.string.no_read_permission),
                positiveButtonText = getString(R.string.ok),
                onPositiveClicked = { requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) },
                negativeButtonText = "",
                onNegativeClicked = {},
            )
    }

    @Composable
    private fun VerifyServer() {
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.verify_server),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.verify_server)
                        alertMessage.value = getString(R.string.verify_server_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            var verifyServer by remember { mutableStateOf(oldVerifyServer) }
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
    private fun CaFile(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.tls_ca_file),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.tls_ca_file)
                        alertMessage.value = getString(R.string.tls_ca_file_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            var caFile by remember { mutableStateOf(oldCaFile) }
            Switch(
                checked = caFile,
                onCheckedChange = {
                    caFile = it
                    newCaFile = caFile
                    if (it) {
                        if (VERSION.SDK_INT < 29) {
                            caFile = false
                            when {
                                ContextCompat.checkSelfPermission(ctx,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    Log.d(TAG, "Read External Storage permission granted")
                                    val downloadsPath = Utils.downloadsPath("ca_certs.crt")
                                    val content = Utils.getFileContents(downloadsPath)
                                    if (content == null) {
                                        alertTitle.value = getString(R.string.error)
                                        alertMessage.value = getString(R.string.read_ca_certs_error)
                                        showAlert.value = true
                                        return@Switch
                                    }
                                    File(BaresipService.filesPath + "/ca_certs.crt").writeBytes(content)
                                    caFile = true
                                    restart = true
                                }
                                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                                    dialogTitle.value = getString(R.string.notice)
                                    dialogMessage.value = getString(R.string.no_read_permission)
                                    positiveText.value = getString(R.string.ok)
                                    onPositiveClicked.value = {
                                        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                    negativeText.value = ""
                                    showDialog.value = true
                                }
                                else ->
                                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
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
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var userAgent by remember { mutableStateOf(oldUserAgent) }
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
                        alertTitle.value = getString(R.string.user_agent)
                        alertMessage.value = getString(R.string.user_agent_help)
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
    private fun AudioSettings(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = stringResource(R.string.audio_settings),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        audioRequest.launch(Intent(ctx, AudioActivity::class.java))
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp,
                fontWeight = FontWeight. Bold
            )
        }
    }

    @Composable
    private fun VideoSize() {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.video_size),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.video_size)
                        alertMessage.value = getString(R.string.video_size_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val frameSizes = mutableListOf<String>()
            frameSizes.addAll(Config.videoSizes)
            frameSizes.removeIf{it == oldVideoSize}
            frameSizes.add(0, oldVideoSize)
            val itemPosition = remember {
                mutableIntStateOf(frameSizes.indexOf(oldVideoSize))
            }
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = frameSizes[itemPosition.intValue],
                        color = LocalCustomColors.current.itemText)
                    CustomElements.DrawDrawable(R.drawable.arrow_drop_down,
                        tint = LocalCustomColors.current.itemText)
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = {
                        isDropDownExpanded.value = false
                    }) {
                    frameSizes.forEachIndexed { index, size ->
                        DropdownMenuItem(text = {
                            Text(text = size)
                        },
                            onClick = {
                                isDropDownExpanded.value = false
                                itemPosition.intValue = index
                                newVideoSize = frameSizes[index]
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
    private fun VideoFps() {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var fps by remember { mutableStateOf(oldVideoFps) }
            newVideoFps = fps
            OutlinedTextField(
                value = fps,
                placeholder = { Text(stringResource(R.string.user_agent)) },
                onValueChange = {
                    fps = it
                    newVideoFps = fps
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = getString(R.string.video_fps)
                        alertMessage.value = getString(R.string.video_fps_help)
                        showAlert.value = true
                    },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.video_fps)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    private fun Ringtone() {
        oldRingtoneUri = if (Preferences(applicationContext).ringtoneUri == "")
            RingtoneManager.getActualDefaultRingtoneUri(applicationContext, RingtoneManager.TYPE_RINGTONE).toString()
        else
            Preferences(applicationContext).ringtoneUri!!
        newRingtoneUri = oldRingtoneUri
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
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                            RingtoneManager.TYPE_RINGTONE)
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.select_ringtone))
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, newRingtoneUri.toUri())
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
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.battery_optimizations),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.battery_optimizations)
                        alertMessage.value = getString(R.string.battery_optimizations_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            var battery by remember { mutableStateOf(oldBatteryOptimizations) }
            Switch(
                checked = battery,
                onCheckedChange = {
                    battery = it
                    newBatteryOptimizations = battery
                     try {
                        androidSettingsRequest.launch(Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"))
                    } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "ActivityNotFound exception: ${e.message}")
                    }
                }
            )
        }
    }

    @RequiresApi(29)
    @Composable
    private fun DefaultDialer() {
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.default_phone_app),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.default_phone_app)
                        alertMessage.value = getString(R.string.default_phone_app_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            var defaultDialer by remember { mutableStateOf(oldDefaultDialer) }
            Switch(
                checked = defaultDialer,
                onCheckedChange = {
                    defaultDialer = it
                    newDefaultDialer = defaultDialer
                    if (it) {
                        if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                            alertTitle.value = getString(R.string.alert)
                            alertMessage.value = getString(R.string.dialer_role_not_available)
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
    private fun Contacts(ctx: Context) {
        val showAlertDialog = remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.contacts),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.contacts)
                        alertMessage.value = getString(R.string.contacts_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val contactNames = listOf("baresip",  "Android", "Both")
            val contactValues = listOf("baresip",  "android", "both")
            val itemPosition = remember {
                mutableIntStateOf(contactValues.indexOf(oldContactsMode))
            }
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
                        DropdownMenuItem(text = {
                            Text(text = name)
                        },
                            onClick = {
                                isDropDownExpanded.value = false
                                val mode = contactValues[index]
                                if (mode != "baresip" && !Utils.checkPermissions(applicationContext, contactsPermissions)) {
                                    dialogTitle.value = getString(R.string.consent_request)
                                    dialogMessage.value = getString(R.string.contacts_consent)
                                    positiveText.value = getString(R.string.accept)
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
                                        } else {
                                            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) ||
                                                    shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CONTACTS))
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
                                    negativeText.value = getString(R.string.deny)
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
                    title = getString(R.string.notice),
                    message = getString(R.string.no_android_contacts),
                    positiveButtonText = getString(R.string.ok),
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
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.dark_theme),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.dark_theme)
                        alertMessage.value = getString(R.string.dark_theme_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            var darkTheme by remember { mutableStateOf(oldDarkTheme) }
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
    private fun Debug() {
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.debug),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.debug)
                        alertMessage.value = getString(R.string.debug_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            var debug by remember { mutableStateOf(oldDebug) }
            Switch(
                checked = debug,
                onCheckedChange = {
                    debug = it
                    newDebug = debug
                }
            )
        }
    }

    @Composable
    private fun SipTrace() {
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.sip_trace),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.sip_trace)
                        alertMessage.value = getString(R.string.sip_trace_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            var sipTrace by remember { mutableStateOf(oldSipTrace) }
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
    private fun Reset() {
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.reset_config),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.reset_config)
                        alertMessage.value = getString(R.string.reset_config_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            var reset by remember { mutableStateOf(false) }
            Switch(
                checked = reset,
                onCheckedChange = {
                    dialogTitle.value = getString(R.string.confirmation)
                    dialogMessage.value = getString(R.string.reset_config_alert)
                    positiveText.value = getString(R.string.reset)
                    onPositiveClicked.value = {
                        Config.reset()
                        save = false
                        restart = true
                        done()
                    }
                    negativeText.value = getString(R.string.cancel)
                    onNegativeClicked.value = {
                        reset = false
                        negativeText.value = ""
                    }
                    showDialog.value = true
                }
            )
        }
    }

    private fun checkOnClick() {

        if (BaresipService.activities.indexOf("config") == -1)
            return

        if (oldAutoStart != newAutoStart) {
            Config.replaceVariable("auto_start",
                if (newAutoStart) "yes" else "no")
            save = true
        }

        val listenAddr = newListenAddr.trim()
        if (listenAddr != oldListenAddr) {
            if ((listenAddr != "") && !Utils.checkIpPort(listenAddr)) {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = "${getString(R.string.invalid_listen_address)}: $listenAddr"
                showAlert.value = true
                return
            }
            Config.replaceVariable("sip_listen", listenAddr)
            save = true
            restart = true
        }

        if (oldAddressFamily != newAddressFamily) {
            Config.replaceVariable("net_af", newAddressFamily)
            save = true
            restart = true
        }

        var dnsServers = newDnsServers.lowercase(Locale.ROOT)
            .replace(" ", "")
        dnsServers = addMissingPorts(dnsServers)
        if (dnsServers != oldDnsServers.replace(" ", "")) {
            if (!checkDnsServers(dnsServers)) {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = "${getString(R.string.invalid_dns_servers)}: $dnsServers"
                showAlert.value = true
                return
            }
            Config.removeVariable("dns_server")
            if (dnsServers.isNotEmpty()) {
                for (server in dnsServers.split(","))
                    Config.addVariable("dns_server", server)
                Config.replaceVariable("dyn_dns", "no")
                if (Api.net_use_nameserver(dnsServers) != 0) {
                    alertTitle.value = getString(R.string.notice)
                    alertMessage.value = "${getString(R.string.failed_to_set_dns_servers)}: $dnsServers"
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

        if (oldVerifyServer != newVerifyServer) {
            Config.replaceVariable("sip_verify_server", if (newVerifyServer) "yes" else "no")
            Api.config_verify_server_set(newVerifyServer)
            save = true
        }

        newUserAgent = newUserAgent.trim()
        if (newUserAgent != oldUserAgent) {
            if ((newUserAgent != "") && !Utils.checkServerVal(newUserAgent)) {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = "${getString(R.string.invalid_user_agent)}: $newUserAgent"
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

        if (oldVideoSize != newVideoSize) {
            Config.replaceVariable("video_size", newVideoSize)
            Api.config_video_frame_size_set(newVideoSize.substringBefore("x").toInt(),
                newVideoSize.substringAfter("x").toInt())
            save = true
        }

        val videoFps = newVideoFps.trim().toInt()
        if (oldVideoFps.toInt() != videoFps) {
            if (videoFps < 10 || videoFps > 30) {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_fps), videoFps)
                showAlert.value = true
                return
            }
            Config.replaceVariable("video_fps", videoFps.toString())
            Api.config_video_fps_set(videoFps)
            save = true
        }

        Log.d(TAG, "Old/new ringtone: $oldRingtoneUri / $newRingtoneUri")
        if (newRingtoneUri != oldRingtoneUri) {
            Preferences(applicationContext).ringtoneUri = newRingtoneUri
            BaresipService.rt = RingtoneManager.getRingtone(applicationContext, newRingtoneUri.toUri())
        }
        if (oldContactsMode != newContactsMode) {
            Config.replaceVariable("contacts_mode", newContactsMode)
            BaresipService.contactsMode = newContactsMode
            when (newContactsMode) {
                "baresip" -> {
                    BaresipService.androidContacts.value = listOf()
                    Contact.restoreBaresipContacts()
                    baresipService.action = "Stop Content Observer"
                }
                "android" -> {
                    BaresipService.baresipContacts.value = mutableListOf()
                    Contact.loadAndroidContacts(this)
                    baresipService.action = "Start Content Observer"
                }
                "both" -> {
                    Contact.restoreBaresipContacts()
                    Contact.loadAndroidContacts(this)
                    baresipService.action = "Start Content Observer"
                }
            }
            Contact.contactsUpdate()
            startService(baresipService)
            save = true
        }

        val newDisplayTheme = if (newDarkTheme)
            AppCompatDelegate.MODE_NIGHT_YES
        else
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        if (oldDarkTheme != newDarkTheme) {
            Preferences(applicationContext).displayTheme = newDisplayTheme
            BaresipService.darkTheme.value = newDarkTheme
            Config.replaceVariable("dark_theme",
                if (newDarkTheme) "yes" else "no")
            save = true
        }

        if (oldDebug != newDebug) {
            val logLevelString = if (newDebug) "0" else "2"
            Config.replaceVariable("log_level", logLevelString)
            Api.log_level_set(logLevelString.toInt())
            Log.logLevelSet(logLevelString.toInt())
            save = true
        }

        if (oldSipTrace != newSipTrace) {
            BaresipService.sipTrace = newSipTrace
            Api.uag_enable_sip_trace(newSipTrace)
        }

        done()

    }

    override fun onStart() {
        super.onStart()
        requestPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    }

    override fun onDestroy() {
        if (VERSION.SDK_INT >= 33) {
            if (backInvokedCallback != null)
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backInvokedCallback!!)
        }
        else
            onBackPressedCallback.remove()
        super.onDestroy()
    }

    private fun isAppearOnTopPermissionGranted(ctx: Context): Boolean {
        return Settings.canDrawOverlays(ctx)
    }

    private fun done() {
        if (save)
            Config.save()
        BaresipService.activities.remove("config")
        val intent = Intent(this, MainActivity::class.java)
        if (restart || audioRestart)
            intent.putExtra("restart", true)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun goBack() {
        BaresipService.activities.remove("config")
        if (audioRestart) {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("restart", true)
            setResult(RESULT_OK, intent)
        } else {
            setResult(RESULT_CANCELED, Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun checkDnsServers(dnsServers: String): Boolean {
        if (dnsServers.isEmpty()) return true
        for (server in dnsServers.split(","))
            if (!Utils.checkIpPort(server.trim())) return false
        return true
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

}


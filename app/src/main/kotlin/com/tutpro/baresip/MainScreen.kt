package com.tutpro.baresip

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.outlined.ArrowCircleRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tutpro.baresip.BaresipService.Companion.contactNames
import com.tutpro.baresip.BaresipService.Companion.uas
import com.tutpro.baresip.BaresipService.Companion.uasStatus
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.DropdownMenu
import com.tutpro.baresip.CustomElements.PasswordDialog
import com.tutpro.baresip.CustomElements.SelectableAlertDialog
import com.tutpro.baresip.CustomElements.verticalScrollbar
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dialpadButtonEnabled = mutableStateOf(true)
private var pullToRefreshEnabled = mutableStateOf(true)

private var downloadsInputUri: Uri? = null
private var downloadsOutputUri: Uri? = null

private val passwordTitle = mutableStateOf("")
private val showPasswordDialog = mutableStateOf(false)
private val showPasswordsDialog = mutableStateOf(false)

private var passwordAccounts = mutableListOf<String>()
private var password = mutableStateOf("")

private val selectItems = mutableStateOf(listOf<String>())
private val selectItemAction = mutableStateOf<(Int) -> Unit>({ _ -> run {} })
private val showSelectItemDialog = mutableStateOf(false)
private var callRunnable: Runnable? = null
private var callHandler: Handler = Handler(Looper.getMainLooper())
private var audioModeChangedListener: AudioManager.OnModeChangedListener? = null

fun NavGraphBuilder.mainScreenRoute(
    navController: NavController,
    viewModel: ViewModel,
    onRequestPermissions: () -> Unit,
    onRestartApp: () -> Unit,
    onQuitApp: () -> Unit
) {
    composable("main") {
        MainScreen(
            navController = navController,
            viewModel = viewModel,
            onRequestPermissions = onRequestPermissions,
            onRestartClick = onRestartApp,
            onQuitClick = onQuitApp
        )
    }
}

@Composable
private fun MainScreen(
    navController: NavController,
    viewModel: ViewModel,
    onRequestPermissions: () -> Unit,
    onRestartClick: () -> Unit,
    onQuitClick: () -> Unit
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val ua = uas.value.find { it.account.aor == viewModel.selectedAor.value }
    val call = ua?.currentCall()

    val showKeyboard by viewModel.showKeyboard.collectAsState()
    val hideKeyboard by viewModel.hideKeyboard.collectAsState()

    LaunchedEffect(showKeyboard) {
        if (viewModel.showKeyboard.value > 0)
            keyboardController?.show()
    }

    LaunchedEffect(hideKeyboard) {
        if (viewModel.hideKeyboard.value > 0)
            keyboardController?.hide()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "Resumed to MainScreen")
                    BaresipService.isMainVisible = true
                    val incomingCall = Call.call("incoming")
                    viewModel.updateCalls(Call.calls().toList())
                    if (incomingCall != null)
                        spinToAor(viewModel, incomingCall.ua.account.aor)
                    else {
                        if (uas.value.isNotEmpty()) {
                            if (viewModel.selectedAor.value == "") {
                                if (Call.inCall())
                                    spinToAor(viewModel, Call.calls().last().ua.account.aor)
                                else
                                    spinToAor(viewModel, uas.value.first().account.aor)
                            }
                        }
                    }
                    val ua = UserAgent.ofAor(viewModel.selectedAor.value)
                    if (ua != null) {
                        showCall(ctx, viewModel, ua)
                        viewModel.triggerAccountUpdate()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "Paused from MainScreen")
                    BaresipService.isMainVisible = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            Log.d(TAG, "onDispose for MainScreen")
            lifecycleOwner.lifecycle.removeObserver(observer)
            BaresipService.isMainVisible = false
        }
    }

    val encryptPasswordTitle = stringResource(R.string.encrypt_password)
    val backupRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.also { uri ->
                downloadsOutputUri = uri
                passwordTitle.value = encryptPasswordTitle
                showPasswordDialog.value = true
            }
        }
    }

    val noticeTitle = stringResource(R.string.notice)
    val noBackupMessage = stringResource(R.string.no_backup)
    fun launchBackupRequest() {
        if (Build.VERSION.SDK_INT < 29) {
            if (!Utils.checkPermissions(ctx, arrayOf(WRITE_EXTERNAL_STORAGE))) {
                alertTitle.value = noticeTitle
                alertMessage.value = noBackupMessage
                showAlert.value = true
            }
            else {
                val path = Utils.downloadsPath("baresip.bs")
                downloadsOutputUri = File(path).toUri()
                passwordTitle.value = encryptPasswordTitle
                showPasswordDialog.value = true
            }
        }
        else {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(
                    Intent.EXTRA_TITLE,
                    "baresip_" + SimpleDateFormat(
                        "yyyy_MM_dd_HH_mm_ss",
                        Locale.getDefault()
                    ).format(Date())
                )
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            }
            backupRequestLauncher.launch(intent)
        }
    }

    val decryptPasswordTitle = stringResource(R.string.decrypt_password)
    val restoreRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.also { uri ->
                downloadsInputUri = uri
                passwordTitle.value = decryptPasswordTitle
                showPasswordDialog.value = true
            }
        }
    }

    val noRestoreMessage = stringResource(R.string.no_restore)
    fun launchRestoreRequest() {
        if (Build.VERSION.SDK_INT < 29) {
            if (!Utils.checkPermissions(ctx, arrayOf(READ_EXTERNAL_STORAGE))) {
                alertTitle.value = noticeTitle
                alertMessage.value = noRestoreMessage
                showAlert.value = true
            }
            else {
                val path = Utils.downloadsPath("baresip.bs")
                downloadsInputUri = File(path).toUri()
                passwordTitle.value = decryptPasswordTitle
                showPasswordDialog.value = true
            }
        }
        else {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            }
            restoreRequestLauncher.launch(intent)
        }
    }

    val logcatRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK)
            result.data?.data?.also { uri ->
                try {
                    val out = ctx.contentResolver.openOutputStream(uri)
                    val process = Runtime.getRuntime().exec("logcat -d --pid=${Process.myPid()}")
                    val bufferedReader = process.inputStream.bufferedReader()
                    bufferedReader.forEachLine { line ->
                        out!!.write(line.toByteArray())
                        out.write('\n'.code.toByte().toInt())
                    }
                    out!!.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write logcat to file: $e")
                }
            }
    }

    fun launchLogcatRequest() {
        if (Build.VERSION.SDK_INT >= 29) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TITLE,
                    "baresip_logcat_" + SimpleDateFormat(
                        "yyyy_MM_dd_HH_mm_ss",
                        Locale.getDefault()
                    ).format(Date())
                )
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            }
            logcatRequestLauncher.launch(intent)
        }
    }

    LaunchedEffect(key1 = call?.status, key2 = configuration.orientation) {
        val isConnected = call != null && call.status.value == "connected" && !call.held
        if (isConnected) {
            if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                call.focusDtmf.value = true
                delay(300)
                keyboardController?.show()
            }
            else
                keyboardController?.hide()
        }
    }

    if (showPasswordDialog.value)
        PasswordDialog(
            ctx = ctx,
            showPasswordDialog = showPasswordDialog,
            password = password,
            keyboardController = keyboardController,
            title = passwordTitle.value,
            okAction = {
                if (password.value != "") {
                    if (passwordTitle.value == encryptPasswordTitle)
                        backup(ctx, password.value)
                    else
                        restore(ctx, password.value, onRestartClick)
                    password.value = ""
                }
            },
            cancelAction = {
                if (downloadsOutputUri != null) {
                    Utils.deleteFile(ctx, downloadsOutputUri!!)
                }
            }
        )

    if (showPasswordsDialog.value) {
        if (passwordAccounts.isNotEmpty()) {
            val account = passwordAccounts.removeAt(0)
            val params = account.substringAfter(">")
            if (Utils.paramValue(params, "auth_user") != "" &&
                    Utils.paramValue(params, "auth_pass") == "") {
                val aor = account.substringAfter("<").substringBefore(">")
                PasswordDialog(
                    ctx = ctx,
                    showPasswordDialog = showPasswordsDialog,
                    password = password,
                    keyboardController = keyboardController,
                    title = stringResource(R.string.authentication_password),
                    message = stringResource(R.string.account) + " " + Utils.plainAor(aor),
                    okAction = {
                        if (password.value != "")
                            BaresipService.aorPasswords[aor] = password.value
                        showPasswordsDialog.value = true
                    },
                    cancelAction = {
                        showPasswordsDialog.value = true
                    }
                )
            } else {
                showPasswordsDialog.value = false
                showPasswordsDialog.value = true
            }
        }
        else
            onRequestPermissions()
    }

    LaunchedEffect(Unit) {
        if (!BaresipService.isServiceRunning) {
            val path = ctx.filesDir.absolutePath + "/accounts"
            if (File(path).exists()) {
                passwordAccounts = String(
                    Utils.getFileContents(path)!!,
                    Charsets.UTF_8
                ).lines().toMutableList()
                showPasswordsDialog.value = true
            } else {
                // Baresip is started for the first time
                onRequestPermissions()
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute, viewModel.selectedAor.collectAsState()) {
        if (currentRoute == "main") {
            Log.d(TAG, "Updating icons for AOR: ${viewModel.selectedAor.value}")
            viewModel.triggerAccountUpdate()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
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
                    viewModel = viewModel,
                    navController = navController,
                    onBackupClick = { launchBackupRequest() },
                    onRestoreClick = { launchRestoreRequest() },
                    onLogcatClick = { launchLogcatRequest() },
                    onRestartClick = onRestartClick,
                    onQuitClick = onQuitClick
                )
            }
        },
        bottomBar = { BottomBar(ctx, viewModel, navController) },
        content = { contentPadding ->
            MainContent(navController, viewModel, contentPadding)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
    viewModel: ViewModel,
    navController: NavController,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onLogcatClick: () -> Unit,
    onRestartClick: () -> Unit,
    onQuitClick: () -> Unit
) {
    val ctx = LocalContext.current
    val currentMicIcon by viewModel.micIcon.collectAsState()

    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val recOffImage = Icons.Filled.VoiceOverOff
    val recOnImage = Icons.Filled.RecordVoiceOver
    var isRecOn by remember { mutableStateOf(BaresipService.isRecOn) }
    val isSpeakerOn = remember { mutableStateOf(Utils.isSpeakerPhoneOn(am)) }
    var menuExpanded by remember { mutableStateOf(false) }

    val about = stringResource(R.string.about)
    val settings = stringResource(R.string.configuration)
    val accounts = stringResource(R.string.accounts)
    val backup = stringResource(R.string.backup)
    val restore = stringResource(R.string.restore)
    val logcat = stringResource(R.string.logcat)
    val restart = stringResource(R.string.restart)
    val quit = stringResource(R.string.quit)

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.baresip),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
        actions = {

            Spacer(modifier = Modifier.width(8.dp))

            val callRecordingTitle = stringResource(R.string.call_recording_title)
            val callRecordingMessage = stringResource(R.string.call_recording_tip)
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = {
                            if (Call.call("connected") == null) {
                                BaresipService.isRecOn = !BaresipService.isRecOn
                                if (BaresipService.isRecOn) {
                                    Api.module_load("sndfile")
                                } else {
                                    Api.module_unload("sndfile")
                                }
                                isRecOn = BaresipService.isRecOn
                            } else {
                                Toast.makeText(ctx, R.string.rec_in_call, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLongClick = {
                            alertTitle.value = callRecordingTitle
                            alertMessage.value = callRecordingMessage
                            showAlert.value = true
                        }
                    )
            ) {
                Icon(
                    imageVector = if (isRecOn) recOnImage else recOffImage,
                    contentDescription = null,
                    tint = if (isRecOn)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(22.dp))

            val microPhoneTitle = stringResource(R.string.microphone_title)
            val microPhoneMessage = stringResource(R.string.microphone_tip)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = {
                            if (Call.call("connected") != null) {
                                BaresipService.isMicMuted = !BaresipService.isMicMuted
                                if (BaresipService.isMicMuted) {
                                    viewModel.updateMicIcon(Icons.Filled.MicOff)
                                    Api.calls_mute(true)
                                } else {
                                    viewModel.updateMicIcon(Icons.Filled.Mic)
                                    Api.calls_mute(false)
                                }
                            }
                        },
                        onLongClick = {
                            alertTitle.value = microPhoneTitle
                            alertMessage.value = microPhoneMessage
                            showAlert.value = true
                        }
                    )
            ) {
                Icon(
                    imageVector = currentMicIcon,
                    contentDescription = null,
                    tint = if (BaresipService.isMicMuted)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            val speakerPhoneTitle = stringResource(R.string.speakerphone_title)
            val speakerPhoneMessage = stringResource(R.string.speakerphone_tip)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 31)
                                Log.d(TAG, "Toggling speakerphone when dev/mode is " +
                                            "${am.communicationDevice!!.type}/${am.mode}")
                            isSpeakerOn.value = !Utils.isSpeakerPhoneOn(am)
                            Utils.toggleSpeakerPhone(ContextCompat.getMainExecutor(ctx), am)
                        },
                        onLongClick = {
                            alertTitle.value = speakerPhoneTitle
                            alertMessage.value = speakerPhoneMessage
                            showAlert.value = true
                        }
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.SpeakerPhone,
                    contentDescription = null,
                    tint = if (isSpeakerOn.value)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(
                onClick = { menuExpanded = !menuExpanded }
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                items = if (Build.VERSION.SDK_INT >= 29)
                    listOf(about, settings, accounts, backup, restore, logcat, restart, quit)
                else
                    listOf(about, settings, accounts, backup, restore, restart, quit),
                onItemClick = { selectedItem ->
                    menuExpanded = false
                    when (selectedItem) {
                        about -> { navController.navigate("about") }
                        settings -> { navController.navigate("settings") }
                        accounts -> { navController.navigate("accounts") }
                        backup -> onBackupClick()
                        restore -> onRestoreClick()
                        logcat -> onLogcatClick()
                        restart -> onRestartClick()
                        quit -> onQuitClick()
                    }
                }
            )
        }
    )
}

@Composable
private fun BottomBar(ctx: Context, viewModel: ViewModel, navController: NavController) {

    val aor by viewModel.selectedAor.collectAsState()
    val accountUpdate by viewModel.accountUpdate.collectAsState()

    val showVmIcon = remember(aor, accountUpdate) {
        if (aor.isNotEmpty()) Account.ofAor(aor)?.vmUri?.isNotEmpty() ?: false else false
    }
    val hasNewVoicemail = remember(aor, accountUpdate) {
        if (aor.isNotEmpty()) (Account.ofAor(aor)?.vmNew ?: 0) > 0 else false
    }
    val hasUnreadMessages = remember(aor, accountUpdate) {
        if (aor.isNotEmpty()) Account.ofAor(aor)?.unreadMessages ?: false else false
    }
    val hasMissedCalls = remember(aor, accountUpdate) {
        if (aor.isNotEmpty()) Account.ofAor(aor)?.missedCalls ?: false else false
    }

    val isDialpadVisible by viewModel.isDialpadVisible.collectAsState()

    val buttonSize = 48.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (showVmIcon)
            IconButton(
                // Disable the button if no account is selected
                enabled = aor.isNotEmpty(),
                onClick = {
                    val ua = UserAgent.ofAor(aor)!!
                    val acc = ua.account
                    if (acc.vmUri.isNotEmpty()) {
                        dialogTitle.value = ctx.getString(R.string.voicemail_messages)
                        dialogMessage.value = acc.vmMessages(ctx)
                        positiveText.value = ctx.getString(R.string.listen)
                        onPositiveClicked.value = {
                            val intent = Intent(ctx, MainActivity::class.java)
                            intent.putExtra("uap", ua.uap)
                            intent.putExtra("peer", acc.vmUri)
                            handleIntent(ctx, viewModel, intent, "call")
                        }
                        negativeText.value = ctx.getString(R.string.cancel)
                        onNegativeClicked.value = {}
                        showDialog.value = true
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .size(buttonSize)
            ) {
                Icon(
                    imageVector = Icons.Filled.Voicemail,
                    contentDescription = null,
                    Modifier.size(buttonSize),
                    tint = if (hasNewVoicemail) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }

        IconButton(
            onClick = { navController.navigate("contacts") },
            modifier = Modifier
                .weight(1f)
                .size(buttonSize)
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                Modifier.size(buttonSize),
                tint = MaterialTheme.colorScheme.secondary
            )
        }

        IconButton(
            enabled = aor.isNotEmpty(),
            onClick = {
                navController.navigate("chats/$aor")
            },
            modifier = Modifier
                .weight(1f)
                .size(buttonSize)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                Modifier.size(buttonSize),
                tint = if (hasUnreadMessages) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            )
        }

        IconButton(
            enabled = aor.isNotEmpty(),
            onClick = {
                navController.navigate("calls/$aor")
            },
            modifier = Modifier
                .weight(1f)
                .size(buttonSize)
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                Modifier.size(buttonSize),
                tint = if (hasMissedCalls) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            )
        }

        IconButton(
            onClick = { viewModel.toggleDialpadVisibility() },
            modifier = Modifier
                .weight(1f)
                .size(buttonSize),
            enabled = dialpadButtonEnabled.value
        ) {
            Icon(
                imageVector = Icons.Filled.Dialpad,
                contentDescription = null,
                modifier = Modifier.size(buttonSize),
                tint = if (isDialpadVisible)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.secondary
            )
        }
    }
}

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

@Composable
private fun CallCard(
    ctx: Context,
    viewModel: ViewModel,
    call: Call?,
    dialerState: ViewModel.DialerState?
) {
    Column {
        CallUriRow(ctx, viewModel, call, dialerState)
        CallRow(ctx, viewModel, call, dialerState)
        if (call != null && call.showOnHoldNotice.value)
            OnHoldNotice()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(navController: NavController, viewModel: ViewModel, contentPadding: PaddingValues) {

    var isRefreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState()
    var offset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 200
    val ctx = LocalContext.current

    val calls by viewModel.calls.collectAsState()
    val selectedAor by viewModel.selectedAor.collectAsState()
    val filteredCalls = calls.filter { it.ua.account.aor == selectedAor }

    val dialingOrRinging = filteredCalls.any { it.status.value == "outgoing" || it.status.value == "incoming" }
    val conferenceCall = filteredCalls.any { it.conferenceCall }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(1000)
            isRefreshing = false
        }
    }

    if (showAlert.value)
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            positiveButtonText = stringResource(R.string.ok),
        )

    if (showDialog.value)
        AlertDialog(
            showDialog = showDialog,
            title = stringResource(R.string.confirmation),
            message = dialogMessage.value,
            positiveButtonText = positiveText.value,
            onPositiveClicked = onPositiveClicked.value,
            negativeButtonText = negativeText.value,
            onNegativeClicked = onNegativeClicked.value
        )

    SelectableAlertDialog(
        openDialog = showSelectItemDialog,
        title = stringResource(R.string.choose_destination_uri),
        items = selectItems.value,
        onItemClicked = selectItemAction.value,
        neutralButtonText = stringResource(R.string.cancel),
        onNeutralClicked = {}
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(top = 18.dp, bottom = 6.dp, start = 16.dp, end = 16.dp)
            .fillMaxSize()
            .pullToRefresh(
                state = refreshState,
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    if (uas.value.isNotEmpty()) {
                        if (viewModel.selectedAor.value == "")
                            spinToAor(viewModel, uas.value.first().account.aor)
                        val ua = UserAgent.ofAor(viewModel.selectedAor.value)!!
                        if (ua.account.regint > 0)
                            Api.ua_register(ua.uap)
                    }
                },
                enabled = pullToRefreshEnabled.value,
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset = 0f },
                    onDragEnd = {
                        if (offset < -swipeThreshold) {
                            if (uas.value.isNotEmpty()) {
                                val curPos = UserAgent.findAorIndex(viewModel.selectedAor.value)
                                val newPos = if (curPos == null)
                                    0
                                else
                                    (curPos + 1) % uas.value.size
                                if (curPos != newPos) {
                                    val ua = uas.value[newPos]
                                    spinToAor(viewModel, ua.account.aor)
                                    showCall(ctx, viewModel, ua)
                                }
                            }
                        } else if (offset > swipeThreshold) {
                            if (uas.value.isNotEmpty()) {
                                val curPos = UserAgent.findAorIndex(viewModel.selectedAor.value)
                                val newPos = when (curPos) {
                                    null -> 0
                                    0 -> uas.value.size - 1
                                    else -> curPos - 1
                                }
                                if (curPos != newPos) {
                                    val ua = uas.value[newPos]
                                    spinToAor(viewModel, ua.account.aor)
                                    showCall(ctx, viewModel, ua)
                                }
                            }
                        }
                    }
                ) { _, dragAmount ->
                    offset += dragAmount
                }
            }
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AccountSpinner(ctx, viewModel, navController)

        filteredCalls.forEach { call ->
            CallCard(ctx = ctx, viewModel = viewModel, call = call, dialerState = null)
        }

        // Only show the dialer if we are not in a transient state
        if (!dialingOrRinging && (filteredCalls.isEmpty() || conferenceCall))
            CallCard(ctx = ctx, viewModel = viewModel, call = null, dialerState = viewModel.dialerState)

        Indicator(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            isRefreshing = isRefreshing,
            state = refreshState,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountSpinner(ctx: Context, viewModel: ViewModel, navController: NavController) {

    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected: String by viewModel.selectedAor.collectAsState()

    if (uas.value.isEmpty())
        viewModel.updateSelectedAor("")
    else
        if (selected == "" || UserAgent.ofAor(selected) == null) {
            viewModel.updateSelectedAor(uas.value.first().account.aor)
        }

    showCall(ctx, viewModel, UserAgent.ofAor(selected))
    viewModel.triggerAccountUpdate()

    if (selected == "") {
        OutlinedButton(
            onClick = {
                navController.navigate("accounts")
            },
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .height(50.dp)
                .fillMaxWidth(),
            colors = ButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(text = "")
        }
    }
    else
        OutlinedButton(
            onClick = {
                expanded = !expanded
            },
            enabled = true,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .height(50.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            expanded = true
                        },
                        onLongPress = {
                            val ua = UserAgent.ofAor(selected)
                            if (ua != null) {
                                val acc = ua.account
                                if (Api.account_regint(acc.accp) > 0) {
                                    Api.account_set_regint(acc.accp, 0)
                                    Api.ua_unregister(ua.uap)
                                } else {
                                    Api.account_set_regint(
                                        acc.accp,
                                        acc.configuredRegInt
                                    )
                                    Api.ua_register(ua.uap)
                                }
                                acc.regint = Api.account_regint(acc.accp)
                                Account.saveAccounts()
                            }
                        }
                    )
                },
            colors = ButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(
                    uasStatus.value[selected] ?: R.drawable.circle_yellow
                ),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .clickable(onClick = {
                        navController.navigate("account/$selected/old")
                    })
            )
            Text(
                text = Account.ofAor(selected)?.text() ?: "",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { expanded = true },
                        onLongClick = {
                            val ua = UserAgent.ofAor(selected)
                            if (ua != null) {
                                val acc = ua.account
                                if (Api.account_regint(acc.accp) > 0) {
                                    Api.account_set_regint(acc.accp, 0)
                                    Api.ua_unregister(ua.uap)
                                } else {
                                    Api.account_set_regint(
                                        acc.accp,
                                        acc.configuredRegInt
                                    )
                                    Api.ua_register(ua.uap)
                                }
                                acc.regint = Api.account_regint(acc.accp)
                                Account.saveAccounts()
                            }
                        }
                    )
            )
            Icon(
                imageVector = if (expanded)
                    Icons.Filled.KeyboardArrowUp
                else
                    Icons.Filled.KeyboardArrowDown,
                contentDescription = null
            )
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                uas.value.forEachIndexed { index, ua ->
                    val acc = ua.account
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            viewModel.updateSelectedAor(acc.aor)
                            showCall(ctx, viewModel, ua)
                            viewModel.triggerAccountUpdate()
                        },
                        text = { Text(
                            text = acc.text(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ) },
                        leadingIcon = {
                            Icon(
                                imageVector = ImageVector.vectorResource(uasStatus.value[acc.aor]!!),
                                contentDescription = null,
                                tint = Color.Unspecified,
                            )
                        }
                    )
                    if (index < uas.value.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
}

@Composable
private fun CallUriRow(
    ctx: Context,
    viewModel: ViewModel,
    call: Call?,
    dialerState: ViewModel.DialerState?
) {

    val isDialer = dialerState != null

    val suggestions by remember { contactNames }
    var filteredSuggestions by remember { mutableStateOf<List<AnnotatedString>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()
    val isDialpadVisible by viewModel.isDialpadVisible.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = if (isDialer) dialerState.callUri.value else call!!.callUri.value,
                readOnly = if (isDialer) !dialerState.callUriEnabled.value else !call!!.callUriEnabled.value,
                singleLine = true,
                onValueChange = {
                    if (isDialer) {
                        if (it != dialerState.callUri.value) {
                            dialerState.callUri.value = it
                            if (it == "") {
                                dialerState.showCallButton.value = true
                                dialerState.showCallConferenceButton.value = true
                            }
                            val normalizedInput = Utils.unaccent(it)
                            filteredSuggestions = suggestions
                                .filter { suggestion ->
                                    it.length > 1 &&
                                            Utils.unaccent(suggestion)
                                                .contains(normalizedInput, ignoreCase = true)
                                }
                                .map { suggestion ->
                                    Utils.buildAnnotatedStringWithHighlight(suggestion, it)
                                }
                            dialerState.showSuggestions.value = it.length > 1
                        }
                    }
                },
                trailingIcon = {
                    if (isDialer && dialerState.callUriEnabled.value && dialerState.callUri.value.isNotEmpty())
                        Icon(Icons.Outlined.Clear,
                            contentDescription = null,
                            modifier = Modifier.clickable {
                                if (dialerState.showSuggestions.value)
                                    dialerState.showSuggestions.value = false
                                dialerState.callUri.value = ""
                                dialerState.showCallButton.value = true
                                dialerState.showCallConferenceButton.value = true
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (isDialer) {
                            val account = Account.ofAor(viewModel.selectedAor.value)
                            if (account != null && account.numericKeypad)
                                if (!isDialpadVisible)
                                    viewModel.toggleDialpadVisibility()
                        }
                    },
                label = {
                    Text(
                        text = if (isDialer)
                            dialerState.callUriLabel.value
                        else
                            call!!.callUriLabel.value,
                        fontSize = 18.sp
                    )
                },
                textStyle = TextStyle(fontSize = 18.sp),
                keyboardOptions = if (isDialpadVisible)
                    KeyboardOptions(keyboardType = KeyboardType.Phone)
                else
                    KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(8.dp))
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .animateContentSize()
            ) {
                if (isDialer && dialerState.showSuggestions.value && filteredSuggestions.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScrollbar(
                                    state = lazyListState,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                ),
                            horizontalAlignment = Alignment.Start,
                            state = lazyListState
                        ) {
                            items(
                                items = filteredSuggestions,
                                key = { suggestion -> suggestion.toString() }
                            ) { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            dialerState.callUri.value = suggestion.toString()
                                            dialerState.showSuggestions.value = false
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = suggestion,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (call != null && call.showCallTimer.value) {
            CallTimer(
                initialDurationSeconds = call.callDuration.toLong(),
                modifier = Modifier.padding(
                    start = 6.dp,
                    top = 6.dp,
                    end = if (call.securityIconTint.value != -1) 6.dp else 0.dp
                )
            )
        }
        if (call != null && call.securityIconTint.value != -1)
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        when (call.securityIconTint.value) {
                            R.color.colorTrafficRed -> {
                                alertTitle.value = ctx.getString(R.string.alert)
                                alertMessage.value = ctx.getString(R.string.call_not_secure)
                                showAlert.value = true
                            }
                            R.color.colorTrafficYellow -> {
                                alertTitle.value = ctx.getString(R.string.alert)
                                alertMessage.value = ctx.getString(R.string.peer_not_verified)
                                showAlert.value = true
                            }
                            R.color.colorTrafficGreen -> {
                                dialogTitle.value = ctx.getString(R.string.info)
                                dialogMessage.value = ctx.getString(R.string.call_is_secure)
                                positiveText.value = ctx.getString(R.string.unverify)
                                onPositiveClicked.value = {
                                    if (Api.cmd_exec("zrtp_unverify " + call.zid) != 0)
                                        Log.e(
                                            TAG,
                                            "Command 'zrtp_unverify ${call.zid}' failed"
                                        )
                                    else
                                        call.securityIconTint.value = R.color.colorTrafficYellow
                                }
                                negativeText.value = ctx.getString(R.string.cancel)
                                showDialog.value = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (call.securityIconTint.value == R.color.colorTrafficRed)
                        Icons.Filled.LockOpen
                    else
                        Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = colorResource(call.securityIconTint.value)
                )
            }
    }
}

@Composable
private fun CallTimer(
    initialDurationSeconds: Long,
    modifier: Modifier = Modifier
) {
    val startTime = remember(initialDurationSeconds) {
        SystemClock.elapsedRealtime() - (initialDurationSeconds * 1000L)
    }

    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(startTime) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val elapsedMillis = now - startTime
            val seconds = if (elapsedMillis > 0) elapsedMillis / 1000 else 0
            timeText = android.text.format.DateUtils.formatElapsedTime(seconds)
            delay(1000L)
        }
    }

    Text(
        text = timeText,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallRow(
    ctx: Context,
    viewModel: ViewModel,
    call: Call?,
    dialerState: ViewModel.DialerState?
) {

    val isDialer = dialerState != null
    val isDialpadVisible by viewModel.isDialpadVisible.collectAsState()

    Row( modifier = Modifier
        .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Absolute.SpaceBetween
    ) {
        if (isDialer) {
            if (dialerState.showCallButton.value)
                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        dialerState.showCallConferenceButton.value = false
                        dialerState.showSuggestions.value = false
                        callClick(ctx, viewModel, dialerState)
                    },
                    enabled = dialerState.callButtonsEnabled.value
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        modifier = Modifier.size(42.dp),
                        tint = colorResource(if (dialerState.callButtonsEnabled.value)
                            R.color.colorTrafficGreen
                        else
                            R.color.colorTrafficYellow),
                        contentDescription = null,
                    )
                }
            if (dialerState.showCallConferenceButton.value) {
                Spacer(modifier = Modifier.weight(1f, true))
                IconButton(
                    modifier = Modifier.size(48.dp),
                    enabled = dialerState.callButtonsEnabled.value,
                    onClick = {
                        dialerState.showCallButton.value = false
                        dialerState.showSuggestions.value = false
                        callClick(ctx, viewModel, dialerState)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddIcCall,
                        modifier = Modifier.size(42.dp),
                        tint = colorResource(
                            if (dialerState.callButtonsEnabled.value)
                                R.color.colorTrafficGreen
                            else
                                R.color.colorTrafficYellow
                        ),
                        contentDescription = null,
                    )
                }
            }
        }
        else {
            if (call!!.showCancelButton.value) {
                if (!call.conferenceCall)
                    Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        abandonAudioFocus(ctx)
                        Log.d(
                            TAG,
                            "AoR ${call.ua.account.aor} canceling call ${call.callp} with ${call.callUri.value}"
                        )
                        Api.ua_hangup(call.ua.uap, call.callp, 487, "Request Terminated")
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        modifier = Modifier.size(42.dp),
                        tint = colorResource(R.color.colorTrafficRed),
                        contentDescription = null,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            if (call.showHangupButton.value) {

                IconButton(
                    modifier = Modifier.size(48.dp),
                    enabled = !call.terminated.value,
                    onClick = {
                        call.terminated.value = true
                        abandonAudioFocus(ctx)
                        Log.d(TAG, "AoR ${call.ua.account.aor} hanging up call ${call.callp} with ${call.callUri.value}")
                        Api.ua_hangup(call.ua.uap, call.callp, 487, "Request Terminated")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        modifier = Modifier.size(42.dp),
                        tint = colorResource(R.color.colorTrafficRed),
                        contentDescription = null,
                    )
                }

                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        if (call.onhold) {
                            Log.d(
                                TAG,
                                "AoR ${call.ua.account.aor} resuming call ${call.callp} with ${call.callUri.value}"
                            )
                            call.resume()
                            call.onhold = false
                        } else {
                            Log.d(
                                TAG,
                                "AoR ${call.ua.account.aor} holding call ${call.callp} with ${call.callUri.value}"
                            )
                            call.hold()
                            call.onhold = true
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PauseCircle,
                        modifier = Modifier.size(42.dp),
                        tint = if (call.callOnHold.value)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.secondary,
                        contentDescription = null,
                    )
                }

                var showTransferDialog by remember { mutableStateOf(false) }

                if (!call.conferenceCall)
                    IconButton(
                        modifier = Modifier.size(48.dp),
                        enabled = call.transferButtonEnabled.value,
                        onClick = {
                            if (call.onHoldCall != null) {
                                if (!call.executeTransfer()) {
                                    alertTitle.value = ctx.getString(R.string.notice)
                                    alertMessage.value = ctx.getString(R.string.transfer_failed)
                                    showAlert.value = true
                                }
                            } else
                                showTransferDialog = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowCircleRight,
                            modifier = Modifier.size(42.dp),
                            tint = if (call.callTransfer.value)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.secondary,
                            contentDescription = null,
                        )
                    }

                if (showTransferDialog) {

                    val showDialog = remember { mutableStateOf(true) }
                    val blindChecked = remember { mutableStateOf(true) }

                    if (showDialog.value)
                        BasicAlertDialog(
                            onDismissRequest = {
                                viewModel.requestHideKeyboard()
                                showDialog.value = false
                                showTransferDialog = false
                            }
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(R.string.call_transfer),
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    var transferUri by remember { mutableStateOf("") }
                                    val suggestions by remember { contactNames }
                                    var filteredSuggestions by remember { mutableStateOf<List<AnnotatedString>>(emptyList()) }
                                    val focusRequester = remember { FocusRequester() }
                                    val lazyListState = rememberLazyListState()
                                    OutlinedTextField(
                                        value = transferUri,
                                        singleLine = true,
                                        onValueChange = {
                                            if (it != transferUri) {
                                                transferUri = it
                                                if (it.length > 1) {
                                                    val normalizedInput = Utils.unaccent(it)
                                                    filteredSuggestions =
                                                        suggestions.filter { suggestion ->
                                                            Utils.unaccent(suggestion)
                                                                .contains(normalizedInput, ignoreCase = true)
                                                        }
                                                            .map { suggestion ->
                                                                Utils.buildAnnotatedStringWithHighlight(suggestion, it)
                                                            }
                                                }
                                                call.showSuggestions.value = transferUri.length > 1
                                            }
                                        },
                                        trailingIcon = {
                                            if (transferUri.isNotEmpty())
                                                Icon(
                                                    Icons.Outlined.Clear,
                                                    contentDescription = null,
                                                    modifier = Modifier.clickable {
                                                        if (call.showSuggestions.value)
                                                            call.showSuggestions.value = false
                                                        else
                                                            transferUri = ""
                                                    },
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = 4.dp,
                                                end = 4.dp,
                                                top = 12.dp,
                                                bottom = 2.dp
                                            )
                                            .focusRequester(focusRequester),
                                        label = { Text(stringResource(R.string.transfer_destination)) },
                                        textStyle = TextStyle(fontSize = 18.sp),
                                        keyboardOptions = if (isDialpadVisible)
                                            KeyboardOptions(keyboardType = KeyboardType.Phone)
                                        else
                                            KeyboardOptions(keyboardType = KeyboardType.Text)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(8.dp, RoundedCornerShape(8.dp))
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .animateContentSize()
                                    ) {
                                        if (call.showSuggestions.value && filteredSuggestions.isNotEmpty()) {
                                            Box(modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 150.dp)) {
                                                LazyColumn(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .verticalScrollbar(
                                                            state = lazyListState,
                                                            color = MaterialTheme.colorScheme.outlineVariant
                                                        ),
                                                    horizontalAlignment = Alignment.Start,
                                                    state = lazyListState,
                                                ) {
                                                    items(
                                                        items = filteredSuggestions,
                                                        key = { suggestion -> suggestion.toString() }
                                                    ) { suggestion ->
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    transferUri = suggestion.toString()
                                                                    call.showSuggestions.value = false
                                                                }
                                                                .padding(12.dp)
                                                        ) {
                                                            Text(
                                                                text = suggestion,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                fontSize = 18.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (call.replaces())
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Start,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = stringResource(R.string.blind),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(8.dp),
                                                )
                                                Switch(
                                                    checked = blindChecked.value,
                                                    onCheckedChange = {
                                                        blindChecked.value = true
                                                    }
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = stringResource(R.string.attended),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(8.dp),
                                                )
                                                Switch(
                                                    checked = !blindChecked.value,
                                                    onCheckedChange = {
                                                        blindChecked.value = false
                                                    }
                                                )
                                            }
                                        }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                viewModel.requestHideKeyboard()
                                                showDialog.value = false
                                                showTransferDialog = false
                                            },
                                            modifier = Modifier.padding(end = 32.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.cancel),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                call.showSuggestions.value = false
                                                var uriText = transferUri.trim()
                                                if (uriText.isNotEmpty()) {
                                                    val uris = Contact.contactUris(uriText)
                                                    if (uris.size > 1) {
                                                        selectItems.value = uris
                                                        selectItemAction.value = { index ->
                                                            val uri = uris[index]
                                                            transfer(
                                                                ctx,
                                                                viewModel,
                                                                call.ua,
                                                                if (Utils.isTelNumber(uri)) "tel:$uri" else uri,
                                                                !blindChecked.value
                                                            )
                                                            showSelectItemDialog.value = false
                                                        }
                                                        showSelectItemDialog.value = true
                                                    }
                                                    else {
                                                        if (uris.size == 1) uriText = uris[0]
                                                        transfer(
                                                            ctx,
                                                            viewModel,
                                                            call.ua,
                                                            if (Utils.isTelNumber(uriText)) "tel:$uriText" else uriText,
                                                            !blindChecked.value
                                                        )
                                                    }
                                                    viewModel.requestHideKeyboard()
                                                    showDialog.value = false
                                                    showTransferDialog = false
                                                }
                                            },
                                            modifier = Modifier.padding(end = 16.dp),
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    if (blindChecked.value)
                                                        R.string.transfer
                                                    else
                                                        R.string.call
                                                ).uppercase(),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                }

                val focusRequester = remember { FocusRequester() }
                val shouldRequestFocus by call.focusDtmf
                val interactionSource = remember { MutableInteractionSource() }
                BasicTextField(
                    value = call.dtmfText.value,
                    onValueChange = { newText ->
                        if (newText.length > call.dtmfText.value.length) {
                            val char = newText.last()
                            if (char.isDigit() || char == '*' || char == '#') {
                                Log.d(TAG, "Got DTMF digit '$char'")
                                call.sendDigit(char)
                            }
                        }
                        call.dtmfText.value = newText
                    },
                    modifier = Modifier
                        .width(80.dp)
                        .focusRequester(focusRequester),
                    enabled = call.dtmfEnabled.value,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    interactionSource = interactionSource,
                    decorationBox = { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = call.dtmfText.value,
                            visualTransformation = VisualTransformation.None,
                            innerTextField = innerTextField,
                            singleLine = true,
                            enabled = call.dtmfEnabled.value,
                            interactionSource = interactionSource,
                            label = {
                                Text(
                                    stringResource(R.string.dtmf),
                                    style = TextStyle(fontSize = 12.sp)
                                )
                            },
                            contentPadding = PaddingValues(
                                start = 4.dp,
                                end = 4.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        )
                    }
                )
                LaunchedEffect(shouldRequestFocus) {
                    if (shouldRequestFocus) {
                        focusRequester.requestFocus()
                        call.focusDtmf.value = false
                    }
                }

                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        val stats = call.stats("audio")
                        if (stats.isNotEmpty() && call.startTime != null) {
                            val parts = stats.split(",") as java.util.ArrayList
                            if (parts[2] == "0/0") {
                                parts[2] = "?/?"
                                parts[3] = "?/?"
                                parts[4] = "?/?"
                            }
                            val codecs = call.audioCodecs()
                            val duration = call.duration()
                            val txCodec = codecs.split(',')[0].split("/")
                            val rxCodec = codecs.split(',')[1].split("/")
                            alertTitle.value = ctx.getString(R.string.call_info)
                            alertMessage.value =
                                "${String.format(ctx.getString(R.string.duration), duration)}\n" +
                                        "${ctx.getString(R.string.codecs)}: ${txCodec[0]} ch ${txCodec[2]}/${rxCodec[0]} ch ${rxCodec[2]}\n" +
                                        "${String.format(ctx.getString(R.string.rate), parts[0])}\n" +
                                        "${String.format(ctx.getString(R.string.average_rate), parts[1])}\n" +
                                        "${ctx.getString(R.string.packets)}: ${parts[2]}\n" +
                                        "${ctx.getString(R.string.lost)}: ${parts[3]}\n" +
                                        String.format(ctx.getString(R.string.jitter), parts[4])
                            showAlert.value = true
                        } else {
                            alertTitle.value = ctx.getString(R.string.call_info)
                            alertMessage.value = ctx.getString(R.string.call_info_not_available)
                            showAlert.value = true
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                        contentDescription = null,
                    )
                }
            }

            if (call.showAnswerRejectButtons.value) {

                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        answer(ctx, call)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        modifier = Modifier.size(42.dp),
                        tint = colorResource(R.color.colorTrafficGreen),
                        contentDescription = null,
                    )
                }

                Spacer(Modifier.weight(1f))

                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        reject(call)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        modifier = Modifier.size(42.dp),
                        tint = colorResource(R.color.colorTrafficRed),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnHoldNotice() {
    OutlinedButton(
        onClick = {},
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.padding(16.dp),
        shape = RoundedCornerShape(20)
    ) {
        Text(
            text = stringResource(R.string.call_is_on_hold),
            fontSize = 18.sp
        )
    }
}

private fun spinToAor(viewModel: ViewModel, aor: String) {
    if (aor != viewModel.selectedAor.value)
        viewModel.updateSelectedAor(aor)
    viewModel.triggerAccountUpdate()
}

private fun callClick(ctx: Context, viewModel: ViewModel, dialerState: ViewModel.DialerState?) {
    if (viewModel.selectedAor.value != "") {
        if (Utils.checkPermissions(ctx, arrayOf(RECORD_AUDIO))) {
            if (dialerState != null) {
                val uriText = dialerState.callUri.value.trim()
                if (uriText.isNotEmpty()) {
                    val uris = Contact.contactUris(uriText)
                    if (uris.isEmpty())
                        makeCall(ctx, viewModel, uriText, dialerState.showCallConferenceButton.value)
                    else if (uris.size == 1)
                        makeCall(ctx, viewModel, uris[0], dialerState.showCallConferenceButton.value)
                    else {
                        selectItems.value = uris
                        selectItemAction.value = { index ->
                            makeCall(ctx, viewModel, uris[index], dialerState.showCallConferenceButton.value)
                        }
                        showSelectItemDialog.value = true
                    }
                } else {
                    val ua = UserAgent.ofAor(viewModel.selectedAor.value)!!
                    val latestPeerUri = CallHistoryNew.aorLatestPeerUri(ua.account.aor)
                    if (latestPeerUri != null)
                        dialerState.callUri.value = Utils.friendlyUri(ctx, latestPeerUri, ua.account)
                }
            }
        }
        else
            Toast.makeText(ctx, R.string.no_calls, Toast.LENGTH_SHORT).show()
    }
}

private fun makeCall(ctx: Context, viewModel: ViewModel, uriText: String, conferenceCall: Boolean) {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val ua = UserAgent.ofAor(viewModel.selectedAor.value)!!
    val aor = ua.account.aor
    val peerUri = if (Utils.isTelNumber(uriText))
        "tel:$uriText"
    else
        uriText
    val uri = if (Utils.isTelUri(peerUri)) {
        if (ua.account.telProvider == "") {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = String.format(ctx.getString(R.string.no_telephony_provider), aor)
            showAlert.value = true
            return
        }
        Utils.telToSip(peerUri, ua.account)
    }
    else
        Utils.uriComplete(peerUri, aor)
    if (!Utils.checkUri(uri)) {
        alertTitle.value = ctx.getString(R.string.notice)
        alertMessage.value = String.format(ctx.getString(R.string.invalid_sip_or_tel_uri), uri)
        showAlert.value = true
    }
    else if (!BaresipService.requestAudioFocus(ctx))
        Toast.makeText(ctx, R.string.audio_focus_denied, Toast.LENGTH_SHORT).show()
    else if (Call.calls().any { it.ua.account.aor != ua.account.aor })
        Toast.makeText(ctx, R.string.call_already_active, Toast.LENGTH_SHORT).show()
    else {
        viewModel.dialerState.callButtonsEnabled.value = false
        if (Build.VERSION.SDK_INT < 31) {
            Log.d(TAG, "Setting audio mode to MODE_IN_COMMUNICATION")
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            runCall(ctx, viewModel, ua, uri, conferenceCall)
        } else {
            if (am.mode == AudioManager.MODE_IN_COMMUNICATION) {
                runCall(ctx, viewModel, ua, uri, conferenceCall)
            } else {
                audioModeChangedListener = AudioManager.OnModeChangedListener { mode ->
                    if (mode == AudioManager.MODE_IN_COMMUNICATION) {
                        Log.d(TAG, "Audio mode changed to MODE_IN_COMMUNICATION using " +
                                "device ${am.communicationDevice!!.type}")
                        if (audioModeChangedListener != null) {
                            am.removeOnModeChangedListener(audioModeChangedListener!!)
                            audioModeChangedListener = null
                        }
                        runCall(ctx, viewModel, ua, uri, conferenceCall)
                    } else {
                        Log.d(TAG, "Audio mode changed to mode ${am.mode} using " +
                                "device ${am.communicationDevice!!.type}")
                    }
                }
                am.addOnModeChangedListener(ctx.mainExecutor, audioModeChangedListener!!)
                Log.d(TAG, "Setting audio mode to MODE_IN_COMMUNICATION")
                am.mode = AudioManager.MODE_IN_COMMUNICATION
            }
        }
    }
}

private fun answer(ctx: Context, call: Call) {
    Log.d(TAG, "AoR ${call.ua.account.aor} answering call from ${call.callUri.value}")
    val intent = Intent(ctx, BaresipService::class.java)
    intent.action = "Call Answer"
    intent.putExtra("uap", call.ua.uap)
    intent.putExtra("callp", call.callp)
    ctx.startService(intent)
}

private fun reject(call: Call) {
    Log.d(TAG, "AoR ${call.ua.account.aor} rejecting call ${call.callp} from ${call.callUri.value}")
    call.rejected = true
    Api.ua_hangup(call.ua.uap, call.callp, 486, "Busy Here")
}

private fun runCall(ctx: Context, viewModel: ViewModel, ua: UserAgent, uri: String, conferenceCall: Boolean) {
    callRunnable = Runnable {
        callRunnable = null
        val newCall = call(ctx, viewModel, ua, uri, conferenceCall)
        if (newCall == null) {
            BaresipService.abandonAudioFocus(ctx)
            viewModel.dialerState.callButtonsEnabled.value = true
        }
    }
    callHandler.postDelayed(callRunnable!!, BaresipService.audioDelay)
}

private fun call(
    ctx: Context,
    viewModel: ViewModel,
    ua: UserAgent,
    uri: String,
    conferenceCall: Boolean,
    onHoldCall: Call? = null
): Call? {
    spinToAor(viewModel, ua.account.aor)
    if (conferenceCall && ua.calls().isEmpty())
        Api.module_load("mixminus")
    val callp = ua.callAlloc(0L, Api.VIDMODE_OFF)
    return if (callp != 0L) {
        Log.d(TAG, "Adding outgoing call ${ua.uap}/$callp/$uri")
        val call = Call(callp, ua, uri, "out", "outgoing")
        call.onHoldCall = onHoldCall
        call.conferenceCall = conferenceCall
        call.add()
        if (onHoldCall != null)
            onHoldCall.newCall = call
        if (call.connect(uri)) {
            showCall(ctx, viewModel, ua)
            call
        } else {
            Log.w(TAG, "call_connect $callp failed")
            if (onHoldCall != null)
                onHoldCall.newCall = null
            call.remove()
            call.destroy()
            showCall(ctx, viewModel, ua)
            null
        }
    } else {
        Log.w(TAG, "callAlloc for ${ua.uap} to $uri failed")
        if (conferenceCall && ua.calls().isEmpty())
            Api.module_unload("mixminus")
        null
    }
}

private fun transfer(ctx: Context, viewModel: ViewModel, ua: UserAgent, uriText: String, attended: Boolean) {
    val uri = if (Utils.isTelUri(uriText))
        Utils.telToSip(uriText, ua.account)
    else
        Utils.uriComplete(uriText, ua.account.aor)
    if (!Utils.checkUri(uri)) {
        alertTitle.value = ctx.getString(R.string.notice)
        alertMessage.value = String.format(ctx.getString(R.string.invalid_sip_or_tel_uri), uri)
        showAlert.value = true
    }
    else {
        val call = ua.currentCall()
        if (call != null) {
            if (attended) {
                if (call.hold()) {
                    call.referTo = uri
                    call(ctx, viewModel, ua, uri, false,call)
                }
            }
            else {
                if (!call.transfer(uri)) {
                    alertTitle.value = ctx.getString(R.string.notice)
                    alertMessage.value = ctx.getString(R.string.transfer_failed)
                    showAlert.value = true
                }
            }
            showCall(ctx, viewModel, ua)
        }
    }
}

private fun showCall(ctx: Context, viewModel: ViewModel, ua: UserAgent?, showCall: Call? = null) {
    if (ua == null)
        return
    val call = showCall ?: ua.currentCall()
    if (call == null) {
        pullToRefreshEnabled.value = true
        viewModel.dialerState.callUri.value = ua.account.resumeUri
        viewModel.dialerState.callUriLabel.value = ctx.getString(R.string.outgoing_call_to_dots)
        viewModel.dialerState.callUriEnabled.value = true
        viewModel.dialerState.showCallButton.value = true
        viewModel.dialerState.showCallConferenceButton.value = true
        viewModel.dialerState.callButtonsEnabled.value = true
        viewModel.dialerState.showSuggestions.value = false
        dialpadButtonEnabled.value = true
        if (BaresipService.isMicMuted) {
            BaresipService.isMicMuted = false
            viewModel.updateMicIcon(Icons.Filled.Mic)
        }
    } else {
        viewModel.dialerState.callUri.value = ""
        pullToRefreshEnabled.value = false
        call.callUriEnabled.value = false
        val isLandscape = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape || call.held || call.status.value != "connected") {
            call.focusDtmf.value = false
            call.dtmfEnabled.value = !call.held
            Handler(Looper.getMainLooper()).postDelayed({
                viewModel.requestHideKeyboard()
            }, 25)
        }
        else {
            call.dtmfEnabled.value = true
            call.focusDtmf.value = true
            viewModel.requestShowKeyboard()
        }
        when (call.status.value) {
            "outgoing", "transferring", "answered" -> {
                call.callUriLabel.value = if (call.status.value == "answered")
                    ctx.getString(R.string.incoming_call_from_dots)
                else
                    ctx.getString(R.string.outgoing_call_to_dots)
                call.callUri.value = Utils.friendlyUri(ctx, call.peerUri, ua.account)
                call.showCallTimer.value = false
                call.securityIconTint.value = -1
                call.showCallButton.value = false
                call.showCancelButton.value = call.status.value == "outgoing"
                call.showHangupButton.value = !call.showCancelButton.value
                call.showAnswerRejectButtons.value = false
                call.showOnHoldNotice.value = false
                dialpadButtonEnabled.value = false
            }
            "incoming" -> {
                call.showCallTimer.value = false
                call.securityIconTint.value = -1
                val uri = call.diverterUri()
                if (uri != "") {
                    call.callUriLabel.value = ctx.getString(R.string.diverted_by_dots)
                    call.callUri.value = Utils.friendlyUri(ctx, uri, ua.account)
                }
                else {
                    call.callUriLabel.value = ctx.getString(R.string.incoming_call_from_dots)
                    call.callUri.value = Utils.friendlyUri(ctx, call.peerUri, ua.account)
                }
                call.showCallButton.value = false
                call.showCancelButton.value = false
                call.showHangupButton.value = false
                call.showAnswerRejectButtons.value = true
                call.showOnHoldNotice.value = false
                dialpadButtonEnabled.value = false
            }
            "connected" -> {
                if (call.referTo != "") {
                    call.callUriLabel.value = ctx.getString(R.string.outgoing_call_to_dots)
                    call.callUri.value = Utils.friendlyUri(ctx, call.referTo, ua.account)
                    call.transferButtonEnabled.value = false
                } else {
                    if (call.dir == "out") {
                        call.callUriLabel.value = ctx.getString(R.string.outgoing_call_to_dots)
                        call.callUri.value = Utils.friendlyUri(ctx, call.peerUri, ua.account)
                    } else {
                        call.callUriLabel.value = ctx.getString(R.string.incoming_call_from_dots)
                        call.callUri.value = Utils.friendlyUri(ctx, call.peerUri, ua.account)
                    }
                    call.transferButtonEnabled.value = true
                }
                call.callTransfer.value = call.onHoldCall != null
                call.callDuration = call.duration()
                call.showCallTimer.value = true
                if (ua.account.mediaEnc == "")
                    call.securityIconTint.value = -1
                else
                    call.securityIconTint.value = call.security
                call.showCallButton.value = false
                call.showCancelButton.value = false
                call.showHangupButton.value = true
                call.showAnswerRejectButtons.value = false
                call.callOnHold.value = call.onhold
                Handler(Looper.getMainLooper()).postDelayed({
                    call.showOnHoldNotice.value = call.held
                }, 100)
            }
        }
    }
}

fun handleServiceEvent(ctx: Context, viewModel: ViewModel, event: String, params: ArrayList<Any>) {

    fun handleNextEvent(logMessage: String? = null) {
        if (logMessage != null)
            Log.w(TAG, logMessage)
        if (BaresipService.serviceEvents.isNotEmpty()) {
            val first = BaresipService.serviceEvents.removeAt(0)
            handleServiceEvent(ctx, viewModel, first.event, first.params)
        }
    }

    if (event == "started") {
        val uriString = params[0] as String
        Log.d(TAG, "Handling service event 'started' with URI '$uriString'")
        if (uriString != "")
            callAction(ctx, viewModel, uriString.toUri(), "dial")
        else {
            if (viewModel.selectedAor.value == "" && uas.value.isNotEmpty())
                viewModel.updateSelectedAor(uas.value.first().account.aor)
        }
        if (Preferences(ctx).displayTheme != AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.setDefaultNightMode(Preferences(ctx).displayTheme)
        }
        handleNextEvent()
        return
    }

    val uap = params[0] as Long
    val ua = UserAgent.ofUap(uap)
    if (ua == null) {
        handleNextEvent("handleServiceEvent '$event' did not find ua $uap")
        return
    }

    val ev = event.split(",")
    Log.d(TAG, "Handling service event '${ev[0]}' for $uap")
    val acc = ua.account
    val aor = ua.account.aor

    when (ev[0]) {
        "call rejected" -> {
            if (aor == viewModel.selectedAor.value)
                viewModel.triggerAccountUpdate()
        }
        "call incoming", "call outgoing" -> {
            val callp = params[1] as Long
            if (!BaresipService.isMainVisible)
                viewModel.navigateToHome()
            spinToAor(viewModel, aor)
            showCall(ctx, viewModel, ua, Call.ofCallp(callp))
        }
        "call answered" -> {
            if (!BaresipService.isMainVisible)
                viewModel.navigateToHome()
            spinToAor(viewModel, aor)
            val callp = params[1] as Long
            showCall(ctx, viewModel, ua, Call.ofCallp(callp))
        }
        "call redirect" -> {
            val redirectUri = ev[1]
            val target = Utils.friendlyUri(ctx, redirectUri, acc)
            if (acc.autoRedirect) {
                redirect(ctx, viewModel, ua, target)
                Toast.makeText(ctx,
                    String.format(ctx.getString(R.string.redirect_notice), target),
                    Toast.LENGTH_SHORT
                ).show()
            }
            else {
                dialogTitle.value = ctx.getString(R.string.redirect_request)
                dialogMessage.value = String.format(ctx.getString(R.string.redirect_request_query), target)
                positiveText.value = ctx.getString(R.string.yes)
                onPositiveClicked.value = {
                    redirect(ctx, viewModel, ua, target)
                }
                negativeText.value = ctx.getString(R.string.no)
                onNegativeClicked.value = {}
                showDialog.value = true
            }
            showCall(ctx, viewModel, ua)
        }
        "call established" -> {
            if (aor == viewModel.selectedAor.value) {
                viewModel.dialerState.callButtonsEnabled.value = true // Re-enable dialer
                val callp = params[1] as Long
                val call = Call.ofCallp(callp)
                if (call != null) {
                    call.dtmfText.value = ""
                    if (call.conferenceCall)
                        Api.cmd_exec("conference")
                }
                showCall(ctx, viewModel, ua, call)
            }
        }
        "call update" -> {
            showCall(ctx, viewModel, ua)
        }
        "call verify" -> {
            val callp = params[1] as Long
            val call = Call.ofCallp(callp)
            if (call == null) {
                handleNextEvent("Call $callp to be verified is not found")
                return
            }
            dialogTitle.value = ctx.getString(R.string.verify)
            dialogMessage.value = String.format(ctx.getString(R.string.verify_sas), ev[1])
            positiveText.value = ctx.getString(R.string.yes)
            onPositiveClicked.value = {
                call.security = if (Api.cmd_exec("zrtp_verify ${ev[2]}") != 0) {
                    Log.e(TAG, "Command 'zrtp_verify ${ev[2]}' failed")
                    R.color.colorTrafficYellow
                } else {
                    R.color.colorTrafficGreen
                }
                call.zid = ev[2]
                if (aor == viewModel.selectedAor.value)
                    call.securityIconTint.value = call.security
            }
            negativeText.value = ctx.getString(R.string.no)
            onNegativeClicked.value = {
                call.security = R.color.colorTrafficYellow
                call.zid = ev[2]
                if (aor == viewModel.selectedAor.value)
                    call.securityIconTint.value = R.color.colorTrafficYellow
                onNegativeClicked.value = {}
            }
            showDialog.value = true
        }
        "call verified", "call secure" -> {
            val callp = params[1] as Long
            val call = Call.ofCallp(callp)
            if (call == null) {
                handleNextEvent("Call $callp that is verified is not found")
                return
            }
            if (aor == viewModel.selectedAor.value)
                call.securityIconTint.value = call.security
        }
        "call transfer", "transfer show" -> {
            if (!BaresipService.isMainVisible)
                viewModel.navigateToHome()
            val callp = params[1] as Long
            val call = Call.ofCallp(callp)
            val target = Utils.friendlyUri(ctx, ev[1], acc)
            dialogTitle.value = if (call != null)
                ctx.getString(R.string.transfer_request)
            else
                ctx.getString(R.string.call_request)
            dialogMessage.value = if (call != null)
                String.format(ctx.getString(R.string.transfer_request_query), target)
            else
                String.format(ctx.getString(R.string.call_request_query), target)
            positiveText.value = ctx.getString(R.string.yes)
            onPositiveClicked.value = {
                if (call in Call.calls())
                    acceptTransfer(ctx, viewModel, ua, call!!, ev[1])
                else
                    makeCall(ctx, viewModel, ev[1], false)
            }
            negativeText.value = ctx.getString(R.string.no)
            onNegativeClicked.value = {
                if (call in Call.calls())
                    call!!.notifySipfrag(603, "Decline")
                onNegativeClicked.value = {}
            }
            showDialog.value = true
        }
        "transfer accept" -> {
            val callp = params[1] as Long
            val call = Call.ofCallp(callp)
            if (call in Call.calls())
                Api.ua_hangup(uap, callp, 487, "Request Terminated")
            call(ctx, viewModel, ua, ev[1], false)
            showCall(ctx, viewModel, ua)
        }
        "transfer failed" -> {
            showCall(ctx, viewModel, ua)
        }
        "call closed" -> {
            if (aor == viewModel.selectedAor.value) {
                viewModel.dialerState.callButtonsEnabled.value = true
                ua.account.resumeUri = ""
                showCall(ctx, viewModel, ua)
                if (acc.missedCalls)
                    viewModel.triggerAccountUpdate()
            }
        }
        "message", "message show", "message reply" -> {
            Handler(Looper.getMainLooper()).postDelayed({
                viewModel.onNewMessageReceived(aor, params[1] as String)
            }, 200)
        }
        "mwi notify" -> {
            val lines = ev[1].split("\n")
            for (line in lines) {
                if (line.startsWith("Voice-Message:")) {
                    val counts = (line.split(" ")[1]).split("/")
                    acc.vmNew = counts[0].toInt()
                    acc.vmOld = counts[1].toInt()
                    break
                }
            }
            if (aor == viewModel.selectedAor.value)
                viewModel.triggerAccountUpdate()
        }
        else -> Log.e(TAG, "Unknown event '${ev[0]}'")
    }

    viewModel.updateCalls(Call.calls().toList())
    handleNextEvent()
}

fun handleIntent(ctx: Context, viewModel: ViewModel, intent: Intent, action: String) {
    Log.d(TAG, "Handling intent '$action'")
    val ev = action.split(",")
    when (ev[0]) {
        "call", "dial" -> {
            if (Call.inCall()) {
                Toast.makeText(ctx, ctx.getString(R.string.call_already_active),
                    Toast.LENGTH_SHORT).show()
                return
            }
            val uap = intent.getLongExtra("uap", 0L)
            val ua = UserAgent.ofUap(uap)
            if (ua == null) {
                Log.w(TAG, "handleIntent 'call' did not find ua $uap")
                return
            }
            viewModel.dialerState.callUri.value = intent.getStringExtra("peer")!!
            spinToAor(viewModel, ua.account.aor)
            if (ev[0] == "call") {
                viewModel.dialerState.showCallConferenceButton.value = false
                callClick(ctx, viewModel, viewModel.dialerState)
            }
        }
        "call show", "call answer" -> {
            val callp = intent.getLongExtra("callp", 0L)
            val call = Call.ofCallp(callp)
            if (call == null) {
                Log.w(TAG, "handleIntent '$action' did not find call $callp")
                return
            }
            val ua = call.ua
            spinToAor(viewModel, ua.account.aor)
            if (ev[0] == "call answer")
                answer(ctx, call)
            else
                BaresipService.postServiceEvent(ServiceEvent(
                    "call incoming",
                    arrayListOf(call.ua.uap, callp),
                    System.nanoTime())
                )
        }
        "call missed" -> {
            val uap = intent.getLongExtra("uap", 0L)
            val ua = UserAgent.ofUap(uap)
            if (ua == null) {
                Log.w(TAG, "handleIntent did not find ua $uap")
                return
            }
            spinToAor(viewModel, ua.account.aor)
            viewModel.navigateToCalls(ua.account.aor)
        }
        "call transfer", "transfer show", "transfer accept" -> {
            val callp = intent.getLongExtra("callp", 0L)
            val call = Call.ofCallp(callp)
            if (call == null) {
                Log.w(TAG, "handleIntent '$action' did not find call $callp")
                // moveTaskToBack(true)
                return
            }
            val uri = if (ev[0] == "call transfer")
                ev[1]
            else
                intent.getStringExtra("uri")!!
            BaresipService.postServiceEvent(ServiceEvent(
                ev[0] + "," + uri,
                arrayListOf(call.ua.uap, callp),
                System.nanoTime())
            )
        }
        "message", "message show", "message reply" -> {
            val uap = intent.getLongExtra("uap", 0L)
            val ua = UserAgent.ofUap(uap)
            if (ua == null) {
                Log.w(TAG, "handleIntent did not find ua $uap")
                return
            }
            spinToAor(viewModel, ua.account.aor)
            BaresipService.postServiceEvent(ServiceEvent(
                ev[0],
                arrayListOf(uap, intent.getStringExtra("peer")!!),
                System.nanoTime())
            )
        }
    }
}

fun handleDialog(ctx: Context, title: String, message: String, action: () -> Unit = {}) {
    dialogTitle.value = title
    dialogMessage.value = message
    positiveText.value = ctx.getString(R.string.ok)
    onPositiveClicked.value = { action() }
    negativeText.value = ""
    showDialog.value = true
}

fun callAction(ctx: Context, viewModel: ViewModel, uri: Uri?, action: String) {
    if (Call.inCall() || uas.value.isEmpty())
        return
    Log.d(TAG, "Action $action to $uri")
    if (uri != null) {
        var uriStr: String
        var uap: Long
        when (uri.scheme) {
            "sip" -> {
                uriStr = Utils.uriUnescape(uri.toString())
                var ua = UserAgent.ofDomain(Utils.uriHostPart(uriStr))
                if (ua == null && uas.value.isNotEmpty())
                    ua = uas.value[0]
                if (ua == null) {
                    Log.w(TAG, "No accounts for '$uriStr'")
                    return
                }
                uap = ua.uap
            }
            "tel" -> {
                uriStr = uri.toString().replace("%2B", "+").replace("%20", "")
                    .filterNot { setOf('-', ' ', '(', ')').contains(it) }
                var account: Account? = null
                for (a in Account.accounts())
                    if (a.telProvider != "") {
                        account = a
                        break
                    }
                if (account == null) {
                    Log.w(TAG, "No telephony providers for '$uriStr'")
                    return
                }
                uap = UserAgent.ofAor(account.aor)!!.uap
            }
            else -> {
                Log.w(TAG, "Unsupported URI scheme ${uri.scheme}")
                return
            }
        }
        val intent = Intent(ctx, MainActivity::class.java)
        intent.putExtra("uap", uap)
        intent.putExtra("peer", uriStr)
        handleIntent(ctx, viewModel, intent, action)
    }
}

private fun redirect(ctx: Context, viewModel: ViewModel, ua: UserAgent, redirectUri: String) {
    if (ua.account.aor != viewModel.selectedAor.value)
        spinToAor(viewModel, ua.account.aor)
    viewModel.dialerState.callUri.value = redirectUri
    callClick(ctx, viewModel, viewModel.dialerState)
}

private fun acceptTransfer(ctx: Context, viewModel: ViewModel, ua: UserAgent, call: Call, uri: String) {
    val newCallp = ua.callAlloc(call.callp, Api.VIDMODE_OFF)
    if (newCallp != 0L) {
        Log.d(TAG, "Adding outgoing call ${ua.uap}/$newCallp/$uri")
        val newCall = Call(newCallp, ua, uri, "out", "transferring")
        newCall.add()
        if (newCall.connect(uri)) {
            if (ua.account.aor != viewModel.selectedAor.value)
                spinToAor(viewModel, ua.account.aor)
            showCall(ctx, viewModel, ua)
        } else {
            Log.w(TAG, "call_connect $newCallp failed")
            call.notifySipfrag(500, "Call Error")
        }
    } else {
        Log.w(TAG, "callAlloc for ua ${ua.uap} call ${call.callp} transfer failed")
        call.notifySipfrag(500, "Call Error")
    }
}

private fun backup(ctx: Context, password: String) {
    val files = arrayListOf("accounts", "config", "contacts", "call_history",
        "messages", "uuid", "gzrtp.zid", "cert.pem", "ca_cert", "ca_certs.crt")
    File(BaresipService.filesPath).walk().forEach {
        if (it.name.endsWith(".png"))
            files.add(it.name)
    }
    val zipFile = ctx.getString(R.string.app_name) + ".zip"
    val zipFilePath = BaresipService.filesPath + "/$zipFile"
    if (!Utils.zip(files, zipFile)) {
        Log.w(TAG, "Failed to write zip file '$zipFile'")
        alertTitle.value = ctx.getString(R.string.error)
        alertMessage.value = String.format(ctx.getString(R.string.backup_failed),
            Utils.fileNameOfUri(ctx, downloadsOutputUri!!))
        showAlert.value = true
        downloadsOutputUri = null
        return
    }
    val content = Utils.getFileContents(zipFilePath)
    if (content == null) {
        Log.w(TAG, "Failed to read zip file '$zipFile'")
        alertTitle.value = ctx.getString(R.string.error)
        alertMessage.value = String.format(ctx.getString(R.string.backup_failed),
            Utils.fileNameOfUri(ctx, downloadsOutputUri!!))
        showAlert.value = true
        downloadsOutputUri = null
        return
    }
    if (!Utils.encryptToUri(ctx, downloadsOutputUri!!, content, password)) {
        alertTitle.value = ctx.getString(R.string.error)
        alertMessage.value = String.format(ctx.getString(R.string.backup_failed),
            Utils.fileNameOfUri(ctx, downloadsOutputUri!!))
        showAlert.value = true
        downloadsOutputUri = null
        return
    }
    alertTitle.value = ctx.getString(R.string.info)
    alertMessage.value = String.format(ctx.getString(R.string.backed_up),
        Utils.fileNameOfUri(ctx, downloadsOutputUri!!))
    showAlert.value = true
    Utils.deleteFile(File(zipFilePath))
    downloadsOutputUri = null
}

private fun restore(ctx: Context, password: String, onRestartApp: () -> Unit) {
    val zipFile = ctx.getString(R.string.app_name) + ".zip"
    val zipFilePath = BaresipService.filesPath + "/$zipFile"
    val zipData = Utils.decryptFromUri(ctx, downloadsInputUri!!, password)
    if (zipData == null) {
        alertTitle.value = ctx.getString(R.string.error)
        alertMessage.value = String.format(ctx.getString(R.string.restore_failed),
            Utils.fileNameOfUri(ctx, downloadsOutputUri!!))
        showAlert.value = true
        downloadsOutputUri = null
        return
    }
    if (!Utils.putFileContents(zipFilePath, zipData)) {
        Log.w(TAG, "Failed to write zip file '$zipFile'")
        alertTitle.value = ctx.getString(R.string.error)
        alertMessage.value = String.format(ctx.getString(R.string.restore_failed),
            Utils.fileNameOfUri(ctx, downloadsOutputUri!!))
        showAlert.value = true
        downloadsOutputUri = null
        return
    }
    if (!Utils.unZip(zipFilePath)) {
        Log.w(TAG, "Failed to unzip file '$zipFile'")
        alertTitle.value = ctx.getString(R.string.error)
        alertMessage.value = String.format(
            ctx.getString(R.string.restore_unzip_failed),
            "baresip",
            BuildConfig.VERSION_NAME
        )
        showAlert.value = true
        downloadsOutputUri = null
        return
    }
    Utils.deleteFile(File(zipFilePath))

    File("${BaresipService.filesPath}/recordings").walk().forEach {
        if (it.name.startsWith("dump"))
            Utils.deleteFile(it)
    }

    Utils.createEmptyFile(BaresipService.filesPath + "/restored")

    dialogTitle.value = ctx.getString(R.string.info)
    dialogMessage.value = ctx.getString(R.string.restored)
    positiveText.value = ctx.getString(R.string.restart)
    onPositiveClicked.value = {
        onRestartApp()
        showDialog.value = false
    }
    negativeText.value = ctx.getString(R.string.cancel)
    onNegativeClicked.value = {
        showDialog.value = false
    }
    showDialog.value = true

    downloadsOutputUri = null
}

private fun abandonAudioFocus(ctx: Context) {
    if (Build.VERSION.SDK_INT < 31) {
        if (callRunnable != null) {
            callHandler.removeCallbacks(callRunnable!!)
            callRunnable = null
            BaresipService.abandonAudioFocus(ctx)
        }
    } else {
        if (audioModeChangedListener != null) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.removeOnModeChangedListener(audioModeChangedListener!!)
            audioModeChangedListener = null
            BaresipService.abandonAudioFocus(ctx)
        }
    }
}

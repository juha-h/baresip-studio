package com.tutpro.baresip.plus

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RelativeLayout
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.material.icons.filled.VideoCall
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tutpro.baresip.plus.BaresipService.Companion.contactNames
import com.tutpro.baresip.plus.BaresipService.Companion.uas
import com.tutpro.baresip.plus.BaresipService.Companion.uasStatus
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.DropdownMenu
import com.tutpro.baresip.plus.CustomElements.PasswordDialog
import com.tutpro.baresip.plus.CustomElements.SelectableAlertDialog
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import kotlin.concurrent.schedule

private val showVideoLayout = mutableStateOf(false)

private val dialpadButtonEnabled = mutableStateOf(true)
private var pullToRefreshEnabled = mutableStateOf(true)

private var downloadsInputUri: Uri? = null
private var downloadsOutputUri: Uri? = null

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
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "Resumed to MainScreen")
                    BaresipService.isMainVisible = true
                    val incomingCall = Call.call("incoming")
                    if (incomingCall != null)
                        spinToAor(viewModel, incomingCall.ua.account.aor)
                    else {
                        if (uas.value.isNotEmpty()) {
                            if (viewModel.selectedAor.value == "") {
                                if (Call.inCall())
                                    spinToAor(viewModel, Call.calls()[0].ua.account.aor)
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

    if (showPasswordDialog.value)
        PasswordDialog(
            ctx = ctx,
            showPasswordDialog = showPasswordDialog,
            password = password,
            keyboardController = keyboardController,
            title = passwordTitle.value,
            okAction = {
                if (password.value != "") {
                    if (passwordTitle.value == ctx.getString(R.string.encrypt_password))
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
                Utils.paramValue(params, "auth_pass") == ""
            ) {
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
        } else
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

    if (showVideoLayout.value)
        VideoLayout(ctx = ctx, viewModel= viewModel,
            onCloseVideo = {
                showVideoLayout.value = false
                videoIcon.value = Video.ON
            }
        )
    else
        DefaultLayout(ctx, navController, viewModel, onRestartClick, onQuitClick)
}

@Composable
fun DefaultLayout(ctx: Context, navController: NavController, viewModel: ViewModel,
                  onRestartClick: () -> Unit, onQuitClick: () -> Unit) {

    val backupRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.also { uri ->
                downloadsOutputUri = uri
                passwordTitle.value = ctx.getString(R.string.encrypt_password)
                showPasswordDialog.value = true
            }
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(viewModel.showKeyboard.value) {
        if (viewModel.showKeyboard.value > 0)
            keyboardController?.show()
    }

    LaunchedEffect(viewModel.hideKeyboard.value) {
        if (viewModel.hideKeyboard.value > 0)
            keyboardController?.hide()
    }

    val configuration = LocalConfiguration.current
    val ua = uas.value.find { it.account.aor == viewModel.selectedAor.value }
    val call = ua?.currentCall()

    LaunchedEffect(key1 = call?.status, key2 = configuration.orientation) {
        val isConnected = call != null && call.status == "connected" && !call.held
        if (isConnected) {
            if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                focusDtmf.value = true
                keyboardController?.show()
            }
            else
                keyboardController?.hide()
        }
    }

    fun launchBackupRequest() {
        if (Build.VERSION.SDK_INT < 29) {
            if (!Utils.checkPermissions(ctx, arrayOf(WRITE_EXTERNAL_STORAGE))) {
                alertTitle.value = ctx.getString(R.string.notice)
                alertMessage.value = ctx.getString(R.string.no_backup)
                showAlert.value = true
            } else {
                val path = Utils.downloadsPath("baresip.bs")
                downloadsOutputUri = File(path).toUri()
                passwordTitle.value = ctx.getString(R.string.encrypt_password)
                showPasswordDialog.value = true
            }
        } else {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(
                    Intent.EXTRA_TITLE,
                    "baresip+_" + SimpleDateFormat(
                        "yyyy_MM_dd_HH_mm_ss",
                        Locale.getDefault()
                    ).format(Date())
                )
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                )
            }
            backupRequestLauncher.launch(intent)
        }
    }

    val restoreRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.also { uri ->
                downloadsInputUri = uri
                passwordTitle.value = ctx.getString(R.string.decrypt_password)
                showPasswordDialog.value = true
            }
        }
    }

    fun launchRestoreRequest() {
        if (Build.VERSION.SDK_INT < 29) {
            if (!Utils.checkPermissions(ctx, arrayOf(READ_EXTERNAL_STORAGE))) {
                alertTitle.value = ctx.getString(R.string.notice)
                alertMessage.value = ctx.getString(R.string.no_restore)
                showAlert.value = true
            } else {
                val path = Utils.downloadsPath("baresip.bs")
                downloadsInputUri = File(path).toUri()
                passwordTitle.value = ctx.getString(R.string.decrypt_password)
                showPasswordDialog.value = true
            }
        } else {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                )
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
                    "baresip+_logcat_" + SimpleDateFormat(
                        "yyyy_MM_dd_HH_mm_ss",
                        Locale.getDefault()
                    ).format(Date())
                )
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                )
            }
            logcatRequestLauncher.launch(intent)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(
                        WindowInsets.statusBars
                            .union(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                            .asPaddingValues()
                    )
                    //.padding(WindowInsets.systemBars.asPaddingValues())
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
        content = { innerPadding ->
            MainContent(navController, viewModel, innerPadding)
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

    val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager

    val recOffImage = Icons.Filled.VoiceOverOff
    val recOnImage = Icons.Filled.RecordVoiceOver
    var recImage by remember { mutableStateOf(recOffImage) }
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
                text = stringResource(R.string.baresip) + "+",
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

            Icon(
                imageVector = recImage,
                modifier = Modifier
                    .size(40.dp)
                    .combinedClickable(
                        onClick = {
                            if (Call.call("connected") == null) {
                                BaresipService.isRecOn = !BaresipService.isRecOn
                                recImage = if (BaresipService.isRecOn) {
                                    Api.module_load("sndfile")
                                    recOnImage
                                }
                                else {
                                    Api.module_unload("sndfile")
                                    recOffImage
                                }
                            } else
                                Toast.makeText(ctx, R.string.rec_in_call, Toast.LENGTH_SHORT)
                                    .show()
                        },
                        onLongClick = {
                            alertTitle.value = ctx.getString(R.string.call_recording_title)
                            alertMessage.value = ctx.getString(R.string.call_recording_tip)
                            showAlert.value = true
                        }
                    ),
                tint = if (BaresipService.isRecOn)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onPrimary,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(22.dp))

            Icon(
                imageVector = currentMicIcon,
                modifier = Modifier
                    .size(40.dp)
                    .combinedClickable(
                        onClick = {
                            if (Call.call("connected") != null) {
                                BaresipService.isMicMuted = !BaresipService.isMicMuted
                                if (BaresipService.isMicMuted) {
                                    viewModel.updateMicIcon(Icons.Filled.MicOff)
                                    Api.calls_mute(true)
                                }
                                else {
                                    viewModel.updateMicIcon(Icons.Filled.Mic)
                                    Api.calls_mute(false)
                                }
                            }
                        },
                        onLongClick = {
                            alertTitle.value = ctx.getString(R.string.microphone_title)
                            alertMessage.value = ctx.getString(R.string.microphone_tip)
                            showAlert.value = true
                        },
                    ),
                tint = if (BaresipService.isMicMuted)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onPrimary,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(16.dp))

            Icon(
                imageVector = Icons.Filled.SpeakerPhone,
                modifier = Modifier
                    .size(40.dp)
                    .combinedClickable(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 31)
                                Log.d(TAG, "Toggling speakerphone when dev/mode is " +
                                        "${am.communicationDevice!!.type}/${am.mode}"
                                )
                            isSpeakerOn.value = !Utils.isSpeakerPhoneOn(am)
                            Utils.toggleSpeakerPhone(ContextCompat.getMainExecutor(ctx), am)
                        },
                        onLongClick = {
                            alertTitle.value = ctx.getString(R.string.speakerphone_title)
                            alertMessage.value = ctx.getString(R.string.speakerphone_tip)
                            showAlert.value = true
                        },
                    ),
                tint = if (isSpeakerOn.value)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onPrimary,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(8.dp))

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
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (showVmIcon)
            IconButton(
                // Disable the button if no account is selected
                enabled = aor.isNotEmpty(),
                onClick = {
                    // No need for an 'if' check here anymore
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
            modifier = Modifier.weight(1f).size(buttonSize),
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

enum class Video { NONE, ON, PENDING, OFF }

private val callUri = mutableStateOf("")
private val callUriEnabled = mutableStateOf(true)
private val callUriLabel = mutableStateOf("")
private var securityIconTint = mutableIntStateOf(-1)
private val showCallTimer = mutableStateOf(false)
private var callDuration = 0
private val showSuggestions = mutableStateOf(false)
private val showCallButton = mutableStateOf(true)
private val callButtonEnabled = mutableStateOf(true)
private val showCallVideoButton = mutableStateOf(true)
private val callVideoButtonEnabled = mutableStateOf(true)
private val showCancelButton = mutableStateOf(false)
private var videoIcon = mutableStateOf(Video.NONE)
private val showAnswerRejectButtons = mutableStateOf(false)
private val showHangupButton = mutableStateOf(false)
private val showOnHoldNotice = mutableStateOf(false)
private var callOnHold = mutableStateOf(false)
private val transferButtonEnabled = mutableStateOf(false)
private val callTransfer = mutableStateOf(false)
private var dtmfText = mutableStateOf("")
private val dtmfEnabled = mutableStateOf(false)
private val focusDtmf = mutableStateOf(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(navController: NavController, viewModel: ViewModel, innerPadding: PaddingValues) {

    var isRefreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState()
    var offset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 200
    val ctx = LocalContext.current

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(1000)
            isRefreshing = false
        }
    }

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
            .padding(innerPadding)
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
                        }
                        else if (offset > swipeThreshold) {
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
        CallUriRow(ctx, viewModel)
        CallRow(ctx, viewModel)
        if (showOnHoldNotice.value)
            OnHoldNotice()
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
                                }
                                else {
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
                                }
                                else {
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                }
            }
        }
}

@Composable
private fun CallUriRow(ctx: Context, viewModel: ViewModel) {

    val suggestions by remember { contactNames }
    var filteredSuggestions by remember { mutableStateOf(suggestions) }
    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()
    val isDialpadVisible by viewModel.isDialpadVisible.collectAsState()

    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = callUri.value,
                readOnly = !callUriEnabled.value,
                singleLine = true,
                onValueChange = {
                    if (it != callUri.value) {
                        callUri.value = it
                        filteredSuggestions = suggestions.filter { suggestion ->
                            it.length > 2 && suggestion.startsWith(it, ignoreCase = true)
                        }
                        showSuggestions.value = it.length > 2
                    }
                },
                trailingIcon = {
                    if (callUriEnabled.value && callUri.value.isNotEmpty())
                        Icon(Icons.Outlined.Clear,
                            contentDescription = null,
                            modifier = Modifier.clickable {
                                if (showSuggestions.value)
                                    showSuggestions.value = false
                                else
                                    callUri.value = ""
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        val account = Account.ofAor(viewModel.selectedAor.value)
                        if (account != null && account.numericKeypad)
                            if (!isDialpadVisible)
                                viewModel.toggleDialpadVisibility()
                    },
                label = { Text(text = callUriLabel.value, fontSize = 18.sp) },
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
                if (showSuggestions.value && filteredSuggestions.isNotEmpty()) {
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
                            state = lazyListState
                        ) {
                            items(
                                items = filteredSuggestions,
                                key = { suggestion -> suggestion }
                            ) { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            callUri.value = suggestion
                                            showSuggestions.value = false
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
        if (showCallTimer.value) {
            val textColor = MaterialTheme.colorScheme.onBackground.toArgb()
            val chronometerInstance = remember { mutableStateOf<Chronometer?>(null) }
            AndroidView(
                factory = { context ->
                    Chronometer(context).apply {
                        textSize = 16F
                        setTextColor(textColor)
                        base = SystemClock.elapsedRealtime() - (callDuration * 1000L)
                        chronometerInstance.value = this
                    }
                },
                update = { chronometerView ->
                    val newBase = SystemClock.elapsedRealtime() - (callDuration * 1000L)
                    if (chronometerView.base != newBase) {
                        chronometerView.base = newBase
                    }
                    chronometerView.start()
                    Log.d(TAG, "Update: Chronometer started/updated")
                },
                modifier = Modifier.padding(start = 6.dp,
                    top = 4.dp,
                    end = if (securityIconTint.intValue != -1) 6.dp else 0.dp),
            )
            DisposableEffect(Unit) {
                onDispose {
                    chronometerInstance.value?.let {
                        it.stop()
                        Log.d(TAG, "DisposableEffect: Chronometer stopped in onDispose")
                    }
                }
            }
        }
        if (securityIconTint.intValue != -1) {
            Icon(
                imageVector = if (securityIconTint.intValue == R.color.colorTrafficRed)
                    Icons.Filled.LockOpen
                else
                    Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .padding(top = 4.dp)
                    .clickable {
                        when (securityIconTint.intValue) {
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
                                    val ua = UserAgent.ofAor(viewModel.selectedAor.value)!!
                                    val call = ua.currentCall()
                                    if (call != null) {
                                        if (Api.cmd_exec("zrtp_unverify " + call.zid) != 0)
                                            Log.e(
                                                TAG,
                                                "Command 'zrtp_unverify ${call.zid}' failed"
                                            )
                                        else
                                            securityIconTint.intValue = R.color.colorTrafficYellow
                                    }
                                }
                                negativeText.value = ctx.getString(R.string.cancel)
                                showDialog.value = true
                            }
                        }
                    },
                tint = colorResource(securityIconTint.intValue)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallRow(ctx: Context, viewModel: ViewModel) {

    val isDialpadVisible by viewModel.isDialpadVisible.collectAsState()

    Row( modifier = Modifier
        .fillMaxWidth()
        .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Absolute.SpaceBetween
    ) {
        if (showCallButton.value)
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = {
                    showSuggestions.value = false
                    callClick(ctx, viewModel, false)
                },
                enabled=callButtonEnabled.value
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    modifier = Modifier.size(48.dp),
                    tint = colorResource(if (callButtonEnabled.value)
                        R.color.colorTrafficGreen
                    else
                        R.color.colorTrafficYellow),
                    contentDescription = null,
                )
            }

        if (showCallVideoButton.value) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.VideoCall,
                modifier = Modifier
                    .size(58.dp)
                    .clickable(
                        enabled = callVideoButtonEnabled.value,
                        onClick = {
                            showSuggestions.value = false
                            callClick(ctx, viewModel, true)
                        }
                    ),
                tint = colorResource(if (callVideoButtonEnabled.value)
                    R.color.colorTrafficGreen
                else
                    R.color.colorTrafficYellow),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.size(6.dp))
        }

        if (showCancelButton.value) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = {
                    showSuggestions.value = false
                    abandonAudioFocus(ctx)
                    val ua: UserAgent = UserAgent.ofAor(viewModel.selectedAor.value)!!
                    val call = ua.currentCall()
                    if (call != null) {
                        val callp = call.callp
                        Log.d(
                            TAG,
                            "AoR ${ua.account.aor} hanging up call $callp with ${callUri.value}"
                        )
                        Api.ua_hangup(ua.uap, callp, 0, "")
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    modifier = Modifier.size(48.dp),
                    tint = colorResource(R.color.colorTrafficRed),
                    contentDescription = null,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        if (showHangupButton.value) {

            val selectedAor: String by viewModel.selectedAor.collectAsState()
            val ua = UserAgent.ofAor(selectedAor)!!

            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = {
                    abandonAudioFocus(ctx)
                    val uaCalls = ua.calls()
                    if (uaCalls.isNotEmpty()) {
                        val call = uaCalls.first()
                        val callp = call.callp
                        Log.d(TAG, "AoR ${ua.account.aor} hanging up call $callp with ${callUri.value}")
                        Api.ua_hangup(ua.uap, callp, 0, "")
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    modifier = Modifier.size(48.dp),
                    tint = colorResource(R.color.colorTrafficRed),
                    contentDescription = null,
                )
            }

            if (videoIcon.value != Video.NONE)
                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        val call = ua.currentCall()
                        if (call != null) {
                            videoIcon.value = Video.PENDING
                            videoClick(ctx, call)
                        }
                    }
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(
                            when (videoIcon.value) {
                                Video.ON, Video.PENDING -> R.drawable.video_on
                                Video.OFF -> R.drawable.video_off
                                else -> 0
                            }
                        ),
                        modifier = Modifier.size(48.dp),
                        tint = when (videoIcon.value) {
                            Video.PENDING -> colorResource(R.color.colorTrafficYellow)
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        contentDescription = null,
                    )
                }


            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = {
                    val aor = ua.account.aor
                    val call = ua.currentCall()
                    if (call != null) {
                        if (call.onhold) {
                            Log.d(
                                TAG,
                                "AoR $aor resuming call ${call.callp} with ${callUri.value}"
                            )
                            call.resume()
                            call.onhold = false
                        } else {
                            Log.d(
                                TAG,
                                "AoR $aor holding call ${call.callp} with ${callUri.value}"
                            )
                            call.hold()
                            call.onhold = true
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.PauseCircle,
                    modifier = Modifier.size(48.dp),
                    tint = if (callOnHold.value)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.secondary,
                    contentDescription = null,
                )
            }

            var showTransferDialog by remember { mutableStateOf(false) }
            IconButton(
                modifier = Modifier.size(48.dp),
                enabled = transferButtonEnabled.value,
                onClick = {
                    val call = ua.currentCall()
                    if (call != null) {
                        if (call.onHoldCall != null) {
                            if (!call.executeTransfer()) {
                                alertTitle.value = ctx.getString(R.string.notice)
                                alertMessage.value = ctx.getString(R.string.transfer_failed)
                                showAlert.value = true
                            }
                        } else
                            showTransferDialog = true
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowCircleRight,
                    modifier = Modifier.size(48.dp),
                    tint = if (callTransfer.value)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.secondary,
                    contentDescription = null,
                )
            }

            if (showTransferDialog) {

                val showDialog = remember { mutableStateOf(true) }
                val blindChecked = remember { mutableStateOf(true) }
                val call = ua.currentCall()

                if (showDialog.value)
                    BasicAlertDialog(
                        onDismissRequest = {
                            viewModel.requestHideKeyboard()
                            showDialog.value = false
                            showTransferDialog = false
                        }
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.call_transfer),
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                var transferUri by remember { mutableStateOf("") }
                                val suggestions by remember { contactNames }
                                var filteredSuggestions by remember { mutableStateOf(suggestions) }
                                val focusRequester = remember { FocusRequester() }
                                val lazyListState = rememberLazyListState()
                                OutlinedTextField(
                                    value = transferUri,
                                    singleLine = true,
                                    onValueChange = {
                                        if (it != transferUri) {
                                            transferUri = it
                                            filteredSuggestions =
                                                suggestions.filter { suggestion ->
                                                    transferUri.length > 2 &&
                                                            suggestion.startsWith(
                                                                transferUri,
                                                                ignoreCase = true
                                                            )
                                                }
                                            showSuggestions.value = transferUri.length > 2
                                        }
                                    },
                                    trailingIcon = {
                                        if (transferUri.isNotEmpty())
                                            Icon(
                                                Icons.Outlined.Clear,
                                                contentDescription = null,
                                                modifier = Modifier.clickable {
                                                    if (showSuggestions.value)
                                                        showSuggestions.value = false
                                                    else
                                                        transferUri = ""
                                                },
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp)
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
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .animateContentSize()
                                ) {
                                    if (showSuggestions.value && filteredSuggestions.isNotEmpty()) {
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
                                                    key = { suggestion -> suggestion }
                                                ) { suggestion ->
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                transferUri = suggestion
                                                                showSuggestions.value = false
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
                                if (call != null && call.replaces())
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.blind),
                                                color = MaterialTheme.colorScheme.error,
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
                                                color = MaterialTheme.colorScheme.error,
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
                                            showSuggestions.value = false
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
                                                            ua,
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
                                                        ua,
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
            val shouldRequestFocus by focusDtmf
            TextField(
                value = dtmfText.value,
                onValueChange = {
                    if (it.length > dtmfText.value.length) {
                        val char = it.last()
                        if (char.isDigit() || char == '*' || char == '#') {
                            Log.d(TAG, "Got DTMF digit '$char'")
                            val ua = UserAgent.ofAor(viewModel.selectedAor.value)!!
                            ua.currentCall()?.sendDigit(char)
                        }
                    }
                    dtmfText.value = it
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .width(80.dp)
                    .focusRequester(focusRequester),
                enabled = dtmfEnabled.value,
                textStyle = TextStyle(fontSize = 16.sp),
                label = { Text(stringResource(R.string.dtmf)) },
                singleLine = true
            )
            LaunchedEffect(shouldRequestFocus) {
                if (shouldRequestFocus) {
                    focusRequester.requestFocus()
                    focusDtmf.value = false
                }
            }

            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = {
                    val call = ua.currentCall()
                    val stats = call?.stats("audio")
                    if (stats != null && call.startTime != null && stats != "") {
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
                                    "${ctx.getString(R.string.codecs)}: ${txCodec[0]} ch ${txCodec[2]}/" +
                                    "${rxCodec[0]} ch ${rxCodec[2]}\n" +
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

        if (showAnswerRejectButtons.value) {

            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = {
                    answer(ctx, viewModel, false)
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Unspecified,
                    contentDescription = null,
                )
            }

            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = {
                    answer(ctx, viewModel, true)
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.VideoCall,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Unspecified,
                    contentDescription = null,
                )
            }

            // Spacer(Modifier.weight(1f)) ???

            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = {
                    reject(viewModel)
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    modifier = Modifier.size(48.dp),
                    tint = colorResource(R.color.colorTrafficRed),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun OnHoldNotice() {
    OutlinedButton(
        onClick = {},
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.padding(16.dp).wrapContentSize(),
        shape = RoundedCornerShape(20)
    ) {
        Text(
            text = stringResource(R.string.call_is_on_hold),
            fontSize = 18.sp
        )
    }
}

@Composable
fun VideoLayout(ctx: Context, viewModel: ViewModel, onCloseVideo: () -> Unit) {

    Surface(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().background(Color.Black),
        color = MaterialTheme.colorScheme.background
    ) {
        val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
        var videoSecurityButtonInstance: ImageButton? = null

        LocalSoftwareKeyboardController.current?.hide()

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { factoryContext ->

                val videoView =  VideoView(factoryContext)

                val frameLayout = FrameLayout(factoryContext).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                }

                videoView.surfaceView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                frameLayout.addView(videoView.surfaceView)

                val buttonsLayout = RelativeLayout(ctx).apply {
                    layoutParams = RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                    )
                }

                // Video Button
                val vb = ImageButton(factoryContext).apply {
                    id = View.generateViewId()
                    setImageResource(R.drawable.video_off)
                    setBackgroundResource(0)
                    val prm: RelativeLayout.LayoutParams =
                        RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                            marginStart = 6
                            bottomMargin = 28
                        }
                    layoutParams = prm
                    setOnClickListener {
                        Call.call("connected")?.setVideoDirection(Api.SDP_INACTIVE)
                        onCloseVideo()
                    }
                }
                buttonsLayout.addView(vb)

                // Camera Button
                val cb = ImageButton(factoryContext).apply {
                    if (!Utils.isCameraAvailable(context))
                        visibility = View.INVISIBLE
                    id = View.generateViewId()
                    setImageResource(R.drawable.camera_front)
                    setBackgroundResource(0)
                    val prm: RelativeLayout.LayoutParams =
                        RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                            addRule(RelativeLayout.ALIGN_PARENT_TOP)
                            marginStart = 6
                            topMargin = 32
                        }
                    layoutParams = prm
                    setOnClickListener {
                        val call = Call.call("connected")
                        if (call != null) {
                            if (call.setVideoSource(!BaresipService.cameraFront) != 0)
                                Log.w(TAG, "Failed to set video source")
                            else
                                BaresipService.cameraFront = !BaresipService.cameraFront
                            if (BaresipService.cameraFront)
                                setImageResource(R.drawable.camera_front)
                            else
                                setImageResource(R.drawable.camera_rear)
                        }
                    }
                }
                buttonsLayout.addView(cb)

                // Snapshot Button
                if ((Build.VERSION.SDK_INT >= 29) ||
                    Utils.checkPermissions(ctx, arrayOf(WRITE_EXTERNAL_STORAGE))) {
                    val sb = ImageButton(factoryContext).apply {
                        id = View.generateViewId()
                        setImageResource(R.drawable.snapshot)
                        setBackgroundResource(0)
                        val prm: RelativeLayout.LayoutParams =
                            RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.WRAP_CONTENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                                addRule(RelativeLayout.BELOW, cb.id)
                                marginStart = 6
                                topMargin = 32
                            }
                        layoutParams = prm
                        setOnClickListener {
                            val sdf = SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault())
                            val fileName = "IMG_" + sdf.format(Date()) + ".png"
                            val filePath = Utils.downloadsPath(fileName)
                            if (Api.cmd_exec("snapshot_recv $filePath") != 0)
                                Log.e(TAG, "Command 'snapshot_recv $filePath' failed")
                            else
                                MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
                        }
                    }
                    buttonsLayout.addView(sb)
                }

                // Video Security Button
                val vs = ImageButton(factoryContext).apply {
                    videoSecurityButtonInstance = this
                    id = View.generateViewId()
                    visibility = if (securityIconTint.intValue != -1) View.VISIBLE else View.GONE
                    setBackgroundResource(0)
                    val prm: RelativeLayout.LayoutParams =
                        RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                            addRule(RelativeLayout.ABOVE, vb.id)
                            marginStart = 6
                            bottomMargin = 32
                        }
                    layoutParams = prm
                    setOnClickListener {
                        when (securityIconTint.intValue) {
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
                                dialogMessage.value = ctx.getString(R.string.call_is_secure)
                                positiveText.value = ctx.getString(R.string.unverify)
                                onPositiveClicked.value = {
                                    val ua = UserAgent.ofAor(viewModel.selectedAor.value)!!
                                    val call = ua.currentCall()
                                    if (call != null) {
                                        if (Api.cmd_exec("zrtp_unverify " + call.zid) != 0)
                                            Log.e(
                                                TAG,
                                                "Command 'zrtp_unverify ${call.zid}' failed"
                                            )
                                        else
                                            securityIconTint.intValue = R.color.colorTrafficYellow
                                    }
                                }
                                onNegativeClicked.value = {}
                                showDialog.value = true
                            }
                        }
                    }
                }
                buttonsLayout.addView(vs)

                // Speaker Button
                val sp = ImageButton(factoryContext).apply {
                    id = View.generateViewId()
                    setBackgroundResource(0)
                    val prm: RelativeLayout.LayoutParams =
                        RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                            addRule(RelativeLayout.ALIGN_PARENT_TOP)
                            marginEnd = 0
                            topMargin = 32
                        }
                    layoutParams = prm
                    setImageResource(
                        if (Utils.isSpeakerPhoneOn(am))
                            R.drawable.speaker_on_button
                        else
                            R.drawable.speaker_off_button
                    )
                    setOnClickListener {
                        Utils.toggleSpeakerPhone(ContextCompat.getMainExecutor(context), am)
                        Timer().schedule(250) {
                            setImageResource(
                                if (Utils.isSpeakerPhoneOn(am)) {
                                    R.drawable.speaker_on_button
                                } else {
                                    R.drawable.speaker_off_button
                                }
                            )
                        }
                    }
                }
                buttonsLayout.addView(sp)

                // Mic Button
                val mb = ImageButton(factoryContext).apply {
                    id = View.generateViewId()
                    setBackgroundResource(0)
                    val prm: RelativeLayout.LayoutParams =
                        RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                            addRule(RelativeLayout.BELOW, sp.id)
                            marginEnd = 0
                            topMargin = 32
                        }
                    layoutParams = prm
                    if (BaresipService.isMicMuted)
                        setImageResource(R.drawable.mic_off_button)
                    else
                        setImageResource(R.drawable.mic_on_button)
                    setOnClickListener {
                        BaresipService.isMicMuted = !BaresipService.isMicMuted
                        if (BaresipService.isMicMuted) {
                            this.setImageResource(R.drawable.mic_off_button)
                            Api.calls_mute(true)
                        } else {
                            this.setImageResource(R.drawable.mic_on_button)
                            Api.calls_mute(false)
                        }
                    }
                }
                buttonsLayout.addView(mb)

                // Hangup Button
                val hb = ImageButton(factoryContext).apply {
                    id = View.generateViewId()
                    setImageResource(R.drawable.hangup)
                    setBackgroundResource(0)
                    val prm: RelativeLayout.LayoutParams =
                        RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                            marginEnd = 0
                            bottomMargin = 32
                        }
                    layoutParams = prm
                    setOnClickListener {
                        if (!Utils.isCameraAvailable(context))
                            Call.call("connected")?.setVideoDirection(Api.SDP_INACTIVE)
                        val ua = UserAgent.ofAor(viewModel.selectedAor.value)!!
                        abandonAudioFocus(ctx)
                        val uaCalls = ua.calls()
                        if (uaCalls.isNotEmpty()) {
                            val call = uaCalls.last()
                            val callp = call.callp
                            Log.d(TAG, "AoR ${ua.account.aor} hanging up call $callp with ${callUri.value}")
                            Api.ua_hangup(ua.uap, callp, 0, "")
                        }
                    }
                }
                buttonsLayout.addView(hb)

                // Info Button
                val ib = ImageButton(factoryContext).apply {
                    setImageResource(R.drawable.video_info)
                    setBackgroundResource(0)
                    val prm: RelativeLayout.LayoutParams =
                        RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                            addRule(RelativeLayout.ABOVE, hb.id)
                            marginEnd = 0
                            bottomMargin = 32
                        }
                    layoutParams = prm
                    setOnClickListener {
                        val ua = UserAgent.ofAor(viewModel.selectedAor.value)!!
                        val calls = ua.calls()
                        if (calls.isNotEmpty()) {
                            val call = calls[0]
                            val stats = call.stats("video")
                            alertTitle.value = ctx.getString(R.string.call_info)
                            if (stats != "") {
                                val parts = stats.split(",")
                                val codecs = call.videoCodecs().split(',')
                                val duration = call.duration()
                                val txCodec = if (codecs.isNotEmpty()) codecs[0] else ""
                                val rxCodec = if (codecs.size > 1) codecs[1] else ""
                                alertMessage.value = "${String.format(ctx.getString(R.string.duration), duration)}\n" +
                                        "${ctx.getString(R.string.codecs)}: $txCodec/$rxCodec\n" +
                                        "${String.format(ctx.getString(R.string.rate), parts[0])}\n" +
                                        "${String.format(ctx.getString(R.string.average_rate), parts[1])}\n" +
                                        "${String.format(ctx.getString(R.string.jitter), parts[4])}\n" +
                                        "${ctx.getString(R.string.packets)}: ${parts[2]}\n" +
                                        "${ctx.getString(R.string.lost)}: ${parts[3]}"
                            }
                            else
                                alertMessage.value = ctx.getString(R.string.call_info_not_available)
                            showAlert.value = true
                        }
                    }
                }
                buttonsLayout.addView(ib)

                frameLayout.addView(buttonsLayout)

                frameLayout
            },
            update = { _ ->
                Log.d(TAG, "AndroidView update")
                videoSecurityButtonInstance?.let { button ->
                    if (securityIconTint.intValue != -1) {
                        button.visibility = View.VISIBLE
                        button.setImageResource(videoSecurityIcon(securityIconTint.intValue))
                    }
                    else
                        button.visibility = View.GONE
                }
            },
            onRelease = { _ ->
                Log.d(TAG, "AndroidView onRelease")
            }
        )

        if (showOnHoldNotice.value)
            OnHoldNotice()
    }
}

private fun videoSecurityIcon(security: Int): Int {
    return when (security) {
        R.color.colorTrafficRed -> R.drawable.unlocked_video
        R.color.colorTrafficYellow -> R.drawable.locked_video_yellow
        else -> R.drawable.locked_video_green
    }
}

private fun spinToAor(viewModel: ViewModel, aor: String) {
    if (aor != viewModel.selectedAor.value)
        viewModel.updateSelectedAor(aor)
    viewModel.triggerAccountUpdate()
}

private fun callClick(ctx: Context, viewModel: ViewModel, video: Boolean) {
    if (viewModel.selectedAor.value != "") {
        if (Utils.checkPermissions(ctx, arrayOf(RECORD_AUDIO))) {
            if (Call.inCall())
                return
            val uriText = callUri.value.trim()
            if (uriText.isNotEmpty()) {
                val uris = Contact.contactUris(uriText)
                if (uris.isEmpty())
                    makeCall(ctx, viewModel, uriText, video)
                else if (uris.size == 1)
                    makeCall(ctx, viewModel, uris[0], video)
                else {
                    selectItems.value = uris
                    selectItemAction.value = { index ->
                        makeCall(ctx, viewModel, uris[index], video)
                    }
                    showSelectItemDialog.value = true
                }
            }
            else {
                val ua = UserAgent.ofAor(viewModel.selectedAor.value)!!
                val latestPeerUri = CallHistoryNew.aorLatestPeerUri(ua.account.aor)
                if (latestPeerUri != null)
                    callUri.value = Utils.friendlyUri(ctx, latestPeerUri, ua.account)
            }
        }
        else
            Toast.makeText(ctx, R.string.no_calls, Toast.LENGTH_SHORT).show()
    }
}

fun videoClick(ctx: Context, call: Call) {
    Handler(Looper.getMainLooper()).postDelayed({
        val dir = call.videoRequest
        if (dir != 0) {
            call.videoRequest = 0
            call.setVideoDirection(dir)
        } else {
            if (Utils.isCameraAvailable(ctx))
                call.setVideoDirection(Api.SDP_SENDRECV)
            else
                call.setVideoDirection(Api.SDP_RECVONLY)
        }
    }, 250)
    showVideoLayout.value = true
}

private fun makeCall(ctx: Context, viewModel: ViewModel, uriText: String, video: Boolean) {
    val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
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
    else {
        if (video) {
            showCallButton.value = false
            callVideoButtonEnabled.value = false
        }
        else {
            showCallVideoButton.value = false
            callButtonEnabled.value = false
        }
        if (Build.VERSION.SDK_INT < 31) {
            Log.d(TAG, "Setting audio mode to MODE_IN_COMMUNICATION")
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            runCall(ctx, viewModel, ua, uri, video)
        } else {
            if (am.mode == AudioManager.MODE_IN_COMMUNICATION) {
                runCall(ctx, viewModel, ua, uri, video)
            } else {
                audioModeChangedListener = AudioManager.OnModeChangedListener { mode ->
                    if (mode == AudioManager.MODE_IN_COMMUNICATION) {
                        Log.d(TAG, "Audio mode changed to MODE_IN_COMMUNICATION using " +
                                "device ${am.communicationDevice!!.type}")
                        if (audioModeChangedListener != null) {
                            am.removeOnModeChangedListener(audioModeChangedListener!!)
                            audioModeChangedListener = null
                        }
                        runCall(ctx, viewModel, ua, uri, video)
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

private fun runCall(ctx: Context, viewModel: ViewModel, ua: UserAgent, uri: String, video: Boolean) {
    callRunnable = Runnable {
        callRunnable = null
        if (!call(ctx, viewModel, ua, uri, video)) {
            BaresipService.abandonAudioFocus(ctx)
            showCallButton.value = true
            callButtonEnabled.value = true
            callVideoButtonEnabled.value = true
            showCancelButton.value = false
        }
        else {
            showCallButton.value = false
            showCancelButton.value = true
        }
    }
    callHandler.postDelayed(callRunnable!!, BaresipService.audioDelay)
}

private fun call(
    ctx: Context,
    viewModel: ViewModel,
    ua: UserAgent,
    uri: String,
    video: Boolean,
    onHoldCall: Call? = null
): Boolean {
    spinToAor(viewModel, ua.account.aor)
    val videoDir = if (video) {
        if (Utils.isCameraAvailable(ctx)) Api.SDP_SENDRECV else Api.SDP_RECVONLY
    }
    else
        Api.SDP_INACTIVE
    val callp = ua.callAlloc(0L, Api.VIDMODE_ON)
    return if (callp != 0L) {
        Log.d(TAG, "Adding outgoing call ${ua.uap}/$callp/$uri")
        val call = Call(callp, ua, uri, "out", "outgoing")
        call.onHoldCall = onHoldCall
        call.setMediaDirection(Api.SDP_SENDRECV, videoDir)
        call.add()
        if (onHoldCall != null)
            onHoldCall.newCall = call
        if (call.connect(uri)) {
            showCall(ctx, viewModel, ua)
            true
        } else {
            Log.w(TAG, "call_connect $callp failed")
            if (onHoldCall != null)
                onHoldCall.newCall = null
            call.remove()
            call.destroy()
            showCall(ctx, viewModel, ua)
            false
        }
    } else {
        Log.w(TAG, "callAlloc for ${ua.uap} to $uri failed")
        false
    }
}

private fun answer(ctx: Context, viewModel: ViewModel, video: Boolean) {
    val ua = UserAgent.ofAor(viewModel.selectedAor.value)!!
    val call = ua.currentCall()
    if (call != null) {
        Log.d(TAG, "AoR ${ua.account.aor} answering call from ${callUri.value}")
        if (video) {
            val videoDir = if (Utils.isCameraAvailable(ctx))
                Api.SDP_SENDRECV
            else
                Api.SDP_RECVONLY
            call.setMediaDirection(Api.SDP_SENDRECV, videoDir)
            if (call.videoEnabled()) {
                Log.d(TAG, "Enabling video layout at answer")
                showVideoLayout.value = true
            }
        }
        else {
            call.setMediaDirection(Api.SDP_SENDRECV, Api.SDP_INACTIVE)
            call.disableVideoStream(true)
        }
        val intent = Intent(ctx, BaresipService::class.java)
        intent.action = "Call Answer"
        intent.putExtra("uap", ua.uap)
        intent.putExtra("callp", call.callp)
        ctx.startService(intent)
    }
}

private fun reject(viewModel: ViewModel) {
    val ua = UserAgent.ofAor(viewModel.selectedAor.value)!!
    val call = ua.currentCall()
    if (call != null) {
        val callp = call.callp
        Log.d(TAG, "AoR ${ua.account.aor} rejecting call $callp from ${callUri.value}")
        call.rejected = true
        Api.ua_hangup(ua.uap, callp, 486, "Busy Here")
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
                    call(ctx, viewModel, ua, uri, false, call)
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
        showVideoLayout.value = false
        if (ua.account.resumeUri != "")
            callUri.value = ua.account.resumeUri
        else
            callUri.value = ""
        callUriLabel.value = ctx.getString(R.string.outgoing_call_to_dots)
        callUriEnabled.value = true
        showCallTimer.value = false
        securityIconTint.intValue = -1
        showHangupButton.value = false
        callTransfer.value = false
        dtmfText.value = ""
        dtmfEnabled.value = false
        focusDtmf.value = false
        showCallButton.value = true
        callButtonEnabled.value = true
        showCallVideoButton.value = true
        callVideoButtonEnabled.value = true
        showCancelButton.value = false
        showAnswerRejectButtons.value = false
        showOnHoldNotice.value = false
        dialpadButtonEnabled.value = true
        videoIcon.value = Video.NONE
        if (BaresipService.isMicMuted) {
            BaresipService.isMicMuted = false
            viewModel.updateMicIcon(Icons.Filled.Mic)
        }
    } else {
        pullToRefreshEnabled.value = false
        callUriEnabled.value = false
        val isLandscape = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (showVideoLayout.value || isLandscape || call.held || call.status != "connected") {
            focusDtmf.value = false
            dtmfEnabled.value = !call.held
            Handler(Looper.getMainLooper()).postDelayed({
                viewModel.requestHideKeyboard()
            }, 25)
        }
        else {
            dtmfEnabled.value = true
            focusDtmf.value = true
            viewModel.requestShowKeyboard()
        }

        when (call.status) {
            "outgoing", "transferring", "answered" -> {
                callUriLabel.value = if (call.status == "answered")
                    ctx.getString(R.string.incoming_call_from_dots)
                else
                    ctx.getString(R.string.outgoing_call_to_dots)
                callUri.value = Utils.friendlyUri(ctx, call.peerUri, ua.account)
                showCallTimer.value = false
                securityIconTint.intValue = -1
                showCallButton.value = false
                showCallVideoButton.value = false
                videoIcon.value = Video.NONE
                showCancelButton.value = call.status == "outgoing"
                showHangupButton.value = !showCancelButton.value
                showAnswerRejectButtons.value = false
                showOnHoldNotice.value = false
                dialpadButtonEnabled.value = false
            }
            "incoming" -> {
                showCallTimer.value = false
                securityIconTint.intValue = -1
                val uri = call.diverterUri()
                if (uri != "") {
                    callUriLabel.value = ctx.getString(R.string.diverted_by_dots)
                    callUri.value = Utils.friendlyUri(ctx, uri, ua.account)
                }
                else {
                    callUriLabel.value = ctx.getString(R.string.incoming_call_from_dots)
                    callUri.value = Utils.friendlyUri(ctx, call.peerUri, ua.account)
                }
                showCallButton.value = false
                showCallVideoButton.value = false
                videoIcon.value = Video.NONE
                showCancelButton.value = false
                showHangupButton.value = false
                showAnswerRejectButtons.value = true
                showOnHoldNotice.value = false
                dialpadButtonEnabled.value = false
            }
            "connected" -> {
                if (call.referTo != "") {
                    callUriLabel.value = ctx.getString(R.string.outgoing_call_to_dots)
                    callUri.value = Utils.friendlyUri(ctx, call.referTo, ua.account)
                    transferButtonEnabled.value = false
                } else {
                    if (call.dir == "out") {
                        callUriLabel.value = ctx.getString(R.string.outgoing_call_to_dots)
                        callUri.value = Utils.friendlyUri(ctx, call.peerUri, ua.account)
                    } else {
                        callUriLabel.value = ctx.getString(R.string.incoming_call_from_dots)
                        callUri.value = Utils.friendlyUri(ctx, call.peerUri, ua.account)
                    }
                    transferButtonEnabled.value = true
                }
                callTransfer.value = call.onHoldCall != null
                callDuration = call.duration()
                showCallTimer.value = true
                if (ua.account.mediaEnc == "")
                    securityIconTint.intValue = -1
                else
                    securityIconTint.intValue = call.security
                showCallButton.value = false
                showCallVideoButton.value = false
                if (call.hasVideo())
                    videoIcon.value = Video.ON
                showCancelButton.value = false
                showHangupButton.value = true
                showAnswerRejectButtons.value = false
                callOnHold.value = call.onhold
                Handler(Looper.getMainLooper()).postDelayed({
                    showOnHoldNotice.value = call.held
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
            showCall(ctx, viewModel, ua)
        }
        "call redirect", "video call redirect" -> {
            val redirectUri = ev[1]
            val target = Utils.friendlyUri(ctx, redirectUri, acc)
            if (acc.autoRedirect) {
                redirect(ctx, viewModel, ev[0], ua, redirectUri)
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
                    redirect(ctx, viewModel, ev[0], ua, redirectUri)
                }
                negativeText.value = ctx.getString(R.string.no)
                onNegativeClicked.value = {}
                showDialog.value = true
            }
            showCall(ctx, viewModel, ua)
        }
        "call established" -> {
            if (aor == viewModel.selectedAor.value) {
                dtmfText.value = ""
                showCall(ctx, viewModel, ua)
            }
        }
        "call update" -> {
            val callp = params[1] as Long
            val call = Call.ofCallp(callp)!!
            if (call.hasVideo()) {
                Log.d(TAG, "Enabling video layout at call update")
                showVideoLayout.value = true
            }
            showCall(ctx, viewModel, ua)
        }
        "call video request" -> {
            val callp = params[1] as Long
            val dir = params[2] as Int
            val call = Call.ofCallp(callp)
            if (call == null) {
                Log.w(TAG, "Video request call $callp not found")
                return
            }
            showOnHoldNotice.value = false
            dialogTitle.value = ctx.getString(R.string.video_request)
            val peerUri = Utils.friendlyUri(ctx, call.peerUri, acc)
            dialogMessage.value = when (dir) {
                1 -> String.format(ctx.getString(R.string.allow_video_recv), peerUri)
                2 -> String.format(ctx.getString(R.string.allow_video_send), peerUri)
                3 -> String.format(ctx.getString(R.string.allow_video), peerUri)
                else -> ""
            }
            positiveText.value = ctx.getString(R.string.yes)
            onPositiveClicked.value = {
                call.videoRequest = dir
                videoClick(ctx, call)
            }
            negativeText.value = ctx.getString(R.string.no)
            onNegativeClicked.value = {
            }
            showDialog.value = true
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
                    securityIconTint.intValue = call.security
            }
            negativeText.value = ctx.getString(R.string.no)
            onNegativeClicked.value = {
                call.security = R.color.colorTrafficYellow
                call.zid = ev[2]
                if (aor == viewModel.selectedAor.value)
                    securityIconTint.intValue = R.color.colorTrafficYellow
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
                securityIconTint.intValue = call.security
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
                Api.ua_hangup(uap, callp, 0, "")
            call(ctx, viewModel, ua, ev[1], false)
            showCall(ctx, viewModel, ua)
        }
        "transfer failed" -> {
            showCall(ctx, viewModel, ua)
        }
        "call closed" -> {
            val call = ua.currentCall()
            if (call != null) {
                call.resume()
                callDuration = call.duration()
                showCallTimer.value = true
            }
            else {
                showCallTimer.value = false
                securityIconTint.intValue = -1
            }
            if (aor == viewModel.selectedAor.value) {
                ua.account.resumeUri = ""
                showCall(ctx, viewModel, ua)
                if (acc.missedCalls)
                    viewModel.triggerAccountUpdate()
            }
            //if (kgm.isDeviceLocked)
            //    this.setShowWhenLocked(false)
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

    handleNextEvent()
}

fun handleIntent(ctx: Context, viewModel: ViewModel, intent: Intent, action: String) {
    Log.d(TAG, "Handling intent '$action'")
    val ev = action.split(",")
    when (ev[0]) {
        "call", "video call", "dial" -> {
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
            callUri.value = intent.getStringExtra("peer")!!
            spinToAor(viewModel, ua.account.aor)
            if (ev[0] == "call")
                callClick(ctx, viewModel, false)
            else if(ev[0] == "video call")
                callClick(ctx, viewModel, true)
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
                answer(ctx, viewModel, false)
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

private fun redirect(ctx: Context, viewModel: ViewModel, event: String, ua: UserAgent, redirectUri: String) {
    if (ua.account.aor != viewModel.selectedAor.value)
        spinToAor(viewModel, ua.account.aor)
    callUri.value = redirectUri
    callClick(ctx, viewModel, event == "video call redirect")
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
    val zipFile = ctx.getString(R.string.app_name_plus) + ".zip"
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
    val zipFile = ctx.getString(R.string.app_name_plus) + ".zip"
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
            val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
            am.removeOnModeChangedListener(audioModeChangedListener!!)
            audioModeChangedListener = null
            BaresipService.abandonAudioFocus(ctx)
        }
    }
}

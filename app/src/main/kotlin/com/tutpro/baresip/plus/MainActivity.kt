@file:OptIn(ExperimentalMaterial3Api::class)

package com.tutpro.baresip.plus

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.CAMERA
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_CALL
import android.content.Intent.ACTION_DIAL
import android.content.Intent.ACTION_VIEW
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Observer
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {

    private lateinit var imm: InputMethodManager
    private lateinit var nm: NotificationManager
    private lateinit var am: AudioManager
    private lateinit var kgm: KeyguardManager
    private lateinit var screenEventReceiver: BroadcastReceiver
    private lateinit var serviceEventObserver: Observer<Event<Long>>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var comDevChangedListener: AudioManager.OnCommunicationDeviceChangedListener

    private lateinit var baresipService: Intent

    private var restart = false
    private var atStartup = false
    private var initialized = false

    private val viewModel: ViewModel by viewModels()
    private lateinit var navController:  NavHostController

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        val extraAction = intent.getStringExtra("action")
        Log.i(TAG, "Main onCreate ${intent.action}/${intent.data}/$extraAction")

        window.addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)

        BaresipService.darkTheme.value = Utils.isThemeDark(this)

        // Must be done after view has been created
        this.setShowWhenLocked(true)
        this.setTurnScreenOn( true)
        Utils.requestDismissKeyguard(this)

        imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        am = getSystemService(AUDIO_SERVICE) as AudioManager
        kgm = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

        serviceEventObserver = Observer {
            val event = it.getContentIfNotHandled()
            Log.d(TAG, "Observed event $event")
            if (event != null && BaresipService.serviceEvents.isNotEmpty()) {
                val first = BaresipService.serviceEvents.removeAt(0)
                if (taskId != -1) {
                    if (first.event == "started" && !initialized)
                    // Android has restarted baresip when permission has been denied in app settings
                        recreate()
                    else {
                        if (first.event == "stopped") {
                            Log.d(
                                TAG,
                                "Handling service event 'stopped' with start error '${first.params[0]}'"
                            )
                            if (first.params[0] != "")
                                handleDialog(
                                    ctx = applicationContext,
                                    title = getString(R.string.notice),
                                    message =getString(R.string.start_failed)
                                )
                            else {
                                finishAndRemoveTask()
                                if (restart)
                                    reStart()
                                else
                                    exitProcess(0)
                            }
                        } else
                            handleServiceEvent(applicationContext, viewModel, first.event, first.params)
                    }
                }
                else
                    Log.d(TAG, "Omit service event '$event' for task -1")
            }
        }

        BaresipService.serviceEvent.observeForever(serviceEventObserver)

        screenEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(contxt: Context, intent: Intent) {
                if (kgm.isKeyguardLocked) {
                    Log.d(TAG, "Screen on when locked")
                    this@MainActivity.setShowWhenLocked(Call.inCall())
                }
            }
        }

        this.registerReceiver(screenEventReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
        })

        if (Build.VERSION.SDK_INT >= 31) {
            comDevChangedListener = AudioManager.OnCommunicationDeviceChangedListener { device ->
                if (device != null) {
                    Log.d(TAG, "Com device changed to type ${device.type} in mode ${am.mode}")
                }
            }
            am.addOnCommunicationDeviceChangedListener(mainExecutor, comDevChangedListener)
        }

        initialized = true

        val restartApp = {
            Log.i(TAG, "Restarting baresip")
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            if (BaresipService.isServiceRunning) {
                restart = true
                baresipService.action = "Stop"
                startService(baresipService)
            } else {
                finishAndRemoveTask()
                val pm = applicationContext.packageManager
                val intent = pm.getLaunchIntentForPackage(applicationContext.packageName)
                if (intent != null) {
                    applicationContext.startActivity(intent)
                    exitProcess(0)
                } else {
                    Log.e(TAG, "Failed to restart: Launch intent is null")
                }
            }
        }

        val quitApp = {
            Log.i(TAG, "Quiting baresip")
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            if (BaresipService.isServiceRunning) {
                restart = false
                baresipService.action = "Stop"
                startService(baresipService)
            } else {
                finishAndRemoveTask()
                exitProcess(0)
            }
        }

        baresipService = Intent(this@MainActivity, BaresipService::class.java)

        atStartup = intent.hasExtra("onStartup")

        when (intent?.action) {
            ACTION_DIAL, ACTION_CALL, ACTION_VIEW ->
                if (BaresipService.isServiceRunning)
                    callAction(applicationContext, viewModel, intent.data, if (intent?.action == ACTION_CALL) "call" else "dial")
                else
                    BaresipService.callActionUri = intent.data.toString().replace("%2B", "+")
                        .replace("%20", "").filterNot{setOf('-', ' ', '(', ')').contains(it)}
        }

        var permissions = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(POST_NOTIFICATIONS, RECORD_AUDIO, BLUETOOTH_CONNECT)
        else if (Build.VERSION.SDK_INT >= 31)
            arrayOf(RECORD_AUDIO, BLUETOOTH_CONNECT)
        else
            if (Build.VERSION.SDK_INT < 29)
                arrayOf(RECORD_AUDIO, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
            else
                arrayOf(RECORD_AUDIO)

        BaresipService.supportedCameras = Utils.supportedCameras(applicationContext).isNotEmpty()

        if (BaresipService.supportedCameras)
            permissions += CAMERA

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                Log.i(TAG, "Permission granted: $isGranted")
            }

        requestPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                val denied = mutableListOf<String>()
                val shouldShow = mutableListOf<String>()
                it.forEach { permission ->
                    if (!permission.value) {
                        denied.add(permission.key)
                        if (shouldShowRequestPermissionRationale(permission.key))
                            shouldShow.add(permission.key)
                    }
                }
                if (denied.contains(POST_NOTIFICATIONS) && !shouldShow.contains(POST_NOTIFICATIONS)) {
                    handleDialog(
                        ctx = applicationContext,
                        title = getString(R.string.notice),
                        message = getString(R.string.no_notifications),
                        action = { quitRestart(false) }
                    )
                }
                else {
                    if (shouldShow.isNotEmpty()) {
                        handleDialog(
                            ctx = applicationContext,
                            title = getString(R.string.permissions_rationale),
                            message =  if (CAMERA in permissions)
                                getString(R.string.audio_and_video_permissions)
                            else
                                getString(R.string.audio_permissions),
                            action = { requestPermissionsLauncher.launch(permissions) }
                        )
                    }
                    else {
                        if (!BaresipService.isStartReceived) {
                            baresipService.action = "Start"
                            startService(baresipService)
                            if (atStartup)
                                moveTaskToBack(true)
                        }
                    }
                }
            }

        setContent {

            AppTheme {

                navController = rememberNavController()

                LaunchedEffect(key1 = viewModel) {
                    viewModel.navigationCommand.collect { command ->
                        Log.d(TAG, "MainActivity: Received NavigationCommand: $command")
                        when (command) {
                            is NavigationCommand.NavigateToChat -> {
                                val route = "chat/${command.aor}/${command.peerUri}"
                                navController.navigate(route)
                            }
                            is NavigationCommand.NavigateToCalls -> {
                                val route = "calls/${command.aor}"
                                navController.navigate(route)
                            }
                            is NavigationCommand.NavigateToHome -> {
                                navController.navigate("main")
                            }
                        }
                    }
                }

                NavHost(navController, startDestination = "main") {
                    mainScreenRoute(
                        navController = navController,
                        viewModel = viewModel,
                        onRequestPermissions = { requestPermissionsLauncher.launch(permissions) },
                        onRestartApp = { restartApp() },
                        onQuitApp = { quitApp() }
                    )
                    aboutScreenRoute(navController)
                    settingsScreenRoute(
                        navController = navController,
                        onRestartApp = { restartApp() }
                    )
                    accountsScreenRoute(navController)
                    audioScreenRoute(navController)
                    accountScreenRoute(navController)
                    codecsScreenRoute(navController)
                    contactsScreenRoute(navController, viewModel)
                    baresipContactScreenRoute(navController)
                    androidContactScreenRoute(navController, viewModel)
                    callsScreenRoute(navController, viewModel)
                    callDetailsScreenRoute(navController, viewModel)
                    blockedScreenRoute(navController)
                    chatsScreenRoute(navController)
                    chatScreenRoute(navController, viewModel)
                }
            }
        }
    } // OnCreate

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "Main onStart")
        val action = intent.getStringExtra("action")
        if (action != null) {
            // MainActivity was not visible when call, message, or transfer request came in
            intent.removeExtra("action")
            handleIntent(applicationContext, viewModel, intent, action)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Main onResume")
        nm.cancelAll()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Main onPause")
    }

    override fun onDestroy() {
        Log.d(TAG, "Main onDestroy")

        this.unregisterReceiver(screenEventReceiver)

        if (Build.VERSION.SDK_INT >= 31)
            am.removeOnCommunicationDeviceChangedListener(comDevChangedListener)

        BaresipService.serviceEvent.removeObserver(serviceEventObserver)
        BaresipService.serviceEvents.clear()

        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        // Called when MainActivity already exists at the top of current task
        super.onNewIntent(intent)

        this.setShowWhenLocked(true)
        this.setTurnScreenOn(true)

        Log.d(TAG, "onNewIntent with action/data '${intent.action}/${intent.data}'")

        when (intent.action) {
            ACTION_DIAL, ACTION_CALL, ACTION_VIEW ->
                callAction(
                    applicationContext,
                    viewModel,
                    intent.data,
                    if (intent.action == ACTION_CALL) "call" else "dial"
                )
            else -> {
                val action = intent.getStringExtra("action")
                if (action != null) {
                    intent.removeExtra("action")
                    handleIntent(applicationContext, viewModel, intent, action)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val stream = if (am.mode == AudioManager.MODE_RINGTONE)
            AudioManager.STREAM_RING
        else
            AudioManager.STREAM_VOICE_CALL
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                am.adjustStreamVolume(stream,
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                        AudioManager.ADJUST_LOWER else
                        AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI)
                Log.d(TAG, "Adjusted volume $keyCode of stream $stream to ${am.getStreamVolume(stream)}")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun quitRestart(reStart: Boolean) {
        Log.i(TAG, "quitRestart Restart = $reStart")
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        if (BaresipService.isServiceRunning) {
            restart = reStart
            baresipService.action = "Stop"
            startService(baresipService)
        } else {
            finishAndRemoveTask()
            if (reStart)
                quitRestart(true)
            else
                exitProcess(0)
        }
    }

    private fun reStart() {
        Log.d(TAG, "Trigger restart")
        val pm = applicationContext.packageManager
        val intent = pm.getLaunchIntentForPackage(this.packageName)
        this.startActivity(intent)
        exitProcess(0)
    }
}

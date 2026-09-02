package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.telecom.TelecomManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tutpro.baresip.BaresipService.Companion.uas
import kotlinx.coroutines.delay

private const val CALL_SCREEN_TAG = "CallScreen"

fun NavGraphBuilder.callScreenRoute(navController: NavController, viewModel: ViewModel) {
    composable("call") {
        CallScreen(navController, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallScreen(navController: NavController, viewModel: ViewModel) {
    val ctx = LocalContext.current
    val selectedAor by viewModel.selectedAor.collectAsState()
    val ua = uas.value.find { it.account.aor == selectedAor }
    val focusedCall by viewModel.focusedCall.collectAsState()
    val calls by viewModel.calls.collectAsState()

    // Active call resolution
    val call = ua?.currentCall() ?: focusedCall ?: calls.lastOrNull()
    val status = call?.status?.value ?: "idle"

    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val micIcon by viewModel.micIcon.collectAsState()
    var isMicMuted by remember { mutableStateOf(BaresipService.isMicMuted) }
    var isRecording by remember { mutableStateOf(BaresipService.isRecOn) }
    var showDialpad by remember { mutableStateOf(false) }

    var showTransferDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(micIcon) {
        isMicMuted = micIcon == Icons.Filled.MicOff || BaresipService.isMicMuted
    }

    // Keep screen on during active call
    DisposableEffect(Unit) {
        val window = (ctx as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-return to main if no calls are active
    LaunchedEffect(calls) {
        if (calls.isEmpty()) {
            Log.d(CALL_SCREEN_TAG, "No active calls, returning to main")
            navController.navigate("main") {
                popUpTo("main") { inclusive = true }
            }
        }
    }

    // Intercept back button to return safely to main without killing call
    BackHandler(enabled = true) {
        navController.navigate("main") {
            popUpTo("main") { inclusive = true }
        }
    }

    val isConnected = status == "connected" || status == "transferring" || status == "answered"
    val isIncoming = status == "incoming"
    val isCalling = status == "outgoing"
    val isRinging = status == "ringing"
    val isOutgoing = isCalling || isRinging
    val isHeldByPeer = call?.showOnHoldNotice?.value == true
    val isHold = call?.callOnHold?.value == true && !isHeldByPeer
    val isOnHold = call?.callOnHold?.value == true

    val greenColor = Color(0xFF2ABB86)
    val redColor = Color(0xFFEA4335)
    val accentGreen = Color(0xFF00C853)
    val yellowColor = Color(0xFFF9A825)

    // Resolve Contact for Avatar & Name
    val contact = remember(call?.peerUri) {
        if (call != null) Contact.findContact(call.peerUri) else null
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Navigation & Security Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                // Minimize Call Screen Button
                IconButton(
                    onClick = {
                        navController.navigate("main") {
                            popUpTo("main") { inclusive = true }
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Center Status Badge & Text
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusText = when {
                        isOnHold -> stringResource(R.string.call_is_on_hold)
                        isIncoming -> stringResource(R.string.incoming_call)
                        isCalling -> stringResource(R.string.calling)
                        isRinging -> stringResource(R.string.ringing)
                        isConnected -> stringResource(R.string.connected)
                        else -> stringResource(R.string.call)
                    }

                    val statusColor = when {
                        isOnHold -> yellowColor
                        isIncoming -> greenColor
                        isCalling -> yellowColor
                        isRinging -> greenColor
                        isConnected -> accentGreen
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isRecording) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            color = redColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, redColor.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(redColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "REC",
                                    color = redColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ZRTP Security Lock Icon
                if (call != null && call.securityIconTint.value != -1) {
                    IconButton(
                        onClick = { showSecurityDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (call.securityIconTint.value == R.color.colorTrafficRed)
                                Icons.Filled.LockOpen
                            else
                                Icons.Filled.Lock,
                            contentDescription = "Security",
                            tint = colorResource(call.securityIconTint.value),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Contact Avatar with Presence Badge
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    modifier = Modifier
                        .size(130.dp)
                        .shadow(4.dp, CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    when (contact) {
                        is Contact.BaresipContact -> {
                            val avatarBitmap = contact.avatarImage
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap.asImageBitmap(),
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(contact.colorInt())),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.name.firstOrNull()?.uppercase() ?: "",
                                        color = Color.White,
                                        fontSize = 52.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        is Contact.AndroidContact -> {
                            val thumbUri = contact.thumbnailUri
                            if (thumbUri != null) {
                                coil.compose.AsyncImage(
                                    model = thumbUri,
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(contact.colorInt())),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.name.firstOrNull()?.uppercase() ?: "",
                                        color = Color.White,
                                        fontSize = 52.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        null -> {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(76.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Presence Indicator Badge
                Surface(
                    modifier = Modifier
                        .size(30.dp)
                        .shadow(2.dp, CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .background(
                                when {
                                    isOnHold -> yellowColor
                                    isConnected -> accentGreen
                                    isIncoming -> greenColor
                                    isCalling -> yellowColor
                                    isRinging -> greenColor
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Caller Name & Number
            val callerName = if (call != null) Utils.friendlyUri(ctx, call.peerUri, call.ua.account) else stringResource(R.string.unknown)
            Text(
                text = callerName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            val peerNumber = call?.peerUri?.substringAfter(":") ?: ""
            if (peerNumber.isNotEmpty() && peerNumber != callerName) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = peerNumber,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Call Duration Timer / Ringing / Calling / Hold Status
            if (call != null) {
                if (isOnHold) {
                    Text(
                        text = stringResource(R.string.call_is_on_hold),
                        fontSize = 18.sp,
                        color = yellowColor,
                        fontWeight = FontWeight.Medium
                    )
                } else if (isCalling || isRinging) {
                    var dots by remember { mutableStateOf(".") }
                    LaunchedEffect(Unit) {
                        while (true) {
                            dots = when (dots) {
                                "." -> ".."
                                ".." -> "..."
                                else -> "."
                            }
                            delay(500)
                        }
                    }
                    val label = if (isCalling) stringResource(R.string.calling) else stringResource(R.string.ringing)
                    Text(
                        text = "$label$dots",
                        fontSize = 18.sp,
                        color = if (isCalling) yellowColor else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                } else if (isConnected) {
                    CallTimerDisplay(initialDurationSeconds = call.duration().toLong())
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Actions Section based on Call State
            when {
                isIncoming -> {
                    IncomingCallContent(
                        onAnswer = { call?.let { answerCall(ctx, it) } },
                        onDecline = { call?.let { rejectCall(it) } }
                    )
                }
                isOutgoing -> {
                    OutgoingCallContent(
                        isMicMuted = isMicMuted,
                        isSpeakerOn = isSpeakerOn,
                        isRecording = isRecording,
                        isMobileAccount = ua?.account?.isMobile ?: false,
                        onToggleMute = {
                            val newMute = !BaresipService.isMicMuted
                            BaresipService.setMicMute(newMute)
                            if (newMute) viewModel.updateMicIcon(Icons.Filled.MicOff) else viewModel.updateMicIcon(Icons.Filled.Mic)
                            isMicMuted = newMute
                        },
                        onToggleSpeaker = {
                            BaresipService.instance?.toggleSpeakerphone()
                        },
                        onToggleRecord = {
                            val nextRec = !BaresipService.isRecOn
                            BaresipService.isRecOn = nextRec
                            if (nextRec) {
                                Api.module_load("sndfile")
                                Toast.makeText(ctx, ctx.getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
                            } else {
                                Api.module_unload("sndfile")
                                val savedNotice = "${ctx.getString(R.string.recording_stopped)}\n${ctx.getString(R.string.recording_saved)} (${ctx.getString(R.string.call_history)})"
                                Toast.makeText(ctx, savedNotice, Toast.LENGTH_LONG).show()
                                call?.let { c ->
                                    val rxName = c.dumpfiles[0]
                                    val fileName = if (rxName.isNotEmpty()) java.io.File(rxName).name else null
                                    BaresipService.instance?.showRecordingSavedNotification(c.dumpfiles[0], fileName)
                                }
                            }
                            isRecording = nextRec
                        },
                        onHangup = {
                            call?.let {
                                Log.d(CALL_SCREEN_TAG, "Hanging up outgoing call ${it.callp}")
                                it.terminated.value = true
                                it.hangup(0, "")
                            }
                        }
                    )
                }
                else -> {
                    InCallContent(
                        isMicMuted = isMicMuted,
                        isSpeakerOn = isSpeakerOn,
                        isRecording = isRecording,
                        isHold = isHold,
                        isHeldByPeer = isHeldByPeer,
                        isMobileAccount = ua?.account?.isMobile ?: false,
                        onToggleMute = {
                            val newMute = !BaresipService.isMicMuted
                            BaresipService.setMicMute(newMute)
                            if (newMute) viewModel.updateMicIcon(Icons.Filled.MicOff) else viewModel.updateMicIcon(Icons.Filled.Mic)
                            isMicMuted = newMute
                        },
                        onToggleSpeaker = {
                            BaresipService.instance?.toggleSpeakerphone()
                        },
                        onToggleRecord = {
                            val nextRec = !BaresipService.isRecOn
                            BaresipService.isRecOn = nextRec
                            if (nextRec) {
                                Api.module_load("sndfile")
                                Toast.makeText(ctx, ctx.getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
                            } else {
                                Api.module_unload("sndfile")
                                val savedNotice = "${ctx.getString(R.string.recording_stopped)}\n${ctx.getString(R.string.recording_saved)} (${ctx.getString(R.string.call_history)})"
                                Toast.makeText(ctx, savedNotice, Toast.LENGTH_LONG).show()
                                call?.let { c ->
                                    val rxName = c.dumpfiles[0]
                                    val fileName = if (rxName.isNotEmpty()) java.io.File(rxName).name else null
                                    BaresipService.instance?.showRecordingSavedNotification(c.dumpfiles[0], fileName)
                                }
                            }
                            isRecording = nextRec
                        },
                        onToggleHold = {
                            call?.let {
                                if (it.callOnHold.value && !it.showOnHoldNotice.value) {
                                    Log.d(CALL_SCREEN_TAG, "User requested resume for ${it.callp}")
                                    it.resume()
                                } else if (!it.callOnHold.value && !it.showOnHoldNotice.value) {
                                    Log.d(CALL_SCREEN_TAG, "User requested hold for ${it.callp}")
                                    it.hold()
                                }
                            }
                        },
                        onToggleDialpad = {
                            showDialpad = true
                        },
                        onTransfer = {
                            call?.let {
                                if (it.onHoldCall != null) {
                                    if (!Api.call_supported(it.callp, Api.REPLACES)) {
                                        Toast.makeText(ctx, R.string.replaces_not_supported, Toast.LENGTH_SHORT).show()
                                    } else {
                                        it.hold()
                                        if (!it.executeTransfer()) {
                                            Toast.makeText(ctx, R.string.transfer_failed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    showTransferDialog = true
                                }
                            }
                        },
                        onInfo = {
                            showInfoDialog = true
                        },
                        onHangup = {
                            call?.let {
                                Log.d(CALL_SCREEN_TAG, "Hanging up connected call ${it.callp}")
                                it.terminated.value = true
                                it.hangup(0, "")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // In-Call DTMF Dialpad Bottom Sheet
    if (showDialpad && !isHeldByPeer && call != null) {
        ModalBottomSheet(
            onDismissRequest = { showDialpad = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            InCallDialpadSheet(
                dtmfText = call.dtmfText.value,
                onSendDtmf = { digit ->
                    call.sendDigit(digit)
                    call.dtmfText.value += digit
                },
                onDismiss = { showDialpad = false }
            )
        }
    }

    // Call Transfer Dialog
    if (showTransferDialog && call != null) {
        CallTransferDialog(
            ctx = ctx,
            call = call,
            onDismiss = { showTransferDialog = false }
        )
    }

    // Call Audio Stats Info Dialog
    if (showInfoDialog && call != null) {
        CallInfoDialog(
            ctx = ctx,
            call = call,
            onDismiss = { showInfoDialog = false }
        )
    }

    // ZRTP Security Dialog
    if (showSecurityDialog && call != null) {
        CallSecurityDialog(
            ctx = ctx,
            call = call,
            onDismiss = { showSecurityDialog = false }
        )
    }
}

private fun answerCall(ctx: Context, call: Call) {
    Log.d(CALL_SCREEN_TAG, "AoR ${call.ua.account.aor} answering call ${call.callp}")
    val intent = Intent(ctx, BaresipService::class.java)
    intent.action = "Call Answer"
    intent.putExtra("uap", call.ua.uap)
    intent.putExtra("callp", call.callp)
    ContextCompat.startForegroundService(ctx, intent)
}

private fun rejectCall(call: Call) {
    Log.d(CALL_SCREEN_TAG, "AoR ${call.ua.account.aor} rejecting call ${call.callp}")
    call.reject()
}

@Composable
private fun CallTimerDisplay(initialDurationSeconds: Long, modifier: Modifier = Modifier) {
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
            delay(1000)
        }
    }

    Text(
        text = timeText,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallTransferDialog(
    ctx: Context,
    call: Call,
    onDismiss: () -> Unit
) {
    val blindChecked = remember { mutableStateOf(true) }
    var transferUri by remember { mutableStateOf("") }
    var filteredSuggestions by remember { mutableStateOf<List<Triple<Contact, AnnotatedString, Contact.ContactUri?>>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.call_transfer),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = transferUri,
                    singleLine = true,
                    onValueChange = { input ->
                        transferUri = input
                        if (input.length > 1) {
                            val normalizedInput = Utils.unaccent(input)
                            val numericInput = input.filter { c -> c.isDigit() || c == '+' }
                            val currentAor = call.ua.account.aor
                            filteredSuggestions = BaresipService.contacts.flatMap { contact ->
                                val nameMatch = Utils.unaccent(contact.name()).contains(normalizedInput, ignoreCase = true)
                                val uris = contact.uris().filter { !Utils.uriMatch(it.uri, currentAor) }
                                val matchingUris = uris.filter { u ->
                                    (u.uri.startsWith("tel:") && numericInput.isNotEmpty() && u.uri.substring(4).contains(numericInput)) ||
                                            (u.uri.startsWith("sip:") && Utils.uriUserPart(u.uri).contains(normalizedInput, ignoreCase = true))
                                }
                                if (nameMatch) {
                                    val annotatedName = Utils.buildAnnotatedStringWithHighlight(contact.name(), input)
                                    if (uris.isEmpty())
                                        listOf(Triple(contact, annotatedName, null))
                                    else
                                        uris.map { Triple(contact, annotatedName, it) }
                                } else if (matchingUris.isNotEmpty()) {
                                    matchingUris.map { Triple(contact, AnnotatedString(contact.name()), it) }
                                } else {
                                    emptyList()
                                }
                            }
                        } else {
                            filteredSuggestions = emptyList()
                        }
                    },
                    trailingIcon = {
                        if (transferUri.isNotEmpty()) {
                            Icon(
                                Icons.Outlined.Clear,
                                contentDescription = null,
                                modifier = Modifier.clickable { transferUri = "" },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text(stringResource(R.string.transfer_destination)) },
                    textStyle = TextStyle(fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Suggestions List
                if (filteredSuggestions.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            state = lazyListState
                        ) {
                            itemsIndexed(
                                items = filteredSuggestions,
                                key = { index, item -> "${item.first.id()}:${item.third?.uri ?: ""}:$index" }
                            ) { _, (contactItem, annotatedName, matchingUri) ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val uri = matchingUri?.uri ?: contactItem.uris().firstOrNull()?.uri ?: contactItem.name()
                                            transferUri = Utils.friendlyUri(ctx, uri, call.ua.account, unique = true)
                                            filteredSuggestions = emptyList()
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = annotatedName,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                if (call.replaces()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { blindChecked.value = true }
                                .padding(end = 16.dp)
                        ) {
                            RadioButton(
                                selected = blindChecked.value,
                                onClick = { blindChecked.value = true }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.blind),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { blindChecked.value = false }
                        ) {
                            RadioButton(
                                selected = !blindChecked.value,
                                onClick = { blindChecked.value = false }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.attended),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val rawInput = transferUri.trim()
                            val uris = Contact.contactUris(rawInput)
                            val targetUri = if (uris.size == 1) uris[0].uri else rawInput
                            val uri = if (Utils.isTelUri(targetUri))
                                Utils.telToSip(targetUri, call.ua.account)
                            else if (targetUri.startsWith("sip:"))
                                targetUri
                            else
                                Utils.uriComplete(targetUri, call.ua.account.aor)

                            if (!Utils.checkUri(uri)) {
                                Toast.makeText(
                                    ctx,
                                    String.format(ctx.getString(R.string.invalid_sip_or_tel_uri), targetUri),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val isAttended = call.replaces() && !blindChecked.value
                                val success = if (isAttended) {
                                    if (call.hold()) {
                                        call.onhold = true
                                        call.referTo = uri
                                        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                                        val extras = Bundle().apply {
                                            putParcelable(
                                                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                                                BaresipService.getPhoneAccountHandle(
                                                    ctx,
                                                    if (call.ua.account.isMobile) BaresipService.PSTN_ACCOUNT_ID else BaresipService.SIP_ACCOUNT_ID
                                                )
                                            )
                                        }
                                        val callExtras = Bundle().apply {
                                            putBoolean("conferenceCall", false)
                                            putLong("uap", call.ua.uap)
                                            putLong("onHoldCallp", call.callp)
                                            if (call.ua.account.isMobile) {
                                                putBoolean("pstnCall", true)
                                                putString("aor", call.ua.account.aor)
                                            }
                                        }
                                        extras.putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, callExtras)
                                        val telecomUri = if (uri.startsWith("tel:") || uri.startsWith("sip:")) uri.toUri() else "sip:$uri".toUri()
                                        try {
                                            tm.placeCall(telecomUri, extras)
                                            true
                                        } catch (e: Exception) {
                                            Log.e(CALL_SCREEN_TAG, "Telecom placeCall for attended transfer failed: ${e.message}")
                                            val intent = Intent(ctx, BaresipService::class.java).apply {
                                                action = "Start Call"
                                                putExtra("uap", call.ua.uap)
                                                putExtra("uri", uri)
                                                putExtra("onHoldCallp", call.callp)
                                            }
                                            ContextCompat.startForegroundService(ctx, intent)
                                            true
                                        }
                                    } else {
                                        false
                                    }
                                } else {
                                    call.hold()
                                    call.transfer(uri)
                                }
                                if (!success) {
                                    Toast.makeText(ctx, R.string.transfer_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                            onDismiss()
                        },
                        enabled = transferUri.isNotBlank()
                    ) {
                        Text(stringResource(R.string.transfer))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallInfoDialog(
    ctx: Context,
    call: Call,
    onDismiss: () -> Unit
) {
    val stats = call.stats("audio")
    val infoMessage = if (stats.isNotEmpty() && call.startTime != null) {
        val parts = ArrayList(stats.split(","))
        if (parts.size >= 5 && parts[2] == "0/0") {
            parts[2] = "?/?"
            parts[3] = "?/?"
            parts[4] = "?/?"
        }
        val codecs = call.audioCodecs().split(',')
        val duration = call.duration()
        val txCodec = codecs.getOrNull(0)?.split("/") ?: listOf("?", "?", "?")
        val rxCodec = codecs.getOrNull(1)?.split("/") ?: listOf("?", "?", "?")
        "${String.format(ctx.getString(R.string.duration), duration)}\n" +
                "${ctx.getString(R.string.codecs)}: \u2192 ${txCodec.getOrElse(0) { "?" }} ${txCodec.getOrElse(1) { "?" }}Hz ${txCodec.getOrElse(2) { "?" }}ch /\n " +
                "    \u2190 ${rxCodec.getOrElse(0) { "?" }} ${rxCodec.getOrElse(1) { "?" }}Hz ${rxCodec.getOrElse(2) { "?" }}ch\n" +
                "${String.format(ctx.getString(R.string.rate), parts.getOrElse(0) { "?" })}\n" +
                "${String.format(ctx.getString(R.string.average_rate), parts.getOrElse(1) { "?" })}\n" +
                "${ctx.getString(R.string.packets)}: ${parts.getOrElse(2) { "?" }}\n" +
                "${ctx.getString(R.string.lost)}: ${parts.getOrElse(3) { "?" }}\n" +
                String.format(ctx.getString(R.string.jitter), parts.getOrElse(4) { "?" })
    } else {
        ctx.getString(R.string.call_info_not_available)
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.call_info),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = infoMessage,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallSecurityDialog(
    ctx: Context,
    call: Call,
    onDismiss: () -> Unit
) {
    val tint = call.securityIconTint.value
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (tint == R.color.colorTrafficGreen) stringResource(R.string.info) else stringResource(R.string.notice),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = when (tint) {
                        R.color.colorTrafficRed -> stringResource(R.string.call_not_secure)
                        R.color.colorTrafficYellow -> stringResource(R.string.peer_not_verified)
                        else -> stringResource(R.string.call_is_secure)
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (tint == R.color.colorTrafficGreen) {
                        TextButton(onClick = {
                            if (Api.cmd_exec("zrtp_unverify " + call.zid) != 0) {
                                Log.e(CALL_SCREEN_TAG, "Command 'zrtp_unverify ${call.zid}' failed")
                            } else {
                                call.securityIconTint.value = R.color.colorTrafficYellow
                            }
                            onDismiss()
                        }) {
                            Text(stringResource(R.string.unverify), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

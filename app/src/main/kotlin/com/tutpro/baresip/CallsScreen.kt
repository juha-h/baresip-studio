package com.tutpro.baresip

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.verticalScrollbar

fun NavGraphBuilder.callsScreenRoute(navController: NavController, viewModel: ViewModel) {
    composable(
        route = "calls/{aor}",
        arguments = listOf(navArgument("aor") { type = NavType.StringType })
    ) { backStackEntry ->
        val aor = backStackEntry.arguments?.getString("aor")!!
        CallsScreen(navController, viewModel, aor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallsScreen(navController: NavController, viewModel: ViewModel, aor: String) {

    val account = Account.ofAor(aor)!!

    val callHistory: MutableState<List<CallRow>> = remember { mutableStateOf(emptyList()) }
    var isHistoryLoaded by remember { mutableStateOf(false) }

    var refreshTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(aor, refreshTrigger) {
        callHistory.value = loadCallHistory(aor)
        isHistoryLoaded = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME)
                refreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = true) {
        account.missedCalls = false
        navController.navigateUp()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                TopAppBar(navController, account, callHistory)
            }
        },
        content = { contentPadding ->
            if (isHistoryLoaded)
                CallsContent(
                    LocalContext.current,
                    navController,
                    viewModel,
                    contentPadding,
                    account,
                    callHistory
                )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(navController: NavController, account: Account, callHistory: MutableState<List<CallRow>>) {

    var expanded by remember { mutableStateOf(false) }

    val delete = stringResource(R.string.delete)
    val disable = stringResource(R.string.disable_history)
    val enable = stringResource(R.string.enable_history)
    val blocked = stringResource(R.string.blocked)

    val showDialog = remember { mutableStateOf(false) }
    val lastAction = remember { mutableStateOf({}) }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = String.format(stringResource(R.string.delete_history_alert), account.text()),
        firstButtonText = stringResource(R.string.cancel),
        lastButtonText = stringResource(R.string.delete),
        onLastClicked = lastAction.value,
    )

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.call_history),
                fontWeight = FontWeight.Bold
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            IconButton(
                onClick = {
                    account.missedCalls = false
                    navController.navigateUp()
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            IconButton(
                onClick = { expanded = !expanded }
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Menu",
                )
            }
            CustomElements.DropdownMenu(
                expanded,
                { expanded = false },
                if (account.callHistory) listOf(disable, delete, blocked) else listOf(enable),
                onItemClick = { selectedItem ->
                    expanded = false
                    when (selectedItem) {
                        delete -> {
                            lastAction.value = {
                                CallHistoryNew.clear(account.aor)
                                callHistory.value = emptyList()
                                Blocked.clear(account.aor)
                            }
                            showDialog.value = true
                        }
                        disable, enable -> {
                            account.callHistory = !account.callHistory
                            Account.saveAccounts()
                        }
                        blocked -> {
                            navController.navigate("blocked/invite/${account.aor}")
                        }
                    }
                }
            )
        }
    )
}

@Composable
private fun CallsContent(
    ctx: Context,
    navController: NavController,
    viewModel: ViewModel,
    contentPadding: PaddingValues,
    account: Account,
    callHistory: MutableState<List<CallRow>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Account(account)
        Calls(ctx, navController, viewModel, account, callHistory)
    }
}

@Composable
private fun Account(account: Account) {
    Text(
        text = stringResource(R.string.account) + " " + account.text(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Calls(
    ctx: Context,
    navController: NavController,
    viewModel: ViewModel,
    account: Account,
    callHistory: MutableState<List<CallRow>>
) {

    val showDialog = remember { mutableStateOf(false) }
    val message = remember { mutableStateOf("") }
    val secondButtonText = remember { mutableStateOf("") }
    val secondAction = remember { mutableStateOf({}) }
    val lastButtonText = remember { mutableStateOf("") }
    val lastAction = remember { mutableStateOf({}) }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = message.value,
        firstButtonText = stringResource(R.string.cancel),
        secondButtonText = secondButtonText.value,
        onSecondClicked = secondAction.value,
        lastButtonText = lastButtonText.value,
        onLastClicked = lastAction.value,
    )

    val lazyListState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp)
            .verticalScrollbar(state = lazyListState)
            .background(MaterialTheme.colorScheme.background),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = callHistory.value, key = { callRow -> callRow.stopTime }) { callRow ->
            val peerUri = callRow.peerUri
            var recordings = false
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                val aor = account.aor
                                val ua = UserAgent.ofAor(aor)
                                val intent = Intent(ctx, MainActivity::class.java)
                                if (ua != null) {
                                    intent.putExtra("uap", ua.uap)
                                    intent.putExtra("peer", peerUri)
                                }
                                else
                                    Log.w(TAG, "onClickListener did not find UA for $aor")
                                val peerName = Utils.friendlyUri(ctx, peerUri, account)
                                message.value = String.format(ctx.getString(R.string.contact_action_question), peerName)
                                secondButtonText.value = ctx.getString(R.string.call)
                                secondAction.value = {
                                    if (ua != null) {
                                        handleIntent(ctx, viewModel, intent, "call")
                                        navController.navigate("main") {
                                            popUpTo("main")
                                            launchSingleTop = true
                                        }
                                    }
                                }
                                lastButtonText.value = ctx.getString(R.string.send_message)
                                lastAction.value = {
                                    if (ua != null) {
                                        handleIntent(ctx, viewModel, intent, "message")
                                        navController.navigateUp()
                                    }
                                }
                                showDialog.value = true
                            },
                            onLongClick = {
                                val peerName = Utils.friendlyUri(ctx, peerUri, account)
                                val callText: String = if (callRow.details.size > 1)
                                    ctx.getString(R.string.calls_calls)
                                else
                                    ctx.getString(R.string.calls_call)
                                val contactExists = Contact.nameExists(peerName, BaresipService.contacts, false)
                                if (contactExists) {
                                    message.value = String.format(
                                        ctx.getString(R.string.calls_delete_question),
                                        peerName, callText
                                    )
                                    secondButtonText.value = ""
                                    lastButtonText.value = ctx.getString(R.string.delete)
                                    lastAction.value = {
                                        removeFromHistory(callHistory, callRow)
                                    }
                                }
                                else {
                                    message.value = String.format(
                                        ctx.getString(R.string.calls_add_delete_question),
                                        peerName, callText
                                    )
                                    secondButtonText.value = ctx.getString(R.string.add_contact)
                                    secondAction.value = {
                                        navController.navigate("baresip_contact/$peerUri/new")
                                    }
                                    lastButtonText.value = ctx.getString(R.string.delete)
                                    lastAction.value = {
                                        removeFromHistory(callHistory, callRow)
                                    }
                                }
                                showDialog.value = true
                            }
                        )
                    ) {
                        when (val contact = Contact.findContact(peerUri)) {
                            is Contact.BaresipContact -> {
                                val avatarImage = contact.avatarImage
                                if (avatarImage != null)
                                    CustomElements.ImageAvatar(avatarImage)
                                else
                                    CustomElements.TextAvatar(contact.name, contact.color)
                            }
                            is Contact.AndroidContact -> {
                                val thumbNailUri = contact.thumbnailUri
                                if (thumbNailUri != null)
                                    AsyncImage(
                                        model = thumbNailUri,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape),
                                    )
                                else
                                    CustomElements.TextAvatar(contact.name, contact.color)
                            }
                            null -> {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.size(36.dp).scale(1.2f),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        var count = 1
                        for (d in callRow.details) {
                            if (d.recording.isNotEmpty() && d.recording[0] != "")
                                recordings = true
                            if (count > 3)
                                continue
                            Icon(
                                imageVector = if (callUp(d.direction))
                                    Icons.AutoMirrored.Filled.CallMade
                                else
                                    Icons.AutoMirrored.Filled.CallReceived,
                                modifier = Modifier.size(20.dp),
                                tint = colorResource(id = callTint(d.direction)),
                                contentDescription = "Direction"
                            )
                            count++
                        }
                        if (count > 3)
                            Text("...", color = MaterialTheme.colorScheme.onBackground)
                        Text(text = Utils.friendlyUri(ctx, peerUri, account),
                            modifier = Modifier.padding(start = 8.dp),
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = Utils.relativeTime(ctx, callRow.stopTime),
                    fontSize = 12.sp,
                    minLines = 2, maxLines = 2,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.End,
                    color = if (recordings)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clickable(onClick = {
                            viewModel.selectCallRow(callRow)
                            navController.navigate("call_details")
                        })
                )
            }
        }
    }
}

private fun loadCallHistory(aor: String): MutableList<CallRow> {
    val res = mutableListOf<CallRow>()
    for (i in BaresipService.callHistory.indices.reversed()) {
        val h = BaresipService.callHistory[i]
        if (h.aor == aor) {
            val direction: Int = if (h.direction == "in") {
                if (h.startTime != null) {
                    if (h.startTime != h.stopTime) CALL_DOWN_GREEN else CALL_DOWN_BLUE
                }
                else
                    if (h.rejected) CALL_DOWN_RED else CALL_MISSED_IN
            }
            else {
                if (h.startTime != null)
                    CALL_UP_GREEN
                else
                    if (h.rejected) CALL_UP_RED else CALL_MISSED_OUT
            }
            if (res.isNotEmpty() && res.last().peerUri == h.peerUri)
                res.last().details.add(CallRow.Details(
                    direction, h.startTime,
                    h.stopTime, h.recording.toList()
                ))
            else
                res.add(CallRow(h.aor, h.peerUri, direction, h.startTime, h.stopTime, h.recording.toList()))
        }
    }
    return res
}

private fun removeFromHistory(callHistory: MutableState<List<CallRow>>, callRow: CallRow) {
    for (details in callRow.details) {
        CallHistoryNew.deleteRecordingFiles(details.recording.toTypedArray())
        BaresipService.callHistory.removeAll {
            it.startTime == details.startTime && it.stopTime == details.stopTime
        }
    }
    CallHistoryNew.deleteRecordingFiles(callRow.recording.toTypedArray())
    val updatedList = callHistory.value.filterNot { it == callRow }
    callHistory.value = updatedList
    CallHistoryNew.save()
}

fun callUp(direction: Int): Boolean {
    return when (direction) {
        CALL_UP_GREEN, CALL_UP_RED, CALL_MISSED_OUT -> true
        else -> false
    }
}

fun callTint(direction: Int): Int {
    return when (direction) {
        CALL_UP_GREEN, CALL_DOWN_GREEN -> R.color.colorTrafficGreen
        CALL_UP_RED, CALL_DOWN_RED -> R.color.colorTrafficRed
        CALL_DOWN_BLUE -> R.color.colorPrimary
        else -> R.color.colorTrafficYellow
    }
}

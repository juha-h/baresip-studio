package com.tutpro.baresip.plus

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar

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
    LaunchedEffect(aor) {
        callHistory.value = loadCallHistory(aor)
        isHistoryLoaded = true
    }

    BackHandler(enabled = true) {
        account.missedCalls = false
        navController.popBackStack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = LocalCustomColors.current.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalCustomColors.current.background)
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

    val showDialog = remember { mutableStateOf(false) }
    val positiveAction = remember { mutableStateOf({}) }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = String.format(stringResource(R.string.delete_history_alert), account.text()),
        positiveButtonText = stringResource(R.string.delete),
        negativeButtonText = stringResource(R.string.cancel),
        onPositiveClicked = positiveAction.value,
    )

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.call_history),
                color = LocalCustomColors.current.light,
                fontWeight = FontWeight.Bold
            )
        },
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = LocalCustomColors.current.primary
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            IconButton(
                onClick = {
                    account.missedCalls = false
                    navController.popBackStack()
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = LocalCustomColors.current.light
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
                    tint = LocalCustomColors.current.light
                )
            }
            CustomElements.DropdownMenu(
                expanded,
                { expanded = false },
                listOf(delete, if (account.callHistory) disable else enable),
                onItemClick = { selectedItem ->
                    expanded = false
                    when (selectedItem) {
                        delete -> {
                            positiveAction.value = {
                                CallHistoryNew.clear(account.aor)
                                callHistory.value = emptyList()
                            }
                            showDialog.value = true
                        }

                        disable, enable -> {
                            account.callHistory = !account.callHistory
                            Account.saveAccounts()
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = LocalCustomColors.current.itemText,
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
    val positiveButtonText = remember { mutableStateOf("") }
    val positiveAction = remember { mutableStateOf({}) }
    val neutralButtonText = remember { mutableStateOf("") }
    val neutralAction = remember { mutableStateOf({}) }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = message.value,
        positiveButtonText = positiveButtonText.value,
        onPositiveClicked = positiveAction.value,
        neutralButtonText = neutralButtonText.value,
        onNeutralClicked = neutralAction.value,
        negativeButtonText = stringResource(R.string.cancel)
    )

    val lazyListState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp)
            .verticalScrollbar(
                state = lazyListState,
                width = 4.dp,
                color = LocalCustomColors.current.gray
            )
            .background(LocalCustomColors.current.background),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = callHistory.value, key = { callRow -> callRow.stopTime }) { callRow ->
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
                                val peerUri = callRow.peerUri
                                val intent = Intent(ctx, MainActivity::class.java)
                                if (ua != null) {
                                    intent.putExtra("uap", ua.uap)
                                    intent.putExtra("peer", peerUri)
                                }
                                else
                                    Log.w(TAG, "onClickListener did not find UA for $aor")
                                val peerName = Utils.friendlyUri(ctx, peerUri, account)
                                message.value = String.format(ctx.getString(R.string.contact_action_question), peerName)
                                positiveButtonText.value = ctx.getString(R.string.call)
                                positiveAction.value = {
                                    if (ua != null) {
                                        handleIntent(ctx, viewModel, intent, "call")
                                        navController.popBackStack()
                                    }
                                }
                                neutralButtonText.value = ctx.getString(R.string.send_message)
                                neutralAction.value = {
                                    if (ua != null) {
                                        handleIntent(ctx, viewModel, intent, "message")
                                        navController.popBackStack()
                                    }
                                }
                                showDialog.value = true
                            },
                            onLongClick = {
                                val peerUri = callRow.peerUri
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
                                    positiveButtonText.value = ctx.getString(R.string.delete)
                                    positiveAction.value = {
                                        removeFromHistory(callHistory, callRow)
                                    }
                                    neutralButtonText.value = ""
                                }
                                else {
                                    message.value = String.format(
                                        ctx.getString(R.string.calls_add_delete_question),
                                        peerName, callText
                                    )
                                    positiveButtonText.value = ctx.getString(R.string.add_contact)
                                    positiveAction.value = {
                                        navController.navigate("baresip_contact/${callRow.peerUri}/new")
                                    }
                                    neutralButtonText.value = ctx.getString(R.string.delete)
                                    neutralAction.value = {
                                        removeFromHistory(callHistory, callRow)
                                    }
                                }
                                showDialog.value = true
                            }
                        )
                    ) {
                        val uri = callRow.peerUri
                        when (val contact = Contact.findContact(uri)) {
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
                                val avatarImage = BitmapFactory
                                    .decodeResource(ctx.resources, R.drawable.person_image)
                                CustomElements.ImageAvatar(avatarImage)
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        var count = 1
                        for (d in callRow.details) {
                            if (d.recording[0] != "")
                                recordings = true
                            if (count > 3)
                                continue
                            Image(painterResource(d.direction), "Direction")
                            count++
                        }
                        if (count > 3)
                            Text("...", color = LocalCustomColors.current.itemText)
                        Text(text = Utils.friendlyUri(ctx, callRow.peerUri, account),
                            modifier = Modifier.padding(start = 8.dp),
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = LocalCustomColors.current.itemText
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
                        LocalCustomColors.current.accent
                    else
                        LocalCustomColors.current.itemText,
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
                    if (h.startTime != h.stopTime)
                        R.drawable.call_down_green
                    else
                        R.drawable.call_down_blue
                } else {
                    if (h.rejected)
                        R.drawable.call_down_red
                    else
                        R.drawable.call_missed_in
                }
            } else {
                if (h.startTime != null) {
                    R.drawable.call_up_green
                } else {
                    if (h.rejected)
                        R.drawable.call_up_red
                    else
                        R.drawable.call_missed_out
                }
            }
            if (res.isNotEmpty() && res.last().peerUri == h.peerUri)
                res.last().details.add(CallRow.Details(
                    direction, h.startTime,
                    h.stopTime, h.recording
                ))
            else
                res.add(CallRow(h.aor, h.peerUri, direction, h.startTime, h.stopTime, h.recording))
        }
    }
    return res
}

private fun removeFromHistory(callHistory: MutableState<List<CallRow>>, callRow: CallRow) {
    for (details in callRow.details) {
        if (details.recording[0] != "")
            CallHistoryNew.deleteRecording(details.recording)
        BaresipService.callHistory.removeAll {
            it.startTime == details.startTime && it.stopTime == details.stopTime
        }
    }
    CallHistoryNew.deleteRecording(callRow.recording)
    val updatedList = callHistory.value.filterNot { it == callRow }
    callHistory.value = updatedList
    CallHistoryNew.save()
}


package com.tutpro.baresip

import android.content.Context
import android.text.format.DateUtils.isToday
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.tutpro.baresip.BaresipService.Companion.contactNames
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.DropdownMenu
import com.tutpro.baresip.CustomElements.SelectableAlertDialog
import com.tutpro.baresip.CustomElements.verticalScrollbar
import java.text.DateFormat
import java.util.GregorianCalendar

fun NavGraphBuilder.chatsScreenRoute(navController: NavController) {
    composable(
        route = "chats/{aor}",
        arguments = listOf(navArgument("aor") { type = NavType.StringType })
    ) { backStackEntry ->
        val aor = backStackEntry.arguments?.getString("aor")!!
        ChatsScreen(navController, aor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsScreen(navController: NavController, aor: String) {

    val account = Account.ofAor(aor)!!
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uaMessages: MutableState<List<Message>> = remember { mutableStateOf(emptyList()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d(TAG, "Resumed to ChatsScreen for AOR: $aor")
                uaMessages.value = loadMessages(account)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var areMessagesLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(aor) {
        uaMessages.value = loadMessages(account)
        areMessagesLoaded = true
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
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                TopAppBar(navController, account, uaMessages)
            }
        },
        bottomBar = { NewChatPeer(ctx, navController, account) },
        content = { contentPadding ->
            if (areMessagesLoaded)
                ChatsContent(LocalContext.current, navController, contentPadding, account, uaMessages)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
    navController: NavController,
    account: Account,
    uaMessages: MutableState<List<Message>>
) {

    var menuExpanded by remember { mutableStateOf(false) }
    val delete = stringResource(R.string.delete)
    val blocked = stringResource(R.string.blocked)
    val showDialog = remember { mutableStateOf(false) }
    val positiveAction = remember { mutableStateOf({}) }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = String.format(stringResource(R.string.delete_chats_alert), account.text()),
        positiveButtonText = stringResource(R.string.delete),
        onPositiveClicked = positiveAction.value,
        negativeButtonText = stringResource(R.string.cancel),
    )

    TopAppBar(
        title = { Text(text = stringResource(R.string.chats), fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        actions = {
            IconButton(
                onClick = { menuExpanded = !menuExpanded }
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Menu",
                )
            }
            DropdownMenu (
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                items = listOf(delete, blocked),
                onItemClick = { selectedItem ->
                    menuExpanded = false
                    when (selectedItem) {
                        delete -> {
                            positiveAction.value = {
                                Message.clearMessagesOfAor(account.aor)
                                Message.save()
                                uaMessages.value = listOf()
                                account.unreadMessages = false
                            }
                            showDialog.value = true
                        }
                        blocked -> {
                            navController.navigate("blocked/message/${account.aor}")
                        }
                    }
                }
            )
        },
    )
}

@Composable
private fun ChatsContent(
    ctx: Context,
    navController: NavController,
    contentPadding: PaddingValues,
    account: Account,
    uaMessages: MutableState<List<Message>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.Top
    ) {
        Account(account)
        Chats(ctx, navController, account, uaMessages)
    }
}

@Composable
private fun Account(account: Account) {
    Text(
        text = stringResource(R.string.account) + " " + account.text(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun Chats(
    ctx: Context,
    navController: NavController,
    account: Account,
    uaMessages: MutableState<List<Message>>
) {
    val aor = account.aor

    val showDialog = remember { mutableStateOf(false) }
    val dialogMessage = remember { mutableStateOf("") }
    val positiveButtonText = remember { mutableStateOf("") }
    val positiveAction = remember { mutableStateOf({}) }
    val neutralButtonText = remember { mutableStateOf("") }
    val neutralAction = remember { mutableStateOf({}) }

    if (showDialog.value)
        AlertDialog(
            showDialog = showDialog,
            title = stringResource(R.string.confirmation),
            message = dialogMessage.value,
            positiveButtonText = positiveButtonText.value,
            onPositiveClicked = positiveAction.value,
            neutralButtonText = neutralButtonText.value,
            onNeutralClicked = neutralAction.value,
            negativeButtonText = stringResource(R.string.cancel)
        )

    val lazyListState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp)
            .verticalScrollbar(
                state = lazyListState,
                width = 4.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            .background(MaterialTheme.colorScheme.background),
        reverseLayout = true,
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = uaMessages.value, key = { message -> message.timeStamp }) { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (val contact = Contact.findContact(message.peerUri)) {
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
                                modifier = Modifier.size(36.dp).clip(CircleShape),
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
                Spacer(modifier = Modifier.width(6.dp))
                val buttonShape = if (message.direction == MESSAGE_DOWN) {
                    RoundedCornerShape(50.dp, 20.dp, 20.dp, 10.dp)
                } else {
                    RoundedCornerShape(20.dp, 10.dp, 50.dp, 20.dp)
                }
                val borderStroke = if (account.unreadMessages && Message.unreadMessagesFromPeer(aor, message.peerUri)) {
                    BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.error)
                } else {
                    null
                }
                CustomElements.Button(
                    onClick = {
                        navController.navigate("chat/${aor}/${message.peerUri}")
                    },
                    onLongClick = {
                        val peer = Utils.friendlyUri(ctx, message.peerUri, account)
                        val contactExists =
                            Contact.nameExists(peer, BaresipService.contacts, false)
                        if (contactExists) {
                            dialogMessage.value = String.format(
                                ctx.getString(R.string.short_chat_question),
                                peer
                            )
                            positiveButtonText.value = ctx.getString(R.string.delete)
                            positiveAction.value = {
                                Message.deleteAorPeerMessages(aor, message.peerUri)
                                uaMessages.value = loadMessages(account)
                            }
                            neutralButtonText.value = ""
                        } else {
                            dialogMessage.value =
                                String.format(ctx.getString(R.string.long_chat_question), peer)
                            positiveButtonText.value = ctx.getString(R.string.add_contact)
                            positiveAction.value = {
                                navController.navigate("baresip_contact/${message.peerUri}/new")
                            }
                            neutralButtonText.value = ctx.getString(R.string.delete)
                            neutralAction.value = {
                                Message.deleteAorPeerMessages(aor, message.peerUri)
                            }
                        }
                        showDialog.value = true
                    },
                    modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(end = 6.dp),
                    shape = buttonShape,
                    border = borderStroke,
                    color = if (message.direction == MESSAGE_DOWN)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                ) {
                    val peer = Utils.friendlyUri(ctx, message.peerUri, account)
                    val cal = GregorianCalendar()
                    cal.timeInMillis = message.timeStamp
                    val fmt: DateFormat = if (isToday(message.timeStamp))
                        DateFormat.getTimeInstance(DateFormat.SHORT)
                    else
                        DateFormat.getDateInstance(DateFormat.SHORT)
                    val info = fmt.format(cal.time)
                    Column {
                        val textColor = if (message.direction == MESSAGE_DOWN)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                        Row {
                            Text(text = peer, color = textColor, fontSize = 12.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = info, color = textColor, fontSize = 12.sp)
                        }
                        Row {
                            BasicText(
                                text = message.message,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = textColor,
                                    fontWeight = if (message.direction == MESSAGE_DOWN && message.new)
                                        FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewChatPeer(ctx: Context, navController: NavController, account: Account) {

    val alertTitle = remember { mutableStateOf("") }
    val alertMessage = remember { mutableStateOf("") }
    val showAlert = remember { mutableStateOf(false) }

    fun makeChat(ctx: Context, navController: NavController, account: Account, chatPeer: String) {
        val peerUri = if (Utils.isTelNumber(chatPeer))
            "tel:$chatPeer"
        else
            chatPeer
        val uri = if (Utils.isTelUri(peerUri)) {
            if (account.telProvider == "") {
                alertTitle.value = ctx.getString(R.string.notice)
                alertMessage.value =
                    String.format(ctx.getString(R.string.no_telephony_provider), account.aor)
                showAlert.value = true
                ""
            } else
                Utils.telToSip(peerUri, account)
        } else
            Utils.uriComplete(peerUri, account.aor)
        if (alertMessage.value.isEmpty()) {
            if (!Utils.checkUri(uri)) {
                alertTitle.value = ctx.getString(R.string.notice)
                alertMessage.value = String.format(ctx.getString(R.string.invalid_sip_or_tel_uri), uri)
                showAlert.value = true
            }
            else
                navController.navigate("chat/${account.aor}/${uri}")
        }
    }

    if (showAlert.value) {
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            positiveButtonText = stringResource(R.string.ok),
        )
    }

    val showDialog = remember { mutableStateOf(false) }
    val items = remember { mutableStateOf(listOf<String>()) }
    val itemAction = remember { mutableStateOf<(Int) -> Unit>({ _ -> run {} }) }

    SelectableAlertDialog(
        openDialog = showDialog,
        title = stringResource(R.string.choose_destination_uri),
        items = items.value,
        onItemClicked = itemAction.value,
        neutralButtonText = stringResource(R.string.cancel),
        onNeutralClicked = {}
    )

    val suggestions by remember { contactNames }
    // 1. Change state to hold AnnotatedStrings
    var filteredSuggestions by remember { mutableStateOf<List<AnnotatedString>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var newPeer by remember { mutableStateOf("") }
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.weight(1f)
        ) {
            if (showSuggestions && filteredSuggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .shadow(8.dp, RoundedCornerShape(8.dp))
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .animateContentSize()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .verticalScrollbar(
                                    state = lazyListState,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                ),
                            horizontalAlignment = Alignment.Start,
                            state = lazyListState
                        ) {
                            items(
                                // 3. Update key and clickable logic
                                items = filteredSuggestions,
                                key = { suggestion -> suggestion.toString() }
                            ) { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            newPeer = suggestion.toString()
                                            showSuggestions = false
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = suggestion, // Text composable accepts AnnotatedString
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = newPeer,
                placeholder = { Text(stringResource(R.string.new_chat_peer)) },
                onValueChange = {
                    newPeer = it
                    showSuggestions = newPeer.length > 1
                    // 2. Update filtering and mapping logic
                    filteredSuggestions = if (it.isEmpty()) {
                        emptyList()
                    } else {
                        val normalizedInput = Utils.unaccent(it)
                        suggestions
                            .filter { suggestion ->
                                it.length > 1 &&
                                    Utils.unaccent(suggestion).contains(normalizedInput, ignoreCase = true)
                            }
                            .map { suggestion ->
                                Utils.buildAnnotatedStringWithHighlight(suggestion, it)
                            }
                    }
                },
                modifier = Modifier.padding(end = 6.dp).fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (newPeer.isNotEmpty())
                        Icon(
                            Icons.Outlined.Clear,
                            contentDescription = null,
                            modifier = Modifier.clickable {
                                if (showSuggestions)
                                    showSuggestions = false
                                newPeer = ""
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                },
                label = { Text(stringResource(R.string.new_chat_peer)) },
                textStyle = TextStyle(fontSize = 18.sp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                )
            )
        }
        Spacer(Modifier.width(4.dp))
        SmallFloatingActionButton(
            modifier = Modifier.padding(end = 4.dp).offset(y = 2.dp),
            onClick = {
                showSuggestions = false
                val peerText = newPeer.trim()
                if (peerText.isNotEmpty()) {
                    val uris = Contact.contactUris(peerText)
                    if (uris.isEmpty())
                        makeChat(ctx, navController, account, peerText)
                    else if (uris.size == 1)
                        makeChat(ctx, navController, account, uris[0])
                    else {
                        items.value = uris
                        itemAction.value = { index ->
                            makeChat(ctx, navController, account, uris[index])
                        }
                        showDialog.value = true
                    }
                }
                newPeer = ""
                focusManager.clearFocus()
            },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                modifier = Modifier.size(36.dp),
                contentDescription = stringResource(R.string.add)
            )
        }
    }
}

private fun loadMessages(account: Account) : List<Message> {
    val res = mutableListOf<Message>()
    account.unreadMessages = false
    for (m in BaresipService.messages.reversed()) {
        if (m.aor != account.aor) continue
        var found = false
        for (r in res)
            if (r.peerUri == m.peerUri) {
                found = true
                break
            }
        if (!found) {
            res.add(0, m)
            if (m.new)
                account.unreadMessages = true
        }
    }
    return res.toList()
}
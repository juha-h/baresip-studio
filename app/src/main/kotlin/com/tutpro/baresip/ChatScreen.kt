package com.tutpro.baresip

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils.isToday
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.res.vectorResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tutpro.baresip.BaresipService.Companion.circleGreen
import com.tutpro.baresip.BaresipService.Companion.colorblind
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.verticalScrollbar
import kotlinx.coroutines.launch
import java.lang.String.format
import java.text.DateFormat
import java.util.GregorianCalendar

fun NavGraphBuilder.chatScreenRoute(navController: NavController, viewModel: ViewModel) {
    composable(
        route = "chat/{aor}/{peer}",
        arguments = listOf(
            navArgument("aor") { type = NavType.StringType },
            navArgument("peer") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val aor = backStackEntry.arguments?.getString("aor")!!
        val peerUri = backStackEntry.arguments?.getString("peer")!!
        ChatScreen(
            ctx = LocalContext.current,
            navController = navController,
            viewModel = viewModel,
            account = Account.ofAor(aor)!!,
            peerUri = peerUri
        )
    }
}

@Composable
private fun ChatScreen(
    ctx: Context,
    navController: NavController,
    viewModel: ViewModel,
    account: Account,
    peerUri: String
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val aor = account.aor

    var chatMessages by remember(aor, peerUri) { mutableStateOf<List<Message>>(emptyList()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d(TAG, "Resumed to ChatScreen for AOR: $aor peer $peerUri")
                chatMessages = loadPeerMessages(aor, peerUri)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var areMessagesLoaded by remember(aor, peerUri) { mutableStateOf(false) }

    val reloadMessages = {
        Log.d(TAG, "Reloading messages for $aor peer $peerUri")
        chatMessages = loadPeerMessages(aor, peerUri)
        if (!areMessagesLoaded)
            areMessagesLoaded = true
    }

    val addMessage = { newMessage: Message ->
        chatMessages = chatMessages + newMessage
    }

    DisposableEffect(key1 = lifecycleOwner, key2 = account.aor, key3 = peerUri) {
        val messagesObserver = Observer<Long> { timestamp ->
            Log.d(TAG, "Message update received via LiveData for $peerUri, timestamp: $timestamp")
            reloadMessages()
        }
        reloadMessages() // Initial load
        Log.d(TAG, "Observing message updates for $peerUri")
        BaresipService.messageUpdate.observe(lifecycleOwner, messagesObserver)
        onDispose {
            Log.d(TAG, "Removing message observer for $peerUri")
            BaresipService.messageUpdate.removeObserver(messagesObserver)
        }
    }

    BackHandler(enabled = true) {
        backAction(navController, account, peerUri)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                Spacer(Modifier.statusBarsPadding())
                TopAppBar(ctx, navController, viewModel, account, peerUri)
            }
        },
        bottomBar = {
            NewMessage(
                ctx = ctx,
                viewModel = viewModel,
                account = account,
                peerUri = peerUri,
                addMessage = addMessage
            )
        },
        content = { contentPadding ->
            if (areMessagesLoaded)
                ChatContent(
                    ctx,
                    navController,
                    contentPadding,
                    account, peerUri,
                    chatMessages,
                    reloadMessages
                )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
    ctx: Context,
    navController: NavController,
    viewModel: ViewModel,
    account: Account,
    peerUri: String
) {
    val aor = account.aor
    TopAppBar(
        title = {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                val contact = Contact.findContact(peerUri)
                if (contact != null) {
                    when (contact) {
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
                                coil.compose.AsyncImage(
                                    model = thumbNailUri,
                                    contentDescription = "Avatar",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                )
                            else
                                CustomElements.TextAvatar(contact.name, contact.color)
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                }
                Text(
                    text = Utils.friendlyUri(ctx, peerUri, account).uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = Color.White,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        navigationIcon = {
            IconButton(onClick = { backAction(navController, account, peerUri) }) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        actions = {
            IconButton(
                onClick = {
                    val ua = UserAgent.ofAor(account.aor)
                    if (ua != null) {
                        val callIntent = Intent(ctx, MainActivity::class.java)
                            .putExtra("uap", ua.uap)
                            .putExtra("peer", peerUri)
                        handleIntent(ctx, viewModel, callIntent, "call")
                    }
                    else
                        Log.w(TAG, "Call button onClick listener did not find UA for $aor")
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Call,
                    contentDescription = "Call",
                )
            }
        }
    )
}

@Composable
private fun ChatContent(
    ctx: Context,
    navController: NavController,
    contentPadding: PaddingValues,
    account: Account,
    peerUri: String,
    messages: List<Message>,
    onMessageDeleted: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(contentPadding).background(Color(0xFFF9FAFB)),
        verticalArrangement = Arrangement.Bottom
    ) {
        Account(ctx, account)
        Messages(ctx, navController, account, peerUri, messages, onMessageDeleted)
    }
}

@Composable
private fun Account(ctx: Context, account: Account) {
    Text(
        text = ctx.getString(R.string.account) + " " + account.text(),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = Color.DarkGray,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ColumnScope.Messages(
    ctx: Context,
    navController: NavController,
    account: Account,
    peerUri: String,
    messages: List<Message>,
    onMessageDeleted: () -> Unit
) {
    val peerName = Utils.friendlyUri(ctx, peerUri, account)

    val showDialog = remember { mutableStateOf(false) }
    val dialogMessage = remember { mutableStateOf("") }
    val secondButtonText = remember { mutableStateOf("") }
    val secondAction = remember { mutableStateOf({}) }
    val lastButtonText = remember { mutableStateOf("") }
    val lastAction = remember { mutableStateOf({}) }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = dialogMessage.value,
        firstButtonText = stringResource(R.string.cancel),
        secondButtonText = secondButtonText.value,
        onSecondClicked = secondAction.value,
        lastButtonText = lastButtonText.value,
        onLastClicked = lastAction.value,
    )

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages) {
        if (messages.isNotEmpty())
            coroutineScope.launch { lazyListState.scrollToItem(0) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 16.dp)
            .verticalScrollbar(state = lazyListState),
        reverseLayout = true,
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = messages, key = { message -> message.timeStamp }) { message ->
            val down = message.direction == MESSAGE_DOWN
            var info: String
            val cal = GregorianCalendar()
            cal.timeInMillis = message.timeStamp
            val fmt: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
            info = fmt.format(cal.time)

            if (message.direction == MESSAGE_UP_FAIL) {
                info = if (message.responseCode != 0)
                    "$info - ${stringResource(R.string.message_failed)}: " + "${message.responseCode} ${message.responseReason}"
                else
                    "$info - ${stringResource(R.string.sending_failed)}"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (down) Arrangement.Start else Arrangement.End
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .wrapContentHeight()
                        .padding(top = 4.dp, bottom = 4.dp)
                        .clip(if (down) RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp) else RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp))
                        .background(if (down) Color.White else Color(0xFFE1F0FF))
                        .clickable {
                            if (Contact.findContact(peerUri) == null) {
                                dialogMessage.value = String.format(
                                    ctx.getString(R.string.long_message_question),
                                    peerUri
                                )
                                secondButtonText.value = ctx.getString(R.string.add_contact)
                                secondAction.value = { navController.navigate("contact/$peerUri/new") }
                                lastButtonText.value = ctx.getString(R.string.delete)
                                lastAction.value = {
                                    message.delete()
                                    onMessageDeleted()
                                }
                            } else {
                                dialogMessage.value = ctx.getString(R.string.short_message_question)
                                secondButtonText.value = ""
                                lastButtonText.value = ctx.getString(R.string.delete)
                                lastAction.value = {
                                    message.delete()
                                    onMessageDeleted()
                                }
                            }
                            showDialog.value = true
                        }
                        .padding(12.dp)
                ) {
                    Column {
                        SelectionContainer {
                            Text(
                                text = message.message,
                                color = Color.Black,
                                fontSize = 16.sp,
                                fontWeight = if (down && message.new) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Row(modifier = Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = info, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
        
        // Today Badge at the top of the messages (end of reversed list)
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFE5E7EB))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(text = "Today", fontSize = 12.sp, color = Color.Black)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewMessage(
    ctx: Context,
    viewModel: ViewModel,
    account: Account,
    peerUri: String,
    addMessage: (message: Message) -> Unit
) {

    val aor = account.aor
    val ua = UserAgent.ofAor(aor)!!

    val newMessage = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(viewModel.getAorPeerMessage(aor, peerUri)))
    }

    var textFieldLoaded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val showDialog = remember { mutableStateOf(false) }
    val dialogMessage = remember { mutableStateOf("") }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.notice),
        message = dialogMessage.value,
        lastButtonText = stringResource(R.string.ok),
    )

    Row(modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFFF9FAFB))
        .navigationBarsPadding()
        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        OutlinedTextField(
            value = newMessage.value,
            placeholder = { Text("Type a message", color = Color.Gray) },
            onValueChange = {
                newMessage.value = it
                viewModel.updateAorPeerMessage(aor, peerUri, it.text)
            },
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .focusRequester(focusRequester)
                .onGloballyPositioned {
                    if (!textFieldLoaded)
                        textFieldLoaded = true
                },
            shape = RoundedCornerShape(50),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color.LightGray,
                unfocusedBorderColor = Color.LightGray,
                cursorColor = Color.Black
            ),
            leadingIcon = {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_emoji_svg),
                    contentDescription = "Emoji",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp).padding(start = 4.dp).clickable {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        val msgText = newMessage.value.text
                        if (msgText.isNotEmpty()) {
                            keyboardController?.hide()
                            val time = System.currentTimeMillis()
                            val msg = Message(
                                aor,
                                peerUri,
                                msgText,
                                time,
                                MESSAGE_UP_WAIT,
                                0,
                                "",
                                true
                            )
                            msg.add()
                            var msgUri = ""
                            addMessage(msg)
                            if (ua.account.isMobile) {
                                if (ua.status != circleGreen.getValue(colorblind)) {
                                    dialogMessage.value = ctx.getString(R.string.airplane_mode)
                                    showDialog.value = true
                                }
                                else {
                                    val destination = Utils.uriUserPart(peerUri)
                                    if (Utils.sendSms(ctx, destination, msgText)) {
                                        msg.direction = MESSAGE_UP
                                        newMessage.value = TextFieldValue("")
                                        viewModel.updateAorPeerMessage(aor, peerUri, "")
                                        keyboardController?.hide()
                                    }
                                    else {
                                        Toast.makeText(
                                            ctx, "${ctx.getString(R.string.message_failed)}!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        msg.direction = MESSAGE_UP_FAIL
                                        msg.responseReason = ctx.getString(R.string.message_failed)
                                    }
                                }
                            }
                            else {
                                if (Utils.isTelUri(peerUri)) {
                                    if (ua.account.telProvider == "") {
                                        dialogMessage.value = String.format(
                                            ctx.getString(R.string.no_telephony_provider),
                                            Utils.plainAor(aor)
                                        )
                                        showDialog.value = true
                                    }
                                    else
                                        msgUri = Utils.telToSip(peerUri, ua.account)
                                }
                                else
                                    msgUri = peerUri
                                if (msgUri != "") {
                                    if (Api.message_send(ua.uap, msgUri, msgText, time.toString()) != 0) {
                                        Toast.makeText(
                                            ctx, "${ctx.getString(R.string.message_failed)}!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        msg.direction = MESSAGE_UP_FAIL
                                        msg.responseReason = ctx.getString(R.string.message_failed)
                                    }
                                    else {
                                        newMessage.value = TextFieldValue("")
                                        viewModel.updateAorPeerMessage(aor, peerUri, "")
                                        keyboardController?.hide()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_send_message_svg),
                        contentDescription = "Send",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp).padding(end = 4.dp)
                    )
                }
            },
            singleLine = false,
            textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = true
            )
        )
        LaunchedEffect(Unit) {
            if (newMessage.value.text.isNotEmpty())
                focusRequester.requestFocus()
        }
    }
}

private fun backAction(navController: NavController, account: Account, peerUri: String) {
    val aor = account.aor
    Message.updateMessagesFromPearRead(aor, peerUri)
    account.unreadMessages = Message.unreadMessages(aor)
    navController.navigateUp()
}

private fun loadPeerMessages(aor: String, peerUri: String): List<Message> {
    val res = mutableListOf<Message>()
    for (m in BaresipService.messages.reversed())
        if ((m.aor == aor) && (m.peerUri == peerUri)) res.add(m)
    return res
}

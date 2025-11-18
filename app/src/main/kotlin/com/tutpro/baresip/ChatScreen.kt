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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var areMessagesLoaded by remember(aor, peerUri) {
        mutableStateOf(false)
    }

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
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                TopAppBar(ctx, navController, viewModel, account, peerUri)
            }
        },
        bottomBar = {
            NewMessage(
                ctx = ctx,
                viewModel,
                account = account,
                peerUri = peerUri,
                addMessage = addMessage
            )
        },
        content = { contentPadding ->
            if (areMessagesLoaded)
                ChatContent(ctx,
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
            Text(
                text = format(ctx.getString(R.string.chat_with), Utils.friendlyUri(ctx, peerUri, account)),
                fontSize = 22.sp,
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
            IconButton(onClick = { backAction(navController, account, peerUri) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        actions = {
            IconButton(
                onClick = {
                    val ua = UserAgent.ofAor(account.aor)
                    if (ua != null) {
                        val intent = Intent(ctx, MainActivity::class.java)
                        intent.putExtra("uap", ua.uap)
                        intent.putExtra("peer", peerUri)
                        handleIntent(ctx, viewModel, intent, "call")
                        navController.navigate("main") {
                            popUpTo("main")
                            launchSingleTop = true
                        }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.Bottom
    ) {
        Account(ctx, account)
        Spacer(modifier = Modifier.weight(1f))
        Messages(ctx, navController, account, peerUri, messages, onMessageDeleted)
    }
}

@Composable
private fun Account(ctx: Context, account: Account) {
    Text(
        text = ctx.getString(R.string.account) + " " + account.text(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun Messages(
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
    val positiveButtonText = remember { mutableStateOf("") }
    val positiveAction = remember { mutableStateOf({}) }
    val neutralButtonText = remember { mutableStateOf("") }
    val neutralAction = remember { mutableStateOf({}) }

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
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages) {
        // Scroll to the bottom when new messages are added
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                lazyListState.scrollToItem(0)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 2.dp)
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
        items(items = messages, key = { message -> message.timeStamp }) { message ->
            val down = message.direction == MESSAGE_DOWN
            val sender: String = if (down)
                peerName
            else if (BaresipService.uas.value.size == 1)
                stringResource(R.string.you)
            else
                account.text()
            var info: String
            val cal = GregorianCalendar()
            cal.timeInMillis = message.timeStamp
            val fmt: DateFormat = if (isToday(message.timeStamp))
                DateFormat.getTimeInstance(DateFormat.SHORT)
            else
                DateFormat.getDateInstance(DateFormat.SHORT)
            info = fmt.format(cal.time)
            if (info.length < 6) info = "${stringResource(R.string.today)} $info"
            if (message.direction == MESSAGE_UP_FAIL) {
                info = if (message.responseCode != 0)
                    "$info - ${stringResource(R.string.message_failed)}: " + "${message.responseCode} ${message.responseReason}"
                else
                    "$info - ${stringResource(R.string.sending_failed)}"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Button(
                    onClick = {
                        if (Contact.findContact(peerUri) == null) {
                            dialogMessage.value = String.format(
                                ctx.getString(R.string.long_message_question),
                                peerUri
                            )
                            positiveButtonText.value = ctx.getString(R.string.add_contact)
                            positiveAction.value = {
                                navController.navigate("baresip_contact/$peerUri/new")
                            }
                            neutralButtonText.value = ctx.getString(R.string.delete)
                            neutralAction.value = {
                                message.delete()
                                onMessageDeleted()
                            }
                        } else {
                            dialogMessage.value = ctx.getString(R.string.short_message_question)
                            positiveButtonText.value = ctx.getString(R.string.delete)
                            positiveAction.value = {
                                message.delete()
                                onMessageDeleted()
                            }
                            neutralButtonText.value = ""
                        }
                        showDialog.value = true
                    },
                    shape = if (message.direction == MESSAGE_DOWN)
                        RoundedCornerShape(50.dp, 20.dp, 20.dp, 10.dp)
                    else
                        RoundedCornerShape(20.dp, 10.dp, 50.dp, 20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (message.direction == MESSAGE_DOWN)
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(
                            start = if (message.direction == MESSAGE_DOWN) 0.dp else 24.dp,
                            end = if (message.direction == MESSAGE_DOWN) 24.dp else 0.dp
                        )
                ) {
                    Column {
                        val textColor = if (message.direction == MESSAGE_DOWN)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                        Row {
                            Text(text = sender, fontSize = 12.sp, color = textColor)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = info, fontSize = 12.sp, color = textColor)
                        }
                        Row {
                            SelectionContainer {
                                Text(
                                    text = message.message,
                                    color = textColor,
                                    fontWeight = if (message.direction == MESSAGE_DOWN && message.new)
                                        FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
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
        positiveButtonText = stringResource(R.string.ok),
    )

    Row(modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        OutlinedTextField(
            value = newMessage.value,
            placeholder = { Text(stringResource(R.string.new_message)) },
            onValueChange = {
                newMessage.value = it
                viewModel.updateAorPeerMessage(aor, peerUri, it.text)
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
                .verticalScroll(rememberScrollState())
                .focusRequester(focusRequester)
                .onGloballyPositioned {
                    if (!textFieldLoaded)
                        textFieldLoaded = true
                },
            singleLine = false,
            trailingIcon = {
                if (newMessage.value.text.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier.clickable {
                            newMessage.value = TextFieldValue("")
                            viewModel.updateAorPeerMessage(aor, peerUri, "")
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            label = { Text(stringResource(R.string.new_message)) },
            textStyle = TextStyle(fontSize = 18.sp),
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
        SmallFloatingActionButton(
            modifier = Modifier.offset(y = 2.dp),
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
                    if (Utils.isTelUri(peerUri))
                        if (ua.account.telProvider == "") {
                            dialogMessage.value = String.format(
                                ctx.getString(R.string.no_telephony_provider),
                                Utils.plainAor(aor)
                            )
                            showDialog.value = true
                        }
                        else {
                            msgUri = Utils.telToSip(peerUri, ua.account)
                        }
                    else
                        msgUri = peerUri
                    if (msgUri != "")
                        if (Api.message_send(ua.uap,
                                msgUri,
                                msgText,
                                time.toString()
                        ) != 0) {
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
            },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                modifier = Modifier.size(28.dp),
                contentDescription = stringResource(R.string.add)
            )
        }
    }
}

private fun backAction(navController: NavController, account: Account, peerUri: String) {
    val aor = account.aor
    Message.updateMessagesFromPearRead(aor, peerUri)
    account.unreadMessages = Message.unreadMessages(aor)
    navController.popBackStack()
}

private fun loadPeerMessages(aor: String, peerUri: String): List<Message> {
    val res = mutableListOf<Message>()
    for (m in BaresipService.messages.reversed())
        if ((m.aor == aor) && (m.peerUri == peerUri)) res.add(m)
    return res
}

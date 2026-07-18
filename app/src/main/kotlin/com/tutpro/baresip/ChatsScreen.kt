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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
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

fun NavGraphBuilder.chatsScreenRoute(navController: NavController, viewModel: com.tutpro.baresip.ViewModel) {
    composable(
        route = "chats/{aor}",
        arguments = listOf(navArgument("aor") { type = NavType.StringType })
    ) { backStackEntry ->
        val aor = backStackEntry.arguments?.getString("aor")!!
        ChatsScreen(navController, aor, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsScreen(navController: NavController, aor: String, viewModel: com.tutpro.baresip.ViewModel) {

    val ctx = LocalContext.current

    val account = Account.ofAor(aor)!!
    val uaMessages: MutableState<List<Message>> = remember { mutableStateOf(emptyList()) }
    var areMessagesLoaded by remember { mutableStateOf(false) }

    var refreshTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(aor, refreshTrigger) {
        uaMessages.value = loadMessages(account)
        areMessagesLoaded = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME)
                refreshTrigger++
        }
        val messageObserver = androidx.lifecycle.Observer<Long> {
            refreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        BaresipService.messageUpdate.observe(lifecycleOwner, messageObserver)
        onDispose { 
            lifecycleOwner.lifecycle.removeObserver(observer)
            BaresipService.messageUpdate.removeObserver(messageObserver)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = Color.White,
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                Spacer(Modifier.statusBarsPadding())
                TopAppBar(navController, account, uaMessages)
            }
        },
        content = { contentPadding ->
            if (areMessagesLoaded) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ChatsContent(LocalContext.current, navController, contentPadding, account, uaMessages)
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        BottomNavigationBar(ctx, viewModel, navController)
                    }
                }
            }
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
    val lastAction = remember { mutableStateOf({}) }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = String.format(stringResource(R.string.delete_chats_alert), account.text()),
        firstButtonText = stringResource(R.string.cancel),
        lastButtonText = stringResource(R.string.delete),
        onLastClicked = lastAction.value,
    )

    TopAppBar(
        title = { Text(text = "Messages", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        actions = {
            IconButton(
                onClick = { menuExpanded = !menuExpanded }
            ) {
                Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
            }
            DropdownMenu (
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                items = listOf(delete, blocked),
                onItemClick = { selectedItem ->
                    menuExpanded = false
                    when (selectedItem) {
                        delete -> {
                            lastAction.value = {
                                deleteMessages(uaMessages, account, "")
                                account.unreadMessages = false
                            }
                            showDialog.value = true
                        }
                        blocked ->
                            navController.navigate("blocked/message/${account.aor}")
                    }
                }
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsContent(
    ctx: Context,
    navController: NavController,
    contentPadding: PaddingValues,
    account: Account,
    uaMessages: MutableState<List<Message>>
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).background(Color.White),
        verticalArrangement = Arrangement.Top
    ) {
        // Search Bar (full width, pill shaped)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = Color.Gray
                    )
                },
                placeholder = { Text("Search contacts or messages", color = Color.Gray) },
                shape = RoundedCornerShape(50), // Pill shaped
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                textStyle = TextStyle(fontSize = 16.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)
            )
        }
        val filteredMessages = remember(searchQuery, uaMessages.value) {
            if (searchQuery.isBlank()) {
                uaMessages.value
            } else {
                val query = searchQuery.lowercase()
                uaMessages.value.filter { message ->
                    val peerName = com.tutpro.baresip.Utils.friendlyUri(ctx, message.peerUri, account, includeLabel = false).lowercase()
                    peerName.contains(query) || message.message.lowercase().contains(query)
                }
            }
        }

        Chats(ctx, navController, account, filteredMessages)
    }
}

@Composable
private fun Chats(
    ctx: Context,
    navController: NavController,
    account: Account,
    messages: List<Message>
) {
    val aor = account.aor

    val showDialog = remember { mutableStateOf(false) }
    val dialogMessage = remember { mutableStateOf("") }
    val secondButtonText = remember { mutableStateOf("") }
    val secondAction = remember { mutableStateOf({}) }
    val lastButtonText = remember { mutableStateOf("") }
    val lastAction = remember { mutableStateOf({}) }

    if (showDialog.value)
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

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScrollbar(state = lazyListState),
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = messages, key = { message -> message.timeStamp }) { message ->
            val peerName = Utils.friendlyUri(ctx, message.peerUri, account, includeLabel = false)
            val cal = GregorianCalendar()
            cal.timeInMillis = message.timeStamp
            val fmt: DateFormat = if (isToday(message.timeStamp))
                DateFormat.getTimeInstance(DateFormat.SHORT)
            else
                DateFormat.getDateInstance(DateFormat.SHORT)
            val info = fmt.format(cal.time)

            // Unread Count Logic
            val unreadCount = BaresipService.messages.count { it.aor == aor && it.peerUri == message.peerUri && it.new }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("chat/${aor}/${message.peerUri}") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF3F4F6)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
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
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                )
                            else
                                CustomElements.TextAvatar(contact.name, contact.color)
                        }
                        null -> {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = "Avatar",
                                modifier = Modifier.size(40.dp),
                                tint = Color.LightGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Text Details
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = peerName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = info,
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message.message,
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD1D5DB))
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Removed NewChatPeer

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

private fun deleteMessages(uaMessages: MutableState<List<Message>>, account: Account, peerUri: String) {
    val updatedMessages = BaresipService.messages.toMutableList()
    updatedMessages.removeAll { it.aor == account.aor && (peerUri == "" || it.peerUri == peerUri) }
    BaresipService.messages = updatedMessages.toList()
    Message.save()
    uaMessages.value = loadMessages(account)
}
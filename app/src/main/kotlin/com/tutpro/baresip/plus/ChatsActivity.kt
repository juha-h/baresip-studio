package com.tutpro.baresip.plus

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils.isToday
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tutpro.baresip.plus.BaresipService.Companion.contactNames
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.ImageAvatar
import com.tutpro.baresip.plus.CustomElements.LabelText
import com.tutpro.baresip.plus.CustomElements.TextAvatar
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar
import com.tutpro.baresip.plus.CustomElements.SelectableAlertDialog
import java.text.DateFormat
import java.util.GregorianCalendar

class ChatsActivity: ComponentActivity() {

    internal lateinit var aor: String
    internal lateinit var account: Account
    private lateinit var chatRequest: ActivityResultLauncher<Intent>

    private var _uaMessages = mutableStateOf<List<Message>>(emptyList())
    private var uaMessages: List<Message> by _uaMessages

    private val alertTitle = mutableStateOf("")
    private val alertMessage = mutableStateOf("")
    private val showAlert = mutableStateOf(false)

    private var backInvokedCallback: OnBackInvokedCallback? = null
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    @RequiresApi(33)
    private fun registerBackInvokedCallback() {
        backInvokedCallback = OnBackInvokedCallback { goBack() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            backInvokedCallback!!
        )
    }

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33)
            registerBackInvokedCallback()
        else {
            onBackPressedCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goBack()
                }
            }
            onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        }

        aor = intent.extras!!.getString("aor")!!
        Utils.addActivity("chats,$aor")
        account = UserAgent.ofAor(aor)!!.account

        val title = getString(R.string.chats)

        _uaMessages.value = uaMessages(aor)

        chatRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK)
                    _uaMessages.value = uaMessages(aor)
            }

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    ChatsScreen(this, title) { goBack() }
                }
            }
        }
    }

    @Composable
    fun ChatsScreen(ctx: Context, title: String, navigateBack: () -> Unit) {
        Scaffold(
            modifier = Modifier
                .fillMaxHeight()
                .imePadding()
                .safeDrawingPadding(),
            containerColor = LocalCustomColors.current.background,
            topBar = { TopAppBar(title, navigateBack) },
            bottomBar = { NewChatPeer() },
            content = { contentPadding ->
                ChatsContent(ctx, contentPadding)
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopAppBar(title: String, navigateBack: () -> Unit) {

        var expanded by remember { mutableStateOf(false) }

        val delete = stringResource(R.string.delete)

        val showDialog = remember { mutableStateOf(false) }
        val positiveAction = remember { mutableStateOf({}) }

        AlertDialog(
            showDialog = showDialog,
            title = stringResource(R.string.confirmation),
            message = String.format(
                stringResource(R.string.delete_chats_alert),
                aor.substringAfter(":")
            ),
            positiveButtonText = stringResource(R.string.delete),
            onPositiveClicked = positiveAction.value,
            negativeButtonText = stringResource(R.string.cancel),
        )

        TopAppBar(
            title = {
                Text(
                    text = title,
                    color = LocalCustomColors.current.light,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.mediumTopAppBarColors(
                containerColor = LocalCustomColors.current.primary
            ),
            navigationIcon = {
                IconButton(onClick = navigateBack) {
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
                    listOf(delete),
                    onItemClick = { selectedItem ->
                        expanded = false
                        when (selectedItem) {
                            delete -> {
                                positiveAction.value = {
                                    Message.clearMessagesOfAor(aor)
                                    Message.save()
                                    _uaMessages.value = listOf()
                                    account.unreadMessages = false
                                }
                                showDialog.value = true
                            }
                        }
                    }
                )
            }
        )
    }

    @Composable
    fun ChatsContent(ctx: Context, contentPadding: PaddingValues) {

        if (showAlert.value) {
            AlertDialog(
                showDialog = showAlert,
                title = alertTitle.value,
                message = alertMessage.value,
                positiveButtonText = stringResource(R.string.ok),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.Top
        ) {
            Account(account)
            Chats(ctx, account)
        }
    }

    @Composable
    fun Account(account: Account) {
        val headerText = stringResource(R.string.account) + " " +
                if (account.nickName.value != "")
                    account.nickName.value
                else
                    aor.split(":")[1]
        Text(
            text = headerText,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }

    @Composable
    fun Chats(ctx: Context, account: Account) {

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
                .imePadding()
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp)
                .verticalScrollbar(
                    state = lazyListState,
                    width = 4.dp,
                    color = LocalCustomColors.current.gray
                )
                .background(LocalCustomColors.current.background),
            reverseLayout = true,
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items = uaMessages, key = { message -> message.timeStamp }) { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (val contact = Contact.findContact(message.peerUri)) {
                        is Contact.BaresipContact -> {
                            val avatarImage = contact.avatarImage
                            if (avatarImage != null)
                                ImageAvatar(avatarImage)
                            else
                                TextAvatar(contact.name, contact.color)
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
                                TextAvatar(contact.name, contact.color)
                        }

                        null -> {
                            val avatarImage = BitmapFactory
                                .decodeResource(ctx.resources, R.drawable.person_image)
                            ImageAvatar(avatarImage)
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    CustomElements.Button(
                        onClick = {
                            val i = Intent(ctx, ChatActivity::class.java)
                            val b = Bundle()
                            b.putString("aor", aor)
                            b.putString("peer", message.peerUri)
                            i.putExtras(b)
                            chatRequest.launch(i)
                        },
                        onLongClick = {
                            val peer = Utils.friendlyUri(ctx, message.peerUri, account)
                            val contactExists =
                                Contact.nameExists(peer, BaresipService.contacts, false)
                            if (contactExists) {
                                dialogMessage.value = String.format(
                                    getString(R.string.short_chat_question),
                                    peer
                                )
                                positiveButtonText.value = getString(R.string.delete)
                                positiveAction.value = {
                                    Message.deleteAorPeerMessages(aor, message.peerUri)
                                    _uaMessages.value = uaMessages(aor)
                                }
                                neutralButtonText.value = ""
                            } else {
                                dialogMessage.value = String.format(
                                    getString(R.string.long_chat_question),
                                    peer
                                )
                                positiveButtonText.value = getString(R.string.add_contact)
                                positiveAction.value = {
                                    val i = Intent(ctx, BaresipContactActivity::class.java)
                                    val b = Bundle()
                                    b.putBoolean("new", true)
                                    b.putString("uri", message.peerUri)
                                    i.putExtras(b)
                                    startActivity(i)
                                }
                                neutralButtonText.value = getString(R.string.delete)
                                neutralAction.value = {
                                    Message.deleteAorPeerMessages(aor, message.peerUri)
                                }
                            }
                            showDialog.value = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(end = 6.dp),
                        shape = if (message.direction == MESSAGE_DOWN)
                            RoundedCornerShape(50.dp, 20.dp, 20.dp, 10.dp)
                        else
                            RoundedCornerShape(20.dp, 10.dp, 50.dp, 20.dp),
                        color =
                            if (message.direction == MESSAGE_DOWN) {
                                if (BaresipService.darkTheme.value)
                                    LocalCustomColors.current.secondaryDark
                                else
                                    LocalCustomColors.current.secondaryLight
                            } else {
                                if (BaresipService.darkTheme.value)
                                    LocalCustomColors.current.primaryDark
                                else
                                    LocalCustomColors.current.primaryLight
                            },
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
                            Row {
                                val textColor =
                                    if (message.direction == MESSAGE_DOWN) {
                                        if (BaresipService.darkTheme.value)
                                            LocalCustomColors.current.secondaryLight
                                        else
                                            LocalCustomColors.current.secondaryDark
                                    } else {
                                        if (BaresipService.darkTheme.value)
                                            LocalCustomColors.current.primaryLight
                                        else
                                            LocalCustomColors.current.primaryDark
                                    }
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
                                        color = LocalCustomColors.current.itemText,
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
    fun NewChatPeer() {

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
        var filteredSuggestions by remember { mutableStateOf(suggestions) }
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
                                LocalCustomColors.current.grayLight,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .animateContentSize()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .verticalScrollbar(
                                        state = lazyListState,
                                        color = LocalCustomColors.current.gray
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
                                                newPeer = suggestion
                                                showSuggestions = false
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = suggestion,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = LocalCustomColors.current.grayDark,
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
                    placeholder = {
                        Text(stringResource(R.string.new_chat_peer))
                    },
                    onValueChange = {
                        newPeer = it
                        showSuggestions = newPeer.length > 2
                        filteredSuggestions = if (it.isEmpty()) {
                            suggestions
                        } else {
                            suggestions.filter { suggestion ->
                                newPeer.length > 2 && suggestion.startsWith(
                                    newPeer,
                                    ignoreCase = true
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (newPeer.isNotEmpty())
                            Icon(
                                Icons.Outlined.Clear,
                                contentDescription = null,
                                modifier = Modifier
                                    .clickable {
                                        if (showSuggestions)
                                            showSuggestions = false
                                        else
                                            newPeer = ""
                                    }
                            )
                    },
                    label = {
                        LabelText(stringResource(R.string.new_chat_peer))
                    },
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        color = LocalCustomColors.current.itemText
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    )
                )
            }
            Spacer(Modifier.width(4.dp))
            SmallFloatingActionButton(
                modifier = Modifier.padding(end = 4.dp),
                onClick = {
                    showSuggestions = false
                    val peerText = newPeer.trim()
                    if (peerText.isNotEmpty()) {
                        val uris = Contact.contactUris(peerText)
                        if (uris.isEmpty())
                            makeChat(peerText)
                        else if (uris.size == 1)
                            makeChat(uris[0])
                        else {
                            items.value = uris
                            itemAction.value = { index ->
                                makeChat(uris[index])
                            }
                            showDialog.value = true
                        }
                    }
                    newPeer = ""
                    focusManager.clearFocus()
                },
                containerColor = LocalCustomColors.current.accent,
                contentColor = LocalCustomColors.current.background
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    modifier = Modifier.size(36.dp),
                    contentDescription = stringResource(R.string.add)
                )
            }
        }
    }

    private fun makeChat(chatPeer: String) {
        val peerUri = if (Utils.isTelNumber(chatPeer))
            "tel:$chatPeer"
        else
            chatPeer
        val uri = if (Utils.isTelUri(peerUri)) {
            if (account.telProvider == "") {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value =
                    String.format(getString(R.string.no_telephony_provider), account.aor)
                showAlert.value = true
                ""
            } else
                Utils.telToSip(peerUri, account)
        } else
            Utils.uriComplete(peerUri, aor)
        if (alertMessage.value.isEmpty()) {
            if (!Utils.checkUri(uri)) {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_sip_or_tel_uri), uri)
                showAlert.value = true
            }
            else {
                val i = Intent(this@ChatsActivity, ChatActivity::class.java)
                val b = Bundle()
                b.putString("aor", aor)
                b.putString("peer", uri)
                i.putExtras(b)
                chatRequest.launch(i)
            }
        }
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        _uaMessages.value = uaMessages(aor)
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (backInvokedCallback != null)
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backInvokedCallback!!)
        }
        else
            onBackPressedCallback.remove()
        super.onDestroy()
    }

    private fun goBack() {
        BaresipService.activities.remove("chats,$aor")
        returnResult()
    }

    private fun returnResult() {
        setResult(RESULT_CANCELED, Intent())
        finish()
    }

    private fun uaMessages(aor: String) : List<Message> {
        val res = mutableListOf<Message>()
        account.unreadMessages = false
        for (m in BaresipService.messages.reversed()) {
            if (m.aor != aor) continue
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
}

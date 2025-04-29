package com.tutpro.baresip.plus

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils.isToday
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Observer
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.LabelText
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.GregorianCalendar

class ChatActivity : ComponentActivity() {

    private lateinit var imm: InputMethodManager
    private lateinit var aor: String
    private lateinit var account: Account
    private lateinit var peerUri: String
    private lateinit var chatPeer: String
    private lateinit var ua: UserAgent

    private var _chatMessages = mutableStateOf<List<Message>>(emptyList())
    private var chatMessages : List<Message> by _chatMessages
    private var focus = false
    private var lastCall: Long = 0
    private var unsentMessage = ""
    private var keyboardController: SoftwareKeyboardController? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {

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

        aor = intent.getStringExtra("aor")!!
        peerUri = intent.getStringExtra("peer")!!
        account = UserAgent.ofAor(aor)!!.account
        focus = intent.getBooleanExtra("focus", false)

        if (BaresipService.activities.first().startsWith("chat,$aor,$peerUri")) {
            returnResult(RESULT_CANCELED)
            return
        } else {
            Utils.addActivity("chat,$aor,$peerUri,$focus")
        }

        val userAgent = UserAgent.ofAor(aor)
        if (userAgent == null) {
            Log.w(TAG, "ChatActivity did not find ua of $aor")
            MainActivity.activityAor = aor
            returnResult(RESULT_CANCELED)
            return
        } else {
            ua = userAgent
        }

        chatPeer = Utils.friendlyUri(this, peerUri, userAgent.account, true)

        imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        val title = String.format(getString(R.string.chat_with), chatPeer)

        val messagesObserver = Observer<Long> {
            _chatMessages.value = listOf()
            _chatMessages.value = uaPeerMessages(aor, peerUri)
        }
        BaresipService.messageUpdate.observe(this, messagesObserver)

        ua.account.unreadMessages = false

        setContent {
            AppTheme {
                keyboardController = LocalSoftwareKeyboardController.current
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ChatScreen(this, title) { goBack() }
                }
            }
        }
    }

    @Composable
    fun ChatScreen(ctx: Context, title: String, navigateBack: () -> Unit) {
        Scaffold(
            modifier = Modifier
                .fillMaxHeight()
                .imePadding()
                .safeDrawingPadding(),
            containerColor = LocalCustomColors.current.background,
            topBar = { TopAppBar(ctx, title, navigateBack) },
            bottomBar = { NewMessage(ctx, peerUri) },
            content = { contentPadding ->
                ChatContent(ctx, contentPadding)
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopAppBar(ctx: Context, title: String, navigateBack: () -> Unit) {
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
                    onClick = {
                        if (SystemClock.elapsedRealtime() - lastCall > 1000) {
                            lastCall = SystemClock.elapsedRealtime()
                            val intent = Intent(ctx, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            intent.putExtra("action", "call")
                            intent.putExtra("uap", ua.uap)
                            intent.putExtra("peer", peerUri)
                            startActivity(intent)
                        }
                    }
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.call_small),
                        contentDescription = "Call",
                        tint = LocalCustomColors.current.light
                    )
                }
            }
        )
    }

    @Composable
    fun ChatContent(ctx: Context, contentPadding: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.Bottom
        ) {
            Account(account)
            Spacer(modifier = Modifier.weight(1f))
            Messages(ctx)
        }
    }

    @Composable
    fun Account(account: Account) {
        val headerText = getString(R.string.account) + " " +
                if (account.nickName.value != "")
                    account.nickName.value
                else
                    aor.split(":")[1]
        Text(
            text = headerText,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }

    @Composable
    fun Messages(ctx: Context) {

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

        LaunchedEffect(Unit) {
            _chatMessages.value = uaPeerMessages(aor, peerUri)
        }

        LaunchedEffect(chatMessages) {
            // Scroll to the bottom when new messages are added
            if (chatMessages.isNotEmpty()) {
                coroutineScope.launch {
                    lazyListState.scrollToItem(0)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth()
                .padding(start = 16.dp, end = 2.dp)
                .verticalScrollbar(
                    state = lazyListState,
                    width = 4.dp,
                    color = LocalCustomColors.current.gray
                ),
            reverseLayout = true,
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items = chatMessages, key = { message -> message.timeStamp }) { message ->
                val down = message.direction == MESSAGE_DOWN
                val peer: String = if (down) {
                    if (chatPeer.startsWith("sip:") &&
                        (Utils.uriHostPart(message.peerUri) == Utils.uriHostPart(message.aor)))
                        Utils.uriUserPart(message.peerUri)
                    else
                        chatPeer
                }
                else
                    stringResource(R.string.you)
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
                            if (chatPeer == peerUri) {
                                dialogMessage.value = String.format(getString(R.string.long_message_question),
                                    peerUri
                                )
                                positiveButtonText.value = getString(R.string.add_contact)
                                positiveAction.value = {
                                    val i = Intent(ctx, BaresipContactActivity::class.java)
                                    val b = Bundle()
                                    b.putBoolean("new", true)
                                    b.putString("uri", peerUri)
                                    i.putExtras(b)
                                    ctx.startActivity(i)
                                }
                                neutralButtonText.value = getString(R.string.delete)
                                neutralAction.value = {
                                    message.delete()
                                    _chatMessages.value = uaPeerMessages(aor, peerUri)
                                }
                            }
                            else {
                                dialogMessage.value = getString(R.string.short_message_question)
                                positiveButtonText.value = getString(R.string.delete)
                                positiveAction.value = {
                                    message.delete()
                                    _chatMessages.value = uaPeerMessages(aor, peerUri)
                                }
                            }
                            showDialog.value = true
                        },
                        shape = if (message.direction == MESSAGE_DOWN)
                            RoundedCornerShape(50.dp, 20.dp, 20.dp, 10.dp)
                        else
                            RoundedCornerShape(20.dp, 10.dp, 50.dp, 20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor =
                            if (message.direction == MESSAGE_DOWN) {
                                if (BaresipService.darkTheme.value)
                                    LocalCustomColors.current.secondaryDark
                                else
                                    LocalCustomColors.current.secondaryLight
                            }
                            else {
                                if (BaresipService.darkTheme.value)
                                    LocalCustomColors.current.primaryDark
                                else
                                    LocalCustomColors.current.primaryLight
                            }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(start = if (message.direction == MESSAGE_DOWN) 0.dp else 24.dp,
                                end = if (message.direction == MESSAGE_DOWN) 24.dp else 0.dp)
                    ) {
                        Column {
                            Row {
                                val textColor =
                                    if (message.direction == MESSAGE_DOWN) {
                                        if (BaresipService.darkTheme.value)
                                            LocalCustomColors.current.secondaryLight
                                        else
                                            LocalCustomColors.current.secondaryDark
                                    }
                                    else {
                                        if (BaresipService.darkTheme.value)
                                            LocalCustomColors.current.primaryLight
                                        else
                                            LocalCustomColors.current.primaryDark
                                    }
                                Text(text = peer, fontSize = 12.sp, color = textColor)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(text = info, fontSize = 12.sp, color = textColor)
                            }
                            Row {
                                SelectionContainer {
                                    Text(
                                        text = message.message,
                                        color = LocalCustomColors.current.itemText,
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
    fun NewMessage(ctx: Context, peerUri: String) {

        val showDialog = remember { mutableStateOf(false) }
        val dialogMessage = remember { mutableStateOf("") }

        AlertDialog(
            showDialog = showDialog,
            title = stringResource(R.string.notice),
            message = dialogMessage.value,
            positiveButtonText = stringResource(R.string.ok),
        )

        var newMessage by remember { mutableStateOf("") }
        Row(modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val keyboardController = LocalSoftwareKeyboardController.current
            OutlinedTextField(
                value = newMessage,
                placeholder = { Text(stringResource(R.string.new_message)) },
                onValueChange = { newMessage = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .verticalScroll(rememberScrollState())
                    .combinedClickable(
                        onClick = {
                            val msgText = newMessage
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
                                _chatMessages.value += msg
                                if (Utils.isTelUri(peerUri))
                                    if (ua.account.telProvider == "") {
                                        dialogMessage.value = String.format(
                                            getString(R.string.no_telephony_provider),
                                            Utils.plainAor(aor))
                                        showDialog.value = true
                                    } else {
                                        msgUri = Utils.telToSip(peerUri, ua.account)
                                    }
                                else
                                    msgUri = peerUri
                                if (msgUri != "")
                                    if (Api.message_send(
                                            ua.uap,
                                            msgUri,
                                            msgText,
                                            time.toString()
                                        ) != 0
                                    ) {
                                        Toast.makeText(ctx, "${getString(R.string.message_failed)}!", Toast.LENGTH_SHORT).show()
                                        msg.direction = MESSAGE_UP_FAIL
                                        msg.responseReason = getString(R.string.message_failed)
                                    } else {
                                        newMessage = ""
                                        unsentMessage = ""
                                        keyboardController?.hide()
                                        BaresipService.chatTexts.remove("$aor::$peerUri")
                                    }
                            }
                        },
                        onLongClick = {
                            val clipboardManager =
                                ctx.getSystemService(CLIPBOARD_SERVICE) as
                                        android.content.ClipboardManager
                            val clipData = clipboardManager.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val pastedText = clipData.getItemAt(0).text.toString()
                                newMessage = pastedText
                            } else {
                                Toast.makeText(ctx, "Nothing to paste", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ),
                singleLine = false,
                trailingIcon = {
                    if (newMessage.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.Clear,
                            contentDescription = "Clear",
                            modifier = Modifier.clickable { newMessage = "" }
                        )
                    } },
                label = { LabelText(stringResource(R.string.new_message)) },
                textStyle = TextStyle(fontSize = 18.sp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    autoCorrectEnabled = true
                )
            )
            Image(
                painter = painterResource(id = R.drawable.send),
                contentDescription = "Send",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp)
                    .clickable {
                        val msgText = newMessage
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
                            _chatMessages.value += msg
                            if (Utils.isTelUri(peerUri))
                                if (ua.account.telProvider == "") {
                                    dialogMessage.value = String.format(
                                        getString(R.string.no_telephony_provider),
                                        Utils.plainAor(aor))
                                    showDialog.value = true
                                } else {
                                    msgUri = Utils.telToSip(peerUri, ua.account)
                                }
                            else
                                msgUri = peerUri
                            if (msgUri != "")
                                if (Api.message_send(
                                        ua.uap,
                                        msgUri,
                                        msgText,
                                        time.toString()
                                    ) != 0
                                ) {
                                    Toast.makeText(ctx, "${getString(R.string.message_failed)}!", Toast.LENGTH_SHORT).show()
                                    msg.direction = MESSAGE_UP_FAIL
                                    msg.responseReason = getString(R.string.message_failed)
                                } else {
                                    newMessage = ""
                                    unsentMessage = ""
                                    keyboardController?.hide()
                                    BaresipService.chatTexts.remove("$aor::$peerUri")
                                }
                        }
                    }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val chatText = BaresipService.chatTexts["$aor::$peerUri"]
        if (chatText != null) {
            Log.d(TAG, "Restoring newMessage $chatText for $aor::$peerUri")
            unsentMessage = chatText
            //newMessage.requestFocus()
            BaresipService.chatTexts.remove("$aor::$peerUri")
        }
        _chatMessages.value = uaPeerMessages(aor, peerUri)
    }

    override fun onPause() {
        super.onPause()
        if (unsentMessage != "") {
            Log.d(TAG, "Saving newMessage $unsentMessage for $aor::$peerUri")
            BaresipService.chatTexts["$aor::$peerUri"] = unsentMessage
        }
        MainActivity.activityAor = aor
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
        var save = false
        for (m in chatMessages) {
            if (m.new) {
                m.new = false
                save = true
            }
        }
        if (save) Message.save()
        keyboardController?.hide()
        BaresipService.activities.remove("chat,$aor,$peerUri,false")
        BaresipService.activities.remove("chat,$aor,$peerUri,true")
        returnResult(RESULT_OK)
    }

    private fun returnResult(code: Int) {
        setResult(code, Intent())
        finish()
    }

    private fun uaPeerMessages(aor: String, peerUri: String): List<Message> {
        val res = mutableListOf<Message>()
        for (m in BaresipService.messages.reversed())
            if ((m.aor == aor) && (m.peerUri == peerUri)) res.add(m)
        return res
    }

}

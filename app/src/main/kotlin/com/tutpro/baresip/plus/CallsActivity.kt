package com.tutpro.baresip.plus

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.ImageAvatar
import com.tutpro.baresip.plus.CustomElements.TextAvatar
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar

class CallsActivity : ComponentActivity() {

    private lateinit var account: Account

    private var aor = ""

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

        val title = String.format(getString(R.string.call_history))

        aor = intent.getStringExtra("aor")!!
        Utils.addActivity("calls,$aor")

        val ua = UserAgent.ofAor(aor)!!
        account = ua.account
        aorGenerateHistory(aor)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    CallsScreen(this, title) { goBack() }
                }
            }
        }

        account.missedCalls = false
    }

    @Composable
    fun CallsScreen(ctx: Context, title: String, navigateBack: () -> Unit) {
        Scaffold(
            modifier = Modifier
                .fillMaxHeight()
                .imePadding()
                .safeDrawingPadding(),
            containerColor = LocalCustomColors.current.background,
            topBar = { TopAppBar(title, navigateBack) },
            content = { contentPadding ->
                CallsContent(ctx, contentPadding)
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopAppBar(title: String, navigateBack: () -> Unit) {

        var expanded by remember { mutableStateOf(false) }

        val delete = String.format(getString(R.string.delete))
        val disable = String.format(getString(R.string.disable_history))
        val enable = String.format(getString(R.string.enable_history))

        val showDialog = remember { mutableStateOf(false) }
        val positiveAction = remember { mutableStateOf({}) }

        AlertDialog(
            showDialog = showDialog,
            title = getString(R.string.confirmation),
            message = String.format(getString(R.string.delete_history_alert), aor.substringAfter(":")),
            positiveButtonText = getString(R.string.delete),
            negativeButtonText = getString(R.string.cancel),
            onPositiveClicked = positiveAction.value,
        )

        TopAppBar(
            title = {
                Text(
                    text = title,
                    color = LocalCustomColors.current.light,
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
                CustomElements.DropdownMenu(expanded,
                    { expanded = false },
                    listOf(delete, if (account.callHistory) disable else enable),
                    onItemClick = { selectedItem ->
                        expanded = false
                        when (selectedItem) {
                            delete -> {
                                positiveAction.value = {
                                    CallHistoryNew.clear(aor)
                                    CallHistoryNew.save()
                                    uaHistory.value = emptyList()
                                }
                                showDialog.value = true
                            }
                            disable, enable -> {
                                account.callHistory = !account.callHistory
                                AccountsActivity.saveAccounts()
                            }
                        }
                    }
                )
            }
        )
    }

    @Composable
    fun CallsContent(ctx: Context, contentPadding: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Account(account)
            Calls(ctx, account)
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
    fun Calls(ctx: Context, account: Account) {

        val showDialog = remember { mutableStateOf(false) }
        val message = remember { mutableStateOf("") }
        val positiveButtonText = remember { mutableStateOf("") }
        val positiveAction = remember { mutableStateOf({}) }
        val neutralButtonText = remember { mutableStateOf("") }
        val neutralAction = remember { mutableStateOf({}) }

        AlertDialog(
            showDialog = showDialog,
            title = getString(R.string.confirmation),
            message = message.value,
            positiveButtonText = positiveButtonText.value,
            onPositiveClicked = positiveAction.value,
            neutralButtonText = neutralButtonText.value,
            onNeutralClicked = neutralAction.value,
            negativeButtonText = getString(R.string.cancel)
        )

        val lazyListState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp)
                .verticalScrollbar(
                    state = lazyListState,
                    width = 4.dp,
                    color = LocalCustomColors.current.gray
                )
                .background(LocalCustomColors.current.background),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = uaHistory.value) { callRow ->
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
                                    val peerUri = callRow.peerUri
                                    val peerName = Utils.friendlyUri(ctx, peerUri, account)
                                    message.value = String.format(ctx.getString(R.string.contact_action_question), peerName)
                                    positiveButtonText.value = ctx.getString(R.string.call)
                                    positiveAction.value = {
                                        BaresipService.activities.remove("calls,$aor")
                                        MainActivity.activityAor = aor
                                        returnResult()
                                        val i = Intent(this@CallsActivity, MainActivity::class.java)
                                        i.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        i.putExtra("action", "call")
                                        i.putExtra("uap", UserAgent.ofAor(aor)!!.uap)
                                        i.putExtra("peer", peerUri)
                                        startActivity(i)
                                    }
                                    neutralButtonText.value = ctx.getString(R.string.send_message)
                                    neutralAction.value = {
                                        BaresipService.activities.remove("calls,$aor")
                                        MainActivity.activityAor = aor
                                        returnResult()
                                        val i = Intent(this@CallsActivity, MainActivity::class.java)
                                        i.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        i.putExtra("action", "message")
                                        i.putExtra("uap", UserAgent.ofAor(aor)!!.uap)
                                        i.putExtra("peer", peerUri)
                                        startActivity(i)
                                    }
                                    showDialog.value = true
                                },
                                onLongClick = {
                                    val peerUri = callRow.peerUri
                                    val peerName = Utils.friendlyUri(ctx, peerUri, account)
                                    val callText: String = if (callRow.details.size > 1)
                                        getString(R.string.calls_calls)
                                    else
                                        getString(R.string.calls_call)
                                    val contactExists = Contact.nameExists(peerName, BaresipService.contacts, false)
                                    if (contactExists) {
                                        message.value = String.format(
                                            getString(R.string.calls_delete_question),
                                            peerName, callText
                                        )
                                        positiveButtonText.value = getString(R.string.delete)
                                        positiveAction.value = {
                                            removeFromHistory(callRow)
                                            CallHistoryNew.save()
                                        }
                                        neutralButtonText.value = ""
                                    }
                                    else {
                                        message.value = String.format(
                                            getString(R.string.calls_add_delete_question),
                                            peerName, callText
                                        )
                                        positiveButtonText.value = getString(R.string.add_contact)
                                        positiveAction.value = {
                                            val i = Intent(ctx, BaresipContactActivity::class.java)
                                            val b = Bundle()
                                            b.putBoolean("new", true)
                                            b.putString("uri", callRow.peerUri)
                                            i.putExtras(b)
                                            ctx.startActivity(i)
                                        }
                                        neutralButtonText.value = getString(R.string.delete)
                                        neutralAction.value = {
                                            removeFromHistory(callRow)
                                            CallHistoryNew.save()
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
                    Box(modifier = Modifier.width(56.dp)) {
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
                                .width(64.dp)
                                .clickable(onClick = {
                                    val i = Intent(ctx, CallDetailsActivity::class.java)
                                    val b = Bundle()
                                    b.putString("aor", account.aor)
                                    b.putString("peer", callRow.peerUri)
                                    b.putInt("position", uaHistory.value.indexOf(callRow))
                                    i.putExtras(b)
                                    ctx.startActivity(i)
                                })
                        )
                    }
                }
            }
        }
    }

    private fun goBack() {
        BaresipService.activities.remove("calls,$aor")
        returnResult()
    }

    private fun returnResult() {
        setResult(RESULT_CANCELED, Intent())
        finish()
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
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

    private fun aorGenerateHistory(aor: String) {
        uaHistory.value = emptyList()
        for (i in BaresipService.callHistory.indices.reversed()) {
            val h = BaresipService.callHistory[i]
            if (h.aor == aor) {
                val direction: Int = if (h.direction == "in") {
                    if (h.startTime != null) {
                        R.drawable.call_down_green
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
                if (uaHistory.value.isNotEmpty() && (uaHistory.value.last().peerUri == h.peerUri))
                    uaHistory.value.last().details.add(CallRow.Details(
                        direction, h.startTime,
                        h.stopTime, h.recording
                    ))
                else
                    addToUaHistory(
                        CallRow(h.aor, h.peerUri, direction, h.startTime, h.stopTime, h.recording)
                    )
            }
        }
    }

    private fun removeFromHistory(callRow: CallRow) {
        for (details in callRow.details) {
            if (details.recording[0] != "")
                CallHistoryNew.deleteRecording(details.recording)
            BaresipService.callHistory.removeAll {
                it.startTime == details.startTime && it.stopTime == details.stopTime
            }
        }
        CallHistoryNew.deleteRecording(callRow.recording)
        deleteFromUaHistory(callRow)
    }

    private fun addToUaHistory(callRow: CallRow) {
        val updatedList = uaHistory.value.toMutableList()
        updatedList.add(callRow)
        uaHistory.value = updatedList
    }

    private fun deleteFromUaHistory(callRow: CallRow) {
        val updatedList = uaHistory.value.toMutableList()
        updatedList.remove(callRow)
        uaHistory.value = updatedList
    }

    @SuppressLint("MutableCollectionMutableState")
    companion object {
        val uaHistory = mutableStateOf(emptyList<CallRow>())
    }

}

package com.tutpro.baresip.plus

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Observer
import coil.compose.AsyncImage
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.TextAvatar
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar
import java.io.File
import java.io.IOException

class ContactsActivity : ComponentActivity() {

    private lateinit var aor: String
    private var newAndroidName: String? = null
    private var lastClick: Long = 0

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

    private val contactRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                if (it.data != null && it.data!!.hasExtra("name"))
                    newAndroidName = it.data!!.getStringExtra("name")
            }
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

        val title = String.format(getString(R.string.contacts))

        aor = intent.getStringExtra("aor")!!
        Utils.addActivity("contacts,$aor")

        val androidContactsObserver = Observer<Long> {
            if (newAndroidName != null) {
                val contentValues = ContentValues()
                contentValues.put(ContactsContract.Contacts.STARRED, 1)
                try {
                    this.contentResolver.update(
                        ContactsContract.RawContacts.CONTENT_URI, contentValues,
                        ContactsContract.Contacts.DISPLAY_NAME + "='" + newAndroidName + "'", null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Update of Android favorite failed: ${e.message}")
                }
                newAndroidName = null
            }
            //Contact.contactsUpdate()
        }
        BaresipService.contactUpdate.observe(this, androidContactsObserver)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    ContactsScreen(LocalContext.current, title) {
                        goBack()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ContactsScreen(ctx: Context, title: String, navigateBack: () -> Unit) {
        Scaffold(
            modifier = Modifier
                .fillMaxHeight()
                .imePadding()
                .safeDrawingPadding(),
            containerColor = LocalCustomColors.current.background,
            topBar = {
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
                )
            },
            floatingActionButton = {
                SmallFloatingActionButton(onClick = {
                    if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                        lastClick = SystemClock.elapsedRealtime()
                        val intent = Intent(ctx, BaresipContactActivity::class.java)
                        val b = Bundle()
                        b.putBoolean("new", true)
                        b.putString("uri", "")
                        intent.putExtras(b)
                        contactRequest.launch(intent)
                    }},
                    containerColor = LocalCustomColors.current.accent,
                    contentColor = LocalCustomColors.current.background
                ) {
                    Icon(imageVector = Icons.Filled.Add,
                        modifier = Modifier.size(36.dp),
                        contentDescription = stringResource(R.string.add)
                    )
                }
            },
            content = { contentPadding ->
                ContactsContent(ctx, contentPadding)
            }
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ContactsContent(ctx: Context, contentPadding: PaddingValues) {

        val showDialog = remember { mutableStateOf(false) }
        val dialogMessage = remember { mutableStateOf("") }
        val positiveText = remember { mutableStateOf("") }
        val positiveAction = remember { mutableStateOf({}) }
        val neutralText = remember { mutableStateOf("") }
        val neutralAction = remember { mutableStateOf({}) }

        if (showDialog.value)
            AlertDialog(
                showDialog = showDialog,
                title = getString(R.string.confirmation),
                message = dialogMessage.value,
                positiveButtonText = positiveText.value,
                onPositiveClicked = positiveAction.value,
                neutralButtonText = neutralText.value,
                onNeutralClicked = neutralAction.value,
                negativeButtonText = getString(R.string.cancel)
            )

        val lazyListState = rememberLazyListState()

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalCustomColors.current.background)
                .padding(contentPadding)
                .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 64.dp)
                .verticalScrollbar(
                    state = lazyListState,
                    width = 4.dp,
                    color = LocalCustomColors.current.gray
                ),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(BaresipService.contacts, key = { it.id() }) { contact ->

                val name = contact.name()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when(contact) {

                        is Contact.BaresipContact -> {
                            val avatarImage = contact.avatarImage
                            if (avatarImage != null)
                                Image(
                                    bitmap = avatarImage.asImageBitmap(),
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                )
                            else
                                TextAvatar(name, contact.color)
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
                                TextAvatar(name, contact.color)
                        }
                    }

                    when(contact) {

                        is Contact.BaresipContact -> {
                            Text(text = name,
                                fontSize = 20.sp,
                                fontStyle = if (contact.favorite()) FontStyle.Italic else FontStyle.Normal,
                                color = LocalCustomColors.current.itemText,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp)
                                    .combinedClickable(
                                        onClick = {
                                            dialogMessage.value = String.format(
                                                ctx.getString(R.string.contact_action_question),
                                                name
                                            )
                                            positiveText.value = ctx.getString(R.string.call)
                                            positiveAction.value = {
                                                val i = Intent(ctx, MainActivity::class.java)
                                                i.flags =
                                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                                            Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                i.putExtra("action", "call")
                                                val ua = UserAgent.ofAor(aor)
                                                if (ua == null)
                                                    Log.w(TAG, "onClickListener did not find AoR $aor")
                                                else {
                                                    BaresipService.activities.clear()
                                                    i.putExtra("uap", ua.uap)
                                                    i.putExtra("peer", contact.uri)
                                                    (ctx as Activity).startActivity(i)
                                                }
                                            }
                                            neutralText.value = ctx.getString(R.string.send_message)
                                            neutralAction.value = {
                                                val i = Intent(
                                                    ctx,
                                                    MainActivity::class.java
                                                )
                                                i.flags =
                                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                                            Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                i.putExtra("action", "message")
                                                val ua = UserAgent.ofAor(aor)
                                                if (ua == null)
                                                    Log.w(TAG, "onClickListener did not find AoR $aor")
                                                else {
                                                    BaresipService.activities.clear()
                                                    i.putExtra("uap", ua.uap)
                                                    i.putExtra("peer", contact.uri)
                                                    (ctx as Activity).startActivity(i)
                                                }
                                            }
                                            showDialog.value = true
                                        },
                                        onLongClick = {
                                            dialogMessage.value = String.format(
                                                ctx.getString(R.string.contact_delete_question),
                                                name
                                            )
                                            positiveText.value = ctx.getString(R.string.delete)
                                            positiveAction.value = {
                                                val id = contact.id
                                                val avatarFile = File(
                                                    BaresipService.filesPath,
                                                    "$id.png"
                                                )
                                                if (avatarFile.exists()) {
                                                    try {
                                                        avatarFile.delete()
                                                    } catch (e: IOException) {
                                                        Log.e(
                                                            TAG,
                                                            "Could not delete file $id.png: ${e.message}"
                                                        )
                                                    }
                                                }
                                                Contact.removeBaresipContact(contact)
                                            }
                                            showDialog.value = true
                                        }
                                    )
                            )
                            SmallFloatingActionButton(
                                modifier = Modifier.padding(end = 10.dp),
                                onClick = {
                                    if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                                        lastClick = SystemClock.elapsedRealtime()
                                        val intent = Intent(ctx, BaresipContactActivity::class.java)
                                        val b = Bundle()
                                        b.putBoolean("new", false)
                                        b.putString("name", name)
                                        intent.putExtras(b)
                                        contactRequest.launch(intent)
                                    }
                                },
                                containerColor = LocalCustomColors.current.background,
                                contentColor = LocalCustomColors.current.secondary
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    modifier = Modifier.size(28.dp),
                                    contentDescription = stringResource(R.string.edit)
                                )
                            }
                        }

                        is Contact.AndroidContact -> {
                            Text(text = name,
                                fontSize = 20.sp,
                                fontStyle = if (contact.favorite()) FontStyle.Italic else FontStyle.Normal,
                                color = LocalCustomColors.current.itemText,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp, top = 4.dp, bottom = 4.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                                                lastClick = SystemClock.elapsedRealtime()
                                                val i =
                                                    Intent(ctx, AndroidContactActivity::class.java)
                                                val b = Bundle()
                                                b.putString("aor", aor)
                                                b.putString("name", name)
                                                i.putExtras(b)
                                                ctx.startActivity(i, null)
                                            }
                                        },
                                        onLongClick = {
                                            dialogMessage.value = String.format(
                                                ctx.getString(R.string.contact_delete_question),
                                                name
                                            )
                                            positiveText.value = ctx.getString(R.string.delete)
                                            positiveAction.value = {
                                                ctx.contentResolver.delete(
                                                    ContactsContract.RawContacts.CONTENT_URI,
                                                    ContactsContract.Contacts.DISPLAY_NAME + "='" + name + "'",
                                                    null
                                                )
                                            }
                                            showDialog.value = true
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
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
        BaresipService.activities.remove("contacts,$aor")
        setResult(RESULT_OK, Intent())
        finish()
    }

}

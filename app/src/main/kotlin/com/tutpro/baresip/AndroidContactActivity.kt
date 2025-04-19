package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

class AndroidContactActivity : ComponentActivity() {

    private lateinit var aor: String
    private lateinit var name: String

    private var color = 0

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
        name = intent.getStringExtra("name")!!

        val title: String = name
        val contact = Contact.androidContact(name)

        if (contact == null) {
            Log.e(TAG, "No Android contact found with name $name")
            goBack()
        }

        Utils.addActivity("android contact, $name")

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    ContactScreen(this, title, contact!!) { goBack() }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ContactScreen(ctx: Context, title: String, contact: Contact.AndroidContact,
            navigateBack: () -> Unit) {
        Scaffold(
            modifier = Modifier.fillMaxHeight().imePadding().safeDrawingPadding(),
            containerColor = LocalCustomColors.current.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title, color = LocalCustomColors.current.grayLight,
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
            content = { contentPadding ->
                ContactContent(ctx, contentPadding, contact)
            }
        )
    }

    @Composable
    fun ContactContent(ctx: Context, contentPadding: PaddingValues, contact: Contact.AndroidContact) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalCustomColors.current.background)
                .padding(contentPadding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 52.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(contact)
            ContactName()
            Uris(ctx, contact)
        }
    }

    @Composable
    fun TextAvatar(text: String, size: Int, color: Int) {
        Box(
            modifier = Modifier.size(size.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(SolidColor(Color(color)))
            }
            Text(text, fontSize = 72.sp, color = Color.White)
        }
    }

    @Composable
    fun ImageAvatar(uri: Uri, size: Int) {
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.avatar_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape)
        )
    }

    @Composable
    fun Avatar(contact: Contact.AndroidContact) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            color = contact.color
            val thumbnailUri = contact.thumbnailUri
            if (thumbnailUri != null)
                ImageAvatar(thumbnailUri, 96)
            else
                TextAvatar(if (name == "") "" else name[0].toString(), 96, color)
        }
    }

    @Composable
    fun ContactName() {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(name, fontSize = 24.sp, color = LocalCustomColors.current.itemText)
        }
    }

    @Composable
    fun Uris(ctx: Context, contact: Contact.AndroidContact) {
        val lazyListState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp)
                .background(LocalCustomColors.current.background),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(contact.uris) { uri ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uri.substringAfter(":"),
                        modifier = Modifier.weight(1f),
                        fontSize = 18.sp,
                        color = LocalCustomColors.current.itemText,
                    )
                    Image(
                        painter = painterResource(R.drawable.message),
                        colorFilter = ColorFilter.tint(LocalCustomColors.current.itemText),
                        contentDescription = "Send Message",
                        modifier = Modifier.padding(end = 24.dp).clickable {
                            val i = Intent(ctx, MainActivity::class.java)
                            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            i.putExtra("action", "message")
                            val ua = UserAgent.ofAor(aor)
                            if (ua == null) {
                                Log.w(TAG, "message clickable did not find AoR $aor")
                            } else {
                                BaresipService.activities.clear()
                                i.putExtra("uap", ua.uap)
                                i.putExtra("peer", uri)
                                (ctx as Activity).startActivity(i)
                            }
                        }
                    )
                    Image(
                        painter = painterResource(R.drawable.call_small),
                        colorFilter = ColorFilter.tint(LocalCustomColors.current.itemText),
                        contentDescription = "Call",
                        modifier = Modifier.clickable {
                            val i = Intent(ctx, MainActivity::class.java)
                            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            i.putExtra("action", "call")
                            val ua = UserAgent.ofAor(aor)
                            if (ua == null) {
                                Log.w(TAG, "call clickable did not find AoR $aor")
                            } else {
                                BaresipService.activities.clear()
                                i.putExtra("uap", ua.uap)
                                i.putExtra("peer", uri)
                                (ctx as Activity).startActivity(i)
                            }
                        }
                    )
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
        BaresipService.activities.remove("android contact,$name")
        setResult(RESULT_CANCELED, Intent(this, MainActivity::class.java))
        finish()
    }
}

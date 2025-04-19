package com.tutpro.baresip

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.verticalScrollbar

class CodecsActivity : ComponentActivity() {

    private lateinit var acc: Account
    private lateinit var ua: UserAgent

    private lateinit var allCodecs: List<String>
    private lateinit var accCodecs: List<String>
    private lateinit var codecs: SnapshotStateList<Codec>

    private var aor = ""
    private var media = ""
    private var title = ""

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
        media = intent.getStringExtra("media")!!

        Utils.addActivity("codecs,$aor,$media")

        ua = UserAgent.ofAor(aor)!!
        acc = ua.account

        if (media == "audio") {
            title = getString(R.string.audio_codecs)
            allCodecs = ArrayList(Api.audio_codecs().split(","))
            accCodecs = acc.audioCodec
        } else {
            title = getString(R.string.video_codecs)
            allCodecs = ArrayList(Api.video_codecs().split(",").distinct())
            accCodecs = acc.videoCodec
        }

        val currentCodecs = mutableListOf<Codec>()
        for (codec in accCodecs)
            currentCodecs.add(Codec(codec, mutableStateOf(true)))
        for (codec in allCodecs)
            if (codec !in accCodecs)
                currentCodecs.add(Codec(codec, mutableStateOf(false)))

        codecs = mutableStateListOf<Codec>().apply { addAll(currentCodecs) }

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    CodecsScreen { goBack() }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CodecsScreen(navigateBack: () -> Unit) {
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
                            text = stringResource(R.string.codecs),
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
                        IconButton(onClick = {
                            updateCodecs()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                tint = LocalCustomColors.current.light,
                                contentDescription = "Check"
                            )
                        }
                    }
                )
            },
            content = { contentPadding ->
                CodecsContent(contentPadding)
            },
        )
    }

    @Composable
    fun CodecsContent(contentPadding: PaddingValues) {

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
                .background(LocalCustomColors.current.background)
                .padding(contentPadding)
                .padding(bottom = 16.dp),
        ) {
            Title()
            Codecs(codecs = codecs, onCodecsChange = { updatedCodecs ->
                onCodecsChange(updatedCodecs)
            })
        }
    }

    private fun onCodecsChange(updatedCodecs: List<Codec>) {
        codecs.clear()
        codecs.addAll(updatedCodecs)
    }

    @Composable
    fun Title() {
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable {
                    if (media == "audio") {
                        alertTitle.value = getString(R.string.audio_codecs)
                        alertMessage.value = getString(R.string.audio_codecs_help)
                    }
                    else {
                        alertTitle.value = getString(R.string.video_codecs)
                        alertMessage.value = getString(R.string.video_codecs_help)
                    }
                    showAlert.value = true
                },
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = LocalCustomColors.current.itemText,
            textAlign = TextAlign.Center
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Codecs(codecs: SnapshotStateList<Codec>, onCodecsChange: (List<Codec>) -> Unit) {

        val draggableState = rememberDraggableListState(
            onMove = { fromIndex, toIndex ->
                codecs.add(toIndex, codecs.removeAt(fromIndex))
                onCodecsChange(codecs.toList())
            }
        )

        LazyColumn(
            modifier = Modifier
                .padding(end = 4.dp)
                .verticalScrollbar(
                    state = draggableState.listState,
                    width = 4.dp,
                    color = LocalCustomColors.current.gray
                ),
            state = draggableState.listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
            //verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            draggableItems(
                state = draggableState,
                items = codecs,
                key = { item -> item.name }
            ) { item, isDragging ->
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = if (isDragging)
                            LocalCustomColors.current.grayLight
                        else
                            LocalCustomColors.current.background
                    ),
                    headlineContent = {
                        Text(text = item.name,
                            modifier = Modifier.fillMaxWidth()
                                .alpha(if (item.enabled.value) 1.0f else 0.5f)
                                .padding(start = 6.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        item.enabled.value = !item.enabled.value
                                        if (item.enabled.value) {
                                            val index = codecs.indexOf(item)
                                            codecs.removeAt(index)
                                            codecs.add(0, item)
                                        }
                                        else {
                                            val index = codecs.indexOf(item)
                                            codecs.removeAt(index)
                                            codecs.add(item)
                                        }
                                        onCodecsChange(codecs.toList())
                                    }
                                )
                        )
                    },
                    trailingContent = {
                        Icon(
                            modifier = Modifier.dragHandle(
                                state = draggableState,
                                key = item.name
                            ),
                            imageVector =ImageVector.vectorResource(R.drawable.reorder),
                            contentDescription = null
                        )
                    },
                )
                if (codecs.indexOf(item) > 0)
                    HorizontalDivider(
                        color = LocalCustomColors.current.gray,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = 1.dp
                    )
            }
        }
    }

    private fun updateCodecs() {

        var save = false
        val newCodecs = ArrayList<String>()

        for (codec in codecs)
            if (codec.enabled.value)
                newCodecs.add(codec.name)

        val codecList = Utils.implode(newCodecs, ",")

        if (media == "audio")
            if (newCodecs != acc.audioCodec) {
                if (Api.account_set_audio_codecs(acc.accp, codecList) == 0) {
                    Log.d(TAG, "New audio codecs '$codecList'")
                    acc.audioCodec = newCodecs
                    save = true
                } else {
                    Log.e(TAG, "Setting of audio codecs '$codecList' failed")
                }
            }

        if (media == "video")
            if (newCodecs != acc.videoCodec) {
                if (Api.account_set_video_codecs(acc.accp, codecList) == 0) {
                    Log.d(TAG, "New video codecs '$codecs'")
                    acc.videoCodec = newCodecs
                    save = true
                } else {
                    Log.e(TAG, "Setting of video codecs '$codecs' failed")
                }
            }

        if (save)
            AccountsActivity.saveAccounts()

        BaresipService.activities.remove("codecs,$aor,$media")
        finish()
    }

    private fun goBack() {
        BaresipService.activities.remove("codecs,$aor,$media")
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
}

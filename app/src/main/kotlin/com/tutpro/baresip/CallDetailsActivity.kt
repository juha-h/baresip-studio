package com.tutpro.baresip

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tutpro.baresip.CallsActivity.Companion.uaHistory
import com.tutpro.baresip.CustomElements.verticalScrollbar
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.GregorianCalendar

class CallDetailsActivity : ComponentActivity() {

    private lateinit var aor: String
    private lateinit var peer: String
    private lateinit var account: Account
    private lateinit var callRow: CallRow
    private lateinit var details: ArrayList<CallRow. Details>
    private val decPlayer = MediaPlayer()
    private val encPlayer = MediaPlayer()
    private var position = 0

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        aor = intent.getStringExtra("aor")!!
        account = Account.ofAor(aor)!!
        peer = intent.getStringExtra("peer")!!
        position = intent.getIntExtra("position", 0)
        callRow = uaHistory.value[position]
        details = callRow.details

        Utils.addActivity("call_details,$aor,$peer,$position")

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    CallDetailsScreen(this, getString(R.string.call_details)) { goBack() }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CallDetailsScreen(ctx: Context, title: String, navigateBack: () -> Unit) {
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
            content = { contentPadding ->
                CallDetailsContent(ctx, contentPadding)
            }
        )
    }

    @Composable
    fun CallDetailsContent(ctx: Context, contentPadding: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalCustomColors.current.background)
                .padding(contentPadding)
                .padding(top = 16.dp, start = 16.dp, end = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Peer(ctx, peer, account)
            Calls(ctx)
        }
    }

    @Composable
    fun Peer(ctx: Context, peer: String, account: Account) {
        val headerText = getString(R.string.peer) + " " +
                Utils.friendlyUri(ctx, peer, account)
        Text(
            text = headerText,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = LocalCustomColors.current.itemText,
            textAlign = TextAlign.Center
        )
    }

    @Composable
    fun Calls(ctx: Context) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = getString(R.string.direction),
                color = LocalCustomColors.current.itemText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(96.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = getString(R.string.time),
                color = LocalCustomColors.current.itemText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(text = getString(R.string.calls_duration),
                modifier = Modifier.padding(end = 12.dp),
                color = LocalCustomColors.current.itemText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        val lazyListState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth()
                .verticalScrollbar(
                    state = lazyListState,
                    width = 4.dp,
                    color = LocalCustomColors.current.gray
                )
                .background(LocalCustomColors.current.background),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(details) { detail ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(detail.direction),
                        contentDescription = "Direction",
                        modifier = Modifier.width(64.dp)
                    )
                    Spacer(modifier = Modifier.width(38.dp))
                    val durationText = startTime(ctx, detail)
                    Spacer(modifier = Modifier.weight(1f))
                    Duration(ctx, detail, durationText)
                }
            }
        }
    }

    @Composable
    fun startTime(ctx: Context, detail: CallRow.Details): String {
        val startTime = detail.startTime
        val stopTime = detail.stopTime
        val startTimeText: String
        val durationText: String
        val stopText = if (DateUtils.isToday(stopTime.timeInMillis)) {
            val fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM)
            ctx.getString(R.string.today) + " " + fmt.format(stopTime.time)
        } else {
            val fmt = DateFormat.getDateTimeInstance()
            fmt.format(stopTime.time)
        }
        if (startTime == GregorianCalendar(0, 0, 0)) {
            startTimeText = stopText
            durationText = "?"
        } else {
            if (startTime == null) {
                startTimeText = stopText
                durationText = ""
            } else {
                val startText = if (DateUtils.isToday(startTime.timeInMillis)) {
                    val fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM)
                    ctx.getString(R.string.today) + " " + fmt.format(startTime.time)
                } else {
                    val fmt = DateFormat.getDateTimeInstance()
                    fmt.format(startTime.time)
                }
                startTimeText = startText
                val duration = (stopTime.time.time - startTime.time.time) / 1000
                durationText = DateUtils.formatElapsedTime(duration)
            }
        }
        Text(text = startTimeText, color = LocalCustomColors.current.itemText)
        return durationText
    }

    @Composable
    fun Duration(ctx: Context, detail: CallRow.Details, durationText: String) {
        val recording = detail.recording
        if (recording[0] != "") {
            Text(
                text = durationText,
                color = LocalCustomColors.current.accent,
                modifier = Modifier.padding(end = 12.dp)
                    .clickable(onClick = {
                    val builder = MaterialAlertDialogBuilder(ctx, R.style.AlertDialogTheme)
                    builder.setTitle("Playing recording ...")
                        .setMessage("")
                        .setCancelable(true)
                    val dialog = builder.create()
                    if (!decPlayer.isPlaying && !encPlayer.isPlaying) {
                        decPlayer.reset()
                        encPlayer.reset()
                        Log.d(TAG, "Playing recordings ${recording[0]} and ${recording[1]}")
                        decPlayer.apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                            setOnPreparedListener {
                                encPlayer.apply {
                                    setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .build()
                                    )
                                    setOnPreparedListener {
                                        it.start()
                                        decPlayer.start()
                                        Log.d(TAG, "Started players")
                                        dialog.show()
                                    }
                                    setOnCompletionListener {
                                        Log.d(TAG, "Stopping encPlayer")
                                        it.stop()
                                        dialog.cancel()
                                    }
                                    try {
                                        val file = recording[0]
                                        val encFile = File(file)
                                            .copyTo(File(BaresipService.filesPath +
                                                    "/tmp/encode.wav"), true)
                                        val encUri = encFile.toUri()
                                        setDataSource(ctx, encUri)
                                        prepareAsync()
                                    } catch (e: IllegalArgumentException) {
                                        Log.e(TAG, "encPlayer IllegalArgumentException: $e")
                                    } catch (e: IOException) {
                                        Log.e(TAG, "encPlayer IOException: $e")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "encPlayer Exception: $e")
                                    }
                                }
                            }
                            setOnCompletionListener {
                                Log.d(TAG, "Stopping decPlayer")
                                it.stop()
                            }
                            try {
                                val file = recording[1]
                                val decFile = File(file)
                                    .copyTo(File(BaresipService.filesPath +
                                            "/tmp/decode.wav"), true)
                                val decUri = decFile.toUri()
                                setDataSource(ctx, decUri)
                                prepareAsync()
                            } catch (e: IllegalArgumentException) {
                                Log.e(TAG, "decPlayer IllegalArgumentException: $e")
                            } catch (e: IOException) {
                                Log.e(TAG, "decPlayer IOException: $e")
                            } catch (e: Exception) {
                                Log.e(TAG, "decPlayer Exception: $e")
                            }
                        }
                    } else if (decPlayer.isPlaying && encPlayer.isPlaying) {
                        decPlayer.stop()
                        encPlayer.stop()
                    }
                })
            )
        } else {
            Text(text = durationText,
                modifier = Modifier.padding(end = 12.dp),
                color = LocalCustomColors.current.itemText)
        }
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("call_details,$aor,$peer,$position") == -1)
            return true

        when (item.itemId) {
            android.R.id.home -> {
                goBack()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun goBack() {
        BaresipService.activities.remove("call_details,$aor,$peer,$position")
        decPlayer.stop()
        decPlayer.release()
        encPlayer.stop()
        encPlayer.release()
        finish()
    }

}
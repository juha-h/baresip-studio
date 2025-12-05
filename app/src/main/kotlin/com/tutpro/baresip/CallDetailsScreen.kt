package com.tutpro.baresip

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tutpro.baresip.CallRow.Details
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.verticalScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.DateFormat
import java.util.GregorianCalendar

fun NavGraphBuilder.callDetailsScreenRoute(navController: NavController, viewModel: ViewModel) {
    composable("call_details") { _ ->
        val callRow = remember { viewModel.consumeSelectedCallRow() }
        CallDetailsScreen(navController, callRow!!)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallDetailsScreen(navController: NavController, callRow: CallRow) {

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.call_details),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        IconButton(onClick = navController::popBackStack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
            }
        },
        content = { contentPadding ->
            CallDetailsContent(LocalContext.current, contentPadding, callRow)
        },
    )
}

@Composable
private fun CallDetailsContent(ctx: Context, contentPadding: PaddingValues, callRow: CallRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(top = 16.dp, start = 16.dp, end = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Peer(ctx, callRow)
        Details(ctx, callRow.details)
    }
}

@Composable
private fun Peer(ctx: Context, callRow: CallRow) {
    val account = Account.ofAor(callRow.aor)!!
    val headerText = stringResource(R.string.peer) + " " + Utils.friendlyUri(ctx, callRow.peerUri, account)
    Text(
        text = headerText,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun Details(ctx: Context, details: ArrayList<Details>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = stringResource(R.string.direction),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(96.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = stringResource(R.string.time),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(text = stringResource(R.string.calls_duration),
            modifier = Modifier.padding(end = 12.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    val lazyListState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScrollbar(
                state = lazyListState,
                width = 4.dp
            )
            .background(MaterialTheme.colorScheme.background),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(details) { detail ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            Toast.makeText(
                                ctx,
                                when (detail.direction) {
                                    CALL_DOWN_GREEN, CALL_UP_GREEN -> ctx.getString(R.string.call_answered)
                                    CALL_DOWN_BLUE -> ctx.getString(R.string.call_answered_elsewhere)
                                    CALL_MISSED_IN, CALL_MISSED_OUT -> ctx.getString(R.string.call_missed)
                                    CALL_DOWN_RED, CALL_UP_RED -> ctx.getString(R.string.call_rejected)
                                    else -> ""
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (callUp(detail.direction))
                            Icons.AutoMirrored.Filled.CallMade
                        else
                            Icons.AutoMirrored.Filled.CallReceived,
                        tint = colorResource(id = callTint(detail.direction)),
                        contentDescription = "Direction"
                    )
                }
                Spacer(modifier = Modifier.width(78.dp))
                val durationText = startTime(detail)
                Spacer(modifier = Modifier.weight(1f))
                Duration(ctx, detail, durationText)
            }
        }
    }
}

@Composable
private fun startTime(detail: Details): String {
    val startTime = detail.startTime
    val stopTime = detail.stopTime
    val startTimeText: String
    val durationText: String
    val stopText = if (DateUtils.isToday(stopTime.timeInMillis)) {
        val fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM)
        stringResource(R.string.today) + " " + fmt.format(stopTime.time)
    } else {
        val fmt = DateFormat.getDateTimeInstance()
        fmt.format(stopTime.time)
    }
    if (startTime == GregorianCalendar(0, 0, 0)) {
        startTimeText = stopText
        durationText = "?"
    } else {
        if (startTime == null  || detail.direction == CALL_DOWN_BLUE) {
            startTimeText = stopText
            durationText = ""
        } else {
            val startText = if (DateUtils.isToday(startTime.timeInMillis)) {
                val fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM)
                stringResource(R.string.today) + " " + fmt.format(startTime.time)
            } else {
                val fmt = DateFormat.getDateTimeInstance()
                fmt.format(startTime.time)
            }
            startTimeText = startText
            val duration = (stopTime.time.time - startTime.time.time) / 1000
            durationText = DateUtils.formatElapsedTime(duration)
        }
    }
    Text(text = startTimeText)
    return durationText
}

@Composable
private fun Duration(ctx: Context, detail: Details, durationText: String) {

    val showDialog = remember { mutableStateOf(false) }
    val recording = detail.recording
    val mediaPlayer = remember { MediaPlayer() }
    val scope = rememberCoroutineScope()

    PlaybackDialog(
        showDialog = showDialog,
        mediaPlayer = mediaPlayer,
        onStop = {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.reset()
            showDialog.value = false
        }
    )

    val isRawState = recording[0] != "" && recording[1] != ""
    val isMergedState = recording[0] != "" && recording[1] == ""

    if (isRawState || isMergedState) {
        Text(
            text = durationText,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .padding(end = 12.dp)
                .clickable(onClick = {
                    if (!mediaPlayer.isPlaying) {
                        mediaPlayer.reset()
                        scope.launch(Dispatchers.IO) {
                            var finalFile: File? = null

                            if (isRawState) {
                                val fileIn = File(recording[0])
                                val fileOut = File(recording[1])
                                val mergedFileName =
                                    "merged_${fileIn.nameWithoutExtension}_${fileOut.nameWithoutExtension}.wav"
                                val mergedFile = File(BaresipService.filesPath + "/tmp", mergedFileName)

                                if (mergedFile.exists()) {
                                    Log.d(TAG, "Using already merged file: ${mergedFile.name}")
                                    finalFile = mergedFile
                                }
                                else {
                                    File(BaresipService.filesPath + "/tmp").mkdirs()
                                    if (Utils.mergeWavFiles(fileIn, fileOut, mergedFile)) {
                                        finalFile = mergedFile
                                    }
                                }

                                // If merge successful, update state and delete originals
                                if (finalFile != null && finalFile.exists()) {
                                    // Update the object state
                                    // We put the merged path in [0] and clear [1]
                                    recording[0] = finalFile.absolutePath
                                    recording[1] = ""

                                    // Delete the original raw files
                                    try {
                                        if (fileIn.exists()) fileIn.delete()
                                        if (fileOut.exists()) fileOut.delete()

                                        // Persist changes so the app remembers the file is merged
                                        CallHistoryNew.save()
                                    } catch (e: Exception) {
                                        Log.w(TAG, "MergeWav: Failed to delete original files: ${e.message}")
                                    }
                                }
                            }
                            else {
                                // We are already in merged state
                                Log.d(TAG, "Using already merged file: ${recording[0]}")
                                finalFile = File(recording[0])
                            }

                            withContext(Dispatchers.Main) {
                                if (finalFile != null && finalFile.exists()) {
                                    try {
                                        mediaPlayer.apply {
                                            setAudioAttributes(
                                                AudioAttributes.Builder()
                                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                                    .build()
                                            )
                                            val fis = FileInputStream(finalFile)
                                            setDataSource(fis.fd)
                                            fis.close()
                                            setOnPreparedListener {
                                                it.start()
                                                Log.d(TAG, "Started playback")
                                                showDialog.value = true
                                            }
                                            setOnCompletionListener {
                                                Log.d(TAG, "Playback complete")
                                                showDialog.value = false
                                                it.reset()
                                            }
                                            prepareAsync()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Playback failed: $e")
                                        Toast.makeText(ctx, "Playback error", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                else {
                                    Toast.makeText(ctx, "Failed to process audio file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    else {
                        mediaPlayer.stop()
                        mediaPlayer.reset()
                        showDialog.value = false
                    }
                })
        )
    } else {
        Text(text = durationText, modifier = Modifier.padding(end = 12.dp))
    }
}

@Composable
private fun PlaybackDialog(
    showDialog: MutableState<Boolean>,
    mediaPlayer: MediaPlayer,
    onStop: () -> Unit
) {
    if (showDialog.value) {
        // State to hold progress (0.0f to 1.0f)
        var currentProgress by remember { mutableFloatStateOf(0f) }

        // Formatted time strings
        var currentPositionText by remember { mutableStateOf("00:00") }
        var totalDurationText by remember { mutableStateOf("00:00") }

        // Update progress every 100ms
        LaunchedEffect(showDialog.value) {
            if (mediaPlayer.isPlaying) {
                val duration = mediaPlayer.duration
                totalDurationText = DateUtils.formatElapsedTime(duration / 1000L)

                while (mediaPlayer.isPlaying) {
                    val current = mediaPlayer.currentPosition
                    currentProgress = if (duration > 0) current.toFloat() / duration.toFloat() else 0f
                    currentPositionText = DateUtils.formatElapsedTime(current / 1000L)
                    delay(100) // Poll every 100ms
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                // If user clicks outside, stop playback
                onStop()
            },
            title = {
                Text(text = stringResource(R.string.playing_recording))
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { currentProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = currentPositionText, style = MaterialTheme.typography.bodySmall)
                        Text(text = totalDurationText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStop()
                    }
                ) {
                    Text(stringResource(R.string.stop))
                }
            }
        )
    }
}

package com.tutpro.baresip

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
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
    val detailsState = remember { callRow.details.toMutableStateList() }
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
            CallDetailsContent(
                LocalContext.current,
                contentPadding,
                callRow,
                detailsState,
                onDelete = { detail ->
                    detailsState.remove(detail)
                    if (detailsState.isEmpty())
                        navController.popBackStack()
                }
            )
        },
    )
}

@Composable
private fun CallDetailsContent(
    ctx: Context,
    contentPadding: PaddingValues,
    callRow: CallRow,
    details: SnapshotStateList<Details>,
    onDelete: (Details) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(top = 16.dp, start = 16.dp, end = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Peer(ctx, callRow)
        Details(ctx, details, onDelete)
    }
}

@Composable
private fun Peer(ctx: Context, callRow: CallRow) {
    val account = Account.ofAor(callRow.aor)!!
    val headerText = stringResource(R.string.peer) + " " +
            Utils.friendlyUri(ctx, callRow.peerUri, account)
    Text(
        text = headerText,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun Details(ctx: Context, details: SnapshotStateList<Details>, onDelete: (Details) -> Unit) {
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
                val durationText = startTime(detail, onDelete)
                Spacer(modifier = Modifier.weight(1f))
                Duration(ctx, detail, durationText)
            }
        }
    }
}

@Composable

private fun startTime(detail: Details, onDelete: (Details) -> Unit): String {
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
    val showDialog = remember { mutableStateOf(false) }
    val positiveAction = remember { mutableStateOf({}) }

    CustomElements.AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = stringResource(R.string.delete_call_alert),
        positiveButtonText = stringResource(R.string.delete),
        onPositiveClicked = {
            CallHistoryNew.remove(detail.startTime, detail.stopTime)
            onDelete(detail)
        },
        negativeButtonText = stringResource(R.string.cancel)
    )

    Text(
        text = startTimeText,
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                positiveAction.value = {
                    CallHistoryNew.remove(startTime, stopTime)
                }
                showDialog.value = true
            }
        )
    )

    return durationText
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Duration(ctx: Context, detail: Details, durationText: String) {

    val showPlaybackDialog = remember { mutableStateOf(false) }
    val showDownloadDialog = remember { mutableStateOf(false) }

    // NOTE: If detail.recording is modified elsewhere, this reference sees the change
    val recording = detail.recording
    val mediaPlayer = remember { MediaPlayer() }
    val scope = rememberCoroutineScope()

    // 1. Setup the File Saver Launcher
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-wav")
    ) { uri ->
        uri?.let { destinationUri ->
            scope.launch(Dispatchers.IO) {
                try {
                    val sourceFile = File(recording[0])
                    if (sourceFile.exists()) {
                        ctx.contentResolver.openOutputStream(destinationUri)?.use { output ->
                            FileInputStream(sourceFile).use { input ->
                                input.copyTo(output)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, ctx.getString(R.string.recording_saved),
                                Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, "Source file not found",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save file: $e")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Save failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    PlaybackDialog(
        showDialog = showPlaybackDialog,
        mediaPlayer = mediaPlayer,
        onStop = {
            if (mediaPlayer.isPlaying)
                mediaPlayer.stop()
            mediaPlayer.reset()
            showPlaybackDialog.value = false
        }
    )

    // 2. Download Confirmation Dialog
    if (showDownloadDialog.value) {
        CustomElements.AlertDialog(
            showDialog = showDownloadDialog,
            title = stringResource(R.string.save_recording),
            message = stringResource(R.string.save_recording_question),
            positiveButtonText = stringResource(R.string.save),
            onPositiveClicked = {
                showDownloadDialog.value = false
                val suggestedName = File(recording[0]).name
                saveLauncher.launch(suggestedName)
            },
            negativeButtonText = stringResource(R.string.cancel)
        )
    }

    val hasRecording = recording[0] != ""
    if (hasRecording) {
        Text(
            text = durationText,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .padding(end = 12.dp)
                .combinedClickable(
                    onLongClick = {
                        showDownloadDialog.value = true
                    },
                    onClick = {
                        if (!mediaPlayer.isPlaying) {
                            mediaPlayer.reset()
                            scope.launch(Dispatchers.IO) {
                                var finalFile: File? = null
                                // RE-EVALUATE STATE INSIDE THE CLICK LISTENER
                                val currentIsRaw = recording[0] != "" && recording[1] != ""
                                val currentIsMerged = recording[0] != "" && recording[1] == ""

                                if (currentIsRaw) {
                                    val fileIn = File(recording[0])
                                    val fileOut = File(recording[1])
                                    // SAFETY CHECK: If the raw file is gone, the background service
                                    // likely finished merging just now.
                                    if (!fileIn.exists()) {
                                        // Try to find the merged file based on naming convention as fallback
                                        val expectedMergedName = "merged_${fileIn.nameWithoutExtension}_${fileOut.nameWithoutExtension}.wav"
                                        val fallbackMerged = File(BaresipService.filesPath + "/recordings", expectedMergedName)
                                        if (fallbackMerged.exists()) {
                                            Log.d(TAG, "Raw file missing, found merged fallback: ${fallbackMerged.name}")
                                            finalFile = fallbackMerged
                                            // Update state to match reality
                                            recording[0] = fallbackMerged.absolutePath
                                            recording[1] = ""
                                        } else {
                                            Log.e(TAG, "Raw file missing and fallback not found: ${recording[0]}")
                                        }
                                    } else {
                                        // Normal Raw processing
                                        val mergedFileName = "merged_${fileIn.nameWithoutExtension}_${fileOut.nameWithoutExtension}.wav"
                                        val mergedFile = File(BaresipService.filesPath + "/recordings", mergedFileName)
                                        if (mergedFile.exists()) {
                                            Log.d(TAG, "Using already merged file: ${mergedFile.name}")
                                            finalFile = mergedFile
                                        } else {
                                            if (Utils.mergeWavFiles(fileIn, fileOut, mergedFile))
                                                finalFile = mergedFile
                                        }

                                        // If merge successful, update state and delete originals
                                        if (finalFile != null && finalFile.exists()) {
                                            recording[0] = finalFile.absolutePath
                                            recording[1] = ""
                                            try {
                                                if (fileIn.exists()) fileIn.delete()
                                                if (fileOut.exists()) fileOut.delete()
                                                CallHistoryNew.save()
                                            } catch (e: Exception) {
                                                Log.w(TAG, "MergeWav: Failed to delete original files: ${e.message}")
                                            }
                                        }
                                    }
                                } else if (currentIsMerged) {
                                    val f = File(recording[0])
                                    if (f.exists()) {
                                        Log.d(TAG, "Using already merged file: ${recording[0]}")
                                        finalFile = f
                                    } else {
                                        Log.e(TAG, "Merged file record exists but file is missing: ${recording[0]}")
                                    }
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
                                                    showPlaybackDialog.value = true
                                                }
                                                setOnCompletionListener {
                                                    showPlaybackDialog.value = false
                                                    it.reset()
                                                }
                                                prepareAsync()
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Playback failed: $e")
                                            Toast.makeText(ctx, "Playback error",
                                                Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(ctx, "Failed to process audio file",
                                            Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            mediaPlayer.stop()
                            mediaPlayer.reset()
                            showPlaybackDialog.value = false
                        }
                    }
                )
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
                        modifier = Modifier.fillMaxWidth().height(8.dp),
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
                    onClick = { onStop() }
                ) {
                    Text(stringResource(R.string.stop))
                }
            }
        )
    }
}

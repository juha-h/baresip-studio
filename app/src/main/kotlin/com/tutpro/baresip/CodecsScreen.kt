package com.tutpro.baresip

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.verticalScrollbar

fun NavGraphBuilder.codecsScreenRoute(navController: NavController) {
    composable(
        route = "codecs/{aor}/{media}",
        arguments = listOf(
            navArgument("aor") { type = NavType.StringType },
            navArgument("media") { type = NavType.StringType })
    ) { backStackEntry ->
        val aor = backStackEntry.arguments?.getString("aor")!!
        val media = backStackEntry.arguments?.getString("media")!!
        val account = UserAgent.ofAor(aor)?.account!!
        CodecsScreen(
            onBack = { navController.popBackStack() },
            checkOnClick = { updatedCodecs ->
                val enabledCodecNames = updatedCodecs.filter { it.enabled.value }.map { it.name }
                val codecList = Utils.implode(enabledCodecNames, ",")
                Log.d(TAG, "Saving codecs for ${account.aor} (${media}): $codecList")
                val success = if (media == "audio")
                    Api.account_set_audio_codecs(account.accp, codecList)
                else
                    Api.account_set_video_codecs(account.accp, codecList)
                if (success == 0) {
                    if (media == "audio")
                        account.audioCodec = enabledCodecNames as ArrayList<String>
                    else
                        account.videoCodec = enabledCodecNames as ArrayList<String>
                    Account.saveAccounts()
                    Log.d("CodecsSave", "Codecs saved successfully.")
                }
                else
                    Log.e(TAG, "Failed to set $aor codecs.")
                navController.popBackStack()
            },
            aor = aor,
            media = media
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodecsScreen(
    onBack: () -> Unit,
    checkOnClick: (List<Codec>) -> Unit,
    aor: String,
    media: String
) {
    val ua = UserAgent.ofAor(aor)!!
    val acc = ua.account
    var currentCodecsState by remember { mutableStateOf<List<Codec>>(emptyList()) }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = LocalCustomColors.current.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalCustomColors.current.background)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (media == "audio")
                                stringResource(R.string.audio_codecs)
                            else
                                stringResource(R.string.video_codecs),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LocalCustomColors.current.primary,
                        navigationIconContentColor = LocalCustomColors.current.onPrimary,
                        titleContentColor = LocalCustomColors.current.onPrimary,
                        actionIconContentColor = LocalCustomColors.current.onPrimary
                    ),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            checkOnClick(currentCodecsState)
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Check"
                            )
                        }
                    }
                )
            }
        },
        content = { contentPadding ->
            CodecsContent(
                contentPadding,
                acc,
                media,
                onUpdateCodecs = { updatedCodecs -> currentCodecsState = updatedCodecs }
            )
        },
    )
}

private val alertTitle = mutableStateOf("")
private val alertMessage = mutableStateOf("")
private val showAlert = mutableStateOf(false)


@Composable
private fun CodecsContent(
    contentPadding: PaddingValues,
    acc: Account,
    media: String,
    onUpdateCodecs: (List<Codec>) -> Unit
) {
    val codecs = remember { mutableStateListOf<Codec>() }

    LaunchedEffect(acc, media) {
        val allCodecs: List<String> = if (media == "audio") {
            Api.audio_codecs().split(",")
        } else {
            Api.video_codecs().split(",").distinct()
        }
        val accCodecs: List<String> = if (media == "audio") {
            acc.audioCodec
        } else {
            acc.videoCodec
        }
        val currentCodecs = mutableListOf<Codec>()
        for (codec in accCodecs)
            currentCodecs.add(Codec(codec, mutableStateOf(true)))
        for (codec in allCodecs)
            if (codec !in accCodecs)
                currentCodecs.add(Codec(codec, mutableStateOf(false)))
        codecs.clear()
        codecs.addAll(currentCodecs)
    }

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
        Codecs(
            codecs = codecs,
            onUpdateCodecs = onUpdateCodecs
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Codecs(codecs: SnapshotStateList<Codec>, onUpdateCodecs: (List<Codec>) -> Unit) {

    val draggableState = rememberDraggableListState(
        onMove = { fromIndex, toIndex ->
            codecs.add(toIndex, codecs.removeAt(fromIndex))
            onUpdateCodecs(codecs.toList())
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
                        modifier = Modifier
                            .fillMaxWidth()
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
                                    } else {
                                        val index = codecs.indexOf(item)
                                        codecs.removeAt(index)
                                        codecs.add(item)
                                    }
                                    onUpdateCodecs(codecs.toList())
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



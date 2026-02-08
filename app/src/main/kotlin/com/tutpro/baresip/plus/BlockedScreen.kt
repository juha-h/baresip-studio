package com.tutpro.baresip.plus

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar
import java.util.GregorianCalendar

fun NavGraphBuilder.blockedScreenRoute(navController: NavController) {
    composable(
        route = "blocked/{request}/{aor}",
        arguments = listOf(
            navArgument("aor") { type = NavType.StringType },
            navArgument("request") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val aor = backStackEntry.arguments?.getString("aor")!!
        val request = backStackEntry.arguments?.getString("request")!!
        BlockedScreen(navController, request, aor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedScreen(navController: NavController, request: String, aor: String) {

    val account = Account.ofAor(aor)!!

    val blocked: MutableState<List<Blocked>> = remember { mutableStateOf(emptyList()) }
    var isBlockedLoaded by remember { mutableStateOf(false) }

    var refreshTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(aor, refreshTrigger) {
        blocked.value = loadBlocked(request, aor)
        isBlockedLoaded = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME)
                refreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = true) {
        navController.navigateUp()
    }

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
                TopAppBar(navController, account, request, blocked)
            }
        },
        content = { contentPadding ->
            if (isBlockedLoaded)
                BlockedContent(
                    LocalContext.current,
                    navController,
                    contentPadding,
                    account, blocked
                )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
    navController: NavController,
    account: Account,
    request: String,
    blocked: MutableState<List<Blocked>>
) {
    var expanded by remember { mutableStateOf(false) }
    val delete = stringResource(R.string.delete)
    val showDialog = remember { mutableStateOf(false) }
    val lastAction = remember { mutableStateOf({}) }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = String.format(stringResource(R.string.blocked_delete_alert), account.text()),
        firstButtonText = stringResource(R.string.cancel),
        lastButtonText = stringResource(R.string.delete),
        onLastClicked = lastAction.value,
    )

    TopAppBar(
        title = {
            Text(
                text = if (request == "invite")
                    stringResource(R.string.blocked_calls)
                else
                    stringResource(R.string.blocked_messages),
                fontWeight = FontWeight.Bold
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            IconButton(
                onClick = { navController.navigateUp() }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
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
                            lastAction.value = {
                                Blocked.clear(account.aor)
                                blocked.value = emptyList()
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
private fun BlockedContent(
    ctx: Context,
    navController: NavController,
    contentPadding: PaddingValues,
    account: Account,
    blocked: MutableState<List<Blocked>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Account(account)
        Blocked(ctx, navController, blocked)
    }
}

@Composable
private fun Account(account: Account) {
    Text(
        text = stringResource(R.string.account) + " " + account.text(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Blocked(ctx: Context, navController: NavController, blocked: MutableState<List<Blocked>>) {
    val showDialog = remember { mutableStateOf(false) }
    val message = remember { mutableStateOf("") }
    val lastButtonText = remember { mutableStateOf("") }
    val lastAction = remember { mutableStateOf({}) }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = message.value,
        firstButtonText = stringResource(R.string.cancel),
        lastButtonText = lastButtonText.value,
        onLastClicked = lastAction.value,
    )

    val lazyListState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp)
            .verticalScrollbar(state = lazyListState)
            .background(MaterialTheme.colorScheme.background),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = blocked.value, key = { blocked -> blocked.timeStamp }) { blocked ->
            val peerUri = blocked.peerUri
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClick = {
                        message.value = String.format(ctx.getString(R.string.blocked_contact_question),
                            peerUri)
                        lastButtonText.value = ctx.getString(R.string.add_contact)
                        lastAction.value = {
                            navController.navigate("baresip_contact/$peerUri/new")
                        }
                        showDialog.value = true
                    })
            ) {
                Text(text = "\u2022",
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                    fontSize = 18.sp)

                Text(text = peerUri.replace("sip:", ""),
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                val calendar = GregorianCalendar()
                calendar.timeInMillis = blocked.timeStamp
                Text(
                    text = Utils.relativeTime(ctx, calendar),
                    fontSize = 12.sp,
                    minLines = 2, maxLines = 2,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    }
}

private fun loadBlocked(request: String, aor: String): MutableList<Blocked> {
    val res = mutableListOf<Blocked>()
    for (i in BaresipService.blocked.indices.reversed()) {
        val b = BaresipService.blocked[i]
        if (b.aor == aor && b.request == request) {
            res.add(Blocked("", b.peerUri, "", b.timeStamp))
        }
    }
    Log.d(TAG, "Loaded ${res.size} blocked $request requests")
    return res
}

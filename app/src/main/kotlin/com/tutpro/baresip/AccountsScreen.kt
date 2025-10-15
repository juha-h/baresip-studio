package com.tutpro.baresip

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.LabelText
import com.tutpro.baresip.CustomElements.verticalScrollbar

fun NavGraphBuilder.accountsScreenRoute(navController: NavController) {
    composable("accounts") {
        AccountsScreen(navController)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(navController: NavController) {
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
                            text = stringResource(R.string.accounts),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LocalCustomColors.current.primary,
                        navigationIconContentColor = LocalCustomColors.current.onPrimary,
                        titleContentColor = LocalCustomColors.current.onPrimary,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            }
        },
        bottomBar = { NewAccount(navController) },
        content = { contentPadding ->
            AccountsContent(contentPadding, navController)
        },
    )
}


@Composable
fun AccountsContent(contentPadding: PaddingValues, navController: NavController) {

    val ctx = LocalContext.current

    val showDialog = remember { mutableStateOf(false) }
    val message = remember { mutableStateOf("") }
    val positiveAction = remember { mutableStateOf({}) }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = message.value,
        positiveButtonText = stringResource(R.string.delete),
        onPositiveClicked = positiveAction.value,
        negativeButtonText = stringResource(R.string.cancel),
    )

    val showAccounts = remember { mutableStateOf(true) }

    if (showAccounts.value && BaresipService.uas.value.isNotEmpty()) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                .verticalScrollbar(scrollState)
                .verticalScroll(state = scrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (ua in BaresipService.uas.value) {
                val account = ua.account
                val aor = account.aor
                val text = account.text()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = text,
                        fontSize = 20.sp,
                        color = LocalCustomColors.current.itemText,
                        modifier = Modifier.weight(1f).padding(start = 10.dp)
                            .clickable {
                                navController.navigate("account/$aor/old")
                            }
                    )
                    SmallFloatingActionButton(
                        onClick = {
                            message.value = String.format(
                                ctx.getString(R.string.delete_account),
                                text
                            )
                            positiveAction.value = {
                                CallHistoryNew.clear(aor)
                                Message.clearMessagesOfAor(aor)
                                ua.remove()
                                Api.ua_destroy(ua.uap)
                                Account.saveAccounts()
                                showAccounts.value = false
                                showAccounts.value = true
                            }
                            showDialog.value = true
                        },
                        containerColor = LocalCustomColors.current.background,
                        contentColor = LocalCustomColors.current.secondary
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NewAccount(navController: NavController) {

    val alertTitle = remember { mutableStateOf("") }
    val alertMessage = remember { mutableStateOf("") }
    val showAlert = remember { mutableStateOf(false) }

    if (showAlert.value)
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            positiveButtonText = stringResource(R.string.ok),
        )

    fun createNew(ctx: Context, newAor: String): Account? {

        val aor = if (newAor.startsWith("sip:"))
            newAor
        else
            "sip:$newAor"

        if (!Utils.checkAor(aor)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value =
                String.format(ctx.getString(R.string.invalid_aor), aor.split(":")[1])
            showAlert.value = true
            return null
        }

        if (Account.ofAor(aor) != null) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value =
                String.format(ctx.getString(R.string.account_exists), aor.split(":")[1])
            showAlert.value = true
            return null
        }

        val ua = UserAgent.uaAlloc(
            "<$aor>;stunserver=\"stun:stun.l.google.com:19302\";regq=0.5;pubint=0;regint=0;mwi=no"
        )
        if (ua == null) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = ctx.getString(R.string.account_allocation_failure)
            showAlert.value = true
            return null
        }

        // Api.account_debug(ua.account.accp)
        val acc = ua.account
        Log.d(TAG, "Allocated UA ${ua.uap} with SIP URI ${acc.luri}")
        Account.saveAccounts()
        return acc
    } // createNew

    var newAor by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ctx = LocalContext.current
        OutlinedTextField(
            value = newAor,
            placeholder = { Text(text = stringResource(R.string.new_account)) },
            onValueChange = { newAor = it },
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
                .verticalScroll(rememberScrollState())
                .clickable {
                    alertTitle.value = ctx.getString(R.string.new_account)
                    alertMessage.value = ctx.getString(R.string.accounts_help)
                    showAlert.value = true
                },
            singleLine = false,
            trailingIcon = {
                if (newAor.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier.clickable { newAor = "" }
                    )
                }
            },
            label = { LabelText(stringResource(R.string.new_account)) },
            textStyle = TextStyle(fontSize = 18.sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
            )
        )
        Image(
            painter = painterResource(id = R.drawable.plus),
            contentDescription = "Add",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp)
                .clickable(
                    onClick = {
                        val account = createNew(ctx, newAor.trim())
                        if (account != null) {
                            navController.navigate("account/${account.aor}/new")
                            newAor = ""
                            focusManager.clearFocus()
                        }
                    }
                )
        )
    }
}


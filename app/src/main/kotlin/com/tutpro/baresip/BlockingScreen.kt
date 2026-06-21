package com.tutpro.baresip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.verticalScrollbar

fun NavGraphBuilder.blockingScreenRoute(navController: NavController) {
    composable(
        route = "blocking/{aor}",
        arguments = listOf(navArgument("aor") { type = NavType.StringType })
    ) { backStackEntry ->
        val aor = backStackEntry.arguments?.getString("aor")!!
        val viewModel = viewModel<AccountViewModel>()
        val ua = UserAgent.ofAor(aor)!!
        BlockingScreen(navController, viewModel, ua)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockingScreen(navController: NavController, viewModel: AccountViewModel, ua: UserAgent) {
    val acc = ua.account
    var rules by remember { mutableStateOf(BaresipService.blockRules.toList()) }

    remember {
        viewModel.loadAccount(acc)
        true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                Spacer(Modifier.statusBarsPadding())
                TopAppBar(
                    title = {
                        Text(text = stringResource(R.string.blocking), fontWeight = FontWeight.Bold)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
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
        bottomBar = { NewRule(onRuleAdded = { rules = BaresipService.blockRules.toList() }) },
        content = { contentPadding ->
            BlockingContent(
                contentPadding,
                viewModel,
                rules,
                acc,
                onRuleDeleted = { rules = BaresipService.blockRules.toList() }
            )
        },
    )
}

@Composable
fun BlockingContent(
    contentPadding: PaddingValues,
    viewModel: AccountViewModel,
    rules: List<BlockRule>,
    acc: Account,
    onRuleDeleted: () -> Unit
) {
    val ctx = LocalContext.current
    val alertTitle = remember { mutableStateOf("") }
    val alertMessage = remember { mutableStateOf("") }
    val showAlert = remember { mutableStateOf(false) }

    if (showAlert.value)
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            lastButtonText = stringResource(R.string.ok),
        )

    @Composable
    fun BlockUnknown() {
        val blockUnknownTitle = stringResource(R.string.block_unknown)
        val blockUnknownHelp = stringResource(R.string.block_unknown_help)
        val block by viewModel.blockUnknown.collectAsState()
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = blockUnknownTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = blockUnknownTitle
                        alertMessage.value = blockUnknownHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            Switch(
                checked = block,
                onCheckedChange = { 
                    viewModel.blockUnknown.value = it
                    acc.blockUnknown = it
                    Account.saveAccounts()
                }
            )
        }
    }

    @Composable
    fun BlockHidden(acc: Account) {
        val blockHiddenTitle = stringResource(R.string.block_hidden)
        val blockHiddenHelp = stringResource(R.string.block_hidden_help)
        val block by viewModel.blockHidden.collectAsState()
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = blockHiddenTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = blockHiddenTitle
                        alertMessage.value = blockHiddenHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            Switch(
                checked = block,
                onCheckedChange = { 
                    viewModel.blockHidden.value = it 
                    acc.blockHidden = it
                    Account.saveAccounts()
                }
            )
        }
    }

    val showDialog = remember { mutableStateOf(false) }
    val message = remember { mutableStateOf("") }
    val lastAction = remember { mutableStateOf({}) }

    AlertDialog(
        showDialog = showDialog,
        title = stringResource(R.string.confirmation),
        message = message.value,
        firstButtonText = stringResource(R.string.cancel),
        lastButtonText = stringResource(R.string.delete),
        onLastClicked = lastAction.value,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        BlockUnknown()
        BlockHidden(acc)
        Spacer(Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.blocking_list),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (rules.isNotEmpty()) {
            val lazyListState = rememberLazyListState()
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScrollbar(state = lazyListState),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(rules) { rule ->
                    val pattern = if (Utils.checkUri(rule.pattern))
                        Utils.friendlyUri(ctx, rule.pattern, acc)
                    else
                        rule.pattern
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "\u2022",
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pattern,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    val deleteRuleMessage = stringResource(R.string.blocking_delete_alert)
                        IconButton(
                            modifier = Modifier.padding(end = 8.dp).size(32.dp),
                            onClick = {
                                message.value = String.format(
                                    deleteRuleMessage,
                                    pattern
                                )
                                lastAction.value = {
                                    BaresipService.blockRules.remove(rule)
                                    BlockRule.save()
                                    onRuleDeleted()
                                }
                                showDialog.value = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp).scale(scaleX = 1f, scaleY = 0.5f)
                            )
                        }
                    }
                }
            }
        }
        else
            Spacer(Modifier.weight(1f))
    }
}

@Composable
fun NewRule(onRuleAdded: () -> Unit) {
    val alertTitle = remember { mutableStateOf("") }
    val alertMessage = remember { mutableStateOf("") }
    val showAlert = remember { mutableStateOf(false) }

    if (showAlert.value)
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            lastButtonText = stringResource(R.string.ok),
        )

    var pattern by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val ruleHelp = stringResource(R.string.blocking_rule_help)
            val newRuleTitle = stringResource(R.string.new_blocking_rule)
            OutlinedTextField(
                value = pattern,
                placeholder = { Text(text = stringResource(R.string.new_blocking_rule)) },
                onValueChange = { pattern = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .clickable {
                        alertTitle.value = newRuleTitle
                        alertMessage.value = ruleHelp
                        showAlert.value = true
                    },
                singleLine = true,
                trailingIcon = {
                    if (pattern.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.Clear,
                            contentDescription = "Clear",
                            modifier = Modifier.clickable { pattern = "" },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                label = { Text(stringResource(R.string.new_blocking_rule)) },
                textStyle = TextStyle(fontSize = 18.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            SmallFloatingActionButton(
                modifier = Modifier.offset(y = 2.dp),
                onClick = {
                    if (pattern.trim().isNotEmpty()) {
                        if (!BlockRule.exists(pattern.trim())) {
                            BaresipService.blockRules.add(BlockRule(pattern.trim()))
                            BlockRule.save()
                            onRuleAdded()
                        }
                        pattern = ""
                        focusManager.clearFocus()
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    modifier = Modifier.size(36.dp),
                    contentDescription = stringResource(R.string.add)
                )
            }
        }
    }
}

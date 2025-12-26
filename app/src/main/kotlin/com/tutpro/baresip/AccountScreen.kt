package com.tutpro.baresip

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.net.URL
import java.util.Locale

fun NavGraphBuilder.accountScreenRoute(navController: NavController) {
    composable(
        route = "account/{aor}/{kind}",
        arguments = listOf(
            navArgument("aor") { type = NavType.StringType },
            navArgument("kind") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val ctx = LocalContext.current
        val viewModel = viewModel<AccountViewModel>()
        val aor = backStackEntry.arguments?.getString("aor")!!
        val kind = backStackEntry.arguments?.getString("kind")!!
        val ua = UserAgent.ofAor(aor)!!
        AccountScreen(
            viewModel = viewModel,
            navController = navController,
            onBack = { navController.popBackStack() },
            checkOnClick = {
                val ok = checkOnClick(ctx, viewModel, ua)
                if (ok) {
                    if (reRegister) ua.reRegister()
                    navController.popBackStack()
                }
            },
            aor = aor,
            kind = kind
        )
    }
}

private var keyboardController: SoftwareKeyboardController? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountScreen(
    viewModel: AccountViewModel,
    navController: NavController,
    onBack: () -> Unit,
    checkOnClick: () -> Unit,
    aor: String,
    kind: String
) {
    val ua = UserAgent.ofAor(aor)!!
    val acc = ua.account

    var isAccountAvailable by remember { mutableStateOf(false) }
    var isAccountLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(kind, acc) {
        if (kind == "new")
            initAccountFromConfig(acc) { isAccountAvailable = true }
        else
            isAccountAvailable = true
    }

    if (isAccountAvailable)
        LaunchedEffect(acc) {
            viewModel.loadAccount(acc)
            isAccountLoaded = true
        }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = acc.text(),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    actions = {
                        IconButton(onClick = checkOnClick) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Check"
                            )
                        }
                    },
                )
            }
        }
    ) { contentPadding ->
        if (isAccountLoaded)
            AccountContent(viewModel, navController, contentPadding, ua)
        else
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
    }
}

private val password = mutableStateOf("")
private val showPasswordDialog = mutableStateOf(false)

private val alertTitle = mutableStateOf("")
private val alertMessage = mutableStateOf("")
private val showAlert = mutableStateOf(false)

private var reRegister = false

@Composable
private fun AccountContent(
    viewModel: AccountViewModel,
    navController: NavController,
    contentPadding: PaddingValues,
    ua: UserAgent
) {
    val ctx = LocalContext.current
    val aor = ua.account.aor

    val mediaNat by viewModel.mediaNat.collectAsState()
    val showStun by remember { derivedStateOf { mediaNat.isNotEmpty() } }

    @Composable
    fun AoR() {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            OutlinedTextField(
                value = ua.account.luri,
                enabled = false,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 18.sp),
                label = {
                    Text(text = stringResource(R.string.sip_uri), fontWeight = FontWeight.Bold)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }
    }

    @Composable
    fun Nickname() {
        val nickNameTitle = stringResource(R.string.nickname)
        val nickNameHelp = stringResource(R.string.account_nickname_help)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val nickName by viewModel.nickName.collectAsState()
            OutlinedTextField(
                value = nickName,
                placeholder = { Text(nickNameTitle) },
                onValueChange = { viewModel.nickName.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = nickNameTitle
                        alertMessage.value = nickNameHelp
                        showAlert.value = true
                    },
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(nickNameTitle) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text),
            )
        }
    }

    @Composable
    fun DisplayName() {
        val displayNameTitle = stringResource(R.string.display_name)
        val displayNameHelp = stringResource(R.string.display_name_help)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val displayName by viewModel.displayName.collectAsState()
            OutlinedTextField(
                value = displayName,
                placeholder = { Text(displayNameTitle) },
                onValueChange = { viewModel.displayName.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = displayNameTitle
                        alertMessage.value = displayNameHelp
                        showAlert.value = true
                    },
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(displayNameTitle) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text
                ),
            )
        }
    }

    @Composable
    fun AuthUser() {
        val authenticationUserNameTitle = stringResource(R.string.authentication_username)
        val authenticationUserNameHelp = stringResource(R.string.authentication_username_help)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val authUser by viewModel.authUser.collectAsState()
            OutlinedTextField(
                value = authUser,
                placeholder = { Text(authenticationUserNameTitle) },
                onValueChange = { viewModel.authUser.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = authenticationUserNameTitle
                        alertMessage.value = authenticationUserNameHelp
                        showAlert.value = true
                    },
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(authenticationUserNameTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun AuthPass() {
        val authenticationPasswordTitle = stringResource(R.string.authentication_password)
        val authenticationPasswordHelp = stringResource(R.string.authentication_password_help)
        val showPassword = remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val authPass by viewModel.authPass.collectAsState()
            OutlinedTextField(
                value = authPass,
                placeholder = { Text(authenticationPasswordTitle) },
                onValueChange = { viewModel.authPass.value = it },
                singleLine = true,
                visualTransformation = if (showPassword.value)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = {
                        showPassword.value = !showPassword.value
                    }) {
                        Icon(
                            if (showPassword.value)
                                Icons.Filled.Visibility
                            else
                                Icons.Filled.VisibilityOff,
                            contentDescription = "Visibility",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = authenticationPasswordTitle
                        alertMessage.value = authenticationPasswordHelp
                        showAlert.value = true
                    },
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(authenticationPasswordTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun AskPassword(ctx: Context, navController: NavController, ua: UserAgent) {
        CustomElements.PasswordDialog(
            ctx = ctx,
            showPasswordDialog = showPasswordDialog,
            password = password,
            keyboardController = keyboardController,
            title = stringResource(R.string.authentication_password),
            okAction = {
                if (password.value != "") {
                    BaresipService.aorPasswords[ua.account.aor] = password.value
                    Api.account_set_auth_pass(ua.account.accp, password.value)
                    password.value = ""
                    ua.reRegister()
                    navController.popBackStack()
                }
            },
            cancelAction = {
                ua.reRegister()
                navController.popBackStack()
            }
        )
    }

    @Composable
    fun Outbound() {
        val outboundProxiesTitle = stringResource(R.string.outbound_proxies)
        val outboundProxiesHelp = stringResource(R.string.outbound_proxies_help)
        Text(text = stringResource(R.string.outbound_proxies),
            fontSize = 18.sp,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable {
                    alertTitle.value = outboundProxiesTitle
                    alertMessage.value = outboundProxiesHelp
                    showAlert.value = true
                }
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val outbound1 by viewModel.outbound1.collectAsState()
            val sipUriOfProxyServerTitle = stringResource(R.string.sip_uri_of_proxy_server)
            OutlinedTextField(
                value = outbound1,
                placeholder = { Text(sipUriOfProxyServerTitle) },
                onValueChange = { viewModel.outbound1.value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(sipUriOfProxyServerTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val outbound2 by viewModel.outbound2.collectAsState()
            val anotherProxyServerTitle = stringResource(R.string.sip_uri_of_another_proxy_server)
            OutlinedTextField(
                value = outbound2,
                placeholder = { Text(anotherProxyServerTitle) },
                onValueChange = { viewModel.outbound2.value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(anotherProxyServerTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun Register() {
        val registerTitle = stringResource(R.string.register)
        val registerHelp = stringResource(R.string.register_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = registerTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = registerTitle
                        alertMessage.value = registerHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp,
            )
            val register by viewModel.register.collectAsState()
            Switch(
                checked = register,
                onCheckedChange = { viewModel.register.value = it }
            )
        }
    }

    @Composable
    fun RegInt() {
        val regIntTitle = stringResource(R.string.reg_int)
        val regIntHelp = stringResource(R.string.reg_int_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val regInt by viewModel.regInt.collectAsState()
            OutlinedTextField(
                value = regInt,
                placeholder = { Text(regIntTitle) },
                onValueChange = { viewModel.regInt.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = regIntTitle
                        alertMessage.value = regIntHelp
                        showAlert.value = true
                    },
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(regIntTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }

    @Composable
    fun CheckOrigin() {
        val checkOriginTitle = stringResource(R.string.check_origin)
        val checkOriginHelp = stringResource(R.string.check_origin_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = checkOriginTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = checkOriginTitle
                        alertMessage.value = checkOriginHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp,
            )
            val checkOrigin by viewModel.checkOrigin.collectAsState()
            Switch(
                checked = checkOrigin,
                onCheckedChange = { viewModel.checkOrigin.value = it }
            )
        }
    }

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
                onCheckedChange = { viewModel.blockUnknown.value = it }
            )
        }
    }

    @Composable
    fun AudioCodecs(navController: NavController, aor: String) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = stringResource(R.string.audio_codecs),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        val route = "codecs/$aor/audio"
                        navController.navigate(route)
                    },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    fun MediaEnc() {
        val mediaEncryptionTitle = stringResource(R.string.media_encryption)
        val mediaEncryptionHelp = stringResource(R.string.media_encryption_help)
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = mediaEncryptionTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = mediaEncryptionTitle
                        alertMessage.value = mediaEncryptionHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val isDropDownExpanded = remember { mutableStateOf(false) }
            val mediaEnc by viewModel.mediaEnc.collectAsState()
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = mediaEncMap[mediaEnc]!!)
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = {
                        isDropDownExpanded.value = false
                    }) {
                    var index = 0
                    mediaEncMap.forEach {
                        DropdownMenuItem(text = { Text(text = it.value) },
                            onClick = {
                                isDropDownExpanded.value = false
                                viewModel.mediaEnc.value = it.key
                            }
                        )
                        if (index < 4)
                            HorizontalDivider(thickness = 1.dp)
                        index++
                    }
                }
            }
        }
    }

    @Composable
    fun MediaNat() {
        val mediaNatTitle = stringResource(R.string.media_nat)
        val mediaNatHelp = stringResource(R.string.media_nat_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(mediaNatTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = mediaNatTitle
                        alertMessage.value = mediaNatHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val isDropDownExpanded = remember { mutableStateOf(false) }
            val mediaNat by viewModel.mediaNat.collectAsState()
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = mediaNatMap[mediaNat]!!)
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = {
                        isDropDownExpanded.value = false
                    }
                ) {
                    var index = 0
                    mediaNatMap.forEach {
                        DropdownMenuItem(text = { Text(text = it.value) },
                            onClick = {
                                isDropDownExpanded.value = false
                                viewModel.mediaNat.value = it.key
                            }
                        )
                        if (index < 3)
                            HorizontalDivider(thickness = 1.dp)
                        index++
                    }
                }
            }
        }
    }

    @Composable
    fun StunServer() {
        val stunServerTitle = stringResource(R.string.stun_server)
        val stunServerHelp = stringResource(R.string.stun_server_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val stunServer by viewModel.stunServer.collectAsState()
            OutlinedTextField(
                value = stunServer,
                placeholder = { Text(stunServerTitle) },
                onValueChange = { viewModel.stunServer.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = stunServerTitle
                        alertMessage.value = stunServerHelp
                        showAlert.value = true
                    },
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(stunServerTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun StunUser() {
        val stunUsernameTitle = stringResource(R.string.stun_username)
        val stunUsernameHelp = stringResource(R.string.stun_username_help)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val stunUser by viewModel.stunUser.collectAsState()
            OutlinedTextField(
                value = stunUser,
                placeholder = { Text(stunUsernameTitle) },
                onValueChange = { viewModel.stunUser.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = stunUsernameTitle
                        alertMessage.value = stunUsernameHelp
                        showAlert.value = true
                    },
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(stunUsernameTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun StunPass() {
        val stunPasswordTitle = stringResource(R.string.stun_password)
        val stunPasswordHelp = stringResource(R.string.stun_password_help)
        val showPassword = remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val stunPass by viewModel.stunPass.collectAsState()
            OutlinedTextField(
                value = stunPass,
                placeholder = { Text(stunPasswordTitle) },
                onValueChange = { viewModel.stunPass.value = it },
                singleLine = true,
                visualTransformation = if (showPassword.value)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword.value = !showPassword.value }) {
                        Icon(
                            if (showPassword.value)
                                Icons.Filled.Visibility
                            else
                                Icons.Filled.VisibilityOff,
                            contentDescription = "Visibility",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = stunPasswordTitle
                        alertMessage.value = stunPasswordHelp
                        showAlert.value = true
                    },
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(stunPasswordTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun RtcpMux() {
        val rtcpMuxTitle = stringResource(R.string.rtcp_mux)
        val rtcpMuxHelp = stringResource(R.string.rtcp_mux_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val rtcpMux by viewModel.rtcpMux.collectAsState()
            Text(text = rtcpMuxTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = rtcpMuxTitle
                        alertMessage.value = rtcpMuxHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            Switch(
                checked = rtcpMux,
                onCheckedChange = { viewModel.rtcpMux.value = it }
            )
        }
    }

    @Composable
    fun Rel100() {
        val rel100Title = stringResource(R.string.rel_100)
        val rel100Help = stringResource(R.string.rel_100_help)
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val rel100 by viewModel.rel100.collectAsState()
            Text(
                text = rel100Title,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = rel100Title
                        alertMessage.value = rel100Help
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            Switch(
                checked = rel100,
                onCheckedChange = { viewModel.rel100.value = it }
            )
        }
    }

    @Composable
    fun Dtmf() {
        val dtmfModeTitle = stringResource(R.string.dtmf_mode)
        val dtmfModeHelp = stringResource(R.string.dtmf_mode_help)
        val dtmfInbandText = stringResource(R.string.dtmf_inband)
        val dtmfInfoText = stringResource(R.string.dtmf_info)
        val dtmfAutoText = stringResource(R.string.dtmf_auto)
        val dtmfMode by viewModel.dtmfMode.collectAsState()
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val dtmfModeMap = mapOf(Api.DTMFMODE_RTP_EVENT to dtmfInbandText,
                Api.DTMFMODE_SIP_INFO to dtmfInfoText,
                Api.DTMFMODE_AUTO to dtmfAutoText)
            Text(text = dtmfModeTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = dtmfModeTitle
                        alertMessage.value = dtmfModeHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val isDropDownExpanded = remember { mutableStateOf(false) }
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = dtmfModeMap[dtmfMode]!!)
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = {
                        isDropDownExpanded.value = false
                    }) {
                    var index = 0
                    dtmfModeMap.forEach {
                        DropdownMenuItem(text = { Text(text = it.value) },
                            onClick = {
                                isDropDownExpanded.value = false
                                viewModel.dtmfMode.value = it.key
                            }
                        )
                        if (index < 2)
                            HorizontalDivider(thickness = 1.dp)
                        index++
                    }
                }
            }
        }
    }

    val manualText = stringResource(R.string.manual)
    val autoText = stringResource(R.string.auto)

    @Composable
    fun Answer() {
        val answerModeTitle = stringResource(R.string.answer_mode)
        val answerModeHelp = stringResource(R.string.answer_mode_help)
        val answerMode by viewModel.answerMode.collectAsState()
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val answerModeMap = mapOf(Api.ANSWERMODE_MANUAL to manualText,
                Api.ANSWERMODE_AUTO to autoText)
            Text(text = answerModeTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = answerModeTitle
                        alertMessage.value = answerModeHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val isDropDownExpanded = remember { mutableStateOf(false) }
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isDropDownExpanded.value = true }
                ) {
                    Text(text = answerModeMap[answerMode]!!)
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = { isDropDownExpanded.value = false }
                ) {
                    var index = 0
                    answerModeMap.forEach {
                        DropdownMenuItem(text = { Text(text = it.value) },
                            onClick = {
                                isDropDownExpanded.value = false
                                viewModel.answerMode.value = it.key
                            })
                        if (index < 1)
                            HorizontalDivider(thickness = 1.dp)
                        index++
                    }
                }
            }
        }
    }

    @Composable
    fun Redirect() {
        val redirectModeTitle = stringResource(R.string.redirect_mode)
        val redirectModeHelp = stringResource(R.string.redirect_mode_help)
        val autoRedirect by viewModel.autoRedirect.collectAsState()
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val redirectModeMap = mapOf(false to manualText,
                true to autoText)
            Text(text = redirectModeTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = redirectModeTitle
                        alertMessage.value = redirectModeHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            val isDropDownExpanded = remember { mutableStateOf(false) }
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isDropDownExpanded.value = true }
                ) {
                    Text(text = redirectModeMap[autoRedirect]!!)
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = { isDropDownExpanded.value = false }
                ) {
                    var index = 0
                    redirectModeMap.forEach {
                        DropdownMenuItem(
                            text = { Text(text = it.value) },
                            onClick = {
                                isDropDownExpanded.value = false
                                viewModel.autoRedirect.value = it.key
                            }
                        )
                        if (index < 1)
                            HorizontalDivider(thickness = 1.dp)
                        index++
                    }
                }
            }
        }
    }

    @Composable
    fun Voicemail() {
        val voicemailUriTitle = stringResource(R.string.voicemail_uri)
        val voicemailUriHelp = stringResource(R.string.voicemain_uri_help)
        val vmUri by viewModel.vmUri.collectAsState()
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            OutlinedTextField(
                value = vmUri,
                placeholder = { Text(voicemailUriTitle) },
                onValueChange = { viewModel.vmUri.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = voicemailUriTitle
                        alertMessage.value = voicemailUriHelp
                        showAlert.value = true
                    },
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(voicemailUriTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun CountryCode() {
        val countryCodeTitle = stringResource(R.string.country_code)
        val countryCodeHelp = stringResource(R.string.country_code_help)
        val countryCode by viewModel.countryCode.collectAsState()
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            OutlinedTextField(
                value = countryCode,
                placeholder = { Text(countryCodeTitle) },
                onValueChange = { viewModel.countryCode.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = countryCodeTitle
                        alertMessage.value = countryCodeHelp
                        showAlert.value = true
                    },
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(countryCodeTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun TelProvider() {
        val telephonyProviderTitle = stringResource(R.string.telephony_provider)
        val telephonyProviderHelp = stringResource(R.string.telephony_provider_help)
        val telProvider by viewModel.telProvider.collectAsState()
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            OutlinedTextField(
                value = telProvider,
                placeholder = { Text(telephonyProviderTitle) },
                onValueChange = { viewModel.telProvider.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        alertTitle.value = telephonyProviderTitle
                        alertMessage.value = telephonyProviderHelp
                        showAlert.value = true
                    },
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp),
                label = { Text(telephonyProviderTitle) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun NumericKeypad() {
        val numericKeypadTitle = stringResource(R.string.numeric_keypad)
        val numericKeypadHelp = stringResource(R.string.numeric_keypad_help)
        val numericKeypad by viewModel.numericKeypad.collectAsState()
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = numericKeypadTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = numericKeypadTitle
                        alertMessage.value = numericKeypadHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            Switch(
                checked = numericKeypad,
                onCheckedChange = { viewModel.numericKeypad.value = it }
            )
        }
    }

    @Composable
    fun DefaultAccount() {
        val defaultAccountTitle = stringResource(R.string.default_account)
        val defaultAccountHelp = stringResource(R.string.default_account_help)
        val defaultAccount by viewModel.defaultAccount.collectAsState()
        Row(
            Modifier.fillMaxWidth().padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = defaultAccountTitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        alertTitle.value = defaultAccountTitle
                        alertMessage.value = defaultAccountHelp
                        showAlert.value = true
                    },
                fontSize = 18.sp
            )
            Switch(
                checked = defaultAccount,
                onCheckedChange = { viewModel.defaultAccount.value = it }
            )
        }
    }

    if (showAlert.value) {
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            positiveButtonText = stringResource(R.string.ok),
        )
    }

    keyboardController = LocalSoftwareKeyboardController.current

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp)
            .verticalScrollbar(scrollState)
            .verticalScroll(state = scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AoR()
        Nickname()
        DisplayName()
        AuthUser()
        AuthPass()
        if (showPasswordDialog.value)
            AskPassword(ctx, navController, ua)
        Outbound()
        Register()
        if (viewModel.register.collectAsState().value) {
            RegInt()
            CheckOrigin()
        }
        BlockUnknown()
        AudioCodecs(navController, aor)
        MediaEnc()
        MediaNat()
        if (showStun) {
            StunServer()
            StunUser()
            StunPass()
        }
        RtcpMux()
        Rel100()
        Dtmf()
        Answer()
        Redirect()
        Voicemail()
        CountryCode()
        TelProvider()
        NumericKeypad()
        DefaultAccount()
    }
}

private fun checkOnClick(ctx: Context, viewModel: AccountViewModel, ua: UserAgent): Boolean {

    val acc = ua.account
    val noticeTitle = ctx.getString(R.string.notice)

    val nn = viewModel.nickName.value.trim()
    if (nn != acc.nickName) {
        if (Account.checkDisplayName(nn)) {
            if (nn == "" || Account.uniqueNickName(nn)) {
                acc.nickName = nn
                Log.d(TAG, "New nickname is ${acc.nickName}")
            }
            else {
                alertTitle.value = noticeTitle
                alertMessage.value = String.format(ctx.getString(R.string.non_unique_account_nickname), nn)
                showAlert.value = true
                return false
            }
        }
        else {
            alertTitle.value = noticeTitle
            alertMessage.value = String.format(ctx.getString(R.string.invalid_account_nickname), nn)
            showAlert.value = true
            return false
        }
    }

    val dn = viewModel.displayName.value.trim()
    if (dn != acc.displayName) {
        if (Account.checkDisplayName(dn)) {
            if (Api.account_set_display_name(acc.accp, dn) == 0) {
                acc.displayName = Api.account_display_name(acc.accp)
                Log.d(TAG, "New display name is ${acc.displayName}")
            } else {
                Log.e(TAG, "Setting of display name failed")
            }
        }
        else {
            alertTitle.value = noticeTitle
            alertMessage.value = String.format(ctx.getString(R.string.invalid_display_name), dn)
            showAlert.value = true
            return false
        }
    }

    val au = viewModel.authUser.value.trim()
    if (au != acc.authUser) {
        if (Account.checkAuthUser(au)) {
            if (Api.account_set_auth_user(acc.accp, au) == 0) {
                acc.authUser = Api.account_auth_user(acc.accp)
                Log.d(TAG, "New auth user is ${acc.authUser}")
                if (acc.regint > 0)
                    reRegister = true
            }
            else {
                Log.e(TAG, "Setting of auth user failed")
            }
        }
        else {
            alertTitle.value = noticeTitle
            alertMessage.value = String.format(ctx.getString(R.string.invalid_authentication_username), au)
            showAlert.value = true
            return false
        }
    }

    val ap = viewModel.authPass.value.trim()
    if (ap != "") {
        if (ap != acc.authPass) {
            if (Account.checkAuthPass(ap)) {
                if (Api.account_set_auth_pass(acc.accp, ap) == 0) {
                    acc.authPass = Api.account_auth_pass(acc.accp)
                    if (acc.regint > 0)
                        reRegister = true
                }
                else
                    Log.e(TAG, "Setting of auth pass failed")
                BaresipService.aorPasswords.remove(acc.aor)
            }
            else {
                alertTitle.value = noticeTitle
                alertMessage.value = String.format(ctx.getString(R.string.invalid_authentication_password), ap)
                showAlert.value = true
                return false
            }
        }
        else
            BaresipService.aorPasswords.remove(acc.aor)
    }
    else { // ap == ""
        if (acc.authPass != NO_AUTH_PASS && acc.authPass != BaresipService.aorPasswords[acc.aor])
            if (Api.account_set_auth_pass(acc.accp, "") == 0) {
                acc.authPass = NO_AUTH_PASS
                BaresipService.aorPasswords[acc.aor] = NO_AUTH_PASS
            }
    }

    val ob = ArrayList<String>()
    var ob1 = viewModel.outbound1.value.trim().replace(" ", "")
    if (ob1 != "") {
        if (!ob1.startsWith("sip:"))
            ob1 = "sip:$ob1"
        if (checkOutboundUri(ob1)) {
            ob.add(ob1)
        }
        else {
            alertTitle.value = noticeTitle
            alertMessage.value = String.format(ctx.getString(R.string.invalid_proxy_server_uri), ob1)
            showAlert.value = true
            return false
        }
    }
    var ob2 = viewModel.outbound2.value.trim().replace(" ", "")
    if (ob2 != "") {
        if (!ob2.startsWith("sip:"))
            ob2 = "sip:$ob2"
        if (checkOutboundUri(ob2))
            ob.add(ob2)
        else {
            alertTitle.value = noticeTitle
            alertMessage.value = String.format(ctx.getString(R.string.invalid_proxy_server_uri), ob2)
            showAlert.value = true
            return false
        }
    }
    if (ob != acc.outbound) {
        for (i in 0..1) {
            val uri = if (ob.size > i)
                ob[i]
            else
                ""
            if (Api.account_set_outbound(acc.accp, uri, i) != 0)
                Log.e(TAG, "Setting of outbound proxy $i uri '$uri' failed")
        }
        Log.d(TAG, "New outbound proxies are $ob")
        acc.outbound = ob
        if (ob.isEmpty())
            Api.account_set_sipnat(acc.accp, "")
        else
            Api.account_set_sipnat(acc.accp, "outbound")
        if (acc.regint > 0)
            reRegister = true
    }

    val regInt = try {
        viewModel.regInt.value.trim().toInt()
    } catch (_: NumberFormatException) {
        0
    }
    if (regInt !in 60..3600) {
        alertTitle.value = noticeTitle
        alertMessage.value = String.format(ctx.getString(R.string.invalid_reg_int), "$regInt")
        showAlert.value = true
        return false
    }
    val reReg = (viewModel.register.value != acc.regint > 0) ||
            (viewModel.register.value && regInt != acc.configuredRegInt)
    if (reReg) {
        if (Api.account_set_regint(acc.accp,
                if (viewModel.register.value) regInt else 0) != 0) {
            Log.e(TAG, "Setting of regint failed")
        } else {
            acc.regint = Api.account_regint(acc.accp)
            acc.configuredRegInt = regInt
            Log.d(TAG, "New regint is ${acc.regint}")
            reRegister = true
        }
    }
    else {
        if (regInt != acc.configuredRegInt)
            acc.configuredRegInt = regInt
    }

    if (acc.regint == 0)
        Api.account_set_check_origin(ua.account.accp, false)
    else {
        Api.account_set_check_origin(ua.account.accp, viewModel.checkOrigin.value)
        acc.checkOrigin = Api.account_check_origin(ua.account.accp)
    }

    val newMediaEnc = viewModel.mediaEnc.value
    if (newMediaEnc != acc.mediaEnc) {
        if (Api.account_set_mediaenc(acc.accp, newMediaEnc) == 0) {
            acc.mediaEnc = Api.account_mediaenc(acc.accp)
            Log.d(TAG, "New mediaenc is ${acc.mediaEnc}")
        }
        else
            Log.e(TAG, "Setting of mediaenc $newMediaEnc failed")
    }

    val newMediaNat = viewModel.mediaNat.value
    if (newMediaNat != acc.mediaNat) {
        if (Api.account_set_medianat(acc.accp, newMediaNat) == 0) {
            acc.mediaNat = Api.account_medianat(acc.accp)
            Log.d(TAG, "New medianat is ${acc.mediaNat}")
        }
        else
            Log.e(TAG, "Setting of medianat $newMediaNat failed")
    }

    var newStunServer = viewModel.stunServer.value.trim()
    if (newMediaNat != "") {
        if ((newMediaNat == "stun" || newMediaNat == "ice") && newStunServer == "")
            newStunServer = ctx.getString(R.string.stun_server_default)
        if (!Utils.checkStunUri(newStunServer) ||
            (newMediaNat == "turn" &&
                    newStunServer.substringBefore(":") !in setOf("turn", "turns"))) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = String.format(ctx.getString(R.string.invalid_stun_server),
                newStunServer)
            showAlert.value = true
            return false
        }
    }

    if (acc.stunServer != newStunServer) {
        if (Api.account_set_stun_uri(acc.accp, newStunServer) == 0) {
            acc.stunServer = Api.account_stun_uri(acc.accp)
            Log.d(TAG, "New STUN/TURN server URI is '${acc.stunServer}'")
        } else {
            Log.e(TAG, "Setting of STUN/TURN URI server $newStunServer failed")
        }
    }

    val newStunUser = viewModel.stunUser.value.trim()
    if (acc.stunUser != newStunUser) {
        if (Account.checkAuthUser(newStunUser)) {
            if (Api.account_set_stun_user(acc.accp, newStunUser) == 0) {
                acc.stunUser = Api.account_stun_user(acc.accp)
                Log.d(TAG, "New STUN/TURN user is ${acc.stunUser}")
            }
            else
                Log.e(TAG, "Setting of STUN/TURN user $newStunUser failed")
        }
        else {
            alertTitle.value = noticeTitle
            alertMessage.value = String.format(ctx.getString(R.string.invalid_stun_username),
                newStunUser)
            showAlert.value = true
            return false
        }
    }

    val newStunPass = viewModel.stunPass.value.trim()
    if (acc.stunPass != newStunPass) {
        if (newStunPass.isEmpty() || Account.checkAuthPass(newStunPass)) {
            if (Api.account_set_stun_pass(acc.accp, newStunPass) == 0)
                acc.stunPass = Api.account_stun_pass(acc.accp)
            else
                Log.e(TAG, "Setting of stun pass failed")
        }
        else {
            alertTitle.value = noticeTitle
            alertMessage.value = String.format(ctx.getString(R.string.invalid_stun_password),
                newStunPass)
            showAlert.value = true
            return false
        }
    }

    val newRtcpMux = viewModel.rtcpMux.value
    if (newRtcpMux != acc.rtcpMux)
        if (Api.account_set_rtcp_mux(acc.accp, newRtcpMux) == 0) {
            acc.rtcpMux = Api.account_rtcp_mux(acc.accp)
            Log.d(TAG, "New rtcpMux is ${acc.rtcpMux}")
        } else {
            Log.e(TAG, "Setting of account_rtc_mux $newRtcpMux failed")
        }

    val new100Rel = viewModel.rel100.value
    if (new100Rel != (acc.rel100Mode == Api.REL100_ENABLED)) {
        val mode = if (new100Rel) Api.REL100_ENABLED else Api.REL100_DISABLED
        if (Api.account_set_rel100_mode(acc.accp, mode) == 0) {
            acc.rel100Mode = Api.account_rel100_mode(acc.accp)
            Api.ua_update_account(ua.uap)
            Log.d(TAG, "New rel100Mode is ${acc.rel100Mode}")
        } else {
            Log.e(TAG, "Setting of account_rel100Mode $mode failed")
        }
    }

    val newDtmfMode = viewModel.dtmfMode.value
    if (newDtmfMode != acc.dtmfMode) {
        if (Api.account_set_dtmfmode(acc.accp, newDtmfMode) == 0) {
            acc.dtmfMode = Api.account_dtmfmode(acc.accp)
            Log.d(TAG, "New dtmfmode is ${acc.dtmfMode}")
        } else {
            Log.e(TAG, "Setting of dtmfmode $newDtmfMode failed")
        }
    }

    val newAnswerMode = viewModel.answerMode.value
    if (newAnswerMode != acc.answerMode) {
        if (Api.account_set_answermode(acc.accp, newAnswerMode) == 0) {
            acc.answerMode = Api.account_answermode(acc.accp)
            Log.d(TAG, "New answermode is ${acc.answerMode}")
        } else {
            Log.e(TAG, "Setting of answermode $newAnswerMode failed")
        }
    }

    val newAutoRedirect = viewModel.autoRedirect.value
    if (newAutoRedirect != acc.autoRedirect) {
        Api.account_set_sip_autoredirect(acc.accp, newAutoRedirect)
        acc.autoRedirect = newAutoRedirect
        Log.d(TAG, "New autoRedirect is ${acc.autoRedirect}")
    }

    val newBlockUnknown = viewModel.blockUnknown.value
    if (newBlockUnknown != acc.blockUnknown) {
        acc.blockUnknown = newBlockUnknown
        Log.d(TAG, "New blockUnknown is ${acc.blockUnknown}")
    }

    var newVmUri = viewModel.vmUri.value.trim()
    if (newVmUri != acc.vmUri) {
        if (newVmUri != "") {
            if (!newVmUri.startsWith("sip:")) newVmUri = "sip:$newVmUri"
            if (!newVmUri.contains("@")) newVmUri = "$newVmUri@${acc.host()}"
            if (!Utils.checkUri(newVmUri)) {
                alertTitle.value = noticeTitle
                alertMessage.value = String.format(ctx.getString(R.string.invalid_sip_or_tel_uri),
                    newVmUri)
                showAlert.value = true
                return false
            }
            Api.account_set_mwi(acc.accp, true)
        }
        else
            Api.account_set_mwi(acc.accp, false)
        acc.vmUri = newVmUri
        Log.d(TAG, "New voicemail URI is ${acc.vmUri}")
    }

    val newCountryCode = viewModel.countryCode.value.trim()
    if (newCountryCode != acc.countryCode) {
        if (newCountryCode != "" && !Utils.checkCountryCode(newCountryCode)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = String.format(ctx.getString(R.string.invalid_country_code),
                newCountryCode)
            showAlert.value = true
            return false
        }
        acc.countryCode = newCountryCode
        Log.d(TAG, "New country code is ${acc.countryCode}")
    }

    val hostPart = viewModel.telProvider.value.trim()
    if (hostPart != acc.telProvider) {
        if (hostPart != "" && !Utils.checkHostPortParams(hostPart)) {
            alertTitle.value = noticeTitle
            alertMessage.value = String.format(ctx.getString(R.string.invalid_sip_uri_hostpart), hostPart)
            showAlert.value = true
            return false
        }
        acc.telProvider = hostPart
        Log.d(TAG, "New tel provider is ${acc.telProvider}")
    }

    val newNumericKeypad = viewModel.numericKeypad.value
    if (newNumericKeypad != acc.numericKeypad) {
        acc.numericKeypad = newNumericKeypad
        Log.d(TAG, "New numericKeyboard is ${acc.numericKeypad}")
    }

    if (viewModel.defaultAccount.value) ua.makeDefault()

    Api.account_debug(acc.accp)

    Account.saveAccounts()

    if (acc.authUser != "" && BaresipService.aorPasswords[acc.aor] == NO_AUTH_PASS) {
        showPasswordDialog.value = true
        return false
    }
    else
        return true
}

private fun initAccountFromConfig(acc: Account, onConfigLoaded: () -> Unit) {
    val scope = CoroutineScope(Job() + Dispatchers.Main)
    scope.launch(Dispatchers.IO) {
        val url = "https://${Utils.uriHostPart(acc.aor)}/baresip/account_config.xml"
        val caFile = File(BaresipService.filesPath + "/ca_certs.crt")
        val config = try {
            if (caFile.exists())
                Utils.readUrlWithCustomCAs(URL(url), caFile)
            else
                URL(url).readText()
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "Failed to get account configuration from network: ${e.message}")
            null
        }
        if (config != null) {
            Log.d(TAG, "Got account config '$config'")
            val parserFactory: XmlPullParserFactory = XmlPullParserFactory.newInstance()
            val parser: XmlPullParser = parserFactory.newPullParser()
            parser.setInput(StringReader(config))
            var tag: String?
            var text = ""
            var event = parser.eventType
            val audioCodecs = ArrayList(Api.audio_codecs().split(","))
            val videoCodecs = ArrayList(Api.video_codecs().split(","))

            while (event != XmlPullParser.END_DOCUMENT) {
                tag = parser.name
                when (event) {
                    XmlPullParser.TEXT ->
                        text = parser.text

                    XmlPullParser.START_TAG -> {
                        if (tag == "audio-codecs")
                            acc.audioCodec.clear()
                        if (tag == "video-codecs")
                            acc.videoCodec.clear()
                    }

                    XmlPullParser.END_TAG ->
                        when (tag) {
                            "outbound-proxy-1" ->
                                if (text.isNotEmpty())
                                    acc.outbound.add(text)

                            "outbound-proxy-2" ->
                                if (text.isNotEmpty())
                                    acc.outbound.add(text)

                            "registration-interval" ->
                                acc.configuredRegInt = try {
                                    text.toInt()
                                } catch (_: NumberFormatException) {
                                    900
                                }

                            "register" -> {
                                acc.regint = if (text == "yes") acc.configuredRegInt else 0
                                if (acc.regint > 0)
                                    acc.checkOrigin = true
                            }

                            "audio-codec" ->
                                if (text in audioCodecs)
                                    acc.audioCodec.add(text)

                            "video-codec" ->
                                if (text in videoCodecs)
                                    acc.videoCodec.add(text)

                            "media-encoding" -> {
                                val enc = text.lowercase(Locale.ROOT)
                                if (enc in mediaEncMap.keys && enc.isNotEmpty())
                                    acc.mediaEnc = enc
                            }

                            "media-nat" -> {
                                val nat = text.lowercase(Locale.ROOT)
                                if (nat in mediaNatMap.keys && nat.isNotEmpty())
                                    acc.mediaNat = nat
                            }

                            "stun-turn-server" ->
                                if (text.isNotEmpty())
                                    acc.stunServer = text

                            "rtcp-mux" ->
                                acc.rtcpMux = text == "yes"

                            "100rel-mode" ->
                                acc.rel100Mode = if (text == "yes")
                                    Api.REL100_ENABLED
                                else
                                    Api.REL100_DISABLED

                            "dtmf-mode" ->
                                if (text in arrayOf("rtp-event", "sip-info", "auto"))
                                    acc.dtmfMode = when (text) {
                                        "rtp-event" -> Api.DTMFMODE_RTP_EVENT
                                        "sip-info" -> Api.DTMFMODE_SIP_INFO
                                        else -> Api.DTMFMODE_AUTO
                                    }

                            "answer-mode" ->
                                if (text in arrayOf("manual", "auto"))
                                    acc.answerMode = if (text == "manual")
                                        Api.ANSWERMODE_MANUAL
                                    else
                                        Api.ANSWERMODE_AUTO

                            "redirect-mode" ->
                                acc.autoRedirect = text == "yes"

                            "voicemail-uri" ->
                                if (text.isNotEmpty())
                                    acc.vmUri = text

                            "country-code" ->
                                acc.countryCode = text

                            "tel-provider" ->
                                acc.telProvider = text
                        }
                }
                event = parser.next()
            }
        }
        onConfigLoaded()
    }
}

private fun checkOutboundUri(uri: String): Boolean {
    if (!uri.startsWith("sip:")) return false
    return Utils.checkHostPortParams(uri.substring(4))
}
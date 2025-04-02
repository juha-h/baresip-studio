package com.tutpro.baresip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutpro.baresip.BaresipService.Companion.uas
import com.tutpro.baresip.CustomElements.Checkbox
import com.tutpro.baresip.CustomElements.verticalScrollbar

class AccountActivity : ComponentActivity() {

    private lateinit var mediaEncMap: Map<String, String>
    private lateinit var mediaNatMap: Map<String, String>
    private lateinit var dtmfModeMap: Map<Int, String>
    private lateinit var answerModeMap: Map<Int, String>
    private lateinit var redirectModeMap: Map<Boolean, String>
    private lateinit var acc: Account
    private lateinit var ua: UserAgent
    private lateinit var aor: String

    private var reRegister = false
    private var oldNickname = ""
    private var newNickname = ""
    private var oldDisplayname = ""
    private var newDisplayname = ""
    private var oldAuthUser = ""
    private var newAuthUser = ""
    private var oldAuthPass = ""
    private var newAuthPass = ""
    private var oldOutbound1 = ""
    private var newOutbound1 = ""
    private var oldOutbound2 = ""
    private var newOutbound2 = ""
    private var oldRegister = false
    private var newRegister = false
    private var oldRegInt = ""
    private var newRegInt = ""
    private var oldMediaEnc = ""
    private var newMediaEnc = ""
    private var oldMediaNat = ""
    private var newMediaNat = ""
    private var oldStunServer = ""
    private var newStunServer = ""
    private var oldStunUser = ""
    private var newStunUser = ""
    private var oldStunPass = ""
    private var newStunPass = ""
    private var oldRtcpMux = false
    private var newRtcpMux = false
    private var old100Rel = false
    private var new100Rel = false
    private var oldDtmfMode = 0
    private var newDtmfMode = 0
    private var oldAnswerMode = 0
    private var newAnswerMode = 0
    private var oldAutoRedirect = false
    private var newAutoRedirect = false
    private var oldVmUri = ""
    private var newVmUri = ""
    private var oldCountryCode = ""
    private var newCountryCode = ""
    private var oldTelProvider = ""
    private var newTelProvider = ""
    private var oldDefaultAccount = false
    private var newDefaultAccount = false
    private var arrowTint = Color.Unspecified

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        aor = intent.getStringExtra("aor")!!

        Utils.addActivity("account,$aor")

        ua = UserAgent.ofAor(aor)!!
        acc = ua.account

        mediaEncMap = mapOf("zrtp" to "ZRTP", "dtls_srtp" to "DTLS-SRTPF",
            "srtp-mand" to "SRTP-MAND", "srtp" to "SRTP", "" to "--")

        mediaNatMap = mapOf("stun" to "STUN", "turn" to "TURN", "ice" to "ICE", "" to "--")

        dtmfModeMap = mapOf(Api.DTMFMODE_RTP_EVENT to getString(R.string.dtmf_inband),
            Api.DTMFMODE_SIP_INFO to getString(R.string.dtmf_info),
            Api.DTMFMODE_AUTO to getString(R.string.dtmf_auto))

        answerModeMap = mapOf(Api.ANSWERMODE_MANUAL to getString(R.string.manual),
            Api.ANSWERMODE_AUTO to getString(R.string.auto))

        redirectModeMap = mapOf(false to getString(R.string.manual),
            true to getString(R.string.auto))

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    AccountScreen { goBack() }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Api.account_debug(acc.accp)

    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AccountScreen(navigateBack: () -> Unit) {
        val title = if (acc.nickName.value != "")
            acc.nickName.value
        else
            acc.aor.substringAfter(":")
        Scaffold(
            modifier = Modifier.safeDrawingPadding(),
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
                    actions = {
                        IconButton(onClick = {
                            updateAccount()
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
                AccountContent(this, contentPadding)
            }
        )
    }

    @Composable
    fun AccountContent(ctx: Context, contentPadding: PaddingValues) {
        arrowTint = if (BaresipService.darkTheme.value)
            LocalCustomColors.current.grayLight
        else
            LocalCustomColors.current.black
        val lazyListState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth()
                .padding(contentPadding)
                .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 4.dp)
                .verticalScrollbar(
                    state = lazyListState,
                    width = 4.dp,
                    color = LocalCustomColors.current.gray
                ),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            oldNickname = acc.nickName.value
            oldDisplayname = acc.displayName
            oldAuthUser = acc.authUser
            if (BaresipService.aorPasswords[aor] == null &&  // check if OK
                acc.authPass != NO_AUTH_PASS)
                oldAuthPass = acc.authPass
            if (acc.outbound.size > 0) {
                oldOutbound1 = acc.outbound[0]
                if (acc.outbound.size > 1)
                    oldOutbound2 = acc.outbound[1]
            }
            oldRegister = acc.regint > 0
            oldRegInt = acc.configuredRegInt.toString()
            oldMediaEnc = acc.mediaEnc
            oldMediaNat = acc.mediaNat
            oldStunServer = acc.stunServer
            oldStunUser = acc.stunUser
            oldStunPass = acc.stunPass
            oldRtcpMux = acc.rtcpMux
            old100Rel = acc.rel100Mode == Api.REL100_ENABLED
            oldDtmfMode = acc.dtmfMode
            oldAnswerMode = acc.answerMode
            oldAutoRedirect = acc.autoRedirect
            oldVmUri = acc.vmUri
            oldCountryCode = acc.countryCode
            oldTelProvider = acc.telProvider
            oldDefaultAccount = UserAgent.findAorIndex(aor)!! == 0

            item { AoR() }
            item { Nickname(ctx) }
            item { Displayname(ctx) }
            item { AuthUser(ctx) }
            item { AuthPass(ctx) }
            item { Outbound(ctx) }
            item { Register(ctx) }
            item { RegInt(ctx) }
            item { AudioCodecs(ctx) }
            item { MediaEnc(ctx) }
            item { MediaNat(ctx) }
            item { StunServer(ctx) }
            item { StunUser(ctx) }
            item { StunPass(ctx) }
            item { RtcpMux() }
            item { Rel100() }
            item { Dtmf(ctx) }
            item { Answer(ctx) }
            item { Redirect(ctx) }
            item { Voicemail(ctx) }
            item { CountryCode(ctx) }
            item { TelProvider(ctx) }
            item { DefaultAccount() }
        }
    }

    @Composable
    private fun AoR() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            OutlinedTextField(
                value = aor,
                enabled = false,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp,
                    color = LocalCustomColors.current.itemText
                ),
                label = { Text(stringResource(R.string.sip_uri)) }
            )
        }
    }

    @Composable
    private fun Nickname(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var nickName by remember { mutableStateOf(oldNickname) }
            newNickname = nickName
            OutlinedTextField(
                value = nickName,
                placeholder = { Text(stringResource(R.string.nickname)) },
                onValueChange = {
                    nickName = it
                    newNickname = nickName
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.nickname),
                            getString(R.string.account_nickname_help)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.nickname)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text),
            )
        }
    }

    @Composable
    private fun Displayname(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var displayName by remember { mutableStateOf(oldDisplayname) }
            newDisplayname = displayName
            OutlinedTextField(
                value = displayName,
                placeholder = { Text(stringResource(R.string.display_name)) },
                onValueChange = {
                    displayName = it
                    newDisplayname = displayName
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.display_name),
                            getString(R.string.display_name_help)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.display_name)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text),
            )
        }
    }

    @Composable
    private fun AuthUser(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var authUser by remember { mutableStateOf(oldAuthUser) }
            newAuthUser = authUser
            OutlinedTextField(
                value = authUser,
                placeholder = { Text(stringResource(R.string.authentication_username)) },
                onValueChange = {
                    authUser = it
                    newAuthUser = authUser
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.authentication_username),
                            getString(R.string.authentication_username_help)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.authentication_username)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    private fun AuthPass(ctx: Context) {
        val showPassword = remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var authPass by remember { mutableStateOf(oldAuthPass) }
            newAuthPass = authPass
            OutlinedTextField(
                value = authPass,
                placeholder = { Text(stringResource(R.string.authentication_password)) },
                onValueChange = {
                    authPass = it
                    newAuthPass = authPass
                },
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
                                ImageVector.vectorResource(R.drawable.visibility)
                            else
                                ImageVector.vectorResource(R.drawable.visibility_off),
                            contentDescription = "Visibility",
                            tint = LocalCustomColors.current.itemText

                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.authentication_password),
                            getString(R.string.authentication_password_help)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.authentication_password)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    private fun Outbound(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.outbound_proxies),
                color = LocalCustomColors.current.itemText,
                modifier = Modifier.clickable {
                    Utils.alertView(
                        ctx, getString(R.string.outbound_proxies),
                        getString(R.string.outbound_proxies_help)
                    )
                })
        }
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var outbound1 by remember { mutableStateOf(oldOutbound1) }
            newOutbound1 = outbound1
            OutlinedTextField(
                value = outbound1,
                placeholder = { Text(stringResource(R.string.sip_uri_of_proxy_server)) },
                onValueChange = {
                    outbound1 = it
                    newOutbound1 = outbound1
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.sip_uri_of_proxy_server)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var outbound2 by remember { mutableStateOf(oldOutbound2) }
            newOutbound2 = outbound2
            OutlinedTextField(
                value = outbound2,
                placeholder = { Text(stringResource(R.string.sip_uri_of_another_proxy_server)) },
                onValueChange = {
                    outbound2 = it
                    newOutbound2 = outbound2
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.sip_uri_of_another_proxy_server)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun Register(ctx: Context) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.register),
                modifier = Modifier.weight(1f)
                    .clickable {
                        Utils.alertView(
                            ctx, getString(R.string.register), getString(R.string.register_help)
                        )
                    },
                color = LocalCustomColors.current.itemText)
            var register by remember { mutableStateOf(oldRegister) }
            newRegister = register
            Checkbox(
                checked = register,
                onCheckedChange = {
                    register = it
                    newRegister = register
                }
            )
        }
    }

    @Composable
    private fun RegInt(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var regInt by remember { mutableStateOf(oldRegInt) }
            newRegInt = regInt
            OutlinedTextField(
                value = regInt,
                placeholder = { Text(stringResource(R.string.reg_int)) },
                onValueChange = {
                    regInt = it
                    newRegInt = regInt
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.reg_int),
                            getString(R.string.reg_int_help)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.reg_int)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }

    @Composable
    private fun AudioCodecs(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(top=12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = stringResource(R.string.audio_codecs),
                modifier = Modifier.weight(1f)
                    .clickable {
                        val i = Intent(ctx, CodecsActivity::class.java)
                        val b = Bundle()
                        b.putString("aor", aor)
                        b.putString("media", "audio")
                        i.putExtras(b)
                        startActivity(i)
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp,
                fontWeight = FontWeight. Bold
            )
        }
    }

    @Composable
    private fun MediaEnc(ctx: Context) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.media_encryption),
                modifier = Modifier.weight(1f)
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.media_encryption),
                            getString(R.string.media_encryption_help))
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember { mutableStateOf(false) }
            val mediaEnc = remember { mutableStateOf(oldMediaEnc) }
            newMediaEnc = mediaEnc.value
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = mediaEncMap[mediaEnc.value]!!,
                        color = LocalCustomColors.current.itemText)
                    CustomElements.DrawDrawable(R.drawable.arrow_drop_down,
                        tint = arrowTint)
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = {
                        isDropDownExpanded.value = false
                    }) {
                    var index = 0
                    mediaEncMap.forEach {
                        DropdownMenuItem(text = {
                            Text(text = it.value,
                                color = LocalCustomColors.current.itemText)
                        },
                        onClick = {
                            isDropDownExpanded.value = false
                            mediaEnc.value = it.key
                            newMediaEnc = mediaEnc.value
                        })
                        if (index < 4)
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = LocalCustomColors.current.itemText
                            )
                        index++
                    }
                }
            }
        }
    }

    @Composable
    private fun MediaNat(ctx: Context) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.media_nat),
                modifier = Modifier.weight(1f)
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.media_nat),
                            getString(R.string.media_nat_help))
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember { mutableStateOf(false) }
            val mediaNat = remember { mutableStateOf(oldMediaNat) }
            newMediaNat = mediaNat.value
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = mediaNatMap[mediaNat.value]!!,
                        color = LocalCustomColors.current.itemText)
                    CustomElements.DrawDrawable(R.drawable.arrow_drop_down,
                        tint = arrowTint)
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = {
                        isDropDownExpanded.value = false
                    }) {
                    var index = 0
                    mediaNatMap.forEach {
                        DropdownMenuItem(text = {
                            Text(text = it.value)
                        },
                            onClick = {
                                isDropDownExpanded.value = false
                                mediaNat.value = it.key
                                newMediaNat = mediaNat.value
                                //if (newMediaNat == "")
                                    // don't show stunserver
                                //else
                                    // show stunserver
                            })
                        if (index < 3)
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = LocalCustomColors.current.itemText
                            )
                        index++
                    }
                }
            }
        }
    }

    @Composable
    private fun StunServer(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var stunServer by remember { mutableStateOf(oldStunServer) }
            newStunServer = stunServer
            OutlinedTextField(
                value = stunServer,
                placeholder = { Text(stringResource(R.string.stun_server)) },
                onValueChange = {
                    stunServer = it
                    newStunServer = stunServer
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.stun_server),
                            getString(R.string.stun_server_help)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.stun_server)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    private fun StunUser(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var stunUser by remember { mutableStateOf(oldStunUser) }
            newStunUser = stunUser
            OutlinedTextField(
                value = stunUser,
                placeholder = { Text(stringResource(R.string.stun_username)) },
                onValueChange = {
                    stunUser = it
                    newStunUser = stunUser
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.stun_username),
                            getString(R.string.stun_username_help)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.stun_username)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    private fun StunPass(ctx: Context) {
        val showPassword = remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var stunPass by remember { mutableStateOf(oldStunPass) }
            newStunPass = stunPass
            OutlinedTextField(
                value = stunPass,
                placeholder = { Text(stringResource(R.string.stun_password)) },
                onValueChange = {
                    stunPass = it
                    newStunPass = stunPass
                },
                singleLine = true,
                visualTransformation = if (showPassword.value)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    val (icon, iconColor) = if (showPassword.value) {
                        Pair(
                            ImageVector.vectorResource(R.drawable.visibility),
                            colorResource(id = R.color.colorAccent)
                        )
                    } else {
                        Pair(
                            ImageVector.vectorResource(R.drawable.visibility_off),
                            colorResource(id = R.color.colorWhite))
                    }
                    IconButton(onClick = { showPassword.value = !showPassword.value }) {
                        Icon(
                            icon,
                            contentDescription = "Visibility",
                            tint = iconColor
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.stun_password),
                            getString(R.string.stun_password_help)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.stun_password)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun RtcpMux() {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.rtcp_mux),
                modifier = Modifier.weight(1f),
                color = LocalCustomColors.current.itemText)
            var rtcpMux by remember { mutableStateOf(oldRtcpMux) }
            newRtcpMux = rtcpMux
            Checkbox(
                checked = rtcpMux,
                onCheckedChange = {
                    rtcpMux = it
                    newRtcpMux = rtcpMux
                }
            )
        }
    }

    @Composable
    fun Rel100() {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.rtcp_mux),
                modifier = Modifier.weight(1f),
                color = LocalCustomColors.current.itemText)
            var rel100 by remember { mutableStateOf(old100Rel) }
            new100Rel = rel100
            Checkbox(
                checked = rel100,
                onCheckedChange = {
                    rel100 = it
                    new100Rel = rel100
                }
            )
        }
    }

    @Composable
    private fun Dtmf(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(top=12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.dtmf_mode),
                modifier = Modifier.weight(1f)
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.dtmf_mode),
                            getString(R.string.dtmf_mode_help))
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val dtmfMode = remember { mutableIntStateOf(oldDtmfMode) }
            newDtmfMode = dtmfMode.intValue
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = dtmfModeMap[dtmfMode.intValue]!!,
                        color = LocalCustomColors.current.itemText)
                    CustomElements.DrawDrawable(R.drawable.arrow_drop_down,
                        tint = arrowTint)
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = {
                        isDropDownExpanded.value = false
                    }) {
                    var index = 0
                    dtmfModeMap.forEach {
                        DropdownMenuItem(text = {
                            Text(text = it.value)
                        },
                        onClick = {
                            isDropDownExpanded.value = false
                            dtmfMode.intValue = it.key
                            newDtmfMode = dtmfMode.intValue
                        })
                        if (index < 2)
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = LocalCustomColors.current.itemText
                            )
                        index++
                    }
                }
            }
        }
    }

    @Composable
    private fun Answer(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(top=12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.answer_mode),
                modifier = Modifier.weight(1f)
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.answer_mode),
                            getString(R.string.answer_mode_help))
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val answerMode = remember { mutableIntStateOf(oldAnswerMode) }
            newAnswerMode = answerMode.intValue
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = answerModeMap[answerMode.intValue]!!,
                        color = LocalCustomColors.current.itemText)
                    CustomElements.DrawDrawable(R.drawable.arrow_drop_down,
                        tint = arrowTint)
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = {
                        isDropDownExpanded.value = false
                    }) {
                    var index = 0
                    answerModeMap.forEach {
                        DropdownMenuItem(text = { Text(text = it.value) },
                        onClick = {
                            isDropDownExpanded.value = false
                            answerMode.intValue = it.key
                            newAnswerMode = answerMode.intValue
                        })
                        if (index < 1)
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = LocalCustomColors.current.itemText
                            )
                        index++
                    }
                }
            }
        }
    }

    @Composable
    private fun Redirect(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(top=12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.redirect_mode),
                modifier = Modifier.weight(1f)
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.redirect_mode),
                            getString(R.string.redirect_mode_help))
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val autoRedirect = remember { mutableStateOf(oldAutoRedirect) }
            newAutoRedirect = autoRedirect.value
            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = redirectModeMap[autoRedirect.value]!!,
                        color = LocalCustomColors.current.itemText)
                    CustomElements.DrawDrawable(R.drawable.arrow_drop_down,
                        tint = arrowTint)
                }
                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = {
                        isDropDownExpanded.value = false
                    }) {
                    var index = 0
                    redirectModeMap.forEach {
                        DropdownMenuItem(text = {
                            Text(text = it.value)
                        },
                            onClick = {
                                isDropDownExpanded.value = false
                                autoRedirect.value = it.key
                                newAutoRedirect = autoRedirect.value
                            })
                        if (index < 1)
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = LocalCustomColors.current.itemText
                            )
                        index++
                    }
                }
            }
        }
    }

    @Composable
    private fun Voicemail(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var vmUri by remember { mutableStateOf(oldVmUri) }
            newVmUri = vmUri
            OutlinedTextField(
                value = vmUri,
                placeholder = { Text(stringResource(R.string.voicemail_uri)) },
                onValueChange = {
                    vmUri = it
                    newVmUri = vmUri
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.voicemail_uri),
                            getString(R.string.voicemain_uri_help)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.voicemail_uri)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    private fun CountryCode(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var countryCode by remember { mutableStateOf(oldCountryCode) }
            newCountryCode = countryCode
            OutlinedTextField(
                value = countryCode,
                placeholder = { Text(stringResource(R.string.country_code)) },
                onValueChange = {
                    countryCode = it
                    newCountryCode = countryCode
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.country_code),
                            getString(R.string.country_code_help)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.country_code)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    private fun TelProvider(ctx: Context) {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var telProvider by remember { mutableStateOf(oldTelProvider) }
            newTelProvider = telProvider
            OutlinedTextField(
                value = telProvider,
                placeholder = { Text(stringResource(R.string.telephony_provider)) },
                onValueChange = {
                    telProvider = it
                    newTelProvider = telProvider
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.telephony_provider),
                            getString(R.string.telephony_provider_help)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { Text(stringResource(R.string.telephony_provider)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun DefaultAccount() {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.default_account),
                modifier = Modifier.weight(1f),
                color = LocalCustomColors.current.itemText)
            var defaultAccount by remember { mutableStateOf(oldDefaultAccount) }
            newDefaultAccount = defaultAccount
            Checkbox(
                checked = defaultAccount,
                onCheckedChange = {
                    defaultAccount = it
                    newDefaultAccount = defaultAccount
                }
            )
        }
    }

    private fun updateAccount() {

        if (BaresipService.activities.indexOf("account,$aor") == -1)
            return

        val nn = newNickname.trim()
        if (nn != oldNickname) {
            if (Account.checkDisplayName(nn)) {
                if (nn == "" || Account.uniqueNickName(nn)) {
                    acc.nickName.value = nn
                    Log.d(TAG, "New nickname is ${acc.nickName.value}")
                } else {
                    Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.non_unique_account_nickname), nn))
                    return
                }
            } else {
                Utils.alertView(this, getString(R.string.notice),
                    String.format(getString(R.string.invalid_account_nickname), nn))
                return
            }
        }

        val dn = newDisplayname.trim()
        if (dn != acc.displayName) {
            if (Account.checkDisplayName(dn)) {
                if (Api.account_set_display_name(acc.accp, dn) == 0) {
                    acc.displayName = Api.account_display_name(acc.accp)
                    Log.d(TAG, "New display name is ${acc.displayName}")
                } else {
                    Log.e(TAG, "Setting of display name failed")
                }
            } else {
                Utils.alertView(this, getString(R.string.notice),
                    String.format(getString(R.string.invalid_display_name), dn))
                return
            }
        }

        val au = newAuthUser.trim()
        if (au != oldAuthUser) {
            if (Account.checkAuthUser(au)) {
                if (Api.account_set_auth_user(acc.accp, au) == 0) {
                    acc.authUser = Api.account_auth_user(acc.accp)
                    Log.d(TAG, "New auth user is ${acc.authUser}")
                    if (acc.regint > 0)
                        reRegister = true
                } else {
                    Log.e(TAG, "Setting of auth user failed")
                }
            } else {
                Utils.alertView(this, getString(R.string.notice),
                    String.format(getString(R.string.invalid_authentication_username), au))
                return
            }
        }

        val ap = newAuthPass.trim()
        if (ap != "") {
            if (ap != oldAuthPass) {
                if (Account.checkAuthPass(ap)) {
                    if (Api.account_set_auth_pass(acc.accp, ap) == 0) {
                        acc.authPass = Api.account_auth_pass(acc.accp)
                        if (acc.regint > 0)
                            reRegister = true
                    } else {
                        Log.e(TAG, "Setting of auth pass failed")
                    }
                    BaresipService.aorPasswords.remove(acc.aor)
                } else {
                    Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.invalid_authentication_password), ap))
                    return
                }
            } else {
                BaresipService.aorPasswords.remove(acc.aor)
            }
        } else { // ap == ""
            if (acc.authPass != NO_AUTH_PASS &&
                acc.authPass != BaresipService.aorPasswords[acc.aor])
                if (Api.account_set_auth_pass(acc.accp, "") == 0) {
                    acc.authPass = NO_AUTH_PASS
                    BaresipService.aorPasswords[aor] = NO_AUTH_PASS
                }
        }

        val ob = ArrayList<String>()
        var ob1 = newOutbound1.trim().replace(" ", "")
        if (ob1 != "") {
            if (!ob1.startsWith("sip:"))
                ob1 = "sip:$ob1"
            if (checkOutboundUri(ob1)) {
                ob.add(ob1)
            } else {
                Utils.alertView(this, getString(R.string.notice),
                    String.format(getString(R.string.invalid_proxy_server_uri), ob1))
                return
            }
        }
        var ob2 = newOutbound2.trim().replace(" ", "")
        if (ob2 != "") {
            if (!ob2.startsWith("sip:"))
                ob2 = "sip:$ob2"
            if (checkOutboundUri(ob2)) {
                ob.add(ob2)
            } else {
                Utils.alertView(this, getString(R.string.notice),
                    String.format(getString(R.string.invalid_proxy_server_uri), ob2))
                return
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

        val regInt = newRegInt.trim().toInt()
        if (regInt < 60 || regInt > 3600) {
            Utils.alertView(
                this, getString(R.string.notice),
                String.format(getString(R.string.invalid_reg_int), "$regInt")
            )
            return
        }
        val reReg = (newRegister != acc.regint > 0) ||
                (newRegister && regInt != acc.configuredRegInt)
        if (reReg) {
            if (Api.account_set_regint(acc.accp,
                    if (newRegister) regInt else 0) != 0) {
                Log.e(TAG, "Setting of regint failed")
            } else {
                acc.regint = Api.account_regint(acc.accp)
                acc.configuredRegInt = regInt
                Log.d(TAG, "New regint is ${acc.regint}")
                reRegister = true
            }
        } else {
            if (regInt != acc.configuredRegInt) {
                acc.configuredRegInt = regInt
            }
        }

        if (newMediaEnc != acc.mediaEnc) {
            if (Api.account_set_mediaenc(acc.accp, newMediaEnc) == 0) {
                acc.mediaEnc = Api.account_mediaenc(acc.accp)
                Log.d(TAG, "New mediaenc is ${acc.mediaEnc}")
            } else {
                Log.e(TAG, "Setting of mediaenc $newMediaEnc failed")
            }
        }

        if (newMediaNat != acc.mediaNat) {
            if (Api.account_set_medianat(acc.accp, newMediaNat) == 0) {
                acc.mediaNat = Api.account_medianat(acc.accp)
                Log.d(TAG, "New medianat is ${acc.mediaNat}")
            } else {
                Log.e(TAG, "Setting of medianat $newMediaNat failed")
            }
        }

        newStunServer = newStunServer.trim()

        if (newMediaNat != "") {
            if (((newMediaNat == "stun") || (newMediaNat == "ice")) && (newStunServer == ""))
                newStunServer = resources.getString(R.string.stun_server_default)
            if (!Utils.checkStunUri(newStunServer) ||
                (newMediaNat == "turn" &&
                        newStunServer.substringBefore(":") !in setOf("turn", "turns"))) {
                Utils.alertView(this, getString(R.string.notice),
                    String.format(getString(R.string.invalid_stun_server), newStunServer))
                return
            }
        }

        if (acc.stunServer != newStunServer) {
            if (Api.account_set_stun_uri(acc.accp, newStunServer) == 0) {
                acc.stunServer = Api.account_stun_uri(acc.accp)
                Log.d(TAG, "New STUN/TURN server URI is '${acc.stunServer}'")
            } else {
                Log.e(TAG, "Setting of STUN/TURN URI server failed")
            }
        }

        newStunUser = newStunUser.trim()
        if (acc.stunUser != newStunUser) {
            if (Account.checkAuthUser(newStunUser)) {
                if (Api.account_set_stun_user(acc.accp, newStunUser) == 0) {
                    acc.stunUser = Api.account_stun_user(acc.accp)
                    Log.d(TAG, "New STUN/TURN user is ${acc.stunUser}")
                } else {
                    Log.e(TAG, "Setting of STUN/TURN user failed")
                }
            } else {
                Utils.alertView(this, getString(R.string.notice), String.format(getString(R.string.invalid_stun_username),
                    newStunUser))
                return
            }
        }

        val newStunPass = newStunPass.trim()
        if (acc.stunPass != newStunPass) {
            if (newStunPass.isEmpty() || Account.checkAuthPass(newStunPass)) {
                if (Api.account_set_stun_pass(acc.accp, newStunPass) == 0) {
                    acc.stunPass = Api.account_stun_pass(acc.accp)
                } else {
                    Log.e(TAG, "Setting of stun pass failed")
                }
            } else {
                Utils.alertView(this, getString(R.string.notice),
                    String.format(getString(R.string.invalid_stun_password), newStunPass))
                return
            }
        }

        if (newRtcpMux != acc.rtcpMux)
            if (Api.account_set_rtcp_mux(acc.accp, newRtcpMux) == 0) {
                acc.rtcpMux = Api.account_rtcp_mux(acc.accp)
                Log.d(TAG, "New rtcpMux is ${acc.rtcpMux}")
            } else {
                Log.e(TAG, "Setting of account_rtc_mux $newRtcpMux failed")
            }

        if (new100Rel != (acc.rel100Mode == Api.REL100_ENABLED)) {
            val mode = if (new100Rel) Api.REL100_ENABLED else Api.REL100_DISABLED
            if (Api.account_set_rel100_mode(acc.accp, mode) == 0) {
                acc.rel100Mode = Api.account_rel100_mode(acc.accp)
                Api.ua_update_account(ua.uap)
                Log.d(TAG, "New rel100Mode is ${acc.rel100Mode}")
            } else {
                Log.e(TAG, "Setting of account_rel100Mode failed")
            }
        }

        if (newDtmfMode != acc.dtmfMode) {
            if (Api.account_set_dtmfmode(acc.accp, newDtmfMode) == 0) {
                acc.dtmfMode = Api.account_dtmfmode(acc.accp)
                Log.d(TAG, "New dtmfmode is ${acc.dtmfMode}")
            } else {
                Log.e(TAG, "Setting of dtmfmode $newDtmfMode failed")
            }
        }

        if (newAnswerMode != acc.answerMode) {
            if (Api.account_set_answermode(acc.accp, newAnswerMode) == 0) {
                acc.answerMode = Api.account_answermode(acc.accp)
                Log.d(TAG, "New answermode is ${acc.answerMode}")
            } else {
                Log.e(TAG, "Setting of answermode $newAnswerMode failed")
            }
        }

        if (newAutoRedirect != acc.autoRedirect) {
            Api.account_set_sip_autoredirect(acc.accp, newAutoRedirect)
            acc.autoRedirect = newAutoRedirect
            Log.d(TAG, "New autoRedirect is ${acc.autoRedirect}")
        }

        newVmUri = newVmUri.trim()
        if (newVmUri != acc.vmUri) {
            if (newVmUri != "") {
                if (!newVmUri.startsWith("sip:")) newVmUri = "sip:$newVmUri"
                if (!newVmUri.contains("@")) newVmUri = "$newVmUri@${acc.host()}"
                if (!Utils.checkUri(newVmUri)) {
                    Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.invalid_sip_or_tel_uri), newVmUri))
                    return
                }
                Api.account_set_mwi(acc.accp, true)
            } else {
                Api.account_set_mwi(acc.accp, false)
            }
            acc.vmUri = newVmUri
            Log.d(TAG, "New voicemail URI is ${acc.vmUri}")
        }

        newCountryCode = newCountryCode.trim()
        if (newCountryCode != acc.countryCode) {
            if (newCountryCode != "" && !Utils.checkCountryCode(newCountryCode)) {
                Utils.alertView(this, getString(R.string.notice),
                    String.format(getString(R.string.invalid_country_code), newCountryCode))
                return
            }
            acc.countryCode = newCountryCode
            Log.d(TAG, "New country code is ${acc.countryCode}")
        }

        val hostPart = newTelProvider.trim()
        if (hostPart != acc.telProvider) {
            if (hostPart != "" && !Utils.checkHostPortParams(hostPart)) {
                Utils.alertView(this, getString(R.string.notice),
                    String.format(getString(R.string.invalid_sip_uri_hostpart), hostPart))
                return
            }
            acc.telProvider = hostPart
            Log.d(TAG, "New tel provider is ${acc.telProvider}")
        }

        val uaIndex = UserAgent.findAorIndex(aor)!!
        if (newDefaultAccount && (uaIndex > 0)) {
            val updatedUas = uas.value.toMutableList()
            updatedUas.add(0, uas.value[uaIndex])
            updatedUas.removeAt(uaIndex + 1)
            uas.value = updatedUas.toList()
        }

        AccountsActivity.saveAccounts()

        if (reRegister) {
            ua.status = R.drawable.circle_yellow
            if (acc.regint == 0)
                Api.ua_unregister(ua.uap)
            else
                Api.ua_register(ua.uap)
        }

        BaresipService.activities.remove("account,$aor")
        returnResult(RESULT_OK)
    }

    private fun goBack() {
        BaresipService.activities.remove("account,$aor")
        returnResult(RESULT_CANCELED)
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
    }

    private fun returnResult(code: Int) {
        val i = Intent()
        if (code == RESULT_OK)
            i.putExtra("aor", aor)
        setResult(code, i)
        finish()
    }

    private fun checkOutboundUri(uri: String): Boolean {
        if (!uri.startsWith("sip:")) return false
        return Utils.checkHostPortParams(uri.substring(4))
    }

}

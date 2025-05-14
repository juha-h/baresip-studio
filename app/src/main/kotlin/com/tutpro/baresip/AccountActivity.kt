package com.tutpro.baresip

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutpro.baresip.BaresipService.Companion.uas
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.LabelText
import com.tutpro.baresip.CustomElements.verticalScrollbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URL
import java.util.Locale

class AccountActivity : ComponentActivity() {

    private lateinit var mediaEncMap: Map<String, String>
    private lateinit var mediaNatMap: Map<String, String>
    private lateinit var dtmfModeMap: Map<Int, String>
    private lateinit var answerModeMap: Map<Int, String>
    private lateinit var redirectModeMap: Map<Boolean, String>
    private lateinit var acc: Account
    private lateinit var ua: UserAgent
    private lateinit var aor: String

    private var kind: String? = null
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
    private var newNumericKeypad = false
    private var password = mutableStateOf("")
    private var showPasswordDialog = mutableStateOf(false)
    private var keyboardController: SoftwareKeyboardController? = null

    private val alertTitle = mutableStateOf("")
    private val alertMessage = mutableStateOf("")
    private val showAlert = mutableStateOf(false)
    private val showStun = mutableStateOf(false)

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
        kind = intent.getStringExtra("kind")

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

        redirectModeMap = mapOf(false to getString(R.string.manual), true to getString(R.string.auto))

        setContent {
            AppTheme {
                keyboardController = LocalSoftwareKeyboardController.current
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    AccountScreen(kind) { goBack() }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AccountScreen(kind: String?, navigateBack: () -> Unit) {

        var isConfigLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(kind, acc) {
            if (kind == "new")
                initAccountFromConfig(acc) { isConfigLoaded = true }
            else
                isConfigLoaded = true
        }

        val title = if (acc.nickName.value != "")
            acc.nickName.value
        else
            acc.aor.substringAfter(":")
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
                if (isConfigLoaded)
                    AccountContent(this, contentPadding)
                else
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
            }
        )
    }

    @Composable
    fun AccountContent(ctx: Context, contentPadding: PaddingValues) {

        oldNickname = acc.nickName.value
        newNickname = oldNickname
        oldDisplayname = acc.displayName
        newDisplayname = oldDisplayname
        oldAuthUser = acc.authUser
        newAuthUser = oldAuthUser
        if (BaresipService.aorPasswords[aor] == null &&  // check if OK
            acc.authPass != NO_AUTH_PASS) {
            oldAuthPass = acc.authPass
            newAuthPass = oldAuthPass
        }
        if (acc.outbound.isNotEmpty()) {
            oldOutbound1 = acc.outbound[0]
            newOutbound1 = oldOutbound1
            if (acc.outbound.size > 1) {
                oldOutbound2 = acc.outbound[1]
                newOutbound2 = oldOutbound2
            }
        }
        oldRegister = acc.regint > 0
        newRegister = oldRegister
        oldRegInt = acc.configuredRegInt.toString()
        newRegInt = oldRegInt
        oldMediaEnc = acc.mediaEnc
        newMediaEnc = oldMediaEnc
        oldMediaNat = acc.mediaNat
        newMediaNat = oldMediaNat
        showStun.value = oldMediaNat != ""
        oldStunServer = acc.stunServer
        newStunServer = oldStunServer
        oldStunUser = acc.stunUser
        newStunUser = oldStunUser
        oldStunPass = acc.stunPass
        newStunPass = oldStunPass
        oldRtcpMux = acc.rtcpMux
        newRtcpMux = oldRtcpMux
        old100Rel = acc.rel100Mode == Api.REL100_ENABLED
        new100Rel = old100Rel
        oldDtmfMode = acc.dtmfMode
        newDtmfMode = oldDtmfMode
        oldAnswerMode = acc.answerMode
        newAnswerMode = oldAnswerMode
        oldAutoRedirect = acc.autoRedirect
        newAutoRedirect = oldAutoRedirect
        oldVmUri = acc.vmUri
        newVmUri = oldVmUri
        oldCountryCode = acc.countryCode
        newCountryCode = oldCountryCode
        oldTelProvider = acc.telProvider
        newTelProvider = oldTelProvider
        newNumericKeypad = acc.numericKeypad
        oldDefaultAccount = UserAgent.findAorIndex(aor)!! == 0
        newDefaultAccount = oldDefaultAccount

        val scrollState = rememberScrollState()

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
            Outbound()
            Register()
            RegInt()
            AudioCodecs(ctx)
            MediaEnc()
            MediaNat()
            StunServer()
            StunUser()
            StunPass()
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
            AskPassword(ctx)
        }
    }

    @Composable
    private fun AoR() {
        Row(
            Modifier.fillMaxWidth().padding(top=8.dp, end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            OutlinedTextField(
                value = acc.luri,
                enabled = false,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    color = LocalCustomColors.current.itemText
                ),
                label = {
                    LabelText(text = stringResource(R.string.sip_uri),
                        fontWeight = FontWeight.Bold)
                }
            )
        }
    }

    @Composable
    private fun Nickname() {
        Row(
            Modifier.fillMaxWidth().padding(top=8.dp, end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var nickName by remember { mutableStateOf(oldNickname) }
            OutlinedTextField(
                value = nickName,
                placeholder = { Text(stringResource(R.string.nickname)) },
                onValueChange = {
                    nickName = it
                    newNickname = nickName
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        alertTitle.value = getString(R.string.nickname)
                        alertMessage.value = getString(R.string.account_nickname_help)
                        showAlert.value = true },
                textStyle = TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.nickname)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text),
            )
        }
    }

    @Composable
    private fun DisplayName() {
        Row(
            Modifier.fillMaxWidth().padding(top=8.dp, end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var displayName by remember { mutableStateOf(oldDisplayname) }
            OutlinedTextField(
                value = displayName,
                placeholder = { Text(stringResource(R.string.display_name)) },
                onValueChange = {
                    displayName = it
                    newDisplayname = displayName
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        alertTitle.value = getString(R.string.display_name)
                        alertMessage.value = getString(R.string.display_name_help)
                        showAlert.value = true },
                textStyle = TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.display_name)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text),
            )
        }
    }

    @Composable
    private fun AuthUser() {
        Row(
            Modifier.fillMaxWidth().padding(top=8.dp, end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var authUser by remember { mutableStateOf(oldAuthUser) }
            OutlinedTextField(
                value = authUser,
                placeholder = { Text(stringResource(R.string.authentication_username)) },
                onValueChange = {
                    authUser = it
                    newAuthUser = authUser
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        alertTitle.value = getString(R.string.authentication_username)
                        alertMessage.value = getString(R.string.authentication_username_help)
                        showAlert.value = true },
                textStyle = TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.authentication_username)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    private fun AuthPass() {
        val showPassword = remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(top=8.dp, end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var authPass by remember { mutableStateOf(oldAuthPass) }
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
                        alertTitle.value = getString(R.string.authentication_password)
                        alertMessage.value = getString(R.string.authentication_password_help)
                        showAlert.value = true },
                textStyle = TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.authentication_password)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    private fun Outbound() {
        Text(text = stringResource(R.string.outbound_proxies),
            color = LocalCustomColors.current.itemText,
            fontSize = 18.sp,
            modifier = Modifier.padding(top=8.dp)
                .clickable {
                    alertTitle.value = getString(R.string.outbound_proxies)
                    alertMessage.value = getString(R.string.outbound_proxies_help)
                    showAlert.value = true
                }
        )
        Row(
            Modifier.fillMaxWidth().padding(top=8.dp, end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var outbound1 by remember { mutableStateOf(oldOutbound1) }
            OutlinedTextField(
                value = outbound1,
                placeholder = { Text(stringResource(R.string.sip_uri_of_proxy_server)) },
                onValueChange = {
                    outbound1 = it
                    newOutbound1 = outbound1
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.sip_uri_of_proxy_server)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top=8.dp, end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var outbound2 by remember { mutableStateOf(oldOutbound2) }
            OutlinedTextField(
                value = outbound2,
                placeholder = { Text(stringResource(R.string.sip_uri_of_another_proxy_server)) },
                onValueChange = {
                    outbound2 = it
                    newOutbound2 = outbound2
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.sip_uri_of_another_proxy_server)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun Register() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.register),
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.register)
                        alertMessage.value = getString(R.string.register_help)
                        showAlert.value = true
                    },
                fontSize = 18.sp,
                color = LocalCustomColors.current.itemText)
            var register by remember { mutableStateOf(oldRegister) }
            Switch(
                checked = register,
                onCheckedChange = {
                    register = it
                    newRegister = register
                }
            )
        }
    }

    @Composable
    private fun RegInt() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var regInt by remember { mutableStateOf(oldRegInt) }
            OutlinedTextField(
                value = regInt,
                placeholder = { Text(stringResource(R.string.reg_int)) },
                onValueChange = {
                    regInt = it
                    newRegInt = regInt
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        alertTitle.value = getString(R.string.reg_int)
                        alertMessage.value = getString(R.string.reg_int_help)
                        showAlert.value = true },
                textStyle = TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.reg_int)) },
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
    private fun MediaEnc() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.media_encryption),
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.media_encryption)
                        alertMessage.value = getString(R.string.media_encryption_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember { mutableStateOf(false) }
            val mediaEnc = remember { mutableStateOf(oldMediaEnc) }
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
                        tint = LocalCustomColors.current.itemText)
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
    private fun MediaNat() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.media_nat),
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.media_nat)
                        alertMessage.value = getString(R.string.media_nat_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember { mutableStateOf(false) }
            val mediaNat = remember { mutableStateOf(oldMediaNat) }
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
                        tint = LocalCustomColors.current.itemText)
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
                                showStun.value = newMediaNat != ""
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
    private fun StunServer() {
        if (showStun.value)
            Row(
                Modifier.fillMaxWidth().padding(end=10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                var stunServer by remember { mutableStateOf(oldStunServer) }
                OutlinedTextField(
                    value = stunServer,
                    placeholder = { Text(stringResource(R.string.stun_server)) },
                    onValueChange = {
                        stunServer = it
                        newStunServer = stunServer
                    },
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            alertTitle.value = getString(R.string.stun_server)
                            alertMessage.value = getString(R.string.stun_server_help)
                            showAlert.value = true },
                    textStyle = TextStyle(
                        fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                    label = { LabelText(stringResource(R.string.stun_server)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
    }

    @Composable
    private fun StunUser() {
        if (showStun.value)
            Row(
                Modifier.fillMaxWidth().padding(top=8.dp, end=10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                var stunUser by remember { mutableStateOf(oldStunUser) }
                OutlinedTextField(
                    value = stunUser,
                    placeholder = { Text(stringResource(R.string.stun_username)) },
                    onValueChange = {
                        stunUser = it
                        newStunUser = stunUser
                    },
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            alertTitle.value = getString(R.string.stun_username)
                            alertMessage.value = getString(R.string.stun_username_help)
                            showAlert.value = true },
                    textStyle = TextStyle(
                        fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                    label = { LabelText(stringResource(R.string.stun_username)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
    }

    @Composable
    private fun StunPass() {
        if (showStun.value) {
            val showPassword = remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                var stunPass by remember { mutableStateOf(oldStunPass) }
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
                                colorResource(id = R.color.colorWhite)
                            )
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
                            alertTitle.value = getString(R.string.stun_password)
                            alertMessage.value = getString(R.string.stun_password_help)
                            showAlert.value = true
                        },
                    textStyle = TextStyle(
                        fontSize = 18.sp, color = LocalCustomColors.current.itemText
                    ),
                    label = { LabelText(stringResource(R.string.stun_password)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        }
    }

    @Composable
    fun RtcpMux() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.rtcp_mux),
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.rtcp_mux)
                        alertMessage.value = getString(R.string.rtcp_mux_help)
                        showAlert.value = true
                    },
                fontSize = 18.sp,
                color = LocalCustomColors.current.itemText)
            var rtcpMux by remember { mutableStateOf(oldRtcpMux) }
            Switch(
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
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.rel_100),
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.rel_100)
                        alertMessage.value = getString(R.string.rel_100_help)
                        showAlert.value = true
                    },
                fontSize = 18.sp,
                color = LocalCustomColors.current.itemText)
            var rel100 by remember { mutableStateOf(old100Rel) }
            Switch(
                checked = rel100,
                onCheckedChange = {
                    rel100 = it
                    new100Rel = rel100
                }
            )
        }
    }

    @Composable
    private fun Dtmf() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.dtmf_mode),
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.dtmf_mode)
                        alertMessage.value = getString(R.string.dtmf_mode_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val dtmfMode = remember { mutableIntStateOf(oldDtmfMode) }
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
                        tint = LocalCustomColors.current.itemText)
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
    private fun Answer() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.answer_mode),
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.answer_mode)
                        alertMessage.value = getString(R.string.answer_mode_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val answerMode = remember { mutableIntStateOf(oldAnswerMode) }
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
                        tint = LocalCustomColors.current.itemText)
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
    private fun Redirect() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.redirect_mode),
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.redirect_mode)
                        alertMessage.value = getString(R.string.redirect_mode_help)
                        showAlert.value = true
                    },
                color = LocalCustomColors.current.itemText,
                fontSize = 18.sp)
            val isDropDownExpanded = remember {
                mutableStateOf(false)
            }
            val autoRedirect = remember { mutableStateOf(oldAutoRedirect) }
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
                        tint = LocalCustomColors.current.itemText)
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
    private fun Voicemail() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var vmUri by remember { mutableStateOf(oldVmUri) }
            OutlinedTextField(
                value = vmUri,
                placeholder = { Text(stringResource(R.string.voicemail_uri)) },
                onValueChange = {
                    vmUri = it
                    newVmUri = vmUri
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        alertTitle.value = getString(R.string.voicemail_uri)
                        alertMessage.value = getString(R.string.voicemain_uri_help)
                        showAlert.value = true },
                textStyle = TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.voicemail_uri)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    private fun CountryCode() {
        Row(
            Modifier.fillMaxWidth().padding(top=8.dp, end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var countryCode by remember { mutableStateOf(oldCountryCode) }
            OutlinedTextField(
                value = countryCode,
                placeholder = { Text(stringResource(R.string.country_code)) },
                onValueChange = {
                    countryCode = it
                    newCountryCode = countryCode
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        alertTitle.value = getString(R.string.country_code)
                        alertMessage.value = getString(R.string.country_code_help)
                        showAlert.value = true },
                textStyle = TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.country_code)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    private fun TelProvider() {
        Row(
            Modifier.fillMaxWidth().padding(top=8.dp, end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            var telProvider by remember { mutableStateOf(oldTelProvider) }
            OutlinedTextField(
                value = telProvider,
                placeholder = { Text(stringResource(R.string.telephony_provider)) },
                onValueChange = {
                    telProvider = it
                    newTelProvider = telProvider
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        alertTitle.value = getString(R.string.telephony_provider)
                        alertMessage.value = getString(R.string.telephony_provider_help)
                        showAlert.value = true },
                textStyle = TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText),
                label = { LabelText(stringResource(R.string.telephony_provider)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }

    @Composable
    fun NumericKeypad() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.numeric_keypad),
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.numeric_keypad)
                        alertMessage.value = getString(R.string.numeric_keypad_help)
                        showAlert.value = true
                    },
                fontSize = 18.sp,
                color = LocalCustomColors.current.itemText)
            var numericKeypad by remember { mutableStateOf(acc.numericKeypad) }
            Switch(
                checked = numericKeypad,
                onCheckedChange = {
                    numericKeypad = it
                    newNumericKeypad = numericKeypad
                }
            )
        }
    }

    @Composable
    fun DefaultAccount() {
        Row(
            Modifier.fillMaxWidth().padding(end=10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = stringResource(R.string.default_account),
                modifier = Modifier.weight(1f)
                    .clickable {
                        alertTitle.value = getString(R.string.default_account)
                        alertMessage.value = getString(R.string.default_account_help)
                        showAlert.value = true },
                fontSize = 18.sp,
                color = LocalCustomColors.current.itemText)
            var defaultAccount by remember { mutableStateOf(oldDefaultAccount) }
            Switch(
                checked = defaultAccount,
                onCheckedChange = {
                    defaultAccount = it
                    newDefaultAccount = defaultAccount
                }
            )
        }
    }

    @Composable
    fun AskPassword(ctx: Context) {
        if (showPasswordDialog.value)
            CustomElements.PasswordDialog(
                ctx = ctx,
                showPasswordDialog = showPasswordDialog,
                password = password,
                keyboardController = keyboardController,
                title = stringResource(R.string.authentication_password),
                okAction = {
                    if (password.value != "") {
                        BaresipService.aorPasswords[acc.aor] = password.value
                        Api.account_set_auth_pass(acc.accp, password.value)
                        password.value = ""
                        reRegister = true
                        finishActivity()
                    }
                },
                cancelAction = {
                    reRegister = true
                    finishActivity()
                }
            )
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
                }
                else {
                    alertTitle.value = getString(R.string.notice)
                    alertMessage.value = String.format(getString(R.string.non_unique_account_nickname), nn)
                    showAlert.value = true
                    return
                }
            }
        else {
            alertTitle.value = getString(R.string.notice)
            alertMessage.value = String.format(getString(R.string.invalid_account_nickname), nn)
            showAlert.value = true
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
            }
            else {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_display_name), dn)
                showAlert.value = true
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
                }
                else {
                    Log.e(TAG, "Setting of auth user failed")
                }
            }
            else {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_authentication_username), au)
                showAlert.value = true
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
                    }
                    else
                        Log.e(TAG, "Setting of auth pass failed")
                    BaresipService.aorPasswords.remove(acc.aor)
                }
                else {
                    alertTitle.value = getString(R.string.notice)
                    alertMessage.value = String.format(getString(R.string.invalid_authentication_password), ap)
                    showAlert.value = true
                    return
                }
            }
            else
                BaresipService.aorPasswords.remove(acc.aor)
        }
        else { // ap == ""
            if (acc.authPass != NO_AUTH_PASS &&
                    acc.authPass != BaresipService.aorPasswords[acc.aor])
                if (Api.account_set_auth_pass(acc.accp, "") == 0) {
                    acc.authPass = NO_AUTH_PASS
                    BaresipService.aorPasswords[acc.aor] = NO_AUTH_PASS
                }
        }

        val ob = ArrayList<String>()
        var ob1 = newOutbound1.trim().replace(" ", "")
        if (ob1 != "") {
            if (!ob1.startsWith("sip:"))
                ob1 = "sip:$ob1"
            if (checkOutboundUri(ob1)) {
                ob.add(ob1)
            }
            else {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_proxy_server_uri), ob1)
                showAlert.value = true
                return
            }
        }
        var ob2 = newOutbound2.trim().replace(" ", "")
        if (ob2 != "") {
            if (!ob2.startsWith("sip:"))
                ob2 = "sip:$ob2"
            if (checkOutboundUri(ob2))
                ob.add(ob2)
            else {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_proxy_server_uri), ob2)
                showAlert.value = true
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
            alertTitle.value = getString(R.string.notice)
            alertMessage.value = String.format(getString(R.string.invalid_reg_int), "$regInt")
            showAlert.value = true
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
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_stun_server), newStunServer)
                showAlert.value = true
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
                }
                else
                    Log.e(TAG, "Setting of STUN/TURN user failed")
            }
            else {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_stun_username), newStunUser)
                showAlert.value = true
                return
            }
        }

        val newStunPass = newStunPass.trim()
        if (acc.stunPass != newStunPass) {
            if (newStunPass.isEmpty() || Account.checkAuthPass(newStunPass)) {
                if (Api.account_set_stun_pass(acc.accp, newStunPass) == 0)
                    acc.stunPass = Api.account_stun_pass(acc.accp)
                else
                    Log.e(TAG, "Setting of stun pass failed")
            }
            else {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_stun_password), newStunPass)
                showAlert.value = true
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
                    alertTitle.value = getString(R.string.notice)
                    alertMessage.value = String.format(getString(R.string.invalid_sip_or_tel_uri), newVmUri)
                    showAlert.value = true
                    return
                }
                Api.account_set_mwi(acc.accp, true)
            }
            else
                Api.account_set_mwi(acc.accp, false)
            acc.vmUri = newVmUri
            Log.d(TAG, "New voicemail URI is ${acc.vmUri}")
        }

        newCountryCode = newCountryCode.trim()
        if (newCountryCode != acc.countryCode) {
            if (newCountryCode != "" && !Utils.checkCountryCode(newCountryCode)) {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_country_code), newCountryCode)
                showAlert.value = true
                return
            }
            acc.countryCode = newCountryCode
            Log.d(TAG, "New country code is ${acc.countryCode}")
        }

        val hostPart = newTelProvider.trim()
        if (hostPart != acc.telProvider) {
            if (hostPart != "" && !Utils.checkHostPortParams(hostPart)) {
                alertTitle.value = getString(R.string.notice)
                alertMessage.value = String.format(getString(R.string.invalid_sip_uri_hostpart), hostPart)
                showAlert.value = true
                return
            }
            acc.telProvider = hostPart
            Log.d(TAG, "New tel provider is ${acc.telProvider}")
        }

        if (newNumericKeypad != acc.numericKeypad) {
            acc.numericKeypad = newNumericKeypad
            Log.d(TAG, "New numericKeyboard is ${acc.numericKeypad}")
        }

        val uaIndex = UserAgent.findAorIndex(aor)!!
        if (newDefaultAccount && (uaIndex > 0)) {
            val updatedUas = uas.value.toMutableList()
            updatedUas.add(0, uas.value[uaIndex])
            updatedUas.removeAt(uaIndex + 1)
            uas.value = updatedUas.toList()
        }

        AccountsActivity.saveAccounts()

        if (acc.authUser != "" && BaresipService.aorPasswords[aor] == NO_AUTH_PASS)
            showPasswordDialog.value = true
        else
            finishActivity()
    }

    private fun finishActivity() {
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

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (backInvokedCallback != null)
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backInvokedCallback!!)
        }
        else
            onBackPressedCallback.remove()
        super.onDestroy()
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


    private fun initAccountFromConfig(acc: Account, onConfigLoaded: () -> Unit) {
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch(Dispatchers.IO) {
            val url = "https://${Utils.uriHostPart(acc.aor)}/baresip/account_config.xml"
            val config = try {
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
                                    acc.configuredRegInt = text.toInt()

                                "register" ->
                                    acc.regint = if (text == "yes") acc.configuredRegInt else 0

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

}

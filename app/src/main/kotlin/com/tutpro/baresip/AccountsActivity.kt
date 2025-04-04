package com.tutpro.baresip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

class AccountsActivity : ComponentActivity() {

    internal lateinit var aor: String
    private lateinit var mediaEncMap: Map<String, String>
    private lateinit var mediaNatMap: Map<String, String>

    private var lastClick: Long = 0
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val title = String.format(getString(R.string.accounts))

        aor = intent.getStringExtra("aor")!!
        Utils.addActivity("accounts,$aor")

        mediaEncMap = mapOf("zrtp" to "ZRTP", "dtls_srtp" to "DTLS-SRTPF",
            "srtp-mand" to "SRTP-MAND", "srtp" to "SRTP", "" to "--")

        mediaNatMap = mapOf("stun" to "STUN", "turn" to "TURN", "ice" to "ICE", "" to "--")

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    AccountsScreen(this, title) { goBack() }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AccountsScreen(ctx: Context, title: String, navigateBack: () -> Unit) {
        Scaffold(
            modifier = Modifier.fillMaxHeight()
                .imePadding()
                .safeDrawingPadding(),
            containerColor = LocalCustomColors.current.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = title,
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
            bottomBar = { NewAccount(ctx) },
            content = { contentPadding ->
                AccountsContent(ctx, contentPadding)
            },
        )
    }

    @Composable
    fun AccountsContent(ctx: Context, contentPadding: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(LocalCustomColors.current.background)
                .padding(contentPadding)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.Top
        ) {
            val lazyListState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .imePadding()
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp)
                    .verticalScrollbar(
                        state = lazyListState,
                        width = 4.dp,
                        color = LocalCustomColors.current.gray
                    ),
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(BaresipService.uas.value, key = { it.account.aor }) { ua ->
                    val account = ua.account
                    val aor = account.aor
                    val text = if (account.nickName.value != "")
                        account.nickName.value
                    else
                        account.aor.substringAfter(":")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = text,
                            fontSize = 20.sp,
                            color = LocalCustomColors.current.itemText,
                            modifier = Modifier.weight(1f).padding(start = 10.dp)
                                .clickable {
                                    val i = Intent(ctx, AccountActivity::class.java)
                                    val b = Bundle()
                                    b.putString("aor", aor)
                                    i.putExtras(b)
                                    startActivity(i)
                                }
                        )
                        SmallFloatingActionButton(
                            onClick = {
                                if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                                    lastClick = SystemClock.elapsedRealtime()
                                    with(
                                        MaterialAlertDialogBuilder(
                                            ctx,
                                            R.style.AlertDialogTheme
                                        )
                                    ) {
                                        setTitle(R.string.confirmation)
                                        setMessage(
                                            String.format(
                                                ctx.getString(R.string.delete_account),
                                                text
                                            )
                                        )
                                        setPositiveButton(R.string.delete) { dialog, _ ->
                                                CallHistoryNew.clear(aor)
                                                Message.clearMessagesOfAor(aor)
                                                ua.remove()
                                                Api.ua_destroy(ua.uap)
                                            saveAccounts()
                                            dialog.dismiss()
                                        }
                                        setNeutralButton(ctx.getText(R.string.cancel)) { dialog, _ ->
                                            dialog.dismiss()
                                        }
                                        show()
                                    }
                                }
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
    fun NewAccount(ctx: Context) {
        var newAor by remember { mutableStateOf("") }
        val focusManager = LocalFocusManager.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newAor,
                placeholder = { CustomElements.Text(text = getString(R.string.new_account)) },
                onValueChange = { newAor = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .verticalScroll(rememberScrollState())
                    .clickable {
                        Utils.alertView(ctx, getString(R.string.new_account),
                            getString(R.string.accounts_help)) },
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
                label = {
                    CustomElements.Text(
                        text = getString(R.string.new_account),
                        fontSize = 18.sp
                    )
                },
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
                            val account = createNew(newAor.trim())
                            if (account != null) {
                                val i = Intent(ctx, AccountActivity::class.java)
                                val b = Bundle()
                                b.putString("aor", account.aor)
                                i.putExtras(b)
                                startActivity(i)
                                newAor = ""
                                focusManager.clearFocus()
                            }
                        }
                    ),
            )
        }
    }

    private fun createNew(newAor: String): Account? {

        val aor = if (newAor.startsWith("sip:"))
                newAor
            else
                "sip:$newAor"

        if (!Utils.checkAor(aor)) {
            Log.d(TAG, "Invalid Address of Record $aor")
            Utils.alertView(
                this, getString(R.string.notice),
                String.format(getString(R.string.invalid_aor),
                    aor.split(":")[1])
            )
            return null
        }

        if (Account.ofAor(aor) != null) {
            Log.d(TAG, "Account $aor already exists")
            Utils.alertView(
                this, getString(R.string.notice),
                String.format(getString(R.string.account_exists),
                    aor.split(":")[1])
            )
            return null
        }

        val ua = UserAgent.uaAlloc(
            "<$aor>;stunserver=\"stun:stun.l.google.com:19302\";regq=0.5;pubint=0;regint=0;mwi=no"
        )
        if (ua == null) {
            Log.e(TAG, "Failed to allocate UA for $aor")
            Utils.alertView(
                this, getString(R.string.notice),
                getString(R.string.account_allocation_failure)
            )
            return null
        }

        // Api.account_debug(ua.account.accp)
        val acc = ua.account
        Log.d(TAG, "Allocated UA ${ua.uap} for ${Api.account_luri(acc.accp)}")
        initAccountFromConfig(this@AccountsActivity, acc)
        saveAccounts()

        return acc
    }

    private fun initAccountFromConfig(ctx: AccountsActivity, acc: Account) {
        scope.launch(Dispatchers.IO) {
            val url = "https://${Utils.uriHostPart(acc.aor)}/baresip/account_config.xml"
            val config = try {
                URL(url).readText()
            } catch (e: java.lang.Exception) {
                Log.d(TAG, "Failed to get account configuration from network")
                null
            }
            if (config != null && !ctx.isFinishing) {
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
        }
    }

    private fun goBack() {
        BaresipService.activities.remove("accounts,$aor")
        setResult(RESULT_CANCELED, Intent())
        finish()
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
    }

    companion object {

        fun saveAccounts() {
            var accounts = ""
            for (a in Account.accounts()) accounts = accounts + a.print() + "\n"
            Utils.putFileContents(BaresipService.filesPath + "/accounts",
                accounts.toByteArray(Charsets.UTF_8))
            // Log.d(TAG, "Saved accounts '${accounts}' to '${BaresipService.filesPath}/accounts'")
        }

    }

}

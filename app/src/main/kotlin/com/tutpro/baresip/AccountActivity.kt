package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.tutpro.baresip.databinding.ActivityAccountBinding
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.lang.ref.WeakReference
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList

class AccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountBinding
    private lateinit var acc: Account
    private lateinit var ua: UserAgent
    private lateinit var uri: TextView
    private lateinit var displayName: EditText
    private lateinit var aor: String
    private lateinit var authUser: EditText
    private lateinit var authPass: EditText
    private lateinit var outbound1: EditText
    private lateinit var outbound2: EditText
    private lateinit var mediaNat: String
    private lateinit var stunServer: EditText
    private lateinit var stunUser: EditText
    private lateinit var stunPass: EditText
    private lateinit var regCheck: CheckBox
    private lateinit var mediaNatSpinner: Spinner
    private lateinit var mediaEnc: String
    private lateinit var mediaEncSpinner: Spinner
    private lateinit var ipV6MediaCheck: CheckBox
    private var dtmfMode = Api.DTMFMODE_RTP_EVENT
    private lateinit var dtmfModeSpinner: Spinner
    private var answerMode = Api.ANSWERMODE_MANUAL
    private lateinit var answerModeSpinner: Spinner
    private lateinit var vmUri: EditText
    private lateinit var defaultCheck: CheckBox

    private val mediaEncKeys = arrayListOf("zrtp", "dtls_srtp", "srtp-mandf", "srtp-mand", "srtp", "")
    private val mediaEncVals = arrayListOf("ZRTP", "DTLS-SRTPF", "SRTP-MANDF", "SRTP-MAND", "SRTP", "-")
    private val mediaNatKeys = arrayListOf("stun", "turn", "ice", "")
    private val mediaNatVals = arrayListOf("STUN", "TURN", "ICE", "-")

    private var save = false
    private var uaIndex= -1
    private val TAG = "Baresip"

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        uri = binding.Uri
        displayName = binding.DisplayName
        authUser = binding.AuthUser
        authPass = binding.AuthPass
        outbound1 = binding.Outbound1
        outbound2 = binding.Outbound2
        regCheck = binding.Register
        mediaNatSpinner = binding.mediaNatSpinner
        stunServer = binding.StunServer
        stunUser = binding.StunUser
        stunPass = binding.StunPass
        mediaEncSpinner = binding.mediaEncSpinner
        ipV6MediaCheck = binding.PreferIPv6Media
        dtmfModeSpinner = binding.dtmfModeSpinner
        answerModeSpinner = binding.answerModeSpinner
        vmUri = binding.voicemailUri
        defaultCheck = binding.Default

        aor = intent.getStringExtra("aor")!!
        ua = UserAgent.ofAor(aor)!!
        acc = ua.account
        uaIndex = UserAgent.findAorIndex(aor)!!

        Utils.addActivity("account,$aor")

        if (intent.getBooleanExtra("new", false)) {
            val url = "https://${Utils.uriHostPart(aor)}/baresip/account_config.xml"
            GetAccountConfigAsyncTask(this).execute(url)
        }

        title = aor.split(":")[1]

        initLayoutFromAccount(acc)

    }

    private fun initLayoutFromAccount(acc: Account) {

        uri.text = acc.luri
        displayName.setText(acc.displayName)
        authUser.setText(acc.authUser)

        if (MainActivity.aorPasswords.containsKey(aor))
            authPass.setText("")
        else
            authPass.setText(acc.authPass)

        if (acc.outbound.size > 0) {
            outbound1.setText(acc.outbound[0])
            if (acc.outbound.size > 1)
                outbound2.setText(acc.outbound[1])
        }

        regCheck.isChecked = acc.regint > 0

        this.acc.audioCodec = acc.audioCodec

        mediaNat = acc.mediaNat
        var keyIx = mediaNatKeys.indexOf(acc.mediaNat)
        var keyVal = mediaNatVals.elementAt(keyIx)
        mediaNatKeys.removeAt(keyIx)
        mediaNatVals.removeAt(keyIx)
        mediaNatKeys.add(0, acc.mediaNat)
        mediaNatVals.add(0, keyVal)
        val mediaNatAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
                mediaNatVals)
        mediaNatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mediaNatSpinner.adapter = mediaNatAdapter
        mediaNatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                mediaNat = mediaNatKeys[mediaNatVals.indexOf(parent.selectedItem.toString())]
                if ((mediaNat == "turn") && stunServer.text.startsWith("stun"))
                    stunServer.setText("")
                else if ((mediaNat == "stun") &&
                        (stunServer.text.startsWith("turn") || (stunServer.text.toString() == "")))
                    stunServer.setText(resources.getString(R.string.stun_server_default))
                else if ((mediaNat == "ice") && (stunServer.text.toString() == ""))
                    stunServer.setText(resources.getString(R.string.stun_server_default))
                else if (mediaNat == "") {
                    stunServer.setText("")
                    stunUser.setText("")
                    stunPass.setText("")
                }
                stunServer.isEnabled = mediaNat != ""
                stunUser.isEnabled = mediaNat != ""
                stunPass.isEnabled = mediaNat != ""
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        stunServer.setText(acc.stunServer)
        stunServer.isEnabled = mediaNat != ""

        stunUser.setText(acc.stunUser)
        stunUser.isEnabled = mediaNat != ""

        stunPass.setText(acc.stunPass)
        stunPass.isEnabled = mediaNat != ""

        mediaEnc = acc.mediaEnc
        keyIx = mediaEncKeys.indexOf(mediaEnc)
        keyVal = mediaEncVals.elementAt(keyIx)
        mediaEncKeys.removeAt(keyIx)
        mediaEncVals.removeAt(keyIx)
        mediaEncKeys.add(0, mediaEnc)
        mediaEncVals.add(0, keyVal)
        val mediaEncAdapter = ArrayAdapter(this,android.R.layout.simple_spinner_item,
                mediaEncVals)
        mediaEncAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mediaEncSpinner.adapter = mediaEncAdapter
        mediaEncSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                mediaEnc = mediaEncKeys[mediaEncVals.indexOf(parent.selectedItem.toString())]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        ipV6MediaCheck.isChecked = acc.preferIPv6Media

        dtmfMode = acc.dtmfMode
        val dtmfModeKeys = arrayListOf(Api.DTMFMODE_RTP_EVENT, Api.DTMFMODE_SIP_INFO)
        val dtmfModeVals = arrayListOf(getString(R.string.dtmf_inband), getString(R.string.dtmf_info))
        keyIx = dtmfModeKeys.indexOf(acc.dtmfMode)
        keyVal = dtmfModeVals.elementAt(keyIx)
        dtmfModeKeys.removeAt(keyIx)
        dtmfModeVals.removeAt(keyIx)
        dtmfModeKeys.add(0, acc.dtmfMode)
        dtmfModeVals.add(0, keyVal)
        val dtmfModeAdapter = ArrayAdapter(this,android.R.layout.simple_spinner_item,
                dtmfModeVals)
        dtmfModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dtmfModeSpinner.adapter = dtmfModeAdapter
        dtmfModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                dtmfMode = dtmfModeKeys[dtmfModeVals.indexOf(parent.selectedItem.toString())]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        answerMode = acc.answerMode
        val answerModeKeys = arrayListOf(Api.ANSWERMODE_MANUAL, Api.ANSWERMODE_AUTO)
        val answerModeVals = arrayListOf(getString(R.string.manual), getString(R.string.auto))
        keyIx = answerModeKeys.indexOf(acc.answerMode)
        keyVal = answerModeVals.elementAt(keyIx)
        answerModeKeys.removeAt(keyIx)
        answerModeVals.removeAt(keyIx)
        answerModeKeys.add(0, acc.answerMode)
        answerModeVals.add(0, keyVal)
        val answerModeAdapter = ArrayAdapter(this,android.R.layout.simple_spinner_item,
                answerModeVals)
        answerModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        answerModeSpinner.adapter = answerModeAdapter
        answerModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                answerMode = answerModeKeys[answerModeVals.indexOf(parent.selectedItem.toString())]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        vmUri.setText(acc.vmUri)

        defaultCheck.isChecked = uaIndex == 0

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)
        return true

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("account,$aor") == -1)
            return true

        when (item.itemId) {

            R.id.checkIcon -> {
                val dn = displayName.text.toString().trim()
                if (dn != acc.displayName) {
                    if (Account.checkDisplayName(dn)) {
                        if (Api.account_set_display_name(acc.accp, dn) == 0) {
                            acc.displayName = Api.account_display_name(acc.accp);
                            Log.d(TAG, "New display name is ${acc.displayName}")
                            save = true
                        } else {
                            Log.e(TAG, "Setting of display name failed")
                        }
                    } else {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.invalid_display_name), dn))
                        return false
                    }
                }

                val au = authUser.text.toString().trim()
                val ap = authPass.text.toString().trim()

                if (au != acc.authUser) {
                    if (Account.checkAuthUser(au)) {
                        if (Api.account_set_auth_user(acc.accp, au) == 0) {
                            acc.authUser = Api.account_auth_user(acc.accp);
                            Log.d(TAG, "New auth user is ${acc.authUser}")
                            save = true
                        } else {
                            Log.e(TAG, "Setting of auth user failed")
                        }
                    } else {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.invalid_authentication_username), au))
                        return false
                    }
                }

                if (ap != "") {
                    if (ap != acc.authPass) {
                        if (Account.checkAuthPass(ap)) {
                            setAuthPass(acc, ap)
                            MainActivity.aorPasswords.remove(aor)
                        } else {
                            Utils.alertView(this, getString(R.string.notice),
                                    String.format(getString(R.string.invalid_authentication_password), ap))
                            return false
                        }
                    } else {
                        if (MainActivity.aorPasswords.containsKey(aor)) {
                            MainActivity.aorPasswords.remove(aor)
                            save = true
                        }
                    }
                } else { // ap == ""
                    if (acc.authUser != "") {
                        if (!MainActivity.aorPasswords.containsKey(aor)) {
                            setAuthPass(acc, "")
                            MainActivity.aorPasswords.put(aor, "")
                        }
                    } else {
                        if (acc.authPass != "") {
                            setAuthPass(acc, "")
                            MainActivity.aorPasswords.remove(aor)
                        }
                    }
                }

                val ob = ArrayList<String>()
                var uri: String
                if (outbound1.text.toString().trim() != "") {
                    uri = outbound1.text.toString().trim().replace(" ", "")
                    if (!uri.startsWith("sip:")) uri = "sip:" + uri
                    ob.add(uri)
                }
                if (outbound2.text.toString().trim() != "") {
                    uri = outbound2.text.toString().trim().replace(" ", "")
                    if (!uri.startsWith("sip:")) uri = "sip:" + uri
                    ob.add(uri)
                }
                if (ob != acc.outbound) {
                    val outbound = ArrayList<String>()
                    for (i in ob.indices) {
                        if ((ob[i] == "") || checkOutboundUri(ob[i])) {
                            if (Api.account_set_outbound(acc.accp, ob[i], i) == 0) {
                                if (ob[i] != "")
                                    outbound.add(Api.account_outbound(acc.accp, i))
                            } else {
                                Log.e(TAG, "Setting of outbound proxy ${ob[i]} failed")
                                break
                            }
                        } else {
                            Utils.alertView(this, getString(R.string.notice),
                                    String.format(getString(R.string.invalid_proxy_server_uri), ob[i]))
                            return false
                        }
                    }
                    Log.d(TAG, "New outbound proxies are ${outbound}")
                    acc.outbound = outbound
                    if (outbound.isEmpty())
                        Api.account_set_sipnat(acc.accp, "")
                    else
                        Api.account_set_sipnat(acc.accp, "outbound")
                    save = true
                }

                var newRegint = -1
                if (regCheck.isChecked) {
                    if (acc.regint != 3600) {
                        newRegint = 3600
                        ua.updateStatus(R.drawable.dot_yellow)
                    }
                } else {
                    if (acc.regint != 0) {
                        Api.ua_unregister(ua.uap)
                        ua.updateStatus(R.drawable.dot_white)
                        newRegint = 0
                    }
                }
                if (newRegint != -1)
                    if (Api.account_set_regint(acc.accp, newRegint) == 0) {
                        acc.regint = Api.account_regint(acc.accp)
                        Log.d(TAG, "New regint is ${acc.regint}")
                        save = true
                    } else {
                        Log.e(TAG, "Setting of regint failed")
                    }

                if (mediaNat != acc.mediaNat) {
                    if (Api.account_set_medianat(acc.accp, mediaNat) == 0) {
                        acc.mediaNat = Api.account_medianat(acc.accp)
                        Log.d(TAG, "New medianat is ${acc.mediaNat}")
                        save = true
                    } else {
                        Log.e(TAG, "Setting of medianat failed")
                    }
                }

                var newStunServer = stunServer.text.toString().trim()
                if (mediaNat != "") {
                    if (((mediaNat == "stun") || (mediaNat == "ice")) && (newStunServer == ""))
                        newStunServer = resources.getString(R.string.stun_server_default)
                    if (!Utils.checkStunUri(newStunServer) ||
                            ((mediaNat == "turn") && !newStunServer.startsWith("turn:"))) {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.invalid_stun_server), newStunServer))
                        return false
                    }
                }

                if (acc.stunServer != newStunServer) {
                    if (Api.account_set_stun_uri(acc.accp, newStunServer) == 0) {
                        acc.stunServer = Api.account_stun_uri(acc.accp)
                        Log.d(TAG, "New STUN/TURN server URI is '${acc.stunServer}'")
                        save = true
                    } else {
                        Log.e(TAG, "Setting of STUN/TURN URI server failed")
                    }
                }

                val newStunUser = stunUser.text.toString().trim()
                if (acc.stunUser != newStunUser) {
                    if (Account.checkAuthUser(newStunUser)) {
                        if (Api.account_set_stun_user(acc.accp, newStunUser) == 0) {
                            acc.stunUser = Api.account_stun_user(acc.accp);
                            Log.d(TAG, "New STUN/TURN user is ${acc.stunUser}")
                            save = true
                        } else {
                            Log.e(TAG, "Setting of STUN/TURN user failed")
                        }
                    } else {
                        Utils.alertView(this, getString(R.string.notice), String.format(getString(R.string.invalid_stun_username),
                                newStunUser))
                        return false
                    }
                }

                val newStunPass = stunPass.text.toString().trim()
                if (acc.stunPass != newStunPass) {
                    if (newStunPass.isEmpty() || Account.checkAuthPass(newStunPass)) {
                        if (Api.account_set_stun_pass(acc.accp, newStunPass) == 0) {
                            acc.stunPass = Api.account_stun_pass(acc.accp);
                            save = true
                        } else {
                            Log.e(TAG, "Setting of stun pass failed")
                        }
                    } else {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.invalid_stun_password), newStunPass))
                        return false
                    }
                }

                if (mediaEnc != acc.mediaEnc) {
                    if (Api.account_set_mediaenc(acc.accp, mediaEnc) == 0) {
                        acc.mediaEnc = Api.account_mediaenc(acc.accp)
                        Log.d(TAG, "New mediaenc is ${acc.mediaEnc}")
                        save = true
                    } else {
                        Log.e(TAG, "Setting of mediaenc $mediaEnc failed")
                    }
                }

                if (ipV6MediaCheck.isChecked != acc.preferIPv6Media) {
                    acc.preferIPv6Media = ipV6MediaCheck.isChecked
                    Log.d(TAG, "New preferIPv6Media is ${acc.preferIPv6Media}")
                    if (acc.preferIPv6Media)
                        Api.ua_set_media_af(ua.uap, Api.AF_INET6)
                    else
                        Api.ua_set_media_af(ua.uap, Api.AF_UNSPEC)
                    save = true
                }

                if (dtmfMode != acc.dtmfMode) {
                    if (Api.account_set_dtmfmode(acc.accp, dtmfMode) == 0) {
                        acc.dtmfMode = Api.account_dtmfmode(acc.accp)
                        Log.d(TAG, "New dtmfmode is ${acc.dtmfMode}")
                        save = true
                    } else {
                        Log.e(TAG, "Setting of dtmfmode $dtmfMode failed")
                    }
                }

                if (answerMode != acc.answerMode) {
                    if (Api.account_set_answermode(acc.accp, answerMode) == 0) {
                        acc.answerMode = Api.account_answermode(acc.accp)
                        Log.d(TAG, "New answermode is ${acc.answerMode}")
                        save = true
                    } else {
                        Log.e(TAG, "Setting of answermode $answerMode failed")
                    }
                }

                var tVmUri = vmUri.text.toString().trim()
                if (tVmUri != acc.vmUri) {
                    if (tVmUri != "") {
                        if (!tVmUri.startsWith("sip:")) tVmUri = "sip:$tVmUri"
                        if (!tVmUri.contains("@")) tVmUri = "$tVmUri@${acc.host()}"
                        if (!Utils.checkSipUri(tVmUri)) {
                            Utils.alertView(this, getString(R.string.notice),
                                    String.format(getString(R.string.invalid_voicemail_uri), tVmUri))
                            return false
                        }
                        Api.account_set_mwi(acc.accp, "yes")
                    } else {
                        Api.account_set_mwi(acc.accp, "no")
                    }
                    acc.vmUri = tVmUri
                    save = true
                }

                if (defaultCheck.isChecked && (uaIndex > 0)) {
                    val uasTmp = BaresipService.uas[0]
                    val statusTmp = BaresipService.status[0]
                    BaresipService.uas[0] = BaresipService.uas[uaIndex]
                    BaresipService.status[0] = BaresipService.status[uaIndex]
                    BaresipService.uas[uaIndex] = uasTmp
                    BaresipService.status[uaIndex] = statusTmp
                    save = true
                }

                if (save) {
                    AccountsActivity.saveAccounts()
                    if (Api.ua_update_account(ua.uap) != 0)
                        Log.e(TAG, "Failed to update UA ${ua.uap} with AoR $aor")
                    //else
                        //Api.ua_debug(ua.uap)
                }

                if (regCheck.isChecked && !((acc.authUser != "") && (acc.authPass == "")))
                    Api.ua_register(ua.uap)

                BaresipService.activities.remove("account,$aor")
                returnResult(Activity.RESULT_OK)
                return true
            }

            android.R.id.home -> {
                onBackPressed()
                return true
            }

        }

        return super.onOptionsItemSelected(item)

    }

    override fun onBackPressed() {
        BaresipService.activities.remove("account,$aor")
        returnResult(Activity.RESULT_CANCELED)
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
    }

    fun onClick(v: View) {
        when (v) {
            binding.DisplayNameTitle -> {
                Utils.alertView(this, getString(R.string.display_name),
                        getString(R.string.display_name_help))
            }
            binding.AuthUserTitle -> {
                Utils.alertView(this, getString(R.string.authentication_username),
                        getString(R.string.authentication_username_help))
            }
            binding.AuthPassTitle -> {
                Utils.alertView(this, getString(R.string.authentication_password),
                        getString(R.string.authentication_password_help))
            }
            binding.OutboundProxyTitle -> {
                Utils.alertView(this, getString(R.string.outbound_proxies),
                        getString(R.string.outbound_proxies_help))
            }
            binding.RegTitle -> {
                Utils.alertView(this, getString(R.string.register),
                        getString(R.string.register_help))
            }
            binding.AudioCodecsTitle -> {
                val i = Intent(this, CodecsActivity::class.java)
                val b = Bundle()
                b.putString("aor", aor)
                b.putString("media", "audio")
                i.putExtras(b)
                startActivity(i)
            }
            binding.MediaNatTitle -> {
                Utils.alertView(this, getString(R.string.media_nat),
                        getString(R.string.media_nat_help))
            }
            binding.StunServerTitle -> {
                Utils.alertView(this, getString(R.string.stun_server),
                        getString(R.string.stun_server_help))
            }
            binding.StunUserTitle -> {
                Utils.alertView(this, getString(R.string.stun_username),
                        getString(R.string.stun_username_help))
            }
            binding.StunPassTitle -> {
                Utils.alertView(this, getString(R.string.stun_password),
                        getString(R.string.stun_password_help))
            }
            binding.MediaEncTitle -> {
                Utils.alertView(this, getString(R.string.media_encryption),
                        getString(R.string.media_encryption_help))
            }
            binding.PreferIPv6MediaTitle -> {
                Utils.alertView(this, getString(R.string.prefer_ipv6_media),
                        getString(R.string.prefer_ipv6_media_help))
            }
            binding.DtmfModeTitle -> {
                Utils.alertView(this, getString(R.string.dtmf_mode),
                        getString(R.string.dtmf_mode_help))
            }
            binding.AnswerModeTitle -> {
                Utils.alertView(this, getString(R.string.answer_mode),
                        getString(R.string.answer_mode_help))
            }
            binding.VoicemailUriTitle -> {
                Utils.alertView(this, getString(R.string.voicemail_uri),
                        getString(R.string.voicemain_uri_help))
            }
            binding.DefaultTitle -> {
                Utils.alertView(this, getString(R.string.default_account),
                        getString(R.string.default_account_help))
            }
        }
    }

    private class GetAccountConfigAsyncTask(context: AccountActivity):
            AsyncTask<String, String, String>() {

        private val activityReference: WeakReference<AccountActivity> = WeakReference(context)
        private val TAG = "Baresip"

        override fun doInBackground(vararg url: String?): String? {
            val result = try {
                URL(url[0]).readText()
            } catch (e: Exception) {
                Log.e(TAG, "Could not get account config from ${url[0]}: $e")
                null
            }
            Log.d(TAG, "Got account config $result")
            return result
        }

        override fun onPostExecute(result: String?) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing || result == null)
                return
            val acc = Account(activity.acc.accp)
            val parserFactory: XmlPullParserFactory = XmlPullParserFactory.newInstance()
            val parser: XmlPullParser = parserFactory.newPullParser()
            parser.setInput(StringReader(result))
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
                            "register" ->
                                acc.regint = if (text == "yes") 3600 else 0
                            "audio-codec" ->
                                if (text in audioCodecs)
                                    acc.audioCodec.add(text)
                            "video-codec" ->
                                if (text in videoCodecs)
                                    acc.videoCodec.add(text)
                            "media-encoding" -> {
                                val enc = text.toLowerCase(Locale.ROOT)
                                if (enc in activity.mediaEncKeys && enc.isNotEmpty())
                                    acc.mediaEnc = enc
                            }
                            "media-nat" -> {
                                val nat = text.toLowerCase(Locale.ROOT)
                                if (nat in activity.mediaNatKeys && nat.isNotEmpty())
                                    acc.mediaNat = nat
                            }
                            "stun-turn-server" ->
                                if (text.isNotEmpty())
                                    acc.stunServer = text
                            "prefer-ipv6-media" ->
                                acc.preferIPv6Media = text == "yes"
                            "dtmf-mode" ->
                                if (text in arrayOf("rtp-event", "sip-info")) {
                                    acc.dtmfMode = if (text == "rtp-event")
                                        Api.DTMFMODE_RTP_EVENT
                                    else
                                        Api.DTMFMODE_SIP_INFO
                                }
                            "answer-mode" ->
                                if (text in arrayOf("manual", "auto")) {
                                    acc.answerMode = if (text == "manual")
                                        Api.ANSWERMODE_MANUAL
                                    else
                                        Api.ANSWERMODE_AUTO
                                }
                            "voicemail-uri" ->
                                if (text.isNotEmpty())
                                    acc.vmUri = text
                    }
                }
                event = parser.next()
            }
            activity.initLayoutFromAccount(acc)
        }
    }

    private fun returnResult(code: Int) {
        val i = Intent()
        if (code == Activity.RESULT_OK)
            i.putExtra("aor", aor)
        setResult(code, i)
        finish()
    }

    private fun setAuthPass(acc: Account, ap: String) {
        if (Api.account_set_auth_pass(acc.accp, ap) == 0) {
            acc.authPass = Api.account_auth_pass(acc.accp)
            MainActivity.aorPasswords.remove(acc.aor)
            save = true
        } else {
            Log.e(TAG, "Setting of auth pass failed")
        }
    }

    private fun checkOutboundUri(uri: String): Boolean {
        if (!uri.startsWith("sip:")) return false
        return Utils.checkHostPortParams(uri.substring(4))
    }

}


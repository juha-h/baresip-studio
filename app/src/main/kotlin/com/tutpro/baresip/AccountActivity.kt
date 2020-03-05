package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout.LayoutParams
import android.widget.*

import kotlinx.android.synthetic.main.activity_account.*

class AccountActivity : AppCompatActivity() {

    internal lateinit var acc: Account
    internal lateinit var ua: UserAgent
    internal lateinit var uri: TextView
    internal lateinit var displayName: EditText
    internal lateinit var aor: String
    internal lateinit var authUser: EditText
    internal lateinit var authPass: EditText
    internal lateinit var outbound1: EditText
    internal lateinit var outbound2: EditText
    internal lateinit var mediaNat: String
    internal lateinit var stunServer: EditText
    internal lateinit var regCheck: CheckBox
    internal lateinit var mediaEnc: String
    internal lateinit var ipV6MediaCheck: CheckBox
    internal lateinit var answerMode: String
    internal lateinit var vmUri: EditText
    internal lateinit var defaultCheck: CheckBox

    private var newCodecs = ArrayList<String>()
    private var save = false
    private var uaIndex= -1

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        val accp = intent.getStringExtra("accp")
        Utils.addActivity("account,$accp")

        acc = Account.find(accp)!!
        aor = acc.aor
        uaIndex = UserAgent.findAorIndex(aor)!!
        ua = UserAgent.uas()[uaIndex]

        setTitle(aor.split(":")[1])

        uri = findViewById(R.id.Uri) as TextView
        uri.setText(acc.uri)

        displayName = findViewById(R.id.DisplayName) as EditText
        displayName.setText(acc.displayName)

        authUser = findViewById(R.id.AuthUser) as EditText
        authUser.setText(acc.authUser)

        authPass = findViewById(R.id.AuthPass) as EditText
        authPass.setText(acc.authPass)

        outbound1 = findViewById(R.id.Outbound1) as EditText
        outbound2 = findViewById(R.id.Outbound2) as EditText
        if (acc.outbound.size > 0) {
            outbound1.setText(acc.outbound[0])
            if (acc.outbound.size > 1)
                outbound2.setText(acc.outbound[1])
        }

        regCheck = findViewById(R.id.Register) as CheckBox
        regCheck.isChecked = acc.regint > 0

        val audioCodecs = ArrayList(Api.audio_codecs().split(","))
        newCodecs.addAll(audioCodecs)
        while (newCodecs.size < audioCodecs.size) newCodecs.add("")

        val layout = findViewById(R.id.CodecSpinners) as LinearLayout
        val spinnerList = Array(audioCodecs.size, {_ -> ArrayList<String>()})
        for (i in audioCodecs.indices) {
            val spinner = Spinner(applicationContext)
            spinner.id = i + 100
            spinner.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            layout.addView(spinner)
            if (acc.audioCodec.size > i) {
                val codec = acc.audioCodec[i]
                spinnerList[i].add(codec)
                for (c in audioCodecs) if (c != codec) spinnerList[i].add(c)
                spinnerList[i].add("-")
            } else {
                spinnerList[i].addAll(audioCodecs)
                spinnerList[i].add(0, "-")
            }
            val codecSpinner = findViewById(spinner.id) as Spinner
            val adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                spinnerList[i])
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            codecSpinner.adapter = adapter
            codecSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    newCodecs.set(parent.id - 100, parent.selectedItem.toString())
                }
                override fun onNothingSelected(parent: AdapterView<*>) {
                }
            }
        }

        mediaNat = acc.mediaNat
        val mediaNatSpinner = findViewById(R.id.mediaNatSpinner) as Spinner
        val mediaNatKeys = arrayListOf("stun", "ice", "")
        val mediaNatVals = arrayListOf("STUN", "ICE", "-")
        var keyIx = mediaNatKeys.indexOf(acc.mediaNat)
        var keyVal = mediaNatVals.elementAt(keyIx)
        mediaNatKeys.removeAt(keyIx)
        mediaNatVals.removeAt(keyIx)
        mediaNatKeys.add(0, acc.mediaNat)
        mediaNatVals.add(0, keyVal)
        val mediaNatAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                mediaNatVals)
        mediaNatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mediaNatSpinner.adapter = mediaNatAdapter
        mediaNatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                mediaNat = mediaNatKeys[mediaNatVals.indexOf(parent.selectedItem.toString())]
                stunServer.isEnabled = mediaNat != ""
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        stunServer = findViewById(R.id.StunServer) as EditText
        stunServer.setText(acc.stunServer)
        stunServer.isEnabled = mediaNat != ""

        mediaEnc = acc.mediaEnc
        val mediaEncSpinner = findViewById(R.id.mediaEncSpinner) as Spinner
        val mediaEncKeys = arrayListOf("zrtp", "dtls_srtp", "srtp-mandf", "srtp-mand", "srtp", "")
        val mediaEncVals = arrayListOf("ZRTP", "DTLS-SRTPF", "SRTP-MANDF", "SRTP-MAND", "SRTP", "-")
        keyIx = mediaEncKeys.indexOf(acc.mediaEnc)
        keyVal = mediaEncVals.elementAt(keyIx)
        mediaEncKeys.removeAt(keyIx)
        mediaEncVals.removeAt(keyIx)
        mediaEncKeys.add(0, acc.mediaEnc)
        mediaEncVals.add(0, keyVal)
        val mediaEncAdapter = ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,
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

        ipV6MediaCheck = findViewById(R.id.PreferIPv6Media) as CheckBox
        ipV6MediaCheck.isChecked = acc.preferIPv6Media

        answerMode = acc.answerMode
        val answerModeSpinner = findViewById(R.id.answerModeSpinner) as Spinner
        val answerModeKeys = arrayListOf("manual", "auto")
        val answerModeVals = arrayListOf(getString(R.string.manual), getString(R.string.auto))
        keyIx = answerModeKeys.indexOf(acc.answerMode)
        keyVal = answerModeVals.elementAt(keyIx)
        answerModeKeys.removeAt(keyIx)
        answerModeVals.removeAt(keyIx)
        answerModeKeys.add(0, acc.answerMode)
        answerModeVals.add(0, keyVal)
        val answerModeAdapter = ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,
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

        vmUri = findViewById(R.id.voicemailUri) as EditText
        vmUri.setText(acc.vmUri)

        defaultCheck = findViewById(R.id.Default) as CheckBox
        defaultCheck.isChecked = uaIndex == 0

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)
        return true

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.checkIcon -> {
                val dn = displayName.text.toString().trim()
                if (dn != acc.displayName) {
                    if (checkDisplayName(dn)) {
                        if (account_set_display_name(acc.accp, dn) == 0) {
                            acc.displayName = account_display_name(acc.accp);
                            Log.d("Baresip", "New display name is ${acc.displayName}")
                            save = true
                        } else {
                            Log.e("Baresip", "Setting of display name failed")
                        }
                    } else {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.invalid_display_name), dn))
                        return false
                    }
                }

                val au = authUser.text.toString().trim()
                val ap = authPass.text.toString().trim()
                if (((au != "") && (ap == "")) || ((au == "") && (ap != ""))) {
                    Utils.alertView(this, getString(R.string.notice),
                            getString(R.string.authentication_username_password_mismatch))
                    return false
                }

                if (au != acc.authUser) {
                    if (checkAuthUser(au)) {
                        if (account_set_auth_user(acc.accp, au) == 0) {
                            acc.authUser = account_auth_user(acc.accp);
                            Log.d("Baresip", "New auth user is ${acc.authUser}")
                            save = true
                        } else {
                            Log.e("Baresip", "Setting of auth user failed")
                        }
                    } else {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.invalid_authentication_username), au))
                        return false
                    }
                }

                if (ap != acc.authPass) {
                    if (Utils.checkPrintAscii(ap)) {
                        if (account_set_auth_pass(acc.accp, ap) == 0) {
                            acc.authPass = account_auth_pass(acc.accp)
                            // Log.d("Baresip", "New auth password is ${acc.authPass}")
                            save = true
                        } else {
                            Log.e("Baresip", "Setting of auth pass failed")
                        }
                    } else {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.invalid_authentication_password), ap))
                        return false
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
                            if (account_set_outbound(acc.accp, ob[i], i) == 0) {
                                if (ob[i] != "")
                                    outbound.add(account_outbound(acc.accp, i))
                            } else {
                                Log.e("Baresip", "Setting of outbound proxy ${ob[i]} failed")
                                break
                            }
                        } else {
                            Utils.alertView(this, getString(R.string.notice),
                                    String.format(getString(R.string.invalid_proxy_server_uri), ob[i]))
                            return false
                        }
                    }
                    Log.d("Baresip", "New outbound proxies are ${outbound}")
                    acc.outbound = outbound
                    if (outbound.isEmpty())
                        account_set_sipnat(acc.accp, "")
                    else
                        account_set_sipnat(acc.accp, "outbound")
                    save = true
                }

                var newRegint = -1
                if (regCheck.isChecked) {
                    if (acc.regint != 3600) newRegint = 3600
                } else {
                    if (acc.regint != 0) {
                        Api.ua_unregister(ua.uap)
                        UserAgent.updateStatus(ua, R.drawable.dot_white)
                        newRegint = 0
                    }
                }
                if (newRegint != -1)
                    if (account_set_regint(acc.accp, newRegint) == 0) {
                        acc.regint = account_regint(acc.accp)
                        Log.d("Baresip", "New regint is ${acc.regint}")
                        save = true
                    } else {
                        Log.e("Baresip", "Setting of regint failed")
                    }

                if (mediaNat != acc.mediaNat) {
                    if (account_set_medianat(acc.accp, mediaNat) == 0) {
                        acc.mediaNat = account_medianat(acc.accp)
                        Log.d("Baresip", "New medianat is ${acc.mediaNat}")
                        save = true
                    } else {
                        Log.e("Baresip", "Setting of medianat failed")
                    }
                }

                if (mediaNat != "") {
                    val newStunServer = stunServer.text.toString().trim()
                    if (acc.stunServer != newStunServer) {
                        if (!Utils.checkHostPort(newStunServer)) {
                            Utils.alertView(this, getString(R.string.notice),
                                    String.format(getString(R.string.invalid_stun_server), newStunServer))
                            return false
                        }
                        var host = ""
                        var port = 0
                        if (newStunServer != "") {
                            val hostPort = newStunServer.split(":")
                            host = hostPort[0]
                            if (hostPort.size == 2) port = hostPort[1].toInt()
                        }
                        if ((account_set_stun_host(acc.accp, host) == 0) &&
                                (account_set_stun_port(acc.accp, port) == 0)) {
                            acc.stunServer = account_stun_host(acc.accp)
                            if (port != 0)
                                acc.stunServer += ":" + account_stun_port(acc.accp).toString()
                            Log.d("Baresip", "New StunServer is '${acc.stunServer}'")
                            save = true
                        } else {
                            Log.e("Baresip", "Setting of StunServer failed")
                        }
                    }
                }

                val ac = ArrayList(LinkedHashSet<String>(newCodecs.filter { it != "-" } as ArrayList<String>))
                if (ac != acc.audioCodec) {
                    Log.d("Baresip", "New codecs ${newCodecs.filter { it != "-" }}")
                    val acParam = ";audio_codecs=" + Utils.implode(ac, ",")
                    if (account_set_audio_codecs(acc.accp, acParam) == 0) {
                        var i = 0
                        while (true) {
                            val codec = account_audio_codec(acc.accp, i)
                            if (codec != "") {
                                Log.d("Baresip", "Found audio codec '$codec'")
                                i++
                            } else {
                                break
                            }
                        }
                        acc.audioCodec = ac
                        save = true
                    } else {
                        Log.e("Baresip", "Setting of audio codecs '$acParam' failed")
                    }
                }

                if (mediaEnc != acc.mediaEnc) {
                    if (account_set_mediaenc(acc.accp, mediaEnc) == 0) {
                        acc.mediaEnc = account_mediaenc(acc.accp)
                        Log.d("Baresip", "New mediaenc is ${acc.mediaEnc}")
                        save = true
                    } else {
                        Log.e("Baresip", "Setting of mediaenc $mediaEnc failed")
                    }
                }

                if (ipV6MediaCheck.isChecked != acc.preferIPv6Media) {
                    acc.preferIPv6Media = ipV6MediaCheck.isChecked
                    Log.d("Baresip", "New preferIPv6Media is ${acc.preferIPv6Media}")
                    if (acc.preferIPv6Media)
                        Api.ua_set_media_af(ua.uap, Api.AF_INET6)
                    else
                        Api.ua_set_media_af(ua.uap, Api.AF_UNSPEC)
                    save = true
                }

                if (answerMode != acc.answerMode) {
                    acc.answerMode = answerMode
                    Log.d("Baresip", "New answermode is ${acc.answerMode}")
                    save = true
                }

                var vmUri = voicemailUri.text.toString().trim()
                if (vmUri != acc.vmUri) {
                    if (vmUri != "") {
                        if (!vmUri.startsWith("sip:")) vmUri = "sip:$vmUri"
                        if (!vmUri.contains("@")) vmUri = "$vmUri@${acc.host()}"
                        if (!Utils.checkSipUri(vmUri)) {
                            Utils.alertView(this, getString(R.string.notice),
                                    String.format(getString(R.string.invalid_voicemail_uri), vmUri))
                            return false
                        }
                        account_set_mwi(acc.accp, "yes")
                    } else {
                        account_set_mwi(acc.accp, "no")
                    }
                    acc.vmUri = vmUri
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
                        Log.e("Baresip", "Failed to update UA ${ua.uap} with AoR $aor")
                    //else
                        //Api.ua_debug(ua.uap)
                }

                if (regCheck.isChecked) Api.ua_register(ua.uap)

                BaresipService.activities.removeAt(0)
                val i = Intent()
                setResult(Activity.RESULT_OK, i)
                finish()

            }

            android.R.id.home -> {

                BaresipService.activities.removeAt(0)
                val i = Intent()
                setResult(Activity.RESULT_OK, i)
                finish()

            }

        }

        return true

    }

    override fun onBackPressed() {

        BaresipService.activities.removeAt(0)
        super.onBackPressed()

    }

    fun onClick(v: View) {
        when (v) {
            findViewById(R.id.DisplayNameTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.display_name),
                        getString(R.string.display_name_help))
            }
            findViewById(R.id.AuthUserTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.authentication_username),
                        getString(R.string.authentication_username_help))
            }
            findViewById(R.id.AuthPassTitle) as TextView-> {
                Utils.alertView(this, getString(R.string.authentication_password),
                        getString(R.string.authentication_password_help))
            }
            findViewById(R.id.OutboundProxyTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.outbound_proxies),
                        getString(R.string.outbound_proxies_help))
            }
            findViewById(R.id.RegTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.register),
                        getString(R.string.register_help))
            }
            findViewById(R.id.AudioCodecsTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.audio_codecs),
                        getString(R.string.audio_codecs_help))
            }
            findViewById(R.id.MediaNatTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.media_nat),
                        getString(R.string.media_nat_help))
            }
            findViewById(R.id.StunServerTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.stun_server),
                        getString(R.string.stun_server_help))
            }
            findViewById(R.id.MediaEncTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.media_encryption),
                        getString(R.string.media_encryption_help))
            }
            findViewById(R.id.PreferIPv6MediaTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.prefer_ipv6_media),
                        getString(R.string.prefer_ipv6_media_help))
            }
            findViewById(R.id.AnswerModeTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.answer_mode),
                        getString(R.string.answer_mode_help))
            }
            findViewById(R.id.VoicemailUriTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.voicemail_uri),
                        getString(R.string.voicemain_uri_help))
            }
            findViewById(R.id.DefaultTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.default_account),
                        getString(R.string.default_account_help))
            }
        }
    }

    private fun checkOutboundUri(uri: String): Boolean {
        if (!uri.startsWith("sip:")) return false
        return Utils.checkHostPortParams(uri.substring(4))
    }

    private fun checkDisplayName(dn: String): Boolean {
        if (dn == "") return true
        val dnRegex = Regex("^([* .!%_`'~]|[+]|[-a-zA-Z0-9]){1,63}\$")
        return dnRegex.matches(dn)
    }

    private fun checkAuthUser(au: String): Boolean {
        if (au == "") return true
        val ud = au.split("@")
        val userIDRegex = Regex("^([* .!%_`'~]|[+]|[-a-zA-Z0-9]){1,63}\$")
        val telnoRegex = Regex("^[+]?[0-9]{1,16}\$")
        if (ud.size == 1) {
            return userIDRegex.matches(ud[0]) || telnoRegex.matches(ud[0])
        } else {
            return (userIDRegex.matches(ud[0]) || telnoRegex.matches(ud[0])) &&
                    Utils.checkDomain(ud[1])
        }
    }

}


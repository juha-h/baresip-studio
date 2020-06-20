package com.tutpro.baresip.plus

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
    internal lateinit var stunUser: EditText
    internal lateinit var stunPass: EditText
    internal lateinit var regCheck: CheckBox
    internal lateinit var mediaEnc: String
    internal lateinit var ipV6MediaCheck: CheckBox
    internal lateinit var answerMode: String
    internal lateinit var vmUri: EditText
    internal lateinit var defaultCheck: CheckBox

    private var save = false
    private var uaIndex= -1

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        aor = intent.getStringExtra("aor")!!
        ua = UserAgent.ofAor(aor)!!
        acc = ua.account
        uaIndex = UserAgent.findAorIndex(aor)!!

        Utils.addActivity("account,$aor")

        setTitle(aor.split(":")[1])

        uri = findViewById(R.id.Uri) as TextView
        uri.setText(acc.luri)

        displayName = findViewById(R.id.DisplayName) as EditText
        displayName.setText(acc.displayName)

        authUser = findViewById(R.id.AuthUser) as EditText
        authUser.setText(acc.authUser)

        authPass = findViewById(R.id.AuthPass) as EditText
        if (MainActivity.aorPasswords.containsKey(aor))
            authPass.setText("")
        else
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

        mediaNat = acc.mediaNat
        val mediaNatSpinner = findViewById(R.id.mediaNatSpinner) as Spinner
        val mediaNatKeys = arrayListOf("stun", "turn", "ice", "")
        val mediaNatVals = arrayListOf("STUN", "TURN", "ICE", "-")
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
                stunUser.isEnabled = mediaNat != ""
                stunPass.isEnabled = mediaNat != ""
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        stunServer = findViewById(R.id.StunServer) as EditText
        stunServer.setText(acc.stunServer)
        stunServer.isEnabled = mediaNat != ""

        stunUser = findViewById(R.id.StunUser) as EditText
        stunUser.setText(acc.stunUser)
        stunUser.isEnabled = mediaNat != ""

        stunPass = findViewById(R.id.StunPass) as EditText
        stunPass.setText(acc.stunPass)
        stunPass.isEnabled = mediaNat != ""

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

        if (BaresipService.activities.indexOf("account,$aor") == -1)
            return true

        when (item.itemId) {

            R.id.checkIcon -> {
                val dn = displayName.text.toString().trim()
                if (dn != acc.displayName) {
                    if (Account.checkDisplayName(dn)) {
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

                if (au != acc.authUser) {
                    if (Account.checkAuthUser(au)) {
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
                    var newStunServer = stunServer.text.toString().trim()
                    if (newStunServer == "") {
                        if (mediaNat.startsWith("turn")) {
                            Utils.alertView(this, getString(R.string.notice),
                                    String.format(getString(R.string.invalid_stun_server), newStunServer))
                            return false
                        } else
                            newStunServer =  resources.getString(R.string.stun_server_default)
                    }
                    if (acc.stunServer != newStunServer) {
                        if (!Utils.checkStunUri(newStunServer)) {
                            Utils.alertView(this, getString(R.string.notice),
                                    String.format(getString(R.string.invalid_stun_server), newStunServer))
                            return false
                        }
                        if (account_set_stun_uri(acc.accp, newStunServer) == 0) {
                            acc.stunServer = account_stun_uri(acc.accp)
                            Log.d("Baresip", "New STUN/TURN server URI is '${acc.stunServer}'")
                            save = true
                        } else {
                            Log.e("Baresip", "Setting of STUN/TURN URI server failed")
                        }
                    }
                    val su = stunUser.text.toString().trim()
                    if (Account.checkAuthUser(su)) {
                        if (account_set_stun_user(acc.accp, su) == 0) {
                            acc.stunUser = account_stun_user(acc.accp);
                            Log.d("Baresip", "New STUN/TURN user is ${acc.stunUser}")
                            save = true
                        } else {
                            Log.e("Baresip", "Setting of STUN/TURN user failed")
                        }
                    } else {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.invalid_stun_username), su))
                        return false
                    }
                    val sp = stunPass.text.toString().trim()
                    if (sp.isEmpty() || Account.checkAuthPass(sp)) {
                        if (account_set_stun_pass(acc.accp, sp) == 0) {
                            acc.stunPass = account_stun_pass(acc.accp);
                            Log.d("Baresip", "New stun pass is ${acc.stunPass}")
                            save = true
                        } else {
                            Log.e("Baresip", "Setting of stun pass failed")
                        }
                    } else {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.invalid_stun_password), sp))
                        return false
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

                var tVmUri = voicemailUri.text.toString().trim()
                if (tVmUri != acc.vmUri) {
                    if (tVmUri != "") {
                        if (!tVmUri.startsWith("sip:")) tVmUri = "sip:$tVmUri"
                        if (!tVmUri.contains("@")) tVmUri = "$tVmUri@${acc.host()}"
                        if (!Utils.checkSipUri(tVmUri)) {
                            Utils.alertView(this, getString(R.string.notice),
                                    String.format(getString(R.string.invalid_voicemail_uri), tVmUri))
                            return false
                        }
                        account_set_mwi(acc.accp, "yes")
                    } else {
                        account_set_mwi(acc.accp, "no")
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
                        Log.e("Baresip", "Failed to update UA ${ua.uap} with AoR $aor")
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
        super.onBackPressed()
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
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
                val i = Intent(this, CodecsActivity::class.java)
                val b = Bundle()
                b.putString("aor", aor)
                b.putString("media", "audio")
                i.putExtras(b)
                startActivity(i)
            }
            findViewById(R.id.VideoCodecsTitle) as TextView -> {
                val i = Intent(this, CodecsActivity::class.java)
                val b = Bundle()
                b.putString("aor", aor)
                b.putString("media", "video")
                i.putExtras(b)
                startActivity(i)
            }
            findViewById(R.id.VideoCodecsTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.video_codecs),
                        getString(R.string.video_codecs_help))
            }
            findViewById(R.id.MediaNatTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.media_nat),
                        getString(R.string.media_nat_help))
            }
            findViewById(R.id.StunServerTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.stun_server),
                        getString(R.string.stun_server_help))
            }
            findViewById(R.id.StunUserTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.stun_username),
                        getString(R.string.stun_username_help))
            }
            findViewById(R.id.StunPassTitle) as TextView -> {
                Utils.alertView(this, getString(R.string.stun_password),
                        getString(R.string.stun_password_help))
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

    private fun returnResult(code: Int) {
        val i = Intent()
        if (code == Activity.RESULT_OK)
            i.putExtra("aor", aor)
        setResult(code, i)
        finish()
    }

    private fun setAuthPass(acc: Account, ap: String) {
        if (account_set_auth_pass(acc.accp, ap) == 0) {
            acc.authPass = account_auth_pass(acc.accp)
            MainActivity.aorPasswords.remove(acc.aor)
            save = true
        } else {
            Log.e("Baresip", "Setting of auth pass failed")
        }
    }

    private fun checkOutboundUri(uri: String): Boolean {
        if (!uri.startsWith("sip:")) return false
        return Utils.checkHostPortParams(uri.substring(4))
    }

}


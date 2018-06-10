package com.tutpro.baresip

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout.LayoutParams
import android.widget.*

class AccountActivity : AppCompatActivity() {

    internal lateinit var acc: Account
    internal lateinit var displayName: EditText
    internal lateinit var aor: String
    internal lateinit var authUser: EditText
    internal lateinit var authPass: EditText
    internal lateinit var outbound1: EditText
    internal lateinit var outbound2: EditText
    internal lateinit var regint: EditText
    internal lateinit var mediaEnc: String

    private var newCodecs = ArrayList<String>()
    private var save = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        acc = Account.find(MainActivity.uas, intent.extras.getString("accp"))!!
        aor = acc.aor
        setTitle(aor.replace("sip:", ""))

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

        regint = findViewById(R.id.RegInt) as EditText
        regint.setText(acc.regint.toString())

        val audioCodecs = ArrayList(Utils.audio_codecs().split(","))
        newCodecs.addAll(audioCodecs)
        while (newCodecs.size < audioCodecs.size) newCodecs.add("")

        val layout = findViewById(R.id.CodecSpinners) as LinearLayout
        val spinnerList = Array(audioCodecs.size, {_ -> ArrayList<String>()})
        for (i in audioCodecs.indices) {
            val spinner = Spinner(applicationContext)
            spinner.id = i + 100
            spinner.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            spinner.setPopupBackgroundResource(R.drawable.spinner_background)
            layout.addView(spinner)

            if (acc.audioCodec.size > i) {
                val codec = acc.audioCodec[i]
                spinnerList[i].add(codec)
                for (c in audioCodecs) if (c != codec) spinnerList[i].add(c)
                spinnerList[i].add("")
            } else {
                spinnerList[i] = audioCodecs
                spinnerList[i].add(0, "")
            }
            val codecSpinner = findViewById(spinner.id) as Spinner
            val adapter = ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,
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

        mediaEnc = acc.mediaenc
        val mediaEncSpinner = findViewById(R.id.mediaEncSpinner) as Spinner
        val mediaEncKeys = arrayListOf("zrtp", "dtls_srtp", "srtp", "srtp-mand", "")
        val mediaEncVals = arrayListOf("ZRTP", "DTLS SRTP", "SRTP", "Madatory SRTP", "")
        val keyIx = mediaEncKeys.indexOf(acc.mediaenc)
        val keyVal = mediaEncVals.elementAt(keyIx)
        mediaEncKeys.removeAt(keyIx)
        mediaEncVals.removeAt(keyIx)
        mediaEncKeys.add(0, acc.mediaenc)
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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        val intent = Intent(this, MainActivity::class.java)

        if (item.itemId == R.id.checkIcon) {

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
                    Utils.alertView(this, "Notice", "Invalid Display Name: $dn")
                    return false
                }
            }

            val au = authUser.text.toString().trim()
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
                    Utils.alertView(this, "Notice",
                            "Invalid Authentication UserName: $au")
                    return false
                }
            }

            val ap = authPass.text.toString().trim()
            if (ap != acc.authPass) {
                if (Utils.checkPrintASCII(ap)) {
                    if (account_set_auth_pass(acc.accp, ap) == 0) {
                        acc.authPass = account_auth_pass(acc.accp)
                        // Log.d("Baresip", "New auth password is ${acc.authPass}")
                        save = true
                    } else {
                        Log.e("Baresip", "Setting of auth pass failed")
                    }
                } else {
                    Utils.alertView(this, "Notice",
                            "Invalid Authentication Password: $ap")
                    return false
                }
            }

            val ob = ArrayList<String>()
            if (outbound1.text.toString().trim() != "")
                ob.add(outbound1.text.toString().trim().replace(" ", ""))
            if (outbound2.text.toString().trim() != "")
                ob.add(outbound2.text.toString().trim().replace(" ", ""))
            if (ob != acc.outbound) {
                val outbound = ArrayList<String>()
                for (i in ob.indices) {
                    if ((ob[i] == "") || Utils.uri_decode(ob[i])) {
                        if (account_set_outbound(acc.accp, ob[i], i) == 0) {
                            if (ob[i] != "")
                                outbound.add(account_outbound(acc.accp, i))
                        } else {
                            Log.e("Baresip", "Setting of outbound proxy ${ob[i]} failed")
                            break
                        }
                    } else {
                        Utils.alertView(this, "Notice",
                                "Invalid Outbound Proxy: ${ob[i]}")
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

            val ri = regint.text.toString().trim()
            if (Utils.checkUint(ri)) {
                if (ri.toInt() != acc.regint) {
                    if (account_set_regint(acc.accp, ri.toInt()) == 0) {
                        acc.regint = account_regint(acc.accp)
                        Log.d("Baresip", "New regint is ${acc.regint}")
                        save = true
                    } else {
                        Log.e("Baresip", "Setting of regint $ri failed")
                    }
                }
            } else {
                Utils.alertView(this, "Notice",
                        "Invalid Registration Interval: $ri")
                return false
            }

            val ac = ArrayList(LinkedHashSet<String>(newCodecs.filter { it != "" } as ArrayList<String>))
            if (ac != acc.audioCodec) {
                Log.d("Baresip", "New codecs ${newCodecs.filter { it != "" }}")
                val acParam = ";audio_codecs=" + Utils.implode(ac, ",")
                if (account_set_audio_codecs(acc.accp, acParam) == 0) {
                    var i = 0
                    while (true) {
                        val codec = account_audio_codec(acc.accp, i)
                        if (codec != "") {
                            Log.d("Baresip", "Found audio codec $codec")
                            i++
                        } else {
                            break
                        }
                    }
                    acc.audioCodec = ac
                    save = true
                } else {
                    Log.e("Baresip", "Setting of audio codecs $acParam failed")
                }
            }

            if (mediaEnc != acc.mediaenc) {
                if (account_set_mediaenc(acc.accp, mediaEnc) == 0) {
                    acc.mediaenc = account_mediaenc(acc.accp)
                    Log.d("Baresip", "New mediaenc is ${acc.mediaenc}")
                    save = true
                } else {
                    Log.e("Baresip", "Setting of mediaenc $mediaEnc failed")
                }
            }

            if (save) {
                AccountsActivity.saveAccounts()
                val uaIndex = UserAgent.findAorIndex(MainActivity.uas, aor)
                if (uaIndex != null) {
                    if (UserAgent.ua_update_account(MainActivity.uas[uaIndex].uap) != 0)
                        Log.e("Baresip", "Failed to update UA with AoR $aor")
                    else if (acc.regint > 0)
                        UserAgent.ua_register(MainActivity.uas[uaIndex].uap)
                } else {
                    Log.e("Baresip", "Did not find UA matching AoR $aor")
                }
            }

            setResult(RESULT_OK, intent)
            finish()
            return true

        } else if (item.itemId == android.R.id.home) {

            Log.d("Baresip", "Back array was pressed at Account")
            setResult(RESULT_CANCELED, intent)
            finish()
            return true

        } else return super.onOptionsItemSelected(item)
    }

    private fun checkDisplayName(dn: String): Boolean {
        if (dn == "") return true
        val dnRegex = Regex("^[a-zA-Z]([ ._-]|[a-zA-Z0-9]){1,63}\$")
        return dnRegex.matches(dn)
    }

    private fun checkAuthUser(au: String): Boolean {
        if (au == "") return true
        val ud = au.split("@")
        val userIDRegex = Regex("^[a-zA-Z]([._-]|[a-zA-Z0-9]){1,63}\$")
        val telnoRegex = Regex("^[+]?[0-9]{1,16}\$")
        if (ud.size == 1) {
            return userIDRegex.matches(ud[0]) || telnoRegex.matches(ud[0])
        } else {
            return (userIDRegex.matches(ud[0]) || telnoRegex.matches(ud[0])) &&
                    Utils.checkDomain(ud[1])
        }
    }

}


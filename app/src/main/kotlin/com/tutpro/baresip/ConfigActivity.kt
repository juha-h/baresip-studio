package com.tutpro.baresip

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tutpro.baresip.Utils.copyInputStreamToFile
import com.tutpro.baresip.Utils.showSnackBar
import com.tutpro.baresip.databinding.ActivityConfigBinding
import java.io.File
import java.io.FileInputStream
import java.util.*

class ConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding
    private lateinit var layout: ScrollView
    private lateinit var baresipService: Intent
    private lateinit var autoStart: CheckBox
    private lateinit var batteryOptimizations: CheckBox
    private lateinit var listenAddr: EditText
    private lateinit var netAfSpinner: Spinner
    private lateinit var netAf: String
    private lateinit var netAfKeys: ArrayList<String>
    private lateinit var dnsServers: EditText
    private lateinit var certificateFile: CheckBox
    private lateinit var verifyServer: CheckBox
    private lateinit var caFile: CheckBox
    private lateinit var darkTheme: CheckBox
    private lateinit var contactsSpinner: Spinner
    private lateinit var contactsMode: String
    private lateinit var contactsModeKeys: ArrayList<String>
    private lateinit var debug: CheckBox
    private lateinit var sipTrace: CheckBox
    private lateinit var reset: CheckBox
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    private var oldAutoStart = ""
    private var oldListenAddr = ""
    private var oldDnsServers = ""
    private var oldCertificateFile = false
    private var oldVerifyServer = ""
    private var oldCAFile = false
    private var oldLogLevel = ""
    private var oldDisplayTheme = -1
    private var oldContactsMode = ""
    private var oldNetAf = ""
    private var save = false
    private var restart = false
    private var menu: Menu? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val contactsPermissions = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        layout = binding.ConfigView

        Utils.addActivity("config")

        baresipService = Intent(this@ConfigActivity, BaresipService::class.java)

        autoStart = binding.AutoStart
        val asCv = Config.variable("auto_start")
        oldAutoStart = if (asCv.size == 0) "no" else asCv[0]
        autoStart.isChecked = oldAutoStart == "yes"

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val androidSettingsRequest = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
        ) {
            batteryOptimizations.isChecked = pm.isIgnoringBatteryOptimizations(packageName) == false
        }
        batteryOptimizations = binding.BatteryOptimizations
        batteryOptimizations.isChecked = pm.isIgnoringBatteryOptimizations(packageName) == false
        batteryOptimizations.setOnCheckedChangeListener { _, _ ->
            try {
                androidSettingsRequest.launch(Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"))
            } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "ActivityNotFound exception: $e")
            }
        }

        listenAddr = binding.ListenAddress
        val laCv = Config.variable("sip_listen")
        oldListenAddr = if (laCv.size == 0) "" else laCv[0]
        listenAddr.setText(oldListenAddr)

        netAfSpinner = binding.NetAfSpinner
        netAfKeys = arrayListOf("", "ipv4", "ipv6")
        val netAfVals = arrayListOf("-", "IPv4", "IPv6")
        val netAfCv = Config.variable("net_af")
        oldNetAf = if (netAfCv.size == 0) "" else netAfCv[0].lowercase()
        netAf = oldNetAf
        val netAfIndex = netAfKeys.indexOf(oldNetAf)
        val netAfValue = netAfVals.elementAt(netAfIndex)
        netAfKeys.removeAt(netAfIndex)
        netAfVals.removeAt(netAfIndex)
        netAfKeys.add(0, oldNetAf)
        netAfVals.add(0, netAfValue)
        val netAfAdapter = ArrayAdapter(this,android.R.layout.simple_spinner_item, netAfVals)
        netAfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        netAfSpinner.adapter = netAfAdapter
        netAfSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                netAf = netAfKeys[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        dnsServers = binding.DnsServers
        val ddCv = Config.variable("dyn_dns")
        if (ddCv[0] == "yes") {
            oldDnsServers = ""
        } else {
            val dsCv = Config.variable("dns_server")
            var dsTv = ""
            for (ds in dsCv) dsTv += ", $ds"
            oldDnsServers = dsTv.trimStart(',').trimStart(' ')
        }
        dnsServers.setText(oldDnsServers)

        certificateFile = binding.CertificateFile
        oldCertificateFile = Config.variable("sip_certificate").isNotEmpty()
        certificateFile.isChecked = oldCertificateFile

        val certificateRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    it.data?.data?.also { uri ->
                        try {
                            val inputStream =
                                applicationContext.contentResolver.openInputStream(uri)
                                        as FileInputStream
                            File(BaresipService.filesPath + "/cert.pem")
                                .copyInputStreamToFile(inputStream)
                            inputStream.close()
                            Config.removeVariable("sip_certificate")
                            Config.addLine("sip_certificate ${BaresipService.filesPath}/cert.pem")
                            save = true
                            restart = true
                        } catch (e: Error) {
                            Utils.alertView(
                                this, getString(R.string.error),
                                getString(R.string.read_cert_error)
                            )
                            certificateFile.isChecked = false
                        }
                    }
                } else {
                    certificateFile.isChecked = false
                }
            }

        certificateFile.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT < 29) {
                    certificateFile.isChecked = false
                    when {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            Log.d(TAG, "Read External Storage permission granted")
                            val downloadsPath = Utils.downloadsPath("cert.pem")
                            val content = Utils.getFileContents(downloadsPath)
                            if (content == null) {
                                Utils.alertView(
                                    this, getString(R.string.error),
                                    getString(R.string.read_cert_error)
                                )
                                return@setOnCheckedChangeListener
                            }
                            val filesPath = BaresipService.filesPath + "/cert.pem"
                            Utils.putFileContents(filesPath, content)
                            Config.removeVariable("sip_certificate")
                            Config.addLine("sip_certificate $filesPath")
                            certificateFile.isChecked = true
                            save = true
                            restart = true
                        }
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) -> {
                            layout.showSnackBar(
                                binding.root,
                                getString(R.string.no_restore),
                                Snackbar.LENGTH_INDEFINITE,
                                getString(R.string.ok)
                            ) {
                                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }
                        else -> {
                            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                } else {
                    Utils.selectInputFile(certificateRequest)
                }
            } else {
                Config.removeVariable("sip_certificate")
                save = true
                restart = true
            }
        }

        verifyServer = binding.VerifyServer
        val vsCv = Config.variable("sip_verify_server")
        oldVerifyServer = if (vsCv.size == 0) "no" else vsCv[0]
        verifyServer.isChecked = oldVerifyServer == "yes"

        caFile = binding.CAFile
        oldCAFile = Config.variable("sip_cafile").isNotEmpty()
        caFile.isChecked = oldCAFile

        val certificatesRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK)
                    it.data?.data?.also { uri ->
                        try {
                            val inputStream =
                                applicationContext.contentResolver.openInputStream(uri)
                                        as FileInputStream
                            File(BaresipService.filesPath + "/ca_certs.crt")
                                .copyInputStreamToFile(inputStream)
                            inputStream.close()
                            Config.removeVariable("sip_cafile")
                            Config.addLine("sip_cafile ${BaresipService.filesPath}/ca_certs.crt")
                            save = true
                            restart = true
                        } catch (e: Error) {
                            Utils.alertView(
                                this, getString(R.string.error),
                                getString(R.string.read_ca_certs_error)
                            )
                            caFile.isChecked = false
                        }
                    }
                else
                    caFile.isChecked = false
            }

        caFile.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT < 29) {
                    caFile.isChecked = false
                    when {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            Log.d(TAG, "Read External Storage permission granted")
                            val downloadsPath = Utils.downloadsPath("ca_certs.crt")
                            val content = Utils.getFileContents(downloadsPath)
                            if (content == null) {
                                Utils.alertView(
                                    this, getString(R.string.error),
                                    getString(R.string.read_ca_certs_error)
                                )
                                return@setOnCheckedChangeListener
                            }
                            val filesPath = BaresipService.filesPath + "/ca_certs.crt"
                            Utils.putFileContents(filesPath, content)
                            Config.removeVariable("sip_cafile")
                            Config.addLine("sip_cafile $filesPath")
                            caFile.isChecked = true
                            save = true
                            restart = true
                        }
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) -> {
                            layout.showSnackBar(
                                binding.root,
                                getString(R.string.no_restore),
                                Snackbar.LENGTH_INDEFINITE,
                                getString(R.string.ok)
                            ) {
                                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }
                        else -> {
                            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                } else {
                    Utils.selectInputFile(certificatesRequest)
                }
            } else {
                Config.removeVariable("sip_cafile")
                save = true
                restart = true
            }
        }

        darkTheme = binding.DarkTheme
        oldDisplayTheme = Preferences(applicationContext).displayTheme
        darkTheme.isChecked = oldDisplayTheme == AppCompatDelegate.MODE_NIGHT_YES

        contactsSpinner = binding.contactsSpinner
        contactsModeKeys = arrayListOf("baresip", "android", "both")
        val contactsModeVals = arrayListOf(getString(R.string.baresip), getString(R.string.android),
                getString(R.string.both))
        val ctCv = Config.variable("contacts_mode")
        oldContactsMode = if (ctCv.size == 0) "baresip" else ctCv[0].lowercase()
        contactsMode = oldContactsMode
        val keyIndex = contactsModeKeys.indexOf(oldContactsMode)
        val keyValue = contactsModeVals.elementAt(keyIndex)
        contactsModeKeys.removeAt(keyIndex)
        contactsModeVals.removeAt(keyIndex)
        contactsModeKeys.add(0, oldContactsMode)
        contactsModeVals.add(0, keyValue)
        val contactsAdapter = ArrayAdapter(this,android.R.layout.simple_spinner_item,
                contactsModeVals)
        contactsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        contactsSpinner.adapter = contactsAdapter
        contactsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                contactsMode = contactsModeKeys[position]
                if (contactsMode != "baresip" && !Utils.checkPermissions(applicationContext, contactsPermissions))
                    with(MaterialAlertDialogBuilder(this@ConfigActivity, R.style.AlertDialogTheme)) {
                        setTitle(getText(R.string.consent_request))
                        setMessage(getText(R.string.contacts_consent))
                        setPositiveButton(getText(R.string.accept)) { dialog, _ ->
                            dialog.dismiss()
                            requestPermissions(contactsPermissions, CONTACTS_PERMISSION_REQUEST_CODE)
                        }
                        setNegativeButton(getText(R.string.deny)) { dialog, _ ->
                            contactsSpinner.setSelection(contactsModeKeys.indexOf(oldContactsMode))
                            dialog.dismiss()
                        }
                        val dialog = this.create()
                        dialog.setCancelable(false)
                        dialog.setCanceledOnTouchOutside(false)
                        dialog.show().apply {
                            findViewById<TextView>(android.R.id.message)
                                ?.movementMethod = LinkMovementMethod.getInstance()
                        }
                    }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        debug = binding.Debug
        val dbCv = Config.variable("log_level")
        oldLogLevel = if (dbCv.size == 0)
            "2"
        else
            dbCv[0]
        debug.isChecked = oldLogLevel == "0"

        sipTrace = binding.SipTrace
        sipTrace.isChecked = BaresipService.sipTrace

        reset = binding.Reset
        reset.isChecked = false

        reset.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                with(MaterialAlertDialogBuilder(this@ConfigActivity, R.style.AlertDialogTheme)) {
                    setTitle(R.string.confirmation)
                    setMessage(getString(R.string.reset_config_alert))
                    setPositiveButton(getText(R.string.reset)) { dialog, _ ->
                        Config.reset(this@ConfigActivity)
                        save = false
                        restart = true
                        done()
                        dialog.dismiss()
                    }
                    setNeutralButton(getText(R.string.cancel)) { dialog, _ ->
                        reset.isChecked = false
                        dialog.dismiss()
                    }
                    show()
                }
            }
        }

        bindTitles()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    }

    override fun onStart() {
        super.onStart()
        requestPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        requestPermissionsLauncher =
                registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    if (it.containsValue(false)) {
                        contactsMode = oldContactsMode
                        contactsSpinner.setSelection(contactsModeKeys.indexOf(oldContactsMode))
                        Snackbar.make(layout, getString(R.string.no_android_contacts), Snackbar.LENGTH_LONG)
                                .setAction(getString(R.string.ok)) {}
                                .show()
                    }
                }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        super.onCreateOptionsMenu(menu)

        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)

        this.menu = menu

        return true

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grandResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grandResults)
        when (requestCode) {
            CONTACTS_PERMISSION_REQUEST_CODE -> {
                if (grandResults.contains(PackageManager.PERMISSION_DENIED)) {
                    when {
                        ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.READ_CONTACTS) -> {
                            layout.showSnackBar(
                                    binding.root,
                                    getString(R.string.no_android_contacts),
                                    Snackbar.LENGTH_INDEFINITE,
                                    getString(R.string.ok)
                            ) {
                                requestPermissionsLauncher.launch(permissions)
                            }
                        }
                        ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.WRITE_CONTACTS) -> {
                            layout.showSnackBar(
                                    binding.root,
                                    getString(R.string.no_android_contacts),
                                    Snackbar.LENGTH_INDEFINITE,
                                    getString(R.string.ok)
                            ) {
                                requestPermissionsLauncher.launch(permissions)
                            }
                        }
                        else -> {
                            requestPermissionsLauncher.launch(permissions)
                        }
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("config") == -1) return true

        when (item.itemId) {

            R.id.checkIcon -> {

                var autoStartString = "no"
                if (autoStart.isChecked) autoStartString = "yes"
                if (oldAutoStart != autoStartString) {
                    Config.replaceVariable("auto_start", autoStartString)
                    save = true
                    restart = false
                }

                val listenAddr = listenAddr.text.toString().trim()
                if (listenAddr != oldListenAddr) {
                    if ((listenAddr != "") && !Utils.checkIpPort(listenAddr)) {
                        Utils.alertView(this, getString(R.string.notice),
                                "${getString(R.string.invalid_listen_address)}: $listenAddr")
                        return false
                    }
                    Config.removeVariable("sip_listen")
                    if (listenAddr != "") Config.addLine("sip_listen $listenAddr")
                    save = true
                    restart = true
                }

                if (oldNetAf != netAf) {
                    if (netAf == "")
                        Config.removeVariable("net_af")
                    else
                        Config.replaceVariable("net_af", netAf)
                    save = true
                    restart = true
                }

                val dnsServers = addMissingPorts(
                    dnsServers.text.toString().trim().lowercase(Locale.ROOT))
                if (dnsServers != oldDnsServers) {
                    if (!checkDnsServers(dnsServers)) {
                        Utils.alertView(this, getString(R.string.notice),
                                "${getString(R.string.invalid_dns_servers)}: $dnsServers")
                        return false
                    }
                    Config.removeVariable("dyn_dns")
                    Config.removeVariable("dns_server")
                    if (dnsServers.isNotEmpty()) {
                        for (server in dnsServers.split(","))
                            Config.addLine("dns_server $server")
                        Config.addLine("dyn_dns no")
                        if (Api.net_use_nameserver(dnsServers) != 0) {
                            Utils.alertView(this, getString(R.string.error),
                                    "${getString(R.string.failed_to_set_dns_servers)}: $dnsServers")
                            return false
                        }
                    } else {
                        Config.addLine("dyn_dns yes")
                        Config.updateDnsServers(BaresipService.dnsServers)
                    }
                    // Api.net_dns_debug()
                    save = true
                }

                if (verifyServer.isChecked && !caFile.isChecked) {
                    Utils.alertView(this, getString(R.string.error),
                            getString(R.string.verify_server_error))
                    verifyServer.isChecked = false
                    return false
                }

                val verifyServerString = if (verifyServer.isChecked) "yes" else "no"
                if (oldVerifyServer != verifyServerString) {
                    Config.replaceVariable("sip_verify_server", verifyServerString)
                    save = true
                    restart = true
                }

                val newDisplayTheme = if (darkTheme.isChecked)
                    AppCompatDelegate.MODE_NIGHT_YES
                else
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                if (oldDisplayTheme != newDisplayTheme)
                    Preferences(applicationContext).displayTheme = newDisplayTheme

                if (oldContactsMode != contactsMode) {
                    Config.replaceVariable("contacts_mode", contactsMode)
                    BaresipService.contactsMode = contactsMode
                    when (contactsMode) {
                        "baresip" -> {
                            BaresipService.androidContacts.clear()
                            Contact.restoreBaresipContacts()
                            baresipService.action = "Stop Content Observer"
                        }
                        "android" -> {
                            BaresipService.baresipContacts.clear()
                            Contact.loadAndroidContacts(this)
                            baresipService.action = "Start Content Observer"
                        }
                        "both" -> {
                            Contact.restoreBaresipContacts()
                            Contact.loadAndroidContacts(this)
                            baresipService.action = "Start Content Observer"
                        }
                    }
                    Contact.contactsUpdate()
                    startService(baresipService)
                    save = true
                }

                var logLevelString = "2"
                if (debug.isChecked) logLevelString = "0"
                if (oldLogLevel != logLevelString) {
                    Config.replaceVariable("log_level", logLevelString)
                    Api.log_level_set(logLevelString.toInt())
                    Log.logLevelSet(logLevelString.toInt())
                    save = true
                }

                BaresipService.sipTrace = sipTrace.isChecked
                Api.uag_enable_sip_trace(sipTrace.isChecked)

                done()

            }

            android.R.id.home -> {
                goBack()
            }
        }

        return true

    }

    private fun done() {

        if (save)
            Config.save()
        BaresipService.activities.remove("config")
        val intent = Intent(this, MainActivity::class.java)
        if (restart)
            intent.putExtra("restart", true)
        setResult(RESULT_OK, intent)
        finish()

    }

    private fun goBack() {

        BaresipService.activities.remove("config")
        setResult(Activity.RESULT_CANCELED, Intent(this, MainActivity::class.java))
        finish()

    }

    private fun bindTitles() {
        binding.AutoStartTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.start_automatically),
                    getString(R.string.start_automatically_help))
        }
        binding.BatteryOptimizationsTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.battery_optimizations),
                getString(R.string.battery_optimizations_help))
        }
        binding.ListenAddressTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.listen_address),
                    getString(R.string.listen_address_help))
        }
        binding.NetAfTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.address_family),
                    getString(R.string.address_family_help))
        }
        binding.DnsServersTitle .setOnClickListener {
            Utils.alertView(this, getString(R.string.dns_servers),
                    getString(R.string.dns_servers_help))
        }
        binding.CertificateFileTitle .setOnClickListener {
            Utils.alertView(this, getString(R.string.tls_certificate_file),
                    getString(R.string.tls_certificate_file_help))
        }
        binding.VerifyServerTitle .setOnClickListener {
            Utils.alertView(this, getString(R.string.verify_server),
                    getString(R.string.verify_server_help))
        }
        binding.CAFileTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.tls_ca_file),
                    getString(R.string.tls_ca_file_help))
        }
        binding.AudioSettingsTitle.setOnClickListener {
            startActivity(Intent(this, AudioActivity::class.java))
        }
        binding.DarkThemeTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.dark_theme),
                    getString(R.string.dark_theme_help))
        }
        binding.ContactsTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.contacts),
                    getString(R.string.contacts_help))
        }
        binding.DebugTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.debug), getString(R.string.debug_help))
        }
        binding.SipTraceTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.sip_trace),
                    getString(R.string.sip_trace_help))
        }
        binding.ResetTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.reset_config),
                    getString(R.string.reset_config_help))
        }
    }

    private fun checkDnsServers(dnsServers: String): Boolean {
        if (dnsServers.isEmpty()) return true
        for (server in dnsServers.split(","))
            if (!Utils.checkIpPort(server.trim())) return false
        return true
    }

    private fun addMissingPorts(addressList: String): String {
        if (addressList == "") return ""
        var result = ""
        for (addr in addressList.split(","))
            result = if (Utils.checkIpPort(addr)) {
                "$result,$addr"
            } else {
                if (Utils.checkIpV4(addr))
                    "$result,$addr:53"
                else
                    "$result,[$addr]:53"
            }
        return result.substring(1)
    }

}


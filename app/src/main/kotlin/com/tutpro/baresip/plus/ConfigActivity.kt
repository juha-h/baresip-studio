package com.tutpro.baresip.plus

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
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
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tutpro.baresip.plus.Utils.copyInputStreamToFile
import com.tutpro.baresip.plus.Utils.showSnackBar
import com.tutpro.baresip.plus.databinding.ActivityConfigBinding
import java.io.File
import java.io.FileInputStream
import java.util.*

class ConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding
    private lateinit var layout: ScrollView
    private lateinit var baresipService: Intent
    private lateinit var autoStart: CheckBox
    private lateinit var listenAddr: EditText
    private lateinit var netAfSpinner: Spinner
    private lateinit var netAf: String
    private lateinit var netAfKeys: ArrayList<String>
    private lateinit var dnsServers: EditText
    private lateinit var certificateFile: CheckBox
    private lateinit var verifyServer: CheckBox
    private lateinit var caFile: CheckBox
    private lateinit var userAgent: EditText
    private lateinit var darkTheme: CheckBox
    private lateinit var videoFps: EditText
    private lateinit var contactsSpinner: Spinner
    private lateinit var contactsMode: String
    private lateinit var contactsModeKeys: ArrayList<String>
    private lateinit var batteryOptimizations: CheckBox
    private lateinit var defaultDialer: CheckBox
    private lateinit var debug: CheckBox
    private lateinit var sipTrace: CheckBox
    private lateinit var reset: CheckBox
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    private var oldAutoStart = ""
    private var oldListenAddr = ""
    private var oldDnsServers = ""
    private var oldCertificateFile = false
    private var oldVerifyServer = false
    private var oldUserAgent = ""
    private var oldLogLevel = ""
    private var oldDisplayTheme = -1
    private var oldContactsMode = ""
    private var oldNetAf = ""
    private var videoSize = ""
    private var oldVideoSize = Config.variable("video_size")
    private var oldVideoFps = Config.variable("video_fps")
    private var save = false
    private var restart = false
    private var audioRestart = false
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

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v: View, insets: WindowInsetsCompat ->
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            if (Build.VERSION.SDK_INT >= 35)
                binding.ConfigView.updatePadding(top = 172)
            WindowInsetsCompat.CONSUMED
        }

        if (!Utils.isDarkTheme(this))
            WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        Utils.addActivity("config")

        baresipService = Intent(this@ConfigActivity, BaresipService::class.java)

        autoStart = binding.AutoStart
        oldAutoStart = Config.variable("auto_start")
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

        if (Build.VERSION.SDK_INT >= 29) {
            val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
            defaultDialer = binding.DefaultPhoneApp
            defaultDialer.isChecked = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            val dialerRoleRequest = registerForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
            ) {
                Log.d(TAG, "dialerRoleRequest succeeded: ${it.resultCode == Activity.RESULT_OK}")
                defaultDialer.isChecked = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            }
            defaultDialer.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER))
                        Utils.alertView(this, getString(R.string.notice),
                                getString(R.string.dialer_role_not_available))
                    else
                        if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER))
                            dialerRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                } else {
                    try {
                        dialerRoleRequest.launch(Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"))
                    } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "ActivityNotFound exception: $e")

                    }
                }
            }
        } else {
            binding.PhoneApp.visibility = View.GONE
        }

        listenAddr = binding.ListenAddress
        oldListenAddr = Config.variable("sip_listen")
        listenAddr.setText(oldListenAddr)

        netAfSpinner = binding.NetAfSpinner
        netAfKeys = arrayListOf("", "ipv4", "ipv6")
        val netAfVals = arrayListOf("-", "IPv4", "IPv6")
        oldNetAf = Config.variable("net_af").lowercase()
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
        val dynamicDns = Config.variable("dyn_dns")
        if (dynamicDns == "yes") {
            oldDnsServers = ""
        } else {
            val servers = Config.variables("dns_server")
            var serverList = ""
            for (server in servers)
                serverList += ", $server"
            oldDnsServers = serverList.trimStart(',').trimStart(' ')
        }
        dnsServers.setText(oldDnsServers)

        certificateFile = binding.CertificateFile
        oldCertificateFile = Config.variable("sip_certificate") != ""
        certificateFile.isChecked = oldCertificateFile

        val certificateRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    it.data?.data?.also { uri ->
                        try {
                            val inputStream =
                                applicationContext.contentResolver.openInputStream(uri)
                                        as FileInputStream
                            val certPath = BaresipService.filesPath + "/cert.pem"
                            File(certPath).copyInputStreamToFile(inputStream)
                            inputStream.close()
                            Config.replaceVariable("sip_certificate", certPath)
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
                            val certPath = BaresipService.filesPath + "/cert.pem"
                            Utils.putFileContents(certPath, content)
                            Config.replaceVariable("sip_certificate", certPath)
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
        oldVerifyServer = Config.variable("sip_verify_server") == "yes"
        verifyServer.isChecked = oldVerifyServer

        caFile = binding.CAFile
        val caCertsFile = File(BaresipService.filesPath + "/ca_certs.crt")
        caFile.isChecked = caCertsFile.exists()

        val certificatesRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK)
                    it.data?.data?.also { uri ->
                        try {
                            val inputStream =
                                applicationContext.contentResolver.openInputStream(uri)
                                        as FileInputStream
                            caCertsFile.copyInputStreamToFile(inputStream)
                            inputStream.close()
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
                if (!caFile.isChecked && caCertsFile.exists())
                    caCertsFile.delete()
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
                            caCertsFile.writeBytes(content)
                            caFile.isChecked = true
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
                if (caCertsFile.exists())
                    caCertsFile.delete()
                restart = true
            }
        }

        videoFps = binding.VideoFps
        videoFps.setText(oldVideoFps)

        val videoSizeSpinner = findViewById<Spinner>(R.id.VideoSizeSpinner)
        val sizes = mutableListOf<String>()
        sizes.addAll(Config.videoSizes)
        sizes.removeIf{it == oldVideoSize}
        sizes.add(0, oldVideoSize)
        val videoSizeAdapter = ArrayAdapter(this,android.R.layout.simple_spinner_item,
                sizes)
        videoSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        videoSizeSpinner.adapter = videoSizeAdapter
        videoSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                videoSize = parent.selectedItem.toString()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                videoSize = oldVideoSize
            }
        }

        userAgent = binding.UserAgent
        oldUserAgent = Config.variable("user_agent")
        userAgent.setText(oldUserAgent)

        darkTheme = binding.DarkTheme
        oldDisplayTheme = Preferences(applicationContext).displayTheme
        darkTheme.isChecked = oldDisplayTheme == AppCompatDelegate.MODE_NIGHT_YES

        contactsSpinner = binding.contactsSpinner
        contactsModeKeys = arrayListOf("baresip", "android", "both")
        val contactsModeVals = arrayListOf(getString(R.string.baresip), getString(R.string.android),
                getString(R.string.both))
        oldContactsMode = Config.variable("contacts_mode").lowercase()
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
        oldLogLevel = Config.variable("log_level")
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
                        Config.reset()
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
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CONTACTS_PERMISSION_REQUEST_CODE -> {
                if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
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

                val autoStartString = if (autoStart.isChecked) "yes" else "no"
                if (oldAutoStart != autoStartString) {
                    Config.replaceVariable("auto_start", autoStartString)
                    save = true
                }

                val listenAddr = listenAddr.text.toString().trim()
                if (listenAddr != oldListenAddr) {
                    if ((listenAddr != "") && !Utils.checkIpPort(listenAddr)) {
                        Utils.alertView(this, getString(R.string.notice),
                                "${getString(R.string.invalid_listen_address)}: $listenAddr")
                        return false
                    }
                    if (listenAddr != "")
                        Config.replaceVariable("sip_listen", listenAddr)
                    else
                         Config.removeVariable("sip_listen")
                    save = true
                    restart = true
                }

                if (oldNetAf != netAf) {
                    Config.replaceVariable("net_af", netAf)
                    save = true
                    restart = true
                }

                var dnsServers = dnsServers.text.toString().lowercase(Locale.ROOT)
                    .replace(" ", "")
                dnsServers = addMissingPorts(dnsServers)
                if (dnsServers != oldDnsServers.replace(" ", "")) {
                    if (!checkDnsServers(dnsServers)) {
                        Utils.alertView(this, getString(R.string.notice),
                                "${getString(R.string.invalid_dns_servers)}: $dnsServers")
                        return false
                    }
                    Config.removeVariable("dns_server")
                    if (dnsServers.isNotEmpty()) {
                        for (server in dnsServers.split(","))
                            Config.addVariable("dns_server", server)
                        Config.replaceVariable("dyn_dns", "no")
                        if (Api.net_use_nameserver(dnsServers) != 0) {
                            Utils.alertView(this, getString(R.string.error),
                                    "${getString(R.string.failed_to_set_dns_servers)}: $dnsServers")
                            return false
                        }
                    } else {
                        Config.replaceVariable("dyn_dns", "yes")
                        Config.updateDnsServers(BaresipService.dnsServers)
                    }
                    Api.net_debug()
                    save = true
                }

                if (oldVerifyServer != verifyServer.isChecked) {
                    Config.replaceVariable("sip_verify_server",
                            if (verifyServer.isChecked) "yes" else "no")
                    Api.config_verify_server_set(verifyServer.isChecked)
                    save = true
                }

                if (oldVideoSize != videoSize) {
                    Config.replaceVariable("video_size", videoSize)
                    Api.config_video_frame_size_set(videoSize.substringBefore("x").toInt(),
                        videoSize.substringAfter("x").toInt())
                    save = true
                }

                val newFps = videoFps.text.toString().trim().toInt()
                if (oldVideoFps.toInt() != newFps) {
                    if (newFps < 10 || newFps > 30) {
                        Utils.alertView(
                            this, getString(R.string.notice),
                            String.format(getString(R.string.invalid_fps), newFps)
                        )
                        return false
                    }
                    Config.replaceVariable("video_fps", newFps.toString())
                    Api.config_video_fps_set(newFps)
                    save = true
                }

                val userAgent = userAgent.text.toString().trim()
                if (userAgent != oldUserAgent) {
                    if ((userAgent != "") && !Utils.checkServerVal(userAgent)) {
                        Utils.alertView(this, getString(R.string.notice),
                            "${getString(R.string.invalid_user_agent)}: $userAgent")
                        return false
                    }
                    if (userAgent != "")
                        Config.replaceVariable("user_agent", userAgent)
                    else
                        Config.removeVariable("user_agent")
                    save = true
                    restart = true
                }

                val newDisplayTheme = if (darkTheme.isChecked)
                    AppCompatDelegate.MODE_NIGHT_YES
                else
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                if (oldDisplayTheme != newDisplayTheme) {
                    Preferences(applicationContext).displayTheme = newDisplayTheme
                    Config.replaceVariable("dark_theme",
                        if (darkTheme.isChecked) "yes" else "no")
                    save = true
                }

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

                val logLevelString = if (debug.isChecked) "0" else "2"
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

        if (save) Config.save()
        BaresipService.activities.remove("config")
        val intent = Intent(this, MainActivity::class.java)
        if (restart || audioRestart)
            intent.putExtra("restart", true)
        setResult(RESULT_OK, intent)
        finish()

    }

    private fun goBack() {

        BaresipService.activities.remove("config")
        if (audioRestart) {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("restart", true)
            setResult(RESULT_OK, intent)
        } else {
            setResult(Activity.RESULT_CANCELED, Intent(this, MainActivity::class.java))
        }
        finish()

    }

    private fun bindTitles() {
        val audioRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            audioRestart = it.resultCode == Activity.RESULT_OK
        }
        binding.AutoStartTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.start_automatically),
                    getString(R.string.start_automatically_help))
        }
        binding.ListenAddressTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.listen_address),
                    getString(R.string.listen_address_help))
        }
        binding.NetAfTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.address_family),
                    getString(R.string.address_family_help))
        }
        binding.DnsServersTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.dns_servers),
                    getString(R.string.dns_servers_help))
        }
        binding.CertificateFileTitle.setOnClickListener {
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
        binding.UserAgentTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.user_agent),
                getString(R.string.user_agent_help))
        }
        binding.AudioSettingsTitle.setOnClickListener {
            audioRequest.launch(Intent(this,  AudioActivity::class.java))
        }
        binding.VideoSizeTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.video_size),
                    getString(R.string.video_size_help))
        }
        binding.DarkThemeTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.dark_theme),
                    getString(R.string.dark_theme_help))
        }
        binding.ContactsTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.contacts),
                    getString(R.string.contacts_help))
        }
        binding.BatteryOptimizationsTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.battery_optimizations),
                    getString(R.string.battery_optimizations_help))
        }
        binding.DefaultPhoneAppTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.default_phone_app),
                    getString(R.string.default_phone_app_help))
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


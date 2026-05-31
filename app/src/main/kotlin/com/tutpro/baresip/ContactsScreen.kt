package com.tutpro.baresip

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import coil.compose.AsyncImage
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.TextAvatar
import com.tutpro.baresip.CustomElements.verticalScrollbar
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val avatarSize: Int = 96

fun NavGraphBuilder.contactsScreenRoute(navController: NavController) {
    composable("contacts") { _ ->
        ContactsScreen(navController = navController)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsScreen(navController: NavController) {

    val ctx = LocalContext.current
    val activity = LocalActivity.current!!
    var searchContactName by remember { mutableStateOf("") }

    val consentRequest = stringResource(R.string.consent_request)
    val contactsConsent = stringResource(R.string.contacts_consent)
    val deny = stringResource(R.string.deny)
    val accept = stringResource(R.string.accept)
    val notice = stringResource(R.string.notice)
    val noAndroidContacts = stringResource(R.string.no_android_contacts)
    val ok = stringResource(R.string.ok)

    var expanded by remember { mutableStateOf(false) }
    val both = stringResource(R.string.both)
    val import = stringResource(R.string.import_contacts)
    val export = stringResource(R.string.export_contacts)
    val delete = stringResource(R.string.delete)
    val confirmation = stringResource(R.string.confirmation)
    val cancel = stringResource(R.string.cancel)
    val contactsDeleteQuestion = stringResource(R.string.contacts_delete_question)
    val contactDeleteQuestion = stringResource(R.string.contact_delete_question)

    val vcfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/vcard")
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val writer = outputStream.bufferedWriter()
                    for (contact in BaresipService.baresipContacts.value) {
                        writer.write("BEGIN:VCARD\n")
                        writer.write("VERSION:3.0\n")
                        writer.write("FN:${contact.name}\n")
                        val nameParts = contact.name.split(" ", limit = 2)
                        if (nameParts.size == 2)
                            writer.write("N:${nameParts[1]};${nameParts[0]};;;\n")
                        else
                            writer.write("N:;${contact.name};;;\n")
                        if (contact.email.isNotEmpty())
                            writer.write("EMAIL:${contact.email}\n")
                        if (contact.avatarImage != null) {
                            val outputStreamPhoto = ByteArrayOutputStream()
                            contact.avatarImage!!.compress(Bitmap.CompressFormat.JPEG, 100, outputStreamPhoto)
                            val base64Image = Base64.encodeToString(outputStreamPhoto.toByteArray(), Base64.NO_WRAP)
                            writer.write("PHOTO;ENCODING=BASE64;JPEG:$base64Image\n")
                        }
                        for (u in contact.uris) {
                            if (u.uri.startsWith("tel:")) {
                                if (u.label.isNotEmpty())
                                    writer.write("TEL;X-${u.label}:${u.uri.substring(4)}\n")
                                else
                                    writer.write("TEL:${u.uri.substring(4)}\n")
                            } else if (u.uri.startsWith("sip:")) {
                                if (u.label.isNotEmpty())
                                    writer.write("X-SIP;X-${u.label}:${u.uri.substring(4)}\n")
                                else
                                    writer.write("X-SIP:${u.uri.substring(4)}\n")
                            }
                        }
                        writer.write("END:VCARD\n")
                    }
                    writer.flush()
                }
                Toast.makeText(ctx, R.string.contact_export_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export VCF: ${e.message}")
                Toast.makeText(ctx, R.string.contact_export_failure, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val vcfImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = inputStream.bufferedReader()
                    val lines = mutableListOf<String>()
                    reader.forEachLine { line ->
                        if (line.startsWith(" ") || line.startsWith("\t")) {
                            if (lines.isNotEmpty()) {
                                lines[lines.size - 1] = lines.last() + line.trim()
                            }
                        } else {
                            lines.add(line)
                        }
                    }

                    var name = ""
                    var email = ""
                    val uris = mutableListOf<Contact.ContactUri>()
                    var photoBase64 = ""
                    var contactNo = 0
                    val newBaresipContacts = BaresipService.baresipContacts.value.toMutableList()

                    for (line in lines) {
                        when {
                            line.startsWith("BEGIN:VCARD", ignoreCase = true) -> {
                                name = ""
                                email = ""
                                uris.clear()
                                photoBase64 = ""
                            }
                            line.startsWith("FN:", ignoreCase = true) -> {
                                name = line.substring(3).trim()
                            }
                            line.startsWith("N:", ignoreCase = true) && name.isEmpty() -> {
                                name = line.substring(2).trim().replace(";", " ").trim()
                            }
                            line.startsWith("EMAIL", ignoreCase = true) -> {
                                if (email.isEmpty())
                                    email = line.substringAfter(":").trim()
                            }
                            line.startsWith("TEL", ignoreCase = true) -> {
                                val label = if (line.contains("X-")) line.substringAfter("X-").substringBefore(":") else ""
                                val value = line.substringAfter(":").trim()
                                val cleanValue = value.filterNot { setOf('-', ' ', '(', ')').contains(it) }
                                if (cleanValue.isNotEmpty()) {
                                    val telUri = if (cleanValue.startsWith("tel:")) cleanValue else "tel:$cleanValue"
                                    if (uris.none { it.uri == telUri }) uris.add(Contact.ContactUri(telUri, label))
                                }
                            }
                            line.startsWith("X-SIP", ignoreCase = true) -> {
                                val label = if (line.contains("X-")) line.substringAfter("X-").substringBefore(":") else ""
                                val sipUri = line.substringAfter(":").trim()
                                if (sipUri.isNotEmpty()) {
                                    val fullSipUri = if (sipUri.startsWith("sip:")) sipUri else "sip:$sipUri"
                                    if (uris.none { it.uri == fullSipUri }) uris.add(Contact.ContactUri(fullSipUri, label))
                                }
                            }
                            line.startsWith("PHOTO", ignoreCase = true) && line.contains("BASE64", ignoreCase = true) -> {
                                photoBase64 = line.substringAfter(":").trim()
                            }
                            line.startsWith("END:VCARD", ignoreCase = true) -> {
                                if (name.isNotEmpty() && (uris.isNotEmpty() || email.isNotEmpty())) {
                                    val existingContact = newBaresipContacts.find { it.name == name }
                                    val contactId = existingContact?.id ?: (System.currentTimeMillis() + contactNo++)
                                    if (photoBase64.isNotEmpty()) {
                                        try {
                                            val decodedString = Base64.decode(photoBase64, Base64.DEFAULT)
                                            val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                                            if (decodedByte != null) {
                                                val avatarFile = File(BaresipService.filesPath, "$contactId.png")
                                                FileOutputStream(avatarFile).use { out ->
                                                    decodedByte.compress(Bitmap.CompressFormat.PNG, 100, out)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to decode photo for $name: ${e.message}")
                                        }
                                    }
                                    if (existingContact != null) {
                                        for (u in uris) {
                                            if (existingContact.uris.none { it.uri == u.uri }) {
                                                existingContact.uris.add(u)
                                            }
                                        }
                                        if (existingContact.email.isEmpty() && email.isNotEmpty()) {
                                            existingContact.email = email
                                        }
                                    } else {
                                        newBaresipContacts.add(
                                            Contact.BaresipContact(
                                                name,
                                                ArrayList(uris),
                                                email,
                                                Utils.randomColor(),
                                                contactId,
                                                false
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    BaresipService.baresipContacts.value = newBaresipContacts.toList()
                    Contact.saveBaresipContacts()
                    Contact.restoreBaresipContacts()
                    Contact.contactsUpdate()
                    Toast.makeText(
                        ctx,
                        R.string.contact_import_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import VCF: ${e.message}")
                Toast.makeText(
                    ctx,
                    R.string.contact_import_failure,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val contactNames = remember(BaresipService.contactsMode) {
        val names = mutableListOf("baresip", "Android", both)
        val values = listOf("baresip", "android", "both")
        val index = values.indexOf(BaresipService.contactsMode)
        if (index != -1) {
            val name = names.removeAt(index)
            names.add(0, name)
        }
        names
    }
    val contactValues = remember(BaresipService.contactsMode) {
        val values = mutableListOf("baresip", "android", "both")
        val index = values.indexOf(BaresipService.contactsMode)
        if (index != -1) {
            val value = values.removeAt(index)
            values.add(0, value)
        }
        values
    }

    val showDialog = remember { mutableStateOf(false) }
    val showNoticeDialog = remember { mutableStateOf(false) }
    val title = remember { mutableStateOf("") }
    val message = remember { mutableStateOf("") }
    val firstButtonText = remember { mutableStateOf("") }
    val onFirstClicked = remember { mutableStateOf({}) }
    val lastButtonText = remember { mutableStateOf("") }
    val onLastClicked = remember { mutableStateOf({}) }
    var pendingMode by remember { mutableStateOf("") }

    AlertDialog(
        showDialog = showDialog,
        title = title.value,
        message = message.value,
        firstButtonText = firstButtonText.value,
        onFirstClicked = onFirstClicked.value,
        lastButtonText = lastButtonText.value,
        onLastClicked = onLastClicked.value,
    )

    fun setContactsMode(mode: String) {
        if (Config.variable("contacts_mode").lowercase() != mode) {
            Config.replaceVariable("contacts_mode", mode)
            BaresipService.contactsMode = mode
            val baresipService = Intent(ctx, BaresipService::class.java)
            when (mode) {
                "baresip" -> {
                    BaresipService.androidContacts.value = listOf()
                    Contact.restoreBaresipContacts()
                    baresipService.action = "Stop Content Observer"
                }
                "android" -> {
                    BaresipService.baresipContacts.value = mutableListOf()
                    Contact.loadAndroidContacts(ctx)
                    baresipService.action = "Start Content Observer"
                }
                "both" -> {
                    Contact.restoreBaresipContacts()
                    Contact.loadAndroidContacts(ctx)
                    baresipService.action = "Start Content Observer"
                }
            }
            Contact.contactsUpdate()
            Config.save()
            ContextCompat.startForegroundService(ctx, baresipService)
        }
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_CONTACTS] == true &&
            permissions[Manifest.permission.WRITE_CONTACTS] == true) {
            if (pendingMode.isNotEmpty()) {
                setContactsMode(pendingMode)
            }
        }
        pendingMode = ""
    }

    AlertDialog(
        showDialog = showNoticeDialog,
        title = notice,
        message = noAndroidContacts,
        lastButtonText = ok,
        onLastClicked = {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
                )
            )
        }
    )

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
                            text = stringResource(R.string.contacts),
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
                        IconButton(
                            onClick = { navController.navigateUp() }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Menu"
                            )
                        }
                        CustomElements.DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            items = contactNames + import + export + delete,
                            onItemClick = { name ->
                                expanded = false
                                if (name == import) {
                                    vcfImportLauncher.launch(arrayOf("text/vcard", "text/x-vcard"))
                                    return@DropdownMenu
                                }
                                if (name == export) {
                                    val fileName = "contacts_" +
                                            SimpleDateFormat(
                                                "yyyy_MM_dd_HH_mm",
                                                Locale.getDefault()
                                            ).format(Date()) + ".vcf"
                                    vcfExportLauncher.launch(fileName)
                                    return@DropdownMenu
                                }
                                if (name == delete) {
                                    title.value = confirmation
                                    message.value = contactsDeleteQuestion
                                    firstButtonText.value = cancel
                                    onFirstClicked.value = { }
                                    lastButtonText.value = delete
                                    onLastClicked.value = {
                                        for (contact in BaresipService.baresipContacts.value) {
                                            val avatarFile = File(BaresipService.filesPath, "${contact.id}.png")
                                            if (avatarFile.exists()) avatarFile.delete()
                                        }
                                        BaresipService.baresipContacts.value = mutableListOf()
                                        Contact.saveBaresipContacts()
                                        Contact.contactsUpdate()
                                    }
                                    showDialog.value = true
                                    return@DropdownMenu
                                }
                                val mode = contactValues[contactNames.indexOf(name)]
                                val contactsPermissions = arrayOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.WRITE_CONTACTS
                                )
                                if (mode != "baresip" && !Utils.checkPermissions(ctx, contactsPermissions)) {
                                    title.value = consentRequest
                                    message.value = contactsConsent
                                    firstButtonText.value = deny
                                    onFirstClicked.value = { }
                                    lastButtonText.value = accept
                                    onLastClicked.value = {
                                        showDialog.value = false
                                        if (ContextCompat.checkSelfPermission(
                                                ctx,
                                                Manifest.permission.READ_CONTACTS
                                            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                                                ctx,
                                                Manifest.permission.WRITE_CONTACTS
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            Log.d(TAG, "Contacts permissions already granted")
                                            setContactsMode(mode)
                                        } else {
                                            if (shouldShowRequestPermissionRationale(
                                                    activity, Manifest.permission.READ_CONTACTS
                                                ) ||
                                                shouldShowRequestPermissionRationale(
                                                    activity, Manifest.permission.WRITE_CONTACTS
                                                )
                                            ) {
                                                pendingMode = mode
                                                showNoticeDialog.value = true
                                            } else {
                                                pendingMode = mode
                                                requestPermissionsLauncher.launch(contactsPermissions)
                                            }
                                        }
                                    }
                                    showDialog.value = true
                                } else {
                                    setContactsMode(mode)
                                }
                            }
                        )
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            }
        },
        bottomBar = {
            BottomBar(
                navController = navController,
                searchContactName = searchContactName,
                onSearchContactNameChange = { searchContactName = it }
            )
        },
        content = { contentPadding ->
            ContactsContent(
                ctx,
                navController,
                contentPadding,
                searchContactName,
                showDialog,
                title,
                message,
                firstButtonText,
                onFirstClicked,
                lastButtonText,
                onLastClicked,
                confirmation,
                cancel,
                delete,
                contactDeleteQuestion
            )
        }
    )
}

@Composable
private fun BottomBar(
    navController: NavController,
    searchContactName: String,
    onSearchContactNameChange: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchContactName,
            onValueChange = {
                onSearchContactNameChange(it)
                if (it.isBlank()) {
                    keyboardController?.hide()
                }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            trailingIcon = {
                if (searchContactName.isNotEmpty())
                    Icon(
                        Icons.Outlined.Clear,
                        contentDescription = null,
                        modifier = Modifier.clickable {
                            onSearchContactNameChange("")
                            keyboardController?.hide()
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
            },
            label = { Text(stringResource(R.string.search)) },
            textStyle = TextStyle(fontSize = 18.sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            )
        )
        SmallFloatingActionButton(
            onClick = { navController.navigate("contact//new") },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                modifier = Modifier.size(36.dp),
                contentDescription = stringResource(R.string.add)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactsContent(
    ctx: Context,
    navController: NavController,
    contentPadding: PaddingValues,
    searchQuery: String,
    showDialog: androidx.compose.runtime.MutableState<Boolean>,
    title: androidx.compose.runtime.MutableState<String>,
    message: androidx.compose.runtime.MutableState<String>,
    firstButtonText: androidx.compose.runtime.MutableState<String>,
    onFirstClicked: androidx.compose.runtime.MutableState<() -> Unit>,
    lastButtonText: androidx.compose.runtime.MutableState<String>,
    onLastClicked: androidx.compose.runtime.MutableState<() -> Unit>,
    confirmationText: String,
    cancelText: String,
    deleteText: String,
    contactDeleteQuestion: String
) {
    val lazyListState = rememberLazyListState()

    val scrollToContact = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<String>("scrollToContact")
        ?.observeAsState()

    var lastSearchQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank() && lastSearchQuery.isNotBlank()) {
            lazyListState.scrollToItem(0)
        }
        lastSearchQuery = searchQuery
    }

    val filteredContacts = remember(BaresipService.contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            BaresipService.contacts.map { contact ->
                Pair(contact, buildAnnotatedString { append(contact.name()) })
            }
        } else {
            val normalizedQuery = Utils.unaccent(searchQuery)
            BaresipService.contacts
                .filter { contact ->
                    Utils.unaccent(contact.name()).contains(normalizedQuery, ignoreCase = true)
                }
                .map { contact ->
                    Pair(contact, Utils.buildAnnotatedStringWithHighlight(contact.name(), searchQuery))
                }
        }
    }

    LaunchedEffect(scrollToContact?.value, filteredContacts) {
        scrollToContact?.value?.let { name ->
            val index = filteredContacts.indexOfFirst { it.first.name() == name }
            if (index != -1) {
                lazyListState.scrollToItem(index)
            }
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("scrollToContact")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 10.dp)
            .verticalScrollbar(state = lazyListState),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            filteredContacts, key = { index, (contact, _) -> "${contact.id()}_$index" }
        ) { _, (contact, annotatedName) ->

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                when (contact) {

                    is Contact.BaresipContact -> {
                        val avatarImage = contact.avatarImage
                        if (avatarImage != null)
                            Image(
                                bitmap = avatarImage.asImageBitmap(),
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                        else
                            TextAvatar(contact.name(), contact.color)
                    }

                    is Contact.AndroidContact -> {
                        val thumbNailUri = contact.thumbnailUri
                        if (thumbNailUri != null)
                            AsyncImage(
                                model = thumbNailUri,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                            )
                        else
                            TextAvatar(contact.name(), contact.color)
                    }
                }

                when (contact) {

                    is Contact.BaresipContact -> {
                        Text(text = annotatedName,
                            fontSize = 20.sp,
                            fontStyle = if (contact.favorite()) FontStyle.Italic else FontStyle.Normal,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("contact/${contact.name()}/old")
                                    },
                                    onLongClick = {
                                        title.value = confirmationText
                                        message.value = String.format(
                                            contactDeleteQuestion,
                                            contact.name()
                                        )
                                        firstButtonText.value = cancelText
                                        onFirstClicked.value = { }
                                        lastButtonText.value = deleteText
                                        onLastClicked.value = {
                                            val id = contact.id
                                            val avatarFile = File(
                                                BaresipService.filesPath,
                                                "$id.png"
                                            )
                                            if (avatarFile.exists()) {
                                                try {
                                                    avatarFile.delete()
                                                } catch (e: IOException) {
                                                    Log.e(
                                                        TAG,
                                                        "Could not delete file $id.png: ${e.message}"
                                                    )
                                                }
                                            }
                                            Contact.removeBaresipContact(contact)
                                        }
                                        showDialog.value = true
                                    }
                                )
                        )
                    }

                    is Contact.AndroidContact -> {
                        Text(text = annotatedName,
                            fontSize = 20.sp,
                            fontStyle = if (contact.favorite()) FontStyle.Italic else FontStyle.Normal,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp, top = 4.dp, bottom = 4.dp)
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("contact/${contact.name()}/old")
                                    },
                                    onLongClick = {
                                        title.value = confirmationText
                                        message.value = String.format(
                                            contactDeleteQuestion,
                                            contact.name()
                                        )
                                        firstButtonText.value = cancelText
                                        onFirstClicked.value = { }
                                        lastButtonText.value = deleteText
                                        onLastClicked.value = {
                                            ctx.contentResolver.delete(
                                                ContactsContract.RawContacts.CONTENT_URI,
                                                ContactsContract.Contacts.DISPLAY_NAME + "='" + contact.name() + "'",
                                                null
                                            )
                                        }
                                        showDialog.value = true
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

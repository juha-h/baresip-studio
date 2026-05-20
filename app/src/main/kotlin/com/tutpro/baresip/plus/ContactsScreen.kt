package com.tutpro.baresip.plus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
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
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
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
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import coil.compose.AsyncImage
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import com.tutpro.baresip.plus.CustomElements.SelectableAlertDialog
import com.tutpro.baresip.plus.CustomElements.TextAvatar
import com.tutpro.baresip.plus.CustomElements.verticalScrollbar
import java.io.File
import java.io.IOException

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

    val vcfImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = inputStream.bufferedReader()
                    var name = ""
                    var email = ""
                    val uris = mutableListOf<String>()
                    reader.forEachLine { line ->
                        when {
                            line.startsWith("BEGIN:VCARD", ignoreCase = true) -> {
                                name = ""
                                email = ""
                                uris.clear()
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
                                val value = line.substringAfter(":").trim()
                                val cleanValue = value.filterNot { setOf('-', ' ', '(', ')').contains(it) }
                                if (cleanValue.isNotEmpty()) {
                                    val telUri = if (cleanValue.startsWith("tel:")) cleanValue else "tel:$cleanValue"
                                    if (telUri !in uris) uris.add(telUri)
                                }
                            }
                            line.startsWith("X-SIP:", ignoreCase = true) -> {
                                val sipUri = line.substring(6).trim()
                                if (sipUri.isNotEmpty()) {
                                    val fullSipUri = if (sipUri.startsWith("sip:")) sipUri else "sip:$sipUri"
                                    if (fullSipUri !in uris) uris.add(fullSipUri)
                                }
                            }
                            line.startsWith("END:VCARD", ignoreCase = true) -> {
                                if (name.isNotEmpty() && (uris.isNotEmpty() || email.isNotEmpty())) {
                                    val existingContact = Contact.baresipContact(name)
                                    if (existingContact != null) {
                                        var updated = false
                                        for (u in uris) {
                                            if (u !in existingContact.uris) {
                                                existingContact.uris.add(u)
                                                updated = true
                                            }
                                        }
                                        if (existingContact.email.isEmpty() && email.isNotEmpty()) {
                                            existingContact.email = email
                                            updated = true
                                        }
                                        if (updated) {
                                            Contact.updateBaresipContact(existingContact.id, existingContact)
                                        }
                                    } else {
                                        Contact.addBaresipContact(
                                            Contact.BaresipContact(
                                                name,
                                                ArrayList(uris),
                                                email,
                                                Utils.randomColor(),
                                                System.currentTimeMillis(),
                                                false
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Contact.saveBaresipContacts()
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

    SelectableAlertDialog(
        openDialog = CustomElements.showSelectItemDialog,
        title = stringResource(R.string.choose_destination_uri),
        items = CustomElements.selectItems.value,
        onItemClicked = CustomElements.selectItemAction.value,
        neutralButtonText = stringResource(R.string.cancel),
        onNeutralClicked = {}
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
                            items = contactNames + import,
                            onItemClick = { name ->
                                expanded = false
                                if (name == import) {
                                    vcfImportLauncher.launch(arrayOf("text/vcard", "text/x-vcard"))
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
            ContactsContent(ctx, navController, contentPadding, searchContactName)
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
    searchQuery: String
) {
    val showConfirmationDialog = remember { mutableStateOf(false) }
    val confirmationDialogMessage = remember { mutableStateOf("") }
    val lastAction = remember { mutableStateOf({}) }

    if (showConfirmationDialog.value)
        AlertDialog(
            showDialog = showConfirmationDialog,
            title = stringResource(R.string.confirmation),
            message = confirmationDialogMessage.value,
            firstButtonText = stringResource(R.string.cancel),
            lastButtonText = stringResource(R.string.delete),
            onLastClicked = lastAction.value,
        )

    val alertTitle = remember { mutableStateOf("") }
    val alertMessage = remember { mutableStateOf("") }
    val showAlert = remember { mutableStateOf(false) }

    AlertDialog(
        showDialog = showAlert,
        title = alertTitle.value,
        message = alertMessage.value,
        lastButtonText = stringResource(R.string.ok),
    )

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
                                        confirmationDialogMessage.value = String.format(
                                            ctx.getString(R.string.contact_delete_question),
                                            contact.name()
                                        )
                                        lastAction.value = {
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
                                        showConfirmationDialog.value = true
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
                                        confirmationDialogMessage.value = String.format(
                                            ctx.getString(R.string.contact_delete_question),
                                            contact.name()
                                        )
                                        lastAction.value = {
                                            ctx.contentResolver.delete(
                                                ContactsContract.RawContacts.CONTENT_URI,
                                                ContactsContract.Contacts.DISPLAY_NAME + "='" + contact.name() + "'",
                                                null
                                            )
                                        }
                                        showConfirmationDialog.value = true
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

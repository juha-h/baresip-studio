package com.tutpro.baresip.plus

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Contacts.Data
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil.compose.rememberAsyncImagePainter
import com.tutpro.baresip.plus.BaresipService.Companion.circleGreen
import com.tutpro.baresip.plus.BaresipService.Companion.colorblind
import com.tutpro.baresip.plus.CustomElements.AlertDialog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

fun NavGraphBuilder.contactScreenRoute(navController: NavController, viewModel: ViewModel) {
    composable(
        route = "contact/{uri_or_name}/{kind}",
        arguments = listOf(
            navArgument("uri_or_name") { type = NavType.StringType },
            navArgument("kind") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val uriOrNameArg = backStackEntry.arguments?.getString("uri_or_name")!!
        val kindArg = backStackEntry.arguments?.getString("kind")!!
        ContactScreen(
            viewModel = viewModel,
            navController = navController,
            uriOrNameArg = uriOrNameArg,
            kindArg = kindArg
        )
    }
}

private data class ScreenState(
    val new: Boolean = false,
    val isEditing: Boolean = false,
    val favorite: Boolean = false,
    val android: Boolean = false,
    val id: Long = 0,
    val newId: Long = 0,
    val name: String = "",
    val uris: List<Contact.ContactUri> = emptyList(),
    val email: String = "",
    val color: Int = 0,
    val avatarImageUri: String? = null,
    val tmpAvatarFile: File? = null,
    val isLoading: Boolean = true,
    val isBaresipContact: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactScreen(
    viewModel: ViewModel,
    navController: NavController,
    uriOrNameArg: String,
    kindArg: String
) {
    val ctx = LocalContext.current
    var screenState by remember { mutableStateOf(ScreenState()) }

    val title = when {
        screenState.new -> stringResource(R.string.new_contact)
        else -> uriOrNameArg
    }

    LaunchedEffect(key1 = uriOrNameArg, key2 = kindArg) {
        val isNew = kindArg == "new"
        if (isNew) {
            val time = System.currentTimeMillis()
            screenState = ScreenState(
                new = true,
                isEditing = true,
                name = "",
                uris = if (uriOrNameArg == "") emptyList() else listOf(Contact.ContactUri(uriOrNameArg, "")),
                email = "",
                favorite = false,
                android = BaresipService.contactsMode == "android",
                color = Utils.randomColor(),
                id = time,
                newId = time,
                isLoading = false,
                isBaresipContact = true
            )
        } else {
            val baresipContact = Contact.baresipContact(uriOrNameArg)
            val androidContact = Contact.androidContact(uriOrNameArg)
            val contact = baresipContact ?: androidContact

            if (contact == null) {
                Log.e(TAG, "No contact found with name $uriOrNameArg")
                navController.navigateUp()
                return@LaunchedEffect
            }

            val avatarFile = File(BaresipService.filesPath, "${contact.id()}.png")
            screenState = ScreenState(
                new = false,
                isEditing = false,
                name = contact.name(),
                uris = contact.uris(),
                email = contact.email(),
                favorite = contact.favorite(),
                android = false,
                color = contact.colorInt(),
                id = contact.id(),
                newId = contact.id(),
                avatarImageUri = when (contact) {
                    is Contact.BaresipContact if contact.avatarImage != null && avatarFile.exists() ->
                        Uri.fromFile(
                            avatarFile
                        ).toString()

                    is Contact.AndroidContact if contact.thumbnailUri != null ->
                        contact.thumbnailUri.toString()
                    else -> null
                },
                isLoading = false,
                isBaresipContact = contact is Contact.BaresipContact
            )
        }
    }

    val onBack: () -> Unit = {
        if (screenState.isEditing && !screenState.new) {
            screenState = screenState.copy(isEditing = false)
            // Reload original state
            val contact = Contact.baresipContact(uriOrNameArg)!!
            val avatarFile = File(BaresipService.filesPath, "${contact.id}.png")
            screenState = screenState.copy(
                name = contact.name,
                uris = contact.uris,
                email = contact.email,
                favorite = contact.favorite,
                color = contact.color,
                avatarImageUri = if (contact.avatarImage != null && avatarFile.exists())
                    Uri.fromFile(avatarFile).toString()
                else
                    null,
                tmpAvatarFile = null
            )
        } else {
            screenState.tmpAvatarFile?.let { tempFile ->
                if (tempFile.exists()) {
                    Log.d(TAG, "Back pressed, deleting temp avatar: ${tempFile.name}")
                    Utils.deleteFile(tempFile)
                }
            }
            navController.navigateUp()
        }
    }

    val onCheck: () -> Unit = {
        val result = checkOnClick(
            ctx = ctx,
            currentState = screenState,
            uriOrNameArg = uriOrNameArg,
        )
        if (result) {
            if (screenState.new) {
                navController.previousBackStackEntry?.savedStateHandle?.set("scrollToContact", screenState.name)
                navController.navigateUp()
            } else {
                // Update UI state with saved values
                val contact = Contact.baresipContact(screenState.name)!!
                val avatarFile = File(BaresipService.filesPath, "${contact.id}.png")
                screenState = screenState.copy(
                    isEditing = false,
                    tmpAvatarFile = null,
                    name = contact.name,
                    uris = contact.uris,
                    email = contact.email,
                    favorite = contact.favorite,
                    color = contact.color,
                    id = contact.id,
                    newId = contact.id,
                    avatarImageUri = if (contact.avatarImage != null && avatarFile.exists())
                        Uri.fromFile(avatarFile).toString()
                    else
                        null
                )
            }
        }
    }

    val onEdit: () -> Unit = {
        screenState = screenState.copy(isEditing = true)
    }

    BackHandler(enabled = true) {
        onBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                TopAppBar(
                    title = title,
                    isEditing = screenState.isEditing,
                    isBaresipContact = screenState.isBaresipContact,
                    onBack = onBack,
                    onCheck = onCheck,
                    onEdit = onEdit
                )
            }
        },
        content = { contentPadding ->
            if (!screenState.isLoading) {
                ContactContent(
                    ctx = ctx,
                    viewModel = viewModel,
                    navController = navController,
                    contentPadding = contentPadding,
                    screenState = screenState,
                    onStateChange = { newState -> screenState = newState }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
    title: String,
    isEditing: Boolean,
    isBaresipContact: Boolean,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onEdit: () -> Unit
) {
    TopAppBar(
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            if (isEditing) {
                IconButton(onClick = onCheck) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Save"
                    )
                }
            } else if (isBaresipContact) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit"
                    )
                }
            }
        }
    )
}

private val alertTitle = mutableStateOf("")
private val alertMessage = mutableStateOf("")
private val showAlert = mutableStateOf(false)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactContent(
    ctx: Context,
    viewModel: ViewModel,
    navController: NavController,
    contentPadding: PaddingValues,
    screenState: ScreenState,
    onStateChange: (ScreenState) -> Unit
) {
    if (showAlert.value) {
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            lastButtonText = stringResource(R.string.ok),
        )
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 52.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvatarSection(
            ctx = ctx,
            isEditing = screenState.isEditing,
            name = screenState.name,
            color = screenState.color,
            currentAvatarUri = screenState.avatarImageUri,
            onNewAvatarChosen = { processedTmpFile, generatedNewId ->
                onStateChange(
                    screenState.copy(
                        avatarImageUri = Uri.fromFile(processedTmpFile).toString(),
                        tmpAvatarFile = processedTmpFile,
                        newId = generatedNewId
                    )
                )
            },
            onAvatarColorChange = { newRandomColor ->
                screenState.tmpAvatarFile?.let {
                    if (it.exists())
                        Utils.deleteFile(it)
                }
                onStateChange(
                    screenState.copy(
                        color = newRandomColor,
                        avatarImageUri = null,
                        tmpAvatarFile = null
                    )
                )
            }
        )

        ContactNameSection(
            name = screenState.name,
            isEditing = screenState.isEditing,
            new = screenState.new,
            onNameChange = { newName -> onStateChange(screenState.copy(name = newName)) }
        )

        UrisSection(
            ctx = ctx,
            viewModel = viewModel,
            navController = navController,
            uris = screenState.uris,
            isEditing = screenState.isEditing,
            onUrisChange = { newUris -> onStateChange(screenState.copy(uris = newUris)) }
        )

        EmailSection(
            ctx = ctx,
            email = screenState.email,
            isEditing = screenState.isEditing,
            onEmailChange = { newEmail -> onStateChange(screenState.copy(email = newEmail)) }
        )

        if (screenState.isEditing) {
            FavoriteSection(
                ctx = ctx,
                favorite = screenState.favorite,
                onFavoriteChange = { newFavorite ->
                    onStateChange(screenState.copy(favorite = newFavorite))
                }
            )
            if (screenState.new && BaresipService.contactsMode == "both")
                AndroidSection(
                    ctx = ctx,
                    android = screenState.android,
                    onAndroidChange = { newAndroid ->
                        onStateChange(screenState.copy(android = newAndroid))
                    }
                )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AvatarSection(
    ctx: Context,
    isEditing: Boolean,
    name: String,
    color: Int,
    currentAvatarUri: String?,
    onNewAvatarChosen: (newImageFile: File, newImageId: Long) -> Unit,
    onAvatarColorChange: (newColor: Int) -> Unit
) {
    val avatarImagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val avatarBitmap = Utils.decodeSampledBitmapFromUri(ctx, uri, 192, 192)

                    if (avatarBitmap == null) {
                        Log.e(TAG, "Failed to decode bitmap from URI: $uri")
                        return@rememberLauncherForActivityResult
                    }

                    val scaledBitmap = avatarBitmap.scale(192, 192)

                    val orientationInputStream = ctx.contentResolver.openInputStream(uri)
                    val exif = if (orientationInputStream != null) ExifInterface(orientationInputStream) else null
                    orientationInputStream?.close()
                    val orientation = exif?.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    ) ?: ExifInterface.ORIENTATION_NORMAL
                    val rotatedBitmap = rotateBitmap(scaledBitmap, orientation)

                    val newImageId = System.currentTimeMillis()
                    val tempNewImageFile = File(BaresipService.filesPath, "${newImageId}.png")

                    if (saveBitmap(rotatedBitmap, tempNewImageFile)) {
                        onNewAvatarChosen(tempNewImageFile, newImageId)
                    } else {
                        Log.e(TAG, "Failed to save processed avatar image")
                        if (tempNewImageFile.exists()) Utils.deleteFile(tempNewImageFile)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not process avatar image: ${e.message}")
                }
            }
        }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(avatarSize.dp)
                .clip(CircleShape)
                .background(if (currentAvatarUri == null) Color(color) else Color.Transparent)
                .let { modifier ->
                    if (isEditing) {
                        modifier.combinedClickable(
                            onClick = { avatarImagePicker.launch("image/*") },
                            onLongClick = { onAvatarColorChange(Utils.randomColor()) }
                        )
                    } else {
                        modifier
                    }
                }
        ) {
            if (currentAvatarUri == null) {
                Box(
                    modifier = Modifier.size(avatarSize.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(SolidColor(Color(color)))
                    }
                    val text = if (name.isNotBlank()) name.take(1).uppercase() else "?"
                    Text(text, fontSize = 72.sp, color = Color.White)
                }
            } else {
                Image(
                    painter = rememberAsyncImagePainter(model = currentAvatarUri),
                    contentDescription = stringResource(R.string.avatar_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(avatarSize.dp).clip(CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ContactNameSection(name: String, isEditing: Boolean, new: Boolean, onNameChange: (String) -> Unit) {
    if (isEditing) {
        val focusRequester = remember { FocusRequester() }
        OutlinedTextField(
            value = name,
            placeholder = { Text(stringResource(R.string.contact_name)) },
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
            label = { Text(stringResource(R.string.contact_name)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            )
        )
        LaunchedEffect(new) {
            if (new) focusRequester.requestFocus()
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = name,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun UrisSection(
    ctx: Context,
    viewModel: ViewModel,
    navController: NavController,
    uris: List<Contact.ContactUri>,
    isEditing: Boolean,
    onUrisChange: (List<Contact.ContactUri>) -> Unit
) {
    val selectedAor by viewModel.selectedAor.collectAsState()

    if (isEditing) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uris.forEachIndexed { index, contactUri ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column (modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = contactUri.uri,
                            placeholder = { Text(stringResource(R.string.user_domain_or_number)) },
                            onValueChange = { newUri ->
                                val newList = uris.toMutableList()
                                newList[index] = contactUri.copy(uri = newUri)
                                onUrisChange(newList.toList())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                            label = { Text("${stringResource(R.string.sip_or_tel_uri)} ${index + 1}") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )
                        OutlinedTextField(
                            value = contactUri.label,
                            placeholder = { Text(stringResource(R.string.label)) },
                            onValueChange = { newLabel ->
                                val newList = uris.toMutableList()
                                newList[index] = contactUri.copy(label = newLabel)
                                onUrisChange(newList.toList())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                            label = { Text(stringResource(R.string.label)) },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                keyboardType = KeyboardType.Text
                            )
                        )
                    }
                    if (uris.size > 1) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            IconButton(
                                onClick = {
                                    val newList = uris.toMutableList()
                                    newList.removeAt(index)
                                    onUrisChange(newList.toList())
                                },
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Remove,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
            IconButton(
                onClick = {
                    val newList = uris.toMutableList()
                    newList.add(Contact.ContactUri("", ""))
                    onUrisChange(newList.toList())
                },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    else
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uris.forEachIndexed { index, contactUri ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val uri = contactUri.uri
                    val label = contactUri.label.ifEmpty {
                        if (uri.startsWith("tel:"))
                            "${stringResource(R.string.tel_uri)} ${index + 1}"
                        else
                            "${stringResource(R.string.sip_uri)} ${index + 1}"
                    }

                    OutlinedTextField(
                        value = uri.substringAfter(":"),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                        label = { Text(text = label, fontWeight = FontWeight.Bold) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )

                    val ua = UserAgent.ofAor(selectedAor)

                    // Chat Button
                    if (ua != null &&
                            if (uri.startsWith("tel:"))
                                ua.account.isMobile || ua.account.telProvider != ""
                            else
                                !ua.account.isMobile)
                        IconButton(
                            onClick = {
                                if (ua.account.isMobile && ua.status != circleGreen.getValue(colorblind)) {
                                    alertTitle.value = ctx.getString(R.string.notice)
                                    alertMessage.value = ctx.getString(R.string.airplane_mode)
                                    showAlert.value = true
                                }
                                else if (ua.account.isMobile && !Utils.isDefaultSmsApp(ctx)) {
                                    alertTitle.value = ctx.getString(R.string.notice)
                                    alertMessage.value = ctx.getString(R.string.enable_default_messaging)
                                    showAlert.value = true
                                }
                                else {
                                    val intent = Intent(ctx, MainActivity::class.java)
                                    intent.putExtra("uap", ua.uap)
                                    intent.putExtra("peer", uri)
                                    handleIntent(ctx, viewModel, intent, "message")
                                    navController.navigate("main") {
                                        popUpTo("main") { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "Send Message",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                    // Call Button
                    if (ua != null && (if (uri.startsWith("tel:"))
                            ua.account.isMobile || ua.account.telProvider != ""
                        else
                            !ua.account.isMobile))
                        IconButton(
                            onClick = {
                                if (ua.account.isMobile && ua.status != circleGreen.getValue(colorblind)) {
                                    alertTitle.value = ctx.getString(R.string.notice)
                                    alertMessage.value = ctx.getString(R.string.airplane_mode)
                                    showAlert.value = true
                                } else {
                                    val intent = Intent(ctx, MainActivity::class.java)
                                    intent.putExtra("uap", ua.uap)
                                    intent.putExtra("peer", uri)
                                    handleIntent(ctx, viewModel, intent, BaresipService.contactAction)
                                    navController.navigate("main") {
                                        popUpTo("main") { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = "Call",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                    // Video Call Button
                    if (ua != null && !ua.account.isMobile)
                        IconButton(
                            onClick = {
                                if (ua.account.isMobile && Utils.isAirplaneModeOn(ctx)) {
                                    alertTitle.value = ctx.getString(R.string.notice)
                                    alertMessage.value = ctx.getString(R.string.airplane_mode)
                                    showAlert.value = true
                                } else {
                                    val intent = Intent(ctx, MainActivity::class.java)
                                    intent.putExtra("uap", ua.uap)
                                    intent.putExtra("peer", uri)
                                    handleIntent(ctx, viewModel, intent, "video " + BaresipService.contactAction)
                                    navController.navigate("main") {
                                        popUpTo("main") { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = "Video call",
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
    }
}

@Composable
private fun EmailSection(ctx: Context, email: String, isEditing: Boolean, onEmailChange: (String) -> Unit) {
    if (isEditing)
        OutlinedTextField(
            value = email,
            placeholder = { Text(stringResource(R.string.email)) },
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
            label = { Text(stringResource(R.string.email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
    else if (email.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                label = { Text(text = stringResource(R.string.email), fontWeight = FontWeight.Bold) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
            IconButton(
                onClick = {
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = "mailto:$email".toUri()
                    }
                    try {
                        ctx.startActivity(emailIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start email activity: ${e.message}")
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Email,
                    contentDescription = "Send Email",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun FavoriteSection(ctx: Context, favorite: Boolean, onFavoriteChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = stringResource(R.string.favorite),
            modifier = Modifier.weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.favorite)
                    alertMessage.value = ctx.getString(R.string.favorite_help)
                    showAlert.value = true
                },
        )
        Switch(
            checked = favorite,
            onCheckedChange = onFavoriteChange
        )
    }
}

@Composable
private fun AndroidSection(ctx: Context, android: Boolean, onAndroidChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = stringResource(R.string.android),
            modifier = Modifier.weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.android)
                    alertMessage.value = ctx.getString(R.string.android_contact_help)
                    showAlert.value = true
                },
        )
        Switch(
            checked = android,
            onCheckedChange = onAndroidChange
        )
    }
}

private fun checkOnClick(
    ctx: Context,
    currentState: ScreenState,
    uriOrNameArg: String
): Boolean {
    val newUris = ArrayList<Contact.ContactUri>()
    for (contactUri in currentState.uris) {
        var u = contactUri.uri.filterNot { setOf('-', ' ', '(', ')').contains(it) }
        if (u == "") continue
        if (!u.startsWith("sip:") && !u.startsWith("tel:"))
            u = if (Utils.isTelNumber(u)) "tel:$u" else "sip:$u"

        if (!Utils.checkUri(u)) {
            alertTitle.value = ctx.getString(R.string.notice)
            alertMessage.value = String.format(ctx.getString(R.string.invalid_sip_or_tel_uri), u)
            showAlert.value = true
            return false
        }
        newUris.add(Contact.ContactUri(u, contactUri.label))
    }

    if (newUris.isEmpty() && currentState.email.trim().isEmpty()) {
        alertTitle.value = ctx.getString(R.string.notice)
        alertMessage.value = ctx.getString(R.string.sip_or_tel_uri)
        showAlert.value = true
        return false
    }

    var newName = currentState.name.trim()
    if (newName == "") newName = if (newUris.isNotEmpty()) newUris[0].uri.substringAfter(":") else currentState.email
    if (!Utils.checkName(newName)) {
        alertTitle.value = ctx.getString(R.string.notice)
        alertMessage.value = String.format(ctx.getString(R.string.invalid_contact), newName)
        showAlert.value = true
        return false
    }

    val alert: Boolean = if (currentState.new)
        Contact.nameExists(newName, BaresipService.contacts, true)
    else {
        (uriOrNameArg != newName) && Contact.nameExists(newName, BaresipService.contacts, false)
    }
    if (alert) {
        alertTitle.value = ctx.getString(R.string.notice)
        alertMessage.value = String.format(ctx.getString(R.string.contact_already_exists), newName)
        showAlert.value = true
        return false
    }

    var idToUse = currentState.id
    if (currentState.tmpAvatarFile != null && currentState.tmpAvatarFile.exists()) {
        if (currentState.id != currentState.newId) {
            val oldAvatar = File(BaresipService.filesPath, "${currentState.id}.png")
            if (oldAvatar.exists()) Utils.deleteFile(oldAvatar)
            idToUse = currentState.newId
        }
    } else if (currentState.avatarImageUri == null) {
        val avatarFile = File(BaresipService.filesPath, "$idToUse.png")
        if (avatarFile.exists()) Utils.deleteFile(avatarFile)
    }

    val contact: Contact.BaresipContact = Contact.BaresipContact(
        newName, newUris, currentState.email.trim(), currentState.color, idToUse, currentState.favorite
    )

    if (currentState.avatarImageUri != null && (currentState.tmpAvatarFile != null || !currentState.new)) {
        val imageFilePath = BaresipService.filesPath + "/${idToUse}.png"
        contact.avatarImage = Utils.decodeSampledBitmapFromFile(imageFilePath, 192, 192)
    }

    if (currentState.android) {
        addOrUpdateAndroidContact(ctx, contact)
        if (contact.favorite) {
            val contentValues = ContentValues()
            contentValues.put(ContactsContract.Contacts.STARRED, 1)
            try {
                ctx.contentResolver.update(
                    ContactsContract.RawContacts.CONTENT_URI, contentValues,
                    ContactsContract.Contacts.DISPLAY_NAME + "='" + newName + "'", null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Update of Android favorite failed: ${e.message}")
            }
        }
    } else {
        if (currentState.new)
            Contact.addBaresipContact(contact)
        else
            Contact.updateBaresipContact(currentState.id, contact)
    }
    return true
}

private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_NORMAL -> return bitmap
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    bitmap.recycle()
    return rotatedBitmap
}

private fun saveBitmap(bitmap: Bitmap, file: File): Boolean {
    if (file.exists()) file.delete()
    try {
        val out = FileOutputStream(file)
        val scaledBitmap = bitmap.scale(avatarSize, avatarSize)
        scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
        out.close()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save bitmap: $e")
        return false
    }
    return true
}

private fun addOrUpdateAndroidContact(ctx: Context, contact: Contact.BaresipContact) {
    val projection = arrayOf(ContactsContract.Data.RAW_CONTACT_ID)
    val selection = ContactsContract.Data.MIMETYPE + "='" +
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "' AND " +
            CommonDataKinds.StructuredName.DISPLAY_NAME + "='" + contact.name + "'"
    val c: Cursor? = ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, null, null)
    if (c != null && c.moveToFirst()) {
        updateAndroidContact(ctx, c.getLong(0), contact)
    } else {
        addAndroidContact(ctx, contact)
    }
    c?.close()
}

private fun addAndroidContact(ctx: Context, contact: Contact.BaresipContact): Boolean {
    val ops = ArrayList<ContentProviderOperation>()
    ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build())
    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
        .withValue(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name).build())
    if (contact.email.isNotEmpty()) {
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.Email.ADDRESS, contact.email)
            .withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_HOME).build())
    }
    for (contactUri in contact.uris) {
        val uri = contactUri.uri
        val label = contactUri.label
        val mimeType = if (uri.startsWith("sip:")) CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE else CommonDataKinds.Phone.CONTENT_ITEM_TYPE
        val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, mimeType)
            .withValue(Data.DATA1, uri.substringAfter(":"))
        if (mimeType == CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
            val type = Contact.stringToType(label)
            builder.withValue(CommonDataKinds.Phone.TYPE, type)
            if (type == CommonDataKinds.Phone.TYPE_CUSTOM)
                builder.withValue(CommonDataKinds.Phone.LABEL, label)
        }
        ops.add(builder.build())
    }
    if (contact.avatarImage != null) {
        val photoData = bitmapToPNGByteArray(contact.avatarImage!!)
        if (photoData != null) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Photo.PHOTO, photoData).build())
        }
    }
    try {
        ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    } catch (e: Exception) {
        Log.e(TAG, "Adding of contact ${contact.name} failed: ${e.message}")
        return false
    }
    return true
}

private fun updateAndroidContact(ctx: Context, rawContactId: Long, contact: Contact.BaresipContact) {
    if (contact.email.isNotEmpty()) {
        if (updateAndroidEmail(ctx, rawContactId, contact.email) == 0) addAndroidEmail(ctx, rawContactId, contact.email)
    }
    for (contactUri in contact.uris) if (updateAndroidUri(ctx, rawContactId, contactUri) == 0) addAndroidUri(ctx, rawContactId, contactUri)
    if (updateAndroidPhoto(ctx, rawContactId, contact.avatarImage) == 0) if (contact.avatarImage != null) addAndroidPhoto(ctx, rawContactId, contact.avatarImage!!)
}

private fun addAndroidUri(ctx: Context, rawContactId: Long, contactUri: Contact.ContactUri) {
    val uri = contactUri.uri
    val label = contactUri.label
    val mimeType = if (uri.startsWith("sip:")) CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE else CommonDataKinds.Phone.CONTENT_ITEM_TYPE
    val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValue(Data.RAW_CONTACT_ID, rawContactId)
        .withValue(Data.MIMETYPE, mimeType)
        .withValue(Data.DATA1, uri.substringAfter(":"))
    if (mimeType == CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
        val type = Contact.stringToType(label)
        builder.withValue(CommonDataKinds.Phone.TYPE, type)
        if (type == CommonDataKinds.Phone.TYPE_CUSTOM)
            builder.withValue(CommonDataKinds.Phone.LABEL, label)
    }
    val ops = ArrayList<ContentProviderOperation>()
    ops.add(builder.build())
    try {
        ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    } catch (e: Exception) {
        Log.e(TAG, "Adding of URI $uri failed: ${e.message}")
    }
}

private fun updateAndroidUri(ctx: Context, rawContactId: Long, contactUri: Contact.ContactUri): Int {
    val uri = contactUri.uri
    val label = contactUri.label
    val mimeType = if (uri.startsWith("sip:")) CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE else CommonDataKinds.Phone.CONTENT_ITEM_TYPE
    val contentValues = ContentValues()
    contentValues.put(ContactsContract.Data.DATA1, uri.substringAfter(":"))
    if (mimeType == CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
        val type = Contact.stringToType(label)
        contentValues.put(CommonDataKinds.Phone.TYPE, type)
        if (type == CommonDataKinds.Phone.TYPE_CUSTOM)
            contentValues.put(CommonDataKinds.Phone.LABEL, label)
    }
    val where = "${ContactsContract.Data.RAW_CONTACT_ID}=$rawContactId and ${ContactsContract.Data.MIMETYPE}='$mimeType'"
    return try {
        ctx.contentResolver.update(ContactsContract.Data.CONTENT_URI, contentValues, where, null)
    } catch (e: Exception) {
        Log.e(TAG, "Update of URI $uri failed: ${e.message}")
        0
    }
}

private fun addAndroidPhoto(ctx: Context, rawContactId: Long, photoBits: Bitmap) {
    val photoBytes = bitmapToPNGByteArray(photoBits)
    if (photoBytes != null) {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(Data.RAW_CONTACT_ID, rawContactId)
            .withValue(Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.Photo.PHOTO, photoBytes).build())
        try {
            ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            Log.e(TAG, "Adding of photo failed: ${e.message}")
        }
    }
}

private fun addAndroidEmail(ctx: Context, rawContactId: Long, email: String) {
    val ops = ArrayList<ContentProviderOperation>()
    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValue(Data.RAW_CONTACT_ID, rawContactId)
        .withValue(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
        .withValue(CommonDataKinds.Email.ADDRESS, email)
        .withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_HOME).build())
    try {
        ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    } catch (e: Exception) {
        Log.e(TAG, "Adding of email failed: ${e.message}")
    }
}

private fun updateAndroidEmail(ctx: Context, rawContactId: Long, email: String): Int {
    val contentValues = ContentValues()
    contentValues.put(CommonDataKinds.Email.ADDRESS, email)
    val where = "${ContactsContract.Data.RAW_CONTACT_ID}=$rawContactId and ${ContactsContract.Data.MIMETYPE}='${CommonDataKinds.Email.CONTENT_ITEM_TYPE}'"
    return try {
        ctx.contentResolver.update(ContactsContract.Data.CONTENT_URI, contentValues, where, null)
    } catch (_: Exception) {
        0
    }
}

private fun updateAndroidPhoto(ctx: Context, rawContactId: Long, photoBits: Bitmap?): Int {
    val photoBytes = if (photoBits == null) null else bitmapToPNGByteArray(photoBits)
    val contentValues = ContentValues()
    contentValues.put(CommonDataKinds.Photo.PHOTO, photoBytes)
    val where = "${ContactsContract.Data.RAW_CONTACT_ID}=$rawContactId and ${ContactsContract.Data.MIMETYPE}='${CommonDataKinds.Photo.CONTENT_ITEM_TYPE}'"
    return try {
        ctx.contentResolver.update(ContactsContract.Data.CONTENT_URI, contentValues, where, null)
    } catch (_: Exception) {
        0
    }
}

private fun bitmapToPNGByteArray(bitmap: Bitmap): ByteArray? {
    val size = bitmap.width * bitmap.height * 4
    val out = ByteArrayOutputStream(size)
    return try {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
        out.close()
        out.toByteArray()
    } catch (_: Exception) {
        null
    }
}

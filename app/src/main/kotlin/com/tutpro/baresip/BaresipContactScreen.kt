package com.tutpro.baresip

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil.compose.rememberAsyncImagePainter
import com.tutpro.baresip.CustomElements.AlertDialog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

fun NavGraphBuilder.baresipContactScreenRoute(navController: NavController) {
    composable(
        route = "baresip_contact/{uri_or_name}/{kind}",
        arguments = listOf(
            navArgument("uri_or_name") { type = NavType.StringType },
            navArgument("kind") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val uriOrNameArg = backStackEntry.arguments?.getString("uri_or_name")!!
        val kindArg = backStackEntry.arguments?.getString("kind")!!
        ContactScreen(
            navController = navController,
            uriOrNameArg = uriOrNameArg,
            kindArg = kindArg
        )
    }
}

private data class ScreenState(
    val new: Boolean = false,
    val favorite: Boolean = false,
    val android: Boolean = false,
    val id: Long = 0,
    val newId: Long = 0,
    val name: String = "",
    val uri: String = "",
    val color: Int = 0,
    val avatarImageUri: String? = null,
    val tmpAvatarFile: File? = null,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactScreen(
    navController: NavController,
    uriOrNameArg: String,
    kindArg: String
) {

    val ctx = LocalContext.current

    var screenState by remember { mutableStateOf(ScreenState()) }

    val title = if (screenState.new)
        stringResource(R.string.new_contact)
    else
        uriOrNameArg

    LaunchedEffect(key1 = uriOrNameArg, key2 = kindArg) {
        val isNew = kindArg == "new"
        if (isNew) {
            val time = System.currentTimeMillis()
            screenState = ScreenState(
                new = true,
                name = "",
                uri = uriOrNameArg,
                favorite = false,
                android = BaresipService.contactsMode == "android",
                color = Utils.randomColor(),
                id = time,
                newId = time,
                isLoading = false
            )
        }
        else {
            val contact = Contact.baresipContact(uriOrNameArg)!!
            val avatarFile = File(BaresipService.filesPath, "${contact.id}.png")
            screenState = ScreenState(
                new = false,
                name = uriOrNameArg,
                uri = contact.uri,
                favorite = contact.favorite,
                android = false,
                color = contact.color,
                id = contact.id,
                newId = contact.id,
                avatarImageUri = if (contact.avatarImage != null && avatarFile.exists())
                    Uri.fromFile(avatarFile).toString()
                else
                    null,
                isLoading = false
            )
        }
    }

    val onBack: () -> Unit = {
        screenState.tmpAvatarFile?.let { tempFile ->
            if (tempFile.exists()) {
                Log.d(TAG, "Back pressed, deleting temp avatar: ${tempFile.name}")
                Utils.deleteFile(tempFile)
            }
        }
        navController.popBackStack()
    }

    val onCheck: () -> Unit = {
        val result = checkOnClick(
            ctx = ctx,
            currentState = screenState,
            uriOrNameArg = uriOrNameArg,
        )
        if (result)
            navController.popBackStack()
    }

    BackHandler(enabled = true) {
        onBack()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                TopAppBar(title, onBack = onBack, onCheck = onCheck)
            }
        },
        content = { contentPadding ->
            if (!screenState.isLoading) {
                ContactContent(
                    contentPadding = contentPadding,
                    screenState = screenState,
                    onStateChange = { newState -> screenState = newState }
                )
            } else {
                // Optional: Show a loading indicator
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(title: String, onBack: () -> Unit, onCheck: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
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
            IconButton(onClick = onCheck) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Check"
                )
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
    contentPadding: PaddingValues,
    screenState: ScreenState,
    onStateChange: (ScreenState) -> Unit
) {
    val ctx = LocalContext.current

    if (showAlert.value) {
        AlertDialog(
            showDialog = showAlert,
            title = alertTitle.value,
            message = alertMessage.value,
            positiveButtonText = stringResource(R.string.ok),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 52.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            ctx = ctx,
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
                // User long-clicked to change color, this means discarding any image.
                // The old tempAvatarFile (if any) should be deleted.
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
        ContactName(
            name = screenState.name,
            new = screenState.new,
            onNameChange = { newName -> onStateChange(screenState.copy(name = newName)) }
        )
        ContactUri(
            uri = screenState.uri,
            onUriChange = { newUri -> onStateChange(screenState.copy(uri = newUri)) }
        )
        Favorite(
            ctx = ctx,
            favorite = screenState.favorite,
            onFavoriteChange = {
                newFavorite -> onStateChange(screenState.copy(favorite = newFavorite))
            }
        )
        if (screenState.new && BaresipService.contactsMode == "both")
            Android(
                android = screenState.android,
                onAndroidChange = { newAndroid -> onStateChange(screenState.copy(android = newAndroid)) }
            )
    }
}

@OptIn(ExperimentalFoundationApi::class) 
@Composable
private fun Avatar(
    ctx: Context,
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
                    val inputStream = ctx.contentResolver.openInputStream(uri)
                    val avatarBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (avatarBitmap == null) {
                        Log.e(TAG, "Failed to decode bitmap from URI: $uri")
                        return@rememberLauncherForActivityResult
                    }

                    val scaledBitmap = avatarBitmap.scale(192, 192) // Define desired scale

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
                        Log.e(TAG, "Failed to save processed avatar image to ${tempNewImageFile.absolutePath}")
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
                .combinedClickable(
                    onClick = {
                        avatarImagePicker.launch("image/*")
                    },
                    onLongClick = {
                        onAvatarColorChange(Utils.randomColor())
                    }
                )
        ) {
            if (currentAvatarUri == null) {
                Box(
                    modifier = Modifier.size(avatarSize.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(SolidColor(Color(color)))
                    }
                    val text = if (name.isNotBlank()) name.substring(0, 1).uppercase() else "?"
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
private fun ContactName(name: String, new: Boolean, onNameChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = name,
        placeholder = { Text(stringResource(R.string.contact_name)) },
        onValueChange = onNameChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground
        ),
        label = { Text(stringResource(R.string.contact_name)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Words
        )
    )
    LaunchedEffect(new) {
        if (new)
            focusRequester.requestFocus()
    }
}

@Composable
private fun ContactUri(uri: String, onUriChange: (String) -> Unit) {
    OutlinedTextField(
        value = uri,
        placeholder = { Text(stringResource(R.string.user_domain_or_number)) },
        onValueChange = onUriChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground
        ),
        label = { Text(stringResource(R.string.sip_or_tel_uri)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
}

@Composable
private fun Favorite(ctx: Context, favorite: Boolean, onFavoriteChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = stringResource(R.string.favorite),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    alertTitle.value = ctx.getString(R.string.favorite)
                    alertMessage.value = ctx.getString(R.string.favorite_help)
                    showAlert.value = true
                },
            color = MaterialTheme.colorScheme.onBackground,
        )
        Switch(
            checked = favorite,
            onCheckedChange = onFavoriteChange
        )
    }
}

@Composable
private fun Android(android: Boolean, onAndroidChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = stringResource(R.string.android),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onBackground
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

    var newUri = currentState.uri.filterNot{setOf('-', ' ', '(', ')').contains(it)}
    if (!newUri.startsWith("sip:") && !newUri.startsWith("tel:"))
        newUri = if (Utils.isTelNumber(newUri))
            "tel:$newUri"
        else
            "sip:$newUri"

    if (!Utils.checkUri(newUri)) {
        alertTitle.value = ctx.getString(R.string.notice)
        alertMessage.value = String.format(ctx.getString(R.string.invalid_sip_or_tel_uri), newUri)
        showAlert.value = true
        return false
    }

    var newName = currentState.name.trim()
    if (newName == "") newName = newUri.substringAfter(":")
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
        if (currentState.id != currentState.newId) { // Avatar changed, implying new ID
            // Delete old avatar if it existed for currentState.contactId
            val oldAvatar = File(BaresipService.filesPath, "${currentState.id}.png")
            if (oldAvatar.exists()) Utils.deleteFile(oldAvatar)
            idToUse = currentState.newId // Use the new ID for the contact
        }
        /*val avatarFile = File(BaresipService.filesPath, "$idToUse.png")
        if (!Utils.moveFile(currentState.tempAvatarFile, avatarFile)) {
            Log.e(TAG, "Failed to move tmp avatar file $idToUse.png")
            return "Failed to save avatar"
        }*/
    } else if (currentState.avatarImageUri == null) { // Avatar was explicitly cleared
        val avatarFile = File(BaresipService.filesPath, "$idToUse.png")
        if (avatarFile.exists())
            Utils.deleteFile(avatarFile)
    }

    val contact: Contact.BaresipContact =
        Contact.BaresipContact(
            newName,
            newUri,
            currentState.color,
            idToUse,
            currentState.favorite
        )

    if (currentState.avatarImageUri == null)
        contact.avatarImage = null
    else {
        val imageFilePath = BaresipService.filesPath + "/${idToUse}.png"
        contact.avatarImage = BitmapFactory.decodeFile(imageFilePath)
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
    }
    else {
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
    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
        bitmap.width, bitmap.height, matrix, true)
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
        Log.d(TAG, "Saved bitmap to ${file.absolutePath} of length ${file.length()}")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save bitmap to ${file.absolutePath}: $e")
        return false
    }
    return true
}

private fun addOrUpdateAndroidContact(ctx: Context, contact: Contact.BaresipContact) {
    val projection = arrayOf(ContactsContract.Data.RAW_CONTACT_ID)
    val selection = ContactsContract.Data.MIMETYPE + "='" +
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "' AND " +
            CommonDataKinds.StructuredName.DISPLAY_NAME + "='" + contact.name + "'"
    val c: Cursor? = ctx.contentResolver.query(
        ContactsContract.Data.CONTENT_URI, projection,
        selection, null, null)
    if (c != null && c.moveToFirst()) {
        updateAndroidContact(ctx, c.getLong(0), contact)
    } else {
        addAndroidContact(ctx, contact)
    }
    c?.close()
}

private fun addAndroidContact(ctx: Context, contact: Contact.BaresipContact): Boolean {
    val ops = ArrayList<ContentProviderOperation>()
    ops.add(
        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build())
    ops.add(
        ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
            .build())
    val mimeType = if (contact.uri.startsWith("sip:"))
        CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE
    else
        CommonDataKinds.Phone.CONTENT_ITEM_TYPE
    ops.add(
        ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, mimeType)
            .withValue(Data.DATA1, contact.uri.substringAfter(":"))
            .build())

    if (contact.avatarImage != null) {
        val photoData: ByteArray? = bitmapToPNGByteArray(contact.avatarImage!!)
        if (photoData != null) {
            ops.add(
                ContentProviderOperation
                    .newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Photo.PHOTO, photoData)
                    .build())
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
    if (updateAndroidUri(ctx, rawContactId, contact.uri) == 0)
        addAndroidUri(ctx, rawContactId, contact.uri)
    if (updateAndroidPhoto(ctx, rawContactId, contact.avatarImage) == 0)
        if (contact.avatarImage != null)
            addAndroidPhoto(ctx, rawContactId, contact.avatarImage!!)
}

private fun addAndroidUri(ctx: Context, rawContactId: Long, uri: String) {
    val mimeType = if (uri.startsWith("sip:"))
        CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE
    else
        CommonDataKinds.Phone.CONTENT_ITEM_TYPE
    val ops = ArrayList<ContentProviderOperation>()
    ops.add(
        ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(Data.RAW_CONTACT_ID, rawContactId)
            .withValue(Data.MIMETYPE, mimeType)
            .withValue(Data.DATA1, uri.substringAfter(":"))
            .build())
    try {
        ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    } catch (e: Exception) {
        Log.e(TAG, "Adding of SIP URI $uri failed: ${e.message}")
    }
}

private fun updateAndroidUri(ctx: Context, rawContactId: Long, uri: String): Int {
    val mimeType = if (uri.startsWith("sip:"))
        CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE
    else
        CommonDataKinds.Phone.CONTENT_ITEM_TYPE
    val contentValues = ContentValues()
    contentValues.put(ContactsContract.Data.DATA1, uri)
    val where = "${ContactsContract.Data.RAW_CONTACT_ID}=$rawContactId and " +
            "${ContactsContract.Data.MIMETYPE}='$mimeType'"
    return try {
        ctx.contentResolver.update(ContactsContract.Data.CONTENT_URI, contentValues, where, null)
    }  catch (e: Exception) {
        Log.e(TAG, "Update of Android URI $uri failed: ${e.message}")
        0
    }
}

private fun addAndroidPhoto(ctx: Context, rawContactId: Long, photoBits: Bitmap) {
    val photoBytes = bitmapToPNGByteArray(photoBits)
    if (photoBytes != null) {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, rawContactId)
                .withValue(Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Photo.PHOTO, photoBytes)
                .build())
        try {
            ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            Log.e(TAG, "Adding of Android photo failed: ${e.message}")
        }
    }
}

private fun updateAndroidPhoto(ctx: Context, rawContactId: Long, photoBits: Bitmap?): Int {
    val photoBytes = if (photoBits == null)
        null
    else
        bitmapToPNGByteArray(photoBits)
    val contentValues = ContentValues()
    contentValues.put(CommonDataKinds.Photo.PHOTO, photoBytes)
    val where = "${ContactsContract.Data.RAW_CONTACT_ID}=$rawContactId and " +
            "${ContactsContract.Data.MIMETYPE}='${CommonDataKinds.Photo.CONTENT_ITEM_TYPE}'"
    return try {
        ctx.contentResolver.update(ContactsContract.Data.CONTENT_URI, contentValues, where, null)
    }  catch (e: Exception) {
        Log.e(TAG, "updateAndroidPhoto failed: ${e.message}")
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
    } catch (e: Exception) {
        Log.w(TAG, "Unable to serialize photo: ${e.message}")
        null
    }
}

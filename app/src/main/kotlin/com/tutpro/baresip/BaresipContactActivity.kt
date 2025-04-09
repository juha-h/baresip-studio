package com.tutpro.baresip

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Contacts.Data
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import coil.compose.rememberAsyncImagePainter
import com.tutpro.baresip.CustomElements.Checkbox
import java.io.ByteArrayOutputStream
import java.io.File

class BaresipContactActivity : ComponentActivity() {

    private lateinit var name: String
    private lateinit var uri: String
    private var new = false
    private var favorite = false
    private var android = false
    private var avatarImage: Bitmap? = null
    private var newName = ""
    private var newUri = ""
    private var newFavorite = false
    private var newAndroid = false
    private var uriOrName = ""
    private var color = 0
    private var id: Long = 0
    private var newAvatar = ""
    private var tmpFile: File? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val title: String

        new = intent.getBooleanExtra("new", false)

        if (new) {
            name = ""
            uri = intent.getStringExtra("uri")!!
            favorite = false
            avatarImage = null
            android = BaresipService.contactsMode == "android"
            title = getString(R.string.new_contact)
            uriOrName = uri
            color = Utils.randomColor()
            id = System.currentTimeMillis()
        } else {
            name = intent.getStringExtra("name")!!
            val contact = Contact.baresipContact(name)!!
            uri = contact.uri
            favorite = contact.favorite
            avatarImage = contact.avatarImage
            android = false
            title = name
            uriOrName = name
            color = contact.color
            id = contact.id
        }

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCustomColors.current.background
                ) {
                    ContactScreen(this, title) { goBack() }
                }
            }
        }

        Utils.addActivity("baresip contact,$new,$uriOrName")

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    @Composable
    fun ContactScreen(ctx: Context, title: String, navigateBack: () -> Unit) {
        Scaffold(
            modifier = Modifier.fillMaxHeight().imePadding().safeDrawingPadding(),
            containerColor = LocalCustomColors.current.background,
            topBar = { TopAppBar(ctx, title, navigateBack) },
            content = { contentPadding ->
                ContactContent(ctx, contentPadding)
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopAppBar(ctx: Context, title: String, navigateBack: () -> Unit) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    color = LocalCustomColors.current.light,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.mediumTopAppBarColors(
                containerColor = LocalCustomColors.current.primary
            ),
            navigationIcon = {
                IconButton(onClick = navigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = LocalCustomColors.current.light
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    checkOnClick(ctx)
                }) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        tint = LocalCustomColors.current.light,
                        contentDescription = "Check"
                    )
                }
            }
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ContactContent(ctx: Context, contentPadding: PaddingValues) {

        var avatarType by remember { mutableStateOf(if (avatarImage != null) "image" else "text") }
        var textAvatarText by remember { mutableStateOf("") }
        var textAvatarColor by remember { mutableIntStateOf(0) }
        var imageAvatarUri by remember { mutableStateOf("") }

        val avatarRequest =
            rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                if (it != null)
                    try {
                        val inputStream = baseContext.contentResolver.openInputStream(it)
                        val avatarBitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        val scaledBitmap = avatarBitmap.scale(192, 192)
                        val exif = ExifInterface(baseContext.contentResolver.openInputStream(it)!!)
                        val orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        val rotatedBitmap = rotateBitmap(scaledBitmap, orientation)
                        tmpFile = File(
                            BaresipService.filesPath + "/tmp",
                            "${System.currentTimeMillis()}.png"
                        )
                        if (Utils.saveBitmap(rotatedBitmap, tmpFile!!)) {
                            newAvatar = "image"
                            avatarType = newAvatar
                            imageAvatarUri = Uri.fromFile(tmpFile).toString()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not read avatar image: ${e.message}")
                    }
            }

        @Composable
        fun TextAvatar(size: Int) {
            Box(
                modifier = Modifier.size(size.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(SolidColor(Color(textAvatarColor)))
                }
                textAvatarText = if (name == "") "?" else name[0].toString()
                Text(textAvatarText, fontSize = 72.sp, color = Color.White)
            }
        }

        @Composable
        fun ImageAvatar(size: Int) {
            Image(
                painter = rememberAsyncImagePainter(model = imageAvatarUri),
                contentDescription = stringResource(R.string.avatar_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).clip(CircleShape)
            )
        }

        @Composable
        fun avatar() {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (new)
                    textAvatarColor = color
                else {
                    val avatarImage = avatarImage
                    val avatarFile = File(BaresipService.filesPath, "${id}.png")
                    if (avatarImage != null && (newAvatar == "" || newAvatar == "image")) {
                        imageAvatarUri = if (tmpFile != null && tmpFile!!.exists())
                            Uri.fromFile(tmpFile).toString()
                        else
                            Uri.fromFile(avatarFile).toString()
                    }
                    else {
                        textAvatarText = "${name[0]}"
                        textAvatarColor = color
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(96.dp)
                        .combinedClickable(
                            onClick = {
                                avatarRequest.launch("image/*")
                            },
                            onLongClick = {
                                textAvatarColor = Utils.randomColor()
                                color = textAvatarColor
                                newAvatar = "text"
                                avatarType = newAvatar
                            }
                        ),
                ) {
                    if (avatarType == "text")
                        TextAvatar(96)
                    else
                        ImageAvatar(96)
                }
            }
        }

        @Composable
        fun contactName() {
            val focusRequester = FocusRequester()
            var contactName by remember { mutableStateOf(name) }
            newName = contactName
            OutlinedTextField(
                value = contactName,
                placeholder = { Text(stringResource(R.string.contact_name)) },
                onValueChange = {
                    contactName = it
                    newName = contactName
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText
                ),
                label = { Text(stringResource(R.string.contact_name)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Sentences
                )
            )
            LaunchedEffect(new) {
                if (new)
                    focusRequester.requestFocus()
            }
        }

        @Composable
        fun contactUri() {
            var contactUri by remember { mutableStateOf(uri) }
            newUri = contactUri
            OutlinedTextField(
                value = contactUri,
                placeholder = { Text(stringResource(R.string.user_domain_or_number)) },
                onValueChange = {
                    contactUri = it
                    newUri = contactUri
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp, color = LocalCustomColors.current.itemText
                ),
                label = { Text(stringResource(R.string.sip_or_tel_uri)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }

        @Composable
        fun favorite(ctx: Context) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = stringResource(R.string.favorite),
                    modifier = Modifier.weight(1f)
                        .clickable {
                            Utils.alertView(
                                ctx, getString(R.string.favorite), getString(R.string.favorite_help)
                            )
                        },
                    color = LocalCustomColors.current.itemText,
                )
                var favoriteContact by remember { mutableStateOf(favorite) }
                newFavorite = favoriteContact
                Checkbox(
                    checked = favoriteContact,
                    onCheckedChange = {
                        favoriteContact = it
                        newFavorite = favoriteContact
                    }
                )
            }
        }

        @Composable
        fun android() {
            if (new && BaresipService.contactsMode != "baresip")
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = stringResource(R.string.android),
                        modifier = Modifier.weight(1f),
                        color = LocalCustomColors.current.itemText
                    )
                    var androidContact by remember { mutableStateOf(android) }
                    newAndroid = androidContact
                    Checkbox(
                        checked = androidContact,
                        onCheckedChange = {
                            if (BaresipService.contactsMode != "android") {
                                androidContact = it
                                newAndroid = androidContact
                            }
                        }
                    )
                }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalCustomColors.current.background)
                .padding(contentPadding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 52.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            avatar()
            contactName()
            contactUri()
            favorite(ctx)
            android()
        }
    }

    private fun checkOnClick(ctx: Context) {
        if (BaresipService.activities.indexOf("baresip contact,$new,$uriOrName") == -1)
            return

        newUri = newUri.filterNot{setOf('-', ' ', '(', ')').contains(it)}
        if (!newUri.startsWith("sip:") && !newUri.startsWith("tel:"))
            newUri = if (Utils.isTelNumber(newUri))
                "tel:$newUri"
            else
                "sip:$newUri"
        if (!Utils.checkUri(newUri)) {
            Utils.alertView(
                ctx, getString(R.string.notice),
                String.format(getString(R.string.invalid_sip_or_tel_uri), newUri)
            )
            return
        }

        newName = newName.trim()
        if (newName == "") newName = newUri.substringAfter(":")
        if (!Utils.checkName(newName)) {
            Utils.alertView(
                ctx, getString(R.string.notice),
                String.format(getString(R.string.invalid_contact), newName)
            )
            return
        }

        val alert: Boolean = if (new)
            Contact.nameExists(newName, BaresipService.contacts,true)
        else {
            (uriOrName != newName) && Contact.nameExists(newName, BaresipService.contacts, false)
        }
        if (alert) {
            Utils.alertView(
                ctx, getString(R.string.notice),
                String.format(getString(R.string.contact_already_exists), newName))
            return
        }

        val contact: Contact.BaresipContact

        if (new) {
            contact = Contact.BaresipContact(newName, newUri, color, id, newFavorite)
        } else {
            contact = Contact.baresipContact(name)!!
            contact.uri = newUri
            contact.name = newName
            contact.color = color
            contact.favorite = newFavorite
        }

        when (newAvatar) {
            "text" -> {
                if (contact.avatarImage != null) {
                    contact.avatarImage = null
                    Utils.deleteFile(File(BaresipService.filesPath, "${contact.id}.png"))
                }
            }
            "image" -> {
                val imageFilePath = BaresipService.filesPath + "/${contact.id}.png"
                val imageFile = File(imageFilePath)
                tmpFile!!.copyTo(target = imageFile, overwrite = true)
                contact.avatarImage = BitmapFactory.decodeFile(imageFilePath)
            }
        }


        if (newAndroid)
            addOrUpdateAndroidContact(ctx, contact)
        else {
            if (new)
                Contact.addBaresipContact(contact)
            else
                Contact.updateBaresipContact(contact)
        }

        BaresipService.activities.remove("baresip contact,$new,$uriOrName")

        val i = Intent(ctx, MainActivity::class.java)
        if (newAndroid && contact.favorite)
            i.putExtra("name", newName)
        setResult(RESULT_OK, i)
        finish()
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

    private fun addOrUpdateAndroidContact(ctx: Context, contact: Contact.BaresipContact) {
        val projection = arrayOf(ContactsContract.Data.RAW_CONTACT_ID)
        val selection = ContactsContract.Data.MIMETYPE + "='" +
                CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "' AND " +
                CommonDataKinds.StructuredName.DISPLAY_NAME + "='" + contact.name + "'"
        val c: Cursor? = ctx.contentResolver.query(
            ContactsContract.Data.CONTENT_URI, projection,
            selection, null, null)
        if (c != null && c.moveToFirst()) {
            updateAndroidContact(c.getLong(0), contact)
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

    private fun updateAndroidContact(rawContactId: Long, contact: Contact.BaresipContact) {
        if (updateAndroidUri(rawContactId, contact.uri) == 0)
            addAndroidUri(rawContactId, contact.uri)
        if (updateAndroidPhoto(rawContactId, contact.avatarImage) == 0)
            if (contact.avatarImage != null)
                addAndroidPhoto(rawContactId, contact.avatarImage!!)
    }

    private fun addAndroidUri(rawContactId: Long, uri: String) {
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
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            Log.e(TAG, "Adding of SIP URI $uri failed: ${e.message}")
        }
    }

    private fun updateAndroidUri(rawContactId: Long, uri: String): Int {
        val mimeType = if (uri.startsWith("sip:"))
            CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE
        else
            CommonDataKinds.Phone.CONTENT_ITEM_TYPE
        val contentValues = ContentValues()
        contentValues.put(ContactsContract.Data.DATA1, uri)
        val where = "${ContactsContract.Data.RAW_CONTACT_ID}=$rawContactId and " +
                "${ContactsContract.Data.MIMETYPE}='$mimeType'"
        return try {
            contentResolver.update(ContactsContract.Data.CONTENT_URI, contentValues, where, null)
        }  catch (e: Exception) {
            Log.e(TAG, "Update of Android URI $uri failed: ${e.message}")
            0
        }
    }

    private fun addAndroidPhoto(rawContactId: Long, photoBits: Bitmap) {
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
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                Log.e(TAG, "Adding of Android photo failed: ${e.message}")
            }
        }
    }

    private fun updateAndroidPhoto(rawContactId: Long, photoBits: Bitmap?): Int {
        val photoBytes = if (photoBits == null)
            null
        else
            bitmapToPNGByteArray(photoBits)
        val contentValues = ContentValues()
        contentValues.put(CommonDataKinds.Photo.PHOTO, photoBytes)
        val where = "${ContactsContract.Data.RAW_CONTACT_ID}=$rawContactId and " +
                "${ContactsContract.Data.MIMETYPE}='${CommonDataKinds.Photo.CONTENT_ITEM_TYPE}'"
        return try {
            contentResolver.update(ContactsContract.Data.CONTENT_URI, contentValues, where, null)
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

    private fun goBack() {
        BaresipService.activities.remove("baresip contact,$new,$uriOrName")
        setResult(RESULT_CANCELED, Intent(this, MainActivity::class.java))
        finish()
    }
}

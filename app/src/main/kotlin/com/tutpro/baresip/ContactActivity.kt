package com.tutpro.baresip

import android.app.Activity
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Contacts.Data
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.exifinterface.media.ExifInterface
import com.tutpro.baresip.databinding.ActivityContactBinding
import java.io.ByteArrayOutputStream
import java.io.File

class ContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactBinding
    private lateinit var layout: LinearLayout
    private lateinit var textAvatarView: TextView
    private lateinit var cardAvatarView: CardView
    private lateinit var cardImageAvatarView: ImageView
    private lateinit var nameView: EditText
    private lateinit var uriView: EditText
    private lateinit var androidCheck: CheckBox
    private lateinit var menu: Menu

    private var newContact = false
    private var newAvatar = ""
    private var uOrI = ""

    private var index = 0
    private var color = 0
    private var id: Long = 0

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityContactBinding.inflate(layoutInflater)
        setContentView(binding.root)
        layout = binding.ContactView

        textAvatarView = binding.TextAvatar
        cardAvatarView = binding.CardAvatar
        cardImageAvatarView = binding.ImageAvatar
        nameView = binding.Name
        uriView = binding.Uri
        androidCheck = binding.Android

        newContact = intent.getBooleanExtra("new", false)

        if (newContact) {
            if (BaresipService.contactsMode == "baresip") {
                binding.AndroidTitle.visibility = View.GONE
                androidCheck.visibility = View.GONE
            }
            title = getString(R.string.new_contact)
            color = Utils.randomColor()
            id = System.currentTimeMillis()
            showTextAvatar("?", color)
            nameView.setText("")
            nameView.hint = getString(R.string.contact_name)
            nameView.setSelection(nameView.text.length)
            val uri = intent.getStringExtra("uri")!!
            if (uri == "")
                uriView.setText("")
            else
                uriView.setText(uri)
            uOrI = uri
            if ((BaresipService.contactsMode == "android")) {
                androidCheck.isChecked = true
                androidCheck.isClickable = false
            } else {
                androidCheck.isChecked = false
            }
        } else {
            binding.AndroidTitle.visibility = View.GONE
            androidCheck.visibility = View.GONE
            index = intent.getIntExtra("index", 0)
            val contact = Contact.contacts()[index] as Contact.BaresipContact
            val name = contact.name
            color = contact.color
            id = contact.id
            val avatarImage = contact.avatarImage
            if (avatarImage != null)
                showImageAvatar(avatarImage)
            else
                showTextAvatar(name, color)
            title = name
            nameView.setText(name)
            uriView.setText(contact.uri)
            uOrI = index.toString()
        }

        val avatarRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK) {
                    it.data?.data?.also { uri ->
                        try {
                            val inputStream = baseContext.contentResolver.openInputStream(uri)
                            val avatarBitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                            val scaledBitmap = Bitmap.createScaledBitmap(avatarBitmap, 192, 192, true)
                            val exif = ExifInterface(baseContext.contentResolver.openInputStream(uri)!!)
                            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL)
                            val rotatedBitmap = rotateBitmap(scaledBitmap, orientation)
                            showImageAvatar(rotatedBitmap)
                            if (Utils.saveBitmap(rotatedBitmap, File(BaresipService.filesPath, "tmp.png")))
                                newAvatar = "image"
                        } catch (e: Exception) {
                            Log.e(TAG, "Could not read avatar image: $e")
                        }
                    }
                }
            }

        textAvatarView.setOnClickListener {

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            avatarRequest.launch(intent)

        }

        textAvatarView.setOnLongClickListener {

            color = Utils.randomColor()
            showTextAvatar(textAvatarView.text.toString(), color)
            newAvatar = "text"
            true

        }

        cardAvatarView.setOnClickListener {

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            avatarRequest.launch(intent)

        }

        cardAvatarView.setOnLongClickListener {

            color = Utils.randomColor()
            showTextAvatar(nameView.text.toString(), color)
            newAvatar = "text"
            true

        }

        binding.AndroidTitle.setOnClickListener {
            Utils.alertView(this, getString(R.string.android),
                getString(R.string.android_contact_help))
        }

        // Log.d(TAG, "Android sip contacts ${logAndroidContacts(this, "sip")}")
        // Log.d(TAG, "Android tel contacts ${logAndroidContacts(this, "tel")}")

        Utils.addActivity("contact,$newContact,$uOrI")

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    }

    override fun onCreateOptionsMenu(optionsMenu: Menu): Boolean {

        super.onCreateOptionsMenu(optionsMenu)

        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, optionsMenu)
        menu = optionsMenu
        return true

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("contact,$newContact,$uOrI") == -1) return true

        when (item.itemId) {

            R.id.checkIcon -> {

                var newName = nameView.text.toString().trim()
                var newUri = uriView.text.toString().filterNot{setOf('-', ' ', '(', ')').contains(it)}

                if (newName == "") newName = newUri

                if (!Utils.checkName(newName)) {
                    Utils.alertView(this, getString(R.string.notice),
                            String.format(getString(R.string.invalid_contact), newName))
                    return false
                }

                val alert: Boolean = if (newContact)
                    Contact.nameExists(newName, true)
                else {
                    val c = Contact.contacts()[index] as Contact.BaresipContact
                    (c.name != newName) && Contact.nameExists(newName, false)
                }
                if (alert) {
                    Utils.alertView(this, getString(R.string.notice),
                            String.format(getString(R.string.contact_already_exists), newName))
                    return false
                }

                if (!newUri.startsWith("sip:") && !newUri.startsWith("tel:"))
                    newUri = if (Utils.isTelNumber(newUri))
                        "tel:$newUri"
                    else
                        "sip:$newUri"
                if (!Utils.checkUri(newUri)) {
                    Utils.alertView(this, getString(R.string.notice),
                            String.format(getString(R.string.invalid_sip_or_tel_uri), newUri))
                    return false
                }

                val contact: Contact.BaresipContact

                if (newContact) {
                    if (Contact.contacts().size >= Contact.CONTACTS_SIZE) {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.contacts_exceeded),
                                        Contact.CONTACTS_SIZE))
                        BaresipService.activities.removeAt(0)
                        return true
                    } else {
                        contact = Contact.BaresipContact(newName, newUri, color, id)
                    }
                } else {
                    contact = Contact.contacts()[index] as Contact.BaresipContact
                    contact.uri = newUri
                    contact.name = newName
                    contact.color = color
                }

                when (newAvatar) {
                    "text" -> {
                        if (contact.avatarImage != null) {
                            contact.avatarImage = null
                            Utils.deleteFile(File(BaresipService.filesPath, "${contact.id}.png"))
                        }
                    }
                    "image" -> {
                        contact.avatarImage = (cardImageAvatarView.drawable as BitmapDrawable).bitmap
                        Utils.deleteFile(File(BaresipService.filesPath, "${contact.id}.png"))
                        File(BaresipService.filesPath, "tmp.png")
                                .renameTo(File(BaresipService.filesPath, "${contact.id}.png"))
                    }
                }

                if (androidCheck.isChecked) {
                    addOrUpdateAndroidContact(this, contact)
                } else {
                    if (newContact)
                        Contact.addBaresipContact(contact)
                    Contact.saveBaresipContacts()
                    Contact.contactsUpdate()
                }

                BaresipService.activities.remove("contact,$newContact,$uOrI")

                val i = Intent(this, MainActivity::class.java)
                i.putExtra("name", newName)
                setResult(Activity.RESULT_OK, i)
                finish()
            }

            android.R.id.home -> {
                goBack()
            }

        }

        return true

    }

    private fun goBack() {
        BaresipService.activities.remove("contact,$newContact,$uOrI")
        setResult(Activity.RESULT_CANCELED, Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showTextAvatar(name: String, color: Int) {
        textAvatarView.visibility = View.VISIBLE
        cardAvatarView.visibility = View.GONE
        textAvatarView.background.setTint(color)
        textAvatarView.text = "${name[0]}"
    }

    private fun showImageAvatar(image: Bitmap) {
        textAvatarView.visibility = View.GONE
        cardAvatarView.visibility = View.VISIBLE
        cardImageAvatarView.setImageBitmap(image)
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
        val c: Cursor? = ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection,
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
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build())
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                .build())
        val mimeType = if (contact.uri.startsWith("sip:"))
            CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE
        else
            CommonDataKinds.Phone.CONTENT_ITEM_TYPE
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, mimeType)
                .withValue(Data.DATA1, contact.uri.substringAfter(":"))
                .build())
        if (contact.avatarImage != null) {
            val photoData: ByteArray? = bitmapToPNGByteArray(contact.avatarImage!!)
            if (photoData != null) {
                ops.add(ContentProviderOperation
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
            Log.e(TAG, "Adding of contact ${contact.name} failed")
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
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, rawContactId)
                .withValue(Data.MIMETYPE, mimeType)
                .withValue(Data.DATA1, uri.substringAfter(":"))
                .build())
        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            Log.e(TAG, "Adding of SIP URI $uri failed")
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
            Log.e(TAG, "Adding of SIP URI $uri failed")
            0
        }
    }

    private fun addAndroidPhoto(rawContactId: Long, photoBits: Bitmap) {
        val photoBytes = bitmapToPNGByteArray(photoBits)
        if (photoBytes != null) {
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(ContentProviderOperation
                    .newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Photo.PHOTO, photoBytes)
                    .build())
            try {
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                Log.e(TAG, "Adding of Android photo failed")
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
            Log.e(TAG, "updateAndroidPhoto failed")
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
            Log.w(TAG, "Unable to serialize photo: $e")
            null
        }
    }

}

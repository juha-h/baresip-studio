package com.tutpro.baresip

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.net.toUri
import java.io.File
import java.util.ArrayList

sealed class Contact {

    class BaresipContact(var name: String, var uri: String, var color: Int, val id: Long): Contact() {
        var avatarImage: Bitmap? = null
    }

    class AndroidContact(var name: String, var color: Int, var thumbnailUri: Uri?): Contact() {
        val uris = ArrayList<String>()
    }

    companion object {

        const val CONTACTS_SIZE = 256

        fun contacts(): ArrayList<Contact> {
            return BaresipService.contacts
        }

        fun contactNames(): ArrayList<String> {
            return BaresipService.contactNames
        }

        // Return contact name of uri or uri itself if contact with uri is not found
        fun contactName(uri: String): String {
            val userPart = Utils.uriUserPart(uri)
            val contact = if (Utils.isTelNumber(userPart))
                findContact("tel:$userPart")
            else
                findContact(uri)
            if (contact != null) {
                return when (contact) {
                    is BaresipContact ->
                        contact.name
                    is AndroidContact ->
                        contact.name
                }
            }
            return uri
        }

        // Return uri of contact name or null if contact is not found
        fun contactUri(name: String): String? {
            for (c in contacts())
                when (c) {
                    is BaresipContact -> {
                        if (c.name.equals(name, ignoreCase = true))
                            return c.uri.removePrefix("<")
                                    .replaceAfter(">", "")
                                    .replace(">", "")
                    }
                    is AndroidContact -> {
                        if (c.name == name) {
                            return if (c.uris.isNotEmpty())
                                c.uris.first()
                            else
                                null
                        }
                    }
                }
            return null
        }

        fun findContact(uri: String): Contact? {
            for (c in contacts())
                when (c) {
                    is BaresipContact -> {
                        if (Utils.uriMatch(c.uri, uri))
                            return c
                    }
                    is AndroidContact -> {
                        for (u in c.uris)
                            if (Utils.uriMatch(u, uri))
                                return c
                    }
                }
            return null
        }

        fun nameExists(name: String, ignoreCase: Boolean): Boolean {
            for (c in BaresipService.contacts)
                when (c) {
                    is BaresipContact ->
                        if (c.name.equals(name, ignoreCase = ignoreCase))
                            return true
                    is AndroidContact ->
                        if (c.name.equals(name, ignoreCase = ignoreCase))
                            return true
                }
            return false
        }

        fun saveBaresipContacts() {
            var contents = ""
            for (c in BaresipService.baresipContacts)
                contents += "\"${c.name}\" <${c.uri}>;id=${c.id};color=${c.color}\n"
            Utils.putFileContents(BaresipService.filesPath + "/contacts",
                    contents.toByteArray())
        }

        fun loadAndroidContacts(ctx: Context) {
            val projection = arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME,
                    ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1,
                    ContactsContract.Data.PHOTO_THUMBNAIL_URI)
            val selection =
                    ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE + "' OR " +
                            ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'"
            val cur: Cursor? = ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection,
                    selection, null, null)
            BaresipService.androidContacts.clear()
            val contacts = HashMap<Long, AndroidContact>()
            while (cur != null && cur.moveToNext()) {
                val id = cur.getLong(0)
                val name = cur.getString(1) ?: ""
                val mime = cur.getString(2)
                val data = cur.getString(3)
                val thumb = cur.getString(4)?.toUri()
                val contact = if (contacts.containsKey(id))
                    contacts[id]!!
                else
                    AndroidContact(name, Utils.randomColor(), thumb)
                if (contact.name == "" && name != "")
                    contact.name = name
                if (contact.thumbnailUri == null &&  thumb != null)
                    contact.thumbnailUri = thumb
                if (mime == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    contact.uris.add("tel:${data.filterNot{setOf('-', ' ').contains(it)}}")
                else if (mime == ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
                    contact.uris.add("sip:$data")
                else
                    continue
                if (!contacts.containsKey(id))
                    contacts[id] = contact
            }
            cur?.close()
            for ((_, value) in contacts)
                if (value.name != "" && value.uris.isNotEmpty())
                    BaresipService.androidContacts.add(value)
        }

        fun restoreBaresipContacts(): Boolean {
            val content = Utils.getFileContents(BaresipService.filesPath + "/contacts")
                    ?: return false
            val contacts = String(content)
            var contactNo = 0
            val baseId = System.currentTimeMillis()
            BaresipService.baresipContacts.clear()
            contacts.lines().forEach {
                val parts = it.split("\"")
                if (parts.size == 3) {
                    contactNo++
                    val name = parts[1]
                    val uriParams = parts[2].trim()
                    val uri = uriParams.substringAfter("<").substringBefore(">")
                    val params = uriParams.substringAfter(">;")
                    val colorValue = Utils.paramValue(params, "color" )
                    val color: Int = if (colorValue != "")
                        colorValue.toInt()
                    else
                        Utils.randomColor()
                    val idValue = Utils.paramValue(params, "id" )
                    val id: Long = if (idValue != "")
                        idValue.toLong()
                    else
                        baseId + contactNo
                    Log.d(TAG, "Restoring contact $name, $uri, $color, $id")
                    val contact = BaresipContact(name, uri, color, id)
                    val avatarFilePath = BaresipService.filesPath + "/$id.png"
                    if (File(avatarFilePath).exists()) {
                        try {
                            contact.avatarImage = BitmapFactory.decodeFile(avatarFilePath)
                            Log.d(TAG, "Set avatarImage")
                            if (contact.avatarImage == null)
                                Log.d(TAG, "Contact $id avatarImage is null")
                        } catch (e: Exception) {
                            Log.e(TAG, "Could not read avatar image from '$id.img")
                        }
                    }
                    BaresipService.baresipContacts.add(contact)
                }
            }
            return true
        }

        fun contactsUpdate() {
            BaresipService.contacts.clear()
            if (BaresipService.contactsMode != "android")
                BaresipService.contacts.addAll(BaresipService.baresipContacts)
            if (BaresipService.contactsMode != "baresip")
                addAndroidContacts()
            sortContacts()
            generateContactNames()
            BaresipService.contactUpdate.postValue(System.nanoTime())
        }

        fun addBaresipContact(contact: BaresipContact) {
            BaresipService.baresipContacts.add(contact)
        }

        fun removeBaresipContact(contact: BaresipContact) {
            BaresipService.baresipContacts.remove(contact)
            saveBaresipContacts()
        }

        private fun generateContactNames () {
            BaresipService.contactNames.clear()
            for (c in BaresipService.contacts)
                when (c) {
                    is BaresipContact ->
                        BaresipService.contactNames.add(c.name)
                    is AndroidContact ->
                        if (c.uris.size == 1)
                            BaresipService.contactNames.add(c.name)
                }
        }

        private fun addAndroidContacts() {
            for (c in BaresipService.androidContacts)
                if (!nameExists(c.name, true))
                    BaresipService.contacts.add(c)
        }

        private fun sortContacts() {
            BaresipService.contacts.sortBy{ when (it) {
                is BaresipContact -> it.name
                is AndroidContact -> it.name
            }}
        }

    }
}

package com.tutpro.baresip.plus

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.net.toUri
import com.tutpro.baresip.plus.BaresipService.Companion.contactNames
import java.io.File
import java.util.ArrayList

sealed class Contact {

    class BaresipContact(var name: String, var uri: String, var color: Int, var id: Long,
                         var favorite: Boolean): Contact() {
        var avatarImage: Bitmap? = null
    }

    class AndroidContact(var name: String, var color: Int, var thumbnailUri: Uri?,
                         var id: Long, var favorite: Boolean): Contact() {
        val uris = ArrayList<String>()
    }

    fun name(): String {
        return when (this) {
            is AndroidContact -> name
            is BaresipContact -> name
        }
    }

    fun id(): Long {
        return when (this) {
            is AndroidContact -> id
            is BaresipContact -> id
        }
    }

    fun favorite(): Boolean {
        return when (this) {
            is AndroidContact -> favorite
            is BaresipContact -> favorite
        }
    }

    fun color(): String {
        val intColor = when (this) {
            is AndroidContact -> color
            is BaresipContact -> color
        }
        // Mask with 0xFFFFFF to ensure we get the standard 6-character hex code (RRGGBB)
        // ignoring the alpha channel for compatibility with simple string parsers.
        return String.format("#%06X", 0xFFFFFF and intColor)
    }

    fun copy(): Contact {
        val copy = when (this) {
            is BaresipContact ->
                BaresipContact(name, uri, color, id, favorite)
            is AndroidContact ->
                AndroidContact(name, color, thumbnailUri, id, favorite)
        }
        when (this) {
            is BaresipContact ->
                (copy as BaresipContact).avatarImage = this.avatarImage
            is AndroidContact ->
                (copy as AndroidContact).uris.addAll(this.uris)
        }
        return copy
    }

    companion object {

        // Return contact name of uri or uri itself if contact with uri is not found
        fun contactName(uri: String): String {
            var contact = findContact(uri)
            if (contact == null) {
                val userPart = Utils.uriUserPart(uri)
                if (Utils.isTelNumber(userPart))
                    contact = findContact("tel:$userPart")
            }
            if (contact != null)
                return contact.name()
            return uri
        }

        fun baresipContact(name: String): BaresipContact? {
            for (c in BaresipService.baresipContacts.value)
                if (c.name == name)
                    return c
            return null
        }

        fun androidContact(name: String): AndroidContact? {
            for (c in BaresipService.androidContacts.value)
                if (c.name == name)
                    return c
            return null
        }

        // Return URIs of contact name
        fun contactUris(name: String): ArrayList<String> {
            val uris = ArrayList<String>()
            for (c in BaresipService.contacts)
                when (c) {
                    is BaresipContact -> {
                        if (c.name.equals(name, ignoreCase = true)) {
                            uris.add(c.uri.removePrefix("<")
                                .replaceAfter(">", "")
                                .replace(">", ""))
                            return uris
                        }
                    }
                    is AndroidContact -> {
                        if (c.name == name) {
                            for (u in c.uris)
                                uris.add(u)
                            return uris
                        }
                    }
                }
            return uris
        }

        fun findContact(uri: String): Contact? {
            for (c in BaresipService.contacts)
                when (c) {
                    is BaresipContact -> {
                        if (Utils.uriMatch(c.uri, uri))
                            return c
                    }
                    is AndroidContact -> {
                        val cleanUri = uri.filterNot{setOf('-', ' ', '(', ')').contains(it)}
                        for (u in c.uris)
                            if (Utils.uriMatch(u.filterNot{setOf('-', ' ', '(', ')').contains(it)},
                                    cleanUri))
                                return c
                    }
                }
            return null
        }

        fun nameExists(name: String, list: List<Contact>, ignoreCase: Boolean): Boolean {
            for (c in list)
                if (c.name().equals(name, ignoreCase = ignoreCase))
                    return true
            return false
        }

        fun saveBaresipContacts() {
            val avatarFiles = avatarFileNames()
            var contents = ""
            for (c in BaresipService.baresipContacts.value) {
                contents += "\"${c.name}\" <${c.uri}>;id=${c.id};color=${c.color}" +
                        ";favorite=${if (c.favorite) "yes" else "no"}\n"
                avatarFiles.remove(c.id.toString() + ".png")
            }
            Utils.putFileContents(BaresipService.filesPath + "/contacts",
                    contents.toByteArray())
            for (f in avatarFiles)
                File(BaresipService.filesPath + "/" + f).delete()
        }

        fun loadAndroidContacts(ctx: Context) {
            // If phone type is needed, add DATA2 to projection. Then phone type can be get from
            // cursor using getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE
            val projection = arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1,
                /* ContactsContract.Data.DATA2 ,*/ ContactsContract.Data.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED)
            val selection =
                ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE + "' OR " +
                        ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'"
            val cur: Cursor? = ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection,
                    selection, null, null)
            BaresipService.androidContacts.value = listOf()
            val contacts = HashMap<Long, AndroidContact>()
            while (cur != null && cur.moveToNext()) {
                val id = cur.getLong(0)
                val name = cur.getString(1) ?: ""
                val mime = cur.getString(2)
                val data = cur.getString(3)
                val thumb = cur.getString(4)?.toUri()
                val starred = cur.getInt(5)
                val contact = if (contacts.containsKey(id))
                    contacts[id]!!
                else
                    AndroidContact(name, Utils.randomColor(), thumb, id, starred == 1)
                if (contact.name == "" && name != "")
                    contact.name = name
                if (contact.thumbnailUri == null &&  thumb != null)
                    contact.thumbnailUri = thumb
                if (mime == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                    val uri = "tel:${data.filterNot { setOf('-', ' ', '(', ')').contains(it) }}"
                    if (uri !in contact.uris)
                        contact.uris.add(uri)
                    // contact.types.add(typeToString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)))
                }
                else if (mime == ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
                    contact.uris.add("sip:$data")
                else
                    continue
                if (!contacts.containsKey(id))
                    contacts[id] = contact
            }
            cur?.close()
            val newList = mutableListOf<AndroidContact>()
            for ((_, value) in contacts)
                if (value.name != "" && value.uris.isNotEmpty())
                    newList.add(value)
            BaresipService.androidContacts.value = newList.toList()
        }

        @Suppress("unused")
        private fun typeToString(type: Int): String {
            return when(type) {
                ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                else -> "Unknown"
            }
        }

        fun restoreBaresipContacts(): Boolean {
            val content = Utils.getFileContents(BaresipService.filesPath + "/contacts")
                    ?: return false
            val contacts = String(content)
            var contactNo = 0
            val baseId = System.currentTimeMillis()
            BaresipService.baresipContacts.value = mutableListOf()
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
                    val favorite = Utils.paramValue(params, "favorite" ) == "yes"
                    Log.d(TAG, "Restoring contact $name, $uri, $color, $id")
                    val contact = BaresipContact(name, uri, color, id, favorite)
                    val avatarFilePath = BaresipService.filesPath + "/$id.png"
                    if (File(avatarFilePath).exists()) {
                        try {
                            contact.avatarImage = BitmapFactory.decodeFile(avatarFilePath)
                            Log.d(TAG, "Set avatarImage")
                            if (contact.avatarImage == null)
                                Log.d(TAG, "Contact $id avatarImage is null")
                        } catch (e: Exception) {
                            Log.e(TAG, "Could not read avatar image from file $id.png: ${e.message}")
                        }
                    }
                    BaresipService.baresipContacts.value += contact
                }
            }
            return true
        }

        fun contactsUpdate() {
            BaresipService.contacts = mutableListOf()
            if (BaresipService.contactsMode != "android")
                for (c in BaresipService.baresipContacts.value)
                    BaresipService.contacts.add(c.copy())
            if (BaresipService.contactsMode != "baresip")
                for (c in BaresipService.androidContacts.value)
                    if (!nameExists(c.name, BaresipService.contacts, true))
                        BaresipService.contacts.add(c.copy())
            BaresipService.contacts.sortBy{ when (it) {
                is BaresipContact -> if (it.favorite) "0" + it.name else "1" + it.name
                is AndroidContact -> if (it.favorite) "0" + it.name else "1" + it.name
            }}
            generateContactNames()
        }

        fun addBaresipContact(contact: BaresipContact) {
            BaresipService.baresipContacts.value += contact
            saveBaresipContacts()
            contactsUpdate()
        }

        fun updateBaresipContact(id: Long, contact: BaresipContact) {
            val updatedContacts = BaresipService.baresipContacts.value.toMutableList()
            updatedContacts.removeIf { it.id == id }
            updatedContacts.add(contact)
            BaresipService.baresipContacts.value = updatedContacts.toList()
            saveBaresipContacts()
            contactsUpdate()
        }

        fun removeBaresipContact(contact: BaresipContact) {
            val removed = BaresipService.baresipContacts.value.toMutableList()
            removed.removeIf { it.id == contact.id }
            BaresipService.baresipContacts.value = removed.toList()
            saveBaresipContacts()
            contactsUpdate()
        }

        private fun generateContactNames () {
            val newList = mutableListOf<String>()
            for (c in BaresipService.contacts)
                when (c) {
                    is BaresipContact ->
                        newList.add(c.name)
                    is AndroidContact ->
                        newList.add(c.name)
                }
            contactNames.value = newList.toList()
        }

        private fun avatarFileNames(): MutableList<String> {
            return File(BaresipService.filesPath).list()!!.filter{ it.endsWith(".png")}.toMutableList()
        }
    }
}

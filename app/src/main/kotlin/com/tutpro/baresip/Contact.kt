package com.tutpro.baresip

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.net.toUri
import com.tutpro.baresip.BaresipService.Companion.contactNames
import java.io.File
import java.util.ArrayList

sealed class Contact {

    data class ContactUri(var uri: String, var label: String = "")

    abstract fun name(): String
    abstract fun id(): Long
    abstract fun favorite(): Boolean
    abstract fun colorInt(): Int
    abstract fun uris(): List<ContactUri>
    abstract fun email(): String

    class BaresipContact(var name: String, val uris: ArrayList<ContactUri>, var email: String, var color: Int,
                         var id: Long, var favorite: Boolean): Contact() {
        var avatarImage: Bitmap? = null
        override fun name() = name
        override fun id() = id
        override fun favorite() = favorite
        override fun colorInt() = color
        override fun uris() = uris
        override fun email() = email
    }

    class AndroidContact(var name: String, val uris: ArrayList<ContactUri>, var email: String, var color: Int,
                         var thumbnailUri: Uri?, var id: Long, var favorite: Boolean): Contact() {
        override fun name() = name
        override fun id() = id
        override fun favorite() = favorite
        override fun colorInt() = color
        override fun uris() = uris
        override fun email() = email
    }

    fun color(): String {
        // Mask with 0xFFFFFF to ensure we get the standard 6-character hex code (RRGGBB)
        // ignoring the alpha channel for compatibility with simple string parsers.
        return String.format("#%06X", 0xFFFFFF and colorInt())
    }

    fun copy(): Contact {
        val copy = when (this) {
            is BaresipContact ->
                BaresipContact(name, ArrayList(uris), email, color, id, favorite)
            is AndroidContact ->
                AndroidContact(name, ArrayList(uris), email, color, thumbnailUri, id, favorite)
        }
        if (this is BaresipContact)
            (copy as BaresipContact).avatarImage = this.avatarImage
        return copy
    }

    companion object {

        // Return contact name of uri or uri itself if contact with uri is not found
        fun contactName(uri: String): String {
            var contact = findContact(uri)
            if (contact == null) {
                val userPart = Utils.uriUserPart(uri).replace("%23", "#")
                if (Utils.isTelNumber(userPart)) {
                    contact = findContact("tel:$userPart")
                }
            }
            if (contact != null)
                return contact.name()
            return uri
        }

        fun baresipContact(name: String): BaresipContact? {
            synchronized(BaresipService.baresipContacts) {
                for (c in BaresipService.baresipContacts.value)
                    if (c.name == name)
                        return c
            }
            return null
        }

        fun androidContact(name: String): AndroidContact? {
            synchronized(BaresipService.androidContacts) {
                for (c in BaresipService.androidContacts.value)
                    if (c.name == name)
                        return c
            }
            return null
        }

        fun contactUris(name: String, tel: Boolean = false): ArrayList<String> {
            val uris = ArrayList<String>()
            synchronized(BaresipService.contacts) {
                for (c in BaresipService.contacts)
                    when (c) {
                        is BaresipContact -> {
                            if (c.name.equals(name, ignoreCase = true)) {
                                for (u in c.uris) {
                                    if (tel && !u.uri.startsWith("tel:"))
                                        continue
                                    uris.add(u.uri)
                                }
                                return uris
                            }
                        }
                        is AndroidContact -> {
                            if (c.name == name) {
                                for (u in c.uris) {
                                    if (tel && !u.uri.startsWith("tel:"))
                                        continue
                                    uris.add(u.uri)
                                }
                                return uris
                            }
                        }
                    }
            }
            return uris
        }

        fun findContact(uri: String): Contact? {
            synchronized(BaresipService.contacts) {
                for (c in BaresipService.contacts)
                    when (c) {
                        is BaresipContact -> {
                            for (u in c.uris)
                                if (Utils.uriMatch(u.uri, uri))
                                    return c
                        }
                        is AndroidContact -> {
                            val cleanUri = uri.filterNot { setOf('-', ' ', '(', ')').contains(it) }
                            for (u in c.uris)
                                if (Utils.uriMatch(
                                        u.uri.filterNot { setOf('-', ' ', '(', ')').contains(it) },
                                        cleanUri
                                    )
                                )
                                    return c
                        }
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
            val contacts = synchronized(BaresipService.baresipContacts) {
                BaresipService.baresipContacts.value.toList()
            }
            for (c in contacts) {
                val uris = c.uris.joinToString(",") {
                    if (it.label.isNotEmpty()) "${it.uri}[${it.label}]" else it.uri
                }
                contents += "\"${c.name}\" <$uris>;email=${c.email};id=${c.id}" +
                        ";color=${c.color};favorite=${if (c.favorite) "yes" else "no"}\n"
                avatarFiles.remove(c.id.toString() + ".png")
            }
            Utils.putFileContents(BaresipService.filesPath + "/contacts",
                    contents.toByteArray())
            for (f in avatarFiles)
                File(BaresipService.filesPath + "/" + f).delete()
        }

        fun loadAndroidContacts(ctx: Context) {
            // If phone type is needed, add DATA2 to projection. Then phone type can be got from
            // cursor using getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val projection = arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2, ContactsContract.Data.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED)
            val selection =
                ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE + "' OR " +
                        ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "' OR " +
                        ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'"
            val cur: Cursor? = ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection,
                    selection, null, null)
            BaresipService.androidContacts.value = listOf()
            val contacts = HashMap<Long, AndroidContact>()
            while (cur != null && cur.moveToNext()) {
                val id = cur.getLong(0)
                val name = cur.getString(1) ?: ""
                val mime = cur.getString(2)
                val data = cur.getString(3) ?: continue
                val type = cur.getInt(4)
                val thumb = cur.getString(5)?.toUri()
                val starred = cur.getInt(6)
                val contact = if (contacts.containsKey(id))
                    contacts[id]!!
                else
                    AndroidContact(name, ArrayList<ContactUri>(), "", Utils.randomColor(), thumb, id, starred == 1)
                if (contact.name == "" && name != "")
                    contact.name = name
                if (contact.thumbnailUri == null &&  thumb != null)
                    contact.thumbnailUri = thumb
                when (mime) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val uri = "tel:${data.filterNot { setOf('-', ' ', '(', ')').contains(it) }}"
                        val label = typeToString(type)
                        if (contact.uris.none { it.uri == uri })
                            contact.uris.add(ContactUri(uri, label))
                    }
                    ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE -> {
                        val uri = "sip:$data"
                        if (contact.uris.none { it.uri == uri })
                            contact.uris.add(ContactUri(uri, ""))
                    }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        if (contact.email == "")
                            contact.email = data
                    }
                }
                if (!contacts.containsKey(id))
                    contacts[id] = contact
            }
            cur?.close()
            val newList = mutableListOf<AndroidContact>()
            for ((_, value) in contacts)
                if (value.name != "" && (value.uris.isNotEmpty() || value.email != ""))
                    newList.add(value)
            BaresipService.androidContacts.value = newList.toList()
        }

        private fun typeToString(type: Int): String {
            return when(type) {
                ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                else -> ""
            }
        }

        fun stringToType(label: String): Int {
            return when (label) {
                "Home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                "Mobile" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                "Work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                "" -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                else -> ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM
            }
        }

        fun restoreBaresipContacts(): Boolean {
            val content = Utils.getFileContents(BaresipService.filesPath + "/contacts")
                    ?: return false
            val contacts = String(content)
            var contactNo = 0
            val baseId = System.currentTimeMillis()
            val newBaresipContacts = mutableListOf<BaresipContact>()
            contacts.lines().forEach {
                val parts = it.split("\"")
                if (parts.size == 3) {
                    contactNo++
                    val name = parts[1]
                    val uriParams = parts[2].trim()
                    val urisPart = uriParams.substringAfter("<").substringBefore(">")
                    val uris = ArrayList<ContactUri>()
                    for (uPart in urisPart.split(",")) {
                        if (uPart.isEmpty()) continue
                        if (uPart.contains("[") && uPart.contains("]")) {
                            val uri = uPart.substringBefore("[")
                            val label = uPart.substringAfter("[").substringBefore("]")
                            uris.add(ContactUri(uri, label))
                        } else {
                            uris.add(ContactUri(uPart, ""))
                        }
                    }
                    val params = uriParams.substringAfter(">;")
                    val email = Utils.paramValue(params, "email")
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
                    // Log.d(TAG, "Restoring contact $name, $urisPart, $color, $id")
                    val contact = BaresipContact(name, uris, email, color, id, favorite)
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
                    newBaresipContacts.add(contact)
                }
            }
            BaresipService.baresipContacts.value = newBaresipContacts.toList()
            return true
        }

        fun contactsUpdate() {
            val newContacts = mutableListOf<Contact>()
            if (BaresipService.contactsMode != "android") {
                val baresipContacts = synchronized(BaresipService.baresipContacts) {
                    BaresipService.baresipContacts.value.toList()
                }
                for (c in baresipContacts)
                    newContacts.add(c.copy())
            }
            if (BaresipService.contactsMode != "baresip") {
                val androidContacts = synchronized(BaresipService.androidContacts) {
                    BaresipService.androidContacts.value.toList()
                }
                for (c in androidContacts)
                    if (!nameExists(c.name, newContacts, true))
                        newContacts.add(c.copy())
            }
            newContacts.sortBy {
                when (it) {
                    is BaresipContact -> if (it.favorite) "0" + it.name else "1" + it.name
                    is AndroidContact -> if (it.favorite) "0" + it.name else "1" + it.name
                }
            }
            BaresipService.contacts = newContacts
            generateContactNames()
        }

        fun addBaresipContact(contact: BaresipContact) {
            synchronized(BaresipService.baresipContacts) {
                BaresipService.baresipContacts.value += contact
            }
            saveBaresipContacts()
            contactsUpdate()
        }

        fun updateBaresipContact(id: Long, contact: BaresipContact) {
            synchronized(BaresipService.baresipContacts) {
                val updatedContacts = BaresipService.baresipContacts.value.toMutableList()
                updatedContacts.removeIf { it.id == id }
                updatedContacts.add(contact)
                BaresipService.baresipContacts.value = updatedContacts.toList()
            }
            saveBaresipContacts()
            contactsUpdate()
        }

        fun removeBaresipContact(contact: BaresipContact) {
            synchronized(BaresipService.baresipContacts) {
                val removed = BaresipService.baresipContacts.value.toMutableList()
                removed.removeIf { it.id == contact.id }
                BaresipService.baresipContacts.value = removed.toList()
            }
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

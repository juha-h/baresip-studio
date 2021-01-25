package com.tutpro.baresip

import android.graphics.Bitmap
import android.graphics.BitmapFactory

import java.io.File
import java.util.ArrayList

class Contact(var name: String, var uri: String, var color: Int, val id: Long) {

    var avatarImage: Bitmap? = null
    var androidContact = false

    companion object {

        const val CONTACTS_SIZE = 100

        fun contacts(): ArrayList<Contact> {
            return BaresipService.contacts
        }

        fun save(): Boolean {
            var contents = ""
            for (c in BaresipService.contacts) contents +=
                    "\"${c.name}\" <${c.uri}>;id=${c.id};color=${c.color};android=${c.androidContact}\n"
            return Utils.putFileContents(BaresipService.filesPath + "/contacts",
                    contents.toByteArray())
        }

        fun restore(): Boolean {
            val content = Utils.getFileContents(BaresipService.filesPath + "/contacts")
            if (content == null) return false
            val contacts = String(content)
            Api.contacts_remove()
            BaresipService.contacts.clear()
            var contactNo = 0
            val baseId = System.currentTimeMillis()
            contacts.lines().forEach {
                val parts = it.split("\"")
                if (parts.size == 3) {
                    contactNo++
                    val name = parts[1]
                    val uriParams = parts[2].trim()
                    val uri = uriParams.substringAfter("<").substringBefore(">")
                    val params = uriParams.substringAfter(">;")
                    val colorValue = Utils.paramValue(params, "color" )
                    val color: Int
                    if (colorValue != "")
                        color = colorValue.toInt()
                    else
                        color = Utils.randomColor()
                    val androidContact = Utils.paramValue(params, "android" ) == "true"
                    val idValue = Utils.paramValue(params, "id" )
                    val id: Long
                    if (idValue != "")
                        id = idValue.toLong()
                    else
                        id = baseId + contactNo
                    Log.d("Baresip", "Restoring contact $name, $uri, $color, $id, $androidContact")
                    val contact = Contact(name, uri, color, id)
                    contact.androidContact = androidContact
                    val avatarFilePath = BaresipService.filesPath + "/$id.png"
                    if (File(avatarFilePath).exists()) {
                        try {
                            contact.avatarImage = BitmapFactory.decodeFile(avatarFilePath)
                            Log.d("Baresip", "Set avatarImage")
                            if (contact.avatarImage == null)
                                Log.d("Baresip", "Contact $id avatarImage is null")
                        } catch (e: Exception) {
                            Log.e("Baresip", "Could not read avatar image from '$id.img")
                        }
                    }
                    BaresipService.contacts.add(contact)
                }
            }
            return true
        }

    }
}
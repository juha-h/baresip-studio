package com.tutpro.baresip

import java.nio.charset.StandardCharsets
import java.util.ArrayList

class Contact(var name: String, var uri: String) {

    companion object {

        const val CONTACTS_SIZE = 100

        fun contacts(): ArrayList<Contact> {
            return BaresipService.contacts
        }

        fun save(): Boolean {
            var contents = ""
            for (c in BaresipService.contacts) contents += "\"${c.name}\" <${c.uri}>\n"
            return Utils.putFileContents(BaresipService.filesPath + "/contacts",
                    contents.toByteArray())
        }

        fun restore(): Boolean {
            val content = Utils.getFileContents(BaresipService.filesPath + "/contacts")
            if (content == null) return false
            val contacts = String(content, StandardCharsets.ISO_8859_1)
            Api.contacts_remove()
            BaresipService.contacts.clear()
            contacts.lines().forEach {
                val parts = it.split("\"")
                if (parts.size == 3) {
                    val name = parts[1]
                    var uri = parts[2].trim()
                    if (uri.startsWith("<"))
                        uri = uri.substringAfter("<").substringBefore(">")
                    // Currently no need to make baresip aware of the contact
                    // Api.contact_add("\"$name\" $uri")
                    BaresipService.contacts.add(Contact(name, uri))
                }
            }
            return true
        }

        fun export(): Boolean {
            return Utils.putFileContents(BaresipService.downloadsPath + "/contacts.bs",
                    Utils.getFileContents(BaresipService.filesPath + "/contacts")!!)
        }

        fun import(): Boolean {
            val contacts = Utils.getFileContents(BaresipService.downloadsPath + "/contacts.bs")
            if (contacts != null) {
                Utils.putFileContents(BaresipService.filesPath + "/contacts", contacts)
                return restore()
            }
            return false
        }

    }
}
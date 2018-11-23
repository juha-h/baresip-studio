package com.tutpro.baresip

import java.util.ArrayList

class Contact(var name: String, var uri: String) {

    companion object {

        const val CONTACTS_SIZE = 100

        fun contacts(): ArrayList<Contact> {
            return BaresipService.contacts
        }

    }
}
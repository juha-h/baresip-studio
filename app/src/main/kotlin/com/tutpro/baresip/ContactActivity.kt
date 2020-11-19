package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.cardview.widget.CardView
import android.view.View

import java.io.File

class ContactActivity : AppCompatActivity() {

    lateinit var textAvatarView: TextView
    lateinit var cardAvatarView: CardView
    lateinit var cardImageAvatarView: ImageView
    lateinit var nameView: EditText
    lateinit var uriView: EditText

    internal var newContact = false
    internal var newAvatar = ""
    internal var uOrI = ""

    private var index = 0
    private var color = 0
    private var id: Long = 0

    private val READ_REQUEST_CODE = 42

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact)

        textAvatarView = findViewById(R.id.TextAvatar) as TextView
        cardAvatarView = findViewById(R.id.CardAvatar) as CardView
        cardImageAvatarView = findViewById(R.id.ImageAvatar) as ImageView
        nameView = findViewById(R.id.Name) as EditText
        uriView = findViewById(R.id.Uri) as EditText

        newContact = intent.getBooleanExtra("new", false)

        if (newContact) {
            title = getString(R.string.new_contact)
            color = Utils.randomColor()
            id = System.currentTimeMillis()
            showTextAvatar("?", color)
            nameView.setText("")
            nameView.hint = getString(R.string.contact_name)
            nameView.setSelection(nameView.text.length)
            val uri = intent.getStringExtra("uri")!!
            if (uri == "") {
                uriView.setText("")
                uriView.hint = getString(R.string.sip_uri)
            } else {
                uriView.setText(uri)
            }
            uOrI = uri
        } else {
            index = intent.getIntExtra("index", 0)
            val contact = Contact.contacts()[index]
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

        textAvatarView.setOnClickListener { _ ->

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, READ_REQUEST_CODE)

        }

        textAvatarView.setOnLongClickListener { _ ->

            color = Utils.randomColor()
            showTextAvatar(textAvatarView.text.toString(), color)
            newAvatar = "text"
            true

        }

        cardAvatarView.setOnClickListener { _ ->

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, READ_REQUEST_CODE)

        }

        cardAvatarView.setOnLongClickListener { _ ->

            color = Utils.randomColor()
            showTextAvatar(nameView.text.toString(), color)
            newAvatar = "text"
            true

        }

        Utils.addActivity("contact,$newContact,$uOrI")

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {

        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            resultData?.data?.also { uri ->
                try {
                    val inputStream = baseContext.contentResolver.openInputStream(uri)
                    val avatarImage = BitmapFactory.decodeStream(inputStream)
                    showImageAvatar(avatarImage)
                    if (Utils.saveBitmap(avatarImage, File(BaresipService.filesPath, "tmp.png")))
                            newAvatar = "image"
                } catch (e: Exception) {
                    Log.e("Baresip", "Could not read avatar image")
                }
            }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        super.onCreateOptionsMenu(menu)

        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)
        return true

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("contact,$newContact,$uOrI") == -1) return true

        when (item.itemId) {

            R.id.checkIcon -> {

                var newName = nameView.text.toString().trim()
                var newUri = uriView.text.toString().trim()

                if (newName == "") newName = newUri

                if (!Utils.checkName(newName)) {
                    Utils.alertView(this, getString(R.string.notice),
                            String.format(getString(R.string.invalid_contact), newName))
                    return false
                }
                val alert: Boolean
                if (newContact)
                    alert = ContactsActivity.nameExists(newName, true)
                else
                    alert = (Contact.contacts()[index].name != newName) &&
                            ContactsActivity.nameExists(newName, false)
                if (alert) {
                    Utils.alertView(this, getString(R.string.notice),
                            String.format(getString(R.string.contact_already_exists), newName))
                    return false
                }

                if (!newUri.startsWith("sip:")) {
                    if (newUri.contains("@") || (BaresipService.uas.size != 1)) {
                        newUri = "sip:$newUri"
                    } else {
                        newUri = "sip:$newUri@${Utils.aorDomain(BaresipService.uas[0].account.aor)}"
                    }
                }
                if (!Utils.checkSipUri(newUri)) {
                    Utils.alertView(this, getString(R.string.notice),
                            String.format(getString(R.string.invalid_contact_uri), newUri))
                    return false
                }

                val contact: Contact
                if (newContact) {
                    if (Contact.contacts().size >= Contact.CONTACTS_SIZE) {
                        Utils.alertView(this, getString(R.string.notice),
                                String.format(getString(R.string.contacts_exceeded),
                                        Contact.CONTACTS_SIZE))
                        BaresipService.activities.removeAt(0)
                        return true
                    } else {
                        contact = Contact(newName, newUri, color, id)
                        Contact.contacts().add(contact)
                    }
                } else {
                    contact = Contact.contacts()[index]
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
                    "image" ->  {
                        contact.avatarImage = (cardImageAvatarView.drawable as BitmapDrawable).bitmap
                        Utils.deleteFile(File(BaresipService.filesPath, "${contact.id}.png"))
                        File(BaresipService.filesPath, "tmp.png")
                                .renameTo(File(BaresipService.filesPath, "${contact.id}.png"))
                    }
                }

                Contact.contacts().sortBy { Contact -> Contact.name }

                Contact.save()

                BaresipService.activities.remove("contact,$newContact,$uOrI")
                val i = Intent(this, MainActivity::class.java)
                i.putExtra("name", newName)
                setResult(Activity.RESULT_OK, i)
                finish()
            }

            android.R.id.home -> {

                BaresipService.activities.remove("contact,$newContact,$uOrI")
                val i = Intent(this, MainActivity::class.java)
                setResult(Activity.RESULT_CANCELED, i)
                finish()
            }

        }

        return true

    }

    override fun onBackPressed() {

        BaresipService.activities.remove("contact,$newContact,$uOrI")
        val i = Intent(this, MainActivity::class.java)
        setResult(Activity.RESULT_CANCELED, i)
        finish()
        super.onBackPressed()

    }

    private fun showTextAvatar(name: String, color: Int) {
        textAvatarView.visibility = View.VISIBLE
        cardAvatarView.visibility = View.GONE
        (textAvatarView.background as GradientDrawable).setColor(color)
        textAvatarView.text = "${name[0]}"
    }

    private fun showImageAvatar(image: Bitmap) {
        textAvatarView.visibility = View.GONE
        cardAvatarView.visibility = View.VISIBLE
        cardImageAvatarView.setImageBitmap(image)
    }
}

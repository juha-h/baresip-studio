package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.tutpro.baresip.databinding.ActivityAndroidContactBinding

class AndroidContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAndroidContactBinding
    private lateinit var layout: LinearLayout
    private lateinit var textAvatarView: TextView
    private lateinit var cardAvatarView: CardView
    private lateinit var cardImageAvatarView: ImageView
    private lateinit var nameView: TextView
    private lateinit var ulAdapter: AndroidUriListAdapter
    private lateinit var aor: String

    private var index = 0
    private var color = 0

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityAndroidContactBinding.inflate(layoutInflater)
        setContentView(binding.root)
        layout = binding.ContactView

        aor = intent.getStringExtra("aor")!!
        index = intent.getIntExtra("index", 0)

        textAvatarView = binding.TextAvatar
        cardAvatarView = binding.CardAvatar
        cardImageAvatarView = binding.ImageAvatar
        nameView = binding.Name

        val contact = Contact.contacts()[index] as Contact.AndroidContact
        val name = contact.name
        color = contact.color
        val thumbnailUri = contact.thumbnailUri
        if (thumbnailUri != null)
            showImageAvatar(thumbnailUri)
        else
            showTextAvatar(name, color)
        title = name
        nameView.text = name

        val listView = binding.uris
        ulAdapter = AndroidUriListAdapter(this, contact.uris, aor)
        listView.adapter = ulAdapter

        Utils.addActivity("android contact,$aor,$index")

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("android contact,$aor,$index") == -1)
            return true

        when (item.itemId) {
            android.R.id.home ->
                goBack()
        }

        return true

    }

    private fun goBack() {
        BaresipService.activities.remove("android contact,$index")
        setResult(Activity.RESULT_CANCELED, Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showTextAvatar(name: String, color: Int) {
        textAvatarView.visibility = View.VISIBLE
        cardAvatarView.visibility = View.GONE
        textAvatarView.background.setTint(color)
        textAvatarView.text = "${name[0]}"
    }

    private fun showImageAvatar(thumbNailUri: Uri) {
        textAvatarView.visibility = View.GONE
        cardAvatarView.visibility = View.VISIBLE
        cardImageAvatarView.setImageURI(thumbNailUri)
    }

}

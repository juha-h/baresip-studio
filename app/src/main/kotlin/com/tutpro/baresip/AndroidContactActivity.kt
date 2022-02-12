package com.tutpro.baresip

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar
import com.tutpro.baresip.Utils.showSnackBar
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
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    private var index = 0
    private var color = 0
    private var id: Long = 0

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

        val contact = AndroidContactsActivity.androidContacts[index]
        val name = contact.name
        color = contact.color
        id = contact.id
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

    }

    override fun onStart() {
        super.onStart()
        requestPermissionsLauncher =
                registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grandResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grandResults)
        when (requestCode) {
            CONTACT_PERMISSION_REQUEST_CODE -> {
                var allowed = true
                for (res in grandResults)
                    allowed = allowed && res == PackageManager.PERMISSION_GRANTED
                if (!allowed) {
                    when {
                        ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.READ_CONTACTS) -> {
                            layout.showSnackBar(
                                    binding.root,
                                    getString(R.string.no_android_contacts),
                                    Snackbar.LENGTH_INDEFINITE,
                                    getString(R.string.ok)
                            ) {
                                requestPermissionsLauncher.launch(permissions)
                            }
                        }
                        ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.WRITE_CONTACTS) -> {
                            layout.showSnackBar(
                                    binding.root,
                                    getString(R.string.no_android_contacts),
                                    Snackbar.LENGTH_INDEFINITE,
                                    getString(R.string.ok)
                            ) {
                                requestPermissionsLauncher.launch(permissions)
                            }
                        }
                        else -> {
                            requestPermissionsLauncher.launch(permissions)
                        }
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("android contact,$aor,$index") == -1) return true

        when (item.itemId) {

            android.R.id.home -> {

                BaresipService.activities.remove("android contact,$aor,$index")
                setResult(Activity.RESULT_CANCELED, Intent(this, MainActivity::class.java))
                finish()
            }

        }

        return true

    }

    override fun onBackPressed() {

        BaresipService.activities.remove("android contact,$index")
        setResult(Activity.RESULT_CANCELED, Intent(this, MainActivity::class.java))
        finish()
        super.onBackPressed()

    }

    private fun showTextAvatar(name: String, color: Int) {
        textAvatarView.visibility = View.VISIBLE
        cardAvatarView.visibility = View.GONE
        textAvatarView.background.setTint(color)
        textAvatarView.text = "${name[0]}"
    }

    private fun showImageAvatar(thumbNailUri: String) {
        textAvatarView.visibility = View.GONE
        cardAvatarView.visibility = View.VISIBLE
        cardImageAvatarView.setImageURI(thumbNailUri.toUri())
    }

}



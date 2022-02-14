package com.tutpro.baresip.plus

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import android.widget.RelativeLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar
import com.tutpro.baresip.plus.Utils.showSnackBar
import com.tutpro.baresip.plus.databinding.ActivityAndroidContactsBinding

class AndroidContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAndroidContactsBinding
    private lateinit var layout: RelativeLayout
    private lateinit var clAdapter: AndroidContactListAdapter
    private lateinit var aor: String
    private var lastSwap: Long = 0
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private val permissions = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityAndroidContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        layout = binding.AndroidContactsView

        aor = intent.getStringExtra("aor")!!

        val listView = binding.androidContacts

        permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allowed = true
            permissions.entries.forEach {
                allowed = allowed && it.value
            }
            if (allowed) {
                fetchAndroidContacts(this)
                clAdapter.notifyDataSetChanged()
            }
        }

        if (Build.VERSION.SDK_INT >= 23 && !Utils.checkPermissions(this, permissions))
            requestPermissions(permissions, CONTACT_PERMISSION_REQUEST_CODE)

        clAdapter = AndroidContactListAdapter(this, BaresipService.androidContacts, aor)
        listView.adapter = clAdapter

        Utils.addActivity("android contacts,$aor")

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.swap_contacts_icon, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.swapIcon -> {
                if (SystemClock.elapsedRealtime() - lastSwap > 1000) {
                    lastSwap = SystemClock.elapsedRealtime()
                    BaresipService.activities.remove("android contacts,$aor")
                    val intent = Intent(this, ContactsActivity::class.java)
                    intent.putExtra("aor", aor)
                    startActivity(intent)
                    finish()
                    return true
                }
            }

            android.R.id.home -> {
                BaresipService.activities.remove("android contacts,$aor")
                setResult(Activity.RESULT_OK, Intent())
                finish()
            }
        }

        return true

    }

    override fun onBackPressed() {

        BaresipService.activities.remove("android contacts,$aor")
        setResult(Activity.RESULT_OK, Intent())
        finish()

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grandResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grandResults)
        when (requestCode) {
            CONTACT_PERMISSION_REQUEST_CODE -> {
                var allowed = true
                for (res in grandResults)
                    allowed = allowed && res == PackageManager.PERMISSION_GRANTED
                if (allowed) {
                    fetchAndroidContacts(this)
                    clAdapter.notifyDataSetChanged()
                } else {
                    when {
                        ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.READ_CONTACTS) -> {
                            layout.showSnackBar(
                                    binding.root,
                                    getString(R.string.no_android_contacts),
                                    Snackbar.LENGTH_INDEFINITE,
                                    getString(R.string.ok)
                            ) {
                                permissionsLauncher.launch(permissions)
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
                                permissionsLauncher.launch(permissions)
                            }
                        }
                        else -> {
                            permissionsLauncher.launch(permissions)
                        }
                    }
                }
            }
        }
    }

    companion object {

        fun fetchAndroidContacts(ctx: Context) {
            val projection = arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME,
                    ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1,
                    ContactsContract.Data.PHOTO_THUMBNAIL_URI)
            val selection =
                    ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE + "' OR " +
                            ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'"
            val cur: Cursor? = ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection,
                    selection, null, null)
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
                    AndroidContact(id, name, 0, thumb)
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
            BaresipService.androidContacts.clear()
            for ((_, value) in contacts)
                if (value.name != "")
                    BaresipService.androidContacts.add(value)
            return
        }

        fun findContact(uri: String): AndroidContact? {
            for (c in BaresipService.androidContacts)
                for (u in c.uris)
                    if (Utils.uriMatch(u, uri))
                        return c
            return null
        }

        // Return contact name of uri or uri itself if contact with uri is not found
        fun contactName(uri: String): String {
            val userPart = Utils.uriUserPart(uri)
            return if (Utils.isTelNumber(userPart))
                findContact("tel:$userPart")?.name ?: uri
            else
                findContact(uri)?.name ?: uri
        }

        // Return first sip (preferred) or tel uri of contact name or
        // null if contact is not found or if it has no uris
        fun contactUri(name: String): String? {
            for (c in BaresipService.androidContacts)
                if (c.name == name) {
                    if (c.uris.isNotEmpty()) {
                        for (u in c.uris)
                            if (u.startsWith("sip:"))
                                return u
                        return c.uris.first()
                    }
                }
            return null
        }

    }

}

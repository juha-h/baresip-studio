package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.database.Cursor
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import com.tutpro.baresip.databinding.ActivityAndroidContactsBinding

class AndroidContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAndroidContactsBinding
    private lateinit var clAdapter: AndroidContactListAdapter
    private lateinit var aor: String
    private var lastSwap: Long = 0

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityAndroidContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aor = intent.getStringExtra("aor")!!

        val listView = binding.androidContacts

        getAndroidContacts(this)
        clAdapter = AndroidContactListAdapter(this, androidContacts, aor)
        listView.adapter = clAdapter

        Utils.addActivity("android contacts,$aor")

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.swap_contacts_icon, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.swapContactsIcon -> {
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

    private fun getAndroidContacts(ctx: Context) {
        val projection = arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1,
                ContactsContract.Data.PHOTO_THUMBNAIL_URI)
        val selection =
                ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE + "' OR "  +
                        ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'"
        Log.d(TAG, "******* selection = /$selection/")
        val cur: Cursor? = ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection,
                selection, null, null)
        val contacts = HashMap<Long, AndroidContact>()
        while (cur != null && cur.moveToNext()) {
            val id = cur.getLong(0)
            val name = cur.getString(1)  // display name
            val mime = cur.getString(2)  // type of data
            val data = cur.getString(3)  // data
            val thumb = cur.getString(4) // thumbnail
            // Log.d(TAG, "****** $id - $name - $mime - $data - $thumb")
            val contact = if (contacts.containsKey(id))
                contacts[id]!!
            else
                AndroidContact(id, name, 0, thumb)
            if (mime == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                contact.uris.add("tel:$data")
            else if (mime == ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
                contact.uris.add("sip:$data")
            else
                continue
            if (!contacts.containsKey(id))
                contacts[id] = contact
        }
        cur?.close()
        androidContacts.clear()
        for ((_, value) in contacts)
            androidContacts.add(value)
        return
    }

    companion object {
        val androidContacts = ArrayList<AndroidContact>()
    }

}

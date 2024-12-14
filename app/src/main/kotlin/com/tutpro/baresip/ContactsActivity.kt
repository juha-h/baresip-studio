package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import com.tutpro.baresip.databinding.ActivityContactsBinding

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var clAdapter: ContactListAdapter
    private lateinit var aor: String
    private var newAndroidName: String? = null
    private var lastClick: Long = 0

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v: View, insets: WindowInsetsCompat ->
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            if (Build.VERSION.SDK_INT >= 35)
                binding.ContactsView.updatePadding(top = systemBars.top + 56)
            WindowInsetsCompat.CONSUMED
        }

        if (!Utils.isDarkTheme(this))
            WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        aor = intent.getStringExtra("aor")!!
        Utils.addActivity("contacts,$aor")

        val listView = binding.contacts
        clAdapter = ContactListAdapter(this, BaresipService.contacts, aor)
        listView.adapter = clAdapter
        listView.isLongClickable = true

        val androidContactsObserver = Observer<Long> {
            if (newAndroidName != null) {
                val contentValues = ContentValues()
                contentValues.put(ContactsContract.Contacts.STARRED, 1)
                try {
                    this.contentResolver.update(
                        ContactsContract.RawContacts.CONTENT_URI, contentValues,
                        ContactsContract.Contacts.DISPLAY_NAME + "='" + newAndroidName + "'", null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Update of Android favorite failed")
                }
                newAndroidName = null
            }
            clAdapter.notifyDataSetChanged()
        }
        BaresipService.contactUpdate.observe(this, androidContactsObserver)

        val contactRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    if (it.data != null && it.data!!.hasExtra("name"))
                        newAndroidName = it.data!!.getStringExtra("name")
                }
                clAdapter.notifyDataSetChanged()
            }

        val plusButton = binding.plusButton
        plusButton.setOnClickListener {
            if (Contact.contacts().size >= Contact.CONTACTS_SIZE) {
                Utils.alertView(this, getString(R.string.notice),
                        String.format(getString(R.string.contacts_exceeded),
                                Contact.CONTACTS_SIZE))
            } else {
                if (SystemClock.elapsedRealtime() - lastClick > 1000) {
                    lastClick = SystemClock.elapsedRealtime()
                    val intent = Intent(this, ContactActivity::class.java)
                    val b = Bundle()
                    b.putBoolean("new", true)
                    b.putString("uri", "")
                    intent.putExtras(b)
                    contactRequest.launch(intent)
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    }

    override fun onResume() {
        super.onResume()
        clAdapter.notifyDataSetChanged()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            android.R.id.home ->
                goBack()
        }

        return true

    }

    private fun goBack() {

        BaresipService.activities.remove("contacts,$aor")
        setResult(Activity.RESULT_OK, Intent())
        finish()
        
    }

}

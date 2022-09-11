package com.tutpro.baresip

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Observer
import com.tutpro.baresip.databinding.ActivityContactsBinding

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var clAdapter: ContactListAdapter
    private lateinit var aor: String
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

        aor = intent.getStringExtra("aor")!!
        Utils.addActivity("contacts,$aor")

        val listView = binding.contacts
        clAdapter = ContactListAdapter(this, BaresipService.contacts, aor)
        listView.adapter = clAdapter
        listView.isLongClickable = true

        val contactObserver = Observer<Long> {
            clAdapter.notifyDataSetChanged()
        }
        BaresipService.contactUpdate.observe(this, contactObserver)

        val contactRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK)
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

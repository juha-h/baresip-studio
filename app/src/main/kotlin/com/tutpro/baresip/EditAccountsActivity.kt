package com.tutpro.baresip

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter

class EditAccountsActivity : AppCompatActivity() {

    private var editText: EditText? = null
    private var path: String? = null
    private var file: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_accounts)

        editText = findViewById(R.id.editAccounts) as EditText
        path = applicationContext.filesDir.absolutePath + "/accounts"
        file = File(path!!)
        var content: String

        if (!file!!.exists()) {
            Log.e("Baresip", "Failed to find accounts file")
            content = "No accounts"
        } else {
            Log.e("Baresip", "Found accounts file")
            val length = file!!.length().toInt()
            val bytes = ByteArray(length)
            try {
                val `in` = FileInputStream(file!!)
                try {
                    `in`.read(bytes)
                } finally {
                    `in`.close()
                }
                content = String(bytes)
            } catch (e: java.io.IOException) {
                Log.e("Baresip", "Failed to read accounts file: " + e.toString())
                content = "Failed to read account file"
            }

        }

        Log.d("Baresip", "Content length is: " + content.length)

        editText!!.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.textsize))
        editText!!.setText(content, TextView.BufferType.EDITABLE)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.edit_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val i = Intent()
        when (item.itemId) {
            R.id.save -> {
                try {
                    val fOut = FileOutputStream(file!!.absoluteFile, false)
                    val fWriter = OutputStreamWriter(fOut)
                    val res = editText!!.text.toString()
                    try {
                        fWriter.write(res)
                        fWriter.close()
                        fOut.close()
                    } catch (e: java.io.IOException) {
                        Log.e("Baresip", "Failed to write accounts file: " + e.toString())
                    }

                } catch (e: java.io.FileNotFoundException) {
                    Log.e("Baresip", "Failed to find accounts file: " + e.toString())
                }

                Log.d("Baresip", "Updated accounts file")
                setResult(RESULT_OK, i)
                finish()
                return true
            }
            R.id.cancel, android.R.id.home -> {
                setResult(RESULT_CANCELED, i)
                finish()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
}


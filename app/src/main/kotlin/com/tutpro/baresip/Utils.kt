package com.tutpro.baresip

import android.content.Context
import android.support.v7.app.AlertDialog
import android.util.Log
import android.widget.ImageButton
import java.io.*

object Utils {

    fun copyAssetToFile(context: Context, asset: String, path: String) {
        try {
            val `is` = context.assets.open(asset)
            val os = FileOutputStream(path)
            val buffer = ByteArray(512)
            var byteRead: Int = `is`.read(buffer)
            while (byteRead  != -1) {
                os.write(buffer, 0, byteRead)
                byteRead = `is`.read(buffer)
            }
        } catch (e: IOException) {
            Log.e("Baresip", "Failed to read asset " + asset + ": " +
                    e.toString())
        }

    }

    fun getFileContents(file: File): String {
        if (!file.exists()) {
            Log.e("Baresip", "Failed to find file: " + file.path)
            return ""
        } else {
            Log.e("Baresip", "Found file: " + file.path)
            val length = file.length().toInt()
            val bytes = ByteArray(length)
            try {
                val `in` = FileInputStream(file)
                try {
                    `in`.read(bytes)
                } finally {
                    `in`.close()
                }
                return String(bytes)
            } catch (e: java.io.IOException) {
                Log.e("Baresip", "Failed to read file: " + file.path + ": " +
                        e.toString())
                return ""
            }

        }
    }

    fun putFileContents(file: File, contents: String) {
        try {
            val fOut = FileOutputStream(file.absoluteFile, false)
            val fWriter = OutputStreamWriter(fOut)
            try {
                fWriter.write(contents)
                fWriter.close()
                fOut.close()
            } catch (e: java.io.IOException) {
                Log.e("Baresip", "Failed to put contents to file: " + e.toString())
            }

        } catch (e: java.io.FileNotFoundException) {
            Log.e("Baresip", "Failed to find contents file: " + e.toString())
        }

    }

    fun alertView(context: Context, title: String, message: String) {
        // val alertDialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert).create()
        val alertDialog = AlertDialog.Builder(context).create()
        alertDialog.setTitle(title)
        alertDialog.setMessage(message)
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK"
        ) { dialog, _ -> dialog.dismiss() }
        alertDialog.show()
    }

    fun uriHostPart(uri: String): String {
        return uri.substringAfter("@")
                .substringBefore(":")
                .substringBefore(";")
                .substringBefore(">")
    }

    fun uriUserPart(uri: String): String {
        return uri.substringAfter(":").substringBefore("@")
    }

    fun setSecurityButtonTag(button: ImageButton, security: Int) {
        when (security) {
            R.drawable.box_red -> { button.tag = "red" }
            R.drawable.box_yellow -> { button.tag = "yellow" }
            R.drawable.box_green -> { button.tag = "green" }
        }
    }

    fun setSecurityButtonOnClickListener(context: Context, button: ImageButton, call: Call) {
        button.setOnClickListener {
            when (button.tag) {
                "red" -> {
                    Utils.alertView(context, "Alert", "This call is NOT secure!")
                }
                "yellow" -> {
                    Utils.alertView(context, "Alert",
                            "This call is SECURE, but peer is NOT verified!")
                }
                "green" -> {
                    val unverifyDialog = AlertDialog.Builder(context)
                    unverifyDialog.setMessage("This call is SECURE and peer is VERIFIED! " +
                            "Do you want to unverify the peer?")
                    unverifyDialog.setPositiveButton("Unverify") { dialog, _ ->
                        if (cmd_exec("zrtp_unverify " + call.zid) != 0) {
                            Log.w("Baresip", "Command 'zrtp_unverify ${call.zid}' failed")
                        } else {
                            button.setImageResource(R.drawable.box_yellow)
                            button.tag = "yellow"
                        }
                        dialog.dismiss()
                    }
                    unverifyDialog.setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                    unverifyDialog.create().show()
                }
            }
        }
    }

    external fun cmd_exec(cmd: String): Int

}

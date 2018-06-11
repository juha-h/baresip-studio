package com.tutpro.baresip

import android.app.ActivityManager
import android.content.Context
import android.support.v7.app.AlertDialog
import android.util.Log
import android.net.ConnectivityManager

import java.io.*
import android.os.Build
import android.os.PowerManager
import android.app.KeyguardManager

object Utils {

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

    fun checkUserID(id: String): Boolean {
        return Regex("^[a-zA-Z]([._-]|[a-zA-Z0-9]){1,49}\$").matches(id)
    }

    fun checkTelNo(no: String): Boolean {
        return Regex("^[+]?[0-9]{1,16}\$").matches(no)
    }

    fun checkIP(ip: String): Boolean {
        return Regex("^(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))/$").matches(ip)
    }

    fun checkUriUser(user: String): Boolean {
        for (c in user)
            if (!(c.isLetterOrDigit() || c in "-_.!~*'()&=+$,;?/")) return false
        return true
    }

    fun checkDomain(domain: String): Boolean {
        val parts = domain.split(".")
        for (p in parts) {
            if (p.length == 0 || p.endsWith("-") || !Regex("^[a-zA-z]([-]|[a-zA-Z0-9])+\$").matches(p))
                return false
        }
        return true
    }

    fun checkUri(uri: String): Boolean {
        if (!uri.startsWith("sip:")) return false
        val parts = uri.substring(4).split("@")
        val hostParams = parts[1].split(";", limit = 2)
        // todo: check also possible params
        return checkUriUser(parts[0]) && (checkDomain(hostParams[0]) || checkIP(hostParams[0]))
    }

    fun checkPrintASCII(s: String): Boolean {
        if (s == "") return true
        val printASCIIRegex = Regex("^[ -~]*\$")
        return printASCIIRegex.matches(s)
    }

    fun checkUint(s: String): Boolean {
        val uintRegex = Regex("^[0-9]+\$")
        return uintRegex.matches(s)
    }

    fun checkName(name: String): Boolean {
        if (name.length < 2) return false
        for (c in name) {
            if (!c.isLetterOrDigit() && !(c in "-.!%*_+`'~ ")) return false
        }
        return true
    }

    fun implode(list: List<String>, sep: String): String {
        var res = ""
        for (s in list) {
            if (res == "")
                res = s
            else
                res = res + sep + s
        }
        return res
    }

    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        if (activeNetwork != null && activeNetwork.isConnected) {
            val networkType = activeNetwork.type
            return networkType == ConnectivityManager.TYPE_WIFI || networkType == ConnectivityManager.TYPE_MOBILE
        } else {
            return false
        }
    }

    fun foregrounded(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo);
        return ((appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) or
                (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE))
    }

    fun isDeviceLocked(context: Context): Boolean {
        val isLocked: Boolean

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val inKeyguardRestrictedInputMode = keyguardManager.inKeyguardRestrictedInputMode()

        if (inKeyguardRestrictedInputMode) {
            isLocked = true
        } else {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            isLocked = !powerManager.isInteractive
        }

        Log.d("Baresip", "Now device is ${if (isLocked) "locked" else "unlocked"}")
        return isLocked
    }

    external fun cmd_exec(cmd: String): Int
    external fun uri_decode(uri: String): Boolean
    external fun audio_codecs(): String

}

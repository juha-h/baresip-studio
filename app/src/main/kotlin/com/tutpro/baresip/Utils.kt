package com.tutpro.baresip

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.support.v7.app.AlertDialog
import android.os.PowerManager
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Base64.encodeToString

import kotlin.collections.ArrayList
import kotlin.Exception

import java.io.*
import java.security.SecureRandom
import java.util.*
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

object Utils {

    fun getFileContents(file: File): String {
        if (!file.exists()) {
            Log.e("Baresip", "Failed to find file: " + file.path)
            return "Failed"
        } else {
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
                return "Failed"
            }

        }
    }

    fun putFileContents(file: File, contents: String): Boolean {
        try {
            val fOut = FileOutputStream(file.absoluteFile, false)
            val fWriter = OutputStreamWriter(fOut)
            try {
                fWriter.write(contents)
                fWriter.close()
                fOut.close()
            } catch (e: java.io.IOException) {
                Log.e("Baresip", "Failed to put contents to file: " + e.toString())
                return false
            }

        } catch (e: java.io.FileNotFoundException) {
            Log.e("Baresip", "Failed to find contents file: " + e.toString())
            return false
        }
        return true
    }

    fun getNameValue(string: String, name: String): ArrayList<String> {
        val lines = string.split("\n")
        val result = ArrayList<String>()
        for (line in lines) {
            if (line.startsWith(name))
                result.add((line.substring(name.length).trim()).split(" \t")[0])
        }
        return result
    }

    fun removeLinesStartingWithName(string: String, name: String): String {
        var result = ""
        for (line in string.split("\n"))
            if (!line.startsWith(name)) result += line + "\n"
        return result
    }

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
            os.close()
            `is`.close()
        } catch (e: IOException) {
            Log.e("Baresip", "Failed to read asset " + asset + ": " +
                    e.toString())
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

    fun friendlyUri(uri: String, domain: String): String {
        var u = uri
        if (uri.startsWith("<") && (uri.endsWith(">")))
            u = uri.substring(1).substringBeforeLast(">")
        if (u.contains("@") && !u.substring(4).contains(":") &&
                !u.contains(";")) {
            val user = uriUserPart(u)
            val host = uriHostPart(u)
            if (host == domain) return user else return "$user@$host"
        } else {
            return uri
        }
    }

    fun aorDomain(aor: String): String {
        return aor.substringAfter("@")
    }

    fun checkTelNo(no: String): Boolean {
        return Regex("^[+]?[0-9]{1,16}\$").matches(no)
    }

    fun checkIP(ip: String): Boolean {
        return Regex("^(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))$").matches(ip)
    }

    fun checkIPv6(ip: String): Boolean {
        return Regex("^(([0-9a-fA-F]{0,4}:){1,7}[0-9a-fA-F]{0,4})$").matches(ip)
    }

    fun checkUriUser(user: String): Boolean {
        for (c in user)
            if (!(c.isLetterOrDigit() || c in "-_.!~*'()&=+$,;?/")) return false
        return user.length > 0
    }

    fun checkDomain(domain: String): Boolean {
        val parts = domain.split(".")
        for (p in parts) {
            if (p.endsWith("-") || p.startsWith("-") ||
                    !Regex("^[-a-zA-Z0-9]+\$").matches(p))
                return false
        }
        return true
    }

    fun checkPort(port: String): Boolean {
        val number = port.toIntOrNull()
        if (number == null) return false
        return (number > 0) && (number < 65536)
    }

    fun checkHostPort(hp: String, portMandatory: Boolean) : Boolean {
        if (hp.startsWith("[")) {
            val parts = hp.split("]")
            if (parts.size != 2) return false
            Log.d("Baresip", "Checking IPv6 '${parts[0].substring(1)}'")
            if (!checkIPv6(parts[0].substring(1))) return false
            if (portMandatory && !parts[1].startsWith(":")) return false
            Log.d("Baresip", "Checking port '${parts[1].substring(1)}'")
            return checkPort(parts[1].substring(1))
        } else {
            val parts = hp.split(":")
            if (portMandatory && (parts.size != 2)) return false
            if (parts.size == 1) return checkIP(parts[0]) || checkDomain(parts[0])
            return checkPort(parts[1]) && (checkIP(parts[0]) || checkDomain(parts[0]))
        }
    }

    fun checkParams(params: String): Boolean {
        for (param in params.split(";"))
            if (!checkParam(param)) return false
        return true
    }

    fun checkParam(param: String): Boolean {
        val nameValue = param.split("=")
        if (nameValue.size == 1)
            /* Todo: do proper check */
            return true
        if (nameValue.size == 2)
            /* Todo: do proper check */
            return true
        return false
    }

    fun checkHostPortParams(hpp: String) : Boolean {
        val restParams = hpp.split(";", limit = 2)
        if (restParams.size == 1)
            return checkHostPort(restParams[0], false)
        else
            return checkHostPort(restParams[0], false) && checkParams(restParams[1])
    }

    fun checkSipUri(uri: String): Boolean {
        if (!uri.startsWith("sip:")) return false
        val userRest = uri.substring(4).split("@")
        if (userRest.size == 1) {
            return checkHostPortParams(userRest[0])
        } else if (userRest.size == 2) {
            return checkUriUser(userRest[0]) && checkHostPortParams(userRest[1])
        } else
            return false
    }

    fun checkAorUri(uri: String): Boolean {
        if (!uri.startsWith("sip:")) return false
        val userDomain = uri.replace("sip:", "").split("@")
        if (userDomain.size != 2) return false
        if (!checkUriUser(userDomain[0])) return false
        return checkDomain(userDomain[1]) || checkIP(userDomain[1])
    }

    fun checkOutboundUri(uri: String): Boolean {
        if (!uri.startsWith("sip:")) return false
        return checkHostPortParams(uri.substring(4))
    }

    fun checkPrintAscii(s: String): Boolean {
        if (s == "") return true
        return Regex("^[ -~]*\$").matches(s)
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

    fun isVisible(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        return appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
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

    fun dtmfWatcher(callp: String): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(sequence: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(sequence: CharSequence, start: Int, before: Int, count: Int) {
                val text = sequence.subSequence(start, start + count).toString()
                if (text.length > 0) {
                    val digit = text[0]
                    Log.d("Baresip", "Got DTMF digit '$digit'")
                    if (((digit >= '0') && (digit <= '9')) || (digit == '*') || (digit == '#'))
                        Api.call_send_digit(callp, digit)
                }
            }
            override fun afterTextChanged(sequence: Editable) {
                // KEYCODE_REL
                // call_send_digit(callp, 4.toChar())
            }
        }
    }

    fun requestPermission(ctx: Context, permission: String) : Boolean {
        if (ContextCompat.checkSelfPermission(ctx, permission) != PackageManager.PERMISSION_GRANTED) {
            Log.w("Baresip", "Baresip does not have $permission permission")
            ActivityCompat.requestPermissions(ctx as Activity, arrayOf(permission),
                    MainActivity.PERMISSION_REQUEST_CODE)
            return false
        }
        return true
    }

    fun encrypt(plainText: String, password: String): String {
        val sr = SecureRandom.getInstance("SHA1PRNG")
        val salt = ByteArray(16)
        sr.nextBytes(salt)
        val iterationCount = Random().nextInt(1024) + 512
        val spec = PBEKeySpec(password.toCharArray(), salt, iterationCount, 256)
        val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec)
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        val cipherText = cipher.doFinal(plainText.toByteArray())
        val byteBuffer = ByteBuffer.allocate(Int.SIZE_BYTES + salt.size + Int.SIZE_BYTES +
                Int.SIZE_BYTES + iv.size + cipherText.size)
        byteBuffer.putInt(salt.size)
        byteBuffer.put(salt)
        byteBuffer.putInt(iterationCount)
        byteBuffer.putInt(iv.size)
        byteBuffer.put(iv)
        byteBuffer.put(cipherText)
        return encodeToString(byteBuffer.array(), Base64.DEFAULT)
    }

    fun decrypt(cipherMessage: String, password: String): String {
        val byteBuffer = ByteBuffer.wrap(Base64.decode(cipherMessage, Base64.DEFAULT))
        val saltLength = byteBuffer.getInt()
        if (saltLength != 16) {
            Log.w("Baresip", "invalid salt length $saltLength")
            return ""
        }
        val salt = ByteArray(saltLength)
        byteBuffer.get(salt)
        val iterationCount = byteBuffer.getInt()
        if ((iterationCount < 512) || (iterationCount > 1023 + 512)) {
            Log.w("Baresip", "invalid iteratorCount $iterationCount")
            return ""
        }
        val spec = PBEKeySpec(password.toCharArray(), salt, iterationCount, 256)
        val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec)
        val ivLength = byteBuffer.getInt()
        if (ivLength != 12) {
            Log.d("Baresip", "invalid iv length $ivLength")
            return ""
        }
        val iv = ByteArray(ivLength)
        byteBuffer.get(iv)
        val cipherText = ByteArray(byteBuffer.remaining())
        byteBuffer.get(cipherText)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        try {
            return String(cipher.doFinal(cipherText))
        } catch (e: Exception) {
            Log.w("Baresip", "Decryption failed ${e.printStackTrace()}")
            return ""
        }
    }

    fun dumpIntent(intent: Intent) {

        val bundle: Bundle = intent.extras ?: return

        val keys = bundle.keySet()
        val it = keys.iterator()

        Log.d("Baresip", "Dumping intent start")

        while (it.hasNext()) {
            val key = it.next()
            Log.d("Baresip","[" + key + "=" + bundle.get(key)+"]");
        }

        Log.d("Baresip", "Dumping intent finish")

    }

}

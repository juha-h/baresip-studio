package com.tutpro.baresip

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.reflect.Method
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.URL
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.util.Calendar
import java.util.Enumeration
import java.util.GregorianCalendar
import java.util.Locale
import java.util.Random
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import androidx.core.graphics.toColorInt
import androidx.navigation.NavController

object Utils {

    fun getNameValue(string: String, name: String): ArrayList<String> {
        val lines = string.split("\n")
        val result = ArrayList<String>()
        for (line in lines) {
            if (line.startsWith(name))
                result.add((line.substring(name.length).trim()).split(" \t")[0])
        }
        return result
    }

    fun removeLinesStartingWithString(lines: String, string: String): String {
        var result = ""
        for (line in lines.split("\n"))
            if (!line.startsWith(string) && (line.isNotEmpty())) result += line + "\n"
        return result
    }

    fun uriHostPart(uri: String): String {
        return if (uri.contains("@")) {
            uri.substringAfter("@")
                    .substringBefore(":")
                    .substringBefore(";")
                    .substringBefore("?")
                    .substringBefore(">")
        } else {
            val parts = uri.split(":")
            when (parts.size) {
                2 -> parts[1].substringBefore(";")
                        .substringBefore("?")
                        .substringBefore(">")
                3 -> parts[1]
                else -> ""
            }
        }
    }

    fun uriUserPart(uri: String): String {
        return if (uri.contains("@"))
            uri.substringAfter(":").substringBefore("@")
        else
            ""
    }

    fun uriMatch(firstUri: String, secondUri: String): Boolean {
        if (firstUri.startsWith("tel:"))
            return firstUri == secondUri || firstUri.substringAfter(":") == uriUserPart(secondUri)
        if (firstUri.startsWith("sip:"))
            return uriUserPart(firstUri) == uriUserPart(secondUri) &&
                    uriHostPart(firstUri) == uriHostPart(secondUri)
        return false
    }

    private fun uriParams(uri: String): List<String> {
        val params = uri.split(";")
        return if (params.size == 1) listOf() else params.subList(1, params.size)
    }

    fun friendlyUri(ctx: Context, uri: String, account: Account, e164Check: Boolean = true): String {
        var u = Contact.contactName(uri)
        if (u != uri)
            return u
        if (e164Check) {
            val e164Uri = e164Uri(uri, account.countryCode)
            u = Contact.contactName(e164Uri)
            if (u != e164Uri)
                return u
        }
        u = u.replace("%23", "#")
        if (u.contains("@")) {
            val user = uriUserPart(u)
            val host = uriHostPart(u)
            val params = uriParams(u).filter{it != "transport=udp"}
            return if (host == aorDomain(account.aor) || params.contains("user=phone"))
                user
            else if (host == "anonymous.invalid")
                ctx.getString(R.string.anonymous)
            else if (host == "unknown.invalid")
                ctx.getString(R.string.unknown)
            else
                if (params.isEmpty())
                    "$user@$host"
                else
                    "$user@$host;" + params.joinToString(";")
        }
        if (uri.startsWith("<") && (uri.endsWith(">")))
            u = uri.substring(1).substringBeforeLast(">")
        u = u.substringBefore("?")
        u = u.replace(":5060", "")
        u = u.replace(";transport=udp", "", true)
        return u
    }

    private fun e164Uri(uri: String, countryCode: String): String {
        if (countryCode == "") return uri
        val scheme = uri.take(4)
        val userPart = uriUserPart(uri)
        return if (userPart.isDigitsOnly()) {
            when {
                userPart.startsWith("00") -> uri.replace("$scheme$userPart",
                        scheme + userPart.substring(2))
                userPart.startsWith("0") -> uri.replace("${scheme}0",
                    "$scheme$countryCode")
                else -> uri.replace(scheme, "$scheme$countryCode")
            }
        } else
            uri
    }

    fun uriComplete(uri: String, aor: String): String {
        val res = if (!uri.startsWith("sip:")) "sip:$uri" else uri
        return if (checkUriUser(uri)) "$res@${aorDomain(aor)}" else res
    }

    private fun String.replace(vararg pairs: Pair<String, String>): String =
            pairs.fold(this) { acc, (old, new) -> acc.replace(old, new, ignoreCase = true) }

    fun uriUnescape(uri: String): String {
        return uri.replace("%2B" to "+", "%3A" to ":", "%3B" to ";", "%40" to "@", "%3D" to "=")
    }

    fun aorDomain(aor: String): String {
        return uriHostPart(aor)
    }

    fun plainAor(aor: String): String {
        return uriUserPart(aor) + "@" + uriHostPart(aor)
    }

    fun checkAor(aor: String): Boolean {
        if (!checkSipUri(aor)) return false
        val params = uriParams(aor)
        return params.isEmpty() ||
                ((params.size == 1) &&
                        params[0] in arrayOf("transport=udp", "transport=tcp", "transport=tls"))
    }

    private fun checkTransport(transport: String, transports: Set<String>): Boolean {
        return transport.split("=")[0] == "transport" &&
                transport.split("=")[1].lowercase() in transports
    }

    fun checkStunUri(uri: String): Boolean {
        if (uri.substringBefore(":").lowercase() !in setOf("stun", "stuns", "turn", "turns"))
            return false
        return checkHostPort(uri.substringAfter(":").substringBefore("?")) &&
                (uri.indexOf("?") == -1 ||
                checkTransport(uri.substringAfter("?"), setOf("udp", "tcp")))
    }

    fun checkIpV4(ip: String): Boolean {
        return Regex("^(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))$").matches(ip)
    }

    private fun checkIpV6(ip: String): Boolean {
        return Regex("^(([0-9a-fA-F]{0,4}:){1,7}[0-9a-fA-F]{0,4})$").matches(ip)
    }

    private fun checkIpv6InBrackets(bracketedIp: String): Boolean {
        return bracketedIp.startsWith("[") && bracketedIp.endsWith("]") &&
                checkIpV6(bracketedIp.substring(1, bracketedIp.length - 2))
    }

    fun checkUriUser(user: String): Boolean {
        val escaped = """%(\d|A|B|C|D|E|F|a|b|c|d|e|f){2}""".toRegex()
        escaped.replace(user, "").forEach {
            if (!(it.isLetterOrDigit() || "-_.!~*\'()&=+$,;?/".contains(it))) return false }
        return user.isNotEmpty() && !checkIpV4(user) && !checkIpV6(user)
    }

    fun checkDomain(domain: String): Boolean {
        val parts = domain.split(".")
        for (p in parts) {
            if (p.endsWith("-") || p.startsWith("-") ||
                    !Regex("^[-a-zA-Z0-9]+$").matches(p))
                return false
        }
        return true
    }

    private fun checkPort(port: String): Boolean {
        val number = port.toIntOrNull() ?: return false
        return (number > 0) && (number < 65536)
    }

    fun checkIpPort(ipPort: String): Boolean {
        return if (ipPort.startsWith("["))
            checkIpv6InBrackets(ipPort.substringBeforeLast(":")) &&
                    checkPort(ipPort.substringAfterLast(":"))
        else
            checkIpV4(ipPort.substringBeforeLast(":")) &&
                    checkPort(ipPort.substringAfterLast(":"))
    }

    private fun checkDomainPort(domainPort: String): Boolean {
        return checkDomain(domainPort.substringBeforeLast(":")) &&
                checkPort(domainPort.substringAfterLast(":"))
    }

    private fun checkHostPort(hostPort: String): Boolean {
        return checkIpV4(hostPort) || checkIpv6InBrackets(hostPort) || checkDomain(hostPort) ||
                checkIpPort(hostPort) || checkDomainPort(hostPort)
    }

    private fun checkParams(params: String): Boolean {
        for (param in params.split(";"))
            if (!checkParam(param)) return false
        return true
    }

    private fun checkParam(param: String): Boolean {
        val nameValue = param.split("=")
        if (nameValue.size == 1)
            return checkParamChars(nameValue[0])
        if (nameValue.size == 2) {
            if (nameValue[0] == "transport")
                return setOf("udp", "tcp", "tls", "wss").contains(nameValue[1].lowercase())
            return checkParamChars(nameValue[1])
        }
        return false
    }

    private fun checkParamChars(s: String): Boolean {
        // Does not currently allow escaped characters
        val allowed = "[]/:&+$-_.!~*'()"
        for (c in s)
            if (!allowed.contains(c) && !c.isLetterOrDigit())
                return false
        return true
    }

    fun paramValue(params: String, name: String): String {
        if (params == "") return ""
        for (param in params.split(";"))
            if (param.substringBefore("=") == name) return param.substringAfter("=")
        return ""
    }

    fun paramExists(params: String, name: String): Boolean {
        for (param in params.split(";"))
            if (param.substringBefore("=") == name) return true
        return false
    }

    fun checkHostPortParams(hpp: String) : Boolean {
        val restParams = hpp.split(";", limit = 2)
        return if (restParams.size == 1)
            checkHostPort(restParams[0])
        else
            checkHostPort(restParams[0]) && checkParams(restParams[1])
    }

    private fun checkSipUri(uri: String): Boolean {
        return if (uri.startsWith("sip:")) {
            val userRest = uri.substring(4).split("@")
            when (userRest.size) {
                1 ->
                    checkHostPortParams(userRest[0])
                2 ->
                    checkUriUser(userRest[0]) && checkHostPortParams(userRest[1])
                else -> false
            }
        } else {
            false
        }
    }

    fun isTelNumber(no: String): Boolean {
        return no.isNotEmpty() && Regex("^([+][1-9])?[0-9- (),*#]{0,24}$").matches(no)
    }

    fun isTelUri(uri: String): Boolean {
        return uri.startsWith("tel:") && isTelNumber(uri.substring(4))
    }

    fun checkUri(uri: String): Boolean {
        return checkSipUri(uri) || isTelUri(uri)
    }

    fun telToSip(telUri: String, account: Account): String {
        val hostPart = if (account.telProvider != "")
            account.telProvider
        else
            aorDomain(account.aor)
        return "sip:" + telUri.substring(4)
            .filterNot{setOf('-', ' ', '(', ')').contains(it)}
            .replace("#", "%23") +
                "@" + hostPart + ";user=phone"
    }

    fun checkName(name: String): Boolean {
        return name.isNotEmpty() && name == String(name.toByteArray(), Charsets.UTF_8) &&
                name.lines().size == 1 && !name.contains('"')
    }

    fun checkCountryCode(cc: String): Boolean {
        return cc.startsWith("+") && cc.length > 1 && cc.length < 5 &&
                cc.substring(1).isDigitsOnly() && cc[1] != '0'
    }

    fun checkServerVal(server: String): Boolean {
        val parts = server.replace(Regex("[(][^()\\\\]+[)]"), "")
            .trim().split("\\s+".toRegex())
        for (part in parts)
            if (!checkProduct(part))
                return false
        return true
    }

    private fun checkProduct(product: String): Boolean {
        val parts = product.split("/", limit = 2)
        return if (parts.count() == 2)
            checkToken(parts[0]) && checkToken(parts[1])
        else
            checkToken(parts[0])
    }

    private fun checkToken(token: String): Boolean {
        return Regex("^[-a-zA-Z0-9.!%*_+`'~]+$").matches(token)
    }

    @Suppress("unused")
    fun checkIfName(name: String): Boolean {
        if ((name.length < 2) || !name.first().isLetter()) return false
        for (c in name)
            if (!c.isLetterOrDigit()) return false
        return true
    }

    fun implode(list: List<String>, sep: String): String {
        var res = ""
        for (s in list) {
            res = if (res == "")
                s
            else
                res + sep + s
        }
        return res
    }

    fun isVisible(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    fun isHotSpotOn(wm: WifiManager): Boolean {
        try {
            val method: Method = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            return method.invoke(wm) as Boolean
        } catch (_: Throwable) {
        }
        return false
    }

    fun hotSpotAddresses(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface: NetworkInterface = interfaces.nextElement()
                val ifName = iface.name
                Log.d(TAG, "Found interface with name $ifName")
                if (ifName.startsWith("ap") || ifName.contains("wlan")) {
                    val addresses: Enumeration<InetAddress> = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val inetAddress: InetAddress = addresses.nextElement()
                        if (inetAddress.isSiteLocalAddress)
                            result[inetAddress.hostAddress!!] = ifName
                    }
                    if (result.isNotEmpty()) return result
                }
            }
        } catch (ex: SocketException) {
            Log.e(TAG, "hotSpotAddresses SocketException: $ex")
        } catch (ex: NullPointerException) {
            Log.e(TAG, "hotSpotAddresses NullPointerException: $ex")
        }
        return result
    }

    fun checkPermissions(ctx: Context, permissions: Array<String>) : Boolean {
        for (p in permissions) {
            if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission $p is denied")
                return false
            } else {
                Log.d(TAG, "Permission $p is granted")
            }
        }
        return true
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
            Log.e(TAG, "Failed to copy asset '$asset' to file: $e")
        }
    }

    fun deleteFile(file: File) {
        if (file.exists()) {
            try {
                file.delete()
            } catch (e: IOException) {
                Log.e(TAG, "Could not delete file ${file.absolutePath}: $e")
            }
        }
    }

    fun deleteFile(ctx: Context, uri: Uri): Boolean {
        val contentResolver: ContentResolver = ctx.contentResolver
        try {
            if (DocumentsContract.isDocumentUri(ctx, uri)) {
                if (DocumentsContract.deleteDocument(contentResolver, uri)) {
                    Log.d(TAG, "File deleted successfully: $uri")
                    return true
                } else {
                    Log.d(TAG, "File not found or could not be deleted: $uri")
                    return false
                }
            } else {
                Log.d(TAG, "Uri is not a document uri: $uri")
                return false
            }
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "Error deleting file $uri: $e")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file $uri: $e")
            return false
        }
    }

    fun getFileContents(filePath: String): ByteArray? {
        return try {
            File(filePath).readBytes()
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "File '$filePath' not found: ${e.printStackTrace()}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file '$filePath': ${e.printStackTrace()}")
            null
        }
    }

    fun putFileContents(filePath: String, contents: ByteArray): Boolean {
        try {
            File(filePath).writeBytes(contents)
        }
        catch (e: IOException) {
            Log.e(TAG, "Failed to write file '$filePath': $e")
            return false
        }
        return true
    }

    fun File.copyInputStreamToFile(inputStream: InputStream): Boolean {
        try {
            this.outputStream().use { fileOut ->
                inputStream.copyTo(fileOut)
            }
            return true
        }
        catch (e: IOException) {
            Log.e(TAG, "Failed to write file '${this.absolutePath}': $e")
        }
        return false
    }

    @RequiresApi(29)
    fun selectInputFile(request: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }
        request.launch(intent)
    }

    @RequiresApi(29)
    @Suppress("unused")
    fun selectOutputFile(title: String) {
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }
    }

    fun downloadsPath(fileName: String): String {
        return Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS).path + "/$fileName"
    }

    fun fileNameOfUri(ctx: Context, uri: Uri): String {
        val cursor = ctx.contentResolver.query(uri, null, null, null, null)
        var name = ""
        if (cursor != null) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            name = cursor.getString(index)
            cursor.close()
        }
        return if (name == "")
            "$uri".substringAfterLast("/")
        else
            name
    }

    class Crypto(val salt: ByteArray, val iter: Int, val iv: ByteArray, val data: ByteArray): Serializable {
        companion object {
            private const val serialVersionUID: Long = -29238082928391L
        }
    }

    private fun encrypt(content: ByteArray, password: CharArray): ByteArray? {
        fun intToByteArray(int: Int): ByteArray {
            val bytes = ByteArray(2)
            bytes[0] = (int shr 0).toByte()
            bytes[1] = (int shr 8).toByte()
            return bytes
        }
        try {
            val sr = SecureRandom()
            val salt = ByteArray(128)
            sr.nextBytes(salt)
            val iterationCount = Random().nextInt(1024) + 512
            val pbKeySpec = PBEKeySpec(password, salt, iterationCount, 128)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val keyBytes = secretKeyFactory.generateSecret(pbKeySpec).encoded
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivRandom = SecureRandom()
            val iv = ByteArray(16)
            ivRandom.nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val cipherData = cipher.doFinal(content)
            val res = ByteArray(128 + 2 + 16 + cipherData.size)
            salt.copyInto(res, 0)
            intToByteArray(iterationCount).copyInto(res, salt.size)
            iv.copyInto(res, salt.size + 2)
            cipherData.copyInto(res, salt.size + 2 + iv.size)
            return res
        } catch (e: Exception) {
            Log.e(TAG, "Encrypt failed: ${e.printStackTrace()}")
        }
        return null
    }

    private fun decrypt(content: ByteArray, password: CharArray): ByteArray? {
        fun byteArrayToInt(bytes: ByteArray) : Int {
            return (bytes[1].toInt() and 0xff shl 8) or (bytes[0].toInt() and 0xff)
        }
        try {
            val salt = content.copyOfRange(0, 128)
            val iterationCount = byteArrayToInt(content.copyOfRange(128, 130))
            val iv = content.copyOfRange(130, 146)
            val data = content.copyOfRange(146, content.size)
            val pbKeySpec = PBEKeySpec(password, salt, iterationCount, 128)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val keyBytes = secretKeyFactory.generateSecret(pbKeySpec).encoded
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            return cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed: ${e.printStackTrace()}")
        }
        return null
    }

    private fun decryptOld(obj: Crypto, password: CharArray): ByteArray? {
        var plainData: ByteArray? = null
        try {
            val pbKeySpec = PBEKeySpec(password, obj.salt, obj.iter, 128)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val keyBytes = secretKeyFactory.generateSecret(pbKeySpec).encoded
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            val ivSpec = IvParameterSpec(obj.iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            plainData = cipher.doFinal(obj.data)
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed: ${e.printStackTrace()}")
        }
        return plainData
    }

    fun encryptToUri(ctx: Context, uri: Uri, content: ByteArray, password: String): Boolean {
        val obj = encrypt(content, password.toCharArray())
        val stream = ctx.contentResolver.openOutputStream(uri) as FileOutputStream
        try {
            ObjectOutputStream(stream).use {
                it.writeObject(obj)
            }
        } catch (e: Exception) {
            Log.w(TAG, "encryptToUri failed: $e")
            return false
        }
        return true
    }

    fun decryptFromUri(ctx: Context, uri: Uri, password: String): ByteArray? {
        var plainData: ByteArray? = null
        var stream: FileInputStream
        try {
            stream = ctx.contentResolver.openInputStream(uri) as FileInputStream
        } catch(e: Exception) {
            Log.w(TAG, "decryptFromUri could not open stream: $e")
            return null
        }
        try {
            ObjectInputStream(stream).use {
                val content = it.readObject() as ByteArray
                plainData = decrypt(content, password.toCharArray())
            }
            stream.close()
        } catch (e: Exception) {
            Log.w(TAG, "decryptFromUri as ByteArray failed: $e")
            stream.close()
            try {
                stream = ctx.contentResolver.openInputStream(uri) as FileInputStream
                ObjectInputStream(stream).use {
                    val obj = it.readObject() as Crypto
                    plainData = decryptOld(obj, password.toCharArray())
                }
                stream.close()
            } catch (e: Exception) {
                Log.w(TAG, "decryptFromUri as Crypto failed: $e")
            }
        }
        return plainData
    }

    fun zip(fileNames: ArrayList<String>, zipFileName: String): Boolean {
        val zipFilePath = BaresipService.filesPath + "/" + zipFileName
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFilePath))).use { out ->
                val data = ByteArray(1024)
                for (fileName in fileNames) {
                    val filePath = BaresipService.filesPath + "/" + fileName
                    if (File(filePath).exists()) {
                        FileInputStream(filePath).use { fi ->
                            BufferedInputStream(fi).use { origin ->
                                val entry = ZipEntry(fileName)
                                out.putNextEntry(entry)
                                while (true) {
                                    val readBytes = origin.read(data)
                                    if (readBytes == -1) break
                                    out.write(data, 0, readBytes)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to zip file '$zipFilePath': $e")
            return false
        }
        return true
    }

    fun unZip(zipFilePath: String): Boolean {
        val allFiles = listOf("accounts", "call_history", "config", "contacts", "messages", "uuid",
                "gzrtp.zid", "cert.pem", "ca_cert", "ca_certs.crt")
        val zipFiles = mutableListOf<String>()
        try {
            ZipFile(File(zipFilePath)).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val entryName = if (entry.name.startsWith("/"))
                        entry.name.substringAfterLast("/")
                    else
                        entry.name
                    zipFiles.add(entryName)
                    zip.getInputStream(entry).use { input ->
                        File(BaresipService.filesPath + "/" + entryName).outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to unzip file '$zipFilePath': $e")
            return false
        }
        (allFiles - zipFiles.toSet()).iterator().forEach {
            deleteFile(File(BaresipService.filesPath, it))
        }
        return true
    }

    @Suppress("unused")
    fun dumpIntent(intent: Intent) {
        val bundle: Bundle = intent.extras ?: return
        val keys = bundle.keySet()
        val it = keys.iterator()
        Log.d(TAG, "Dumping intent start")
        while (it.hasNext()) {
            val key = it.next()
            Log.d(TAG, "[" + key + "=" + bundle.getBundle(key) + "]")
        }
        Log.d(TAG, "Dumping intent finish")
    }

    fun randomColor(): Int {
        val rnd = Random()
        return android.graphics.Color.argb(255, rnd.nextInt(256), rnd.nextInt(256),
                rnd.nextInt(256))
    }

    fun requestDismissKeyguard(activity: Activity) {
        val kgm = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        kgm.requestDismissKeyguard(activity, null)

    }

    fun isThemeDark(ctx: Context) : Boolean {
        return Preferences(ctx).displayTheme == AppCompatDelegate.MODE_NIGHT_YES ||
                ctx.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
    }

    fun relativeTime(ctx: Context, time: GregorianCalendar): String {
        return if (DateUtils.isToday(time.timeInMillis)) {
            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
            ctx.getString(R.string.today) + "\n" + fmt.format(time.time)
        } else {
            val month = time.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())!!
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val day = time.get(Calendar.DAY_OF_MONTH)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (time.get(Calendar.YEAR) == currentYear) {
                val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
                "$month $day" + "\n" + fmt.format(time.time)
            } else {
                "$month $day" + "\n" + time.get(Calendar.YEAR)
            }
        }
    }

    fun isSpeakerPhoneOn(am: AudioManager): Boolean {
        return if (Build.VERSION.SDK_INT >= 31)
             am.communicationDevice!!.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        else
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn
    }

    private fun setSpeakerPhone(executor: Executor, am: AudioManager, enable: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            val current = am.communicationDevice!!.type
            Log.d(TAG, "Current com dev/mode is $current/${am.mode}")
            var speakerDevice: AudioDeviceInfo? = null
            if (enable) {
                for (device in am.availableCommunicationDevices)
                    if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        speakerDevice = device
                        break
                    }
            } else {
                for (device in am.availableCommunicationDevices)
                    if (device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                        speakerDevice = device
                        break
                    }
            }
            if (speakerDevice == null) {
                Log.w(TAG,"Could not find requested communication device")
                return
            }
            if (current != speakerDevice.type) {
                if (speakerDevice.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                    clearCommunicationDevice(am)
                    Log.d(TAG, "Setting com device to TYPE_BUILTIN_EARPIECE")
                    if (!am.setCommunicationDevice(speakerDevice))
                        Log.e(TAG, "Could not set com device")
                    if (BaresipService.audioFocusRequest != null && am.mode == AudioManager.MODE_NORMAL) {
                        Log.d(TAG, "Setting mode to communication")
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                    }
                } else {
                    // Currently at API levels 31+, speakerphone needs normal mode
                    if (am.mode == AudioManager.MODE_NORMAL) {
                        Log.d(TAG, "Setting com device to ${speakerDevice.type} in MODE_NORMAL")
                        if (!am.setCommunicationDevice(speakerDevice))
                            Log.e(TAG, "Could not set com device")
                    } else {
                        val normalListener = object : AudioManager.OnModeChangedListener {
                            override fun onModeChanged(mode: Int) {
                                if (mode == AudioManager.MODE_NORMAL) {
                                    am.removeOnModeChangedListener(this)
                                    Log.d(
                                        TAG, "Setting com device to ${speakerDevice.type}" +
                                                " in mode ${am.mode}"
                                    )
                                    if (!am.setCommunicationDevice(speakerDevice))
                                        Log.e(TAG, "Could not set com device")
                                }
                            }
                        }
                        am.addOnModeChangedListener(executor, normalListener)
                        Log.d(TAG, "Setting mode to NORMAL")
                        am.mode = AudioManager.MODE_NORMAL
                    }
                }
                Log.d(TAG, "New com device/mode is ${am.communicationDevice!!.type}/${am.mode}")
            }
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = enable
            Log.d(TAG, "Speakerphone is $enable")
        }
    }

    fun toggleSpeakerPhone(executor: Executor, am: AudioManager) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (am.communicationDevice!!.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
                setSpeakerPhone(executor, am, true)
            else if (am.communicationDevice!!.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                setSpeakerPhone(executor, am, false)
        } else {
            @Suppress("DEPRECATION")
            setSpeakerPhone(executor, am, !am.isSpeakerphoneOn)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setCommunicationDevice(am: AudioManager, type: Int) {
        val current = am.communicationDevice!!.type
        Log.d(TAG, "Current com dev/mode $current/${am.mode}")
        for (device in am.availableCommunicationDevices)
            if (device.type == type) {
                am.setCommunicationDevice(device)
                break
            }
        Log.d(TAG, "New com dev/mode ${am.communicationDevice!!.type}/${am.mode}")
    }

    private fun clearCommunicationDevice(am: AudioManager) {
        if (Build.VERSION.SDK_INT >= 31) {
            am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            if (am.isSpeakerphoneOn)
                am.isSpeakerphoneOn = false
        }
    }

    @Suppress("unused")
    fun playFile(ctx: Context, path: String) {
        Log.d(TAG, "Playing file $path")
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener {
                Log.d(TAG, "Starting MediaPlayer")
                it.start()
                Log.d(TAG, "MediaPlayer started")
            }
            setOnCompletionListener {
                Log.d(TAG, "Stopping MediaPlayer")
                it.stop()
                it.release()
            }
            try {
                Log.d(TAG, "Preparing $path")
                setDataSource(ctx, path.toUri())
                prepareAsync()
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "MediaPlayer IllegalArgumentException: ${e.printStackTrace()}")
            } catch (e: IOException) {
                Log.e(TAG, "MediaPlayer IOException: ${e.printStackTrace()}")
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer Exception: ${e.printStackTrace()}")
            }
        }
    }

    fun aecAgcCheck() {
        val sessionId = Api.AAudio_open_stream()
        if (sessionId == -1) {
            Log.e(TAG, "Failed to open AAudio stream")
            return
        }

        if (AcousticEchoCanceler.isAvailable()) {
            val aec = AcousticEchoCanceler.create(sessionId)
            if (aec != null) {
                BaresipService.aecAvailable = true
                aec.release()
                Log.i(TAG, "Creation of hardware AEC for $sessionId succeeded")
            } else {
                Log.w(TAG, "Creation of hardware AEC for $sessionId failed")
            }
        }
        else
            Log.i(TAG, "Hardware AEC is NOT available")

        if (AutomaticGainControl.isAvailable()) {
            val agc = AcousticEchoCanceler.create(sessionId)
            if (agc != null) {
                BaresipService.agcAvailable = true
                agc.release()
                Log.d(TAG, "Creation of hardware AGC for $sessionId succeeded")
            } else {
                Log.w(TAG, "Creation of hardware AGC for $sessionId failed")
            }
        }
        else
            Log.i(TAG, "Hardware AGC is NOT available")

        Api.AAudio_close_stream()
    }

    fun readUrlWithCustomCAs(url: URL, caFile: File): String? {
        if (!caFile.exists()) {
            Log.d("Utils", "Custom CA file not found at ${caFile.path}")
            return null
        }

        try {
            // 1. Create a TrustManager that trusts the CAs in the user-provided file
            val customTrustManager = fun(): X509TrustManager {
                val certificateFactory = CertificateFactory.getInstance("X.509")
                val certificateInputStream = caFile.inputStream()
                // generateCertificates (plural) is crucial for loading all certs from the file
                val certificates = certificateFactory.generateCertificates(certificateInputStream)
                certificateInputStream.close()

                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                keyStore.load(null, null)
                certificates.forEachIndexed { index, certificate ->
                    keyStore.setCertificateEntry("user_ca_$index", certificate)
                }

                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(keyStore)
                return tmf.trustManagers.find { it is X509TrustManager } as X509TrustManager
            }()

            // 2. Create a TrustManager that trusts the default system CAs
            val systemTrustManager = fun(): X509TrustManager {
                val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                factory.init(null as KeyStore?) // A null keystore loads the system's default CAs
                return factory.trustManagers.find { it is X509TrustManager } as X509TrustManager
            }()

            // 3. Create a composite TrustManager that delegates to both system and custom CAs
            @SuppressLint("CustomX509TrustManager")
            val compositeTrustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    // This is for client certificate authentication, which you are not using.
                    // It's safe to just delegate to the system manager by default.
                    systemTrustManager.checkClientTrusted(chain, authType)
                }

                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    try {
                        // First, try to validate the chain with the system's default TrustManager.
                        systemTrustManager.checkServerTrusted(chain, authType)
                    } catch (_: CertificateException) {
                        // If that fails, and only if that fails, try to validate with our custom TrustManager.
                        // This will throw the final CertificateException if it also fails, which is the correct behavior.
                        customTrustManager.checkServerTrusted(chain, authType)
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    // Return a combined list of issuers from both trust managers.
                    return systemTrustManager.acceptedIssuers + customTrustManager.acceptedIssuers
                }
            }

            // 4. Create an SSLContext that uses our new composite TrustManager
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(compositeTrustManager), null)

            // 5. Tell HttpsURLConnection to use our custom SSLContext for this connection
            val urlConnection = url.openConnection() as HttpsURLConnection
            urlConnection.sslSocketFactory = sslContext.socketFactory

            // 6. Proceed with the connection and return the result
            return urlConnection.inputStream.bufferedReader().use { it.readText() }

        } catch (e: Exception) {
            // Catch any exception from certificate loading or from the network connection and log it
            Log.e("Utils", "readUrlWithCustomCa failed: ${e.message}")
            return null
        }
    }

    fun Bitmap.toCircle(): Bitmap {
        // Use full package names to avoid conflict with Compose classes
        val output = androidx.core.graphics.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint()
        val rect = android.graphics.Rect(0, 0, this.width, this.height)

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)

        canvas.drawCircle(this.width / 2f, this.height / 2f, this.width / 2f, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(this, rect, rect, paint)
        return output
    }

    fun createTextAvatar(letter: String, colorHex: String): Bitmap {
        // Standard notification large icon size is usually 64dp or 48dp.
        // We use a decent resolution (e.g. 128x128) and let Android scale it down.
        val size = 128

        // Use KTX createBitmap to match toCircle style
        val bitmap = androidx.core.graphics.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        // Use Fully Qualified Names to avoid Compose conflicts
        val canvas = android.graphics.Canvas(bitmap)

        // 1. Draw the colored circle background
        val bgPaint = android.graphics.Paint()
        bgPaint.isAntiAlias = true
        try {
            bgPaint.color = colorHex.toColorInt()
        } catch (_: Exception) {
            bgPaint.color = android.graphics.Color.GRAY // Fallback color
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        // 2. Draw the text (Initial)
        val textPaint = android.graphics.Paint()
        textPaint.isAntiAlias = true
        textPaint.color = android.graphics.Color.WHITE
        textPaint.textSize = size / 2f // Text size is half the circle size
        textPaint.textAlign = android.graphics.Paint.Align.CENTER

        // Use a bold font if possible to match your UI
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)

        // Calculate vertical center to center the text properly
        val bounds = android.graphics.Rect()
        textPaint.getTextBounds(letter, 0, letter.length, bounds)
        val yOffset = (bounds.bottom - bounds.top) / 2f

        // Draw text at Center X, Center Y + half text height (to visually center)
        canvas.drawText(letter.uppercase(), size / 2f, (size / 2f) + (yOffset / 2) + (bounds.height()/4), textPaint)

        return bitmap
    }

    @Suppress("unused")
    fun listFilesInDirectory(directoryPath: String): List<File> {
        val directory = File(directoryPath)
        if (!directory.exists()) {
            Log.w(TAG, "Directory does not exist: $directoryPath")
            return emptyList()
        }
        if (!directory.isDirectory) {
            Log.w(TAG, "Path is not a directory: $directoryPath")
            return emptyList()
        }
        val files = directory.listFiles()
        if (files == null) {
            Log.e(
                TAG,
                "Failed to list files in directory (listFiles returned null): $directoryPath"
            )
            return emptyList()
        }
        return files.filter { it.isFile }
    }

    @SuppressLint("RestrictedApi")
    @Suppress("unused", "DEPRECATION")
    fun printBackStack(navController: NavController) {
        Log.e(TAG, "---- Current Navigation Back Stack ----")
        navController.currentBackStack.value.forEachIndexed { index, navBackStackEntry ->
            val route = navBackStackEntry.destination.route
            val arguments = navBackStackEntry.arguments?.let { bundle ->
                bundle.keySet().joinToString(", ") { key -> "$key=${bundle.get(key)}" }
            } ?: "null"
            Log.e(TAG, "$index: Route='${route}', Args=[$arguments], ID=${navBackStackEntry.id}")
        }
        Log.e(TAG, "--------------------------------------")
    }

}

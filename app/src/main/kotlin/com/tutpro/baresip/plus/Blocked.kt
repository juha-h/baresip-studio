package com.tutpro.baresip.plus

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import java.io.File

@Serializable
class Blocked (
    val aor: String,
    val peerUri: String,
    val request: String,
    val timeStamp: Long
) {

    fun add() {
        synchronized(BaresipService.blocked) {
            BaresipService.blocked.add(this)
            val aorBlocked = BaresipService.blocked.filter { it.aor == this.aor && it.request == this.request }
            if (aorBlocked.size > BLOCKED_SIZE) {
                val oldestToRemove = aorBlocked.first()
                BaresipService.blocked.remove(oldestToRemove)
            }
        }
        save()
    }

    companion object {

        private const val BLOCKED_SIZE = 256

        fun clear(aor: String) {
            synchronized(BaresipService.blocked) {
                BaresipService.blocked.removeAll { it.aor == aor }
            }
            save()
        }

        fun remove(aor: String, peerUri: String) {
            synchronized(BaresipService.blocked) {
                BaresipService.blocked.removeAll { it.aor == aor && it.peerUri == peerUri }
            }
            save()
        }

        fun save() {
            if (!BaresipService.isNativeReady) return
            val blockedCopy = synchronized(BaresipService.blocked) {
                ArrayList(BaresipService.blocked)
            }
            Log.d(TAG, "Saving ${blockedCopy.size} blocked calls and messages")
            val file = File(BaresipService.filesPath + "/blocked")
            try {
                val jsonString = Json.encodeToString(blockedCopy)
                file.writeText(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Serialization exception", e)
            }
        }

        fun restore() {
            val file = File(BaresipService.filesPath + "/blocked")
            val oldFile = File(BaresipService.filesPath + "/blocked.json")
            if (oldFile.exists()) {
                Log.i(TAG, "Migrating blocked.json to blocked")
                oldFile.renameTo(file)
            }
            if (file.exists())
                try {
                    val jsonString = file.readText()
                    val blockedList = Json.decodeFromString<List<Blocked>>(jsonString)
                    synchronized(BaresipService.blocked) {
                        BaresipService.blocked.clear()
                        BaresipService.blocked.addAll(blockedList)
                    }
                    Log.d(TAG, "Restored ${BaresipService.blocked.size} blocked calls and messages")
                } catch (e: Exception) {
                    Log.e(TAG, "Deserialization exception: $e")
                }
        }
    }
}

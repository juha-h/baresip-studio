package com.tutpro.baresip

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
    private val blockedSize = 3

    fun add() {
        BaresipService.blocked.add(this)
        val aorBlocked = BaresipService.blocked.filter { it.aor == this.aor }
        if (aorBlocked.size > blockedSize) {
            val oldestToRemove = aorBlocked.first()
            BaresipService.blocked.remove(oldestToRemove)
        }
        save()
    }

    companion object {

        fun clear(aor: String) {
            val updatedBlockedList = BaresipService.blocked.filter { it.aor != aor }
            BaresipService.blocked = ArrayList(updatedBlockedList)
            save()
        }

        fun save() {
            Log.d(TAG, "Saving ${BaresipService.blocked.size} blocked calls and messages")
            val file = File(BaresipService.filesPath + "/blocked.json")
            try {
                val jsonString = Json.encodeToString(BaresipService.blocked)
                file.writeText(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Serialization exception: $e")
                e.printStackTrace()
            }
        }

        fun restore() {
            val file = File(BaresipService.filesPath + "/blocked.json")
            if (file.exists()) {
                try {
                    val jsonString = file.readText()
                    val blockedList = Json.decodeFromString<List<Blocked>>(jsonString)
                    BaresipService.blocked = ArrayList(blockedList)
                    Log.d(TAG, "Restored ${BaresipService.blocked.size} blocked calls and messages")
                } catch (e: Exception) {
                    Log.e(TAG, "Deserialization exception: - $e")
                }
            }
        }
    }
}

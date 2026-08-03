package com.tutpro.baresip

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
class BlockRule(val aor: String = "", val pattern: String) {
    fun matches(uri: String): Boolean {
        if (uri.contains(pattern, ignoreCase = true)) return true
        return try {
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(uri)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun exists(aor: String, pattern: String): Boolean {
            return BaresipService.blockRules.any { it.aor == aor && it.pattern == pattern }
        }

        fun clear(aor: String) {
            BaresipService.blockRules.removeAll { it.aor == aor }
            save()
        }

        fun save() {
            Log.d(TAG, "Saving ${BaresipService.blockRules.size} block rules")
            val file = File(BaresipService.filesPath + "/blocking")
            try {
                val jsonString = Json.encodeToString(BaresipService.blockRules)
                file.writeText(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Serialization exception", e)
            }
        }

        fun restore() {
            val file = File(BaresipService.filesPath + "/blocking")
            val oldFile = File(BaresipService.filesPath + "/blocking.json")
            if (oldFile.exists()) {
                Log.i(TAG, "Migrating blocking.json to blocking")
                oldFile.renameTo(file)
            }
            if (file.exists())
                try {
                    val jsonString = file.readText()
                    BaresipService.blockRules = Json.decodeFromString<MutableList<BlockRule>>(jsonString)
                    Log.d(TAG, "Restored ${BaresipService.blockRules.size} block rules")
                } catch (e: Exception) {
                    Log.e(TAG, "Deserialization exception: $e")
                }
        }

    }
}

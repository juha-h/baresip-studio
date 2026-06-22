package com.tutpro.baresip.plus

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
class BlockRule(
    val pattern: String
) {
    fun matches(uri: String): Boolean {
        if (uri.contains(pattern, ignoreCase = true)) return true
        return try {
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(uri)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun exists(pattern: String): Boolean {
            return BaresipService.blockRules.any { it.pattern == pattern }
        }

        fun save() {
            Log.d(TAG, "Saving ${BaresipService.blockRules.size} block rules")
            val file = File(BaresipService.filesPath + "/blocking.json")
            try {
                val jsonString = Json.encodeToString(BaresipService.blockRules)
                file.writeText(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Serialization exception", e)
            }
        }

        fun restore() {
            val file = File(BaresipService.filesPath + "/blocking.json")
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

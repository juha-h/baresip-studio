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

    fun add() {
        synchronized(BaresipService.blockRules) {
            BaresipService.blockRules.add(this)
            val aorRules = BaresipService.blockRules.filter { it.aor == this.aor }
            if (aorRules.size > BLOCK_RULE_SIZE) {
                val oldestToRemove = aorRules.first()
                BaresipService.blockRules.remove(oldestToRemove)
            }
        }
        save()
    }

    companion object {

        private const val BLOCK_RULE_SIZE = 256

        fun exists(aor: String, pattern: String): Boolean {
            synchronized(BaresipService.blockRules) {
                return BaresipService.blockRules.any { it.aor == aor && it.pattern == pattern }
            }
        }

        fun clear(aor: String) {
            synchronized(BaresipService.blockRules) {
                BaresipService.blockRules.removeAll { it.aor == aor }
            }
            save()
        }

        fun save() {
            if (!BaresipService.isNativeReady) return
            val rulesCopy = synchronized(BaresipService.blockRules) {
                ArrayList(BaresipService.blockRules)
            }
            Log.d(TAG, "Saving ${rulesCopy.size} block rules")
            val file = File(BaresipService.filesPath + "/blocking")
            try {
                val jsonString = Json.encodeToString(rulesCopy)
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
                    val blockRules = Json.decodeFromString<List<BlockRule>>(jsonString)
                    synchronized(BaresipService.blockRules) {
                        BaresipService.blockRules.clear()
                        BaresipService.blockRules.addAll(blockRules)
                    }
                    Log.d(TAG, "Restored ${BaresipService.blockRules.size} block rules")
                } catch (e: Exception) {
                    Log.e(TAG, "Deserialization exception: $e")
                }
        }

    }
}

package com.tutpro.baresip

import android.Manifest
import android.content.Context
import android.provider.CallLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.util.GregorianCalendar
import java.util.concurrent.Executors

@Serializable
class CallHistory(val aor: String, val peerUri: String, val direction: String) : java.io.Serializable {

    // Set to time when call is established (if ever) or stopTime if call was completed elsewhere
    @Serializable(with = Utils.GregorianCalendarSerializer::class)
    var startTime: GregorianCalendar? = null
    @Serializable(with = Utils.GregorianCalendarSerializer::class)
    var stopTime = GregorianCalendar()        // Set to time when call is closed
    var rejected = false
    var recording = arrayOf("", "")           // Encoder and decoder recording files, merged file is in [0]

    fun add() {
        synchronized(BaresipService.callHistory) {
            BaresipService.callHistory.add(this)
            val aorSpecificHistory = BaresipService.callHistory.filter { it.aor == this.aor }
            if (aorSpecificHistory.size > CALL_HISTORY_SIZE) {
                val oldestToRemove = aorSpecificHistory.first()
                deleteRecordingFiles(oldestToRemove.recording)
                BaresipService.callHistory.remove(oldestToRemove)
            }
        }
        save()
    }

    companion object {

        @Suppress("unused")
        const val serialVersionUID: Long = 3L
        private const val CALL_HISTORY_SIZE = 256

        fun aorLatestPeerUri(aor: String): String? {
            synchronized(BaresipService.callHistory) {
                for (h in BaresipService.callHistory.reversed())
                    if (h.aor == aor) return h.peerUri
            }
            return null
        }

        fun clear(aor: String) {
            synchronized(BaresipService.callHistory) {
                for (i in BaresipService.callHistory.indices.reversed()) {
                    val h = BaresipService.callHistory[i]
                    if (h.aor == aor) {
                        deleteRecordingFiles(h.recording)
                        BaresipService.callHistory.removeAt(i)
                    }
                }
            }
            save()
        }

        fun remove(startTime: GregorianCalendar?, stopTime: GregorianCalendar) {
            synchronized(BaresipService.callHistory) {
                val iterator = BaresipService.callHistory.iterator()
                while (iterator.hasNext()) {
                    val h = iterator.next()
                    if (h.startTime == startTime && h.stopTime == stopTime) {
                        deleteRecordingFiles(h.recording)
                        iterator.remove()
                    }
                }
            }
            save()
        }

        fun save() {
            if (!BaresipService.isNativeReady) return
            val historyCopy = synchronized(BaresipService.callHistory) {
                ArrayList(BaresipService.callHistory)
            }
            Log.d(TAG, "Saving history of ${historyCopy.size} calls")
            val file = File(BaresipService.filesPath + "/call_history")
            try {
                val jsonString = Json.encodeToString(historyCopy)
                file.writeText(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Serialization exception: $e")
                try {
                    val fos = FileOutputStream(file)
                    val oos = ObjectOutputStream(fos)
                    oos.writeObject(historyCopy)
                    oos.close()
                    fos.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "OutputStream exception: $e2")
                }
            }
        }

        fun restore() {
            val file = File(BaresipService.filesPath + "/call_history")
            if (file.exists()) {
                val content = file.readText()
                if (content.startsWith("[")) {
                    try {
                        val restoredHistory = Json.decodeFromString<List<CallHistory>>(content)
                        synchronized(BaresipService.callHistory) {
                            BaresipService.callHistory.clear()
                            BaresipService.callHistory.addAll(restoredHistory)
                        }
                        Log.d(TAG, "Restored history of ${BaresipService.callHistory.size} calls from JSON")
                        return
                    } catch (e: Exception) {
                        Log.d(TAG, "JSON restore failed, trying Java serialization: $e")
                    }
                }
                try {
                    val fis = FileInputStream(file)
                    val ois = object : ObjectInputStream(fis) {
                        override fun readClassDescriptor(): ObjectStreamClass {
                            val desc = super.readClassDescriptor()
                            if (desc.name == "com.tutpro.baresip.CallHistoryNew")
                                return ObjectStreamClass.lookup(CallHistory::class.java)
                            return desc
                        }
                    }
                    @Suppress("UNCHECKED_CAST")
                    val restoredHistory = ois.readObject() as? List<CallHistory>
                    if (restoredHistory != null) {
                        synchronized(BaresipService.callHistory) {
                            BaresipService.callHistory.clear()
                            BaresipService.callHistory.addAll(restoredHistory)
                        }
                        Log.d(TAG, "Restored history of ${BaresipService.callHistory.size} calls from Java")
                        save()
                    }
                    ois.close()
                    fis.close()
                } catch (e: Exception) {
                    Log.e(TAG, "InputStream exception: - $e")
                }
            }
        }

        fun deleteRecordingFiles(recording: Array<String>) {
            Utils.deleteFile(File(recording[0]))
            Utils.deleteFile(File(recording[1]))
        }

        fun copyFromSystemCallLog(context: Context, mobileAor: String) {
            if (!Utils.checkPermissions(context, arrayOf(Manifest.permission.READ_CALL_LOG))) return

            Executors.newSingleThreadExecutor().execute {
                try {
                    val projection = arrayOf(
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION
                    )

                    val cursor = context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${CallLog.Calls.DATE} DESC"
                    ) ?: return@execute

                    val newEntries = mutableListOf<CallHistory>()

                    cursor.use {
                        val numberCol = it.getColumnIndex(CallLog.Calls.NUMBER)
                        val typeCol = it.getColumnIndex(CallLog.Calls.TYPE)
                        val dateCol = it.getColumnIndex(CallLog.Calls.DATE)
                        val durationCol = it.getColumnIndex(CallLog.Calls.DURATION)

                        while (it.moveToNext()) {
                            val number = it.getString(numberCol) ?: continue
                            if (number.isEmpty()) continue
                            val type = it.getInt(typeCol)
                            val dateMs = it.getLong(dateCol)
                            val durationSec = it.getLong(durationCol)

                            val rawNumber = number.removePrefix("tel:").removePrefix("sip:").split("@")[0].replace("%23", "#")
                            val cleanNumber = Utils.uriUnescape(rawNumber).trim()
                            if (cleanNumber.isEmpty()) continue
                            val peerUri = "tel:$cleanNumber"

                            val direction = if (type == CallLog.Calls.OUTGOING_TYPE) "out" else "in"
                            val history = CallHistory(mobileAor, peerUri, direction)

                            val startDate = GregorianCalendar().apply { timeInMillis = dateMs }
                            val endDate = GregorianCalendar().apply { timeInMillis = dateMs + (durationSec * 1000) }

                            when (type) {
                                CallLog.Calls.INCOMING_TYPE, CallLog.Calls.OUTGOING_TYPE -> {
                                    history.startTime = startDate
                                    history.stopTime = endDate
                                }
                                CallLog.Calls.MISSED_TYPE -> {
                                    history.startTime = null
                                    history.stopTime = startDate
                                    history.rejected = false
                                }
                                CallLog.Calls.REJECTED_TYPE -> {
                                    history.startTime = null
                                    history.stopTime = startDate
                                    history.rejected = true
                                }
                                else -> continue
                            }

                            val isDuplicate = synchronized(BaresipService.callHistory) {
                                BaresipService.callHistory.any { existing ->
                                    existing.aor == mobileAor &&
                                    existing.peerUri == history.peerUri &&
                                    existing.stopTime.timeInMillis == history.stopTime.timeInMillis
                                }
                            }

                            if (!isDuplicate) {
                                newEntries.add(history)
                            }
                        }
                    }

                    if (newEntries.isNotEmpty()) {
                        synchronized(BaresipService.callHistory) {
                            BaresipService.callHistory.addAll(newEntries)
                            BaresipService.callHistory.sortBy { it.stopTime.timeInMillis }
                            val mobileHistory = BaresipService.callHistory.filter { it.aor == mobileAor }
                            if (mobileHistory.size > CALL_HISTORY_SIZE) {
                                val toRemoveCount = mobileHistory.size - CALL_HISTORY_SIZE
                                val toRemove = mobileHistory.take(toRemoveCount)
                                for (old in toRemove) {
                                    deleteRecordingFiles(old.recording)
                                    BaresipService.callHistory.remove(old)
                                }
                            }
                        }
                        save()
                        Log.d(TAG, "Copied ${newEntries.size} system call log entries to mobile call history")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy system call log: $e")
                }
            }
        }

        fun clearRecordings() {
            synchronized(BaresipService.callHistory) {
                for (h in BaresipService.callHistory)
                    h.recording = arrayOf("", "")
            }
        }

        @Suppress("UNUSED")
        fun print() {
            for (h in BaresipService.callHistory)
                Log.d(
                    TAG,
                    "[${h.aor}, ${h.peerUri}, ${h.direction}, ${h.startTime}," +
                        "${h.stopTime}, ${h.rejected}, ${h.recording}"
                )
        }

    }
}

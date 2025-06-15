package com.tutpro.baresip

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.GregorianCalendar

class CallHistoryNew(val aor: String, val peerUri: String, val direction: String) : Serializable {

    // Set to time when call is established (if ever) or stopTime if call was completed elsewhere
    var startTime: GregorianCalendar? = null
    var stopTime = GregorianCalendar()        // Set to time when call is closed
    var rejected = false
    var recording = arrayOf("", "")           // Encoder and decoder recording files

    fun add() {
        BaresipService.callHistory.add(this)
        var count = 0
        var remove: CallHistoryNew? = null
        for (h in BaresipService.callHistory)
            if (h.aor == this.aor) {
                count++
                if (count > CALL_HISTORY_SIZE) {
                    remove = h
                    break
                }
            }
        if (remove != null) {
            if (remove.recording[0] != "")
                deleteRecording(remove.recording)
            BaresipService.callHistory.remove(remove)
        }
        save()
    }

    companion object {

        private const val serialVersionUID: Long = 3
        private const val CALL_HISTORY_SIZE = 256

        fun aorLatestPeerUri(aor: String): String? {
            for (h in BaresipService.callHistory.reversed())
                if (h.aor == aor) return h.peerUri
            return null
        }

        fun clear(aor: String) {
            for (i in BaresipService.callHistory.indices.reversed()) {
                val h = BaresipService.callHistory[i]
                if (h.aor == aor) {
                    if (h.recording[0] != "")
                        deleteRecording(h.recording)
                    BaresipService.callHistory.removeAt(i)
                }
            }
            save()
        }

        fun save() {
            Log.d(TAG, "Saving history of ${BaresipService.callHistory.size} calls")
            val file = File(BaresipService.filesPath + "/call_history")
            try {
                val fos = FileOutputStream(file)
                val oos = ObjectOutputStream(fos)
                oos.writeObject(BaresipService.callHistory)
                oos.close()
                fos.close()
            } catch (e: IOException) {
                Log.e(TAG, "OutputStream exception: $e")
                e.printStackTrace()
            }
        }

        fun restore() {
            val file = File(BaresipService.filesPath + "/call_history")
            if (file.exists()) {
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    @Suppress("UNCHECKED_CAST")
                    BaresipService.callHistory = ois.readObject() as ArrayList<CallHistoryNew>
                    ois.close()
                    fis.close()
                    Log.d(TAG, "Restored history of ${BaresipService.callHistory.size} calls")
                } catch (e: Exception) {
                    Log.e(TAG, "InputStream exception: - $e")
                }
            }
        }

        fun deleteRecording(recording: Array<String>) {
            Utils.deleteFile(File(recording[0]))
            Utils.deleteFile(File(recording[1]))
        }

        @Suppress("UNUSED")
        fun print() {
            for (h in BaresipService.callHistory)
                Log.d(TAG, "[${h.aor}, ${h.peerUri}, ${h.direction}, ${h.startTime}," +
                        "${h.stopTime}, ${h.rejected}, ${h.recording}")
        }
    }

}

class CallHistory(val aor: String, val peerUri: String, val direction: String) : Serializable {

    var startTime: GregorianCalendar? = null
    var stopTime = GregorianCalendar()
    var recording = arrayOf("", "")

    companion object {

        private const val serialVersionUID: Long = 2

        fun get(): ArrayList<CallHistory> {
            val file = File(BaresipService.filesPath, "history")
            var result = ArrayList<CallHistory>()
            if (file.exists()) {
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    @Suppress("UNCHECKED_CAST")
                    result = ois.readObject() as ArrayList<CallHistory>
                    ois.close()
                    fis.close()
                    Log.d(TAG, "Got history of ${result.size} calls")
                    file.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "InputStream exception: - $e")
                }
            }
            return result
        }
    }

}
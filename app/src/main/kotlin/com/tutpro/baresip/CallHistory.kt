package com.tutpro.baresip

import java.io.*
import java.util.ArrayList
import java.util.GregorianCalendar

class CallHistory(val aor: String, val peerUri: String, val direction: String) : Serializable {

    var startTime: GregorianCalendar? = null  // Set to time when call is established (if ever)
    var stopTime = GregorianCalendar()        // Set to time when call is closed
    var recording = arrayOf("", "")           // Encoder and decoder recording files

    companion object {

        private const val serialVersionUID: Long = 2
        private const val CALL_HISTORY_SIZE = 128

        fun add(history: CallHistory) {
            BaresipService.callHistory.add(history)
            if (aorHistorySize(history.aor) > CALL_HISTORY_SIZE) {
                for (h in BaresipService.callHistory)
                    if (h.aor == history.aor) {
                        if (h.recording[0] != "")
                            deleteRecording(h.recording)
                        BaresipService.callHistory.remove(h)
                        break
                    }
            }
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
        }

        private fun aorHistorySize(aor: String): Int {
            return BaresipService.callHistory.count { it.aor == aor }
        }

        fun aorLatestPeerUri(aor: String): String? {
            for (h in BaresipService.callHistory.reversed())
                if (h.aor == aor) return h.peerUri
            return null
        }

        fun save() {
            Log.d(TAG, "Saving history of ${BaresipService.callHistory.size} calls")
            val file = File(BaresipService.filesPath + "/history")
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
            val file = File(BaresipService.filesPath + "/history")
            if (file.exists()) {
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    @Suppress("UNCHECKED_CAST")
                    BaresipService.callHistory = ois.readObject() as ArrayList<CallHistory>
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
                        "${h.stopTime}, ${h.recording}")
        }
    }

}

class NewCallHistory(val aor: String, val peerUri: String, val direction: String) : Serializable {

    var startTime: GregorianCalendar? = null
    var stopTime = GregorianCalendar()

    companion object {

        private const val serialVersionUID: Long = 1

        fun get(): ArrayList<NewCallHistory> {
            val file = File(BaresipService.filesPath, "call_history")
            var result = ArrayList<NewCallHistory>()
            if (file.exists()) {
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    @Suppress("UNCHECKED_CAST")
                    result = ois.readObject() as ArrayList<NewCallHistory>
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
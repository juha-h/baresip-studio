package com.tutpro.baresip

import java.io.*
import java.util.ArrayList
import java.util.GregorianCalendar

class NewCallHistory(val aor: String, val peerUri: String, val direction: String) : Serializable {

    var startTime: GregorianCalendar? = null  // Set to time when call is established (if ever)
    var stopTime = GregorianCalendar()  // Set to time when call is closed

    companion object {

        private const val serialVersionUID: Long = 1
        private const val CALL_HISTORY_SIZE = 128

        fun add(history: NewCallHistory) {
            BaresipService.callHistory.add(history)
            if (aorHistorySize(history.aor) > CALL_HISTORY_SIZE) {
                var i = 0
                for (h in BaresipService.callHistory)
                    if (h.aor == history.aor)
                        break
                    else
                        i++
                BaresipService.callHistory.removeAt(i)
            }
        }

        fun clear(aor: String) {
            val it = BaresipService.callHistory.iterator()
            while (it.hasNext()) if (it.next().aor == aor) it.remove()
        }

        private fun aorHistorySize(aor: String): Int {
            return BaresipService.callHistory.filter { it.aor == aor }.count()
        }

        fun aorLatestHistory(aor: String): NewCallHistory? {
            for (h in BaresipService.callHistory.reversed())
                if (h.aor == aor) return h
            return null
        }

        fun save() {
            Log.d(TAG, "Saving history of ${BaresipService.callHistory.size} calls")
            val file = File(BaresipService.filesPath, "call_history")
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
            val file = File(BaresipService.filesPath, "call_history")
            if (file.exists()) {
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    @Suppress("UNCHECKED_CAST")
                    BaresipService.callHistory = ois.readObject() as ArrayList<NewCallHistory>
                    ois.close()
                    fis.close()
                    Log.d(TAG, "Restored history of ${BaresipService.callHistory.size} calls")
                } catch (e: Exception) {
                    Log.e(TAG, "InputStream exception: - $e")
                }
            }
        }

        @Suppress("UNUSED")
        fun print() {
            for (h in BaresipService.callHistory)
                Log.d(TAG, "[${h.aor}, ${h.peerUri}, ${h.direction}, ${h.startTime}, ${h.stopTime}]")
        }
    }

}

class CallHistory(val aor: String, val peerUri: String, val direction: String,
                  val connected: Boolean) : Serializable {

    val time: GregorianCalendar = GregorianCalendar()

    companion object {

        fun get(): ArrayList<CallHistory> {
            val file = File(BaresipService.filesPath, "calls")
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
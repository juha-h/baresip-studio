package com.tutpro.baresip.plus

import java.io.*
import java.util.ArrayList
import java.util.GregorianCalendar

class CallHistory(val aor: String, val peerURI: String, val direction: String,
              val connected: Boolean) : Serializable {

    val time: GregorianCalendar = GregorianCalendar()

    companion object {

        const val CALL_HISTORY_SIZE = 100

        fun add(history: CallHistory) {
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

        fun aorHistorySize(aor: String): Int {
            return BaresipService.callHistory.filter { it.aor == aor }.count()
        }

        fun aorLatestHistory(aor: String): CallHistory? {
            for (h in BaresipService.callHistory.reversed())
                if (h.aor == aor) return h
            return null
        }

        fun save() {
            Log.d("Baresip", "Saving history of ${BaresipService.callHistory.size} calls")
            val file = File(BaresipService.filesPath, "calls")
            try {
                val fos = FileOutputStream(file)
                val oos = ObjectOutputStream(fos)
                oos.writeObject(BaresipService.callHistory)
                oos.close()
                fos.close()
            } catch (e: IOException) {
                Log.e("Baresip", "OutputStream exception: " + e.toString())
                e.printStackTrace()
            }
        }

        fun restore() {
            val file = File(BaresipService.filesPath, "calls")
            if (file.exists())
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    @Suppress("UNCHECKED_CAST")
                    BaresipService.callHistory = ois.readObject() as ArrayList<CallHistory>
                    ois.close()
                    fis.close()
                    Log.d("Baresip", "Restored history of ${BaresipService.callHistory.size} calls")
                } catch (e: Exception) {
                    Log.e("Baresip", "InputStream exception: - " + e.toString())
                }
        }

        fun print() {
            for (h in BaresipService.callHistory)
                Log.d("Baresip", "[${h.aor}, ${h.peerURI}, ${h.direction}, ${h.connected}]")
        }
    }

}

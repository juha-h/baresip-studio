package com.tutpro.baresip

import java.io.*
import java.util.ArrayList
import java.util.GregorianCalendar

class CallHistory(val aor: String, val peerURI: String, val direction: String,
              val connected: Boolean) : Serializable {

    val time: GregorianCalendar = GregorianCalendar()

    companion object {

        const val CALL_HISTORY_SIZE = 100

        fun history(): ArrayList<CallHistory> {
            return BaresipService.history
        }

        fun add(history: CallHistory) {
            BaresipService.history.add(history)
            if (aorHistorySize(history.aor) > CALL_HISTORY_SIZE) {
                var i = 0
                for (h in BaresipService.history)
                    if (h.aor == history.aor)
                        break
                    else
                        i++
                BaresipService.history.removeAt(i)
            }
        }

        fun clear(aor: String) {
            val it = BaresipService.history.iterator()
            while (it.hasNext()) if (it.next().aor == aor) it.remove()
        }

        fun aorHistorySize(aor: String): Int {
            return BaresipService.history.filter { it.aor == aor }.count()
        }

        fun aorLatestHistory(aor: String): CallHistory? {
            for (h in BaresipService.history.reversed())
                if (h.aor == aor) return h
            return null
        }

        fun save() {
            Log.d("Baresip", "Saving call history of size ${BaresipService.history.size}")
            val file = File(BaresipService.filesPath, "history")
            try {
                val fos = FileOutputStream(file)
                val oos = ObjectOutputStream(fos)
                oos.writeObject(BaresipService.history)
                oos.close()
                fos.close()
            } catch (e: IOException) {
                Log.e("Baresip", "OutputStream exception: " + e.toString())
                e.printStackTrace()
            }
        }

        fun restore() {
            val file = File(BaresipService.filesPath, "history")
            if (file.exists())
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    BaresipService.history = ois.readObject() as ArrayList<CallHistory>
                    ois.close()
                    fis.close()
                    Log.d("Baresip", "Restored call history of size ${BaresipService.history.size}")
                } catch (e: Exception) {
                    Log.e("Baresip", "InputStream exception: - " + e.toString())
                }
        }

        fun print() {
            for (h in BaresipService.history)
                Log.d("Baresip", "[${h.aor}, ${h.peerURI}, ${h.direction}, ${h.connected}]")
        }
    }

}

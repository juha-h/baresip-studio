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

        fun aorHistorySize(aor: String): Int {
            var size = 0;
            for (h in BaresipService.history) {
                if (h.aor == aor) size++
            }
            return size
        }

        fun aorLatestHistory(aor: String): CallHistory? {
            for (h in BaresipService.history.reversed())
                if (h.aor == aor) return h
            return null
        }

        fun save(path: String) {
            Log.d("Baresip", "Saving history of size ${BaresipService.history.size}")
            val file = File(path, "history")
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

        fun restore(path: String) {
            val file = File(path + "/history")
            if (file.exists()) {
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    BaresipService.history = ois.readObject() as ArrayList<CallHistory>
                    ois.close()
                    fis.close()
                    Log.d("Baresip", "Restored history of size ${BaresipService.history.size}")
                } catch (e: Exception) {
                    Log.e("Baresip", "InputStream exception: - " + e.toString())
                }
            }

        }
    }

}

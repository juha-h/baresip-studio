package com.tutpro.baresip

import java.io.*
import java.util.ArrayList

class Message(val aor: String, val peerUri: String, val message: String, val timeStamp: Long,
              var direction: Int, var responseCode: Int, var responseReason: String,
              var new: Boolean): Serializable {

    fun add() {
        BaresipService.messages.add(this)
        var count = 0
        var firstIndex = -1
        for (i in BaresipService.messages.indices)
            if (BaresipService.messages[i].aor == this.aor) {
                if (count == 0) firstIndex = i
                count++
                if (count > MESSAGE_HISTORY_SIZE) {
                    break
                }
            }
        if (count > MESSAGE_HISTORY_SIZE)
            BaresipService.messages.removeAt(firstIndex)
    }

    companion object {

        const val MESSAGE_HISTORY_SIZE = 100

        fun messages(): ArrayList<Message> {
            return BaresipService.messages
        }

        fun clear(aor: String) {
            val it = BaresipService.messages.iterator()
            while (it.hasNext()) if (it.next().aor == aor) it.remove()
        }

        fun save() {
            val file = File(BaresipService.filesPath, "messages")
            try {
                val fos = FileOutputStream(file)
                val oos = ObjectOutputStream(fos)
                oos.writeObject(BaresipService.messages)
                oos.close()
                fos.close()
                Log.d(TAG, "Saved ${BaresipService.messages.size} messages")
            } catch (e: IOException) {
                Log.e(TAG, "OutputStream exception: $e")
                e.printStackTrace()
            }
        }

        fun restore() {
            val file = File(BaresipService.filesPath, "messages")
            if (file.exists()) {
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    @Suppress("UNCHECKED_CAST")
                    BaresipService.messages = ois.readObject() as ArrayList<Message>
                    ois.close()
                    fis.close()
                    Log.d(TAG, "Restored ${BaresipService.messages.size} messages")
                } catch (e: Exception) {
                    Log.e(TAG, "InputStream exception: - $e")
                }
            }
        }

    }
}

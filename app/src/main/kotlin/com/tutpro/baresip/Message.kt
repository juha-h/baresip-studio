package com.tutpro.baresip

import java.io.*
import java.util.ArrayList

class Message(val aor: String, val peerUri: String, val message: String, val timeStamp: Long,
              var direction: Int, var responseCode: Int, var responseReason: String,
              var new: Boolean): Serializable {

    companion object {

        val MESSAGE_HISTORY_SIZE = 100

        fun messages(): ArrayList<Message> {
            return BaresipService.messages
        }

        fun add(message: Message) {
            BaresipService.messages.add(message)
            var count = 0
            var firstIndex = -1
            for (i in BaresipService.messages.indices)
                if (BaresipService.messages[i].aor == message.aor) {
                    if (count == 0) firstIndex = i
                    count++
                    if (count > MESSAGE_HISTORY_SIZE) {
                        break
                    }
                }
            if (count > MESSAGE_HISTORY_SIZE)
                BaresipService.messages.removeAt(firstIndex)
        }

        fun saveMessages(path: String) {
            val file = File(path, "messages")
            try {
                val fos = FileOutputStream(file)
                val oos = ObjectOutputStream(fos)
                oos.writeObject(BaresipService.messages)
                oos.close()
                fos.close()
                Log.d("Baresip", "Saved ${BaresipService.messages.size} messages")
            } catch (e: IOException) {
                Log.e("Baresip", "OutputStream exception: " + e.toString())
                e.printStackTrace()
            }
        }

        fun restoreMessages(path: String) {
            val file = File(path, "messages")
            if (file.exists()) {
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    BaresipService.messages = ois.readObject() as ArrayList<Message>
                    ois.close()
                    fis.close()
                    Log.d("Baresip", "Restored ${BaresipService.messages.size} messages")
                } catch (e: Exception) {
                    Log.e("Baresip", "InputStream exception: - " + e.toString())
                }
            }
        }


    }
}

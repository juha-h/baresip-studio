package com.tutpro.baresip.plus

import java.io.*
import java.util.ArrayList

class Message(val aor: String, val peerUri: String, val message: String, val timeStamp: Long,
              var direction: Int, var responseCode: Int, var responseReason: String,
              var new: Boolean): Serializable {

    fun add() {
        val updatedMessages = BaresipService.messages.toMutableList()
        updatedMessages.add(this)
        BaresipService.messages = updatedMessages.toList()
        var count = 0
        var remove: Message? = null
        for (message in BaresipService.messages)
            if (message.aor == this.aor) {
                count++
                if (count > MESSAGE_HISTORY_SIZE) {
                    remove = message
                    break
                }
            }
        if (remove != null)
            updatedMessages.remove(remove)
        BaresipService.messages = updatedMessages.toList()
        save()
    }

    fun delete() {
        val updatedMessages = BaresipService.messages.toMutableList()
        updatedMessages.remove(this)
        BaresipService.messages = updatedMessages.toList()
        save()
    }

    companion object {

        const val MESSAGE_HISTORY_SIZE = 100

        fun messages(): List<Message> {
            return BaresipService.messages
        }

        fun clearMessagesOfAor(aor: String) {
            val updatedMessages = BaresipService.messages.toMutableList()
            val it = updatedMessages.iterator()
            while (it.hasNext()) if (it.next().aor == aor) it.remove()
            BaresipService.messages = updatedMessages.toList()
        }

        fun deleteAorMessage(aor: String, time: Long) {
            val updatedMessages = BaresipService.messages.toMutableList()
            for (message in updatedMessages)
                if (message.aor == aor && message.timeStamp == time) {
                    updatedMessages.remove(message)
                    BaresipService.messages = updatedMessages.toList()
                    save()
                    return
                }
        }

        fun deleteAorPeerMessages(aor: String, peerUri: String) {
            val updatedMessages = BaresipService.messages.toMutableList()
            for (message in updatedMessages)
                if (message.aor == aor && message.peerUri == peerUri)
                    updatedMessages.remove(message)
            BaresipService.messages = updatedMessages.toList()
            save()
        }

        fun updateAorMessage(aor: String, time: Long) {
            val updatedMessages = BaresipService.messages.toMutableList()
            for (message in updatedMessages)
                if (message.aor == aor && message.timeStamp == time) {
                    message.new = false
                    BaresipService.messages = updatedMessages.toList()
                    save()
                    return
                }
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

package com.tutpro.baresip

import java.io.*

class Message(val aor: String, val peerUri: String, val message: String, val timeStamp: Long,
              var direction: Int, var responseCode: Int, var responseReason: String,
              var new: Boolean): Serializable {

    fun add() {
        val updatedMessages = synchronized(BaresipService.messages) {
            BaresipService.messages.toMutableList()
        }
        updatedMessages.add(this)
        var count = 0
        var remove: Message? = null
        for (message in updatedMessages)
            if (message.aor == this.aor) {
                count++
                if (count > MESSAGE_HISTORY_SIZE) {
                    remove = message
                    break
                }
            }
        if (remove != null)
            updatedMessages.remove(remove)
        synchronized(BaresipService.messages) {
            BaresipService.messages = updatedMessages.toList()
        }
        save()
    }

    fun delete() {
        val updatedMessages = synchronized(BaresipService.messages) {
            BaresipService.messages.toMutableList()
        }
        updatedMessages.remove(this)
        synchronized(BaresipService.messages) {
            BaresipService.messages = updatedMessages.toList()
        }
        save()
    }

    companion object {

        const val MESSAGE_HISTORY_SIZE = 100

        fun messages(): List<Message> {
            return BaresipService.messages
        }

        fun clearMessagesOfAor(aor: String) {
            val updatedMessages = synchronized(BaresipService.messages) {
                BaresipService.messages.toMutableList()
            }
            val it = updatedMessages.iterator()
            while (it.hasNext()) if (it.next().aor == aor) it.remove()
            synchronized(BaresipService.messages) {
                BaresipService.messages = updatedMessages.toList()
            }
            save()
        }

        fun deleteAorMessage(aor: String, time: Long) {
            val updatedMessages = synchronized(BaresipService.messages) {
                BaresipService.messages.toMutableList()
            }
            for (message in updatedMessages.reversed())
                if (message.aor == aor && message.timeStamp == time) {
                    updatedMessages.remove(message)
                    synchronized(BaresipService.messages) {
                        BaresipService.messages = updatedMessages.toList()
                    }
                    save()
                    return
                }
        }

        fun updateAorMessage(aor: String, time: Long) {
            val updatedMessages = synchronized(BaresipService.messages) {
                BaresipService.messages.toMutableList()
            }
            for (message in updatedMessages.reversed())
                if (message.aor == aor && message.timeStamp == time) {
                    message.new = false
                    synchronized(BaresipService.messages) {
                        BaresipService.messages = updatedMessages.toList()
                    }
                    save()
                    return
                }
        }

        fun unreadMessages(aor: String): Boolean {
            synchronized(BaresipService.messages) {
                for (message in BaresipService.messages.reversed())
                    if (message.aor == aor && message.new)
                        return true
            }
            return false
        }

        fun unreadMessagesFromPeer(aor: String, peerUri: String): Boolean {
            synchronized(BaresipService.messages) {
                for (message in BaresipService.messages.reversed())
                    if (message.aor == aor && message.peerUri == peerUri && message.new)
                        return true
            }
            return false
        }

        fun updateMessagesFromPearRead(aor: String, peerUri: String): Boolean {
            val updatedMessages = synchronized(BaresipService.messages) {
                BaresipService.messages.toMutableList()
            }
            var updated = false
            for (message in updatedMessages)
                if (message.aor == aor && message.peerUri == peerUri && message.new) {
                    message.new = false
                    updated = true
                }
            if (updated) {
                synchronized(BaresipService.messages) {
                    BaresipService.messages = updatedMessages.toList()
                }
                save()
            }
            return updated
        }

        fun save() {
            val messagesCopy = synchronized(BaresipService.messages) {
                BaresipService.messages.toList()
            }
            val file = File(BaresipService.filesPath, "messages")
            try {
                val fos = FileOutputStream(file)
                val oos = ObjectOutputStream(fos)
                oos.writeObject(messagesCopy)
                oos.close()
                fos.close()
                Log.d(TAG, "Saved ${messagesCopy.size} messages")
            } catch (e: IOException) {
                Log.e(TAG, "OutputStream exception", e)
            }
        }

        fun restore() {
            val file = File(BaresipService.filesPath, "messages")
            if (file.exists()) {
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    @Suppress("UNCHECKED_CAST")
                    val restoredMessages = ois.readObject() as? List<Message>
                    if (restoredMessages != null)
                        BaresipService.messages = restoredMessages
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

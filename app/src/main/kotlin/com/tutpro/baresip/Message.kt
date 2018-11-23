package com.tutpro.baresip

import java.io.*
import java.util.ArrayList

class Message(val aor: String, val peerUri: String, var direction: Int, val message: String,
              val timeStamp: Long, var new: Boolean): Serializable {

    companion object {

        val MESSAGE_HISTORY_SIZE = 100

        fun messages(): ArrayList<Message> {
            return BaresipService.messages
        }

        fun messages(messages: ArrayList<Message>) {
            BaresipService.messages = messages
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

    }
}

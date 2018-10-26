package com.tutpro.baresip

import java.io.Serializable
import java.util.GregorianCalendar

class CallHistory(val aor: String, val peerURI: String, val direction: String,
              val connected: Boolean) : Serializable {

    val time: GregorianCalendar = GregorianCalendar()

    companion object {

        fun aorHistory(history: ArrayList<CallHistory>, aor: String): Int {
            var size = 0;
            for (h in history) {
                if (h.aor == aor) size++
            }
            return size
        }

        fun aorRemoveHistory(history: ArrayList<CallHistory>, aor: String) {
            for (h in history) {
                if (h.aor == aor) {
                    history.remove(h)
                    return
                }
            }
        }

        fun aorLatestHistory(history: ArrayList<CallHistory>, aor: String): CallHistory? {
            for (h in history.reversed())
                if (h.aor == aor) return h
            return null
        }

    }

}

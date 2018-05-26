package com.tutpro.baresip

import java.io.Serializable
import java.util.GregorianCalendar

class History(val aor: String, val peerURI: String, val direction: String,
              val connected: Boolean) : Serializable {

    val time: GregorianCalendar = GregorianCalendar()

    companion object {

        fun aorHistory(history: ArrayList<History>, aor: String): Int {
            var size = 0;
            for (h in history) {
                if (h.aor == aor) size++
            }
            return size
        }

        fun aorRemoveHistory(history: ArrayList<History>, aor: String) {
            for (h in history) {
                if (h.aor == aor) {
                    history.remove(h)
                    return
                }
            }
        }

        fun aorLatestHistory(history: ArrayList<History>, aor: String): History? {
            for (h in history.reversed())
                if (h.aor == aor) return h
            return null
        }

    }


}

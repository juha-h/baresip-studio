package com.tutpro.baresip

class UserAgent (val uap: String) {

    val account = Account(Api.ua_account(uap))

    companion object {

        fun uas(): ArrayList<UserAgent> {
            return BaresipService.uas
        }

        fun status(): ArrayList<Int> {
            return BaresipService.status
        }

        fun add(ua: UserAgent, status: Int) {
            BaresipService.uas.add(ua)
            BaresipService.status.add(status)
        }

        fun remove(ua: UserAgent) {
            val index = BaresipService.uas.indexOf(ua)
            if (index != -1) {
                BaresipService.uas.removeAt(index)
                BaresipService.status.removeAt(index)
            }
        }

        fun updateStatus(ua: UserAgent, status: Int) {
            val index = BaresipService.uas.indexOf(ua)
            if (index != -1) {
                BaresipService.status[index] = status
            }
        }

        fun uaAlloc(uri: String): UserAgent? {
            val uap = Api.ua_alloc(uri)
            if (uap != "") return UserAgent(uap)
            return null
        }

        fun find(uap: String): UserAgent? {
            for (ua in BaresipService.uas) {
                if (ua.uap == uap) return ua
            }
            return null
        }

        fun findAorIndex(aor: String): Int? {
            for (i in BaresipService.uas.indices) {
                if (BaresipService.uas[i].account.aor == aor) return i
            }
            return null
        }

        fun register() {
            for (ua in BaresipService.uas) {
                if (ua.account.regint > 0)
                    if (Api.ua_register(ua.uap) != 0)
                        Log.d("Baresip", "Failed to register ${ua.account.aor}")
            }
        }
    }
}


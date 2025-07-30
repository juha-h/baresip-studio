package com.tutpro.baresip

import com.tutpro.baresip.BaresipService.Companion.circleYellow
import com.tutpro.baresip.BaresipService.Companion.colorblind
import com.tutpro.baresip.BaresipService.Companion.uas
import com.tutpro.baresip.BaresipService.Companion.uasStatus

class UserAgent(val uap: Long) {

    val account = Account(Api.ua_account(uap))
    var status = R.drawable.circle_white

    fun callAlloc(xCall: Long, videoMode: Int): Long {
        return Api.ua_call_alloc(uap, xCall, videoMode)
    }

    fun add() {
        val updatedUas = uas.value.toMutableList()
        updatedUas.add(this)
        uas.value = updatedUas.toList()
        uasStatus.value = statusMap()
    }

    fun remove() {
        val updatedUas = uas.value.toMutableList()
        updatedUas.remove(this)
        uas.value = updatedUas.toList()
        uasStatus.value = statusMap()
    }

    fun updateStatus(status: Int) {
        uas.value.find { it.uap == this.uap }?.status = status
        uasStatus.value = statusMap()
    }

    fun calls(dir: String = ""): ArrayList<Call> {
        val result = ArrayList<Call>()
        for (c in BaresipService.calls)
            if ((c.ua == this) && ((dir == "") || c.dir == dir)) result.add(c)
        return result
    }

    fun currentCall(): Call? {
        for (c in BaresipService.calls)
            if (c.ua == this)
                return c
        return null
    }

    fun reRegister() {
        this.status = circleYellow.getValue(colorblind)
        if (this.account.regint == 0)
            Api.ua_unregister(this.uap)
        else
            Api.ua_register(this.uap)
    }

    fun makeDefault() {
        val index = uas.value.indexOf(this)
        val updatedUas = uas.value.toMutableList()
        updatedUas.removeAt(index)
        updatedUas.add(0, this)
        uas.value = updatedUas.toList()
        uasStatus.value = statusMap()
    }

    companion object {

        fun ofAor(aor: String): UserAgent? {
            for (ua in uas.value)
                if (ua.account.aor == aor) return ua
            return null
        }

        fun ofDomain(domain: String): UserAgent? {
            for (ua in uas.value)
                if (Utils.aorDomain(ua.account.aor) == domain) return ua
            return null
        }

        fun ofUap(uap: Long): UserAgent? {
            for (ua in uas.value)
                if (ua.uap == uap) return ua
            return null
        }

        fun uaAlloc(uri: String): UserAgent? {
            val uap = Api.ua_alloc(uri)
            if (uap != 0L) return UserAgent(uap)
            Log.e(TAG, "Failed to allocate UserAgent for $uri")
            return null
        }

        fun findAorIndex(aor: String): Int? {
            for (i in uas.value.indices) {
                if (uas.value[i].account.aor == aor) return i
            }
            return null
        }

        fun register() {
            for (ua in uas.value) {
                if (ua.account.regint > 0) {
                    if (Api.ua_register(ua.uap) != 0)
                        Log.d(TAG, "Failed to register ${ua.account.aor}")
                }
            }
        }

        fun updateColorblindStatus() {
            val updatedUas = uas.value.toMutableList()
            for (ua in updatedUas)
                ua.status =
                    if (colorblind)
                        when (ua.status) {
                            R.drawable.circle_green -> R.drawable.circle_green_blind
                            R.drawable.circle_yellow -> R.drawable.circle_yellow_blind
                            R.drawable.circle_red -> R.drawable.circle_red_blind
                            else -> R.drawable.circle_white
                        }
                    else
                        when (ua.status) {
                            R.drawable.circle_green_blind -> R.drawable.circle_green
                            R.drawable.circle_yellow_blind -> R.drawable.circle_yellow
                            R.drawable.circle_red_blind -> R.drawable.circle_red
                            else -> R.drawable.circle_white
                        }
            uas.value = updatedUas.toList()
            uasStatus.value = statusMap()
        }

        fun statusMap(): Map<String, Int> {
            val result = emptyMap<String, Int>().toMutableMap()
            for (ua in uas.value)
                result[ua.account.aor] = ua.status
            return result
        }
    }
}


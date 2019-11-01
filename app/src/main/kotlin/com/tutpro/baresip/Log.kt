package com.tutpro.baresip

object Log {

    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR, OFF
    }

    var logLevel: LogLevel = LogLevel.INFO

    fun logLevelSet(value: Int) {
        when (value) {
            0 -> logLevel = LogLevel.DEBUG
            1 -> logLevel = LogLevel.INFO
            2 -> logLevel = LogLevel.WARN
            3 -> logLevel = LogLevel.ERROR
            4 -> logLevel = LogLevel.OFF
        }
    }

    fun d(tag: String, msg: String) {
        if (logLevel < LogLevel.INFO) android.util.Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (logLevel < LogLevel.WARN) android.util.Log.i(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (logLevel < LogLevel.ERROR) android.util.Log.w(tag, msg)
    }

    fun e(tag: String, msg: String) {
        if (logLevel < LogLevel.OFF) android.util.Log.w(tag, msg)
    }
}
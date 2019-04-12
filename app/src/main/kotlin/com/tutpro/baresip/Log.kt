package com.tutpro.baresip

object Log {

    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR, OFF
    }

    var logLevel: LogLevel = LogLevel.INFO

    fun logLevelSet(value: Int) {
        when (value) {
            0 -> logLevel = Log.LogLevel.DEBUG
            1 -> logLevel = Log.LogLevel.INFO
            2 -> logLevel = Log.LogLevel.WARN
            3 -> logLevel = Log.LogLevel.ERROR
            4 -> logLevel = Log.LogLevel.OFF
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
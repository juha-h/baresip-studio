package com.tutpro.baresip

import java.util.concurrent.atomic.AtomicBoolean

open class Event<out T>(private val content: T) {
    private val hasBeenHandled = AtomicBoolean(false)
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled.get()) {
            null
        } else {
            hasBeenHandled.set(true)
            content
        }
    }
}
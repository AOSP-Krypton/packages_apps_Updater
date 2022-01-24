package com.krypton.updater.data

class Event<out T>(
    private val data: T
) {
    var hasBeenHandled = false
        private set

    fun getOrNull(): T? =
        if (hasBeenHandled) null
        else {
            hasBeenHandled = true
            data
        }

    fun peek(): T = data
}
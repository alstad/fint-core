package org.example.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
class Printer(val message: String) {
    fun printMessage() =
        runBlocking {
            val now: Instant = Clock.System.now()
            launch {
                delay(1000L)
                println(now.toString())
            }
            println(message)
        }
}

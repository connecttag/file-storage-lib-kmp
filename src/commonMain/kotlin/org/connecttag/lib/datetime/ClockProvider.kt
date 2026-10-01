package org.connecttag.lib.datetime

import kotlin.time.Clock

/**
 * Interface to provide the current time.
 * Essential for testing time-dependent logic.
 */
interface ClockProvider {
    /**
     * Returns the current time in milliseconds since epoch.
     */
    fun nowMillis(): Long
}

/**
 * Platform clock implementation using Kotlin stdlib Clock.
 */
object SystemClockProvider : ClockProvider {
    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

/**
 * A fake clock provider for testing purposes.
 */
class FakeClockProvider(
    initialMillis: Long = 0L,
) : ClockProvider {
    private var currentMillis: Long = initialMillis

    override fun nowMillis(): Long = currentMillis

    fun setTime(millis: Long) {
        currentMillis = millis
    }

    fun setNowMillis(millis: Long) {
        currentMillis = millis
    }

    fun advanceBy(millis: Long) {
        currentMillis += millis
    }
}

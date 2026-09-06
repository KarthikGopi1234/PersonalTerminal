package dev.personalterminal.domain

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Single source of "now" for the app. Production uses the system clock; the screenshot / golden
 * suite pins it so rendered screens are identical from one day to the next.
 */
object AppClock {
    @Volatile var clock: Clock = Clock.systemDefaultZone()

    fun today(): LocalDate = LocalDate.now(clock)
    fun now(): LocalDateTime = LocalDateTime.now(clock)
}

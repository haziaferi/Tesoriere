@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere.engine

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Ported from Sci-Fin native/shared/src/jvmCommon/kotlin/app/scifin/engine/SystemClock.kt at 2892110.
 */

/** The real clock: the day is the device's local day, the logged moment a UTC instant (SPEC R20). */
object SystemClock : Clock {
    override fun today() = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    override fun now(): Instant = kotlin.time.Clock.System.now()
}

/** Ids are UUID strings, unique across Profiles by construction (SPEC R20). */
fun newId(): String = UUID.randomUUID().toString()

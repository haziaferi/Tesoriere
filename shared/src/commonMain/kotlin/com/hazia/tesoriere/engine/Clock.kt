@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere.engine

import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Ledger.kt at 2892110,
 * where the interface sat beside the ledger.
 */

/** The engine's clock, injected so every rule about "now" is testable. */
interface Clock {
    /** The device's calendar day (SPEC R20). */
    fun today(): LocalDate
    /** The instant an entry is logged (SPEC R20). */
    fun now(): Instant
}

package com.hazia.tesoriere.model

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/model/Money.kt at 2892110.
 * Sci-Fin's moneyOf(Double) is not ported: no floating-point type holds money (SPEC R20).
 */

/**
 * Money is a `Long` in minor units (cents) everywhere in the engine (SPEC R20).
 *
 * A typealias rather than a value class: Room's KMP codegen does not store value classes, and
 * the engine's arithmetic (`+`, `-`, comparisons, `sumOf`) reads best on the primitive.
 */
typealias Money = Long

/** 100 minor units per major unit for every currency Tesoriere offers. */
const val MINOR_UNITS_PER_MAJOR: Long = 100

/** Rounding up to the minor unit (SPEC R55): the smallest Money that, times [n], covers [this]. */
fun Money.ceilDiv(n: Long): Money = if (n <= 0) this else (this + n - 1) / n

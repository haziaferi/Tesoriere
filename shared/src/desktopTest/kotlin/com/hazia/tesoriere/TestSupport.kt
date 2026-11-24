@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere

import com.hazia.tesoriere.engine.Clock
import com.hazia.tesoriere.model.Money
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A clock the tests move by hand. `now` starts at the given day's 10:00 UTC. */
class TestClock(day: LocalDate) : Clock {
    var today: LocalDate = day
    var now: Instant = Instant.parse("${day}T10:00:00Z")
    override fun today() = today
    override fun now() = now
    fun advance(by: Duration) { now += by }
    fun setDay(day: LocalDate) { today = day; now = Instant.parse("${day}T10:00:00Z") }
}

/** Ids that count up, readable in a failure message. */
class Ids(private val prefix: String = "t") {
    private var n = 0
    fun next(): String = "$prefix-${++n}"
}

private val CENTS = Regex("""(-?)(\d{1,3}(?:,\d{3})*|\d+)\.(\d{2})""")

/**
 * A figure as the spec writes it, read into cents with integer arithmetic only (SPEC R20):
 * `"42.30"` is 4230, `"1,000.00"` is 100000, `"-60.00"` is -6000. Two decimals are required, so
 * a test cannot mean 42.3 and write 42.03.
 */
fun cents(text: String): Money {
    val m = requireNotNull(CENTS.matchEntire(text)) { "not a figure with two decimals: $text" }
    val (sign, units, hundredths) = m.destructured
    val value = units.replace(",", "").toLong() * 100 + hundredths.toLong()
    return if (sign == "-") -value else value
}

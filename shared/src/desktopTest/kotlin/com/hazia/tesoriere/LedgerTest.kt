@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.goal
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.engine.Ledger
import com.hazia.tesoriere.engine.TransactionKey
import com.hazia.tesoriere.engine.Writes
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/LedgerTest.kt at 2892110,
 * without its category quick-add cases: a category's spent is worked out (SPEC R25), and
 * payments replace the quick-add (Task 5).
 */
class LedgerTest {
    private val day = LocalDate(2026, 9, 14)

    private fun tx(id: String, key: String, amount: String, at: Instant, date: LocalDate = day, payee: String? = null, isIncome: Boolean = false) =
        Transaction(id, Fixture.PROFILE, date, at, payee, cents(amount), key, "x", isIncome)

    // ---- the key table -----------------------------------------------------------------

    // SPEC:R51
    @Test fun `R51 a savings row moves Savings by plus its amount`() {
        assertEquals(cents("20.00"), Ledger.savingsEffect(tx("a", "savings", "20.00", Instant.DISTANT_PAST)))
        assertEquals(cents("-20.00"), Ledger.savingsEffect(tx("a", "savings", "-20.00", Instant.DISTANT_PAST)))
    }

    // SPEC:R51
    @Test fun `R51 a goal row moves Savings by minus its amount`() {
        assertEquals(cents("-20.00"), Ledger.savingsEffect(tx("a", "goal:g", "20.00", Instant.DISTANT_PAST)))
    }

    // SPEC:R51 SPEC:R25
    @Test fun `R51 a 20_00 adjustment on a category is cat with 2000 and lowers Savings by 20_00`() {
        assertEquals(cents("-20.00"), Ledger.savingsEffect(tx("a", "cat:c", "20.00", Instant.DISTANT_PAST)))
    }

    // SPEC:R51
    @Test fun `R51 a 100_00 sweep lowers Savings by 100_00 and its reversal raises it by 100_00`() {
        assertEquals(cents("-100.00"), Ledger.savingsEffect(tx("a", "rec:e", "100.00", Instant.DISTANT_PAST)))
        assertEquals(cents("100.00"), Ledger.savingsEffect(tx("b", "rec:e", "-100.00", Instant.DISTANT_PAST)))
    }

    // SPEC:R51
    @Test fun `R51 an account row and a Sci-Fin goal spend row leave Savings as it was`() {
        assertEquals(0L, Ledger.savingsEffect(tx("a", "acct:x", "500.00", Instant.DISTANT_PAST)))
        assertEquals(0L, Ledger.savingsEffect(tx("b", "spend:g", "-30.00", Instant.DISTANT_PAST)))
    }

    // SPEC:R51
    @Test fun `R51 a key that is not in the table is refused`() {
        assertNull(TransactionKey.parse("wallet:1"))
        assertNull(TransactionKey.parse("goal"))
        assertNull(TransactionKey.parse(":g1"))
        assertEquals(TransactionKey.GoalKey("g1"), TransactionKey.parse("goal:g1"))
        // a stored row whose key cannot be read, a corrupted one, moves nothing and counts nowhere
        val corrupt = tx("c", "wallet:1", "-30.00", Instant.DISTANT_PAST)
        assertEquals(0L, Ledger.savingsEffect(corrupt))
        assertNull(Ledger.spent(corrupt))
    }

    // ---- logAndMove ----------------------------------------------------------------------

    // SPEC:R50
    @Test fun `R50 adding 150_00 to a Goal raises its saved money by 150_00 and lowers Savings by 150_00`() {
        val d1 = Writes.goalQuickAdd(profile(savings = "1,000.00", goals = listOf(goal("g1", "Trip"))), "g1", cents("150.00"), null, TestClock(day), "t1")
        assertEquals(cents("150.00"), d1.goals.single().saved)
        assertEquals(cents("850.00"), d1.savings.amount)
        val row = d1.transactions.single()
        assertEquals("goal:g1", row.key); assertEquals(cents("150.00"), row.amount); assertEquals("Toward Trip", row.payee)
    }

    // SPEC:R49
    @Test fun `R49 a 60_00 movement out of an unset Savings leaves it at -60_00`() {
        val d0 = profile(savings = null, goals = listOf(goal("g1", "Trip")))
        assertNull(d0.savings.amount)
        val d1 = Writes.goalQuickAdd(d0, "g1", cents("60.00"), null, TestClock(day), "t1")
        assertEquals(cents("-60.00"), d1.savings.amount)
    }

    // SPEC:R51
    @Test fun `R51 an account row is logged and Savings does not move`() {
        val d1 = Ledger.logAccountEdit(profile(savings = "100.00"), "a1", "Card", cents("45.00"), "Charge", TestClock(day), "t1")
        assertEquals(cents("100.00"), d1.savings.amount)
        assertEquals(1, d1.transactions.size)
    }

    // SPEC:R51
    @Test fun `R51 an income row is tagged and marks the last income date with the row's date`() {
        val d1 = Writes.savingsQuickAdd(profile(), cents("1,450.00"), "Salary", true, TestClock(day), "t1", date = LocalDate(2026, 9, 1))
        assertTrue(d1.transactions.single().isIncome)
        assertEquals(LocalDate(2026, 9, 1), d1.settings.lastIncomeLoggedDate)
        assertEquals(cents("2,450.00"), d1.savings.amount)
    }

    // SPEC:R51
    @Test fun `R51 the entered date places the row while the figure moves now`() {
        val clock = TestClock(day)
        val d1 = Writes.savingsQuickAdd(profile(), cents("10.00"), null, false, clock, "t1", date = LocalDate(2026, 8, 2))
        assertEquals(LocalDate(2026, 8, 2), d1.transactions.single().date)
        assertEquals(clock.now(), d1.transactions.single().loggedAt)
        assertEquals(cents("1,010.00"), d1.savings.amount)
    }

    // ---- direct edits --------------------------------------------------------------------

    // SPEC:R52
    @Test fun `R52 setting a Goal from 400_00 to 300_00 logs -100_00 and raises Savings by 100_00`() {
        val d0 = profile(savings = "100.00", goals = listOf(goal("g1", "Trip", saved = "400.00")))
        val d1 = Writes.goalSavedEdit(d0, "g1", cents("300.00"), null, TestClock(day), "t1")
        assertEquals(cents("300.00"), d1.goals.single().saved)
        assertEquals(cents("200.00"), d1.savings.amount)
        assertEquals(cents("-100.00"), d1.transactions.single().amount)
        assertEquals(StoredLabels.CORRECTION, d1.transactions.single().payee)
    }

    // SPEC:R52
    @Test fun `R52 setting Savings from 150_00 to 1,000_00 logs +850_00, not the new figure`() {
        val d1 = Writes.savingsDirectEdit(profile(savings = "150.00"), cents("1,000.00"), null, TestClock(day), "t1")
        assertEquals(cents("1,000.00"), d1.savings.amount)
        assertEquals(cents("850.00"), d1.transactions.single().amount)
        assertFalse(d1.transactions.single().isIncome)
    }

    // SPEC:R49 SPEC:R52
    @Test fun `R49 a direct edit to 0_00 ends the unset state and logs nothing`() {
        val d1 = Writes.savingsDirectEdit(profile(savings = null), 0, null, TestClock(day), "t1")
        assertEquals(0L, d1.savings.amount)
        assertEquals(0, d1.transactions.size)
    }

    // SPEC:R52
    @Test fun `R52 an edit to the same figure logs nothing`() {
        val d0 = profile(goals = listOf(goal("g1", saved = "40.00")))
        assertEquals(d0, Writes.goalSavedEdit(d0, "g1", cents("40.00"), null, TestClock(day), "t1"))
    }

    // ---- pairs ---------------------------------------------------------------------------

    private val t0 = Instant.parse("2026-09-14T10:00:00Z")

    // SPEC:R53
    @Test fun `R53 only an exact opposite on the same key pairs`() {
        val rows = listOf(tx("a", "goal:g", "40.00", t0), tx("b", "goal:g", "-40.00", t0 + 1.hours), tx("c", "goal:h", "-40.00", t0 + 2.hours))
        val p = Ledger.pairEligibility(rows)
        assertEquals("b", p["a"]); assertEquals("a", p["b"]); assertNull(p["c"])
        assertTrue(Ledger.pairEligibility(listOf(tx("a", "goal:g", "40.00", t0), tx("b", "goal:g", "-39.00", t0 + 1.hours))).isEmpty())
    }

    // SPEC:R53
    @Test fun `R53 the second must be next in logged order, not in date order`() {
        // Dated three weeks earlier, but logged between the two in real time: it breaks the pair.
        val rows = listOf(
            tx("a", "savings", "-40.00", t0),
            tx("x", "savings", "5.00", t0 + 30.minutes, date = LocalDate(2026, 8, 20)),
            tx("b", "savings", "40.00", t0 + 1.hours),
        )
        assertTrue(Ledger.pairEligibility(rows).isEmpty())
    }

    // SPEC:R53
    @Test fun `R53 four rows +40, -40, +40, -40 make two pairs, the first two and the last two`() {
        val rows = listOf(
            tx("a", "goal:g", "40.00", t0), tx("b", "goal:g", "-40.00", t0 + 1.hours),
            tx("c", "goal:g", "40.00", t0 + 2.hours), tx("d", "goal:g", "-40.00", t0 + 3.hours),
        )
        assertEquals(mapOf("a" to "b", "b" to "a", "c" to "d", "d" to "c"), Ledger.pairEligibility(rows))
    }

    // SPEC:R53
    @Test fun `R53 a row belongs to one pair at most, pairing forward only`() {
        val rows = listOf(tx("a", "goal:g", "40.00", t0), tx("b", "goal:g", "-40.00", t0 + 1.hours), tx("c", "goal:g", "40.00", t0 + 2.hours))
        val p = Ledger.pairEligibility(rows)
        assertEquals("b", p["a"]); assertNull(p["c"])
    }

    // SPEC:R53
    @Test fun `R53 rows 47 hours apart pair, 49 hours apart do not`() {
        assertTrue(Ledger.pairEligibility(listOf(tx("a", "goal:g", "40.00", t0), tx("b", "goal:g", "-40.00", t0 + 47.hours))).isNotEmpty())
        assertTrue(Ledger.pairEligibility(listOf(tx("a", "goal:g", "40.00", t0), tx("b", "goal:g", "-40.00", t0 + 48.hours))).isNotEmpty())
        assertTrue(Ledger.pairEligibility(listOf(tx("a", "goal:g", "40.00", t0), tx("b", "goal:g", "-40.00", t0 + 49.hours))).isEmpty())
    }

    // SPEC:R53 SPEC:R25
    @Test fun `R53 adjustments on one category pair only within one month`() {
        val march = tx("a", "cat:c", "20.00", t0, date = LocalDate(2026, 3, 31))
        assertTrue(Ledger.pairEligibility(listOf(march, tx("b", "cat:c", "-20.00", t0 + 1.hours, date = LocalDate(2026, 4, 1)))).isEmpty())
        assertEquals("b", Ledger.pairEligibility(listOf(march, tx("b", "cat:c", "-20.00", t0 + 1.hours, date = LocalDate(2026, 3, 31))))["a"])
    }

    // SPEC:R53
    @Test fun `R53 sweep rows and zero rows never pair`() {
        assertTrue(Ledger.pairEligibility(listOf(tx("a", "rec:e", "100.00", t0), tx("b", "rec:e", "-100.00", t0 + 1.hours))).isEmpty())
        assertTrue(Ledger.pairEligibility(listOf(tx("a", "goal:g", "0.00", t0), tx("b", "goal:g", "0.00", t0 + 1.hours))).isEmpty())
    }

    // SPEC:R53
    @Test fun `R53 deleting a pair removes both rows, moves no balance and recomputes the last income date`() {
        val d0 = profile(savings = "100.00").let {
            it.copy(
                transactions = listOf(
                    tx("i1", "savings", "1,000.00", t0 - 72.hours, date = LocalDate(2026, 9, 1), payee = StoredLabels.AUTO_DEPOSIT, isIncome = true),
                    tx("i2", "savings", "50.00", t0 - 24.hours, date = LocalDate(2026, 9, 13), payee = "Gift", isIncome = true),
                    tx("m", "savings", "50.00", t0, date = LocalDate(2026, 9, 14), payee = "Gift", isIncome = true),
                    tx("c", "savings", "-50.00", t0 + 1.hours, date = LocalDate(2026, 9, 14)),
                ),
                settings = it.settings.copy(incomeEnabled = true, lastIncomeLoggedDate = LocalDate(2026, 9, 14), lastIncomeDepositDate = LocalDate(2026, 9, 1)),
            )
        }
        assertEquals("c", Ledger.pairEligibility(d0.transactions)["m"])
        val d1 = Ledger.deletePair(d0, "m", "c")
        assertEquals(listOf("i1", "i2"), d1.transactions.map { it.id })
        assertEquals(LocalDate(2026, 9, 13), d1.settings.lastIncomeLoggedDate)
        assertEquals(LocalDate(2026, 9, 1), d1.settings.lastIncomeDepositDate)
        assertEquals(cents("100.00"), d1.savings.amount)
    }

    // SPEC:R49
    @Test fun `R49 savingsAmount reads unset as zero for arithmetic only`() {
        assertEquals(0L, profile(savings = null).savingsAmount)
        assertNull(profile(savings = null).savings.amount)
    }
}

package com.hazia.tesoriere.payments

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.TestClock
import com.hazia.tesoriere.cents
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpentTest {
    private val clock = TestClock(LocalDate(2026, 4, 10))
    private val world = profile(savings = "1,000.00", categories = listOf(category("groc", "Groceries")))

    private fun pay(date: LocalDate, total: String) =
        PaymentDraft(date, cents(total), PaymentSource.Savings, null, listOf(Part("groc", cents(total))))

    // SPEC:R20 SPEC:R25
    @Test fun `R20 a payment dated 2026-03-31 counts in March and not in April`() {
        val d = Payments.record(world, pay(LocalDate(2026, 3, 31), "42.30"), clock, "pay-1")
        assertEquals(cents("42.30"), Spent.of(d, "groc", "2026-03"))
        assertEquals(0L, Spent.of(d, "groc", "2026-04"))
    }

    // SPEC:R25
    @Test fun `R25 an adjustment counts in its month only`() {
        var d = Payments.record(world, pay(LocalDate(2026, 3, 5), "100.00"), clock, "pay-1")
        d = Payments.record(d, pay(LocalDate(2026, 4, 2), "30.00"), clock, "pay-2")
        d = Spent.adjust(d, "groc", "2026-03", cents("120.00"), clock, "adj-1")
        assertEquals(cents("120.00"), Spent.of(d, "groc", "2026-03"))
        assertEquals(cents("30.00"), Spent.of(d, "groc", "2026-04"))
        val row = d.transactions.single()
        assertEquals("cat:groc", row.key); assertEquals(cents("20.00"), row.amount)
        assertEquals("2026-03", row.date.toString().substring(0, 7))
    }

    // SPEC:R25 SPEC:R51
    @Test fun `R25 an adjustment moves Savings by its opposite and an adjustment to the same figure logs nothing`() {
        val d1 = Spent.adjust(world, "groc", "2026-04", cents("25.00"), clock, "adj-1")
        assertEquals(cents("975.00"), d1.savings.amount)
        assertEquals(LocalDate(2026, 4, 10), d1.transactions.single().date)
        assertEquals(d1, Spent.adjust(d1, "groc", "2026-04", cents("25.00"), clock, "adj-2"))
    }

    // SPEC:R25
    @Test fun `R25 a month still to come cannot be adjusted`() {
        assertFailsWith<IllegalArgumentException> { Spent.adjust(world, "groc", "2026-05", cents("1.00"), clock, "adj-1") }
    }
}

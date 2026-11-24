package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.engine.Categories
import com.hazia.tesoriere.engine.CategoryEditing
import com.hazia.tesoriere.engine.CategoryMonth
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/CategoryEditingTest.kt at
 * 2892110, without its spent cases: spent is worked out per month (SPEC R25, R59), and changing
 * it is an adjustment (SpentTest).
 */
class CategoryEditingTest {
    private val clock = TestClock(LocalDate(2026, 3, 20))
    private val ids = Ids("c")

    // SPEC:R59
    @Test fun `R59 a new category keeps its name, icon and planned amount, and logs nothing`() {
        val d = CategoryEditing.save(profile(), CategoryEditing.Draft(name = " Groceries ", icon = "basket", planned = cents("300.00")), clock, ids::next)
        val c = d.categories.single()
        assertEquals("Groceries", c.name); assertEquals("basket", c.icon); assertEquals(cents("300.00"), c.planned)
        assertEquals(emptyList(), d.transactions)
        assertEquals(profile().savings, d.savings)
    }

    // SPEC:R59
    @Test fun `R59 an edit changes name, icon and planned amount and logs nothing`() {
        val d0 = CategoryEditing.save(profile(), CategoryEditing.Draft(name = "Food", planned = cents("300.00")), clock, ids::next)
        val c = d0.categories.single()
        val d = CategoryEditing.save(d0, CategoryEditing.Draft.of(c).copy(name = "Groceries", planned = cents("350.00")), clock, ids::next)
        assertEquals(listOf(c.copy(name = "Groceries", planned = cents("350.00"))), d.categories)
        assertEquals(emptyList(), d.transactions)
    }

    // SPEC:R17
    @Test fun `R17 a blank name saves nothing, as in Sci-Fin`() {
        val d = profile()
        assertEquals(d, CategoryEditing.save(d, CategoryEditing.Draft(name = "  "), clock, ids::next))
    }

    // SPEC:R59 SPEC:R25
    @Test fun `R59 a category planned 300_00 with payments of 320_00 in March reads planned 30000 and spent 32000`() {
        var d = CategoryEditing.save(profile(), CategoryEditing.Draft(name = "Groceries", planned = cents("300.00")), clock, ids::next)
        val id = d.categories.single().id
        val payments = listOf(LocalDate(2026, 3, 3) to "200.00", LocalDate(2026, 3, 18) to "120.00", LocalDate(2026, 4, 9) to "55.00")
        payments.forEachIndexed { n, (date, total) ->
            d = Payments.record(d, PaymentDraft(date, cents(total), PaymentSource.Savings, null, listOf(Part(id, cents(total)))), clock, "pay-$n")
        }
        assertEquals(listOf(CategoryMonth(id, 30000, 32000)), Categories.month(d, "2026-03"))
        assertEquals(listOf(CategoryMonth(id, 30000, 5500)), Categories.month(d, "2026-04"))
    }
}

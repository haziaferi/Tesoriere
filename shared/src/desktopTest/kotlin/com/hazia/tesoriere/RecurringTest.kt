package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.recurring
import com.hazia.tesoriere.engine.Recurring
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.DateMode
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.payments.Spent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/RecurringTest.kt at 2892110,
 * plus the category tests of owner answer 2 of 2026-11-24 (SPEC R67).
 */
class RecurringTest {

    /** A Profile holding the categories the expenses are filed under. */
    private fun world(savings: String? = "1,000.00") =
        profile(savings = savings, categories = listOf(category("c1", "Bills"), category("phone", "Phone"), category("net", "Internet")))

    private fun withExpenses(d: ProfileData, vararg e: RecurringExpense) = d.copy(recurringExpenses = e.toList())

    // ---- the occurrence walk ----------------------------------------------------------------

    // SPEC:R60
    @Test fun `R60 a weekly expense from 2 January 2026 falls due 5 times in January and 4 in February, a biweekly one 3 and 2`() {
        val w = recurring("w", "10.00", Cadence.WEEKLY, LocalDate(2026, 1, 2))
        assertEquals(5, Recurring.occurrenceDatesInMonth(w, 2026, 1)!!.size)       // 2, 9, 16, 23, 30 Jan
        assertEquals(4, Recurring.occurrenceDatesInMonth(w, 2026, 2)!!.size)
        val b = recurring("b", "10.00", Cadence.BIWEEKLY, LocalDate(2026, 1, 2))
        assertEquals(3, Recurring.occurrenceDatesInMonth(b, 2026, 1)!!.size)       // 2, 16, 30 Jan
        assertEquals(2, Recurring.occurrenceDatesInMonth(b, 2026, 2)!!.size)
    }

    // SPEC:R61
    @Test fun `R61 no due date falls before the first due date of 10 March 2026`() {
        val e = recurring("e", "10.00", Cadence.MONTHLY, LocalDate(2026, 3, 10), dueDate = LocalDate(2026, 9, 10))
        assertEquals(0, Recurring.occurrenceDatesInMonth(e, 2026, 2)!!.size)
        assertEquals(listOf(LocalDate(2026, 3, 10)), Recurring.occurrenceDatesInMonth(e, 2026, 3))
    }

    // SPEC:R61
    @Test fun `R61 a rent on the 31st falls due on 28 February 2026 and returns to 31 March`() {
        val e = recurring("rent", "950.00", Cadence.MONTHLY, LocalDate(2026, 1, 31))
        assertEquals(listOf(LocalDate(2026, 2, 28)), Recurring.occurrenceDatesInMonth(e, 2026, 2))
        assertEquals(listOf(LocalDate(2026, 3, 31)), Recurring.occurrenceDatesInMonth(e, 2026, 3))
        assertEquals(LocalDate(2026, 3, 31), Recurring.nextOccurrenceAfter(e, LocalDate(2026, 2, 28)))
    }

    // SPEC:R60
    @Test fun `R60 a 30-day interval from 1 January 2026 falls due on 1 and 31 January, then 2 March`() {
        val e = recurring("sub", "10.00", Cadence.MONTHLY, LocalDate(2026, 1, 1), dateMode = DateMode.INTERVAL)
        assertEquals(listOf(LocalDate(2026, 1, 1), LocalDate(2026, 1, 31)), Recurring.occurrenceDatesInMonth(e, 2026, 1))
        assertEquals(emptyList(), Recurring.occurrenceDatesInMonth(e, 2026, 2))
        assertEquals(listOf(LocalDate(2026, 3, 2)), Recurring.occurrenceDatesInMonth(e, 2026, 3))
    }

    // SPEC:R60 SPEC:R63
    @Test fun `R63 a semimonthly 300_00 expense on the 15th and 31st falls due on 15 and 28 February 2026`() {
        val e = recurring("s", "300.00", Cadence.SEMIMONTHLY, LocalDate(2026, 1, 15), dueDate2 = LocalDate(2026, 1, 31), dateMode = null)
        assertEquals(listOf(LocalDate(2026, 2, 15), LocalDate(2026, 2, 28)), Recurring.occurrenceDatesInMonth(e, 2026, 2))
        assertEquals(LocalDate(2026, 2, 28), Recurring.nextOccurrenceAfter(e, LocalDate(2026, 2, 15)))
        assertEquals(LocalDate(2026, 3, 15), Recurring.nextOccurrenceAfter(e, LocalDate(2026, 2, 28)))
    }

    // SPEC:R63
    @Test fun `R63 an end date of 20 September 2026 sweeps the 20th, and one of the 19th sweeps nothing in September`() {
        val e = recurring("e", "100.00", Cadence.MONTHLY, LocalDate(2026, 1, 20), dueDate = LocalDate(2026, 9, 20), endDate = LocalDate(2026, 9, 20))
        assertEquals(1, Recurring.occurrenceDatesInMonth(e, 2026, 9)!!.size)
        assertEquals(0, Recurring.occurrenceDatesInMonth(e, 2026, 10)!!.size)
        val cut = e.copy(endDate = LocalDate(2026, 9, 19))
        assertEquals(0, Recurring.occurrenceDatesInMonth(cut, 2026, 9)!!.size)   // its due date against the end date, not the sweep's
    }

    // SPEC:R62 SPEC:R63
    @Test fun `R63 a yearly expense holding 50_00 sweeps 50_00 and a bimonthly 90_00 one with no share sweeps 45_00`() {
        assertNull(Recurring.occurrenceDatesInMonth(recurring("y", "600.00", Cadence.YEARLY, LocalDate(2026, 3, 1), share = "50.00"), 2026, 9))
        assertEquals(cents("50.00"), Recurring.sweepAmountForMonth(recurring("y", "600.00", Cadence.YEARLY, LocalDate(2026, 3, 1), share = "50.00"), 2026, 9))
        assertEquals(cents("45.00"), Recurring.sweepAmountForMonth(recurring("b", "90.00", Cadence.BIMONTHLY, LocalDate(2026, 1, 5)), 2026, 9))
    }

    // SPEC:R63
    @Test fun `R63 a finished 100_00 expense sweeps 0_00`() {
        assertEquals(0L, Recurring.sweepAmountForMonth(recurring("e", "100.00", Cadence.MONTHLY, LocalDate(2026, 1, 1), finishedAt = LocalDate(2026, 8, 1)), 2026, 9))
    }

    // ---- the anchor chain -------------------------------------------------------------------

    // SPEC:R61
    @Test fun `R61 due 10 September 2026 is still due that day, due 10 October on the 11th and 10 January 2027 on 25 December`() {
        val d = withExpenses(world(), recurring("e", "100.00", Cadence.MONTHLY, LocalDate(2026, 9, 10)))
        assertEquals(LocalDate(2026, 9, 10), Recurring.advanceDueDates(d, LocalDate(2026, 9, 10)).recurringExpenses[0].dueDate)
        assertEquals(LocalDate(2026, 10, 10), Recurring.advanceDueDates(d, LocalDate(2026, 9, 11)).recurringExpenses[0].dueDate)
        assertEquals(LocalDate(2027, 1, 10), Recurring.advanceDueDates(d, LocalDate(2026, 12, 25)).recurringExpenses[0].dueDate)
    }

    // SPEC:R61
    @Test fun `R61 from 31 January 2026 an interval expense falls due next on 2 March and a fixed-date one on 28 February`() {
        val i = recurring("i", "10.00", Cadence.MONTHLY, LocalDate(2026, 1, 31), dateMode = DateMode.INTERVAL)
        assertEquals(LocalDate(2026, 3, 2), Recurring.nextOccurrenceAfter(i, LocalDate(2026, 1, 31)))
        val f = recurring("f", "10.00", Cadence.MONTHLY, LocalDate(2026, 1, 31))
        assertEquals(LocalDate(2026, 2, 28), Recurring.nextOccurrenceAfter(f, LocalDate(2026, 1, 31)))
    }

    // SPEC:R61 SPEC:R64
    @Test fun `R61 entered on 14 September 2026 with a first due date of 5 March, it keeps 5 March and is next due 5 October`() {
        val clock = TestClock(LocalDate(2026, 9, 14))
        val ids = Ids()
        val d = Recurring.create(world(), recurring("e", "100.00", Cadence.MONTHLY, LocalDate(2026, 3, 5)), clock) { ids.next() }
        val e = d.recurringExpenses.single()
        assertEquals(LocalDate(2026, 3, 5), e.firstDueDate)
        assertEquals(LocalDate(2026, 10, 5), e.dueDate)
        assertEquals(5, e.dueDateAnchorDay)
    }

    // SPEC:R61
    @Test fun `R61 with an end date of 20 September 2026, read on 2 October it stays due 20 September and is not finished`() {
        val e = recurring("e", "100.00", Cadence.MONTHLY, LocalDate(2026, 6, 20), endDate = LocalDate(2026, 9, 20))
        val out = Recurring.advanceDueDates(withExpenses(world(), e), LocalDate(2026, 10, 2)).recurringExpenses[0]
        assertEquals(LocalDate(2026, 9, 20), out.dueDate)
        assertNull(out.finishedAt)                         // finishing is the month boundary's
    }

    // ---- apportioned share ------------------------------------------------------------------

    // SPEC:R62
    @Test fun `R62 a 1,200_00 yearly due 15 July 2027 has a share of 100_00 from 15 July 2026 and 120_00 from 14 September`() {
        val e = recurring("y", "1,200.00", Cadence.YEARLY, LocalDate(2026, 7, 15), dueDate = LocalDate(2027, 7, 15))
        assertEquals(cents("100.00"), Recurring.apportionedShareFor(e, LocalDate(2026, 7, 15)))
        assertEquals(cents("120.00"), Recurring.apportionedShareFor(e, LocalDate(2026, 9, 14)))
    }

    // SPEC:R62
    @Test fun `R62 a 1,200_00 yearly due 25 June 2026 counted from 20 June has a share of 1,200_00`() {
        val e = recurring("y", "1,200.00", Cadence.YEARLY, LocalDate(2026, 6, 25), dueDate = LocalDate(2026, 6, 25))
        assertEquals(cents("1,200.00"), Recurring.apportionedShareFor(e, LocalDate(2026, 6, 20)))
    }

    // SPEC:R62
    @Test fun `R62 a 1,000_00 yearly due 1 April 2026 counted from 1 January has a share of 333_34`() {
        val e = recurring("y", "1,000.00", Cadence.YEARLY, LocalDate(2026, 1, 1), dueDate = LocalDate(2026, 4, 1))
        assertEquals(cents("333.34"), Recurring.apportionedShareFor(e, LocalDate(2026, 1, 1)))
    }

    // SPEC:R62
    @Test fun `R62 a share of 300_00 is held until 15 July 2026 passes, then 100_00 for the next cycle`() {
        val e = recurring("y", "1,200.00", Cadence.YEARLY, LocalDate(2025, 7, 15), dueDate = LocalDate(2026, 7, 15), share = "300.00")
        val d = withExpenses(world(), e)
        assertEquals(cents("300.00"), Recurring.advanceDueDates(d, LocalDate(2026, 7, 15)).recurringExpenses[0].apportionedShare)
        val rolled = Recurring.advanceDueDates(d, LocalDate(2026, 9, 14)).recurringExpenses[0]
        assertEquals(LocalDate(2027, 7, 15), rolled.dueDate)
        assertEquals(cents("100.00"), rolled.apportionedShare)
    }

    // SPEC:R62 SPEC:R65
    @Test fun `R62 renaming keeps a held share of 100_00 and an amount of 2,400_00 on 14 September 2026 gives 240_00`() {
        val today = LocalDate(2026, 9, 14)
        val e = recurring("y", "1,200.00", Cadence.YEARLY, LocalDate(2026, 7, 15), dueDate = LocalDate(2027, 7, 15), share = "100.00", lastSweptMonth = "2026-09")
        val d = withExpenses(world(), e)
        val (renamed, r1) = Recurring.edit(d, e.copy(name = "Car insurance"), today)
        assertEquals(cents("100.00"), renamed.recurringExpenses[0].apportionedShare); assertNull(r1)
        val (replanned, _) = Recurring.edit(d, e.copy(amount = cents("2,400.00")), today)
        assertEquals(cents("240.00"), replanned.recurringExpenses[0].apportionedShare)
    }

    // ---- the four creation branches ---------------------------------------------------------

    // SPEC:R64
    @Test fun `R64 created on 14 September 2026, a 100_00 monthly first due the 20th leaves 1,000_00 at 900_00`() {
        val clock = TestClock(LocalDate(2026, 9, 14)); val ids = Ids()
        val d = Recurring.create(world(), recurring("e", "100.00", Cadence.MONTHLY, LocalDate(2026, 9, 20)), clock) { ids.next() }
        assertEquals(cents("900.00"), d.savings.amount)
        assertEquals(LocalDate(2026, 9, 20), d.transactions.single().date)
        assertEquals("2026-09", d.recurringExpenses[0].lastSweptMonth)
    }

    // SPEC:R64
    @Test fun `R64 created on 14 September 2026, first due the 10th sweeps nothing, monthly or biweekly`() {
        val clock = TestClock(LocalDate(2026, 9, 14)); val ids = Ids()
        val d = Recurring.create(world(), recurring("e", "100.00", Cadence.MONTHLY, LocalDate(2026, 9, 10)), clock) { ids.next() }
        assertEquals(cents("1,000.00"), d.savings.amount)
        assertEquals("2026-09", d.recurringExpenses[0].lastSweptMonth)
        // a biweekly whose first due date passed, with 24 September still ahead: nothing either
        val b = Recurring.create(world(), recurring("b", "100.00", Cadence.BIWEEKLY, LocalDate(2026, 9, 10)), clock) { ids.next() }
        assertEquals(cents("1,000.00"), b.savings.amount)
    }

    // SPEC:R64
    @Test fun `R64 created on 25 September 2026, first due 5 October sweeps nothing for September`() {
        val clock = TestClock(LocalDate(2026, 9, 25)); val ids = Ids()
        val d = Recurring.create(world(), recurring("e", "100.00", Cadence.MONTHLY, LocalDate(2026, 10, 5)), clock) { ids.next() }
        assertEquals(cents("1,000.00"), d.savings.amount)
        assertEquals("2026-09", d.recurringExpenses[0].lastSweptMonth)
    }

    // SPEC:R64 SPEC:R62
    @Test fun `R64 a yearly 1,200_00 due 15 July 2027 created on 14 September 2026 sweeps nothing and holds 120_00`() {
        val clock = TestClock(LocalDate(2026, 9, 14)); val ids = Ids()
        val d = Recurring.create(world(), recurring("y", "1,200.00", Cadence.YEARLY, LocalDate(2027, 7, 15)), clock) { ids.next() }
        assertEquals(cents("1,000.00"), d.savings.amount)
        assertEquals(cents("120.00"), d.recurringExpenses[0].apportionedShare)
        assertEquals("2026-09", d.recurringExpenses[0].lastSweptMonth)
    }

    // SPEC:R60
    @Test fun `R60 a weekly expense saved with fixed-date mode keeps no date mode and no anchor day`() {
        val clock = TestClock(LocalDate(2026, 9, 14)); val ids = Ids()
        val d = Recurring.create(world(), recurring("w", "10.00", Cadence.WEEKLY, LocalDate(2026, 9, 20), dateMode = DateMode.FIXED_DATE), clock) { ids.next() }
        assertNull(d.recurringExpenses[0].dateMode)
        assertNull(d.recurringExpenses[0].dueDateAnchorDay)
    }

    // ---- edit-and-reconcile -----------------------------------------------------------------

    // SPEC:R65
    @Test fun `R65 a swept 1,000_00 rent due the 20th edited to 1,200_00 offers 200_00 dated 20 September, leaving 800_00`() {
        val clock = TestClock(LocalDate(2026, 9, 10)); val ids = Ids()
        var d = Recurring.create(world("2,000.00"), recurring("rent", "1,000.00", Cadence.MONTHLY, LocalDate(2026, 9, 20)), clock) { ids.next() }
        val e = d.recurringExpenses[0]
        val (edited, r) = Recurring.edit(d, e.copy(amount = cents("1,200.00")), clock.today())
        assertNotNull(r)
        assertEquals(cents("200.00"), r.delta)
        assertEquals(LocalDate(2026, 9, 20), r.occurrenceDate)
        d = Recurring.applyReconcile(edited, r, clock) { ids.next() }
        assertEquals(cents("800.00"), d.savings.amount)                                          // 2,000 - 1,000 - 200
        assertEquals(cents("1,200.00"), Recurring.reversalAmount(d, d.recurringExpenses[0], clock.today()))
        assertEquals("c1", d.transactions.last().categoryId)                                     // filed like its sweep (R67)
    }

    // SPEC:R65
    @Test fun `R65 an edit from 100_00 to 150_00 before the month has swept offers nothing`() {
        val e = recurring("e", "100.00", Cadence.MONTHLY, LocalDate(2026, 9, 20), lastSweptMonth = "2026-08")
        val (_, r) = Recurring.edit(withExpenses(world(), e), e.copy(amount = cents("150.00")), LocalDate(2026, 9, 10))
        assertNull(r)
    }

    // ---- deletion reversal ------------------------------------------------------------------

    // SPEC:R66
    @Test fun `R66 a rent swept at 1,000_00 and edited to 1,200_00 without the difference offers 1,000_00`() {
        val clock = TestClock(LocalDate(2026, 9, 10)); val ids = Ids()
        var d = Recurring.create(world("2,000.00"), recurring("rent", "1,000.00", Cadence.MONTHLY, LocalDate(2026, 9, 20)), clock) { ids.next() }
        d = d.copy(recurringExpenses = d.recurringExpenses.map { it.copy(amount = cents("1,200.00")) })
        assertEquals(cents("1,000.00"), Recurring.reversalAmount(d, d.recurringExpenses[0], clock.today()))
    }

    // SPEC:R66
    @Test fun `R66 a semimonthly 300_00 due the 15th and 30th deleted on 20 September returns 300_00 of 2,000_00 to leave 1,700_00`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        var d = Recurring.create(world("2,000.00"), recurring("s", "300.00", Cadence.SEMIMONTHLY, LocalDate(2026, 9, 15), dueDate2 = LocalDate(2026, 9, 30)), clock) { ids.next() }
        assertEquals(cents("1,400.00"), d.savings.amount)
        clock.setDay(LocalDate(2026, 9, 20))
        assertEquals(cents("300.00"), Recurring.reversalAmount(d, d.recurringExpenses[0], clock.today()))
        d = Recurring.delete(d, "s", reverse = true, clock) { ids.next() }
        assertEquals(cents("1,700.00"), d.savings.amount)
        assertEquals(0, d.recurringExpenses.size)
        assertEquals(3, d.transactions.size)                                                    // both sweeps and the return stay
    }

    // SPEC:R66 SPEC:R61
    @Test fun `R66 an expense due 1 September 2026 offers 100_00 on that day and 0_00 on the 2nd`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        val d = Recurring.create(world("2,000.00"), recurring("e", "100.00", Cadence.MONTHLY, LocalDate(2026, 9, 1)), clock) { ids.next() }
        assertEquals(cents("100.00"), Recurring.reversalAmount(d, d.recurringExpenses[0], LocalDate(2026, 9, 1)))
        assertEquals(0L, Recurring.reversalAmount(d, d.recurringExpenses[0], LocalDate(2026, 9, 2)))
    }

    // SPEC:R66 SPEC:R63
    @Test fun `R66 a yearly expense that swept 100_00 on 1 August and 1 September 2026 offers 200_00`() {
        val clock = TestClock(LocalDate(2026, 9, 14)); val ids = Ids()
        val e = recurring("y", "1,200.00", Cadence.YEARLY, LocalDate(2026, 7, 15), dueDate = LocalDate(2027, 7, 15), share = "100.00")
        var d = withExpenses(world("2,000.00"), e)
        d = Recurring.sweepOccurrences(d, "y", LocalDate(2026, 8, 1), clock) { ids.next() }.first
        d = Recurring.sweepOccurrences(d, "y", LocalDate(2026, 9, 1), clock) { ids.next() }.first
        assertEquals(listOf(LocalDate(2026, 8, 1), LocalDate(2026, 9, 1)), d.transactions.map { it.date })
        assertEquals(cents("200.00"), Recurring.reversalAmount(d, d.recurringExpenses[0], clock.today()))
    }

    // SPEC:R66
    @Test fun `R66 a finished 100_00 expense offers 0_00`() {
        val e = recurring("e", "100.00", Cadence.MONTHLY, LocalDate(2026, 9, 20), finishedAt = LocalDate(2026, 9, 1))
        assertEquals(0L, Recurring.reversalAmount(withExpenses(world(), e), e, LocalDate(2026, 9, 14)))
    }

    // SPEC:R66
    @Test fun `R66 deleting an expense linked to a Loan of 5,000_00 removes both and logs a -5,000_00 acct row`() {
        val clock = TestClock(LocalDate(2026, 9, 14)); val ids = Ids()
        val e = recurring("pay", "260.00", Cadence.MONTHLY, LocalDate(2026, 9, 15), linkedAccountId = "loan", lastSweptMonth = "2026-09")
        val a = Account("loan", Fixture.PROFILE, "Car loan", null, AccountType.LOAN, balance = cents("5,000.00"), linkedRecurringExpenseId = "pay")
        val d = Recurring.delete(withExpenses(world(), e).copy(accounts = listOf(a)), "pay", reverse = false, clock) { ids.next() }
        assertTrue(d.accounts.isEmpty()); assertTrue(d.recurringExpenses.isEmpty())
        assertEquals(cents("-5,000.00"), d.transactions.single().amount)
        assertEquals("acct:loan", d.transactions.single().key)
    }

    // SPEC:R67 SPEC:R54 SPEC:R25
    @Test fun `R67 a Loan's 260_00 payment adds 260_00 to Car in September, and after a 9,800_00 car charged to the Loan in October its sweep is a transfer`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        val e = recurring("pay", "260.00", Cadence.MONTHLY, LocalDate(2026, 9, 15), linkedAccountId = "loan", categoryId = "c1", name = "Car loan payment")
        val loan = Account("loan", Fixture.PROFILE, "Car loan", null, AccountType.LOAN, balance = cents("0.00"), linkedRecurringExpenseId = "pay")
        var d = withExpenses(world(), e).copy(accounts = listOf(loan))
        d = Recurring.sweepOccurrences(d, "pay", LocalDate(2026, 9, 1), clock) { ids.next() }.first
        assertEquals(cents("260.00"), Spent.of(d, "c1", "2026-09"))
        // the car, bought in October, is a payment charged to the Loan (SPEC R54)
        val car = com.hazia.tesoriere.payments.PaymentDraft(
            LocalDate(2026, 10, 3), cents("9,800.00"), com.hazia.tesoriere.payments.PaymentSource.Account("loan"), "Car dealer",
            listOf(com.hazia.tesoriere.payments.Part("c1", cents("9,800.00"))),
        )
        d = com.hazia.tesoriere.payments.Payments.record(d, car, clock, "car")
        assertEquals(cents("9,800.00"), d.accounts.single().balance)
        d = Recurring.sweepOccurrences(d, "pay", LocalDate(2026, 10, 1), clock) { ids.next() }.first
        assertEquals(cents("9,800.00"), Spent.of(d, "c1", "2026-10"))                       // the October sweep is a transfer
        assertEquals(cents("260.00"), Spent.of(d, "c1", "2026-09"))                         // September stays as it was
        assertEquals(cents("480.00"), d.savings.amount)                                     // both sweeps still moved Savings
        // its return is a transfer too, and lowers no spent
        clock.setDay(LocalDate(2026, 10, 10))
        val back = Recurring.delete(d, "pay", reverse = true, clock) { ids.next() }
        assertEquals(cents("9,800.00"), Spent.of(back, "c1", "2026-10"))
        assertEquals(cents("740.00"), back.savings.amount)
    }

    // ---- every recurring expense has a category (owner answer 2 of 2026-11-24) ----------------

    // SPEC:R67
    @Test fun `R67 a recurring expense cannot be saved without a category`() {
        val clock = TestClock(LocalDate(2026, 9, 14)); val ids = Ids()
        val good = recurring("e", "60.00", Cadence.MONTHLY, LocalDate(2026, 9, 20), categoryId = "phone")
        assertTrue(Recurring.canSave(world(), good))
        assertFalse(Recurring.canSave(world(), good.copy(categoryId = "")))
        assertFalse(Recurring.canSave(world(), good.copy(categoryId = "gone")))         // names no category that exists
        assertFailsWith<IllegalArgumentException> { Recurring.create(world(), good.copy(categoryId = ""), clock) { ids.next() } }
        val d = Recurring.create(world(), good, clock) { ids.next() }
        assertFailsWith<IllegalArgumentException> { Recurring.edit(d, d.recurringExpenses[0].copy(categoryId = "gone"), clock.today()) }
    }

    // SPEC:R67 SPEC:R25 SPEC:R21
    @Test fun `R67 a 60_00 monthly expense under Phone adds 60_00 to Phone's spent in each month it sweeps`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        var d = Recurring.create(world(), recurring("e", "60.00", Cadence.MONTHLY, LocalDate(2026, 9, 5), categoryId = "phone"), clock) { ids.next() }
        d = Recurring.sweepOccurrences(d, "e", LocalDate(2026, 10, 1), clock) { ids.next() }.first
        assertEquals(cents("60.00"), Spent.of(d, "phone", "2026-09"))
        assertEquals(cents("60.00"), Spent.of(d, "phone", "2026-10"))
        assertEquals(0L, Spent.of(d, "c1", "2026-09"))
        assertTrue(d.payments.isEmpty())                                                        // a sweep is no payment (R21)
        assertEquals(cents("880.00"), d.savings.amount)
    }

    // SPEC:R67 SPEC:R62
    @Test fun `R67 a yearly 600_00 expense sweeps a 50_00 share into its category each month`() {
        val clock = TestClock(LocalDate(2026, 7, 15)); val ids = Ids()
        var d = Recurring.create(world(), recurring("y", "600.00", Cadence.YEARLY, LocalDate(2027, 7, 15), categoryId = "net"), clock) { ids.next() }
        assertEquals(cents("50.00"), d.recurringExpenses[0].apportionedShare)
        d = Recurring.sweepOccurrences(d, "y", LocalDate(2026, 8, 1), clock) { ids.next() }.first
        d = Recurring.sweepOccurrences(d, "y", LocalDate(2026, 9, 1), clock) { ids.next() }.first
        assertEquals(cents("50.00"), Spent.of(d, "net", "2026-08"))
        assertEquals(cents("50.00"), Spent.of(d, "net", "2026-09"))
    }

    // SPEC:R67 SPEC:R66 SPEC:R25
    @Test fun `R67 a reversal lowers the category's spent in the reversal's month`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        var d = Recurring.create(world("2,000.00"), recurring("s", "300.00", Cadence.SEMIMONTHLY, LocalDate(2026, 9, 15), dueDate2 = LocalDate(2026, 9, 30), categoryId = "phone"), clock) { ids.next() }
        assertEquals(cents("600.00"), Spent.of(d, "phone", "2026-09"))
        clock.setDay(LocalDate(2026, 9, 20))
        d = Recurring.delete(d, "s", reverse = true, clock) { ids.next() }
        assertEquals(cents("300.00"), Spent.of(d, "phone", "2026-09"))
        val back = d.transactions.last()
        assertEquals(cents("-300.00"), back.amount); assertEquals("phone", back.categoryId); assertEquals(LocalDate(2026, 9, 20), back.date)
    }

    // SPEC:R66 SPEC:R67 SPEC:R25
    @Test fun `R66 a yearly 1,200_00 insurance deleted on 14 September 2026 returns 200_00 and leaves Insurance's September spent at -100_00`() {
        val clock = TestClock(LocalDate(2026, 9, 14)); val ids = Ids()
        val e = recurring("ins", "1,200.00", Cadence.YEARLY, LocalDate(2026, 7, 15), dueDate = LocalDate(2027, 7, 15), share = "100.00", categoryId = "ins", name = "Insurance")
        var d = withExpenses(profile(savings = "2,000.00", categories = listOf(category("ins", "Insurance"))), e)
        d = Recurring.sweepOccurrences(d, "ins", LocalDate(2026, 8, 1), clock) { ids.next() }.first
        d = Recurring.sweepOccurrences(d, "ins", LocalDate(2026, 9, 1), clock) { ids.next() }.first
        assertEquals(cents("100.00"), Spent.of(d, "ins", "2026-09"))
        d = Recurring.delete(d, "ins", reverse = true, clock) { ids.next() }
        assertEquals(cents("-100.00"), Spent.of(d, "ins", "2026-09"))                     // a month's spent may be below zero
        assertEquals(cents("100.00"), Spent.of(d, "ins", "2026-08"))                      // the past month is left as it was
        assertEquals(cents("2,000.00"), d.savings.amount)
    }

    // SPEC:R66 SPEC:R67 SPEC:R25
    @Test fun `R66 a semimonthly 300_00 swept under Phone, moved to Internet and deleted on 20 September returns 300_00 under Phone`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        var d = Recurring.create(world("2,000.00"), recurring("s", "300.00", Cadence.SEMIMONTHLY, LocalDate(2026, 9, 15), dueDate2 = LocalDate(2026, 9, 30), categoryId = "phone"), clock) { ids.next() }
        clock.setDay(LocalDate(2026, 9, 20))
        d = Recurring.edit(d, d.recurringExpenses[0].copy(categoryId = "net"), clock.today()).first
        d = Recurring.delete(d, "s", reverse = true, clock) { ids.next() }
        assertEquals(cents("300.00"), Spent.of(d, "phone", "2026-09"))
        assertEquals(0L, Spent.of(d, "net", "2026-09"))
        assertEquals("phone", d.transactions.last().categoryId)
        assertEquals(cents("1,700.00"), d.savings.amount)
    }

    // SPEC:R66 SPEC:R67
    @Test fun `R66 a yearly 100_00 share swept under Phone in August and Internet in September returns -100_00 under each`() {
        val clock = TestClock(LocalDate(2026, 9, 14)); val ids = Ids()
        val e = recurring("y", "1,200.00", Cadence.YEARLY, LocalDate(2026, 7, 15), dueDate = LocalDate(2027, 7, 15), share = "100.00", categoryId = "phone")
        var d = withExpenses(world("2,000.00"), e)
        d = Recurring.sweepOccurrences(d, "y", LocalDate(2026, 8, 1), clock) { ids.next() }.first
        d = d.copy(recurringExpenses = d.recurringExpenses.map { it.copy(categoryId = "net") })
        d = Recurring.sweepOccurrences(d, "y", LocalDate(2026, 9, 1), clock) { ids.next() }.first
        d = Recurring.delete(d, "y", reverse = true, clock) { ids.next() }
        val back = d.transactions.drop(2)
        assertEquals(listOf("phone" to cents("-100.00"), "net" to cents("-100.00")), back.map { it.categoryId to it.amount })
        assertTrue(back.all { it.date == LocalDate(2026, 9, 14) && it.key == "rec:y" })
        assertEquals(cents("2,000.00"), d.savings.amount)
    }

    // SPEC:R67 SPEC:R34
    @Test fun `R67 changing an expense's category files later sweeps under the new one and leaves past months`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        var d = Recurring.create(world(), recurring("e", "60.00", Cadence.MONTHLY, LocalDate(2026, 9, 5), categoryId = "phone"), clock) { ids.next() }
        clock.setDay(LocalDate(2026, 9, 10))
        val (moved, r) = Recurring.edit(d, d.recurringExpenses[0].copy(categoryId = "net"), clock.today())
        assertNull(r)                                                                          // the month's sweep is unchanged
        d = Recurring.sweepOccurrences(moved, "e", LocalDate(2026, 10, 1), clock) { ids.next() }.first
        assertEquals(cents("60.00"), Spent.of(d, "phone", "2026-09"))
        assertEquals(0L, Spent.of(d, "net", "2026-09"))
        assertEquals(cents("60.00"), Spent.of(d, "net", "2026-10"))
        assertEquals(0L, Spent.of(d, "phone", "2026-10"))
    }

    // SPEC:R25 SPEC:R67
    @Test fun `R25 setting Phone's September spent to 100_00 over a 60_00 sweep logs a 40_00 adjustment`() {
        val clock = TestClock(LocalDate(2026, 9, 10)); val ids = Ids()
        var d = Recurring.create(world(), recurring("e", "60.00", Cadence.MONTHLY, LocalDate(2026, 9, 20), categoryId = "phone"), clock) { ids.next() }
        d = Spent.adjust(d, "phone", "2026-09", cents("100.00"), clock, "adj-1")
        assertEquals(cents("100.00"), Spent.of(d, "phone", "2026-09"))
        assertEquals(cents("40.00"), d.transactions.single { it.key == "cat:phone" }.amount)
    }
}

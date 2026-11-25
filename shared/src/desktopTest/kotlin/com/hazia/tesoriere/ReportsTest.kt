@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.goal
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.engine.AccountEditing
import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.NewMonth
import com.hazia.tesoriere.engine.Recurring
import com.hazia.tesoriere.engine.Reports
import com.hazia.tesoriere.engine.Slice
import com.hazia.tesoriere.engine.SliceKind
import com.hazia.tesoriere.engine.Writes
import com.hazia.tesoriere.engine.daysTo
import com.hazia.tesoriere.engine.firstOfNextMonth
import com.hazia.tesoriere.engine.plusDays
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.engine.ymKey
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.statements.BankRow
import com.hazia.tesoriere.statements.RowStatus
import com.hazia.tesoriere.statements.StatementImport
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RateType
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.model.Transaction
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import com.hazia.tesoriere.payments.Spent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/ReportsTest.kt at 2892110.
 * Changed: figures are cents; expenses are payments (SPEC R21), so the cases with Sci-Fin's
 * category quick-add and goal spend record payments instead, and the payment cases are added
 * (SPEC R95, R96, R98, R99, R100, R101); Spending's slices are a kind and a category id, not a label;
 * Income vs Expenses returns its figures with Income off, showing them being the caller's (SPEC
 * R18). Not ported: the parity test against the prototype's seeded figures, whose fixture is
 * Sci-Fin's prototype seed, not a synthetic Profile; Savings vs Owed's check on that seed becomes
 * the R100 cases below.
 */
class ReportsTest {
    private val today = LocalDate(2026, 9, 14)
    private val t0 = Instant.parse("2026-01-01T10:00:00Z")

    private fun tx(id: String, key: String, amount: String, date: LocalDate, income: Boolean = false, categoryId: String? = null) =
        Transaction(id, Fixture.PROFILE, date, t0, null, cents(amount), key, "x", income, categoryId = categoryId)

    private fun pay(id: String, total: String, date: LocalDate, source: PaymentSource = PaymentSource.Savings, parts: List<Part>? = null) =
        Payment(id, Fixture.PROFILE, date, t0, cents(total), source, null, parts ?: listOf(Part("c", cents(total))))

    private fun ProfileData.with(vararg rows: Transaction) = copy(transactions = transactions + rows)
    private fun ProfileData.paying(vararg ps: Payment) = copy(payments = payments + ps)

    private fun keyed(s: List<Slice>) = s.map { Triple(it.kind, it.categoryId, it.amount) }

    // ---- the movements of Savings ----------------------------------------------------------

    // SPEC:R95
    @Test fun `R95 a 100_00 sweep, 42_30 from Savings, 30_00 from a Goal and 40_00 charged to a card take 142_30 out of Savings`() {
        val d = profile().with(tx("s", "rec:e", "100.00", LocalDate(2026, 9, 1), categoryId = "c")).paying(
            pay("p1", "42.30", LocalDate(2026, 9, 3)),
            pay("p2", "30.00", LocalDate(2026, 9, 4), PaymentSource.Goal("g")),
            pay("p3", "40.00", LocalDate(2026, 9, 5), PaymentSource.Account("cc")),
        )
        val moves = Reports.savingsMovements(d)
        assertEquals(cents("142.30"), moves.filter { it.date.ymKey() == "2026-09" && it.effect < 0 }.sumOf { -it.effect })
        assertEquals(listOf(-cents("100.00"), -cents("42.30")), moves.map { it.effect })
    }

    // ---- Age of Money ----------------------------------------------------------------------

    // SPEC:R96
    @Test fun `R96 100_00 in on 1 June, 100_00 on 1 August, 50_00 out on 10 August read 105 days on 14 September 2026, and 44 after 60_00 more out`() {
        val d = profile(savings = "150.00").with(
            tx("a", "savings", "100.00", LocalDate(2026, 6, 1)),
            tx("b", "savings", "100.00", LocalDate(2026, 8, 1)),
            tx("c", "cat:x", "50.00", LocalDate(2026, 8, 10)),          // takes 50.00 of the June parcel
        )
        assertEquals(105, Reports.ageOfMoney(d, today))
        assertEquals(LocalDate(2026, 6, 1).daysTo(today), Reports.ageOfMoney(d, today))
        val d2 = d.with(tx("e", "cat:x", "60.00", LocalDate(2026, 9, 1)))  // the June parcel is gone
        assertEquals(44, Reports.ageOfMoney(d2, today))
        assertEquals(0, Reports.ageOfMoney(profile(), today))
    }

    // SPEC:R96
    @Test fun `R96 a 250_00 overshoot is a shortfall the next 1,000_00 fills, leaving a July parcel of 350_00 that reads 75 days`() {
        val base = profile(savings = "-250.00").with(
            tx("a", "savings", "100.00", LocalDate(2026, 6, 1)),
            tx("b", "cat:x", "350.00", LocalDate(2026, 6, 10)),
        )
        assertEquals(0, Reports.ageOfMoney(base, today))                       // Savings at or below 0: no oldest money
        val d = base.copy(savings = base.savings.copy(amount = cents("350.00"))).with(
            tx("c", "savings", "1,000.00", LocalDate(2026, 7, 1)),
            tx("e", "cat:x", "400.00", LocalDate(2026, 8, 1)),
        )
        assertEquals(75, Reports.ageOfMoney(d, today))                         // the July parcel, 350.00 of it
        val emptied = d.with(tx("f", "cat:x", "351.00", LocalDate(2026, 9, 1)))
        assertEquals(0, Reports.ageOfMoney(emptied, today))                    // 1.00 below zero reads 0, not July
    }

    // SPEC:R96 SPEC:R95
    @Test fun `R96 a 100_00 payment from Savings on 10 August empties the June parcel and reads 44, from a Goal it reads 105`() {
        val base = profile(savings = "100.00").with(
            tx("a", "savings", "100.00", LocalDate(2026, 6, 1)),
            tx("b", "savings", "100.00", LocalDate(2026, 8, 1)),
        )
        assertEquals(44, Reports.ageOfMoney(base.paying(pay("p", "100.00", LocalDate(2026, 8, 10))), today))
        assertEquals(105, Reports.ageOfMoney(base.paying(pay("p", "100.00", LocalDate(2026, 8, 10), PaymentSource.Goal("g"))), today))
        assertEquals(105, Reports.ageOfMoney(base.paying(pay("p", "100.00", LocalDate(2026, 8, 10), PaymentSource.Account("cc"))), today))
    }

    // SPEC:R96
    @Test fun `R96 a 500_00 acct row opens no parcel and reads 0`() {
        val d = profile().with(tx("a", "acct:c", "500.00", LocalDate(2026, 6, 1)))
        assertEquals(0, Reports.ageOfMoney(d, today))
    }

    // SPEC:R97
    @Test fun `R97 with 100_00 in on 20 August 2026 a two-month trend on 14 September reads 11 days for August and 25 for September`() {
        val d = profile(savings = "100.00").with(tx("a", "savings", "100.00", LocalDate(2026, 8, 20)))
        val pts = Reports.ageOfMoneyTrend(d, today, 2)
        assertEquals(listOf(11L, 25L), pts.map { it.value })                   // August as of the 31st, not the same day
        assertEquals(listOf(8, 9), pts.map { it.month.month.number })
    }

    // SPEC:R97
    @Test fun `R97 300_00 in on 10 July, 400_00 out on 5 August, 200_00 in on 1 September give points for July to September only, August at 0`() {
        val d = profile(savings = "100.00").with(
            tx("a", "savings", "300.00", LocalDate(2026, 7, 10)),
            tx("b", "cat:x", "400.00", LocalDate(2026, 8, 5)),
            tx("c", "savings", "200.00", LocalDate(2026, 9, 1)),
        )
        val pts = Reports.ageOfMoneyTrend(d, today, 6)
        assertEquals(listOf(7, 8, 9), pts.map { it.month.month.number })     // April to June left out, not drawn as 0
        assertEquals(0L, pts[1].value)                                        // August's shortfall is a real 0 and stays
    }

    // ---- Coverage --------------------------------------------------------------------------

    // SPEC:R98
    @Test fun `R98 on 14 September 2026 the window is 1 June to 31 August, 92 days`() {
        val w = Reports.trailingSpendWindow(today)
        assertEquals(LocalDate(2026, 6, 1), w.start); assertEquals(LocalDate(2026, 9, 1), w.end); assertEquals(92, w.days)
    }

    // SPEC:R98
    @Test fun `R98 920_00 against 920_00 out covers 92 days, 0 at -5_00, none when unset, too young or nothing went out`() {
        assertNull(Reports.coverageDaysOrNull(profile(savings = null), today))
        val young = profile().with(tx("a", "cat:x", "10.00", LocalDate(2026, 7, 1)))
        assertNull(Reports.coverageDaysOrNull(young, today))
        val noOut = profile().with(tx("a", "savings", "10.00", LocalDate(2026, 5, 1)))
        assertNull(Reports.coverageDaysOrNull(noOut, today))
        val ok = profile(savings = "920.00").with(tx("a", "savings", "10.00", LocalDate(2026, 5, 1)), tx("b", "cat:x", "920.00", LocalDate(2026, 7, 1)))
        assertEquals(92, Reports.coverageDaysOrNull(ok, today))                // 920.00 over 92 days is 10.00 a day
        assertEquals(0, Reports.coverageDaysOrNull(ok.copy(savings = ok.savings.copy(amount = cents("-5.00"))), today))
    }

    // SPEC:R98 SPEC:R95
    @Test fun `R98 a 920_00 payment from Savings covers 92 days, charged to a card there is no figure`() {
        val base = profile(savings = "920.00").with(tx("a", "savings", "10.00", LocalDate(2026, 5, 1)))
        assertEquals(92, Reports.coverageDaysOrNull(base.paying(pay("p", "920.00", LocalDate(2026, 7, 1))), today))
        assertNull(Reports.coverageDaysOrNull(base.paying(pay("p", "920.00", LocalDate(2026, 7, 1), PaymentSource.Account("cc"))), today))
        val paidFirst = profile(savings = "920.00").paying(pay("p0", "1.00", LocalDate(2026, 5, 1)), pay("p", "919.00", LocalDate(2026, 7, 1)))
        assertEquals(92, Reports.coverageDaysOrNull(paidFirst, today))         // a payment alone dates the ledger's start too
    }

    // SPEC:R98 SPEC:R20
    @Test fun `R98 25_00 against 920_00 out covers 2 days and 35_00 covers 4, a half to the even day`() {
        val out = profile().with(tx("a", "savings", "10.00", LocalDate(2026, 5, 1)), tx("b", "cat:x", "920.00", LocalDate(2026, 7, 1)))
        assertEquals(2, Reports.coverageDaysOrNull(out.copy(savings = out.savings.copy(amount = cents("25.00"))), today))
        assertEquals(4, Reports.coverageDaysOrNull(out.copy(savings = out.savings.copy(amount = cents("35.00"))), today))
        assertEquals(3, Reports.coverageDaysOrNull(out.copy(savings = out.savings.copy(amount = cents("30.01"))), today))
    }

    // ---- Spending --------------------------------------------------------------------------

    // SPEC:R99 SPEC:R24 SPEC:R25
    @Test fun `R99 a split payment of 42_30 counts 30_00 in Groceries and 12_30 in Household in Spending`() {
        val clock = TestClock(today)
        val d = Payments.record(
            profile(categories = listOf(category("groc", "Groceries"), category("home", "Household"))),
            PaymentDraft(today, cents("42.30"), PaymentSource.Savings, "Corner Market", listOf(Part("groc", cents("30.00")), Part("home", cents("12.30")))),
            clock, "p1",
        )
        val s = Reports.spendingComposition(d, today)
        assertEquals(
            listOf(Triple(SliceKind.CATEGORY, "groc", cents("30.00")), Triple(SliceKind.CATEGORY, "home", cents("12.30"))),
            keyed(s.slices),
        )
        assertEquals(cents("42.30"), s.net)
    }

    // SPEC:R99
    @Test fun `R99 100_00 and 50_00 into two Goals, a 30_00 adjustment on Food, income, a -20_00 savings row and 60_00 paid off a card give one slice, Food 30_00`() {
        val d = profile(goals = listOf(goal("g1", "A"), goal("g2", "B")), categories = listOf(category("c", "Food"))).with(
            tx("1", "goal:g1", "100.00", today), tx("2", "goal:g2", "50.00", today), tx("3", "cat:c", "30.00", today),
            tx("4", "savings", "1,450.00", today, income = true), tx("5", "savings", "-20.00", today), tx("6", "acct:x", "99.00", today),
            tx("7", "cat:c", "5.00", LocalDate(2026, 8, 30)),      // last month: left out
        )
        val ids = Ids("pay")
        val cardPaidOff = Accounts.quickAdd(
            d.copy(accounts = listOf(Account("cc", Fixture.PROFILE, "Card", type = AccountType.CREDIT, balance = cents("100.00")))),
            "cc", cents("60.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Savings, clock = TestClock(today),
        ) { ids.next() }
        val s = Reports.spendingComposition(cardPaidOff, today)
        assertEquals(listOf(Triple(SliceKind.CATEGORY, "c", cents("30.00"))), keyed(s.slices))
        assertEquals(cents("30.00"), s.net); assertFalse(s.plainList)
    }

    // SPEC:R99 SPEC:R67 SPEC:R22
    @Test fun `R99 a 60_00 sweep filed under Phone counts in Phone, and parts paid by card or from a Goal count in their categories`() {
        val d = profile(categories = listOf(category("phone", "Phone"), category("food", "Food"))).with(
            tx("s", "rec:e", "60.00", LocalDate(2026, 9, 1), categoryId = "phone"),
        ).paying(
            pay("p1", "40.00", today, PaymentSource.Account("cc"), listOf(Part("food", cents("40.00")))),
            pay("p2", "25.00", today, PaymentSource.Goal("g"), listOf(Part("food", cents("25.00")))),
        )
        assertEquals(
            listOf(Triple(SliceKind.CATEGORY, "food", cents("65.00")), Triple(SliceKind.CATEGORY, "phone", cents("60.00"))),
            keyed(Reports.spendingComposition(d, today).slices),
        )
    }

    // SPEC:R99 SPEC:R19 SPEC:R21
    @Test fun `R99 a 15_00 part with no category and a 9_00 sweep with no category make 24_00 Uncategorised`() {
        val d = profile().with(tx("s", "rec:e", "9.00", LocalDate(2026, 9, 1))).paying(
            pay("p", "15.00", today, PaymentSource.Goal("g"), listOf(Part(null, cents("15.00")))),
        )
        assertEquals(listOf(Triple(SliceKind.UNCATEGORISED, null, cents("24.00"))), keyed(Reports.spendingComposition(d, today).slices))
    }

    // SPEC:R62 SPEC:R99 SPEC:R107 SPEC:R67
    @Test fun `R62 a 600_00 yearly expense counts 50_00 in spent and Spending each month, the month its bill is paid included`() {
        val clock = TestClock(LocalDate(2026, 7, 15)); val ids = Ids()
        var d = Recurring.create(
            profile(savings = "2,000.00", categories = listOf(category("ins", "Insurance"))),
            Fixture.recurring("ins", "600.00", Cadence.YEARLY, LocalDate(2027, 7, 15), categoryId = "ins", name = "Insurance"),
            clock,
        ) { ids.next() }
        assertEquals(cents("50.00"), d.recurringExpenses.single().apportionedShare)
        var month = LocalDate(2026, 8, 1)
        while (month <= LocalDate(2027, 7, 1)) {
            d = Recurring.sweepOccurrences(d, "ins", month, clock) { ids.next() }.first
            month = month.firstOfNextMonth()
        }
        // the bank pays the whole 600.00 on the due date; its row arrives as already swept
        val bill = StatementImport.review(d, listOf(BankRow(LocalDate(2027, 7, 15), cents("-600.00"), "ASSICURAZIONE CASA POLIZZA 0099", null)), LocalDate(2027, 7, 31))
        assertEquals(RowStatus.RECURRING, bill.single().status)
        clock.setDay(LocalDate(2027, 7, 31))
        d = StatementImport.confirm(d, bill, clock) { ids.next() }
        month = LocalDate(2026, 8, 1)
        while (month <= LocalDate(2027, 7, 1)) {
            assertEquals(cents("50.00"), Spent.of(d, "ins", month.ymKey()), month.toString())
            assertEquals(
                listOf(Triple(SliceKind.CATEGORY, "ins", cents("50.00"))),
                keyed(Reports.spendingComposition(d, month.plusDays(14)).slices), month.toString(),
            )
            month = month.firstOfNextMonth()
        }
        assertTrue(d.payments.isEmpty())
    }

    // SPEC:R99
    @Test fun `R99 Food at 30_00 and -50_00 in one month is a plain list, and so is a month with nothing`() {
        val d = profile(categories = listOf(category("c", "Food"))).with(tx("1", "cat:c", "30.00", today), tx("2", "cat:c", "-50.00", today))
        assertTrue(Reports.spendingComposition(d, today).plainList)
        assertTrue(Reports.spendingComposition(profile(), today).plainList)
    }

    // ---- Savings vs Owed -------------------------------------------------------------------

    // SPEC:R100 SPEC:R54 SPEC:R86
    @Test fun `R100 1,000_00 in Savings, a card opened at 640_00 and 40_00 charged to it read 320_00, the trend 1,000_00, 1,000_00, 320_00`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        var d = profile(savings = null, categories = listOf(category("c", "Food")))
        d = Writes.savingsQuickAdd(d, cents("1,000.00"), null, false, clock, ids.next(), LocalDate(2026, 7, 10))
        d = AccountEditing.save(d, Account("cc", Fixture.PROFILE, "Card", type = AccountType.CREDIT, balance = cents("640.00")), clock) { ids.next() }
        clock.setDay(LocalDate(2026, 9, 5))
        d = Payments.record(d, PaymentDraft(clock.today, cents("40.00"), PaymentSource.Account("cc"), null, listOf(Part("c", cents("40.00")))), clock, ids.next())
        assertEquals(cents("320.00"), Reports.savingsVsOwedNow(d))
        val trend = Reports.savingsVsOwedTrend(d, today, 3)
        assertEquals(listOf(cents("1,000.00"), cents("1,000.00"), cents("320.00")), trend.map { it.value })
        assertEquals(Reports.savingsVsOwedNow(d), Reports.savingsVsOwedTrend(d, today).last().value)
    }

    /** 1,000.00 in Savings, a card at 0.00 with a 40.00 charge under Food, then the card deleted (SPEC R88). */
    private fun deletedCardWithCharge(clock: TestClock, ids: Ids): ProfileData {
        var d = profile(savings = null, categories = listOf(category("c", "Food")))
        d = Writes.savingsQuickAdd(d, cents("1,000.00"), null, false, clock, ids.next(), LocalDate(2026, 7, 10))
        d = AccountEditing.save(d, Account("cc", Fixture.PROFILE, "Card", type = AccountType.CREDIT), clock) { ids.next() }
        d = AccountEditing.save(d, Account("cc2", Fixture.PROFILE, "Second card", type = AccountType.CREDIT), clock) { ids.next() }
        d = Payments.record(d, PaymentDraft(clock.today, cents("40.00"), PaymentSource.Account("cc"), null, listOf(Part("c", cents("40.00")))), clock, "pay")
        d = Accounts.deleteCascade(d, "cc", clock) { ids.next() }
        assertEquals(Reports.savingsVsOwedNow(d), Reports.savingsVsOwedTrend(d, clock.today).last().value)
        return d
    }

    // SPEC:R100 SPEC:R54 SPEC:R88
    @Test fun `R100 a 40_00 charge to a card at 0_00 deleted since, removed, logs a 40_00 acct row and the trend still equals 1,000_00`() {
        val clock = TestClock(LocalDate(2026, 9, 5)); val ids = Ids()
        var d = deletedCardWithCharge(clock, ids)
        clock.setDay(today)
        d = Payments.remove(d, "pay", clock, ids.next())
        val row = d.transactions.last()
        assertEquals("acct:cc", row.key); assertEquals(cents("40.00"), row.amount); assertEquals(today, row.date)
        assertEquals(StoredLabels.DELETED_ACCOUNT_PAYMENT, row.payee)
        assertEquals(cents("1,000.00"), d.savingsAmount)
        assertEquals(cents("1,000.00"), Reports.savingsVsOwedNow(d))
        assertEquals(Reports.savingsVsOwedNow(d), Reports.savingsVsOwedTrend(d, today).last().value)
        assertEquals(0L, d.transactions.filter { it.key == "acct:cc" }.sumOf { it.amount })
    }

    // SPEC:R100 SPEC:R54 SPEC:R88
    @Test fun `R100 a 40_00 charge to a deleted card re-filed to a second card leaves it at 40_00, the trend 960_00 as live`() {
        val clock = TestClock(LocalDate(2026, 9, 5)); val ids = Ids()
        var d = deletedCardWithCharge(clock, ids)
        clock.setDay(today)
        d = Payments.edit(d, "pay", PaymentDraft(LocalDate(2026, 9, 5), cents("40.00"), PaymentSource.Account("cc2"), null, listOf(Part("c", cents("40.00")))), clock, ids.next())
        assertEquals(cents("40.00"), d.accounts.single { it.id == "cc2" }.balance)
        assertEquals(cents("40.00"), d.transactions.last { it.key == "acct:cc" }.amount)
        assertEquals(cents("960.00"), Reports.savingsVsOwedNow(d))
        assertEquals(Reports.savingsVsOwedNow(d), Reports.savingsVsOwedTrend(d, today).last().value)
        // a new total on the deleted card: 40.00 lowered to 25.00 logs +15.00
        val d2 = Payments.edit(deletedCardWithCharge(TestClock(LocalDate(2026, 9, 5)), Ids("b")), "pay",
            PaymentDraft(LocalDate(2026, 9, 5), cents("25.00"), PaymentSource.Account("cc"), null, listOf(Part("c", cents("25.00")))), clock, "tx-e")
        assertEquals(cents("15.00"), d2.transactions.last().amount)
        assertEquals(Reports.savingsVsOwedNow(d2), Reports.savingsVsOwedTrend(d2, today).last().value)
    }

    // SPEC:R100 SPEC:R50
    @Test fun `R100 400_00 put into a Goal and a 42_30 payment from Savings leave 557_70 against nothing owed`() {
        val clock = TestClock(today); val ids = Ids()
        var d = profile(savings = null, goals = listOf(goal("g")), categories = listOf(category("c")))
        d = Writes.savingsQuickAdd(d, cents("1,000.00"), null, false, clock, ids.next(), LocalDate(2026, 8, 1))
        d = Writes.goalQuickAdd(d, "g", cents("400.00"), null, clock, ids.next())
        d = Payments.record(d, PaymentDraft(today, cents("42.30"), PaymentSource.Savings, null, listOf(Part("c", cents("42.30")))), clock, ids.next())
        assertEquals(cents("557.70"), Reports.savingsVsOwedNow(d))
        assertEquals(listOf(cents("1,000.00"), cents("557.70")), Reports.savingsVsOwedTrend(d, today, 2).map { it.value })
    }

    // ---- Income vs Expenses ----------------------------------------------------------------

    // SPEC:R99 SPEC:R101 SPEC:R67 SPEC:R93 SPEC:R25
    @Test fun `R99 40_00 charged to a card under Food and the card's 40_00 payment swept under Food give Food 40_00, Spending 40_00 and 40_00 out`() {
        val clock = TestClock(today); val ids = Ids()
        var d = profile(categories = listOf(category("food", "Food")))
        d = AccountEditing.save(d, Account("cc", Fixture.PROFILE, "Card", type = AccountType.CREDIT), clock) { ids.next() }
        d = Accounts.commitFixedPayment(d, "cc", cents("40.00"), "", "food", clock, "card-pay")   // first due 14 October
        clock.setDay(LocalDate(2026, 10, 1))
        d = NewMonth.runCatchUp(d, clock) { ids.next() }.first
        val sweep = d.transactions.single { it.key == "rec:card-pay" }
        assertEquals(cents("40.00"), sweep.amount); assertEquals(LocalDate(2026, 10, 14), sweep.date)
        assertEquals(cents("960.00"), d.savingsAmount)                                            // the sweep still moves Savings
        d = Payments.record(d, PaymentDraft(LocalDate(2026, 10, 5), cents("40.00"), PaymentSource.Account("cc"), null, listOf(Part("food", cents("40.00")))), clock, "pay")
        val oct20 = LocalDate(2026, 10, 20)
        assertEquals(cents("40.00"), Spent.of(d, "food", "2026-10"))
        val s = Reports.spendingComposition(d, oct20)
        assertEquals(listOf(Slice(SliceKind.CATEGORY, "food", cents("40.00"))), s.slices)
        assertEquals(cents("40.00"), s.net)
        assertEquals(cents("40.00"), Reports.incomeVsExpenses(d, oct20, 1).single().expenses)
    }

    // SPEC:R67 SPEC:R99 SPEC:R73
    @Test fun `R67 R87 a Loan's 229_70 payment swept under Car still counts 229_70 in Car's spent and Spending`() {
        val clock = TestClock(today); val ids = Ids()
        var d = profile(savings = "5,000.00", categories = listOf(category("car", "Car")))
        d = Accounts.getLoan(d, cents("9,800.00"), 0.059, 48, RateType.FIXED, "Car loan", "Car loan payment", "car", false, clock) { ids.next() }
        clock.setDay(LocalDate(2026, 10, 1))
        d = NewMonth.runCatchUp(d, clock) { ids.next() }.first
        assertEquals(cents("229.70"), Spent.of(d, "car", "2026-10"))
        assertEquals(cents("229.70"), Reports.spendingComposition(d, LocalDate(2026, 10, 20)).net)
    }

    // SPEC:R67 SPEC:R66 SPEC:R93
    @Test fun `R67 R87 a card's 40_00 payment released from the card, deleted with its return, lowers no spent and Savings is 1,000_00`() {
        val clock = TestClock(today); val ids = Ids()
        var d = profile(categories = listOf(category("food", "Food")))
        d = AccountEditing.save(d, Account("cc", Fixture.PROFILE, "Card", type = AccountType.CREDIT), clock) { ids.next() }
        d = Accounts.commitFixedPayment(d, "cc", cents("40.00"), "", "food", clock, "card-pay")
        clock.setDay(LocalDate(2026, 10, 1))
        d = NewMonth.runCatchUp(d, clock) { ids.next() }.first                    // swept as a card payoff, dated 14 October
        clock.setDay(LocalDate(2026, 10, 10))
        d = Accounts.commitFixedPayment(d, "cc", cents("50.00"), "", "food", clock, "card-pay-2")   // releases card-pay (R87)
        assertNull(d.recurringExpenses.single { it.id == "card-pay" }.linkedAccountId)
        d = Recurring.delete(d, "card-pay", reverse = true, clock) { ids.next() }
        val back = d.transactions.last { it.key == "rec:card-pay" }
        assertEquals(cents("-40.00"), back.amount)
        assertEquals(cents("1,000.00"), d.savingsAmount)
        assertEquals(0L, Spent.of(d, "food", "2026-10"))
        assertTrue(Reports.spendingComposition(d, LocalDate(2026, 10, 20)).slices.isEmpty())
    }

    // SPEC:R101 SPEC:R54
    @Test fun `R101 1,450_00 in, 200_00 into a Goal and 30_00 paid from Savings read 30_00 out, 110_00 after 80_00 from the Goal, 150_00 after 40_00 by card and after 60_00 paid off it`() {
        val clock = TestClock(today); val ids = Ids()
        var d = profile(goals = listOf(goal("g", "G")), categories = listOf(category("c", "Food")))
        d = d.copy(settings = d.settings.copy(incomeEnabled = true), accounts = listOf(Account("cc", Fixture.PROFILE, "Card", type = AccountType.CREDIT)))
        d = Writes.savingsQuickAdd(d, cents("1,450.00"), "Salary", true, clock, ids.next())
        d = Writes.goalQuickAdd(d, "g", cents("200.00"), null, clock, ids.next())
        fun ProfileData.paid(total: String, source: PaymentSource) =
            Payments.record(this, PaymentDraft(today, cents(total), source, null, listOf(Part("c", cents(total)))), clock, ids.next())
        d = d.paid("30.00", PaymentSource.Savings)
        val pts = Reports.incomeVsExpenses(d, today)
        assertEquals(6, pts.size); assertEquals(cents("1,450.00"), pts.last().income); assertEquals(cents("30.00"), pts.last().expenses)
        d = d.paid("80.00", PaymentSource.Goal("g"))
        assertEquals(cents("110.00"), Reports.incomeVsExpenses(d, today).last().expenses)   // the contribution still is not
        d = d.paid("40.00", PaymentSource.Account("cc"))
        assertEquals(cents("150.00"), Reports.incomeVsExpenses(d, today).last().expenses)
        d = Accounts.quickAdd(d, "cc", cents("60.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Savings, clock = clock) { ids.next() }
        assertEquals(cents("150.00"), Reports.incomeVsExpenses(d, today).last().expenses)   // paying the card off is a transfer
        assertEquals(Reports.spendingComposition(d, today).net, Reports.incomeVsExpenses(d, today).last().expenses)
        d = d.paid("-15.00", PaymentSource.Savings)                                          // a refund (SPEC R21)
        assertEquals(cents("135.00"), Reports.incomeVsExpenses(d, today).last().expenses)
        assertEquals(cents("1,450.00"), Reports.incomeVsExpenses(d, today).last().income)
    }

    // SPEC:R95 SPEC:R54
    @Test fun `R95 a 60_00 payment from a Goal holding 50_00 takes 10_00 out of Savings on its date`() {
        val d0 = profile(goals = listOf(goal("g", "G", saved = "50.00")), categories = listOf(category("c", "Food")))
        val d = Payments.record(d0, PaymentDraft(today, cents("60.00"), PaymentSource.Goal("g"), null, listOf(Part("c", cents("60.00")))), TestClock(today), "p")
        assertEquals(listOf(today to cents("-10.00")), Reports.savingsMovements(d).map { it.date to it.effect })
    }

    // SPEC:R95 SPEC:R96 SPEC:R98 SPEC:R21
    @Test fun `R95 a 30_00 refund to Savings on 1 September is money in, Age of Money 13 days, and a 460_00 refund is no outflow for Coverage`() {
        val clock = TestClock(today); val ids = Ids("pay")
        fun ProfileData.paid(total: String, date: LocalDate) =
            Payments.record(this, PaymentDraft(date, cents(total), PaymentSource.Savings, null, listOf(Part("c", cents(total)))), clock, ids.next())
        var d = profile(categories = listOf(category("c", "Clothing"))).with(tx("in", "savings", "100.00", LocalDate(2026, 6, 1)))
        d = d.paid("100.00", LocalDate(2026, 8, 10))
        assertEquals(0, Reports.ageOfMoney(d, today))
        d = d.paid("-30.00", LocalDate(2026, 9, 1))
        assertEquals(cents("30.00"), Reports.savingsMovements(d).last().effect)
        assertEquals(13, Reports.ageOfMoney(d, today))
        val c0 = profile(savings = "920.00", categories = listOf(category("c", "Clothing")))
            .with(tx("a", "savings", "10.00", LocalDate(2026, 5, 1)), tx("b", "cat:x", "920.00", LocalDate(2026, 7, 1)))
        val c1 = c0.paid("-460.00", LocalDate(2026, 8, 1))
        assertEquals(92, Reports.coverageDaysOrNull(c1.copy(savings = c0.savings), today))   // the outflow is still 920.00
    }

    // SPEC:R99 SPEC:R101 SPEC:R21
    @Test fun `R99 a 40_00 payment and a 15_00 refund under Food give one slice, Food 25_00, and Income vs Expenses 25_00 out with 1,450_00 in`() {
        val clock = TestClock(today); val ids = Ids("pay")
        var d = profile(categories = listOf(category("food", "Food")))
        d = Writes.savingsQuickAdd(d, cents("1,450.00"), "Salary", true, clock, ids.next())
        fun ProfileData.paid(total: String) =
            Payments.record(this, PaymentDraft(today, cents(total), PaymentSource.Savings, null, listOf(Part("food", cents(total)))), clock, ids.next())
        d = d.paid("40.00").paid("-15.00")
        assertEquals(listOf(Triple(SliceKind.CATEGORY, "food", cents("25.00"))), keyed(Reports.spendingComposition(d, today).slices))
        val point = Reports.incomeVsExpenses(d, today).last()
        assertEquals(cents("25.00"), point.expenses); assertEquals(cents("1,450.00"), point.income)
    }

    // SPEC:R101 SPEC:R18
    @Test fun `R101 with Income off the six months are still worked out, 100_00 swept in September and no income`() {
        val d = profile().with(tx("s", "rec:e", "100.00", LocalDate(2026, 9, 1), categoryId = "c"))
        val pts = Reports.incomeVsExpenses(d, today)
        assertEquals(listOf(4, 5, 6, 7, 8, 9), pts.map { it.month.month.number })
        assertEquals(cents("100.00"), pts.last().expenses); assertEquals(0L, pts.sumOf { it.income })
    }
}

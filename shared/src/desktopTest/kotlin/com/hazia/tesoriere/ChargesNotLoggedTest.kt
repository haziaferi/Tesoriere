@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.goal
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.engine.AccountEditing
import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.Ledger
import com.hazia.tesoriere.engine.Recurring
import com.hazia.tesoriere.engine.Reports
import com.hazia.tesoriere.engine.Slice
import com.hazia.tesoriere.engine.SliceKind
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.Transaction
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentNotice
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import com.hazia.tesoriere.payments.Spent
import com.hazia.tesoriere.statements.BankRow
import com.hazia.tesoriere.statements.StatementImport
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Tesoriere's own: a card whose charges are not logged (SPEC R109, owner answer D of 2026-11-25).
 * Every figure is made up.
 */
class ChargesNotLoggedTest {
    private val sept = LocalDate(2026, 9, 14)
    private val clock = TestClock(sept)
    private val ids = Ids()

    private fun card(on: Boolean = true, category: String? = "shop", linked: String? = null) = Account(
        "cc", Fixture.PROFILE, "Orbita card", type = AccountType.CREDIT, balance = cents("300.00"),
        chargesNotLogged = on, payoffCategoryId = category, linkedRecurringExpenseId = linked,
    )

    /** Savings 1,000.00, a Trip goal holding 80.00, Shopping and Household, and the card at 300.00. */
    private fun world(card: Account = card()): ProfileData = profile(
        goals = listOf(goal("trip", "Trip", saved = "80.00")),
        categories = listOf(category("shop", "Shopping"), category("home", "Household")),
    ).copy(accounts = listOf(card))

    private fun ProfileData.paidOff(amount: String, from: Accounts.PayoffFrom, date: LocalDate = clock.today) =
        Accounts.quickAdd(this, "cc", cents(amount), Accounts.Direction.PAYMENT, null, from, clock, date) { ids.next() }

    private fun shop(d: ProfileData, ym: String) = Spent.of(d, "shop", ym)

    // SPEC:R109 SPEC:R89 SPEC:R25 SPEC:R99 SPEC:R101
    @Test fun `R109 with the switch on, 60_00 paid off the card from Savings adds 60_00 to Shopping's September and to Spending, and 60_00 from a Goal 60_00 more`() {
        var d = world().paidOff("60.00", Accounts.PayoffFrom.Savings)
        assertEquals(cents("60.00"), shop(d, "2026-09"))
        assertEquals("shop", d.transactions.single { it.key == "savings" }.categoryId)
        assertEquals(listOf(Slice(SliceKind.CATEGORY, "shop", cents("60.00"))), Reports.spendingComposition(d, sept).slices)
        assertEquals(cents("60.00"), Reports.incomeVsExpenses(d, sept).last().expenses)
        assertEquals(cents("940.00"), d.savings.amount)
        d = d.paidOff("60.00", Accounts.PayoffFrom.Goal("trip"))
        assertEquals(cents("120.00"), shop(d, "2026-09"))
        assertEquals(cents("20.00"), d.goals.single().saved)
        // from nowhere it counts nothing
        assertEquals(cents("120.00"), shop(d.paidOff("10.00", Accounts.PayoffFrom.None), "2026-09"))
    }

    // SPEC:R109 SPEC:R67 SPEC:R93 SPEC:R66
    @Test fun `R109 the card's 50_00 fixed payment sweeps into Shopping while on, its return lowers Shopping, and while off it is a card payoff`() {
        val fixed = Fixture.recurring("fix", "50.00", Cadence.MONTHLY, firstDue = LocalDate(2026, 9, 20), categoryId = "home", name = "Orbita card payment", linkedAccountId = "cc")
        val on = world(card(linked = "fix")).copy(recurringExpenses = listOf(fixed))
        val swept = Recurring.sweepOccurrences(on, "fix", LocalDate(2026, 9, 1), clock) { ids.next() }.first
        val row = swept.transactions.single()
        assertEquals("shop" to false, row.categoryId to row.cardPayoff)
        assertEquals(cents("50.00"), shop(swept, "2026-09")); assertEquals(0L, Spent.of(swept, "home", "2026-09"))
        val returned = Recurring.delete(swept, "fix", reverse = true, clock) { ids.next() }
        assertEquals(0L, shop(returned, "2026-09"))
        val off = world(card(on = false, linked = "fix")).copy(recurringExpenses = listOf(fixed))
        val sweptOff = Recurring.sweepOccurrences(off, "fix", LocalDate(2026, 9, 1), clock) { ids.next() }.first
        assertTrue(sweptOff.transactions.single().cardPayoff)
        assertEquals(0L, shop(sweptOff, "2026-09")); assertEquals(0L, Spent.of(sweptOff, "home", "2026-09"))
    }

    // SPEC:R109 SPEC:R107 SPEC:R89
    @Test fun `R109 a 40_00 statement payoff of the card from Savings adds 40_00 to Shopping`() {
        val d = world()
        val review = StatementImport.review(d, listOf(BankRow(LocalDate(2026, 9, 10), -4000, "ADDEBITO ORBITA", null)), clock.today)
            .map { it.copy(payoffAccountId = "cc") }
        val after = StatementImport.confirm(d, review, clock) { ids.next() }
        assertEquals(cents("40.00"), shop(after, "2026-09"))
        assertEquals(cents("260.00"), after.accounts.single().balance)
    }

    // SPEC:R109
    @Test fun `R109 turned off in October, Shopping's September stays 60_00 and a 30_00 October payoff adds nothing`() {
        var d = world().paidOff("60.00", Accounts.PayoffFrom.Savings)
        clock.setDay(LocalDate(2026, 10, 5))
        d = AccountEditing.save(d, d.accounts.single().copy(chargesNotLogged = false), clock) { ids.next() }
        assertFalse(d.accounts.single().chargesNotLogged)
        d = d.paidOff("30.00", Accounts.PayoffFrom.Savings)
        assertEquals(cents("60.00"), shop(d, "2026-09"))
        assertEquals(0L, shop(d, "2026-10"))
    }

    // SPEC:R109
    @Test fun `R109 a card off whose September payoff counted nothing, turned on in October, leaves September as it was, and so does deleting it`() {
        var d = world(card(on = false, category = null)).paidOff("60.00", Accounts.PayoffFrom.Savings)
        assertEquals(0L, shop(d, "2026-09"))
        clock.setDay(LocalDate(2026, 10, 5))
        d = AccountEditing.save(d, d.accounts.single().copy(chargesNotLogged = true, payoffCategoryId = "shop"), clock) { ids.next() }
        assertTrue(d.accounts.single().chargesNotLogged)
        assertEquals(0L, shop(d, "2026-09"))
        assertTrue(Reports.spendingComposition(d, sept).slices.isEmpty())
        d = d.paidOff("30.00", Accounts.PayoffFrom.Savings)
        val gone = Accounts.deleteCascade(d, "cc", clock) { ids.next() }
        assertEquals(cents("30.00"), shop(gone, "2026-10"))
    }

    // SPEC:R109 SPEC:R86
    @Test fun `R109 the switch on with no payoff category, or one that does not exist, is not saved`() {
        val d = world(card(on = false, category = null))
        val c = d.accounts.single()
        for (bad in listOf(c.copy(chargesNotLogged = true), c.copy(chargesNotLogged = true, payoffCategoryId = "nowhere"))) {
            assertFalse(AccountEditing.canSave(d, bad))
            assertEquals(d, AccountEditing.save(d, bad, clock) { ids.next() })
        }
        assertTrue(AccountEditing.canSave(d, c.copy(chargesNotLogged = true, payoffCategoryId = "shop")))
        val loan = Account("l", Fixture.PROFILE, "Loan", type = AccountType.LOAN, chargesNotLogged = true, payoffCategoryId = "shop")
        assertFalse(AccountEditing.canSave(d, loan))
    }

    // SPEC:R109 SPEC:R22
    @Test fun `R109 a payment charged to the card carries the warning, and none with the switch off`() {
        val draft = PaymentDraft(sept, cents("12.00"), PaymentSource.Account("cc"), null, listOf(Part("home", cents("12.00"))))
        assertEquals(PaymentNotice.CHARGES_NOT_LOGGED, Payments.draftNotice(world(), draft))
        assertNull(Payments.draftNotice(world(card(on = false)), draft))
        assertTrue(Payments.canSave(world(), draft))
    }

    // SPEC:R94 SPEC:R109
    @Test fun `R94 a card whose charges were not logged under Shopping, changed to a Loan, has the switch off and no payoff category`() {
        val loan = Accounts.retype(card(), AccountType.LOAN)
        assertFalse(loan.chargesNotLogged); assertNull(loan.payoffCategoryId)
    }

    private val t0 = Instant.parse("2026-09-14T10:00:00Z")
    private fun row(id: String, amount: String, categoryId: String?, after: Int = 0) =
        Transaction(id, Fixture.PROFILE, sept, t0 + after.hours, null, cents(amount), "savings", "Savings", categoryId = categoryId)

    // SPEC:R53 SPEC:R109
    @Test fun `R53 a -60_00 savings row filed under Shopping and a 60_00 correction an hour later do not pair, two filed under Shopping do`() {
        assertTrue(Ledger.pairEligibility(listOf(row("a", "-60.00", "shop"), row("b", "60.00", null, after = 1))).isEmpty())
        assertEquals(mapOf("a" to "b", "b" to "a"), Ledger.pairEligibility(listOf(row("a", "-60.00", "shop"), row("b", "60.00", "shop", after = 1))))
    }
}

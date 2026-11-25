package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.goal
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.GoalEditing
import com.hazia.tesoriere.engine.Recurring
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.payments.Spent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tesoriere's own: undoing an Account quick-add as a whole (SPEC R111, owner answer of
 * 2026-11-25 to review finding M2). Every figure is made up.
 */
class QuickAddUndoTest {
    private val day = LocalDate(2026, 9, 14)
    private val clock = TestClock(day)
    private val ids = Ids()

    private fun card(balance: String = "100.00") = Account("cc", Fixture.PROFILE, "Card", type = AccountType.CREDIT, balance = cents(balance))

    /** Savings 1,000.00, a Trip goal holding 80.00, Shopping and Fees, and the card. */
    private fun world(card: Account = card()): ProfileData = profile(
        goals = listOf(goal("trip", "Trip", saved = "80.00")),
        categories = listOf(category("shop", "Shopping"), category("fees", "Fees")),
    ).copy(accounts = listOf(card))

    private fun ProfileData.paid(amount: String, from: Accounts.PayoffFrom) =
        Accounts.quickAdd(this, "cc", cents(amount), Accounts.Direction.PAYMENT, null, from, clock) { ids.next() }

    /** The group of the last quick-add's rows. */
    private fun ProfileData.lastGroup(): String = requireNotNull(transactions.last().groupId)

    // SPEC:R111 SPEC:R89 SPEC:R53
    @Test fun `R111 a card at 100_00 paid 60_00 from Savings of 1,000_00 and undone reads 100_00 with Savings 1,000_00 and neither row left`() {
        val d0 = world()
        val d1 = d0.paid("60.00", Accounts.PayoffFrom.Savings)
        val group = d1.lastGroup()
        assertEquals(2, d1.transactions.count { it.groupId == group })                     // its acct: and savings rows
        assertNull(Accounts.undoQuickAddProblem(d1, group))
        val undone = Accounts.undoQuickAdd(d1, group)
        assertEquals(d0, undone)
    }

    // SPEC:R111 SPEC:R49
    @Test fun `R111 on a Profile whose Savings is unset, a card paid 60_00 from Savings and undone leaves Savings at 0_00, not unset`() {
        val d0 = world().copy(savings = world().savings.copy(amount = null))
        val paid = d0.paid("60.00", Accounts.PayoffFrom.Savings)
        assertEquals(cents("-60.00"), paid.savings.amount)
        val undone = Accounts.undoQuickAdd(paid, paid.lastGroup())
        assertEquals(0L, undone.savings.amount)
        assertEquals(cents("100.00"), undone.accounts.single().balance)
    }

    // SPEC:R111 SPEC:R51
    @Test fun `R111 paid 60_00 from a Goal holding 80_00 and undone, the Goal holds 80_00, and a second quick-add is untouched`() {
        val d1 = world().paid("20.00", Accounts.PayoffFrom.None)
        val keep = d1.lastGroup()
        val d2 = d1.paid("60.00", Accounts.PayoffFrom.Goal("trip"))
        val undone = Accounts.undoQuickAdd(d2, d2.lastGroup())
        assertEquals(cents("80.00"), undone.goals.single().saved)
        assertEquals(cents("80.00"), undone.accounts.single().balance)
        assertEquals(d1.transactions, undone.transactions)
        assertTrue(undone.transactions.all { it.groupId == keep })
    }

    // SPEC:R111 SPEC:R109 SPEC:R110
    @Test fun `R111 a payoff filed under Shopping and a 12_50 fee filed under Fees, undone, leave Shopping, Fees and the card as they were`() {
        val notLogged = world(card().copy(chargesNotLogged = true, payoffCategoryId = "shop"))
        val paid = notLogged.paid("60.00", Accounts.PayoffFrom.Savings)
        assertEquals(cents("60.00"), Spent.of(paid, "shop", "2026-09"))
        assertEquals(0L, Spent.of(Accounts.undoQuickAdd(paid, paid.lastGroup()), "shop", "2026-09"))
        val withFees = world(card(balance = "300.00").copy(feesCategoryId = "fees"))
        val fee = Accounts.quickAdd(withFees, "cc", cents("12.50"), Accounts.Direction.INCREASE, null, Accounts.PayoffFrom.None, clock) { ids.next() }
        assertEquals(cents("12.50"), Spent.of(fee, "fees", "2026-09"))
        val undone = Accounts.undoQuickAdd(fee, fee.lastGroup())
        assertEquals(cents("300.00"), undone.accounts.single().balance)
        assertEquals(0L, Spent.of(undone, "fees", "2026-09"))
    }

    // SPEC:R111 SPEC:R87 SPEC:R58
    @Test fun `R111 a quick-add on a card deleted since, or from a Goal deleted since, is refused with a code and changes nothing`() {
        val paid = world().paid("60.00", Accounts.PayoffFrom.Goal("trip"))
        val group = paid.lastGroup()
        val cardGone = Accounts.deleteCascade(paid, "cc", clock) { ids.next() }
        assertEquals(Accounts.UndoProblem.ACCOUNT_DELETED, Accounts.undoQuickAddProblem(cardGone, group))
        assertFailsWith<IllegalArgumentException> { Accounts.undoQuickAdd(cardGone, group) }
        val goalGone = GoalEditing.delete(paid, "trip", returnSaved = true, clock) { ids.next() }
        assertEquals(Accounts.UndoProblem.GOAL_DELETED, Accounts.undoQuickAddProblem(goalGone, group))
    }

    // SPEC:R111 SPEC:R53 SPEC:R63
    @Test fun `R111 a row with no group and a sweep's row are not undone`() {
        val d = world().paid("60.00", Accounts.PayoffFrom.Savings)
        assertEquals(Accounts.UndoProblem.NO_SUCH_GROUP, Accounts.undoQuickAddProblem(d, "nothing"))
        val e = Fixture.recurring("pay", "50.00", Cadence.MONTHLY, firstDue = LocalDate(2026, 9, 20), categoryId = "shop")
        val swept = Recurring.sweepOccurrences(world().copy(recurringExpenses = listOf(e)), "pay", LocalDate(2026, 9, 1), clock) { ids.next() }.first
        val sweep = swept.transactions.single()
        assertNull(sweep.groupId)
        // a group that holds a sweep, as no quick-add writes, is refused
        val forged = swept.copy(transactions = swept.transactions.map { it.copy(groupId = "g") })
        assertEquals(Accounts.UndoProblem.NOT_A_QUICK_ADD, Accounts.undoQuickAddProblem(forged, "g"))
    }
}

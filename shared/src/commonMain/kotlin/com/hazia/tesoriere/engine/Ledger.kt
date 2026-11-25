@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Ledger.kt at 2892110.
 *
 * The ledger (SPEC R51) and pair deletion (SPEC R53). Every movement of money other than a
 * payment goes through [Ledger.logAndMove], the one writer that both logs and moves. Account
 * rows are logged but never move Savings.
 */

/** A ledger row's key, parsed once. The stored form keeps Sci-Fin's spelling (SPEC R51). */
sealed class TransactionKey(val stored: String) {
    data object SavingsKey : TransactionKey("savings")
    data class GoalKey(val id: String) : TransactionKey("goal:$id")
    /** An adjustment to a category's spent figure (SPEC R25). */
    data class CategoryKey(val id: String) : TransactionKey("cat:$id")
    data class RecurringKey(val id: String) : TransactionKey("rec:$id")
    data class AccountKey(val id: String) : TransactionKey("acct:$id")
    /** Money spent out of a Goal in Sci-Fin; only read from a Sci-Fin backup (SPEC R19, R51). */
    data class GoalSpendKey(val id: String) : TransactionKey("spend:$id")

    companion object {
        fun parse(s: String): TransactionKey {
            if (s == "savings") return SavingsKey
            val i = s.indexOf(':')
            require(i > 0) { "not a transaction key: $s" }
            val id = s.substring(i + 1)
            return when (s.substring(0, i)) {
                "goal" -> GoalKey(id)
                "cat" -> CategoryKey(id)
                "rec" -> RecurringKey(id)
                "acct" -> AccountKey(id)
                "spend" -> GoalSpendKey(id)
                else -> throw IllegalArgumentException("not a transaction key: $s")
            }
        }
    }
}

/** What a write hands the ledger. `amount` is always the change to the target (SPEC R51). */
data class Movement(
    val amount: Money,
    val key: TransactionKey = TransactionKey.SavingsKey,
    val targetLabel: String = StoredLabels.SAVINGS,
    val payee: String? = null,
    val isIncome: Boolean = false,
    /** null = today, the device's day. */
    val date: LocalDate? = null,
    /** Hourly paydays only. */
    val hoursWorked: Double? = null,
    val rate: Money? = null,
    /** A recurring sweep's category at sweep time (owner answer 2 of 2026-11-24), or a card's payoff category (SPEC R109). */
    val categoryId: String? = null,
    /** A `rec:` row of an expense linked to a card, or to a Loan with a payment charged to it: counted in no spent (SPEC R67, R93). */
    val accountPayoff: Boolean = false,
    /** The group of the rows of one Account quick-add, undone together (SPEC R111). */
    val groupId: String? = null,
    /** A bimonthly or yearly expense's share row (SPEC R63). */
    val share: Boolean = false,
)

object Ledger {

    /** How far apart two rows of a pair may be logged (SPEC R53). */
    val PAIR_WINDOW = 48.hours

    /** SPEC R51's table, in one place: the Savings movement follows from the key and is never stored. */
    fun savingsEffect(tx: Transaction): Money = when (TransactionKey.parse(tx.key)) {
        TransactionKey.SavingsKey -> tx.amount
        is TransactionKey.AccountKey -> 0
        is TransactionKey.GoalSpendKey -> 0      // it left Savings when it went into the Goal
        else -> -tx.amount
    }

    /** What one ledger row counts in a category's spent (SPEC R25): the category, null for Uncategorised, and the amount. */
    data class RowSpent(val categoryId: String?, val amount: Money)

    /**
     * What [tx] counts as spending, read as it was written (SPEC R25, R109), or null for none: a
     * `cat:` adjustment in its own category; a sweep in the category it was filed under, unless it
     * is an Account payoff (SPEC R67); a `savings` or `spend:` row filed under a card's payoff
     * category by minus its amount, the money that left Savings or the Goal (SPEC R109); an
     * `acct:` row filed under a category by its amount, a card's interest or fees (SPEC R110).
     * Every other row, income, transfers and corrections, counts nowhere.
     */
    fun spent(tx: Transaction): RowSpent? = when (val k = TransactionKey.parse(tx.key)) {
        is TransactionKey.CategoryKey -> RowSpent(k.id, tx.amount)
        is TransactionKey.RecurringKey -> if (tx.accountPayoff) null else RowSpent(tx.categoryId, tx.amount)
        TransactionKey.SavingsKey, is TransactionKey.GoalSpendKey -> tx.categoryId?.let { RowSpent(it, -tx.amount) }
        is TransactionKey.AccountKey -> tx.categoryId?.let { RowSpent(it, tx.amount) }
        is TransactionKey.GoalKey -> null
    }

    /**
     * The one writer for every movement that both logs and moves money. Returns the new state
     * and the row it wrote. A movement that touches Savings, or any `savings` row, ends the unset
     * state, counting from zero (SPEC R49).
     */
    fun logAndMove(d: ProfileData, m: Movement, clock: Clock, id: String): Pair<ProfileData, Transaction> {
        val tx = Transaction(
            id = id,
            profileId = d.profileId,
            date = m.date ?: clock.today(),
            loggedAt = clock.now(),
            payee = m.payee?.takeIf { it.isNotBlank() },
            amount = m.amount,
            key = m.key.stored,
            targetLabel = m.targetLabel,
            isIncome = m.isIncome,
            hoursWorked = m.hoursWorked,
            rate = m.rate,
            categoryId = m.categoryId,
            accountPayoff = m.accountPayoff,
            groupId = m.groupId,
            share = m.share,
        )
        val effect = savingsEffect(tx)
        var next = d.copy(transactions = d.transactions + tx)
        if (effect != 0L || m.key == TransactionKey.SavingsKey) {
            next = next.copy(savings = next.savings.copy(amount = (next.savings.amount ?: 0) + effect))
        }
        if (m.isIncome) {
            // a row dated earlier never moves the marker back
            val last = next.settings.lastIncomeLoggedDate
            next = next.copy(settings = next.settings.copy(lastIncomeLoggedDate = if (last == null || tx.date > last) tx.date else last))
        }
        return next to tx
    }

    /** A movement on an Account alone: logged, never touching Savings (SPEC R51); [categoryId] files a card's interest or fees (SPEC R110). */
    fun logAccountEdit(
        d: ProfileData, accountId: String, accountName: String, delta: Money, payee: String?, clock: Clock, id: String,
        date: LocalDate? = null, categoryId: String? = null, groupId: String? = null,
    ): ProfileData =
        logAndMove(d, Movement(delta, TransactionKey.AccountKey(accountId), accountName, payee, date = date, categoryId = categoryId, groupId = groupId), clock, id).first

    /**
     * SPEC R53's conditions, found once against the ledger as it stands. Returns each row that
     * can be deleted with its counterpart's id, both ways. Ordered by `loggedAt`, not `date`:
     * ordering by date would let a backdated row slip between a pair.
     */
    fun pairEligibility(transactions: List<Transaction>): Map<String, String> {
        val pairs = HashMap<String, String>()
        transactions
            .filter { !it.key.startsWith("rec:") }                       // not a sweep
            // same target, filed under the same category or none; a row counting in a category's
            // spent targets that spent for one month (SPEC R25, R53)
            .groupBy { if (it.key.startsWith("cat:") || it.categoryId != null) "${it.key}|${it.categoryId}|${it.date.ymKey()}" else it.key }
            .values
            .forEach { rows ->
                val sorted = rows.sortedWith(compareBy({ it.loggedAt }, { it.id }))   // next in logged order
                var i = 0
                while (i < sorted.size - 1) {
                    val a = sorted[i]; val b = sorted[i + 1]
                    val ok = a.amount != 0L && a.amount + b.amount == 0L && b.loggedAt - a.loggedAt <= PAIR_WINDOW
                    if (ok) {
                        pairs[a.id] = b.id; pairs[b.id] = a.id
                        i += 2                                                      // neither row can pair again
                    } else i += 1
                }
            }
        return pairs
    }

    /**
     * Deletes a pair (SPEC R53). The two rows net to zero on their target and on Savings, so
     * removing them is reversing them. Which rows pair is not found again here; the caller drops
     * the two ids from the map it holds.
     *
     * The last logged income date is recomputed only when a removed row was income, from the
     * income rows on or after `incomeEnabledDate`. The deposit marker is never lowered: re-opening
     * a processed payday would let the next catch-up deposit it again.
     */
    fun deletePair(d: ProfileData, id: String, otherId: String): ProfileData {
        val ids = setOf(id, otherId)
        val survivors = d.transactions.filter { it.id !in ids }
        val s = d.settings
        val floor = s.incomeEnabledDate
        val logged = if (!s.incomeEnabled || d.transactions.none { it.id in ids && it.isIncome }) s.lastIncomeLoggedDate
            else survivors.filter { it.isIncome && (floor == null || it.date >= floor) }.maxOfOrNull { it.date } ?: floor
        return d.copy(transactions = survivors, settings = s.copy(lastIncomeLoggedDate = logged))
    }
}

/** Savings for arithmetic: unset reads as 0 here only (SPEC R49). */
val ProfileData.savingsAmount: Money get() = savings.amount ?: 0

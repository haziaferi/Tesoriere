package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.ImportState
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.Profile
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.Savings
import com.hazia.tesoriere.model.Settings
import com.hazia.tesoriere.model.Shift
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.model.Transaction
import com.hazia.tesoriere.model.TrashItem
import com.hazia.tesoriere.pairing.Pairing
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.payments.PaymentNotice
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import com.hazia.tesoriere.receipts.ReceiptReview
import com.hazia.tesoriere.receipts.ReceiptSettings
import com.hazia.tesoriere.receipts.Receipts
import com.hazia.tesoriere.statements.BankRow
import com.hazia.tesoriere.statements.ReviewRow
import com.hazia.tesoriere.statements.RowStatus
import com.hazia.tesoriere.statements.StatementImport
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Trash.kt at 2892110.
 *
 * The Trash (SPEC R121-R126). A delete happens at once and settles its money then, through the
 * engine function that deletes the item; what it removed is kept 30 days with what a restore
 * needs, and restore puts it back. While in the Trash an item is out of ProfileData, so it sweeps
 * nothing, reminds of nothing and counts towards no figure (SPEC R121).
 *
 * Changed from Sci-Fin:
 * - The payload keeps the rows the delete wrote ([TrashPayload.written]): a Goal's return (SPEC
 *   R58), the return on deletion of an expense (SPEC R66), an Account's closing row (SPEC R87), a
 *   deleted source's row of a payment (SPEC R54). Restore undoes each with a row of the opposite
 *   amount on the same key, dated the same day and filed the same way (category, payoff and share
 *   marks), so every month's spent and every figure is as before the delete (SPEC R122). Sci-Fin
 *   kept two sums, a Goal's returned money and an expense's reversed sweep, and dated its restore
 *   rows today; Tesoriere's returns are filed under categories and count in a month's spent.
 * - Payments (SPEC R125): a payment deleted through Payments.remove waits whole, with its receipt
 *   lines and bank keys, and is put back through Payments.reinstate. Its receipt id stays among
 *   the recorded ids and its bank keys among the import's keys, as the removal leaves them (SPEC
 *   R41). The payload keeps what each payment from a deleted Goal or charged to a deleted Account
 *   took from it ([TrashPayload.charged]); a change since, which wrote a row in the item's place
 *   (SPEC R54), refuses the restore with a code ([RestoreProblem], owner answer of 2026-11-25).
 *   A payment whose other side of a pair was recorded while it was in the Trash, a receipt's or a
 *   bank row's, is noted ([RestoreNotice.PairAvailable]) and may come back as that payment's pair,
 *   through Pairing.pairReceiptWithPayment or Pairing.pairBankWithPayment, so the purchase counts
 *   once; a receipt side keeps its own parts (SPEC R127).
 * - A category's delete removes its category links and keeps them ([TrashPayload.categoryLinks]),
 *   and takes another category for the recurring expenses and Accounts that file under it (SPEC
 *   R42, R123); restore brings the links back, never the moved expenses.
 * - An Account quick-add undone (SPEC R111) goes to the Trash like a pair (SPEC R53): its rows
 *   come back as they were, each moving its target again.
 * - A restored recurring expense or Account whose category is gone takes a category given in its
 *   place, or the restore is refused (SPEC R122, R123).
 * - A Profile in the Trash holds its payments and receipt settings (SPEC R126); deleting the
 *   receipt Profile frees the folder, and restoring it takes the folder back only while no other
 *   Profile holds one.
 * - The day-20 notice and the day-30 expiry are worked out here ([Trash.tidy]); Sci-Fin's session
 *   did it and wrote the notice's wording, which is the design pass's here (SPEC R16).
 * - Sci-Fin's section references are replaced by SPEC rule numbers; stored text comes from StoredLabels.
 */

/** What one delete removed, and what a restore needs to undo exactly that (SPEC R121, R122). */
@Serializable
data class TrashPayload(
    val goals: List<Goal> = emptyList(),
    val categories: List<Category> = emptyList(),
    val expenses: List<RecurringExpense> = emptyList(),
    val accounts: List<Account> = emptyList(),
    /** Rows removed by the delete and brought back as they were: a cancelled pair (SPEC R53), an undone quick-add (SPEC R111). */
    val transactions: List<Transaction> = emptyList(),
    /** Payments removed by the delete, kept whole (SPEC R41, R125). */
    val payments: List<Payment> = emptyList(),
    /** Chatelaine category id to category id: the links a category's delete removed (SPEC R42). */
    val categoryLinks: Map<String, String> = emptyMap(),
    /** The rows the delete wrote; restore undoes each on its own day and filing (SPEC R122). */
    val written: List<Transaction> = emptyList(),
    /**
     * Payment id to what it took from the deleted Goal (its total less the share Savings paid) or
     * the deleted Account (its total), for each payment from or charged to it at the delete (SPEC R122).
     */
    val charged: Map<String, Money> = emptyMap(),
    /**
     * Payment id to how many other payments held its bank keys at the delete, by
     * StatementImport.sameRow, when any did: twins of one day with no FITID (SPEC R125). A restore
     * is `RECORDED_AGAIN` only when more hold them.
     */
    val keysHeld: Map<String, Int> = emptyMap(),
    /** A whole Profile (SPEC R126). */
    val profile: TrashedProfile? = null,
)

/** A Profile's whole world, in serialisable pieces (SPEC R126). */
@Serializable
data class TrashedProfile(
    val profile: Profile, val savings: Savings, val settings: Settings, val importState: ImportState,
    val goals: List<Goal>, val categories: List<Category>, val expenses: List<RecurringExpense>,
    val accounts: List<Account>, val transactions: List<Transaction>,
    val shifts: List<Shift> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val receiptSettings: ReceiptSettings? = null,
) {
    fun data() = ProfileData(profile, savings, settings, importState, goals, categories, expenses, accounts, transactions, shifts, payments, receiptSettings)

    companion object {
        fun of(d: ProfileData) = TrashedProfile(
            d.profile, d.savings, d.settings, d.importState, d.goals, d.categories, d.recurringExpenses, d.accounts,
            d.transactions, d.shifts, d.payments, d.receiptSettings,
        )
    }
}

/** Why a restore is refused, as a code (SPEC R122); how it reads is the design pass's (SPEC R16). */
enum class RestoreProblem {
    /** A payment from the deleted Goal, or charged to the deleted Account, changed since and wrote a row in its place (SPEC R54). */
    WRITTEN_SINCE,
    /** A restored recurring expense or Account files under a category that no longer exists, and none that exists is given (SPEC R123). */
    CATEGORY_MISSING,
    /** Another payment holds the restored payment's receipt id, or more hold its bank keys than at its delete (SPEC R107, R125). */
    RECORDED_AGAIN,
    /** The rows of an undone quick-add move a Goal or an Account deleted since (SPEC R111). */
    TARGET_DELETED,
    /** The rows of an undone quick-add take more from a Goal than it holds. */
    GOAL_SHORT,
    /** A payment restored as a pair that pairing refuses (SPEC R119, R127): see Pairing.receiptPairProblem and bankPairProblem. */
    CANNOT_PAIR,
}

/** What a restore notes before it is done (SPEC R125); codes, not wording (SPEC R16). */
sealed interface RestoreNotice {
    /** The restored payment's own warning: its source deleted since, or a Goal short of its share (SPEC R54). */
    data class Source(val notice: PaymentNotice) : RestoreNotice

    /**
     * The restored payment would pair with [paymentId], recorded since (SPEC R127): it may be
     * restored as that pair instead of on its own. [notice] is the warning the pair itself would
     * carry (SPEC R54, R109), the difference not yet filed.
     */
    data class PairAvailable(val paymentId: String, val notice: PaymentNotice? = null) : RestoreNotice
}

/** What the Trash's daily look finds (SPEC R121): items to go for good, and items due their one notice. Trash item ids. */
data class TrashTidy(val gone: List<String>, val warn: List<String>)

object Trash {
    const val KEPT_DAYS = 30
    /** The one notice that an item is about to go for good (SPEC R121). */
    const val WARN_DAYS = 20

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    fun encode(p: TrashPayload): String = json.encodeToString(TrashPayload.serializer(), p)
    fun decode(s: String): TrashPayload? = runCatching { json.decodeFromString(TrashPayload.serializer(), s) }.getOrNull()

    fun goesOn(deletedOn: LocalDate): LocalDate = deletedOn.plus(DatePeriod(days = KEPT_DAYS))
    fun expired(deletedOn: LocalDate, today: LocalDate) = deletedOn.daysUntil(today) >= KEPT_DAYS
    fun warnDue(deletedOn: LocalDate, today: LocalDate) = deletedOn.daysUntil(today) >= WARN_DAYS

    /**
     * The daily look at the Trash (SPEC R121): items 30 days old or more go; items 20 days old or
     * more, not yet warned and not going, are due their one notice. An item whose day cannot be
     * read is left alone. Marking an item warned and removing one are the caller's.
     */
    fun tidy(items: List<TrashItem>, today: LocalDate): TrashTidy {
        val gone = ArrayList<String>(); val warn = ArrayList<String>()
        for (t in items) {
            val on = runCatching { LocalDate.parse(t.deletedOn) }.getOrNull() ?: continue
            when {
                expired(on, today) -> gone += t.id
                !t.warned && warnDue(on, today) -> warn += t.id
            }
        }
        return TrashTidy(gone, warn)
    }

    /** The ids of Profile [profileId]'s categories in the Trash, for Spending's deleted slices (SPEC R124). */
    fun trashedCategories(items: List<TrashItem>, profileId: String): Set<String> =
        items.filter { it.profileId == profileId }.flatMapTo(HashSet()) { t -> decode(t.payload)?.categories.orEmpty().map { it.id } }

    // ---- the deletes, each returning the state after it and what it removed ------------------------

    /** A Goal (SPEC R58), with what each payment from it took from it. */
    fun deleteGoal(d: ProfileData, id: String, returnSaved: Boolean, clock: Clock, ids: () -> String): Pair<ProfileData, TrashPayload>? {
        val g = d.goals.firstOrNull { it.id == id } ?: return null
        val after = GoalEditing.delete(d, id, returnSaved, clock, ids)
        return after to TrashPayload(goals = listOf(g), written = written(d, after), charged = charged(d, listOf(id), emptyList()))
    }

    /**
     * A category (SPEC R42, R123), with the links to it; the recurring expenses and Accounts that
     * file under it move to [replacementId]. Null when CategoryEditing.deleteProblem refuses it.
     */
    fun deleteCategory(d: ProfileData, id: String, replacementId: String? = null): Pair<ProfileData, TrashPayload>? {
        val c = d.categories.firstOrNull { it.id == id } ?: return null
        if (CategoryEditing.deleteProblem(d, id, replacementId) != null) return null
        val links = d.receiptSettings?.categoryLinks?.filterValues { it == id }.orEmpty()
        return CategoryEditing.delete(d, id, replacementId) to TrashPayload(categories = listOf(c), categoryLinks = links)
    }

    /** A recurring expense, with the Account linked to it: they go, and come back, as one (SPEC R66, R87). */
    fun deleteExpense(d: ProfileData, id: String, reverse: Boolean, clock: Clock, ids: () -> String): Pair<ProfileData, TrashPayload>? {
        val e = d.recurringExpenses.firstOrNull { it.id == id } ?: return null
        val acct = e.linkedAccountId?.let { a -> d.accounts.firstOrNull { it.id == a } }
        val after = Recurring.delete(d, id, reverse, clock, ids)
        return after to TrashPayload(
            expenses = listOf(e), accounts = listOfNotNull(acct), written = written(d, after),
            charged = charged(d, emptyList(), listOfNotNull(acct?.id)),
        )
    }

    /** An Account, with its linked recurring expense (SPEC R87, R88). */
    fun deleteAccount(d: ProfileData, id: String, reverse: Boolean, clock: Clock, ids: () -> String): Pair<ProfileData, TrashPayload>? {
        val a = d.accounts.firstOrNull { it.id == id } ?: return null
        val linked = a.linkedRecurringExpenseId?.let { e -> d.recurringExpenses.firstOrNull { it.id == e } }
        val after = Accounts.deleteCascade(d, id, clock, reverse, ids)
        return after to TrashPayload(
            accounts = listOf(a), expenses = listOfNotNull(linked), written = written(d, after), charged = charged(d, emptyList(), listOf(id)),
        )
    }

    /** A cancelled pair (SPEC R53): which rows pair is the caller's, found once from the ledger before any delete. */
    fun deletePair(d: ProfileData, id: String, otherId: String): Pair<ProfileData, TrashPayload>? {
        val rows = d.transactions.filter { it.id == id || it.id == otherId }
        if (rows.size != 2) return null
        return Ledger.deletePair(d, id, otherId) to TrashPayload(transactions = rows)
    }

    /** An Account quick-add undone as a whole (SPEC R111); null when Accounts.undoQuickAddProblem refuses it. */
    fun undoQuickAdd(d: ProfileData, groupId: String): Pair<ProfileData, TrashPayload>? {
        if (Accounts.undoQuickAddProblem(d, groupId) != null) return null
        val rows = d.transactions.filter { it.groupId == groupId }
        return Accounts.undoQuickAdd(d, groupId) to TrashPayload(transactions = rows)
    }

    /**
     * A payment (SPEC R41, R125), removed through Payments.remove, which undoes its source's effect
     * and, for a source deleted before it, writes the row the payload keeps. Its receipt id stays
     * recorded and its bank keys stay among the import's keys.
     */
    fun deletePayment(d: ProfileData, id: String, clock: Clock, ids: () -> String): Pair<ProfileData, TrashPayload>? {
        val p = d.payments.firstOrNull { it.id == id } ?: return null
        val after = Payments.remove(d, id, clock, ids())
        val held = holding(after, p, except = null)
        return after to TrashPayload(payments = listOf(p), written = written(d, after), keysHeld = if (held > 0) mapOf(id to held) else emptyMap())
    }

    /** A whole Profile (SPEC R126); deleting the receipt Profile frees the receipt folder (SPEC R112). */
    fun deleteProfile(app: AppState, d: ProfileData): Pair<AppState, TrashPayload> =
        app.copy(receiptProfileId = app.receiptProfileId?.takeIf { it != d.profileId }) to TrashPayload(profile = TrashedProfile.of(d))

    // ---- Restore ------------------------------------------------------------------------------

    /**
     * A Profile back from the Trash, as one more Profile, not opened (SPEC R126): it holds the
     * receipt folder again while no other Profile holds one; otherwise it comes back with no folder,
     * keeping its recorded receipt ids, links and stores' sources.
     */
    fun restoreProfile(app: AppState, t: TrashedProfile): Pair<AppState, ProfileData> {
        val d = t.data()
        val s = d.receiptSettings
        if (s?.folder == null) return app to d
        return if (Receipts.offersFolder(app, d.profileId)) app.copy(receiptProfileId = d.profileId) to d
        else app to d.copy(receiptSettings = s.copy(folder = null))
    }

    /**
     * Why [p] cannot be restored into [d], or null when it can (SPEC R122): rows written since
     * against a deleted Goal or Account, a category gone that nothing given replaces, a payment
     * recorded again, an undone quick-add whose Goal or Account is gone or whose Goal is short, or,
     * restored as the pair of bank payment [pairWith], a pair that pairing refuses (SPEC R125).
     */
    fun restoreProblem(
        d: ProfileData, p: TrashPayload, replacementCategoryId: String? = null, pairWith: String? = null, differenceCategoryId: String? = null,
    ): RestoreProblem? {
        val goals = p.goals.filter { g -> d.goals.none { it.id == g.id } }.map { it.id }
        val accounts = p.accounts.filter { a -> d.accounts.none { it.id == a.id } }.map { it.id }
        // each payment from or charged to it as at the delete: any change since wrote a row in its place (SPEC R54)
        if ((goals.isNotEmpty() || accounts.isNotEmpty()) && charged(d, goals, accounts) != p.charged) return RestoreProblem.WRITTEN_SINCE

        val live = d.categories.mapTo(HashSet()) { it.id }.apply { p.categories.forEach { add(it.id) } }
        val needed = p.expenses.filter { e -> d.recurringExpenses.none { it.id == e.id } }.map { it.categoryId } +
            p.accounts.filter { it.id in accounts }.flatMap { listOfNotNull(it.payoffCategoryId, it.feesCategoryId, it.loanCategoryId) }
        if (needed.any { it !in live } && (replacementCategoryId == null || replacementCategoryId !in live)) return RestoreProblem.CATEGORY_MISSING

        for (pay in p.payments.filter { x -> d.payments.none { it.id == x.id } }) {
            // restored as a pair, the partner holds what it completes (SPEC R127); its bank keys are
            // held again only by more payments than held them at the delete, twins aside (SPEC R125)
            val receipt = pay.receiptId != null && d.payments.any { o -> o.id != pairWith && o.receiptId == pay.receiptId }
            if (receipt || holding(d, pay, except = pairWith) > (p.keysHeld[pay.id] ?: 0)) return RestoreProblem.RECORDED_AGAIN
        }
        if (pairWith != null) pairProblem(d, p, pairWith, differenceCategoryId, replacementCategoryId)?.let { return it }

        val rows = p.transactions.filter { t -> d.transactions.none { it.id == t.id } }
        val byGoal = HashMap<String, Money>(); val byAccount = HashMap<String, Money>()
        for (t in rows) when (val k = TransactionKey.parse(t.key)) {
            is TransactionKey.GoalKey -> byGoal[k.id] = (byGoal[k.id] ?: 0L) + t.amount
            is TransactionKey.GoalSpendKey -> byGoal[k.id] = (byGoal[k.id] ?: 0L) + t.amount
            is TransactionKey.AccountKey -> byAccount[k.id] = (byAccount[k.id] ?: 0L) + t.amount
            else -> Unit
        }
        for ((id, net) in byGoal) if (net != 0L) {
            val g = d.goals.firstOrNull { it.id == id } ?: return RestoreProblem.TARGET_DELETED
            if (g.saved + net < 0) return RestoreProblem.GOAL_SHORT
        }
        for ((id, net) in byAccount) if (net != 0L && d.accounts.none { it.id == id }) return RestoreProblem.TARGET_DELETED
        return null
    }

    /**
     * What a restore of [p] notes (SPEC R125, R127): a payment's own warning, when its source was
     * deleted since its removal or its Goal is short; and the payment recorded since that it would
     * pair with, best first in R118's order, with the warning the pair would carry.
     */
    fun restoreNotices(d: ProfileData, p: TrashPayload): List<RestoreNotice> =
        p.payments.filter { x -> d.payments.none { it.id == x.id } }.flatMap { pay ->
            listOfNotNull(
                Payments.reinstateNotice(d, pay, settled = settled(d, p, pay))?.let { RestoreNotice.Source(it) },
                pairFor(d, pay)?.let { RestoreNotice.PairAvailable(it, pairNotice(d, pay, it)) },
            )
        }

    /**
     * Puts [p]'s items back into [d] as they were and undoes the rows its delete wrote, each on its
     * own day and filing, so every figure and every month's spent is as before the delete (SPEC
     * R122). A recurring expense resumes from the month of the restore: it is marked swept for it,
     * and the months it spent in the Trash are not caught up. A restored expense or Account whose
     * category is gone files under [replacementCategoryId] (SPEC R123). An item whose id is in the
     * Profile again is left as it is. With [pairWith], the payment comes back as the pair of that
     * payment, with its difference filed under [differenceCategoryId], instead of on its own, and
     * its delete's rows stay (SPEC R127). Refused while [restoreProblem] finds one; a Profile is
     * restored by [restoreProfile].
     */
    fun restore(
        d: ProfileData, p: TrashPayload, clock: Clock, ids: () -> String, replacementCategoryId: String? = null,
        pairWith: String? = null, differenceCategoryId: String? = null,
    ): ProfileData {
        require(p.profile == null) { "a Profile is restored with restoreProfile (SPEC R126)" }
        val problem = restoreProblem(d, p, replacementCategoryId, pairWith, differenceCategoryId)
        require(problem == null) { "the item cannot be restored: $problem (SPEC R122)" }
        val today = clock.today()
        var n = d
        // the keys whose item comes back: the rows its delete wrote are undone with it
        val back = HashSet<String>()

        for (c in p.categories) if (n.categories.none { it.id == c.id }) n = n.copy(categories = n.categories + c.copy(profileId = n.profileId))
        n.receiptSettings?.let { s ->
            // a link comes back unless its Chatelaine category was linked since (SPEC R42)
            val links = p.categoryLinks.filter { (k, v) -> k !in s.categoryLinks && n.categories.any { it.id == v } }
            if (links.isNotEmpty()) n = n.copy(receiptSettings = s.copy(categoryLinks = s.categoryLinks + links))
        }
        val live = n.categories.mapTo(HashSet()) { it.id }
        fun filed(id: String?): String? = if (id == null || id in live) id else replacementCategoryId

        // a Goal and an Account as the delete left them; undoing its rows below brings their figures back
        for (g in p.goals) if (n.goals.none { it.id == g.id }) {
            n = n.copy(goals = n.goals + g.copy(profileId = n.profileId, saved = g.saved + change(p.written, TransactionKey.GoalKey(g.id))))
            back += TransactionKey.GoalKey(g.id).stored
        }
        for (a in p.accounts) if (n.accounts.none { it.id == a.id }) {
            n = n.copy(accounts = n.accounts + a.copy(
                profileId = n.profileId, balance = a.balance + change(p.written, TransactionKey.AccountKey(a.id)),
                payoffCategoryId = filed(a.payoffCategoryId), feesCategoryId = filed(a.feesCategoryId), loanCategoryId = filed(a.loanCategoryId),
            ))
            back += TransactionKey.AccountKey(a.id).stored
        }
        val expenses = p.expenses.filter { e -> n.recurringExpenses.none { it.id == e.id } }.map { e ->
            back += TransactionKey.RecurringKey(e.id).stored
            e.copy(profileId = n.profileId, categoryId = filed(e.categoryId)!!, lastSweptMonth = maxOf(e.lastSweptMonth ?: "", today.ymKey()))
        }
        if (expenses.isNotEmpty()) {
            // only the restored expenses' due dates move on to today (SPEC R61)
            n = n.copy(recurringExpenses = n.recurringExpenses + Recurring.advanceDueDates(n.copy(recurringExpenses = expenses), today).recurringExpenses)
        }

        val payments = p.payments.filter { x -> n.payments.none { it.id == x.id } }
        // a payment whose removal wrote a row for its source, deleted before it and still gone: that
        // row is undone with it (SPEC R125); a source back since is moved again instead, the row
        // staying, cancelled by the one its restore onto the deleted source wrote; restored as a pair
        // it stays removed, its partner completed instead (SPEC R127)
        val settled = payments.filter { settled(n, p, it) }.mapTo(HashSet()) { it.id }
        for (t in p.written) if ((settled.isNotEmpty() && pairWith == null) || t.key in back) n = undoRow(n, t, clock, ids())
        for (pay in payments) n = if (pairWith != null) restoreAsPair(n, p, pay, pairWith, differenceCategoryId, replacementCategoryId, clock, ids())
            else Payments.reinstate(n, pay, settled = pay.id in settled, clock, ids())

        val rows = p.transactions.filter { t -> n.transactions.none { it.id == t.id } }
        for (t in rows) n = reAdd(n, t)
        if (rows.isNotEmpty()) {
            val s = n.settings
            val income = rows.filter { it.isIncome }.maxOfOrNull { it.date }
            if (s.incomeEnabled && income != null && (s.lastIncomeLoggedDate == null || income > s.lastIncomeLoggedDate)) {
                n = n.copy(settings = s.copy(lastIncomeLoggedDate = income))
            }
        }
        return n
    }

    /**
     * What a delete moved into Savings, which its restore takes back out: the Savings movement of
     * the rows it wrote, of the payments it removed, and of the rows it removed (SPEC R121).
     */
    fun moneyMoved(p: TrashPayload): Money =
        p.written.sumOf { Ledger.savingsEffect(it) } +
            p.payments.sumOf { when (it.source) { PaymentSource.Savings -> it.total; is PaymentSource.Goal -> it.fromSavings; is PaymentSource.Account -> 0L } } -
            p.transactions.sumOf { Ledger.savingsEffect(it) }

    // ---- helpers ----------------------------------------------------------------------------------

    /** How many payments of [d] but [pay] and [except] hold [pay]'s bank keys, by StatementImport.sameRow (SPEC R26, R125). */
    private fun holding(d: ProfileData, pay: Payment, except: String?): Int {
        val k = pay.bankKeys ?: return 0
        return d.payments.count { o -> o.id != pay.id && o.id != except && o.bankKeys?.let { StatementImport.sameRow(it, k) } == true }
    }

    /** Which side of a pair a payment in the Trash brings (SPEC R127): a receipt's, or a bank row's holding no receipt. */
    private enum class Side { RECEIPT, BANK }

    private fun sideOf(pay: Payment): Side? = when {
        pay.total <= 0 -> null
        pay.receiptId != null -> Side.RECEIPT
        pay.bankKeys != null -> Side.BANK
        else -> null
    }

    /**
     * The payment recorded since that [pay] could be restored as the pair of (SPEC R127): for a
     * receipt holding no bank keys, the best bank row's payment Pairing.paymentCandidates finds;
     * for a receipt holding them, a payment with no receipt holding one of them; for a bank row's
     * payment, the best receipt payment Pairing.receiptCandidates finds. Null for none, or a refund.
     */
    private fun pairFor(d: ProfileData, pay: Payment): String? = when (sideOf(pay)) {
        Side.RECEIPT -> {
            val held = pay.bankKeys
            if (held != null) d.payments.firstOrNull { o -> o.receiptId == null && o.total > 0 && o.bankKeys?.let { StatementImport.sameRow(it, held) } == true }?.id
            else Receipts.reopen(d, pay)?.let { Pairing.paymentCandidates(d, it.file).firstOrNull()?.first }
        }
        Side.BANK -> Pairing.receiptCandidates(d, bankRow(pay)).firstOrNull()?.first
        null -> null
    }

    /** The warning the pair of [pay] with [partner] would carry, the difference not yet filed (SPEC R127). */
    private fun pairNotice(d: ProfileData, pay: Payment, partner: String): PaymentNotice? = when (sideOf(pay)) {
        Side.RECEIPT -> receiptSideOf(d, pay, null)?.let { (review, parts) -> Pairing.receiptPairNotice(unrecorded(d, pay), partner, review, differenceFor(d, pay, null), parts) }
        Side.BANK -> Pairing.bankPairNotice(unimported(d, pay), partner, reviewRow(pay), null)
        null -> null
    }

    /**
     * Why restoring [p]'s payment as the pair of [pairWith] is refused (SPEC R127): a receipt side
     * whose part or line names a category gone, with no category that exists given in its place,
     * is `CATEGORY_MISSING`; one payload payment that pairing refuses, its receipt id read as not
     * recorded and its bank keys as not imported, since its delete kept them so, is `CANNOT_PAIR`.
     */
    private fun pairProblem(d: ProfileData, p: TrashPayload, pairWith: String, differenceCategoryId: String?, replacement: String?): RestoreProblem? {
        val pay = p.payments.singleOrNull()?.takeIf { x -> d.payments.none { it.id == x.id } } ?: return RestoreProblem.CANNOT_PAIR
        return when (sideOf(pay)) {
            Side.RECEIPT -> {
                val live = d.categories.mapTo(HashSet()) { it.id }
                val named = pay.parts.filter { !it.difference }.mapNotNull { it.categoryId } + pay.receiptLines.map { it.categoryId }
                if (named.any { it !in live } && (replacement == null || replacement !in live)) return RestoreProblem.CATEGORY_MISSING
                val (review, parts) = receiptSideOf(d, pay, replacement) ?: return RestoreProblem.CANNOT_PAIR
                RestoreProblem.CANNOT_PAIR.takeIf { Pairing.receiptPairProblem(unrecorded(d, pay), pairWith, review, differenceFor(d, pay, differenceCategoryId), parts) != null }
            }
            Side.BANK -> RestoreProblem.CANNOT_PAIR.takeIf {
                Pairing.bankPairProblem(unimported(d, pay), pairWith, reviewRow(pay), differenceCategoryId, twinRows(p, pay)) != null
            }
            null -> RestoreProblem.CANNOT_PAIR
        }
    }

    /**
     * [pay] back as the pair of [pairWith] (SPEC R119, R127). A receipt side completes a bank row's
     * payment through Pairing.pairReceiptWithPayment, with its own parts less any difference part,
     * its lines in the categories they were filed under, a category gone taking [replacement], and
     * the links kept; a bank row's payment completes a receipt payment through
     * Pairing.pairBankWithPayment, with its keys and its amount. The recorded receipt ids and the
     * import's keys stay as they were, the payment's among them.
     */
    private fun restoreAsPair(
        d: ProfileData, p: TrashPayload, pay: Payment, pairWith: String, differenceCategoryId: String?, replacement: String?, clock: Clock, txId: String,
    ): ProfileData = when (sideOf(pay)) {
        Side.RECEIPT -> {
            val (review, parts) = requireNotNull(receiptSideOf(d, pay, replacement))
            val paired = Pairing.pairReceiptWithPayment(unrecorded(d, pay), pairWith, review, differenceFor(d, pay, differenceCategoryId), clock, txId, parts)
            paired.copy(receiptSettings = paired.receiptSettings?.copy(recordedReceiptIds = d.receiptSettings!!.recordedReceiptIds))
        }
        Side.BANK -> {
            val paired = Pairing.pairBankWithPayment(unimported(d, pay), pairWith, reviewRow(pay), differenceCategoryId, clock, txId, twinRows(p, pay))
            paired.copy(importState = paired.importState.copy(importedKeys = d.importState.importedKeys))
        }
        null -> error("not a side of a pair (SPEC R127)")
    }

    /**
     * Where a receipt side's difference is filed (SPEC R127): the category [given], else the one its
     * own difference part was filed under when it held one and that category still exists.
     */
    private fun differenceFor(d: ProfileData, pay: Payment, given: String?): String? =
        given ?: pay.parts.firstOrNull { it.difference }?.categoryId?.takeIf { c -> d.categories.any { it.id == c } }

    /**
     * Receipt payment [pay] read back as its receipt under review, its lines in the categories they
     * were filed under, with its own parts less any difference part (SPEC R127); a category gone
     * takes [replacement].
     */
    private fun receiptSideOf(d: ProfileData, pay: Payment, replacement: String?): Pair<ReceiptReview, List<Part>>? {
        val review = Receipts.reopen(d, pay) ?: return null
        val live = d.categories.mapTo(HashSet()) { it.id }
        fun filed(c: String?) = if (c == null || c in live) c else replacement
        val parts = pay.parts.filter { !it.difference }.map { it.copy(categoryId = filed(it.categoryId)) }
        val lines = review.lineCategories.map { filed(it) }
        return review.copy(lineCategories = lines, openedCategories = lines) to parts
    }

    /** Bank row's payment [pay] read back as its row: its day, amount and text, and its keys (SPEC R127). */
    private fun bankRow(pay: Payment) = BankRow(pay.date, -pay.total, pay.payee ?: "", pay.bankKeys?.fitid)

    private fun reviewRow(pay: Payment) = ReviewRow(bankRow(pay), requireNotNull(pay.bankKeys), pay.payee ?: "", RowStatus.READY, false, null, null, null)

    /**
     * Bank payment [pay]'s row with the rows its twins stood for at its delete ([TrashPayload.keysHeld]),
     * as one review: each other payment holding its keys stands for one of them (SPEC R119, R127).
     */
    private fun twinRows(p: TrashPayload, pay: Payment): List<ReviewRow> = List(1 + (p.keysHeld[pay.id] ?: 0)) { reviewRow(pay) }

    /** [d] with [pay]'s receipt id read as not recorded, so pairing takes its receipt again. */
    private fun unrecorded(d: ProfileData, pay: Payment): ProfileData {
        val id = pay.receiptId ?: return d
        return d.receiptSettings?.let { s -> d.copy(receiptSettings = s.copy(recordedReceiptIds = s.recordedReceiptIds - id)) } ?: d
    }

    /** [d] with [pay]'s bank keys read as not imported, so pairing takes its row again. */
    private fun unimported(d: ProfileData, pay: Payment): ProfileData {
        val keys = pay.bankKeys?.let(StatementImport::keyStrings)?.toSet() ?: return d
        return d.copy(importState = d.importState.copy(importedKeys = d.importState.importedKeys.filter { it !in keys }))
    }

    /**
     * Whether payment [pay] of [p] was removed after its Goal or Account was deleted, its removal
     * writing the row [p] keeps, and that source is still out of [d] (SPEC R125): then undoing the
     * row restores it. A source back in the Profile since is moved again as recorded instead.
     */
    private fun settled(d: ProfileData, p: TrashPayload, pay: Payment): Boolean = p.written.isNotEmpty() && when (val s = pay.source) {
        is PaymentSource.Goal -> d.goals.none { it.id == s.id }
        is PaymentSource.Account -> d.accounts.none { it.id == s.id }
        PaymentSource.Savings -> false
    }

    /** The rows [after] holds that [before] did not: what a delete wrote. */
    private fun written(before: ProfileData, after: ProfileData): List<Transaction> {
        val had = before.transactions.mapTo(HashSet()) { it.id }
        return after.transactions.filter { it.id !in had }
    }

    /** Payment id to what it takes from each Goal of [goals] (its total less the share Savings paid) or Account of [accounts] (its total). */
    private fun charged(d: ProfileData, goals: List<String>, accounts: List<String>): Map<String, Money> =
        d.payments.mapNotNull { pay ->
            when (val s = pay.source) {
                is PaymentSource.Goal -> if (s.id in goals) pay.id to (pay.total - pay.fromSavings) else null
                is PaymentSource.Account -> if (s.id in accounts) pay.id to pay.total else null
                PaymentSource.Savings -> null
            }
        }.toMap()

    /** The sum of [rows]' changes to the target [key]. */
    private fun change(rows: List<Transaction>, key: TransactionKey): Money = rows.filter { it.key == key.stored }.sumOf { it.amount }

    /**
     * Undoes row [t] a delete wrote (SPEC R122): its target moves back, and a row of the opposite
     * amount is logged on the same key, dated the same day, filed under the same category with the
     * same payoff and share marks.
     */
    private fun undoRow(d: ProfileData, t: Transaction, clock: Clock, id: String): ProfileData {
        val key = TransactionKey.parse(t.key)
        val moved = moveTarget(d, key, -t.amount)
        val m = Movement(
            -t.amount, key, t.targetLabel, StoredLabels.RESTORED, date = t.date,
            categoryId = t.categoryId, accountPayoff = t.accountPayoff, share = t.share,
        )
        return Ledger.logAndMove(moved, m, clock, id).first
    }

    /** Row [t] back in the ledger as it was, at its place in logged order, moving its target and Savings again. */
    private fun reAdd(d: ProfileData, t: Transaction): ProfileData {
        val key = TransactionKey.parse(t.key)
        var n = moveTarget(d, key, t.amount)
        val effect = Ledger.savingsEffect(t)
        if (effect != 0L || key == TransactionKey.SavingsKey) n = n.copy(savings = n.savings.copy(amount = (n.savings.amount ?: 0) + effect))
        val at = n.transactions.indexOfFirst { it.loggedAt > t.loggedAt }.let { if (it < 0) n.transactions.size else it }
        val row = t.copy(profileId = n.profileId)
        return n.copy(transactions = n.transactions.subList(0, at) + row + n.transactions.subList(at, n.transactions.size))
    }

    /** A Goal's saved money or an Account's balance moved by [amount], for a row on [key]; any other key moves no target here. */
    private fun moveTarget(d: ProfileData, key: TransactionKey, amount: Money): ProfileData = when (key) {
        is TransactionKey.GoalKey -> d.copy(goals = d.goals.map { if (it.id == key.id) it.copy(saved = it.saved + amount) else it })
        is TransactionKey.GoalSpendKey -> d.copy(goals = d.goals.map { if (it.id == key.id) it.copy(saved = it.saved + amount) else it })
        is TransactionKey.AccountKey -> d.copy(accounts = d.accounts.map { if (it.id == key.id) it.copy(balance = it.balance + amount) else it })
        else -> d
    }
}

package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/AccountEditing.kt at 2892110.
 *
 * The Account editor over the engine (SPEC R86, R87): a new Account's balance other than zero logs
 * an opening row; an edit's balance change is a logged correction; a Loan follows its payment only
 * with a rate to work from; a linked expense is linked both ways, one link at a time.
 *
 * Changed from Sci-Fin: stored notes come from StoredLabels; `fresh` sets no icon name (how an
 * Account looks is the design pass's, SPEC R16); section references are SPEC rule numbers.
 * canSave() is Tesoriere's: a name, and a payoff category that exists while a card's charges
 * are not logged (SPEC R109); save() refuses what it refuses, as it refused a blank name.
 */
object AccountEditing {

    /**
     * Whether [draft] can be saved: it has a name (SPEC R86); its switch for charges not logged
     * is on only on a card with a payoff category that exists (SPEC R109); a fees category it
     * names exists; and a rise of a stored card's balance, its interest or fees, has a category
     * that exists, [riseCategoryId] if picked, else the fees category, unless its charges are not
     * logged (SPEC R110).
     */
    fun canSave(d: ProfileData, draft: Account, riseCategoryId: String? = null): Boolean {
        if (draft.name.isBlank()) return false
        if (draft.chargesNotLogged) {
            if (draft.type != AccountType.CREDIT) return false
            if (draft.payoffCategoryId == null || d.categories.none { it.id == draft.payoffCategoryId }) return false
        }
        if (draft.feesCategoryId != null && d.categories.none { it.id == draft.feesCategoryId }) return false
        val existing = d.accounts.firstOrNull { it.id == draft.id }
        if (existing != null && draft.balance > existing.balance && !Accounts.canRise(d, draft, riseCategoryId)) return false
        return true
    }

    /**
     * Saves [draft], or returns [d] unchanged when it cannot be saved ([canSave]). A rise of a
     * card's balance is logged as its interest or fees, filed under [riseCategoryId] if picked,
     * else the card's fees category (SPEC R110); an opening balance and a fall are filed under none.
     */
    fun save(d: ProfileData, draft: Account, clock: Clock, riseCategoryId: String? = null, ids: () -> String): ProfileData {
        if (!canSave(d, draft, riseCategoryId)) return d
        val name = draft.name.trim()
        val existing = d.accounts.firstOrNull { it.id == draft.id }
        val cleaned = draft.copy(
            name = name,
            autoUpdateFromPayments = draft.autoUpdateFromPayments && Accounts.loanInputsComplete(draft),
            originationDate = draft.originationDate ?: if (draft.type == AccountType.LOAN) clock.today() else null,
        )
        var next = if (existing == null) {
            var n = d.copy(accounts = d.accounts + cleaned.copy(balance = 0))
            if (cleaned.balance != 0L) n = Accounts.balanceEdit(n, cleaned.id, cleaned.balance, StoredLabels.OPENING_BALANCE, clock, ids())
            n
        } else {
            var n = d
            if (cleaned.balance != existing.balance) {
                val rise = if (cleaned.balance > existing.balance) Accounts.riseCategory(d, cleaned, riseCategoryId) else null
                n = Accounts.balanceEdit(n, existing.id, cleaned.balance, StoredLabels.CORRECTION, clock, ids(), categoryId = rise)
            }
            n.copy(accounts = n.accounts.map { if (it.id == existing.id) cleaned else it })
        }
        // both ways, and one way at a time: an expense this Account no longer links loses its
        // back-link, or deleting it later would take the Account with it (SPEC R87)
        val eid = cleaned.linkedRecurringExpenseId
        next = next.copy(recurringExpenses = next.recurringExpenses.map {
            when {
                it.id == eid -> it.copy(linkedAccountId = cleaned.id)
                it.linkedAccountId == cleaned.id -> it.copy(linkedAccountId = null)
                else -> it
            }
        }, accounts = next.accounts.map { if (eid != null && it.id != cleaned.id && it.linkedRecurringExpenseId == eid) it.copy(linkedRecurringExpenseId = null) else it })
        return next
    }

    /** A fresh draft for the editor: a card by default (SPEC R86). */
    fun fresh(id: String, profileId: String) = Account(id, profileId, "", type = AccountType.CREDIT)
}

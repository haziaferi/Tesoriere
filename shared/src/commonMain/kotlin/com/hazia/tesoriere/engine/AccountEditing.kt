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
 * problem() and canSave() are Tesoriere's: a name, a payoff category that exists while a card's
 * charges are not logged (SPEC R109), and, for a rise of a card's balance, the user's choice of
 * interest or fees or a correction (SPEC R110); save() refuses what they refuse, as it refused a
 * blank name.
 */
object AccountEditing {

    /** What a direct edit upward of a card's balance is, as the user chooses for the edit (SPEC R110). */
    enum class BalanceRise {
        /** Interest or fees: filed under a category and counted as spending. */
        FEES,
        /** A correction of the figure: filed under none, counted nowhere. */
        CORRECTION,
    }

    /** Why a draft cannot be saved, as a code; how it reads is the design pass's (SPEC R16). */
    enum class AccountProblem {
        /** The name is blank (SPEC R86). */
        NAME_BLANK,
        /** The switch for charges not logged is on, but not on a card, or with no payoff category that exists (SPEC R109). */
        PAYOFF_CATEGORY_MISSING,
        /** The fees category names no category that exists (SPEC R110). */
        FEES_CATEGORY_UNKNOWN,
        /** A Loan's own category names no category that exists (SPEC R86, R89); having none is allowed. */
        LOAN_CATEGORY_UNKNOWN,
        /** A card's balance rises and the user has not chosen interest or fees or a correction (SPEC R110). */
        RISE_CHOICE_MISSING,
        /** A card's balance rises as interest or fees with no category that exists (SPEC R110). */
        RISE_CATEGORY_MISSING,
    }

    /**
     * Why [draft] cannot be saved, or null when it can: it has a name (SPEC R86); its switch for
     * charges not logged is on only on a card with a payoff category that exists (SPEC R109); a
     * fees category it names exists; and a rise of a stored card's balance has the user's [rise]
     * choice, interest or fees needing a category that exists, [riseCategoryId] if picked, else
     * the fees category, unless the card's charges are not logged (SPEC R110).
     */
    fun problem(d: ProfileData, draft: Account, rise: BalanceRise? = null, riseCategoryId: String? = null): AccountProblem? {
        if (draft.name.isBlank()) return AccountProblem.NAME_BLANK
        if (draft.chargesNotLogged &&
            (draft.type != AccountType.CREDIT || draft.payoffCategoryId == null || d.categories.none { it.id == draft.payoffCategoryId })
        ) return AccountProblem.PAYOFF_CATEGORY_MISSING
        if (draft.feesCategoryId != null && d.categories.none { it.id == draft.feesCategoryId }) return AccountProblem.FEES_CATEGORY_UNKNOWN
        if (draft.loanCategoryId != null && d.categories.none { it.id == draft.loanCategoryId }) return AccountProblem.LOAN_CATEGORY_UNKNOWN
        if (cardRise(d, draft)) {
            if (rise == null) return AccountProblem.RISE_CHOICE_MISSING
            if (rise == BalanceRise.FEES && !Accounts.canRise(d, draft, riseCategoryId)) return AccountProblem.RISE_CATEGORY_MISSING
        }
        return null
    }

    /** Whether [draft] can be saved: [problem] finds nothing. */
    fun canSave(d: ProfileData, draft: Account, rise: BalanceRise? = null, riseCategoryId: String? = null): Boolean =
        problem(d, draft, rise, riseCategoryId) == null

    /** Whether saving [draft] raises a stored card's balance, which takes the user's choice (SPEC R110). */
    private fun cardRise(d: ProfileData, draft: Account): Boolean {
        val existing = d.accounts.firstOrNull { it.id == draft.id } ?: return false
        return draft.type == AccountType.CREDIT && draft.balance > existing.balance
    }

    /**
     * Saves [draft], or returns [d] unchanged when it cannot be saved ([problem]). A rise of a
     * card's balance chosen as interest or fees is filed under [riseCategoryId] if picked, else
     * the card's fees category; chosen as a correction, under none (SPEC R110); an opening
     * balance and a fall are filed under none.
     */
    fun save(d: ProfileData, draft: Account, clock: Clock, rise: BalanceRise? = null, riseCategoryId: String? = null, ids: () -> String): ProfileData {
        if (!canSave(d, draft, rise, riseCategoryId)) return d
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
                val filed = if (rise == BalanceRise.FEES && cleaned.balance > existing.balance) Accounts.riseCategory(d, cleaned, riseCategoryId) else null
                n = Accounts.balanceEdit(n, existing.id, cleaned.balance, StoredLabels.CORRECTION, clock, ids(), categoryId = filed)
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

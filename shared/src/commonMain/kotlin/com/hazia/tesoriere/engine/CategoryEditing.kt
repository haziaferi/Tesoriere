package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/CategoryEditing.kt at
 * 2892110. Changed from Sci-Fin: the draft has no spent figure, since spent is worked out per
 * month and changed by an adjustment (SPEC R25, R59). Deleting a category also removes the
 * category links to it (SPEC R42), and when a recurring expense or an Account files later rows
 * under it, it takes another existing category and moves them to it (SPEC R123, owner answer of
 * 2026-11-25); in Sci-Fin no expense had a category. Past parts and rows keep its id (SPEC R124).
 * The Trash wraps the delete (engine/Trash.kt).
 */
object CategoryEditing {

    /** The category editor: name, icon and planned amount (SPEC R59). */
    data class Draft(val id: String? = null, val name: String = "", val icon: String? = null, val planned: Money = 0) {
        companion object { fun of(c: Category) = Draft(c.id, c.name, c.icon, c.planned) }
    }

    /** Saves a new category or an edit; neither logs a row nor moves money. A blank name saves nothing. */
    fun save(d: ProfileData, draft: Draft, @Suppress("UNUSED_PARAMETER") clock: Clock, ids: () -> String): ProfileData {
        val name = draft.name.trim(); if (name.isEmpty()) return d
        return if (draft.id == null) {
            d.copy(categories = d.categories + Category(ids(), d.profileId, name, draft.icon, planned = draft.planned))
        } else {
            if (d.categories.none { it.id == draft.id }) return d
            d.copy(categories = d.categories.map { if (it.id == draft.id) it.copy(name = name, icon = draft.icon, planned = draft.planned) else it })
        }
    }

    /** Why a category cannot be deleted as asked, as a code (SPEC R123); how it reads is the design pass's (SPEC R16). */
    enum class DeleteProblem {
        NO_SUCH_CATEGORY,
        /** The category given to take its place does not exist, or is the one deleted. */
        REPLACEMENT_UNKNOWN,
        /** A recurring expense or an Account files later rows under it, and no category is given in its place. */
        REPLACEMENT_NEEDED,
    }

    /**
     * Whether a recurring expense or an Account files later rows under category [id] (SPEC R123):
     * an expense's category (SPEC R67), a card's payoff or fees category (SPEC R109, R110), or a
     * Loan's own category (SPEC R89).
     */
    fun inUse(d: ProfileData, id: String): Boolean =
        d.recurringExpenses.any { it.categoryId == id } ||
            d.accounts.any { it.payoffCategoryId == id || it.feesCategoryId == id || it.loanCategoryId == id }

    /** Why deleting [id], with [replacementId] in its place, is refused, or null when it is not (SPEC R123). */
    fun deleteProblem(d: ProfileData, id: String, replacementId: String?): DeleteProblem? = when {
        d.categories.none { it.id == id } -> DeleteProblem.NO_SUCH_CATEGORY
        replacementId != null && (replacementId == id || d.categories.none { it.id == replacementId }) -> DeleteProblem.REPLACEMENT_UNKNOWN
        replacementId == null && inUse(d, id) -> DeleteProblem.REPLACEMENT_NEEDED
        else -> null
    }

    /**
     * Deletes category [id] (SPEC R42, R123): the category and the category links to it go; every
     * recurring expense and Account field that files later rows under it moves to [replacementId]
     * in the same step, their past rows staying where they were filed (SPEC R67). The parts of
     * payments and the ledger rows filed under it keep its id (SPEC R124). Refused unless
     * [deleteProblem] finds nothing.
     */
    fun delete(d: ProfileData, id: String, replacementId: String? = null): ProfileData {
        val problem = deleteProblem(d, id, replacementId)
        require(problem == null) { "the category cannot be deleted: $problem (SPEC R123)" }
        fun moved(c: String?) = if (c == id) replacementId else c
        return d.copy(
            categories = d.categories.filter { it.id != id },
            recurringExpenses = d.recurringExpenses.map { if (it.categoryId == id) it.copy(categoryId = replacementId!!) else it },
            accounts = d.accounts.map {
                it.copy(payoffCategoryId = moved(it.payoffCategoryId), feesCategoryId = moved(it.feesCategoryId), loanCategoryId = moved(it.loanCategoryId))
            },
            receiptSettings = d.receiptSettings?.let { s -> s.copy(categoryLinks = s.categoryLinks.filterValues { it != id }) },
        )
    }
}

package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels
import kotlinx.datetime.LocalDate

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Writes.kt at 2892110,
 * without categoryQuickAdd, categorySpentEdit and goalSpend: spending is a payment (SPEC R21,
 * R54) and a change of spent is an adjustment (SPEC R25).
 *
 * Each write is a thin wrapper over [Ledger.logAndMove]. A direct edit logs the signed change,
 * never the new figure (SPEC R52).
 */
object Writes {

    /** Money into a Goal; Savings moves by the opposite (SPEC R50). A Goal that does not exist gives [d] back unchanged. */
    fun goalQuickAdd(d: ProfileData, goalId: String, amount: Money, payee: String?, clock: Clock, txId: String, date: LocalDate? = null): ProfileData {
        val g = d.goals.firstOrNull { it.id == goalId } ?: return d
        val next = d.copy(goals = d.goals.map { if (it.id == goalId) it.copy(saved = it.saved + amount) else it })
        val note = payee ?: StoredLabels.fill(StoredLabels.TOWARD, g.name)
        return Ledger.logAndMove(next, Movement(amount, TransactionKey.GoalKey(goalId), g.name, note, date = date), clock, txId).first
    }

    /** Money into Savings, tagged as income when [isIncome] (SPEC R51). */
    fun savingsQuickAdd(d: ProfileData, signed: Money, payee: String?, isIncome: Boolean, clock: Clock, txId: String, date: LocalDate? = null): ProfileData =
        Ledger.logAndMove(d, Movement(signed, TransactionKey.SavingsKey, StoredLabels.SAVINGS, payee, isIncome, date), clock, txId).first

    /**
     * Savings set directly (SPEC R52): logs the change, never tags income. An edit to the same
     * figure logs nothing, but still ends the unset state by writing the figure (SPEC R49).
     */
    fun savingsDirectEdit(d: ProfileData, newValue: Money, payee: String?, clock: Clock, txId: String): ProfileData {
        val delta = newValue - d.savingsAmount
        if (delta == 0L) return d.copy(savings = d.savings.copy(amount = newValue))
        return Ledger.logAndMove(d, Movement(delta, TransactionKey.SavingsKey, StoredLabels.SAVINGS, payee ?: StoredLabels.CORRECTION), clock, txId).first
    }

    /** Why a Goal's saved money cannot be set directly, as a code, first match in this order (SPEC R52). */
    enum class GoalSavedProblem {
        /** The Goal does not exist: deleted since, say. */
        GOAL_UNKNOWN,
        /** A Goal cannot hold less than nothing. */
        BELOW_ZERO,
    }

    /** Why [goalSavedEdit] would refuse to set Goal [goalId] to [newValue], or null when it would (SPEC R52). */
    fun goalSavedEditProblem(d: ProfileData, goalId: String, newValue: Money): GoalSavedProblem? = when {
        d.goals.none { it.id == goalId } -> GoalSavedProblem.GOAL_UNKNOWN
        newValue < 0 -> GoalSavedProblem.BELOW_ZERO
        else -> null
    }

    /**
     * A Goal's saved money set directly: any change, either way, moves Savings by its opposite (SPEC
     * R52). While [goalSavedEditProblem] finds one, [d] comes back unchanged.
     */
    fun goalSavedEdit(d: ProfileData, goalId: String, newValue: Money, payee: String?, clock: Clock, txId: String): ProfileData {
        if (goalSavedEditProblem(d, goalId, newValue) != null) return d
        val g = d.goals.firstOrNull { it.id == goalId } ?: return d
        val delta = newValue - g.saved
        if (delta == 0L) return d
        val next = d.copy(goals = d.goals.map { if (it.id == goalId) it.copy(saved = newValue) else it })
        return Ledger.logAndMove(next, Movement(delta, TransactionKey.GoalKey(goalId), g.name, payee ?: StoredLabels.CORRECTION), clock, txId).first
    }
}

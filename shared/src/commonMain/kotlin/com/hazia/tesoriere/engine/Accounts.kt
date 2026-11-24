package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels
import kotlinx.datetime.LocalDate

/*
 * Ported in part from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Accounts.kt at
 * 2892110: only the two writes a recurring expense needs, a Loan's balance following an accepted
 * difference (SPEC R65) and the closing row of an Account deleted with its expense (SPEC R66).
 * The rest of Accounts (quick-add, the loan and credit calculators, re-typing, the cascade from
 * the Account's side) is ported with the Accounts piece.
 *
 * Changed from Sci-Fin: the stored notes come from StoredLabels; `close` is internal to the
 * engine module as in Sci-Fin.
 */
object Accounts {

    /** A direct balance edit: logs the change as an `acct:` row; Savings is untouched (SPEC R51). */
    fun balanceEdit(d: ProfileData, accountId: String, newBalance: Money, payee: String?, clock: Clock, txId: String, date: LocalDate? = null): ProfileData {
        val a = d.accounts.first { it.id == accountId }
        val delta = newBalance - a.balance
        val next = d.copy(accounts = d.accounts.map { if (it.id == accountId) it.copy(balance = newBalance) else it })
        if (delta == 0L) return next
        return Ledger.logAccountEdit(next, a.id, a.name, delta, payee ?: StoredLabels.CORRECTION, clock, txId, date)
    }

    /** An Account leaves with a closing row for the balance it still showed (SPEC R66). */
    internal fun close(d: ProfileData, a: Account, clock: Clock, ids: () -> String): ProfileData {
        val next = if (a.balance != 0L) Ledger.logAccountEdit(d, a.id, a.name, -a.balance, StoredLabels.ACCOUNT_CLOSED, clock, ids()) else d
        return next.copy(accounts = next.accounts.filter { it.id != a.id })
    }
}

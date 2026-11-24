package com.hazia.tesoriere.model

/**
 * Text the engine itself writes into the data: a ledger row's note or target name. It is stored
 * in English, as fixed tokens, and shown in the active language (SPEC R07, owner answer 4 of
 * 2026-11-24); how each reads on screen is the design pass's (SPEC R16). The same English as
 * Sci-Fin's native/shared/src/commonMain/kotlin/app/scifin/i18n/Stored.kt at 2892110, so rows read
 * from a Sci-Fin backup match these tokens.
 */
object StoredLabels {
    /** The note of a direct edit the user wrote no note for (SPEC R52). */
    const val CORRECTION = "Correction"
    /** The note of the first money put into a Goal when it is created. */
    const val STARTING_BALANCE = "Starting balance"
    /** The payee of an income row the app deposited by itself. */
    const val AUTO_DEPOSIT = "Auto-deposit"
    /** The payee of a confirmed hourly payday's income row (SPEC R81). */
    const val PAYDAY = "Payday"
    /** The target name of a `savings` ledger row. */
    const val SAVINGS = "Savings"
    /** The note of the row that returns a deleted Goal's money to Savings (SPEC R58). */
    const val GOAL_DELETED_RETURNED = "Goal deleted — returned to Savings"
    /** The note of a recurring expense's sweep row (SPEC R63). */
    const val RECURRING_SWEEP = "Recurring sweep"
    /** The note of the row that returns a deleted recurring expense's sweeps (SPEC R66). */
    const val DELETION_REVERSAL = "Deletion reversal"
    /** The note of an accepted difference after editing a recurring expense (SPEC R65). */
    const val EDIT_RECONCILIATION = "Edit reconciliation"
    /** The note of the closing row of an Account that is deleted. */
    const val ACCOUNT_CLOSED = "Account closed"
    /** The note of the row that clears a Loan whose payment reached the end of its term (SPEC R72). */
    const val LOAN_FINISHED = "Loan finished"

    /** Templates with a name inside: `{0}` is the name, kept as the user typed it. */
    const val TOWARD = "Toward {0}"
    const val SPENT_FROM = "Spent from {0}"
    const val PAYMENT_TO = "Payment to {0}"
    const val NAMED_PAYMENT = "{0} payment"

    val TEMPLATES: List<String> = listOf(TOWARD, SPENT_FROM, PAYMENT_TO, NAMED_PAYMENT)

    /** [template] with its `{0}` replaced by [name]. */
    fun fill(template: String, name: String): String = template.replace("{0}", name)
}

package com.hazia.tesoriere.model

import com.hazia.tesoriere.payments.Payment

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/model/ProfileData.kt at 2892110.
 */

/**
 * One Profile's whole world, in memory: the shape the engine works on. Every engine function is
 * `(ProfileData, inputs) -> ProfileData` (or a figure read from it), so the engine is testable
 * with no database at all, and switching Profile swaps one object.
 */
data class ProfileData(
    val profile: Profile,
    val savings: Savings,
    val settings: Settings,
    val importState: ImportState,
    val goals: List<Goal> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recurringExpenses: List<RecurringExpense> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    /** Hours logged since the last payday that paid them. */
    val shifts: List<Shift> = emptyList(),
    /** Every expense (SPEC R21). */
    val payments: List<Payment> = emptyList(),
) {
    val profileId: String get() = profile.id

    companion object {
        /**
         * A new Profile with the app's defaults: Savings unset (SPEC R49) and every piece that
         * starts off switched off (SPEC R18). `onboardingComplete` is the caller's to say.
         */
        fun fresh(id: String, name: String, onboardingComplete: Boolean = true): ProfileData = ProfileData(
            profile = Profile(id, name),
            savings = Savings(id, null),
            settings = Settings(profileId = id, onboardingComplete = onboardingComplete),
            importState = ImportState(profileId = id),
        )
    }
}

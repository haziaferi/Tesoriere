package com.hazia.tesoriere.model

import com.hazia.tesoriere.payments.Payment
import kotlinx.datetime.LocalDate

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
         * starts off switched off (SPEC R18). `onboardingComplete` is the caller's to say. Created
         * on [createdOn], its month is the last month processed (SPEC R70); without it, the first
         * open sets the mark.
         */
        fun fresh(id: String, name: String, onboardingComplete: Boolean = true, createdOn: LocalDate? = null): ProfileData = ProfileData(
            profile = Profile(id, name),
            savings = Savings(id, null),
            settings = Settings(
                profileId = id, onboardingComplete = onboardingComplete,
                // `YYYY-MM-DD` cut to `YYYY-MM`, the month key the cycle compares
                lastMonthProcessed = createdOn?.toString()?.substring(0, 7),
            ),
            importState = ImportState(profileId = id),
        )
    }
}

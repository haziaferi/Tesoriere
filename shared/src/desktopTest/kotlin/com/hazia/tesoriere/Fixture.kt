package com.hazia.tesoriere

import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.Savings

/**
 * Small synthetic Profiles built in code: every name and figure is made up. Figures are written
 * as the spec writes them and read with [cents].
 */
object Fixture {
    const val PROFILE = "p-1"

    /** A Profile with [savings] (null: unset, SPEC R49) and the goals and categories given. */
    fun profile(
        savings: String? = "1,000.00",
        goals: List<Goal> = emptyList(),
        categories: List<Category> = emptyList(),
    ): ProfileData = ProfileData.fresh(PROFILE, "Main").copy(
        savings = Savings(PROFILE, savings?.let(::cents)),
        goals = goals.map { it.copy(profileId = PROFILE) },
        categories = categories.map { it.copy(profileId = PROFILE) },
    )

    fun goal(id: String, name: String = id, saved: String = "0.00", target: String? = null) =
        Goal(id, PROFILE, name, saved = cents(saved), target = target?.let(::cents))

    fun category(id: String, name: String = id, planned: String = "100.00") =
        Category(id, PROFILE, name, planned = cents(planned))
}

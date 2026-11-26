package com.hazia.tesoriere.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/model/Enums.kt at 2892110.
 * Each enum carries the exact token Sci-Fin stores, so a Sci-Fin backup reads word for word
 * (SPEC R19). Sci-Fin's appearance enums (Theme, ThemeMode, Density) are not ported: how
 * Tesoriere looks is the design pass's (SPEC R16). Each `of` reads a stored token, null for one
 * that is not the enum's: storage may be corrupted.
 */

@Serializable
enum class Cadence(val token: String) {
    @SerialName("weekly") WEEKLY("weekly"), @SerialName("biweekly") BIWEEKLY("biweekly"), @SerialName("semimonthly") SEMIMONTHLY("semimonthly"),
    @SerialName("monthly") MONTHLY("monthly"), @SerialName("bimonthly") BIMONTHLY("bimonthly"), @SerialName("yearly") YEARLY("yearly");

    /** Date mode applies only to the month-anchored cadences. */
    val monthAnchored: Boolean get() = this == MONTHLY || this == BIMONTHLY || this == YEARLY

    companion object { fun of(token: String) = entries.firstOrNull { it.token == token } }
}

@Serializable
enum class IncomeCadence(val token: String) {
    @SerialName("weekly") WEEKLY("weekly"), @SerialName("biweekly") BIWEEKLY("biweekly"), @SerialName("semimonthly") SEMIMONTHLY("semimonthly"), @SerialName("monthly") MONTHLY("monthly");
    companion object { fun of(token: String) = entries.firstOrNull { it.token == token } }
}

/** A known paycheck, or pay that exists only once hours are worked. */
@Serializable
enum class IncomeType(val token: String) {
    @SerialName("salaried") SALARIED("salaried"), @SerialName("hourly") HOURLY("hourly");
    companion object { fun of(token: String) = entries.firstOrNull { it.token == token } }
}

@Serializable
enum class DateMode(val token: String) {
    @SerialName("interval") INTERVAL("interval"), @SerialName("fixedDate") FIXED_DATE("fixedDate");
    companion object { fun of(token: String) = entries.firstOrNull { it.token == token } }
}

/** How a Goal with a target is planned (SPEC R55). */
@Serializable
enum class GoalMode(val token: String) {
    @SerialName("date") DATE("date"), @SerialName("amount") AMOUNT("amount");
    companion object { fun of(token: String) = entries.firstOrNull { it.token == token } }
}

@Serializable
enum class AccountType(val token: String) {
    @SerialName("credit") CREDIT("credit"), @SerialName("loan") LOAN("loan");
    companion object { fun of(token: String) = entries.firstOrNull { it.token == token } }
}

@Serializable
enum class RateType(val token: String) {
    @SerialName("fixed") FIXED("fixed"), @SerialName("variable") VARIABLE("variable"), @SerialName("interestOnly") INTEREST_ONLY("interestOnly");
    companion object { fun of(token: String) = entries.firstOrNull { it.token == token } }
}

/** The only lead times a due-date reminder may carry. */
val REMINDER_LEAD_DAYS: List<Int> = listOf(1, 3, 7, 14)

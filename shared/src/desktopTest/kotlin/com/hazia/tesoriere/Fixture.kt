package com.hazia.tesoriere

import com.hazia.tesoriere.engine.Recurring
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.DateMode
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.Savings
import com.hazia.tesoriere.receipts.ReceiptFile
import com.hazia.tesoriere.receipts.ReceiptFileLine
import com.hazia.tesoriere.receipts.ReceiptSettings
import kotlinx.datetime.LocalDate

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

    /**
     * A recurring expense as stored, with the derived fields filled the way creation fills them:
     * fixed-date mode by default on monthly, bimonthly and yearly; the anchor day from [firstDue];
     * the interval from the cadence in interval mode. Its category defaults to `c1`.
     */
    fun recurring(
        id: String, amount: String, cadence: Cadence, firstDue: LocalDate, dueDate: LocalDate = firstDue,
        dateMode: DateMode? = if (cadence.monthAnchored) DateMode.FIXED_DATE else null,
        dueDate2: LocalDate? = null, endDate: LocalDate? = null, intervalDays: Int? = null, share: String? = null,
        linkedAccountId: String? = null, lastSweptMonth: String? = null, finishedAt: LocalDate? = null,
        categoryId: String = "c1", name: String = id, reminderEnabled: Boolean = false, reminderLeadDays: Int = 3,
        createdOn: LocalDate? = null,
    ) = RecurringExpense(
        id = id, profileId = PROFILE, name = name, amount = cents(amount), cadence = cadence, categoryId = categoryId,
        firstDueDate = firstDue, dueDate = dueDate, dueDate2 = dueDate2,
        dueDateAnchorDay = if (dateMode == DateMode.FIXED_DATE) firstDue.day else null, dateMode = dateMode,
        intervalDays = intervalDays ?: if (dateMode == DateMode.INTERVAL) Recurring.defaultIntervalDays(cadence) else null,
        apportionedShare = share?.let(::cents), endDate = endDate, linkedAccountId = linkedAccountId,
        lastSweptMonth = lastSweptMonth, finishedAt = finishedAt,
        reminderEnabled = reminderEnabled, reminderLeadDays = reminderLeadDays, createdOn = createdOn,
    )

    /** The receipt settings of the receipt Profile (SPEC R112), with a made-up folder handle. */
    fun receiptSettings(folder: String? = "folder-handle-1") = ReceiptSettings(PROFILE, folder)

    /** The app's state, which keeps [PROFILE] as the receipt Profile (SPEC R112). */
    val receiptApp = AppState(activeProfileId = PROFILE, receiptProfileId = PROFILE)

    /** One receipt line as Chatelaine's spec, rule 123, has it: [price] is the whole line's price paid. */
    fun line(name: String, price: String, chatelaineId: String, chatelaineName: String, pack: String? = null, quantity: Int = 1) =
        ReceiptFileLine(name, pack, quantity, cents(price), chatelaineId, chatelaineName)

    /** A receipt whose total paid is the sum of its [lines], as rule 123 says it is. */
    fun receipt(id: String, store: String, date: LocalDate, vararg lines: ReceiptFileLine, currency: String = "EUR", format: Int = 1) =
        ReceiptFile(format, id, store, date, currency, lines.sumOf { it.pricePaid }, lines.toList())
}

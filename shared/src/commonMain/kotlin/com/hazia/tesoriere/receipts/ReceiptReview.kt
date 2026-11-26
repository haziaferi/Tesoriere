package com.hazia.tesoriere.receipts

import com.hazia.tesoriere.engine.Clock
import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.pairing.Pairing
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentNotice
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import com.hazia.tesoriere.payments.ReceiptLine

/*
 * Tesoriere's own: Sci-Fin at 2892110 had no receipts. A receipt's review, from opening it to
 * recording it as one payment (SPEC R31-R35, R112-R115). A review is a value: nothing in the
 * Profile changes until [Receipts.confirm], so closing a review is simply not confirming it
 * (SPEC R31, R33). The payment is recorded through Payments.record, so every source behaves as
 * it does for any payment (SPEC R54, R109).
 */

/**
 * A receipt under review. [lineCategories] holds, per line of [file], the Tesoriere category it is
 * filed under, null while it has none (SPEC R113); [newLinks] are the links the user made in this
 * review, Chatelaine category id to Tesoriere category id; [addedCategories] are the categories the
 * user added in it (SPEC R114). Links made by name are not held here: Confirm makes them again
 * from the Profile (SPEC R115). [openedSource] is the source the review opened with and [moved] the
 * indices of the lines the user moved by hand, so the review knows its changes (SPEC R33, R114);
 * [openedCategories] the category each line opened in. [sourcePicked] is whether [source] was
 * picked, by the user or as a recorded payment's own: a pair with a bank row recorded first keeps
 * the bank payment's source while it is not and the store has none kept (SPEC R35, R119).
 */
data class ReceiptReview(
    val file: ReceiptFile,
    val source: PaymentSource,
    val lineCategories: List<String?>,
    val newLinks: Map<String, String>,
    val addedCategories: List<Category>,
    val openedSource: PaymentSource = source,
    val moved: Set<Int> = emptySet(),
    val openedCategories: List<String?> = lineCategories,
    val sourcePicked: Boolean = false,
)

/** What a review notes for one Chatelaine category (SPEC R114). Codes, not wording (SPEC R16). */
sealed interface ReviewNote {
    /** Linked before, to a Tesoriere category of another name. */
    data class NamesDiffer(val chatelaineId: String) : ReviewNote

    /** Linked by the user in this review to an existing category of another name: rename it in Chatelaine. */
    data class RenameInChatelaine(val chatelaineId: String, val tesoriereCategoryId: String) : ReviewNote

    /** No link, no equal name, no answer yet: Confirm stays off while a line of it is in no category (SPEC R32). */
    data class Unlinked(val chatelaineId: String) : ReviewNote

    /**
     * The receipt meets R37 with [paymentId], recorded from a bank row with no receipt: a screen can
     * offer the pair before Confirm, which stays on (SPEC R114, R118). Not a Chatelaine category's note.
     */
    data class PairAvailable(val paymentId: String) : ReviewNote
}

/** Why a review's Confirm is off, as a code, first match in this order (SPEC R115). */
enum class ReceiptProblem {
    /** This Profile does not hold the receipt folder (SPEC R112). */
    NOT_RECEIPT_PROFILE,
    /** The receipt id was recorded before (SPEC R30). */
    ALREADY_RECORDED,
    /** The receipt's currency is not the Profile's (SPEC R30, R47). */
    OTHER_CURRENCY,
    /** A line of a Chatelaine category with no link is in no category (SPEC R32, R114). */
    UNLINKED,
    /** A line has no category, or one that does not exist. */
    CATEGORY_MISSING,
    /** The payment could not be saved: parts that break SPEC R23, or a source that does not exist (SPEC R54). */
    CANNOT_SAVE,
}

object Receipts {

    /** Whether Profile [profileId] may be offered a receipt folder: no other Profile holds one (SPEC R28, R112). */
    fun offersFolder(app: AppState, profileId: String): Boolean =
        app.receiptProfileId == null || app.receiptProfileId == profileId

    /** Makes [d]'s Profile the receipt Profile with [folder] (SPEC R112). While another Profile holds one, nothing changes. */
    fun chooseFolder(app: AppState, d: ProfileData, folder: String): Pair<AppState, ProfileData> {
        if (!offersFolder(app, d.profileId)) return app to d
        val s = d.receiptSettings ?: ReceiptSettings(d.profileId)
        return app.copy(receiptProfileId = d.profileId) to d.copy(receiptSettings = s.copy(folder = folder))
    }

    /** Opens [file] for review: each line in the category its link gives, the store's last source (SPEC R113). */
    fun open(d: ProfileData, file: ReceiptFile): ReceiptReview = ReceiptReview(
        file = file,
        source = sourceFor(d, file.store),
        lineCategories = file.lines.map { keptLink(d, it.categoryId) ?: byName(d, it.categoryName) },
        newLinks = emptyMap(),
        addedCategories = emptyList(),
    )

    /**
     * Files line [index] under [categoryId] by hand, for this receipt only; no link changes, and a
     * later answer for its Chatelaine category leaves it where it is (SPEC R31, R114). Moving it to
     * the category it is in already changes nothing; moving it back to the one it would hold unmoved
     * makes it unmoved again. An index that is no line's changes nothing.
     */
    fun moveLine(r: ReceiptReview, index: Int, categoryId: String): ReceiptReview {
        if (index !in r.file.lines.indices || index !in r.lineCategories.indices) return r
        val cats = r.lineCategories.toMutableList().also { it[index] = categoryId }
        // back to where it would be unmoved, the answer given since or else where it opened: no longer moved (SPEC R114)
        val home = r.newLinks[r.file.lines[index].categoryId] ?: r.openedCategories[index]
        return when {
            categoryId == home -> r.copy(lineCategories = cats, moved = r.moved - index)
            r.lineCategories[index] == categoryId -> r
            else -> r.copy(lineCategories = cats, moved = r.moved + index)
        }
    }

    /** Links Chatelaine category [chatelaineId] to the existing category [categoryId] (SPEC R32, R114). */
    fun link(r: ReceiptReview, chatelaineId: String, categoryId: String): ReceiptReview = answer(r, chatelaineId, categoryId, null)

    /** Adds Chatelaine category [chatelaineId] as a new category [newCategoryId], with its Chatelaine name and 0.00 planned (SPEC R32, R114). */
    fun addAsNew(r: ReceiptReview, chatelaineId: String, newCategoryId: String, profileId: String): ReceiptReview {
        // no line of that Chatelaine category: nothing to answer
        val name = r.file.lines.firstOrNull { it.categoryId == chatelaineId }?.categoryName?.trim() ?: return r
        return answer(r, chatelaineId, newCategoryId, Category(newCategoryId, profileId, name, planned = 0))
    }

    /** The user picks the receipt's source (SPEC R35), the one it opened with included. */
    fun setSource(r: ReceiptReview, source: PaymentSource): ReceiptReview = r.copy(source = source, sourcePicked = true)

    /** The source kept for [store] (SPEC R35, R113), or null when none is kept or its Goal or Account does not exist. */
    fun keptSource(d: ProfileData, store: String): PaymentSource? {
        val stored = d.receiptSettings?.storeSources?.get(store) ?: return null
        return when (val s = PaymentSource.parse(stored)) {
            is PaymentSource.Goal -> s.takeIf { d.goals.any { it.id == s.id } }
            is PaymentSource.Account -> s.takeIf { d.accounts.any { it.id == s.id } }
            PaymentSource.Savings -> s
            null -> null
        }
    }

    /**
     * What waits for the user, one note per Chatelaine category in the order the lines first name
     * them (SPEC R114): unlinked, names differ, or rename in Chatelaine; then a pair available, when the receipt meets R37 with a payment recorded from a bank row (SPEC R118).
     */
    fun notes(d: ProfileData, r: ReceiptReview): List<ReviewNote> =
        categoryNotes(d, r) + listOfNotNull(Pairing.paymentCandidates(d, r.file).firstOrNull()?.let { ReviewNote.PairAvailable(it.first) })

    private fun categoryNotes(d: ProfileData, r: ReceiptReview): List<ReviewNote> = chatelaineCategories(r).mapNotNull { (id, name) ->
        val answered = r.newLinks[id]
        val kept = keptLink(d, id)
        when {
            answered != null -> d.categories.firstOrNull { it.id == answered }
                ?.takeIf { !sameName(it.name, name) }?.let { ReviewNote.RenameInChatelaine(id, answered) }
            kept != null -> ReviewNote.NamesDiffer(id).takeIf { !sameName(d.categories.first { it.id == kept }.name, name) }
            byName(d, name) != null -> null
            else -> ReviewNote.Unlinked(id)
        }
    }

    /**
     * The payment's parts: one per Tesoriere category whose lines add up to other than zero, in the
     * order the lines first name them (SPEC R115). A line with no category gives a part with none,
     * which no payment can be saved with.
     */
    fun parts(r: ReceiptReview): List<Part> {
        val sums = LinkedHashMap<String?, Money>()
        r.file.lines.forEachIndexed { i, l -> val c = r.lineCategories[i]; sums[c] = (sums[c] ?: 0L) + l.pricePaid }
        return sums.filter { it.value != 0L }.map { (c, amount) -> Part(c, amount) }
    }

    /** Why Confirm is off, or null when it is on (SPEC R115). */
    fun problem(d: ProfileData, r: ReceiptReview): ReceiptProblem? {
        val s = d.receiptSettings
        if (s?.folder == null) return ReceiptProblem.NOT_RECEIPT_PROFILE
        if (r.file.receiptId in s.recordedReceiptIds) return ReceiptProblem.ALREADY_RECORDED
        if (!r.file.currency.equals(d.settings.currency, ignoreCase = true)) return ReceiptProblem.OTHER_CURRENCY
        // an unlinked category blocks only while a line of it is in no category (SPEC R32, owner answer B of 2026-11-25)
        val unlinked = notes(d, r).filterIsInstance<ReviewNote.Unlinked>().mapTo(HashSet()) { it.chatelaineId }
        if (r.file.lines.indices.any { r.file.lines[it].categoryId in unlinked && r.lineCategories[it] == null }) return ReceiptProblem.UNLINKED
        val base = withAdded(d, r)
        if (r.lineCategories.any { c -> c == null || base.categories.none { it.id == c } }) return ReceiptProblem.CATEGORY_MISSING
        if (!Payments.canSave(base, draft(r))) return ReceiptProblem.CANNOT_SAVE
        return null
    }

    fun canConfirm(d: ProfileData, r: ReceiptReview): Boolean = problem(d, r) == null

    /** The warning the payment would carry, as for any payment: a Goal short of the total, a card whose charges are not logged (SPEC R54, R109). */
    fun notice(d: ProfileData, r: ReceiptReview): PaymentNotice? = Payments.draftNotice(withAdded(d, r), draft(r))

    /**
     * Whether closing the review loses something the user did: a line moved, the source changed, a
     * link made or a category added (SPEC R33, R114, owner answer A of 2026-11-25).
     */
    fun hasChanges(r: ReceiptReview): Boolean =
        r.moved.isNotEmpty() || r.source != r.openedSource || r.newLinks.isNotEmpty() || r.addedCategories.isNotEmpty()

    /**
     * Records the review as payment [id] through Payments.record (SPEC R115), with the receipt's id
     * and every line, and keeps the receipt id, the links, by name or by the user, the added
     * categories and the store's source. While [problem] finds one, [d] comes back unchanged.
     */
    fun confirm(d: ProfileData, r: ReceiptReview, clock: Clock, id: String): ProfileData {
        if (problem(d, r) != null) return d
        val written = Payments.record(withAdded(d, r), draft(r), clock, id)
        return keep(d, written, r, id)
    }

    /**
     * What a recorded receipt leaves beside its payment (SPEC R115, R119): payment [paymentId] of
     * [written] gets the receipt's id and every line; the receipt id, the links, by name (worked
     * out against [before], the Profile the review was opened on) or by the user, and the store's
     * source are kept in the receipt settings. Shared by Confirm and by pairing a receipt with a
     * bank row's payment.
     */
    internal fun keep(before: ProfileData, written: ProfileData, r: ReceiptReview, paymentId: String): ProfileData {
        val lines = r.file.lines.mapIndexed { i, l ->
            ReceiptLine(l.name, l.pack, l.quantity, l.pricePaid, l.categoryId, l.categoryName, r.lineCategories[i]!!)
        }
        val byName = chatelaineCategories(r).mapNotNull { (cid, name) ->
            if (cid in r.newLinks || keptLink(before, cid) != null) null else byName(before, name)?.let { cid to it }
        }.toMap()
        val s = written.receiptSettings!!
        return written.copy(
            payments = written.payments.map { if (it.id == paymentId) it.copy(receiptId = r.file.receiptId, receiptLines = lines) else it },
            receiptSettings = s.copy(
                recordedReceiptIds = s.recordedReceiptIds + r.file.receiptId,
                categoryLinks = s.categoryLinks + byName + r.newLinks,
                storeSources = s.storeSources + (r.file.store to r.source.stored),
            ),
        )
    }

    /**
     * Recorded receipt payment [p] read back as its receipt under review, so it can be paired on a
     * restore from the Trash (SPEC R125): the file from its receipt id, store, day and lines, in the
     * Profile's currency, its total the sum of the lines (Chatelaine's spec, rule 123); each line in
     * the category it was filed under; the payment's source, as picked (SPEC R35). Null for a payment with no receipt.
     */
    fun reopen(d: ProfileData, p: Payment): ReceiptReview? {
        val id = p.receiptId ?: return null
        if (p.receiptLines.isEmpty()) return null
        val lines = p.receiptLines.map { ReceiptFileLine(it.name, it.pack, it.quantity, it.pricePaid, it.chatelaineCategoryId, it.chatelaineCategoryName) }
        val file = ReceiptFile(ReceiptPickup.HIGHEST_FORMAT, id, p.payee ?: "", p.date, d.settings.currency, lines.sumOf { it.pricePaid }, lines)
        return ReceiptReview(file, p.source, p.receiptLines.map { it.categoryId }, emptyMap(), emptyList(), sourcePicked = true)
    }

    /**
     * Changes a link for the receipts recorded after it; recorded lines stay where they were filed
     * (SPEC R34). Outside the receipt Profile, or to a category that does not exist, nothing changes.
     */
    fun changeLink(d: ProfileData, chatelaineId: String, categoryId: String): ProfileData {
        val s = d.receiptSettings ?: return d
        if (d.categories.none { it.id == categoryId }) return d
        return d.copy(receiptSettings = s.copy(categoryLinks = s.categoryLinks + (chatelaineId to categoryId)))
    }

    /**
     * An answer for [chatelaineId] (SPEC R114): every line of it the user has not moved by hand goes
     * to [target], whatever category it opened in or an earlier answer gave; a category an earlier
     * answer added is dropped once no line and no link holds it. With no line of [chatelaineId],
     * there is nothing to answer and [r] comes back unchanged.
     */
    private fun answer(r: ReceiptReview, chatelaineId: String, target: String, added: Category?): ReceiptReview {
        if (r.file.lines.none { it.categoryId == chatelaineId }) return r
        val cats = r.file.lines.mapIndexed { i, l ->
            if (l.categoryId == chatelaineId && i !in r.moved) target else r.lineCategories[i]
        }
        val links = r.newLinks + (chatelaineId to target)
        val addedCats = (r.addedCategories.filter { it.id != added?.id } + listOfNotNull(added))
            .filter { a -> a.id in cats || a.id in links.values }
        return r.copy(lineCategories = cats, newLinks = links, addedCategories = addedCats)
    }

    private fun draft(r: ReceiptReview) = PaymentDraft(r.file.date, r.file.totalPaid, r.source, r.file.store, parts(r))

    /** [d] with the categories the review added, as Confirm writes them (SPEC R114, R115). */
    internal fun withAdded(d: ProfileData, r: ReceiptReview): ProfileData =
        d.copy(categories = d.categories + r.addedCategories.filter { a -> d.categories.none { it.id == a.id } })

    /** Each Chatelaine category id on the receipt with its name on the first line that names it. */
    private fun chatelaineCategories(r: ReceiptReview): List<Pair<String, String>> =
        r.file.lines.distinctBy { it.categoryId }.map { it.categoryId to it.categoryName }

    /** A link kept from an earlier receipt, while its category exists (SPEC R42, R113). */
    private fun keptLink(d: ProfileData, chatelaineId: String): String? =
        d.receiptSettings?.categoryLinks?.get(chatelaineId)?.takeIf { id -> d.categories.any { it.id == id } }

    private fun byName(d: ProfileData, name: String): String? = d.categories.firstOrNull { sameName(it.name, name) }?.id

    private fun sameName(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    /** The store's last source, or Savings when there is none or its Goal or Account does not exist (SPEC R35, R113). */
    private fun sourceFor(d: ProfileData, store: String): PaymentSource = keptSource(d, store) ?: PaymentSource.Savings
}

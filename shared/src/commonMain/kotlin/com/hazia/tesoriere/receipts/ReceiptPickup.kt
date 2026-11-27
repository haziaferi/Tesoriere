package com.hazia.tesoriere.receipts

import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.ProfileData

/*
 * Tesoriere's own: Sci-Fin at 2892110 had no receipts. A scan of the receipt folder (SPEC R29,
 * R30, R116): it sorts each file into ready to review or refused with a code, and writes nothing,
 * to the folder or to the Profile. When to scan, on open or by a daily check (SPEC R29), is the
 * platform's; the count of waiting receipts is the engine's.
 */

/** What the parser, ReceiptFileFormat.parse, makes of one file's bytes (SPEC R30, R117). */
sealed interface ParseResult {
    data class Ok(val file: ReceiptFile) : ParseResult

    /** Its format is above the highest Tesoriere reads. */
    data class NewerFormat(val version: Int) : ParseResult

    /** Not a receipt file, or one that cannot be read. */
    data object Unreadable : ParseResult
}

/** Why a file cannot be taken, as a code (SPEC R30); how it reads is the design pass's (SPEC R16). */
sealed interface Refusal {
    /** Needs a newer Tesoriere: the file's format [version]. */
    data class NewerFormat(val version: Int) : Refusal

    /** The receipt's currency [file] is not the Profile's, [profile] (SPEC R47). */
    data class OtherCurrency(val file: String, val profile: String) : Refusal

    data object Unreadable : Refusal
}

/** A scan's outcome: receipts to review, oldest name first, and each refused file's name with its reason. */
data class Pickup(val ready: List<ReceiptFile>, val refused: List<Pair<String, Refusal>>)

object ReceiptPickup {
    /** The highest receipt file format Tesoriere reads (Chatelaine's spec, rule 123). */
    const val HIGHEST_FORMAT = 1

    /** The ending of a receipt file's name; a file under any other name is passed over (SPEC R116). */
    private const val ENDING = ".json"

    /**
     * Reads the receipt folder for Profile [d] (SPEC R116): only names ending in `.json`, in name
     * order, each sorted by the first check that fits. A recorded receipt id, or one met in an
     * earlier file of this scan, is left out. Nothing is written; a Profile other than the receipt
     * Profile [app] keeps (SPEC R112) scans nothing, whatever its own receipt settings hold.
     */
    fun scan(app: AppState, d: ProfileData, folder: ReceiptFolder, parse: (ByteArray) -> ParseResult): Pickup {
        if (app.receiptProfileId != d.profileId) return Pickup(emptyList(), emptyList())
        // a payment's receipt id counts as recorded too (SPEC R112, R141)
        val recorded = (d.receiptSettings?.recordedReceiptIds.orEmpty() + d.payments.mapNotNull { it.receiptId }).toHashSet()
        val seen = HashSet<String>()
        val ready = mutableListOf<ReceiptFile>()
        val refused = mutableListOf<Pair<String, Refusal>>()
        for (name in folder.names().filter { it.endsWith(ENDING) }.sorted()) {
            // one file that fails to read in any way is refused, and the scan goes on (SPEC R116)
            val result = try {
                folder.read(name)?.let(parse) ?: ParseResult.Unreadable
            } catch (_: Throwable) {
                ParseResult.Unreadable
            }
            val file = when (result) {
                ParseResult.Unreadable -> { refused += name to Refusal.Unreadable; continue }
                is ParseResult.NewerFormat -> { refused += name to Refusal.NewerFormat(result.version); continue }
                is ParseResult.Ok -> result.file
            }
            if (file.formatVersion > HIGHEST_FORMAT) { refused += name to Refusal.NewerFormat(file.formatVersion); continue }
            if (file.receiptId in recorded || !seen.add(file.receiptId)) continue
            if (!file.currency.equals(d.settings.currency, ignoreCase = true)) {
                refused += name to Refusal.OtherCurrency(file.currency, d.settings.currency); continue
            }
            ready += file
        }
        return Pickup(ready, refused)
    }

    /** How many receipts wait for review: the ones a scan finds ready (SPEC R29, R116). */
    fun waiting(app: AppState, d: ProfileData, folder: ReceiptFolder, parse: (ByteArray) -> ParseResult): Int = scan(app, d, folder, parse).ready.size
}

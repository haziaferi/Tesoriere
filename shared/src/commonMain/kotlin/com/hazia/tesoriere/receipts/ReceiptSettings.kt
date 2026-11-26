package com.hazia.tesoriere.receipts

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Tesoriere's own: Sci-Fin at 2892110 had no receipts. The receipt Profile's memory of the
 * receipt folder (SPEC R112), kept beside ImportState the same way.
 */

/** How new receipt files are found (SPEC R29); the first is the default. */
@Serializable
enum class PickupMode(val token: String) {
    /** Counted each time the app opens. */
    @SerialName("onOpen") ON_OPEN("onOpen"),

    /** As on open, plus a daily background check; the check itself is the platform's. */
    @SerialName("dailyCheck") DAILY_CHECK("dailyCheck"),
}

/**
 * The receipt settings, held only by the receipt Profile (SPEC R112): the folder the user chose,
 * as the platform's handle on it; how new files are found (SPEC R29); every receipt id recorded,
 * so a file is not offered again (SPEC R30, R41); Chatelaine category id to Tesoriere category id
 * (SPEC R32, R34); store name, as the receipt file writes it, to its last confirmed source as
 * stored by `PaymentSource.stored` (SPEC R35); store name to a bank row's payee text (SPEC R36).
 */
@Serializable
@Entity(tableName = "receipt_settings")
data class ReceiptSettings(
    @PrimaryKey val profileId: String,
    val folder: String? = null,
    val pickup: PickupMode = PickupMode.ON_OPEN,
    val recordedReceiptIds: List<String> = emptyList(),
    val categoryLinks: Map<String, String> = emptyMap(),
    val storeSources: Map<String, String> = emptyMap(),
    val storeLinks: Map<String, String> = emptyMap(),
)

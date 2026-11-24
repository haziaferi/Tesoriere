@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/model/InstantMillisSerializer.kt
 * at 2892110.
 */

/** The moment an entry is logged is an instant, stored and serialised as epoch milliseconds (SPEC R20). */
object InstantMillisSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("com.hazia.tesoriere.Instant", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilliseconds())
    override fun deserialize(decoder: Decoder): Instant = Instant.fromEpochMilliseconds(decoder.decodeLong())
}

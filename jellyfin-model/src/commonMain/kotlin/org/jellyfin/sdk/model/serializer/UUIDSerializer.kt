package org.jellyfin.sdk.model.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jellyfin.sdk.model.UUID

/**
 * Convert string to UUID. Accepts simple and hyphenated notations.
 * @throws IllegalArgumentException if string is not a valid UUID.
 */
public fun String.toUUID(): UUID = UUID.parse(this)

/**
 * Convert string to UUID or null if the string is not an UUID.
 * Accepts simple and hyphenated notations.
 */
public fun String.toUUIDOrNull(): UUID? = UUID.parseOrNull(this)

/**
 * A UUID serializer that supports the GUIDs without dashes from the Jellyfin API.
 */
public class UUIDSerializer : KSerializer<UUID> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("org.jellyfin.UUID", PrimitiveKind.STRING)

	override fun deserialize(decoder: Decoder): UUID = decoder.decodeString().toUUID()

	override fun serialize(encoder: Encoder, value: UUID) {
		encoder.encodeString(value.toString())
	}
}

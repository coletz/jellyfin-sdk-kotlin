package org.jellyfin.sdk.model.serializer

import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.format
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jellyfin.sdk.model.DateTime
import kotlin.time.Instant

/**
 * Format this date time as an ISO 8601 string with the UTC offset of [timeZone], for example
 * `2021-06-30T01:33:07+01:00` or `2021-06-30T01:33:07Z`. This is the format expected by the Jellyfin API.
 */
public fun DateTime.toIsoOffsetString(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
	val instant = toInstant(timeZone)
	val offset = timeZone.offsetAt(instant)
	return DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET.format {
		setDateTime(this@toIsoOffsetString)
		setOffset(offset)
	}
}

/**
 * Parse an ISO 8601 date time string. Strings with a UTC offset or time zone are converted to [timeZone]. Strings
 * without one (like `0001-01-01T00:00:00` sometimes returned by the server) are read as-is.
 */
public fun String.toDateTime(timeZone: TimeZone = TimeZone.currentSystemDefault()): DateTime =
	when (val instant = Instant.parseOrNull(this)) {
		null -> DateTime.parse(this)
		else -> instant.toLocalDateTime(timeZone)
	}

/**
 * Serializer to read zoned date times as local date time and writing it back. Uses the system time zone.
 */
public class DateTimeSerializer() : KSerializer<DateTime> {
	// Note: the primary constructor must stay parameterless for the serialization plugin (@UseSerializers)
	private var timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }

	internal constructor(timeZone: () -> TimeZone) : this() {
		this.timeZone = timeZone
	}

	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("org.jellyfin.LocalDateTime", PrimitiveKind.STRING)

	override fun deserialize(decoder: Decoder): DateTime = decoder.decodeString().toDateTime(timeZone())

	override fun serialize(encoder: Encoder, value: DateTime): Unit =
		encoder.encodeString(value.toIsoOffsetString(timeZone()))
}

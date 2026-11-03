package org.jellyfin.sdk.model.serializer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.serialization.json.Json

class DateTimeSerializerTests : FunSpec({
	val utc = DateTimeSerializer { TimeZone.UTC }
	val plusOne = DateTimeSerializer { UtcOffset(hours = 1).asTimeZone() }

	test("Encodes dates and times (UTC)") {
		Json.encodeToString(
			utc,
			LocalDateTime(2021, 6, 30, 1, 33, 7)
		) shouldBe """"2021-06-30T01:33:07Z""""
	}

	test("Encodes dates and times (Offset)") {
		Json.encodeToString(
			plusOne,
			LocalDateTime(2021, 6, 30, 1, 33, 7)
		) shouldBe """"2021-06-30T01:33:07+01:00""""
	}

	test("Parses minimum value") {
		Json.decodeFromString(utc, """"0001-01-01T00:00:00"""") shouldBe LocalDateTime(1, 1, 1, 0, 0, 0)
		Json.decodeFromString(plusOne, """"0001-01-01T00:00:00"""") shouldBe LocalDateTime(1, 1, 1, 0, 0, 0)
	}

	test("Parses dates and times (UTC)") {
		Json.decodeFromString(
			utc,
			""""2021-06-30T01:33:07Z""""
		) shouldBe LocalDateTime(2021, 6, 30, 1, 33, 7)

		Json.decodeFromString(
			utc,
			""""2021-06-30T01:33:07.420Z""""
		) shouldBe LocalDateTime(2021, 6, 30, 1, 33, 7, 420000000)

		// Server uses 7 fractional digits
		Json.decodeFromString(
			utc,
			""""2021-06-30T01:33:07.4200000Z""""
		) shouldBe LocalDateTime(2021, 6, 30, 1, 33, 7, 420000000)
	}

	test("Parses dates and times (Offset)") {
		Json.decodeFromString(
			plusOne,
			""""2021-06-30T01:33:07+01:00""""
		) shouldBe LocalDateTime(2021, 6, 30, 1, 33, 7)

		Json.decodeFromString(
			plusOne,
			""""2021-06-30T01:33:07.420+01:00""""
		) shouldBe LocalDateTime(2021, 6, 30, 1, 33, 7, 420000000)

		Json.decodeFromString(
			plusOne,
			""""2024-07-23T20:25:24.420Z""""
		) shouldBe LocalDateTime(2024, 7, 23, 21, 25, 24, 420000000)
	}

	test("Round trips values") {
		val value = LocalDateTime(2024, 7, 23, 21, 25, 24, 420000000)
		Json.decodeFromString(plusOne, Json.encodeToString(plusOne, value)) shouldBe value
	}
})

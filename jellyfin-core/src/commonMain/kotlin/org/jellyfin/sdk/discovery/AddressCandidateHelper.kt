package org.jellyfin.sdk.discovery

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url

private val logger = KotlinLogging.logger {}

/**
 * Parses the given [input] and allows to fix common mistakes.
 */
public class AddressCandidateHelper(
	private val input: String,
) {
	public companion object {
		/**
		 * Default HTTP port for Jellyfin
		 */
		public const val JF_HTTP_PORT: Int = 8096

		/**
		 * Default HTTPS port for Jellyfin
		 */
		public const val JF_HTTPS_PORT: Int = 8920

		/**
		 * HTTP protocol prefix: http://
		 */
		private const val PROTOCOL_HTTP: String = "http://"

		/**
		 * HTTPS protocol prefix: https://
		 */
		private const val PROTOCOL_HTTPS: String = "https://"

		private const val HTTP_DEFAULT_PORT = 80
		private const val HTTPS_DEFAULT_PORT = 443
		private const val MAX_PORT = 65535
	}

	/**
	 * A normalized server address: lowercase host, canonical IPv6 notation and an explicit port.
	 */
	private data class Candidate(
		val isHttps: Boolean,
		val host: String,
		val port: Int,
		val path: String,
	) {
		val defaultPort get() = if (isHttps) HTTPS_DEFAULT_PORT else HTTP_DEFAULT_PORT

		override fun toString(): String = buildString {
			append(if (isHttps) PROTOCOL_HTTPS else PROTOCOL_HTTP)
			append(host)
			if (port != defaultPort) append(':').append(port)

			// Normalize path to always end with a slash, unless there is no path at all
			val trimmedPath = path.trimEnd('/')
			if (trimmedPath.isNotEmpty()) append(trimmedPath).append('/')
		}
	}

	private val candidates = mutableSetOf<Candidate>()
	private val prioritizeComparator = Comparator<Candidate> { a, b -> b.score() - a.score() }

	init {
		logger.debug { "Input is $input" }

		// Add the input as initial candidate
		if (input.isNotBlank()) {
			val hasProtocol = input.startsWith(PROTOCOL_HTTP, ignoreCase = true) ||
				input.startsWith(PROTOCOL_HTTPS, ignoreCase = true)
			val candidate = parseCandidate(if (hasProtocol) input else PROTOCOL_HTTP + input)

			if (candidate != null) candidates.add(candidate)
			else logger.error { "Input $input could not be parsed" }
		}
	}

	@Suppress("ReturnCount", "TooGenericExceptionCaught")
	private fun parseCandidate(value: String): Candidate? {
		val url = try {
			Url(value)
		} catch (err: Exception) {
			logger.debug(err) { "Unable to parse $value" }
			return null
		}

		val isHttps = when (url.protocol.name.lowercase()) {
			"https" -> true
			"http" -> false
			else -> return null
		}

		val authority = value
			.substringAfter("://")
			.substringBefore('/')
			.substringBefore('?')
			.substringBefore('#')
			.substringAfterLast('@')
		val bracketed = authority.startsWith('[')

		// Only accept IPv6 hosts written in brackets, like in a URL
		val host = normalizeHost(url.host, bracketed) ?: return null

		// Validate the explicit port ourselves, Ktor treats port 0 as "default port"
		val explicitPort = when {
			bracketed -> authority.substringAfter(']', "").removePrefix(":")
			else -> authority.substringAfter(':', "")
		}
		if (explicitPort.isNotEmpty() && explicitPort.toIntOrNull() !in 1..MAX_PORT) return null

		val port = url.port
		if (port !in 1..MAX_PORT) return null

		return Candidate(isHttps, host, port, url.encodedPath)
	}

	/**
	 * Add an HTTPS candidate for each HTTP candidate
	 */
	public fun addProtocolCandidates() {
		logger.debug { "Adding protocol candidates" }

		// Add HTTPS candidate for each HTTP candidate
		candidates
			.filterNot { it.isHttps }
			.forEach {
				candidates.add(
					it.copy(
						isHttps = true,
						port = if (it.port == HTTP_DEFAULT_PORT) HTTPS_DEFAULT_PORT else it.port,
					)
				)
			}
	}

	/**
	 * Add a candidate using Jellyfin ports for each candidate without a specified port or
	 * protocol-default port.
	 */
	public fun addPortCandidates() {
		logger.debug { "Adding port candidates" }

		candidates
			.filter { it.port == it.defaultPort }
			.forEach {
				when {
					it.isHttps -> {
						candidates.add(it.copy(port = JF_HTTP_PORT))
						candidates.add(it.copy(port = JF_HTTPS_PORT))
					}

					else -> {
						candidates.add(it.copy(port = JF_HTTP_PORT))
					}
				}
			}
	}

	/**
	 * Applies all fixes to the [input].
	 */
	public fun addCommonCandidates() {
		logger.debug { "Adding common candidates" }

		addProtocolCandidates()
		addPortCandidates()
	}

	@Suppress("MagicNumber")
	private fun Candidate.score(): Int {
		// Start out with a score of 0
		var score = 0

		// Score based on protocol
		when {
			isHttps -> score += 5
			else -> score -= 5 // HTTP is less secure, try a secure option first
		}

		// Score based on port
		when (port) {
			JF_HTTP_PORT -> score += 2
			JF_HTTPS_PORT -> score -= 1 // Using the Jellyfin HTTPS port is not common
		}

		// Prefer default port for used protocol
		if (isHttps && port == HTTPS_DEFAULT_PORT) score += 3
		if (!isHttps && port == HTTP_DEFAULT_PORT) score += 3

		return score
	}

	/**
	 * Returns all unique candidates sorted by priority.
	 *
	 * The priority is based on a few rules:
	 * - HTTPS before HTTP
	 * - Jellyfin ports before protocol default ports
	 */
	public fun getCandidates(): Collection<String> = candidates
		.sortedWith(prioritizeComparator)
		.map { it.toString() }
		.distinct()
}

private const val FORBIDDEN_HOST_CHARACTERS = " #%/:?@[\\]"

/**
 * Validate and normalize a host. Returns null when the host is invalid.
 */
internal fun normalizeHost(rawHost: String, bracketed: Boolean): String? {
	val host = rawHost.removePrefix("[").removeSuffix("]")

	return when {
		host.isEmpty() -> null
		// IPv6
		':' in host -> if (bracketed) canonicalizeIpv6(host)?.let { "[$it]" } else null
		// Hostname or IPv4
		host.any { it <= ' ' || it == '\u007f' || it in FORBIDDEN_HOST_CHARACTERS } -> null
		else -> host.lowercase()
	}
}

private const val IPV6_GROUPS = 8
private const val IPV6_GROUP_MAX_LENGTH = 4
private const val IPV4_PARTS = 4
private const val IPV4_PART_MAX_LENGTH = 3
private const val BYTE_MAX = 255
private const val BITS_PER_GROUP = 16
private const val GROUP_MASK = 0xffff

/**
 * Canonicalize an IPv6 address (without brackets) following RFC 5952: lowercase, no leading zeros and the longest run
 * of at least two zero groups compressed. Returns null when the address is invalid.
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod")
internal fun canonicalizeIpv6(address: String): String? {
	fun parseIpv4(value: String): Int? {
		val parts = value.split('.')
		if (parts.size != IPV4_PARTS) return null

		return parts.fold(0) { acc, part ->
			if (part.isEmpty() || part.length > IPV4_PART_MAX_LENGTH || !part.all(Char::isDigit)) return null
			val byte = part.toInt()
			if (byte > BYTE_MAX) return null
			(acc shl Byte.SIZE_BITS) or byte
		}
	}

	fun parseGroups(value: String): List<Int>? {
		if (value.isEmpty()) return emptyList()

		val tokens = value.split(':')
		val groups = mutableListOf<Int>()
		for ((index, token) in tokens.withIndex()) {
			if ('.' in token) {
				// Embedded IPv4 address is only allowed as last part
				if (index != tokens.lastIndex) return null
				val ipv4 = parseIpv4(token) ?: return null
				groups += ipv4 ushr BITS_PER_GROUP
				groups += ipv4 and GROUP_MASK
			} else {
				if (token.isEmpty() || token.length > IPV6_GROUP_MAX_LENGTH) return null
				groups += token.toIntOrNull(radix = 16) ?: return null
			}
		}
		return groups
	}

	val parts = address.split("::")
	if (parts.size > 2) return null

	val head = parseGroups(parts[0]) ?: return null
	val groups = if (parts.size == 2) {
		val tail = parseGroups(parts[1]) ?: return null
		val missing = IPV6_GROUPS - head.size - tail.size
		if (missing < 1) return null
		head + List(missing) { 0 } + tail
	} else {
		head
	}
	if (groups.size != IPV6_GROUPS || groups.any { it !in 0..GROUP_MASK }) return null

	// Find longest run of zero groups
	var bestStart = -1
	var bestLength = 0
	var i = 0
	while (i < IPV6_GROUPS) {
		if (groups[i] != 0) {
			i++
			continue
		}

		var end = i
		while (end < IPV6_GROUPS && groups[end] == 0) end++
		if (end - i > bestLength) {
			bestStart = i
			bestLength = end - i
		}
		i = end
	}
	// A single zero group is not compressed
	if (bestLength < 2) bestStart = -1

	return buildString {
		var index = 0
		while (index < IPV6_GROUPS) {
			if (index == bestStart) {
				append("::")
				index += bestLength
				continue
			}

			if (isNotEmpty() && !endsWith(':')) append(':')
			append(groups[index].toString(radix = 16))
			index++
		}
	}
}

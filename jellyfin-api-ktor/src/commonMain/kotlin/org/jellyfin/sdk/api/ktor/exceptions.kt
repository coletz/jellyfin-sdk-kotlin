package org.jellyfin.sdk.api.ktor

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Map an exception thrown by Ktor (or the underlying engine) to an [ApiClientException]. Unknown exceptions,
 * including [CancellationException], are returned unchanged.
 */
internal fun Throwable.toApiClientException(): Throwable = when (this) {
	is ApiClientException -> this
	is HttpRequestTimeoutException -> TimeoutException("HTTP request timed out", this)
	is ConnectTimeoutException -> TimeoutException("Connection timed out", this)
	is SocketTimeoutException -> TimeoutException("Socket timed out", this)
	is CancellationException -> this
	is SerializationException -> InvalidContentException("Serialization failed", this)
	else -> toPlatformApiClientException()
		?: if (this is IOException) ApiClientException("Unknown IO error occurred!", this) else this
}

/**
 * Map engine specific exceptions to an [ApiClientException] or return null when the exception is unknown.
 */
internal expect fun Throwable.toPlatformApiClientException(): ApiClientException?

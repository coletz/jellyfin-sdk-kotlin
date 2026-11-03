package org.jellyfin.sdk.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal actual fun createDefaultHttpClient(): HttpClient = HttpClient(OkHttp)

internal actual fun Throwable.toPlatformApiClientException(): ApiClientException? = when (this) {
	is UnknownHostException -> TimeoutException("HTTP host unreachable", this)
	is InterruptedIOException -> TimeoutException("Connection timed out or was interrupted", this)
	is ConnectException -> TimeoutException("Connection failed", this)
	is SSLException -> SecureConnectionException("Unknown SSL error occurred", this)
	else -> null
}

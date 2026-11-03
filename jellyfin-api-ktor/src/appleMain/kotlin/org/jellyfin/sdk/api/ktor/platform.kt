package org.jellyfin.sdk.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorClientCertificateRejected
import platform.Foundation.NSURLErrorClientCertificateRequired
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorSecureConnectionFailed
import platform.Foundation.NSURLErrorServerCertificateHasBadDate
import platform.Foundation.NSURLErrorServerCertificateHasUnknownRoot
import platform.Foundation.NSURLErrorServerCertificateNotYetValid
import platform.Foundation.NSURLErrorServerCertificateUntrusted
import platform.Foundation.NSURLErrorTimedOut

internal actual fun createDefaultHttpClient(): HttpClient = HttpClient(Darwin)

internal actual fun Throwable.toPlatformApiClientException(): ApiClientException? {
	val error = (this as? DarwinHttpRequestException)?.origin ?: return null
	if (error.domain != NSURLErrorDomain) return null

	val message = error.localizedDescription
	return when (error.code) {
		NSURLErrorTimedOut,
		NSURLErrorCannotFindHost,
		NSURLErrorCannotConnectToHost,
		NSURLErrorDNSLookupFailed,
		NSURLErrorNotConnectedToInternet,
		NSURLErrorNetworkConnectionLost -> TimeoutException(message, this)

		NSURLErrorSecureConnectionFailed,
		NSURLErrorServerCertificateHasBadDate,
		NSURLErrorServerCertificateUntrusted,
		NSURLErrorServerCertificateHasUnknownRoot,
		NSURLErrorServerCertificateNotYetValid,
		NSURLErrorClientCertificateRejected,
		NSURLErrorClientCertificateRequired -> SecureConnectionException(message, this)

		else -> ApiClientException(message, this)
	}
}

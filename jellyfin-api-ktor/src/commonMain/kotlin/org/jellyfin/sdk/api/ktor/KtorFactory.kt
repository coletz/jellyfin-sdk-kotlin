package org.jellyfin.sdk.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.ApiClientFactory
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.sockets.SocketConnection
import org.jellyfin.sdk.api.sockets.SocketConnectionFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo

/**
 * Create a new factory that creates Ktor ApiClient instances and SocketConnections. All instances are built on top of
 * the [base] HttpClient and share its engine. The default base uses the OkHttp engine on the JVM and the Darwin engine
 * on Apple platforms.
 */
public class KtorFactory(
	public val base: HttpClient = createDefaultHttpClient(),
) : ApiClientFactory, SocketConnectionFactory {
	override fun create(
		baseUrl: String?,
		accessToken: String?,
		clientInfo: ClientInfo,
		deviceInfo: DeviceInfo,
		httpClientOptions: HttpClientOptions,
		socketConnectionFactory: SocketConnectionFactory,
	): ApiClient = KtorClient(
		client = createClient(httpClientOptions),
		initialBaseUrl = baseUrl,
		initialAccessToken = accessToken,
		initialClientInfo = clientInfo,
		initialDeviceInfo = deviceInfo,
		httpClientOptions = httpClientOptions,
		socketConnectionFactory = socketConnectionFactory,
	)

	override fun create(
		clientOptions: HttpClientOptions,
		scope: CoroutineScope,
	): SocketConnection = KtorSocketConnection(
		client = createSocketClient(clientOptions),
		scope = scope,
	)

	/**
	 * Create a new [HttpClient] instance for HTTP requests with the [HttpClientOptions] applied to it.
	 */
	public fun createClient(httpClientOptions: HttpClientOptions): HttpClient = base.config {
		expectSuccess = false
		followRedirects = httpClientOptions.followRedirects

		install(HttpTimeout) {
			connectTimeoutMillis = httpClientOptions.connectTimeout.inWholeMilliseconds
			requestTimeoutMillis = httpClientOptions.requestTimeout.inWholeMilliseconds
			socketTimeoutMillis = httpClientOptions.socketTimeout.inWholeMilliseconds
		}
	}

	/**
	 * Create a new [HttpClient] instance for WebSocket connections with the [HttpClientOptions] applied to it. Only the
	 * connect timeout is used, a WebSocket is long-lived and may be idle between keep-alive messages.
	 */
	public fun createSocketClient(httpClientOptions: HttpClientOptions): HttpClient = base.config {
		followRedirects = httpClientOptions.followRedirects

		install(HttpTimeout) {
			connectTimeoutMillis = httpClientOptions.connectTimeout.inWholeMilliseconds
		}

		install(WebSockets)
	}
}

/**
 * Create the [HttpClient] used by default in [KtorFactory], using the recommended engine for the platform.
 */
internal expect fun createDefaultHttpClient(): HttpClient

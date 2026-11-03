package org.jellyfin.sdk.api.ktor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.util.toMap
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.sockets.DefaultSocketApi
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.api.sockets.SocketConnectionFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.FileInfo
import io.ktor.http.HttpMethod as KtorHttpMethod

private val logger = KotlinLogging.logger {}

@Suppress("LongParameterList")
public class KtorClient(
	private val client: HttpClient,
	initialBaseUrl: String?,
	initialAccessToken: String?,
	initialClientInfo: ClientInfo,
	initialDeviceInfo: DeviceInfo,
	override val httpClientOptions: HttpClientOptions,
	private val socketConnectionFactory: SocketConnectionFactory,
) : ApiClient() {
	public override var baseUrl: String? = initialBaseUrl
		private set
	public override var accessToken: String? = initialAccessToken
		private set
	public override var clientInfo: ClientInfo = initialClientInfo
		private set
	public override var deviceInfo: DeviceInfo = initialDeviceInfo
		private set

	private val _webSocket = lazy {
		DefaultSocketApi(this, httpClientOptions.socketReconnectPolicy, socketConnectionFactory)
	}

	override val webSocket: SocketApi by _webSocket

	override fun update(baseUrl: String?, accessToken: String?, clientInfo: ClientInfo, deviceInfo: DeviceInfo) {
		this.baseUrl = baseUrl
		this.accessToken = accessToken
		this.clientInfo = clientInfo
		this.deviceInfo = deviceInfo

		// Notify websocket only if initialized
		if (_webSocket.isInitialized()) {
			_webSocket.value.notifyApiClientUpdate()
		}
	}

	override suspend fun request(
		method: HttpMethod,
		pathTemplate: String,
		pathParameters: Map<String, Any?>,
		queryParameters: Map<String, Any?>,
		requestBody: Any?,
	): RawResponse {
		val url = createUrl(pathTemplate, pathParameters, queryParameters)

		// Log HTTP call with access token removed
		logger.info {
			val safeUrl = accessToken?.let { url.replace(it, "******") } ?: url
			"$method $safeUrl"
		}

		// Exceptions are mapped to ApiClientException subclasses that keep the original as cause
		@Suppress("TooGenericExceptionCaught", "SwallowedException")
		try {
			val response = client.request(url) {
				this.method = KtorHttpMethod.parse(method.name)

				header(HttpHeaders.Accept, HEADER_ACCEPT)
				header(
					HttpHeaders.Authorization,
					AuthorizationHeaderBuilder.buildHeader(
						clientName = clientInfo.name,
						clientVersion = clientInfo.version,
						deviceId = deviceInfo.id,
						deviceName = deviceInfo.name,
						accessToken = accessToken,
					)
				)
				header(HttpHeaders.UserAgent, "${clientInfo.name}/${clientInfo.version} via jellyfin-sdk-kotlin (Ktor)")
				if (deviceInfo.languages.isNotEmpty()) {
					header(HttpHeaders.AcceptLanguage, deviceInfo.languages.joinToString(","))
				}

				if (method == HttpMethod.POST) createRequestBody(requestBody)?.let(::setBody)
			}

			if (!response.status.isSuccess()) throw InvalidStatusException(response.status.value)
			return RawResponse(response.bodyAsBytes(), response.status.value, response.headers.toMap())
		} catch (err: Throwable) {
			val mapped = err.toApiClientException()
			if (mapped !== err) logger.debug(err) { mapped.message ?: "Request failed" }
			throw mapped
		}
	}

	private fun createRequestBody(requestBody: Any?): OutgoingContent? = when (requestBody) {
		null -> null
		// String content
		is String -> TextContent(requestBody, ContentType.Text.Plain)
		// File content
		is FileInfo -> ByteArrayContent(requestBody.content, ContentType.parse(requestBody.mediaType))
		// Binary content
		is ByteArray -> ByteArrayContent(requestBody, ContentType.Application.OctetStream)
		// Json content
		else -> ApiSerializer.encodeRequestBody(requestBody)?.let { TextContent(it, ContentType.Application.Json) }
	}
}

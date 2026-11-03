package org.jellyfin.sdk.api.ktor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod as KtorHttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.io.IOException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.NameIdPair
import org.jellyfin.sdk.model.toFileInfo

class KtorClientTests : FunSpec({
	fun createApi(
		handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
	): ApiClient {
		val factory = KtorFactory(HttpClient(MockEngine(handler)))
		return factory.create(
			baseUrl = "https://demo.jellyfin.org/stable/",
			accessToken = "token",
			clientInfo = ClientInfo("Test", "1.0"),
			deviceInfo = DeviceInfo("device-id", "Device", listOf("en-US", "nl")),
			httpClientOptions = HttpClientOptions(),
			socketConnectionFactory = factory,
		)
	}

	test("Sends GET requests with SDK headers") {
		var request: HttpRequestData? = null
		val api = createApi {
			request = it
			respond("""{"a":1}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
		}

		val response = api.request(HttpMethod.GET, "/Items/{id}", mapOf("id" to "1"), mapOf("q" to "x"))

		response.status shouldBe 200
		response.body.decodeToString() shouldBe """{"a":1}"""
		response.headers[HttpHeaders.ContentType] shouldBe listOf("application/json")

		val sent = request!!
		sent.method shouldBe KtorHttpMethod.Get
		sent.url.toString() shouldBe "https://demo.jellyfin.org/stable/Items/1?q=x"
		sent.headers[HttpHeaders.Accept] shouldBe ApiClient.HEADER_ACCEPT
		sent.headers[HttpHeaders.Authorization]!! shouldStartWith "MediaBrowser "
		sent.headers[HttpHeaders.Authorization]!! shouldContain "Token=\"token\""
		sent.headers[HttpHeaders.UserAgent] shouldBe "Test/1.0 via jellyfin-sdk-kotlin (Ktor)"
		sent.headers[HttpHeaders.AcceptLanguage] shouldBe "en-US,nl"
	}

	test("Sends POST bodies with matching content types") {
		val bodies = mutableListOf<Any>()
		val api = createApi {
			bodies += it.body
			respond("", HttpStatusCode.NoContent)
		}

		api.request(HttpMethod.POST, "/json", requestBody = NameIdPair(name = "value", id = "1"))
		api.request(HttpMethod.POST, "/text", requestBody = "text")
		api.request(HttpMethod.POST, "/bytes", requestBody = byteArrayOf(1, 2))
		api.request(HttpMethod.POST, "/file", requestBody = byteArrayOf(3).toFileInfo("image/png"))

		(bodies[0] as TextContent).run {
			text shouldBe """{"Name":"value","Id":"1"}"""
			contentType.withoutParameters() shouldBe ContentType.Application.Json
		}
		(bodies[1] as TextContent).run {
			text shouldBe "text"
			contentType.withoutParameters() shouldBe ContentType.Text.Plain
		}
		(bodies[2] as ByteArrayContent).run {
			bytes().toList() shouldBe listOf<Byte>(1, 2)
			contentType shouldBe ContentType.Application.OctetStream
		}
		(bodies[3] as ByteArrayContent).run {
			bytes().toList() shouldBe listOf<Byte>(3)
			contentType shouldBe ContentType.Image.PNG
		}
	}

	test("Throws InvalidStatusException for unsuccessful responses") {
		val api = createApi { respond("", HttpStatusCode.NotFound) }

		shouldThrow<InvalidStatusException> {
			api.request(HttpMethod.GET, "/missing")
		}.status shouldBe 404
	}

	test("Maps connection timeouts to TimeoutException") {
		val api = createApi { throw ConnectTimeoutException("timeout") }

		shouldThrow<TimeoutException> { api.request(HttpMethod.GET, "/") }
	}

	test("Maps IO errors to ApiClientException") {
		val api = createApi { throw IOException("broken") }

		shouldThrow<ApiClientException> { api.request(HttpMethod.GET, "/") }
	}
})

package org.jellyfin.sdk.api.ktor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.sockets.SocketConnection
import org.jellyfin.sdk.api.sockets.SocketConnectionState
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

private val logger = KotlinLogging.logger {}

@Suppress("TooGenericExceptionCaught")
public class KtorSocketConnection(
	private val client: HttpClient,
	private val scope: CoroutineScope,
) : SocketConnection {
	@Volatile
	private var session: DefaultClientWebSocketSession? = null
	private val _state = MutableStateFlow<SocketConnectionState>(SocketConnectionState.Disconnected())
	public override val state: StateFlow<SocketConnectionState> = _state.asStateFlow()

	override suspend fun connect(
		url: String,
		clientName: String,
		clientVersion: String,
		deviceId: String,
		deviceName: String,
		accessToken: String,
		languages: List<String>,
	): Boolean {
		if (session != null) disconnect()

		_state.value = SocketConnectionState.Connecting
		logger.info { "Connecting to $url" }

		val newSession = try {
			client.webSocketSession(url.toWebSocketUrl()) {
				val authorization = AuthorizationHeaderBuilder.buildHeader(
					clientName = clientName,
					clientVersion = clientVersion,
					deviceId = deviceId,
					deviceName = deviceName,
					accessToken = accessToken,
				)
				header(HttpHeaders.Authorization, authorization)
				header(HttpHeaders.UserAgent, "$clientName/$clientVersion via jellyfin-sdk-kotlin (Ktor)")
				if (languages.isNotEmpty()) header(HttpHeaders.AcceptLanguage, languages.joinToString(","))
			}
		} catch (err: CancellationException) {
			_state.value = SocketConnectionState.Disconnected()
			throw err
		} catch (err: Throwable) {
			logger.warn(err) { "WebSocket has failed" }
			_state.value = SocketConnectionState.Disconnected(err.toApiClientException())
			return false
		}

		session = newSession
		scope.launch { receive(newSession) }

		// Wait until the first non-connect message and check if it is not disconnected
		// to ensure we have a valid connection
		return state.first { it != SocketConnectionState.Connecting } !is SocketConnectionState.Disconnected
	}

	private suspend fun receive(currentSession: DefaultClientWebSocketSession) {
		var error: Throwable? = null

		try {
			for (frame in currentSession.incoming) {
				if (frame !is Frame.Text) continue

				val text = frame.readText()
				logger.debug { "Receiving (raw) message $text" }
				_state.value = SocketConnectionState.Message(text)
			}
		} catch (err: CancellationException) {
			throw err
		} catch (err: Throwable) {
			logger.warn(err) { "WebSocket has failed" }
			error = err
		} finally {
			logger.debug { "WebSocket has closed" }

			// Only update the state when this session wasn't replaced or disconnected
			if (session === currentSession) {
				session = null
				_state.value = SocketConnectionState.Disconnected(error)
			}
		}
	}

	override suspend fun send(message: String): Boolean {
		logger.debug { "Sending (raw) message $message" }

		val ws = session
		if (ws == null) {
			logger.warn { "Unable to send message: session is null" }
			return false
		}

		return try {
			ws.send(Frame.Text(message))
			true
		} catch (err: CancellationException) {
			throw err
		} catch (err: Throwable) {
			logger.warn(err) { "Unable to send message" }
			false
		}
	}

	override suspend fun disconnect() {
		val ws = session ?: return

		logger.info { "Disconnecting" }
		session = null

		try {
			ws.close(CloseReason(CloseReason.Codes.NORMAL, ""))
		} catch (err: CancellationException) {
			throw err
		} catch (err: Throwable) {
			logger.debug(err) { "Failed to close WebSocket" }
		}

		_state.value = SocketConnectionState.Disconnected()
	}

	private fun String.toWebSocketUrl(): String = when {
		startsWith("https://", ignoreCase = true) -> "wss://" + substring("https://".length)
		startsWith("http://", ignoreCase = true) -> "ws://" + substring("http://".length)
		else -> this
	}
}

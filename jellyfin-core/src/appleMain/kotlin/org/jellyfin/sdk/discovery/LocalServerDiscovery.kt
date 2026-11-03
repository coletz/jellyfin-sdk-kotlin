package org.jellyfin.sdk.discovery

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jellyfin.sdk.JellyfinOptions
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo

private val logger = KotlinLogging.logger {}

/**
 * Local server discovery is not implemented on Apple platforms yet, [discover] returns an empty flow.
 *
 * Note: a future implementation using UDP broadcasts requires the app to have the
 * `com.apple.developer.networking.multicast` entitlement and a `NSLocalNetworkUsageDescription`.
 */
public actual class LocalServerDiscovery actual constructor(jellyfinOptions: JellyfinOptions) {
	public actual companion object {
		public actual val DISCOVERY_TIMEOUT: Int = 500 // ms
		public actual val DISCOVERY_MAX_SERVERS: Int = 15
	}

	public actual fun discover(
		timeout: Int,
		maxServers: Int,
	): Flow<ServerDiscoveryInfo> = flow {
		logger.warn { "Local server discovery is not supported on Apple platforms yet" }
	}
}

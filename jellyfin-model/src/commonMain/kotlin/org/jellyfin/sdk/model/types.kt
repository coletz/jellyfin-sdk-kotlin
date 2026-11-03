package org.jellyfin.sdk.model

/**
 * UUID type used by the SDK. Alias for the Kotlin standard library [kotlin.uuid.Uuid].
 */
public typealias UUID = kotlin.uuid.Uuid

/**
 * Date time type used by the SDK. Alias for [kotlinx.datetime.LocalDateTime]. Values are converted to/from the system
 * time zone when (de)serialized, see [org.jellyfin.sdk.model.serializer.DateTimeSerializer].
 */
public typealias DateTime = kotlinx.datetime.LocalDateTime

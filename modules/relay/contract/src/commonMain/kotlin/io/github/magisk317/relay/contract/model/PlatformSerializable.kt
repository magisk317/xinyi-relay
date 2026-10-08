package io.github.magisk317.relay.contract.model

/**
 * Marker for types that must stay Java-serializable on the JVM and Android.
 *
 * Common sources cannot name `java.io.Serializable`, so the platform source sets
 * supply it. Carrying the marker through an expect/actual keeps the runtime
 * behaviour of every marked type exactly as it was.
 */
expect interface PlatformSerializable

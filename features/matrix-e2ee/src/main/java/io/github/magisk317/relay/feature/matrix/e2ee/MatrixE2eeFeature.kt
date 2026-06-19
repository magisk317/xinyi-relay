package io.github.magisk317.relay.feature.matrix.e2ee

/**
 * Marker entry-point for the Matrix E2EE dynamic feature module.
 *
 * The base Play app probes this class to verify that SplitCompat can see the
 * dynamic feature, then calls [MatrixE2eeBridge.install] to register the real
 * sender and verification implementations.
 */
object MatrixE2eeFeature

package io.github.magisk317.relay.feature.matrix.e2ee

/**
 * Marker entry-point for the Matrix E2EE dynamic feature module.
 *
 * The actual E2EE sending logic lives in
 * [io.github.magisk317.relay.sender.MatrixE2eeUtils] (withE2ee source set).
 * This class is intentionally kept minimal — the DFM exists primarily to
 * bundle the matrix-rust-sdk-crypto native library so that Play Feature
 * Delivery can install it on demand.
 */
object MatrixE2eeFeature

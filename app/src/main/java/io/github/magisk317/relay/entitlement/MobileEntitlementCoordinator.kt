package io.github.magisk317.relay.entitlement

import android.content.Context
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator as PrivateMobileEntitlementCoordinator
import kotlinx.coroutines.CoroutineScope

typealias MobileEntitlementStatus = com.magisk317.mobile.entitlement.MobileEntitlementStatus
typealias MobileEntitlementClaims = com.magisk317.mobile.entitlement.MobileEntitlementClaims
typealias MobileEntitlementEvaluation = com.magisk317.mobile.entitlement.MobileEntitlementEvaluation
typealias MobileEntitlementChallenge = com.magisk317.mobile.entitlement.MobileEntitlementChallenge
typealias MobileEntitlementGoogleChallenge = com.magisk317.mobile.entitlement.MobileEntitlementGoogleChallenge
typealias MobileEntitlementActivationStatus = com.magisk317.mobile.entitlement.MobileEntitlementActivationStatus
typealias MobileEntitlementActivationState = com.magisk317.mobile.entitlement.MobileEntitlementActivationState

object MobileEntitlementCoordinator {
    fun initialize(context: Context, scope: CoroutineScope) =
        PrivateMobileEntitlementCoordinator.initialize(context, scope)

    fun readPendingChallenge(context: Context): String? =
        PrivateMobileEntitlementCoordinator.readPendingChallenge(context)

    fun clearPendingChallenge(context: Context) =
        PrivateMobileEntitlementCoordinator.clearPendingChallenge(context)

    suspend fun refresh(context: Context): MobileEntitlementEvaluation =
        PrivateMobileEntitlementCoordinator.refresh(context)

    suspend fun createTelegramChallenge(context: Context): MobileEntitlementChallenge =
        PrivateMobileEntitlementCoordinator.createTelegramChallenge(context)

    suspend fun createGoogleChallenge(context: Context): MobileEntitlementGoogleChallenge =
        PrivateMobileEntitlementCoordinator.createGoogleChallenge(context)

    suspend fun pollTelegramChallenge(
        context: Context,
        challengeId: String,
    ): MobileEntitlementActivationState =
        PrivateMobileEntitlementCoordinator.pollTelegramChallenge(context, challengeId)

    suspend fun activateWithLicenseCode(
        context: Context,
        licenseCode: String,
    ): MobileEntitlementEvaluation =
        PrivateMobileEntitlementCoordinator.activateWithLicenseCode(context, licenseCode)

    fun readSavedLicenseCode(context: Context): String? =
        PrivateMobileEntitlementCoordinator.readSavedLicenseCode(context)

    fun clearSavedLicenseCode(context: Context) =
        PrivateMobileEntitlementCoordinator.clearSavedLicenseCode(context)

    suspend fun activateWithGoogleIdToken(
        context: Context,
        challengeId: String,
        idToken: String,
    ): MobileEntitlementActivationState =
        PrivateMobileEntitlementCoordinator.activateWithGoogleIdToken(context, challengeId, idToken)
}

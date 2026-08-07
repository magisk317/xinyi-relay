package io.github.magisk317.relay.entitlement

import org.koin.dsl.module

val mobileEntitlementGoogleSignInModule = module {
    single<MobileEntitlementGoogleSignIn> { UnavailableMobileEntitlementGoogleSignIn() }
}

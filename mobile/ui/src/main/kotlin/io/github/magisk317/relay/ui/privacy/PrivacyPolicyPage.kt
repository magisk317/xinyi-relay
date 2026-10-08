package io.github.magisk317.relay.ui.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.foundation.stripMarkdown
import io.github.magisk317.uikit.surface.PrivacyPolicyScaffold

@Composable
fun PrivacyPolicyPage(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val policyText = remember {
        stripMarkdown(
            context.resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() },
        )
    }
    PrivacyPolicyScaffold(
        title = stringResource(id = R.string.pref_privacy_policy_title),
        policyText = policyText,
        onDismiss = onDismiss,
    )
}

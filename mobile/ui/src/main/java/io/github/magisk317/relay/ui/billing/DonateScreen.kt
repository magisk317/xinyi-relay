package io.github.magisk317.relay.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.magisk317.relay.core.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateScreen(
    onBack: () -> Unit,
    viewModel: DonateViewModel = viewModel(),
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.donate_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Subscription section
            Text(
                text = stringResource(id = R.string.donate_subscription_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.launchSubscription(context as android.app.Activity, "sub_monthly") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = R.string.donate_subscription_monthly))
                }
                Button(
                    onClick = { viewModel.launchSubscription(context as android.app.Activity, "sub_yearly") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = R.string.donate_subscription_yearly))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // One-time donation section
            Text(
                text = stringResource(id = R.string.donate_one_time_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.launchDonation(context as android.app.Activity, "donate_099") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = R.string.donate_one_time_099))
                }
                Button(
                    onClick = { viewModel.launchDonation(context as android.app.Activity, "donate_200") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = R.string.donate_one_time_200))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.launchDonation(context as android.app.Activity, "donate_999") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = R.string.donate_one_time_999))
                }
                Button(
                    onClick = { viewModel.launchDonation(context as android.app.Activity, "donate_1999") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = R.string.donate_one_time_1999))
                }
            }

            // Current subscription status
            val status = if (viewModel.isSubscriptionActive()) {
                stringResource(
                    id = R.string.donate_subscription_active,
                    viewModel.getActiveProductId() ?: "Unknown",
                )
            } else {
                stringResource(id = R.string.donate_subscription_none)
            }

            Text(
                text = stringResource(id = R.string.donate_current_subscription),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

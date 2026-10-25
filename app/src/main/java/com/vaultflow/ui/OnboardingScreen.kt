package com.vaultflow.ui

import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vaultflow.R
import com.vaultflow.feature.VaultViewModel

@Composable
fun OnboardingScreen(viewModel: VaultViewModel) {
    var page by remember { mutableIntStateOf(0) }
    var biometricUnavailable by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pages = listOf(
        Triple(R.string.onboarding_title_1, R.string.onboarding_body_1, Icons.Outlined.FolderOpen),
        Triple(R.string.onboarding_title_2, R.string.onboarding_body_2, Icons.Outlined.VerifiedUser),
        Triple(R.string.onboarding_title_3, R.string.onboarding_body_3, Icons.Outlined.VerifiedUser),
    )
    val (title, body, icon) = pages[page]

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(pages.size) { index ->
                Spacer(
                    Modifier.size(if (index == page) 26.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(stringResource(R.string.brand_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(124.dp),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            imageVector = icon,
                            contentDescription = stringResource(R.string.welcome_icon_description),
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                if (biometricUnavailable) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.biometric_unavailable), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (page == 2) {
                Button(
                    onClick = {
                        val flags = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        val canAuthenticate = BiometricManager.from(context).canAuthenticate(flags) == BiometricManager.BIOMETRIC_SUCCESS
                        biometricUnavailable = !canAuthenticate
                        if (canAuthenticate && viewModel.setBiometricEnabled(true, canAuthenticate)) viewModel.finishOnboarding()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text(stringResource(R.string.enable_lock)) }
                TextButton(onClick = viewModel::finishOnboarding) { Text(stringResource(R.string.not_now)) }
            } else {
                Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text(stringResource(R.string.continue_label))
                }
            }
        }
    }
}

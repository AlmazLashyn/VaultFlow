package com.vaultflow.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vaultflow.BuildConfig
import com.vaultflow.R
import com.vaultflow.data.local.CategoryWithCount
import com.vaultflow.data.local.DocumentEntity
import com.vaultflow.data.local.DocumentWithTags
import com.vaultflow.feature.VaultViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(viewModel: VaultViewModel, onAddDocument: () -> Unit, onOpenDocument: (String) -> Unit) {
    val documents by viewModel.documents.collectAsState()
    val count by viewModel.documentCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var sortMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(stringResource(R.string.your_vault), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.hello_again), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::search,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) },
                placeholder = { Text(stringResource(R.string.search_documents)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(pluralStringResource(R.plurals.documents_count, count, count), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.local_storage), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onAddDocument, modifier = Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_document), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recently_added), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort_by)) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf("recent" to R.string.sort_recent, "title" to R.string.sort_name, "expiration" to R.string.sort_expiration).forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { viewModel.setSortOrder(key); sortMenu = false })
                        }
                    }
                }
            }
        }
        if (documents.isEmpty()) {
            item {
                EmptyVaultState(
                    title = if (count == 0) stringResource(R.string.empty_vault_title) else stringResource(R.string.empty_search),
                    body = if (count == 0) stringResource(R.string.empty_vault_body) else "",
                    onAddDocument = if (count == 0) onAddDocument else null,
                )
            }
        } else {
            items(documents, key = { it.document.id }) { item ->
                DocumentCard(item, onClick = { onOpenDocument(item.document.id) }, onFavorite = { viewModel.toggleFavorite(item.document) })
            }
        }
        if (categories.isNotEmpty()) {
            item { Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.take(4).forEach { category ->
                        OutlinedButton(onClick = { viewModel.selectCategory(category.id) }) { Text(categoryLabel(category.id)) }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoriesScreen(viewModel: VaultViewModel, onSelect: (String) -> Unit) {
    val categories by viewModel.categoriesWithCount.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.categories), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
        items(categories, key = { it.category.id }) { category -> CategoryCard(category, onClick = { onSelect(category.category.id) }) }
    }
}

@Composable
fun FavoritesScreen(viewModel: VaultViewModel, onOpenDocument: (String) -> Unit) {
    val documents by viewModel.documents.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.favorites), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        if (documents.isEmpty()) EmptyVaultState(stringResource(R.string.favorites), stringResource(R.string.no_favorites), null)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(documents, key = { it.document.id }) { item ->
                DocumentCard(item, onClick = { onOpenDocument(item.document.id) }, onFavorite = { viewModel.toggleFavorite(item.document) })
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: VaultViewModel) {
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val remindersEnabled by viewModel.expirationRemindersEnabled.collectAsState()
    val reminderIntervals by viewModel.reminderIntervals.collectAsState()
    var clearConfirmation by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var notificationDeniedOnce by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val currentLanguage = AppCompatDelegate.getApplicationLocales().get(0)?.language
        ?: configuration.locales[0]?.language
        ?: "en"
    val selectedLanguage = if (currentLanguage == "ru") "ru" else "en"
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationPermissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionGranted = granted
        notificationDeniedOnce = !granted
        if (granted && remindersEnabled) viewModel.refreshReminders()
    }
    DisposableEffect(lifecycleOwner, context, remindersEnabled, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (granted && !notificationPermissionGranted && remindersEnabled) viewModel.refreshReminders()
                notificationPermissionGranted = granted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.language_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedLanguage == "en",
                            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) },
                            label = { Text(stringResource(R.string.language_english)) },
                        )
                        FilterChip(
                            selected = selectedLanguage == "ru",
                            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru")) },
                            label = { Text(stringResource(R.string.language_russian)) },
                        )
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.security), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.biometric_lock), fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.biometric_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { enabled ->
                                val flags = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                val canAuthenticate = BiometricManager.from(context).canAuthenticate(flags) == BiometricManager.BIOMETRIC_SUCCESS
                                biometricError = !viewModel.setBiometricEnabled(enabled, canAuthenticate)
                            },
                        )
                    }
                    if (biometricError) Text(stringResource(R.string.biometric_unavailable), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.expiration_reminders), fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.expiration_reminders_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = remindersEnabled, onCheckedChange = { enabled ->
                            viewModel.setExpirationRemindersEnabled(enabled)
                            if (enabled && Build.VERSION.SDK_INT >= 33 && !notificationPermissionGranted) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        })
                    }
                    if (remindersEnabled) {
                        listOf(30 to R.string.remind_30_days, 7 to R.string.remind_7_days, 1 to R.string.remind_1_day).forEach { (days, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.setReminderInterval(days, days !in reminderIntervals) }) {
                                Checkbox(checked = days in reminderIntervals, onCheckedChange = { checked -> viewModel.setReminderInterval(days, checked) })
                                Text(stringResource(label))
                            }
                        }
                        if (!notificationPermissionGranted) {
                            Text(stringResource(R.string.notifications_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 33 && notificationDeniedOnce) {
                                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                                } else if (Build.VERSION.SDK_INT >= 33) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }) { Text(stringResource(R.string.allow_notifications)) }
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.local_storage), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.local_storage_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    TextButton(onClick = { showPrivacy = true }) { Text(stringResource(R.string.privacy_policy)) }
                    Text(stringResource(R.string.privacy_policy_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { showLicenses = true }) { Text(stringResource(R.string.open_source_licenses)) }
                    Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedButton(onClick = { clearConfirmation = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.clear_all_data), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (clearConfirmation) {
        AlertDialog(
            onDismissRequest = { clearConfirmation = false },
            title = { Text(stringResource(R.string.clear_all_title)) },
            text = { Text(stringResource(R.string.clear_all_message)) },
            confirmButton = {
                TextButton(onClick = { clearConfirmation = false; viewModel.clearAllData() }) {
                    Text(stringResource(R.string.clear_all_data), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { clearConfirmation = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showPrivacy || showLicenses) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false; showLicenses = false },
            title = { Text(stringResource(if (showPrivacy) R.string.privacy_policy else R.string.open_source_licenses)) },
            text = { Text(stringResource(if (showPrivacy) R.string.privacy_details else R.string.licenses_details)) },
            confirmButton = { TextButton(onClick = { showPrivacy = false; showLicenses = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun EmptyVaultState(title: String, body: String, onAddDocument: (() -> Unit)?) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (body.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onAddDocument != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAddDocument) { Text(stringResource(R.string.add_first_document)) }
            }
        }
    }
}

@Composable
private fun DocumentCard(item: DocumentWithTags, onClick: () -> Unit, onFavorite: () -> Unit) {
    val document = item.document
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = iconTint(document.mimeType).copy(alpha = 0.15f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(documentIcon(document.mimeType), contentDescription = stringResource(R.string.document_icon_description), tint = iconTint(document.mimeType)) }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(document.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(categoryLabel(document.categoryId), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (item.tags.isNotEmpty()) Text(item.tags.joinToString(" · ") { it.tag }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                if (document.isMissing) Text(stringResource(R.string.document_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = if (document.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(if (document.isFavorite) R.string.unfavorite else R.string.favorite),
                    tint = if (document.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(item: CategoryWithCount, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(categoryIcon(item.category.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(categoryLabel(item.category.id), fontWeight = FontWeight.SemiBold)
                Text(pluralStringResource(R.plurals.category_documents, item.documentCount, item.documentCount), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun categoryLabel(id: String): String = stringResource(
    when (id) {
        "personal" -> R.string.personal
        "finance" -> R.string.finance
        "work" -> R.string.work
        "contracts" -> R.string.contracts
        "receipts" -> R.string.receipts
        "insurance" -> R.string.insurance
        else -> R.string.other
    },
)

private fun documentIcon(mime: String): ImageVector = when {
    mime == "application/pdf" -> Icons.Default.PictureAsPdf
    mime.startsWith("image/") -> Icons.Default.Image
    else -> Icons.Default.Description
}

private fun iconTint(mime: String): Color = when {
    mime == "application/pdf" -> Color(0xFFB4564D)
    mime.startsWith("image/") -> Color(0xFF597E71)
    else -> Color(0xFF566B87)
}

private fun categoryIcon(icon: String): ImageVector = when (icon) {
    "work" -> Icons.Default.Work
    "payments" -> Icons.Default.Favorite
    else -> Icons.Default.Description
}

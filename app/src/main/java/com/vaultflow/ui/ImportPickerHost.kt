package com.vaultflow.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vaultflow.R
import com.vaultflow.data.local.CategoryEntity
import com.vaultflow.data.repository.DocumentDraft
import com.vaultflow.feature.VaultViewModel
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ImportPickerHost(
    viewModel: VaultViewModel = hiltViewModel(),
    chooserRequested: Boolean,
    onChooserHandled: () -> Unit,
) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val fallbackTitle = stringResource(R.string.document_name)
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val onUri: (Uri?) -> Unit = { uri -> selectedUri = uri }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onUri)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onUri)

    if (chooserRequested) {
        AlertDialog(
            onDismissRequest = onChooserHandled,
            title = { Text(stringResource(R.string.choose_import)) },
            text = {
                Column {
                    Button(onClick = { onChooserHandled(); filePicker.launch(arrayOf("application/pdf", "image/*")) }) {
                        Text(stringResource(R.string.browse_files))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onChooserHandled(); photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(stringResource(R.string.choose_photo))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onChooserHandled) { Text(stringResource(R.string.cancel)) } },
        )
    }

    selectedUri?.let { uri ->
        ImportMetadataDialog(
            uri = uri,
            categories = categories,
            initialTitle = remember(uri, fallbackTitle) {
                val name = queryDisplayName(context.contentResolver, uri, fallbackTitle)
                name.substringBeforeLast('.', name)
            },
            onCancel = { selectedUri = null },
            onSave = { draft -> viewModel.importDocument(uri, draft); selectedUri = null },
        )
    }
}

@Composable
private fun ImportMetadataDialog(
    uri: Uri,
    categories: List<CategoryEntity>,
    initialTitle: String,
    onCancel: () -> Unit,
    onSave: (DocumentDraft) -> Unit,
) {
    val fallbackTitle = stringResource(R.string.document_name)
    var title by remember(uri, fallbackTitle) { mutableStateOf(initialTitle.ifBlank { fallbackTitle }) }
    var categoryId by remember(uri) { mutableStateOf("personal") }
    var tags by remember(uri) { mutableStateOf("") }
    var documentDate by remember(uri) { mutableStateOf("") }
    var expirationDate by remember(uri) { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    val category = categories.firstOrNull { it.id == categoryId }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.add_document)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text(stringResource(R.string.document_name)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { menuExpanded = true }) { Text(category?.let { categoryLabel(it.id) } ?: stringResource(R.string.category)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        categories.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(categoryLabel(item.id)) },
                                onClick = { categoryId = item.id; menuExpanded = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(tags, { tags = it }, label = { Text(stringResource(R.string.tags_hint)) })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(documentDate, { documentDate = it; dateError = false }, label = { Text(stringResource(R.string.document_date)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(expirationDate, { expirationDate = it; dateError = false }, label = { Text(stringResource(R.string.expiration_date)) }, singleLine = true)
                if (dateError) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.invalid_dates), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val dates = parseDates(documentDate, expirationDate)
                if (title.isBlank() || dates == null) {
                    dateError = true
                    return@Button
                }
                onSave(DocumentDraft(title, categoryId, tags.split(',').map(String::trim), dates.first, dates.second))
            }, enabled = title.isNotBlank()) { Text(stringResource(R.string.save_document)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun parseDates(documentDate: String, expirationDate: String): Pair<Long?, Long?>? = runCatching {
    fun parse(value: String): Long? = if (value.isBlank()) null else LocalDate.parse(value.trim())
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val document = parse(documentDate)
    val expiration = parse(expirationDate)
    if (document != null && expiration != null && expiration < document) null else document to expiration
}.getOrNull()

private fun queryDisplayName(resolver: ContentResolver, uri: Uri, fallback: String): String = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else fallback
    } ?: fallback
}.getOrDefault(fallback)

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


package com.vaultflow.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import androidx.core.graphics.createBitmap
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaultflow.R
import com.vaultflow.data.local.CategoryEntity
import com.vaultflow.data.local.DocumentEntity
import com.vaultflow.data.local.DocumentWithTags
import com.vaultflow.data.repository.OpenedDocument
import com.vaultflow.feature.VaultViewModel
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentDetailsScreen(id: String, viewModel: VaultViewModel, onBack: () -> Unit) {
    val document by viewModel.observeDocument(id).collectAsState(initial = null)
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showExportWarning by remember { mutableStateOf(false) }
    val categories by viewModel.categories.collectAsState()
    val export by viewModel.export.collectAsState()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_document)
    val exportedName = stringResource(R.string.exported_document)
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.finishExport()
    }
    LaunchedEffect(export) {
        val opened = export ?: return@LaunchedEffect
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", opened.plaintextFile)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = opened.document.document.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(exportedName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            shareLauncher.launch(Intent.createChooser(send, shareTitle))
        } catch (_: Exception) {
            viewModel.finishExport()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close)) }
            Text(stringResource(R.string.document_details), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        val item = document
        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Card(shape = RoundedCornerShape(26.dp)) {
                        Column(Modifier.fillMaxWidth().padding(22.dp)) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = stringResource(R.string.document_icon_description), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(18.dp))
                            Text(item.document.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(categoryLabel(item.document.categoryId), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(14.dp))
                            Text(stringResource(R.string.added_on, formatDate(item.document.createdAt)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.file_size, formatBytes(item.document.sizeBytes), item.document.mimeType), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            item.document.documentDate?.let { Text("${stringResource(R.string.document_date).substringBefore('(')}: ${formatDate(it)}") }
                            item.document.expirationDate?.let { Text("${stringResource(R.string.expiration_date).substringBefore('(')}: ${formatDate(it)}") }
                                ?: Text(stringResource(R.string.no_expiration), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.tags.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(item.tags.joinToString(" · ") { it.tag }, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
                if (item.document.isMissing) {
                    item { Text(stringResource(R.string.document_unavailable), color = MaterialTheme.colorScheme.error) }
                }
                item {
                    Button(onClick = { viewModel.openDocument(id) }, enabled = !item.document.isMissing, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.open_document))
                    }
                }
                item {
                    OutlinedButton(onClick = { showExportWarning = true }, enabled = !item.document.isMissing, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.export_document))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { viewModel.toggleFavorite(item.document) }, modifier = Modifier.weight(1f)) {
                            Icon(if (item.document.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = stringResource(if (item.document.isFavorite) R.string.unfavorite else R.string.favorite))
                        }
                        OutlinedButton(onClick = { showEdit = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit)); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.edit))
                        }
                        OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }

    if (showEdit && document != null) {
        EditDocumentDialog(
            item = document!!,
            categories = categories,
            onDismiss = { showEdit = false },
            onSave = { updated, tags -> viewModel.updateDocument(updated, tags); showEdit = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDocument(id) { deleted -> if (deleted) onBack() }
                    showDelete = false
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = { Text(stringResource(R.string.export_warning_title)) },
            text = { Text(stringResource(R.string.export_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExportWarning = false
                    viewModel.prepareExport(id)
                }) { Text(stringResource(R.string.export_document)) }
            },
            dismissButton = { TextButton(onClick = { showExportWarning = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun EditDocumentDialog(
    item: DocumentWithTags,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (DocumentEntity, List<String>) -> Unit,
) {
    var title by remember(item.document.id) { mutableStateOf(item.document.title) }
    var categoryId by remember(item.document.id) { mutableStateOf(item.document.categoryId) }
    var tags by remember(item.document.id) { mutableStateOf(item.tags.joinToString(", ") { it.tag }) }
    var documentDate by remember(item.document.id) { mutableStateOf(item.document.documentDate?.let(::dateInput).orEmpty()) }
    var expirationDate by remember(item.document.id) { mutableStateOf(item.document.expirationDate?.let(::dateInput).orEmpty()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val category = categories.firstOrNull { it.id == categoryId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_document)) },
        text = {
            Column {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text(stringResource(R.string.document_name)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { menuExpanded = true }) { Text(category?.let { categoryLabel(it.id) } ?: stringResource(R.string.category)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        categories.forEach { c -> DropdownMenuItem(text = { Text(categoryLabel(c.id)) }, onClick = { categoryId = c.id; menuExpanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(tags, { tags = it }, label = { Text(stringResource(R.string.tags_hint)) })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(documentDate, { documentDate = it; error = false }, label = { Text(stringResource(R.string.document_date)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(expirationDate, { expirationDate = it; error = false }, label = { Text(stringResource(R.string.expiration_date)) }, singleLine = true)
                if (error) Text(stringResource(R.string.invalid_dates), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val dates = runCatching {
                    val document = parseDate(documentDate)
                    val expiration = parseDate(expirationDate)
                    require(document == null || expiration == null || expiration >= document)
                    document to expiration
                }.getOrNull()
                if (title.isBlank() || dates == null) {
                    error = true
                } else {
                    onSave(item.document.copy(title = title.trim(), categoryId = categoryId, documentDate = dates.first, expirationDate = dates.second), tags.split(',').map(String::trim))
                }
            }, enabled = title.isNotBlank()) { Text(stringResource(R.string.save_changes)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun DocumentViewerDialog(opened: OpenedDocument, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close)) }
                    Text(opened.document.document.title, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                if (opened.document.document.mimeType == "application/pdf") PdfDocumentPreview(opened.plaintextFile)
                else ImageDocumentPreview(opened.plaintextFile, opened.document.document.title)
            }
        }
    }
}

@Composable
private fun ImageDocumentPreview(file: File, title: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { decodeSampledBitmap(file) }
    }
    if (bitmap == null) ErrorPreview()
    else {
        var zoom by remember(file) { mutableFloatStateOf(1f) }
        val transform = rememberTransformableState { _, zoomChange, _, _ -> zoom = (zoom * zoomChange).coerceIn(1f, 5f) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize().transformable(transform).graphicsLayer(scaleX = zoom, scaleY = zoom),
            )
        }
    }
}

@Composable
private fun PdfDocumentPreview(file: File) {
    val pageCount by produceState(initialValue = 0, file) {
        value = withContext(Dispatchers.IO) { runCatching { openPdf(file).use { it.pageCount } }.getOrDefault(0) }
    }
    var pageIndex by remember(file) { mutableIntStateOf(0) }
    val bitmap by produceState<Bitmap?>(initialValue = null, file, pageIndex) {
        value = withContext(Dispatchers.IO) { renderPdfPage(file, pageIndex) }
    }
    if (pageCount <= 0 || bitmap == null) {
        ErrorPreview()
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = pageIndex > 0) { Text(stringResource(R.string.previous)) }
                Text(stringResource(R.string.page_of, pageIndex + 1, pageCount))
                TextButton(onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) }, enabled = pageIndex < pageCount - 1) { Text(stringResource(R.string.next)) }
            }
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = stringResource(R.string.document_icon_description),
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        }
    }
}

@Composable
private fun ErrorPreview() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.could_not_render), color = MaterialTheme.colorScheme.error)
    }
}

private fun openPdf(file: File): PdfRenderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))

private fun renderPdfPage(file: File, index: Int): Bitmap? = runCatching {
    openPdf(file).use { renderer ->
        if (index !in 0 until renderer.pageCount) return null
        renderer.openPage(index).use { page ->
            val scale = (1600f / page.width).coerceAtMost(1.5f)
            val bitmap = createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1))
            page.render(bitmap, Rect(0, 0, bitmap.width, bitmap.height), null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }
}.getOrNull()

private fun decodeSampledBitmap(file: File): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

private fun dateInput(timestamp: Long): String = java.time.Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()
private fun parseDate(value: String): Long? = if (value.isBlank()) null else LocalDate.parse(value.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun formatDate(timestamp: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
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

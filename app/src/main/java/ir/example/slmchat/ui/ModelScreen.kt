package ir.example.slmchat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.example.slmchat.R
import ir.example.slmchat.data.CuratedModel
import ir.example.slmchat.data.CuratedModels
import ir.example.slmchat.data.DownloadUi
import java.io.File

private fun formatSize(bytes: Long): String =
    if (bytes >= 1L shl 30) "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    else "${bytes / (1024 * 1024)} MB"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(
    state: ChatUiState,
    onDownload: (url: String, fileName: String?) -> Unit,
    onCancelDownload: () -> Unit,
    onImport: (uri: Uri, fileName: String?) -> Unit,
    onLoad: (File) -> Unit,
    onDelete: (File) -> Unit,
    onDismissError: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<File?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // اگر نام خالی باشد، نام واقعی فایل انتخاب‌شده استفاده می‌شود
        uri?.let { onImport(it, fileName.ifBlank { null }) }
    }

    toDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_model_title)) },
            text = { Text(stringResource(R.string.delete_model_text, file.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(file); toDelete = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.models_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isLoadingModel) {
                item {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.loading_model), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.errorMessage?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(msg, modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismissError) { Text(stringResource(R.string.ok)) }
                        }
                    }
                }
            }

            // ---- پیشرفت دانلود ----
            state.download?.let { dl ->
                item { DownloadCard(dl, onCancelDownload) }
            }

            // ---- مدل‌های پیشنهادی ----
            item { Text(stringResource(R.string.section_catalog), style = MaterialTheme.typography.titleMedium) }
            items(CuratedModels.list) { model ->
                CuratedCard(
                    model = model,
                    alreadySaved = state.availableModels.any { it.name == model.fileName },
                    canDownload = state.download == null,
                    onDownload = { onDownload(model.url, model.fileName) },
                )
            }

            // ---- لینک مستقیم / ایمپورت ----
            item {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.section_custom), style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(stringResource(R.string.filename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onDownload(url, fileName.ifBlank { null }) },
                        enabled = url.isNotBlank() && state.download == null,
                    ) { Text(stringResource(R.string.download)) }
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = state.importPercent == null,
                    ) { Text(stringResource(R.string.import_file)) }
                }
            }
            state.importPercent?.let { p ->
                item {
                    Column {
                        LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.importing, p), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ---- مدل‌های ذخیره‌شده ----
            item {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.section_saved), style = MaterialTheme.typography.titleMedium)
            }
            if (state.availableModels.isEmpty()) {
                item { Text(stringResource(R.string.no_models)) }
            }
            items(state.availableModels) { file ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.bodyLarge)
                            Text(formatSize(file.length()), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { toDelete = file }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = { onLoad(file) }, enabled = !state.isLoadingModel) {
                            Text(stringResource(R.string.load))
                        }
                    }
                }
            }

            if (state.isModelLoaded) {
                item {
                    Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.back_to_chat, state.loadedModelName ?: ""))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(dl: DownloadUi, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.downloading, dl.fileName), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            if (dl.total > 0 && !dl.waiting) {
                val percent = (dl.downloaded * 100 / dl.total).toInt().coerceIn(0, 100)
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.download_progress, percent, formatSize(dl.downloaded), formatSize(dl.total)),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    if (dl.waiting) stringResource(R.string.download_waiting)
                    else stringResource(R.string.download_progress_unknown, formatSize(dl.downloaded)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun CuratedCard(
    model: CuratedModel,
    alreadySaved: Boolean,
    canDownload: Boolean,
    onDownload: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(model.title, style = MaterialTheme.typography.titleSmall)
            Text(model.sizeLabel, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(model.descriptionRes), style = MaterialTheme.typography.bodyMedium)
            if (model.gated) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.gated_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDownload, enabled = canDownload && !alreadySaved) {
                Text(stringResource(if (alreadySaved) R.string.downloaded else R.string.download))
            }
        }
    }
}

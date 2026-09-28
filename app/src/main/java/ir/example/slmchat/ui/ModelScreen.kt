package ir.example.slmchat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(
    state: ChatUiState,
    onDownload: (url: String, fileName: String) -> Unit,
    onImport: (uri: Uri, fileName: String) -> Unit,
    onLoad: (File) -> Unit,
    onDelete: (File) -> Unit,
    onDismissError: () -> Unit,
    onOpenChat: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("model.task") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it, fileName.ifBlank { "model.task" }) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("مدل‌های SLM") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            state.errorMessage?.let { msg ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(msg, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissError) { Text("باشه") }
                    }
                }
            }

            Text("دانلود مدل با لینک مستقیم (فایل .task)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("آدرس دانلود (URL)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("نام فایل ذخیره‌شده") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = { onDownload(url, fileName.ifBlank { "model.task" }) },
                    enabled = url.isNotBlank() && state.downloadPercent == null,
                ) { Text("دانلود") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Text("انتخاب فایل از حافظه گوشی")
                }
            }
            state.downloadPercent?.let { percent ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("$percent% دانلود شد")
            }

            Spacer(Modifier.height(20.dp))
            Text("مدل‌های ذخیره‌شده روی گوشی", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (state.availableModels.isEmpty()) {
                Text("هنوز مدلی دانلود یا اضافه نشده است.")
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.availableModels) { file ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${file.length() / (1024 * 1024)} مگابایت",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { onDelete(file) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف")
                            }
                            Spacer(Modifier.width(4.dp))
                            Button(onClick = { onLoad(file) }) { Text("بارگذاری") }
                        }
                    }
                }
            }

            if (state.isModelLoaded) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                    Text("بازگشت به چت (${state.loadedModelName})")
                }
            }
        }
    }
}

package ir.example.slmchat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.example.slmchat.R
import ir.example.slmchat.data.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissInfo: () -> Unit,
    onDismissError: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // لیست معکوس است (جدیدترین پیام پایین و در ایندکس ۰)؛ رشد پاسخ در حال استریم خودبه‌خود پایین می‌ماند
    LaunchedEffect(state.messages.size) { listState.scrollToItem(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.loadedModelName ?: stringResource(R.string.chat_title_default),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat, enabled = !state.isLoadingModel) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_chat))
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.history))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.type_message)) },
                        enabled = !state.isGenerating && !state.isLoadingModel,
                        maxLines = 5,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (state.isGenerating) {
                        FilledIconButton(onClick = onStop) {
                            Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.stop))
                        }
                    } else {
                        IconButton(
                            onClick = {
                                onSend(input)
                                input = ""
                            },
                            enabled = input.isNotBlank() && !state.isLoadingModel,
                        ) {
                            // نسخه‌ی AutoMirrored: در حالت RTL (فارسی) جهت فلش برعکس می‌شود
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoadingModel) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.errorMessage?.let { msg ->
                BannerCard(msg, isError = true, onDismiss = onDismissError)
            }
            state.infoMessage?.let { msg ->
                BannerCard(msg, isError = false, onDismiss = onDismissInfo)
            }

            if (state.messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.empty_chat),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val reversed = remember(state.messages) { state.messages.asReversed() }
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(reversed) { index, msg ->
                        MessageBubble(msg, streaming = state.isGenerating && index == 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerCard(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, streaming: Boolean) {
    val isUser = msg.role == ChatMessage.Role.USER
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 340.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (msg.text.isBlank()) {
                    Text("…")
                } else {
                    // متن قابل انتخاب و کپی؛ جهت متن (فارسی/انگلیسی) را خود Compose از محتوا تشخیص می‌دهد
                    SelectionContainer {
                        if (isUser) Text(msg.text) else MarkdownText(msg.text)
                    }
                }
                if (!isUser && !streaming && msg.text.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(msg.text)) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        msg.tokensPerSec?.let { tps ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.tokens_per_sec, tps),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

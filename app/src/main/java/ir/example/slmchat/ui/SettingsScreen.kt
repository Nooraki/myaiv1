package ir.example.slmchat.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.example.slmchat.R
import ir.example.slmchat.data.AppSettings
import ir.example.slmchat.data.BackendChoice
import ir.example.slmchat.data.PromptTemplate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    // برای فیلدهای متنی و اسلایدرها state محلی نگه می‌داریم تا حین تایپ/کشیدن نپرد
    var token by remember { mutableStateOf(settings.hfToken) }
    var system by remember { mutableStateOf(settings.systemPrompt) }
    var temperature by remember { mutableStateOf(settings.temperature) }
    var topK by remember { mutableStateOf(settings.topK.toFloat()) }
    var topP by remember { mutableStateOf(settings.topP) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- توکن Hugging Face ----
            OutlinedTextField(
                value = token,
                onValueChange = { v ->
                    token = v
                    onChange { s -> s.copy(hfToken = v.trim()) }
                },
                label = { Text(stringResource(R.string.s_hf_token)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.s_hf_token_note), style = MaterialTheme.typography.bodySmall)

            // ---- پرامپت سیستمی ----
            OutlinedTextField(
                value = system,
                onValueChange = { v ->
                    system = v
                    onChange { s -> s.copy(systemPrompt = v) }
                },
                label = { Text(stringResource(R.string.s_system_prompt)) },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            // ---- پارامترهای تولید ----
            Text(stringResource(R.string.s_temperature, temperature), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                onValueChangeFinished = { onChange { s -> s.copy(temperature = temperature) } },
                valueRange = 0f..2f,
            )

            Text(stringResource(R.string.s_top_k, topK.roundToInt()), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = topK,
                onValueChange = { topK = it },
                onValueChangeFinished = { onChange { s -> s.copy(topK = topK.roundToInt().coerceAtLeast(1)) } },
                valueRange = 1f..100f,
            )

            Text(stringResource(R.string.s_top_p, topP), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = topP,
                onValueChange = { topP = it },
                onValueChangeFinished = { onChange { s -> s.copy(topP = topP) } },
                valueRange = 0.1f..1f,
            )

            HorizontalDivider()

            // ---- حداکثر توکن ----
            Text(stringResource(R.string.s_max_tokens), style = MaterialTheme.typography.titleSmall)
            ChoiceRow(
                options = listOf(512, 1024, 2048, 4096).map { it to it.toString() },
                selected = settings.maxTokens,
                onSelect = { v -> onChange { s -> s.copy(maxTokens = v) } },
            )

            // ---- پردازنده ----
            Text(stringResource(R.string.s_backend), style = MaterialTheme.typography.titleSmall)
            ChoiceRow(
                options = listOf(BackendChoice.CPU to "CPU", BackendChoice.GPU to "GPU"),
                selected = settings.backend,
                onSelect = { v -> onChange { s -> s.copy(backend = v) } },
            )
            Text(stringResource(R.string.s_gpu_note), style = MaterialTheme.typography.bodySmall)

            // ---- قالب پرامپت ----
            Text(stringResource(R.string.s_template), style = MaterialTheme.typography.titleSmall)
            ChoiceRow(
                options = listOf(
                    PromptTemplate.AUTO to stringResource(R.string.t_auto),
                    PromptTemplate.NONE to stringResource(R.string.t_none),
                    PromptTemplate.GEMMA to "Gemma",
                    PromptTemplate.CHATML to "ChatML (Qwen)",
                ),
                selected = settings.template,
                onSelect = { v -> onChange { s -> s.copy(template = v) } },
            )
            Text(stringResource(R.string.s_template_note), style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Text(stringResource(R.string.s_reload_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

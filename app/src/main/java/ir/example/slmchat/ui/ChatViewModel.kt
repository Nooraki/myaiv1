package ir.example.slmchat.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import ir.example.slmchat.R
import ir.example.slmchat.data.AppSettings
import ir.example.slmchat.data.BackendChoice
import ir.example.slmchat.data.ChatMessage
import ir.example.slmchat.data.Conversation
import ir.example.slmchat.data.ConversationMeta
import ir.example.slmchat.data.ConversationStore
import ir.example.slmchat.data.DownloadUi
import ir.example.slmchat.data.DownloadWorker
import ir.example.slmchat.data.ModelRepository
import ir.example.slmchat.data.SettingsStore
import ir.example.slmchat.llm.LlmModelManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val isLoadingModel: Boolean = false,
    val isModelLoaded: Boolean = false,
    val loadedModelName: String? = null,
    val download: DownloadUi? = null,
    val importPercent: Int? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val availableModels: List<File> = emptyList(),
    val conversations: List<ConversationMeta> = emptyList(),
    val currentConversationId: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val repository = ModelRepository(application)
    private val conversationStore = ConversationStore(application)
    private val settingsStore = SettingsStore(application)
    private val llm = LlmModelManager()

    val settings: StateFlow<AppSettings> = settingsStore.flow

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private var epoch = 0 // با هر «گفتگوی جدید/باز کردن تاریخچه» زیاد می‌شود تا کارهای قدیمی روی state جدید اثر نگذارند
    private var loadedFile: File? = null
    private var appliedKey: List<Any?>? = null
    private var autoTried = false
    private val seenActiveWork = mutableSetOf<UUID>()

    private val stopTokens = listOf("<end_of_turn>", "<|im_end|>", "<eos>", "<|endoftext|>")

    init {
        repository.pruneFinishedWork()
        refreshModels()
        refreshConversations()
        observeDownload()
    }

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun AppSettings.sessionKey(): List<Any?> =
        listOf(temperature, topK, topP, systemPrompt, template, maxTokens, backend)

    // ---------------------------------------------------------------- مدل‌ها

    fun refreshModels() {
        _uiState.update { it.copy(availableModels = repository.listModels()) }
    }

    private fun observeDownload() {
        viewModelScope.launch {
            repository.downloadInfoFlow().collect { info ->
                if (info == null) {
                    _uiState.update { it.copy(download = null) }
                    return@collect
                }
                when (info.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        seenActiveWork.add(info.id)
                        val name = info.tags
                            .firstOrNull { it.startsWith(DownloadWorker.TAG_FILE_PREFIX) }
                            ?.removePrefix(DownloadWorker.TAG_FILE_PREFIX) ?: ""
                        val done = info.progress.getLong(DownloadWorker.KEY_DOWNLOADED, 0L)
                        val total = info.progress.getLong(DownloadWorker.KEY_TOTAL, -1L)
                        _uiState.update {
                            it.copy(download = DownloadUi(name, done, total, info.state != WorkInfo.State.RUNNING))
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _uiState.update { it.copy(download = null) }
                        refreshModels()
                    }
                    WorkInfo.State.FAILED -> {
                        // فقط خطای دانلودی را نشان می‌دهیم که در همین اجرا دیده‌ایم (نه بقایای قدیمی)
                        val message = if (seenActiveWork.remove(info.id)) {
                            info.outputData.getString(DownloadWorker.KEY_ERROR) ?: str(R.string.err_download_generic)
                        } else null
                        _uiState.update { it.copy(download = null, errorMessage = message ?: it.errorMessage) }
                    }
                    WorkInfo.State.CANCELLED -> _uiState.update { it.copy(download = null) }
                }
            }
        }
    }

    fun downloadModel(url: String, fileName: String?) {
        try {
            repository.enqueueDownload(url, fileName)
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    fun cancelDownload() {
        repository.cancelDownload()
    }

    fun importModel(uri: Uri, fileName: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(importPercent = 0) }
            try {
                repository.importFromUri(uri, fileName) { p ->
                    _uiState.update { it.copy(importPercent = p) }
                }
                refreshModels()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: str(R.string.err_read_file)) }
            } finally {
                _uiState.update { it.copy(importPercent = null) }
            }
        }
    }

    fun loadModel(file: File, keepChat: Boolean = false, onLoaded: () -> Unit = {}) {
        if (_uiState.value.isLoadingModel) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModel = true, errorMessage = null) }
            val s = settings.value
            try {
                generationJob?.cancelAndJoin()
                val history = _uiState.value.messages
                settingsStore.loadingMarker = file.name
                llm.load(app, file, s)
                settingsStore.loadingMarker = null
                if (keepChat) llm.resetSession(s, history)
                loadedFile = file
                appliedKey = s.sessionKey()
                settingsStore.update { it.copy(lastModel = file.name) }
                _uiState.update {
                    it.copy(
                        isModelLoaded = true,
                        loadedModelName = file.name,
                        messages = if (keepChat) it.messages else emptyList(),
                        currentConversationId = if (keepChat) it.currentConversationId else null,
                    )
                }
                onLoaded()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                settingsStore.loadingMarker = null
                val hint = if (s.backend == BackendChoice.GPU) " " + str(R.string.err_load_hint_gpu) else ""
                _uiState.update {
                    it.copy(
                        isModelLoaded = llm.isLoaded,
                        errorMessage = str(R.string.err_load_failed, e.message ?: e.javaClass.simpleName) + hint,
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoadingModel = false) }
            }
        }
    }

    /** در اجرای بعدی برنامه، آخرین مدل را خودکار بارگذاری می‌کند. */
    fun autoLoadLast(onLoaded: () -> Unit) {
        if (autoTried) return
        autoTried = true
        if (settingsStore.loadingMarker != null) {
            // اجرای قبلی وسط بارگذاری بسته شده (احتمالاً کمبود رم)
            settingsStore.loadingMarker = null
            _uiState.update { it.copy(errorMessage = str(R.string.err_prev_crash)) }
            return
        }
        val name = settings.value.lastModel ?: return
        val file = repository.listModels().firstOrNull { it.name == name } ?: return
        loadModel(file, onLoaded = onLoaded)
    }

    fun deleteModel(file: File) {
        if (file == loadedFile) {
            generationJob?.cancel()
            llm.close()
            loadedFile = null
            _uiState.update {
                it.copy(isModelLoaded = false, loadedModelName = null, messages = emptyList(), isGenerating = false)
            }
        }
        repository.deleteModel(file)
        if (settings.value.lastModel == file.name) settingsStore.update { it.copy(lastModel = null) }
        refreshModels()
    }

    // ---------------------------------------------------------------- تنظیمات

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        settingsStore.update(block)
    }

    /** پس از بازگشت از صفحه‌ی تنظیمات: اگر چیزی مؤثر بر مدل/نشست عوض شده، اعمالش می‌کند. */
    fun refreshSession() {
        if (!llm.isLoaded) return
        val s = settings.value
        if (s.sessionKey() == appliedKey) return
        val file = loadedFile ?: return
        if (llm.needsReload(s)) {
            loadModel(file, keepChat = true)
            return
        }
        viewModelScope.launch {
            generationJob?.cancelAndJoin()
            epoch++
            _uiState.update { it.copy(isGenerating = false) }
            withContext(Dispatchers.IO) { llm.resetSession(s, _uiState.value.messages) }
            appliedKey = s.sessionKey()
        }
    }

    // ---------------------------------------------------------------- چت

    private fun cleanOutput(raw: String): String {
        var r = raw
        stopTokens.forEach { r = r.replace(it, "") }
        return r
    }

    fun sendMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || !llm.isLoaded || _uiState.value.isGenerating) return

        val history = _uiState.value.messages
        val myEpoch = epoch
        _uiState.update {
            it.copy(
                messages = history + ChatMessage(ChatMessage.Role.USER, clean) + ChatMessage(ChatMessage.Role.MODEL, ""),
                isGenerating = true,
                infoMessage = null,
            )
        }

        generationJob = viewModelScope.launch {
            var accumulated = ""
            var chunks = 0
            var firstAt = 0L
            try {
                prepareContext(clean, history)
                llm.generate(clean).collect { partial ->
                    if (chunks == 0) firstAt = SystemClock.elapsedRealtime()
                    chunks++
                    accumulated += partial
                    val shown = cleanOutput(accumulated)
                    if (myEpoch == epoch) replaceLastModelMessage(ChatMessage(ChatMessage.Role.MODEL, shown))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (myEpoch == epoch) {
                    // IllegalStateException یعنی پیام خودمان (مثلاً «پیام خیلی طولانی است»)
                    val message = if (e is IllegalStateException && !e.message.isNullOrBlank()) {
                        e.message!!
                    } else {
                        str(R.string.err_generate, e.message ?: e.javaClass.simpleName)
                    }
                    _uiState.update { it.copy(errorMessage = message) }
                }
            } finally {
                if (myEpoch == epoch) {
                    val elapsed = (SystemClock.elapsedRealtime() - firstAt) / 1000f
                    val tps = if (chunks > 1 && elapsed > 0f) chunks / elapsed else null
                    val finalText = cleanOutput(accumulated).trim()
                    _uiState.update { st ->
                        val list = st.messages.toMutableList()
                        if (list.isNotEmpty() && list.last().role == ChatMessage.Role.MODEL) {
                            if (finalText.isEmpty()) list.removeAt(list.lastIndex)
                            else list[list.lastIndex] = ChatMessage(ChatMessage.Role.MODEL, finalText, tps)
                        }
                        st.copy(messages = list, isGenerating = false)
                    }
                    val toSave = accumulated
                    viewModelScope.launch(Dispatchers.IO) {
                        llm.noteResponse(toSave)
                        saveCurrent()
                    }
                }
            }
        }
    }

    private fun replaceLastModelMessage(message: ChatMessage) {
        _uiState.update { st ->
            if (st.messages.isEmpty() || st.messages.last().role != ChatMessage.Role.MODEL) return@update st
            val list = st.messages.toMutableList()
            list[list.lastIndex] = message
            st.copy(messages = list)
        }
    }

    /** اگر جای پیام جدید در حافظه‌ی مدل نیست، Session را با بخش اخیر تاریخچه بازسازی می‌کند. */
    private suspend fun prepareContext(text: String, history: List<ChatMessage>) =
        withContext(Dispatchers.IO) {
            val need = llm.countTokens(text)
            if (need > llm.maxTokens * 0.8) throw IllegalStateException(str(R.string.err_too_long))
            val reserve = minOf(512, llm.maxTokens / 3)
            if (llm.contextUsed + need + reserve > llm.maxTokens) {
                llm.resetSession(settings.value, history)
                _uiState.update { it.copy(infoMessage = str(R.string.info_context_trimmed)) }
            }
        }

    fun stopGeneration() {
        generationJob?.cancel()
    }

    fun newChat() {
        viewModelScope.launch {
            generationJob?.cancelAndJoin()
            epoch++
            _uiState.update {
                it.copy(messages = emptyList(), currentConversationId = null, isGenerating = false, infoMessage = null)
            }
            if (llm.isLoaded) {
                withContext(Dispatchers.IO) { llm.resetSession(settings.value, emptyList()) }
                appliedKey = settings.value.sessionKey()
            }
        }
    }

    // ---------------------------------------------------------------- تاریخچه

    private fun refreshConversations() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { conversationStore.list() }
            _uiState.update { it.copy(conversations = list) }
        }
    }

    private suspend fun saveCurrent() {
        val st = _uiState.value
        if (st.messages.isEmpty()) return
        val id = st.currentConversationId ?: UUID.randomUUID().toString()
        val title = st.messages.firstOrNull { it.role == ChatMessage.Role.USER }?.text?.take(40) ?: id
        conversationStore.save(Conversation(id, title, System.currentTimeMillis(), st.messages))
        val list = conversationStore.list()
        _uiState.update { it.copy(currentConversationId = id, conversations = list) }
    }

    fun openConversation(id: String, onOpened: () -> Unit) {
        viewModelScope.launch {
            generationJob?.cancelAndJoin()
            epoch++
            val conv = withContext(Dispatchers.IO) { conversationStore.load(id) } ?: return@launch
            _uiState.update {
                it.copy(messages = conv.messages, currentConversationId = id, isGenerating = false, infoMessage = null)
            }
            if (llm.isLoaded) {
                withContext(Dispatchers.IO) { llm.resetSession(settings.value, conv.messages) }
                appliedKey = settings.value.sessionKey()
            }
            onOpened()
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { conversationStore.delete(id) }
            if (_uiState.value.currentConversationId == id) newChat()
            refreshConversations()
        }
    }

    // ---------------------------------------------------------------- متفرقه

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    override fun onCleared() {
        llm.close()
        super.onCleared()
    }
}

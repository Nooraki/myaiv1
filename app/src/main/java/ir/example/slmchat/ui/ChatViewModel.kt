package ir.example.slmchat.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.example.slmchat.data.DownloadProgress
import ir.example.slmchat.data.ModelRepository
import ir.example.slmchat.llm.LlmModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ChatMessage(val role: Role, val text: String) {
    enum class Role { USER, MODEL }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val isModelLoaded: Boolean = false,
    val loadedModelName: String? = null,
    val downloadPercent: Int? = null,
    val errorMessage: String? = null,
    val availableModels: List<File> = emptyList(),
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ModelRepository(application)
    private val llm = LlmModelManager()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        refreshModels()
    }

    fun refreshModels() {
        _uiState.update { it.copy(availableModels = repository.listDownloadedModels()) }
    }

    fun downloadModel(url: String, fileName: String) {
        viewModelScope.launch {
            repository.downloadModel(url, fileName).collect { progress ->
                when (progress) {
                    is DownloadProgress.InProgress ->
                        _uiState.update { it.copy(downloadPercent = progress.percent) }
                    is DownloadProgress.Done -> {
                        _uiState.update { it.copy(downloadPercent = null) }
                        refreshModels()
                    }
                    is DownloadProgress.Error ->
                        _uiState.update { it.copy(downloadPercent = null, errorMessage = progress.message) }
                }
            }
        }
    }

    fun importModel(uri: Uri, fileName: String) {
        viewModelScope.launch {
            try {
                repository.importFromUri(uri, fileName)
                refreshModels()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun loadModel(file: File) {
        viewModelScope.launch {
            try {
                llm.load(getApplication(), file.absolutePath)
                _uiState.update {
                    it.copy(isModelLoaded = true, loadedModelName = file.name, messages = emptyList())
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "بارگذاری مدل ناموفق بود: ${e.message}") }
            }
        }
    }

    fun deleteModel(file: File) {
        repository.deleteModel(file)
        refreshModels()
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || !llm.isLoaded) return
        val userMsg = ChatMessage(ChatMessage.Role.USER, text)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + ChatMessage(ChatMessage.Role.MODEL, ""),
                isGenerating = true,
            )
        }
        viewModelScope.launch {
            var accumulated = ""
            try {
                llm.generateResponseStream(text).collect { partial ->
                    accumulated += partial
                    _uiState.update { state ->
                        val updated = state.messages.toMutableList()
                        updated[updated.lastIndex] = ChatMessage(ChatMessage.Role.MODEL, accumulated)
                        state.copy(messages = updated)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "خطا در تولید پاسخ: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        llm.close()
        super.onCleared()
    }
}

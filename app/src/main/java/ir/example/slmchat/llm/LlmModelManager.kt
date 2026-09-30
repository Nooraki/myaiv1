package ir.example.slmchat.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import ir.example.slmchat.data.AppSettings
import ir.example.slmchat.data.BackendChoice
import ir.example.slmchat.data.ChatMessage
import ir.example.slmchat.data.PromptTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class ChatFormat { NONE, GEMMA, CHATML }

/**
 * لایه‌ی نازک روی MediaPipe LLM Inference API.
 *
 * - یک Session در طول چت نگه داشته می‌شود تا مدل زمینه را به یاد داشته باشد.
 * - تعداد توکن‌های مصرف‌شده‌ی زمینه (contextUsed) دنبال می‌شود تا قبل از پر شدن حافظه،
 *   Session با بخش اخیر تاریخچه بازسازی شود.
 * - قالب پرامپت (Gemma / ChatML) و پرامپت سیستمی اینجا اعمال می‌شود.
 */
class LlmModelManager {

    private var llm: LlmInference? = null
    private var session: LlmInferenceSession? = null

    private var modelName: String = ""
    private var format = ChatFormat.NONE
    private var systemPrompt = ""
    private var systemPending = true
    private var loadedBackend = BackendChoice.CPU

    var maxTokens: Int = 1024
        private set
    var contextUsed: Int = 0
        private set

    val isLoaded: Boolean
        get() = llm != null && session != null

    /** آیا تغییر تنظیمات نیاز به بارگذاری مجدد خود مدل دارد؟ */
    fun needsReload(s: AppSettings): Boolean =
        isLoaded && (s.maxTokens != maxTokens || s.backend != loadedBackend)

    suspend fun load(context: Context, file: File, settings: AppSettings) =
        withContext(Dispatchers.IO) {
            close()
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(settings.maxTokens)
                .setPreferredBackend(
                    if (settings.backend == BackendChoice.GPU) LlmInference.Backend.GPU
                    else LlmInference.Backend.CPU
                )
                .build()
            val inference = LlmInference.createFromOptions(context.applicationContext, options)
            llm = inference
            modelName = file.name
            maxTokens = settings.maxTokens
            loadedBackend = settings.backend
            createSession(inference, settings)
        }

    private fun createSession(inference: LlmInference, settings: AppSettings) {
        session?.close()
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(settings.topK)
            .setTopP(settings.topP)
            .setTemperature(settings.temperature)
            .build()
        session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
        systemPrompt = settings.systemPrompt.trim()
        systemPending = true
        format = resolveFormat(settings.template, modelName)
        contextUsed = 0
    }

    private fun resolveFormat(pref: PromptTemplate, name: String): ChatFormat = when (pref) {
        PromptTemplate.NONE -> ChatFormat.NONE
        PromptTemplate.GEMMA -> ChatFormat.GEMMA
        PromptTemplate.CHATML -> ChatFormat.CHATML
        PromptTemplate.AUTO -> {
            val n = name.lowercase()
            when {
                "gemma" in n -> ChatFormat.GEMMA
                "qwen" in n -> ChatFormat.CHATML
                else -> ChatFormat.NONE
            }
        }
    }

    // ---------- قالب‌بندی نوبت‌ها ----------

    private fun userTurn(text: String): String {
        val sys = if (systemPending && systemPrompt.isNotEmpty()) systemPrompt else null
        systemPending = false
        return when (format) {
            ChatFormat.GEMMA -> {
                val body = if (sys != null) "$sys\n\n$text" else text
                "<start_of_turn>user\n$body<end_of_turn>\n<start_of_turn>model\n"
            }
            ChatFormat.CHATML -> {
                val prefix = if (sys != null) "<|im_start|>system\n$sys<|im_end|>\n" else ""
                "$prefix<|im_start|>user\n$text<|im_end|>\n<|im_start|>assistant\n"
            }
            ChatFormat.NONE -> if (sys != null) "$sys\n\n$text" else text
        }
    }

    private fun modelTurn(text: String): String = when (format) {
        ChatFormat.GEMMA -> "$text<end_of_turn>\n"
        ChatFormat.CHATML -> "$text<|im_end|>\n"
        ChatFormat.NONE -> "$text\n"
    }

    // ---------- شمارش توکن و مدیریت زمینه ----------

    fun countTokens(text: String): Int =
        runCatching { session?.sizeInTokens(text) }.getOrNull() ?: (text.length / 3 + 1)

    /** طول پاسخ تولیدشده را به زمینه‌ی مصرف‌شده اضافه می‌کند. */
    fun noteResponse(text: String) {
        contextUsed += countTokens(text)
    }

    /**
     * Session را از نو می‌سازد و بخش اخیر تاریخچه را (حداکثر ~۵۰٪ ظرفیت) دوباره به آن می‌دهد.
     * برای «گفتگوی جدید»، بازکردن تاریخچه، پر شدن حافظه و اعمال تنظیمات جدید استفاده می‌شود.
     */
    suspend fun resetSession(settings: AppSettings, history: List<ChatMessage>) =
        withContext(Dispatchers.IO) {
            val inference = llm ?: return@withContext
            createSession(inference, settings)

            val budget = (maxTokens * 0.5).toInt()
            var used = 0
            val picked = ArrayDeque<ChatMessage>()
            for (m in history.asReversed()) {
                if (m.text.isBlank()) continue
                val n = countTokens(m.text) + 8
                if (used + n > budget) break
                used += n
                picked.addFirst(m)
            }
            while (picked.isNotEmpty() && picked.first().role != ChatMessage.Role.USER) {
                picked.removeFirst()
            }
            val s = session ?: return@withContext
            for (m in picked) {
                val chunk = if (m.role == ChatMessage.Role.USER) userTurn(m.text) else modelTurn(m.text)
                s.addQueryChunk(chunk)
                contextUsed += countTokens(chunk)
            }
        }

    // ---------- تولید ----------

    /** پاسخ را تکه‌به‌تکه برمی‌گرداند. لغو کردن collect، تولید را هم متوقف می‌کند. */
    fun generate(prompt: String): Flow<String> = callbackFlow {
        val s = session ?: run {
            close(IllegalStateException("Model is not loaded"))
            return@callbackFlow
        }
        val chunk = userTurn(prompt)
        contextUsed += countTokens(chunk)
        val finished = AtomicBoolean(false)
        s.addQueryChunk(chunk)
        s.generateResponseAsync(
            ProgressListener<String> { partial, done ->
                trySend(partial ?: "")
                if (done) {
                    finished.set(true)
                    close()
                }
            }
        )
        awaitClose {
            if (!finished.get()) runCatching { s.cancelGenerateResponseAsync() }
        }
    }.flowOn(Dispatchers.IO)

    fun close() {
        runCatching { session?.close() }
        runCatching { llm?.close() }
        session = null
        llm = null
        contextUsed = 0
    }
}

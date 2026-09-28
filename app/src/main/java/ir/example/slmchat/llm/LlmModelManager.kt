package ir.example.slmchat.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * لایه‌ای نازک روی MediaPipe LLM Inference API (com.google.mediapipe:tasks-genai)
 * برای اجرای مدل‌های زبانی کوچک (SLM) به‌صورت کاملاً آفلاین روی گوشی.
 *
 * مدل باید در قالب .task باشد. یک Session در طول چت نگه داشته می‌شود
 * تا مدل زمینه‌ی گفتگو را به یاد داشته باشد.
 */
class LlmModelManager {

    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null

    val isLoaded: Boolean
        get() = session != null

    /** مدل .task را از یک مسیر مطلق در حافظه‌ی گوشی بارگذاری می‌کند. */
    suspend fun load(
        context: Context,
        modelPath: String,
        maxTokens: Int = 1024,
        topK: Int = 40,
        temperature: Float = 0.8f,
    ) = withContext(Dispatchers.IO) {
        close()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .build()
        val inference = LlmInference.createFromOptions(context, options)

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(topK)
            .setTemperature(temperature)
            .build()
        llmInference = inference
        session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
    }

    /** پاسخ مدل را به‌صورت جریانی (تکه‌به‌تکه) برمی‌گرداند. */
    fun generateResponseStream(prompt: String): Flow<String> = callbackFlow {
        val currentSession = session
        if (currentSession == null) {
            close(IllegalStateException("مدل هنوز بارگذاری نشده است"))
            return@callbackFlow
        }
        currentSession.addQueryChunk(prompt)
        currentSession.generateResponseAsync(
            ProgressListener<String> { partial, done ->
                trySend(partial)
                if (done) close()
            }
        )
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    fun close() {
        session?.close()
        llmInference?.close()
        session = null
        llmInference = null
    }
}

package ir.example.slmchat.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * لایه‌ای نازک روی MediaPipe LLM Inference API (com.google.mediapipe:tasks-genai)
 * برای اجرای مدل‌های زبانی کوچک (SLM) به‌صورت کاملاً آفلاین روی خود گوشی.
 *
 * مدل باید در قالب .task (فرمت مخصوص MediaPipe / LiteRT) باشد. مدل‌هایی مثل
 * Gemma-3 1B، Gemma-2 2B، Phi-2، Falcon-1B و StableLM به این فرمت در دسترس هستند.
 *
 * نکته: این API گوگل در حالت «maintenance-only» است؛ برای پروژه‌های جدی‌تر در آینده
 * می‌توانید به LiteRT-LM Android API مهاجرت کنید.
 */
class LlmModelManager {

    private var llmInference: LlmInference? = null
    private var onPartialResult: ((partial: String, done: Boolean) -> Unit)? = null

    val isLoaded: Boolean
        get() = llmInference != null

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
            .setTopK(topK)
            .setTemperature(temperature)
            .setResultListener { partial, done -> onPartialResult?.invoke(partial, done) }
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
    }

    /** پاسخ مدل را به‌صورت جریانی (تکه‌به‌تکه) برمی‌گرداند. */
    fun generateResponseStream(prompt: String): Flow<String> = callbackFlow {
        val inference = llmInference
        if (inference == null) {
            close(IllegalStateException("مدل هنوز بارگذاری نشده است"))
            return@callbackFlow
        }
        onPartialResult = { partial, done ->
            trySend(partial)
            if (done) close()
        }
        inference.generateResponseAsync(prompt)
        awaitClose { onPartialResult = null }
    }.flowOn(Dispatchers.IO)

    fun close() {
        llmInference?.close()
        llmInference = null
        onPartialResult = null
    }
}

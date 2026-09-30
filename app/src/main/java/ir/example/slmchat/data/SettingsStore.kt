package ir.example.slmchat.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackendChoice { CPU, GPU }

enum class PromptTemplate { AUTO, NONE, GEMMA, CHATML }

data class AppSettings(
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val maxTokens: Int = 1024,
    val backend: BackendChoice = BackendChoice.CPU,
    val systemPrompt: String = "",
    val template: PromptTemplate = PromptTemplate.AUTO,
    val hfToken: String = "",
    val lastModel: String? = null,
)

/**
 * ذخیره‌ی تنظیمات در SharedPreferences اختصاصی اپ.
 * توجه: توکن Hugging Face هم همین‌جا (متن ساده، اما در حافظه‌ی خصوصی اپ) ذخیره می‌شود.
 */
class SettingsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<AppSettings> = _flow.asStateFlow()
    val current: AppSettings get() = _flow.value

    private fun read() = AppSettings(
        temperature = prefs.getFloat("temperature", 0.8f),
        topK = prefs.getInt("top_k", 40),
        topP = prefs.getFloat("top_p", 0.95f),
        maxTokens = prefs.getInt("max_tokens", 1024),
        backend = runCatching { BackendChoice.valueOf(prefs.getString("backend", "CPU") ?: "CPU") }
            .getOrDefault(BackendChoice.CPU),
        systemPrompt = prefs.getString("system_prompt", "") ?: "",
        template = runCatching { PromptTemplate.valueOf(prefs.getString("template", "AUTO") ?: "AUTO") }
            .getOrDefault(PromptTemplate.AUTO),
        hfToken = prefs.getString("hf_token", "") ?: "",
        lastModel = prefs.getString("last_model", null),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_flow.value)
        _flow.value = next
        prefs.edit()
            .putFloat("temperature", next.temperature)
            .putInt("top_k", next.topK)
            .putFloat("top_p", next.topP)
            .putInt("max_tokens", next.maxTokens)
            .putString("backend", next.backend.name)
            .putString("system_prompt", next.systemPrompt)
            .putString("template", next.template.name)
            .putString("hf_token", next.hfToken)
            .putString("last_model", next.lastModel)
            .apply()
    }

    /**
     * نشانگر «در حال بارگذاری مدل». اگر برنامه وسط بارگذاری کرش کند (مثلاً کمبود رم)،
     * نشانگر می‌ماند و در اجرای بعدی بارگذاری خودکار انجام نمی‌شود (جلوگیری از حلقه‌ی کرش).
     */
    var loadingMarker: String?
        get() = prefs.getString("loading_marker", null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove("loading_marker") else editor.putString("loading_marker", value)
            editor.commit() // همگام، تا قبل از کرش احتمالی نوشته شود
        }
}

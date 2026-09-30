package ir.example.slmchat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatMessage(
    val role: Role,
    val text: String,
    val tokensPerSec: Float? = null,
) {
    enum class Role { USER, MODEL }
}

data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
)

data class ConversationMeta(
    val id: String,
    val title: String,
    val updatedAt: Long,
)

/**
 * ذخیره‌ی تاریخچه‌ی گفتگوها به‌صورت یک فایل JSON برای هر گفتگو (بدون نیاز به Room/KSP).
 */
class ConversationStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "conversations").apply { mkdirs() }

    private fun fileOf(id: String) = File(dir, "$id.json")

    fun list(): List<ConversationMeta> =
        dir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching {
                    val o = JSONObject(f.readText())
                    ConversationMeta(o.getString("id"), o.getString("title"), o.getLong("updatedAt"))
                }.getOrNull()
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    fun load(id: String): Conversation? = runCatching {
        val o = JSONObject(fileOf(id).readText())
        val arr = o.getJSONArray("messages")
        val messages = (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            val tps = m.optDouble("tps", Double.NaN)
            ChatMessage(
                role = if (m.getString("role") == "user") ChatMessage.Role.USER else ChatMessage.Role.MODEL,
                text = m.getString("text"),
                tokensPerSec = if (tps.isNaN()) null else tps.toFloat(),
            )
        }
        Conversation(o.getString("id"), o.getString("title"), o.getLong("updatedAt"), messages)
    }.getOrNull()

    fun save(conversation: Conversation) {
        val arr = JSONArray()
        conversation.messages.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("role", if (m.role == ChatMessage.Role.USER) "user" else "model")
                    put("text", m.text)
                    m.tokensPerSec?.let { put("tps", it.toDouble()) }
                }
            )
        }
        val json = JSONObject().apply {
            put("id", conversation.id)
            put("title", conversation.title)
            put("updatedAt", conversation.updatedAt)
            put("messages", arr)
        }
        val tmp = File(dir, "${conversation.id}.tmp")
        tmp.writeText(json.toString())
        val target = fileOf(conversation.id)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    fun delete(id: String) {
        fileOf(id).delete()
    }
}

package ir.example.slmchat.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadProgress {
    data class InProgress(val percent: Int) : DownloadProgress()
    data class Done(val file: File) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}

/**
 * مدیریت فایل‌های مدل (.task): دانلود مستقیم از یک URL، یا وارد کردن دستی
 * فایلی که کاربر از حافظه‌ی گوشی انتخاب می‌کند. همه‌ی مدل‌ها در حافظه‌ی
 * داخلی اختصاصی اپ ذخیره می‌شوند (بدون نیاز به مجوز ذخیره‌سازی).
 */
class ModelRepository(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun modelFile(fileName: String): File = File(modelsDir, fileName)

    fun listDownloadedModels(): List<File> =
        modelsDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.sortedBy { it.name }
            ?: emptyList()

    fun deleteModel(file: File): Boolean = file.delete()

    /** دانلود فایل مدل از یک لینک مستقیم، همراه با گزارش درصد پیشرفت. */
    fun downloadModel(
        url: String,
        fileName: String,
        headers: Map<String, String> = emptyMap(),
    ): Flow<DownloadProgress> = flow {
        val target = modelFile(fileName)
        val tmp = File(modelsDir, "$fileName.part")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
                connect()
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                emit(DownloadProgress.Error("سرور خطای $code برگرداند. اگر مدل روی HuggingFace گیت‌شده (مثل Gemma) است، به یک هدر Authorization با توکن دسترسی نیاز دارید."))
                return@flow
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                emit(DownloadProgress.InProgress(percent))
                            }
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
            emit(DownloadProgress.Done(target))
        } catch (e: Exception) {
            tmp.delete()
            emit(DownloadProgress.Error(e.message ?: "خطای ناشناخته در دانلود مدل"))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /** فایل مدلی را که کاربر با انتخابگر فایل گوشی انتخاب کرده کپی می‌کند. */
    suspend fun importFromUri(uri: Uri, fileName: String): File = withContext(Dispatchers.IO) {
        val target = modelFile(fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("امکان خواندن فایل انتخاب‌شده وجود نداشت")
        target
    }
}

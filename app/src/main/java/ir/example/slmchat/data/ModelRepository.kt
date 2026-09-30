package ir.example.slmchat.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import ir.example.slmchat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** وضعیت دانلود برای نمایش در UI. total ≤ 0 یعنی حجم نامعلوم. */
data class DownloadUi(
    val fileName: String,
    val downloaded: Long,
    val total: Long,
    val waiting: Boolean,
)

/**
 * مدیریت فایل‌های مدل: دانلود پس‌زمینه (WorkManager)، ایمپورت از حافظه‌ی گوشی
 * (با نوار پیشرفت و نوشتن امن روی فایل موقت)، فهرست و حذف.
 */
class ModelRepository(context: Context) {

    private val appContext = context.applicationContext
    private val workManager get() = WorkManager.getInstance(appContext)

    private val modelsDir: File
        get() = File(appContext.filesDir, "models").apply { mkdirs() }

    fun listModels(): List<File> =
        modelsDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".import") }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun deleteModel(file: File): Boolean = file.delete()

    // ---------- نام و آدرس ----------

    fun sanitizeName(raw: String): String {
        var cleaned = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .trimStart('.')
            .take(120)
        if (cleaned.isBlank()) cleaned = "model.task"
        if (cleaned.endsWith(".part") || cleaned.endsWith(".import")) cleaned += ".task"
        return cleaned
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        // لینک صفحه‌ی فایل در Hugging Face (blob) را به لینک دانلود مستقیم (resolve) تبدیل می‌کند
        return if (trimmed.contains("huggingface.co/")) trimmed.replace("/blob/", "/resolve/") else trimmed
    }

    private fun nameFromUrl(url: String): String =
        Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() } ?: "model.task"

    private fun uniqueFile(name: String): File {
        var candidate = File(modelsDir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (candidate.exists()) {
            candidate = File(modelsDir, if (ext.isEmpty()) "$base-$i" else "$base-$i.$ext")
            i++
        }
        return candidate
    }

    // ---------- دانلود (WorkManager) ----------

    fun enqueueDownload(url: String, fileName: String?) {
        val clean = normalizeUrl(url)
        if (!clean.startsWith("https://")) {
            throw IllegalArgumentException(appContext.getString(R.string.err_invalid_url))
        }
        val name = sanitizeName(fileName?.takeIf { it.isNotBlank() } ?: nameFromUrl(clean))
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_URL to clean, DownloadWorker.KEY_NAME to name))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(DownloadWorker.TAG_FILE_PREFIX + name)
            .build()
        workManager.enqueueUniqueWork(DownloadWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelDownload() {
        workManager.cancelUniqueWork(DownloadWorker.UNIQUE_NAME)
        // فایل‌های نیمه‌کاره‌ی دانلود را پاک می‌کنیم (فایل‌های ایمپورت پسوند .import دارند)
        modelsDir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    fun downloadInfoFlow(): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(DownloadWorker.UNIQUE_NAME).map { it.firstOrNull() }

    fun pruneFinishedWork() {
        workManager.pruneWork()
    }

    // ---------- ایمپورت از حافظه‌ی گوشی ----------

    /**
     * فایل انتخاب‌شده را با نوار پیشرفت کپی می‌کند. نام پیش‌فرض همان نام واقعی فایل است
     * و اگر تکراری باشد، مدل قبلی بازنویسی نمی‌شود.
     */
    suspend fun importFromUri(uri: Uri, desiredName: String?, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            var displayName: String? = null
            var size = -1L
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0) displayName = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }

            val base = sanitizeName(desiredName?.takeIf { it.isNotBlank() } ?: displayName ?: "model.task")
            val target = uniqueFile(base)
            if (size > 0 && size > modelsDir.usableSpace - 50L * 1024 * 1024) {
                throw IllegalStateException(appContext.getString(R.string.err_no_space))
            }

            val tmp = File(modelsDir, target.name + ".import")
            try {
                val input = resolver.openInputStream(uri)
                    ?: throw IllegalStateException(appContext.getString(R.string.err_read_file))
                input.use { inp ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var copied = 0L
                        var lastPercent = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = inp.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            copied += n
                            if (size > 0) {
                                val p = (copied * 100 / size).toInt()
                                if (p != lastPercent) {
                                    lastPercent = p
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
                if (!tmp.renameTo(target)) throw IOException("rename failed")
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
            target
        }
}

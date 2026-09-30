package ir.example.slmchat.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ir.example.slmchat.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * دانلود مدل در پس‌زمینه با WorkManager + اعلان Foreground.
 * - ادامه‌ی دانلود (Resume) با هدر Range از فایل .part
 * - در صورت قطع شدن اینترنت، خودکار دوباره تلاش می‌کند
 * - ریدایرکت‌ها دستی دنبال می‌شوند و توکن فقط برای میزبان اصلی فرستاده می‌شود
 *   (CDN ی Hugging Face با هدر Authorization اضافه مشکل پیدا می‌کند)
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "model_download"
        const val KEY_URL = "url"
        const val KEY_NAME = "name"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val TAG_FILE_PREFIX = "file:"
        private const val CHANNEL_ID = "model_download"
        private const val NOTIF_ID = 1001
        private const val MAX_ATTEMPTS = 5
        private const val SPACE_MARGIN = 50L * 1024 * 1024
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val name = inputData.getString(KEY_NAME) ?: return Result.failure()
        return try {
            withContext(Dispatchers.IO) { download(url, name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else fail(applicationContext.getString(R.string.err_network, e.message ?: ""))
        } catch (e: Exception) {
            fail(e.message ?: applicationContext.getString(R.string.err_download_generic))
        }
    }

    private fun fail(message: String) = Result.failure(workDataOf(KEY_ERROR to message))

    private suspend fun download(url: String, name: String): Result {
        val ctx = applicationContext
        val dir = File(ctx.filesDir, "models").apply { mkdirs() }
        val target = File(dir, name)
        val part = File(dir, "$name.part")
        val token = SettingsStore(ctx).current.hfToken.trim().takeIf { it.isNotEmpty() }

        try {
            setForeground(foregroundInfo(name, -1))
        } catch (_: Exception) {
            // اگر سیستم اجازه‌ی foreground نداد، دانلود بدون اعلان ادامه پیدا می‌کند
        }

        var existing = if (part.exists()) part.length() else 0L
        var conn = open(url, token, existing)
        var code = conn.responseCode
        if (code == 416) { // فایل .part نامعتبر است؛ از اول شروع کن
            conn.disconnect()
            part.delete()
            existing = 0L
            conn = open(url, token, 0L)
            code = conn.responseCode
        }

        try {
            if (code == 401 || code == 403) return fail(ctx.getString(R.string.err_auth, code))
            if (code !in 200..299) {
                return if (code >= 500 && runAttemptCount < MAX_ATTEMPTS) Result.retry()
                else fail(ctx.getString(R.string.err_http, code))
            }

            val resumed = code == 206 && existing > 0
            if (!resumed) part.delete() // سرور Range را نادیده گرفته؛ از صفر
            val offset = if (resumed) existing else 0L
            val total = if (resumed) {
                conn.getHeaderField("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull()
                    ?: conn.contentLengthLong.takeIf { it > 0 }?.plus(offset)
                    ?: -1L
            } else {
                conn.contentLengthLong
            }

            if (total > 0 && total - offset > dir.usableSpace - SPACE_MARGIN) {
                return fail(ctx.getString(R.string.err_no_space))
            }

            var downloaded = offset
            var lastReport = 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, resumed).use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastReport >= 700) {
                            lastReport = now
                            report(name, downloaded, total)
                        }
                    }
                }
            }
            report(name, downloaded, total)

            if (total > 0 && part.length() != total) {
                part.delete()
                return fail(ctx.getString(R.string.err_size_mismatch))
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) return fail(ctx.getString(R.string.err_download_generic))
            return Result.success()
        } finally {
            conn.disconnect()
        }
    }

    /** اتصال را باز می‌کند و ریدایرکت‌ها را دستی دنبال می‌کند. */
    private fun open(url: String, token: String?, rangeFrom: Long): HttpURLConnection {
        var current = URL(url)
        val originHost = current.host
        var hops = 0
        while (true) {
            val c = current.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "SlmChat/2.0")
            if (token != null && current.host == originHost) {
                c.setRequestProperty("Authorization", "Bearer $token")
            }
            if (rangeFrom > 0) c.setRequestProperty("Range", "bytes=$rangeFrom-")
            val code = c.responseCode
            if (code in 300..399 && code != 304) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location == null || ++hops > 6) throw IOException("bad redirect")
                current = URL(current, location)
                continue
            }
            return c
        }
    }

    private suspend fun report(name: String, done: Long, total: Long) {
        setProgress(workDataOf(KEY_DOWNLOADED to done, KEY_TOTAL to total))
        val percent = if (total > 0) (done * 100 / total).toInt() else -1
        try {
            setForeground(foregroundInfo(name, percent))
        } catch (_: Exception) {
        }
    }

    private fun foregroundInfo(name: String, percent: Int): ForegroundInfo {
        val ctx = applicationContext
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val cancelIntent = WorkManager.getInstance(ctx).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(ctx.getString(R.string.notif_title, name))
            .setContentText(if (percent >= 0) "$percent%" else "…")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                ctx.getString(R.string.cancel),
                cancelIntent,
            )
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }
}

package com.mrabah.oneuischedule

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The app is sideloaded, so nothing updates it. This asks GitHub what the
 * newest release is and reports back; installing stays the user's decision.
 */
object UpdateChecker {

    private const val LATEST =
        "https://api.github.com/repos/mraba7/Schedule/releases/latest"

    data class Result(
        val newer: Boolean,
        val version: String,
        val page: String,
        val apkUrl: String?,
        val size: Long,
    )

    /** Why a check failed, in words the user can act on. */
    var lastError: String = ""
        private set

    suspend fun check(context: Context): Result? = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Accept", "application/vnd.github+json")
                // GitHub rejects API calls with no User-Agent
                setRequestProperty("User-Agent", "ClassSchedule/" + BuildConfig.VERSION_NAME)
            }

            val code = connection.responseCode
            if (code != 200) {
                lastError = "الخادم ردّ بالرمز $code"
                connection.disconnect()
                return@withContext null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            val page = json.optString("html_url")
            if (tag.isBlank()) {
                lastError = "لا يوجد إصدار منشور بعد"
                return@withContext null
            }

            val assets = json.optJSONArray("assets")
            var apk: String? = null
            var size = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk")) {
                        apk = asset.optString("browser_download_url")
                        size = asset.optLong("size")
                        break
                    }
                }
            }

            Result(
                newer = build(tag) > build(BuildConfig.VERSION_NAME),
                version = tag,
                page = page,
                apkUrl = apk,
                size = size,
            )
        } catch (t: Throwable) {
            lastError = when (t) {
                is java.net.UnknownHostException -> "لا يوجد اتصال بالإنترنت"
                is java.net.SocketTimeoutException -> "انتهت مهلة الاتصال"
                is javax.net.ssl.SSLException -> "فشل الاتصال الآمن — قد تكون الشبكة تحجب GitHub"
                else -> t.javaClass.simpleName
            }
            null
        }
    }

    /** Versions look like 1.42; compare the build number after the dot. */
    private fun build(version: String): Int =
        version.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull() ?: 0
}


/**
 * Downloads the release APK and hands it to the system installer. Android
 * never lets an app replace itself silently, so the last step is always the
 * user tapping "install" — this just removes the browser and the file manager
 * from the middle of it.
 */
object Updater {

    /** @param onProgress 0f..1f, called on a background thread. */
    suspend fun download(
        context: Context,
        url: String,
        expectedBytes: Long,
        onProgress: (Float) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "class-schedule.apk")

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
            }
            val total = if (expectedBytes > 0) expectedBytes else connection.contentLengthLong

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buffer).also { read = it } > 0) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            connection.disconnect()
            target
        } catch (t: Throwable) {
            null
        }
    }

    /** True when the system will even offer the install dialog. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun requestPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".updates", apk
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

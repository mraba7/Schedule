package com.mrabah.oneuischedule

import android.content.Context
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

    data class Result(val newer: Boolean, val version: String, val page: String)

    suspend fun check(context: Context): Result? = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            val page = json.optString("html_url")
            if (tag.isBlank()) return@withContext null

            Result(
                newer = build(tag) > build(BuildConfig.VERSION_NAME),
                version = tag,
                page = page,
            )
        } catch (t: Throwable) {
            null // offline, rate limited, no release yet — all the same to the user
        }
    }

    /** Versions look like 1.42; compare the build number after the dot. */
    private fun build(version: String): Int =
        version.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull() ?: 0
}

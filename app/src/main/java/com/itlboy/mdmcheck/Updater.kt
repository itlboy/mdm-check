package com.itlboy.mdmcheck

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Latest(val version: String, val url: String)

/** Asks GitHub Releases for the latest version. Network errors → null, never thrown to the UI. */
object Updater {
    const val REPO = "itlboy/mdm-check"
    const val RELEASES_URL = "https://github.com/$REPO/releases/latest"

    suspend fun latest(): Latest? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 5_000
            c.readTimeout = 5_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "mdm-check-android")
            try {
                if (c.responseCode != 200) return@runCatching null
                val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                Rules.parseRelease(json.optString("tag_name"), json.optString("html_url"))
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}

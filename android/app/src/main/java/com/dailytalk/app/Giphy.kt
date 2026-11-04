package com.dailytalk.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** GIPHY REST API data used by the app's custom Compose pickers. */
data class Gif(val id: String, val url: String, val preview: String)

object Giphy {
    val configured get() = BuildConfig.GIPHY_API_KEY.isNotBlank()

    /** Search or show trending GIFs; the API key is injected at build time as GIPHY_API_KEY. */
    suspend fun search(q: String): List<Gif> = request("https://api.giphy.com/v1/gifs/${if (q.isBlank()) "trending" else "search"}") {
        it.setRequestProperty("Accept", "application/json")
        it.setRequestProperty("X-GIPHY-API-KEY", BuildConfig.GIPHY_API_KEY)
        it.setRequestProperty("X-Request-Query", q.trim())
    }

    /** Returns GIPHY's animated emoji media for a custom emoji-style picker. */
    suspend fun emojis(): List<Gif> = withContext(Dispatchers.IO) {
        require(configured) { "Add the GIPHY API key to GitHub Actions secret GIPHY_API_KEY and rebuild Android." }
        val url = URL("https://api.giphy.com/v1/emoji?api_key=${URLEncoder.encode(BuildConfig.GIPHY_API_KEY, "UTF-8")}&limit=100&rating=g")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) error("GIPHY emoji request failed (${connection.responseCode})")
            val items = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data") ?: return@withContext emptyList()
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val images = item.optJSONObject("images") ?: return@mapNotNull null
                val full = images.optJSONObject("fixed_height")?.optString("url").orEmpty()
                    .ifBlank { images.optJSONObject("original")?.optString("url").orEmpty() }
                val preview = images.optJSONObject("fixed_height_small")?.optString("url").orEmpty().ifBlank { full }
                if (!full.startsWith("https://media", ignoreCase = true) || full.isBlank()) null
                else Gif(item.optString("id", index.toString()), full, preview)
            }
        } finally { connection.disconnect() }
    }

    private suspend fun request(endpoint: String, configure: (HttpURLConnection) -> Unit): List<Gif> = withContext(Dispatchers.IO) {
        require(configured) { "Add the GIPHY API key to GitHub Actions secret GIPHY_API_KEY and rebuild Android." }
        val query = endpoint.substringAfterLast('/')
        val search = if (query == "search") "&q=${URLEncoder.encode("", "UTF-8")}" else ""
        val url = URL("$endpoint?api_key=${URLEncoder.encode(BuildConfig.GIPHY_API_KEY, "UTF-8")}&limit=24&rating=g$search")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            configure(this)
        }
        try {
            if (connection.responseCode !in 200..299) error("GIPHY request failed (${connection.responseCode})")
            val items = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data") ?: return@withContext emptyList()
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val images = item.optJSONObject("images") ?: return@mapNotNull null
                val full = images.optJSONObject("fixed_height")?.optString("url").orEmpty().ifBlank { images.optJSONObject("original")?.optString("url").orEmpty() }
                val preview = images.optJSONObject("fixed_height_small")?.optString("url").orEmpty().ifBlank { full }
                if (full.isBlank() || !full.startsWith("https://media", ignoreCase = true)) null
                else Gif(item.optString("id", index.toString()), full, preview)
            }
        } finally { connection.disconnect() }
    }
}

package com.dailytalk.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Gif(val id: String, val url: String, val preview: String)

object Giphy {
    val configured get() = BuildConfig.GIPHY_API_KEY.isNotBlank()

    /** Only https://media*.giphy.com URLs pass database.rules.json. */
    suspend fun search(q: String): List<Gif> = withContext(Dispatchers.IO) {
        val endpoint = if (q.isBlank()) "trending" else "search"
        val url = URL("https://api.giphy.com/v1/gifs/$endpoint?api_key=${BuildConfig.GIPHY_API_KEY}&limit=24&rating=g&q=${URLEncoder.encode(q.trim(), "UTF-8")}")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        try {
            if (conn.responseCode != 200) error("GIF search failed")
            val data = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("data")
            (0 until data.length()).mapNotNull { i ->
                val o = data.getJSONObject(i); val img = o.getJSONObject("images")
                val full = img.getJSONObject("fixed_height").getString("url")
                if (!Regex("^https://media[0-9]*\\.giphy\\.com/.*").matches(full)) null
                else Gif(o.getString("id"), full, img.getJSONObject("fixed_height_small").getString("url"))
            }
        } finally { conn.disconnect() }
    }
}

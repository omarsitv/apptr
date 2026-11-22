package com.spmusic.app

import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val image: String,
    val audio: String,
    val download: String,
    val canDownload: Boolean
)

object Api {
    // Gratis en https://devportal.jamendo.com  ->  crea una app y copia el "client_id"
    const val CLIENT_ID = "PON_TU_CLIENT_ID"

    fun search(q: String): List<Track> {
        var url = "https://api.jamendo.com/v3.0/tracks/?client_id=$CLIENT_ID&format=json" +
                "&limit=40&imagesize=300&audioformat=mp32&order=popularity_total"
        if (q.isNotBlank()) url += "&namesearch=" + URLEncoder.encode(q, "UTF-8")
        val conn = URL(url).openConnection().apply {
            connectTimeout = 8000
            readTimeout = 8000
        }
        val text = conn.getInputStream().bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("results")
        val out = ArrayList<Track>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val audio = o.optString("audio")
            if (audio.isBlank()) continue
            out.add(
                Track(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    artist = o.optString("artist_name"),
                    image = o.optString("image"),
                    audio = audio,
                    download = o.optString("audiodownload"),
                    canDownload = o.optBoolean("audiodownload_allowed", false)
                )
            )
        }
        return out
    }
}

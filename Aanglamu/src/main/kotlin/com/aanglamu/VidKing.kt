package com.nomoresnow

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class Vidking : ExtractorApi() {
    override val name = "Vidking"
    override val mainUrl = "https://www.vidking.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val isMovie = url.contains("/embed/movie/")
            val isTv = url.contains("/embed/tv/")

            if (!isMovie && !isTv) {
                // Fallback to extracting direct sources if the URL format isn't recognized
                val doc = app.get(url, referer = mainUrl).document
                extractDirectSources(doc.html(), callback)
                return
            }

            val mediaType = if (isMovie) "movie" else "tv"
            val parts = url.substringAfter("/embed/$mediaType/").split("/")
            val tmdbId = parts.getOrNull(0)?.toIntOrNull() ?: return
            val seasonId = if (isTv) parts.getOrNull(1)?.toIntOrNull() ?: 1 else 1
            val episodeId = if (isTv) parts.getOrNull(2)?.toIntOrNull() ?: 1 else 1

            val headers = mapOf(
                "Accept" to "*/*",
                "Origin" to "https://player.videasy.to",
                "Referer" to "https://player.videasy.to/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
                "Cache-Control" to "no-cache, no-store, must-revalidate",
                "Pragma" to "no-cache",
                "Expires" to "0"
            )

            // 1. Fetch TMDB metadata from wingsdatabase
            val detailsUrl = "https://db.wingsdatabase.com/3/$mediaType/$tmdbId?append_to_response=external_ids"
            val detailsRes = app.get(detailsUrl, headers = headers)
            if (!detailsRes.isSuccessful) {
                throw Exception("Failed to fetch media details from wingsdatabase")
            }
            val detailsJson = JSONObject(detailsRes.text)
            val title = if (mediaType == "movie") detailsJson.optString("title") else detailsJson.optString("name")
            val date = if (mediaType == "movie") detailsJson.optString("release_date") else detailsJson.optString("first_air_date")
            val year = date.split("-").firstOrNull()?.toIntOrNull() ?: 0
            val imdbId = detailsJson.optJSONObject("external_ids")?.optString("imdb_id") ?: ""

            // 2. Fetch the seed
            val seedUrl = "https://api.wingsdatabase.com/seed?mediaId=$tmdbId"
            val seedRes = app.get(seedUrl, headers = headers)
            if (!seedRes.isSuccessful) {
                throw Exception("Failed to fetch seed from wingsdatabase")
            }
            val seed = JSONObject(seedRes.text).getString("seed")

            // 3. Query source endpoints sequentially
            val endpoints = listOf(
                "cdn/sources-with-title" to "Hydrogen",
                "tejo/sources-with-title" to "Titanium",
                "neon2/sources-with-title" to "Oxygen",
                "downloader2/sources-with-title" to "Lithium",
                "1movies/sources-with-title" to "Helium"
            )

            var success = false
            for ((endpoint, serverName) in endpoints) {
                if (success) break
                try {
                    val queryUrl = "https://api.wingsdatabase.com/$endpoint"
                    val params = mapOf(
                        "title" to title,
                        "mediaType" to mediaType,
                        "year" to year.toString(),
                        "episodeId" to episodeId.toString(),
                        "seasonId" to seasonId.toString(),
                        "tmdbId" to tmdbId.toString(),
                        "imdbId" to imdbId,
                        "enc" to "2",
                        "seed" to seed,
                        "_t" to System.currentTimeMillis().toString()
                    )

                    val response = app.get(queryUrl, params = params, headers = headers)
                    if (response.isSuccessful) {
                        val encryptedPayload = response.text
                        if (encryptedPayload.isNotEmpty()) {
                            val decrypted = ShadowlemonDecryptor.decrypt(encryptedPayload, seed, tmdbId)
                            parseDecryptedJson(decrypted, callback, subtitleCallback)
                            success = true
                        }
                    }
                } catch (e: Exception) {
                    logError(e)
                }
            }

            if (!success) {
                // Fallback to page HTML scrape
                val doc = app.get(url, referer = mainUrl).document
                extractDirectSources(doc.html(), callback)
            }

        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun extractDirectSources(html: String, callback: (ExtractorLink) -> Unit) {
        Regex("""["']?(https?://[^\s"']+\.(m3u8|mp4))["']?""").findAll(html).forEach {
            val url = it.groupValues[1]
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "Vidking Direct",
                    url = url,
                    referer = mainUrl,
                    quality = getQuality("", url),
                    type = if (url.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                )
            )
        }
    }

    private fun parseDecryptedJson(
        jsonStr: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        try {
            val json = JSONObject(jsonStr)

            json.optJSONArray("sources")?.let { sources ->
                for (i in 0 until sources.length()) {
                    val src = sources.getJSONObject(i)
                    val url = src.optString("url")
                    val qualityStr = src.optString("quality", "Auto")
                    if (url.isNotEmpty()) {
                        callback.invoke(
                            ExtractorLink(
                                source = name,
                                name = "Vidking $qualityStr",
                                url = url,
                                referer = mainUrl,
                                quality = getQuality(qualityStr, url),
                                type = if (url.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            )
                        )
                    }
                }
            }

            json.optJSONArray("subtitles")?.let { subs ->
                for (i in 0 until subs.length()) {
                    val sub = subs.getJSONObject(i)
                    subtitleCallback.invoke(
                        SubtitleFile(sub.optString("label", "Subtitle"), sub.optString("url"))
                    )
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun getQuality(qualityStr: String, url: String): Int {
        val q = qualityStr.lowercase()
        val u = url.lowercase()
        return when {
            q.contains("1080") || u.contains("1080") -> Qualities.P1080.value
            q.contains("720") || u.contains("720") -> Qualities.P720.value
            q.contains("480") || u.contains("480") -> Qualities.P480.value
            q.contains("360") || u.contains("360") -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
}

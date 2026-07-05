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
            val doc = app.get(url, referer = mainUrl).document
            val html = doc.html()

            val encrypted = extractEncryptedPayload(html)
            val tmdbId = extractTmdbId(html) ?: 0

            if (!encrypted.isNullOrEmpty() && tmdbId > 0) {
                val decrypted = ShadowlemonDecryptor.decrypt(encrypted, tmdbId, tmdbId)
                parseDecryptedJson(decrypted, callback, subtitleCallback)
            } else {
                extractDirectSources(html, callback)
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun extractEncryptedPayload(html: String): String? {
        val regex = Regex("""["']([A-Za-z0-9+/=_-]{80,})["']""")
        return regex.findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it.length > 100 }
    }

    private fun extractTmdbId(html: String): Int? {
        val regex = Regex("""tmdbId["']\s*:\s*(\d+)""")
        return regex.find(html)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractDirectSources(html: String, callback: (ExtractorLink) -> Unit) {
        Regex("""["']?(https?://[^\s"']+\.(m3u8|mp4))["']?""").findAll(html).forEach {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "Vidking Direct",
                    url = it.groupValues[1],
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.VIDEO
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
                    if (url.isNotEmpty()) {
                        callback.invoke(
                            ExtractorLink(
                                source = name,
                                name = "Vidking ${src.optString("quality", "Auto")}",
                                url = url,
                                referer = mainUrl,
                                quality = getQuality(url),
                                type = ExtractorLinkType.VIDEO
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

    private fun getQuality(url: String): Int {
        return when {
            url.contains("1080") -> Qualities.Q1080.value
            url.contains("720") -> Qualities.Q720.value
            url.contains("480") -> Qualities.Q480.value
            else -> Qualities.Unknown.value
        }
    }
}

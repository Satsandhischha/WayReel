package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed interface OEmbedResult {
    data class Success(
        val captionOrTitle: String,
        val authorName: String,
        val sourceUrl: String
    ) : OEmbedResult

    data class FallbackNeeded(
        val normalizedUrl: String,
        val reason: String
    ) : OEmbedResult

    data class InvalidInput(
        val reason: String
    ) : OEmbedResult
}

class InstagramOEmbedClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    suspend fun tryFetchMetadata(rawInput: String): OEmbedResult = withContext(Dispatchers.IO) {
        val url = extractUrl(rawInput)
            ?: return@withContext OEmbedResult.InvalidInput(
                "Paste a full Instagram Reel link (instagram.com/reel/...), or paste the caption below."
            )

        if (!isLikelyInstagramUrl(url)) {
            return@withContext OEmbedResult.InvalidInput(
                "That doesn't look like an Instagram Reel link. Paste an instagram.com link or enter the caption directly."
            )
        }

        val encoded = URLEncoder.encode(url, "UTF-8")
        val endpoint = "https://api.instagram.com/oembed/?url=$encoded"

        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", "WayReelAndroid/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val reason = when (response.code) {
                        400, 401, 403 ->
                            "Instagram now requires a Meta app token to read Reel links automatically. Paste the Reel caption or upload a screen recording below."
                        404 ->
                            "Instagram didn't return public metadata for that link (it may be private or expired). Paste the caption below instead."
                        429 ->
                            "Instagram rate-limited link lookups. Paste the caption or upload a screen recording below."
                        else ->
                            "Couldn't read that Reel link automatically (HTTP ${response.code}). Paste the caption below to continue."
                    }
                    return@withContext OEmbedResult.FallbackNeeded(url, reason)
                }

                val json = JSONObject(body)
                val title = json.optString("title", "").trim()
                val author = json.optString("author_name", "").trim()
                if (title.length < 15) {
                    return@withContext OEmbedResult.FallbackNeeded(
                        normalizedUrl = url,
                        reason = "Instagram returned very little text for this Reel${if (author.isNotBlank()) " by @$author" else ""}. Paste the full caption or upload a recording so we don't miss stops."
                    )
                }

                OEmbedResult.Success(
                    captionOrTitle = title,
                    authorName = author,
                    sourceUrl = url
                )
            }
        } catch (_: Exception) {
            OEmbedResult.FallbackNeeded(
                normalizedUrl = url,
                reason = "Couldn't reach Instagram's oEmbed endpoint right now. Paste the caption or upload a screen recording below."
            )
        }
    }

    private fun extractUrl(input: String): String? {
        val regex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        return regex.find(input.trim())?.value?.trimEnd('.', ',', ')')
    }

    private fun isLikelyInstagramUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("instagram.com/") || lower.contains("instagr.am/")
    }
}

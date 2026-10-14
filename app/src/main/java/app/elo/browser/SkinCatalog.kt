package app.elo.browser

import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

internal object SkinCatalog {
    private const val CATALOG_URL =
        "https://raw.githubusercontent.com/littleban0305/CSGO-API/main/public/api/zh-TW/skins_not_grouped.json"
    private const val MAX_RESULTS = 100

    @Volatile
    private var cachedSkins: List<ArticleResult>? = null

    fun search(term: String): List<ArticleResult> {
        val catalog = cachedSkins ?: synchronized(this) {
            cachedSkins ?: downloadCatalog().also { cachedSkins = it }
        }
        val normalizedTerm = term.trim().lowercase(Locale.ROOT)
        val queryTerms = normalizedTerm.split(Regex("\\s+")).filter(String::isNotBlank)
        return catalog.asSequence()
            .mapIndexed { index, skin ->
                Triple(relevance(skin, normalizedTerm, queryTerms), index, skin)
            }
            .filter { it.first > 0 }
            .sortedWith(compareByDescending<Triple<Int, Int, ArticleResult>> { it.first }.thenBy { it.second })
            .take(MAX_RESULTS)
            .map { it.third }
            .toList()
    }

    private fun relevance(skin: ArticleResult, query: String, terms: List<String>): Int {
        val title = skin.title.lowercase(Locale.ROOT)
        val description = skin.snippet.lowercase(Locale.ROOT)
        var score = when {
            title == query -> 10_000
            title.startsWith(query) -> 6_000
            title.contains(query) -> 4_000
            else -> 0
        }
        score += terms.fold(0) { total, term ->
            total + when {
                title.contains(term) -> 500
                description.contains(term) -> 50
                else -> 0
            }
        }
        return score
    }

    private fun downloadCatalog(): List<ArticleResult> {
        val connection = URL(CATALOG_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 45_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("CS 圖鑑伺服器回應 $status")
            val body = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use(BufferedReader::readText)
            }
            val json = JSONArray(body)
            return List(json.length()) { index ->
                val item = json.getJSONObject(index)
                val rarity = item.optJSONObject("rarity")?.optString("name").orEmpty()
                val wear = item.optJSONObject("wear")?.optString("name").orEmpty()
                val weapon = item.optJSONObject("weapon")?.optString("name")?.takeUnless(String::isBlank)
                val metadata = listOf(rarity, wear).filter(String::isNotBlank).joinToString(" · ")
                val description = item.optString("description").takeUnless { it == "null" }.orEmpty()
                ArticleResult(
                    title = item.optString("name", "未命名武器外觀"),
                    path = item.optString("id", index.toString()),
                    snippet = listOf(description, metadata).filter(String::isNotBlank).joinToString("\n"),
                    imageUrl = item.optString("image").takeUnless { it.isBlank() || it == "null" },
                    isCatalogItem = true,
                    weaponName = weapon,
                    wearName = wear.takeUnless(String::isBlank)
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
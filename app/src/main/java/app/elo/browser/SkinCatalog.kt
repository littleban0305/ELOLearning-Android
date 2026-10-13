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
        "https://raw.githubusercontent.com/littleban0305/CSGO-API/main/public/api/en/skins_not_grouped.json"
    private const val MAX_RESULTS = 50

    @Volatile
    private var cachedSkins: List<ArticleResult>? = null

    fun search(term: String): List<ArticleResult> {
        val catalog = cachedSkins ?: synchronized(this) {
            cachedSkins ?: downloadCatalog().also { cachedSkins = it }
        }
        val normalizedTerm = term.trim().lowercase(Locale.ROOT)
        return catalog.asSequence()
            .filter { skin ->
                skin.title.lowercase(Locale.ROOT).contains(normalizedTerm) ||
                    skin.snippet.lowercase(Locale.ROOT).contains(normalizedTerm)
            }
            .take(MAX_RESULTS)
            .toList()
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
                val metadata = listOf(rarity, wear).filter(String::isNotBlank).joinToString(" · ")
                val description = item.optString("description").takeUnless { it == "null" }.orEmpty()
                ArticleResult(
                    title = item.optString("name", "未命名武器外觀"),
                    path = item.optString("id", index.toString()),
                    snippet = listOf(description, metadata).filter(String::isNotBlank).joinToString("\n"),
                    imageUrl = item.optString("image").takeUnless { it.isBlank() || it == "null" }
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
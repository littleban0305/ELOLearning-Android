package app.elo.browser

import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object LibraryServer {
    fun search(serverUrl: String, term: String, wikipediaOnly: Boolean = false): List<ArticleResult> {
        val baseUrl = serverUrl.trim().trimEnd('/')
        val encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8.name())
        val categoryFilter = if (wikipediaOnly) "&books.filter.category=wikipedia" else ""
        val connection = URL("$baseUrl/search?pattern=$encodedTerm&format=xml&count=50$categoryFilter")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/xml, text/xml")

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use(BufferedReader::readText)
            }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("伺服器回應 $status：${body.take(240)}")
            }
            val query = term.lowercase(java.util.Locale.ROOT)
            val terms = query.split(Regex("\\s+")).filter(String::isNotBlank)
            return parseResults(body, baseUrl).mapIndexed { index, result ->
                Triple(relevance(result, query, terms), index, result)
            }.sortedWith(compareByDescending<Triple<Int, Int, ArticleResult>> { it.first }.thenBy { it.second })
                .map { it.third }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResults(xml: String, baseUrl: String): List<ArticleResult> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val results = mutableListOf<ArticleResult>()
        var inResult = false
        var resultDepth = -1
        var title: String? = null
        var link: String? = null
        var snippet = ""
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT && results.size < 50) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase()
                    if (name in setOf("item", "entry", "result")) {
                        inResult = true
                        resultDepth = parser.depth
                        title = null
                        link = null
                        snippet = ""
                    } else if (inResult) {
                        when (name) {
                            "title" -> if (parser.depth == resultDepth + 1) {
                                title = readElementText(parser)
                            }
                            "link", "url", "guid" -> {
                                val href = parser.getAttributeValue(null, "href")
                                val rel = parser.getAttributeValue(null, "rel")
                                val value = href ?: readElementText(parser)
                                if (link == null || rel == "alternate") link = value
                            }
                            "description", "summary", "content", "snippet" ->
                                snippet = readElementText(parser)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.lowercase() in setOf("item", "entry", "result") && inResult) {
                        val resultTitle = articleTitle(title.orEmpty(), link.orEmpty(), baseUrl)
                        val resultLink = link.orEmpty()
                        if (resultTitle.isNotBlank() && resultLink.isNotBlank()) {
                            results += ArticleResult(
                                title = resultTitle,
                                path = URL(URL(baseUrl), resultLink).toExternalForm(),
                                snippet = snippet
                            )
                        }
                        inResult = false
                    }
                }
            }
            event = parser.next()
        }
        return results
    }

    private fun articleTitle(title: String, link: String, baseUrl: String): String {
        val isGenericBookTitle = title.contains("Wikipedia", ignoreCase = true) ||
            title.contains("維基百科") ||
            title.isBlank()
        if (!isGenericBookTitle) return title
        val pathSegments = Uri.parse(URL(URL(baseUrl), link).toString()).pathSegments
        val articleMarker = pathSegments.indexOfFirst { it.equals("A", ignoreCase = true) }
        val articleSegments = if (articleMarker >= 0) {
            pathSegments.drop(articleMarker + 1)
        } else {
            pathSegments.takeLast(1)
        }
        val pathTitle = Uri.decode(articleSegments.joinToString("/")).replace('_', ' ').trim()
        return pathTitle.ifBlank { title }
    }

    private fun relevance(result: ArticleResult, query: String, terms: List<String>): Int {
        val title = result.title.lowercase(java.util.Locale.ROOT)
        val snippet = result.snippet.lowercase(java.util.Locale.ROOT)
        var score = when {
            title == query -> 10_000
            title.startsWith(query) -> 6_000
            title.contains(query) -> 4_000
            else -> 0
        }
        score += terms.fold(0) { total, term ->
            total + when {
                title.contains(term) -> 500
                snippet.contains(term) -> 50
                else -> 0
            }
        }
        return score
    }

    private fun readElementText(parser: XmlPullParser): String {
        val elementDepth = parser.depth
        val text = StringBuilder()
        var event = parser.next()
        while (event != XmlPullParser.END_TAG || parser.depth != elementDepth) {
            if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT || event == XmlPullParser.ENTITY_REF) {
                text.append(parser.text.orEmpty())
            }
            event = parser.next()
        }
        return text.toString().trim()
    }
}
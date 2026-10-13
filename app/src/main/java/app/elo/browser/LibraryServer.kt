package app.elo.browser

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
    fun search(serverUrl: String, term: String): List<ArticleResult> {
        val baseUrl = serverUrl.trim().trimEnd('/')
        val encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8.name())
        val connection = URL("$baseUrl/search?pattern=$encodedTerm&format=xml&count=50")
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
            return parseResults(body, baseUrl)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResults(xml: String, baseUrl: String): List<ArticleResult> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val results = mutableListOf<ArticleResult>()
        var inResult = false
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
                        title = null
                        link = null
                        snippet = ""
                    } else if (inResult) {
                        when (name) {
                            "title" -> title = parser.nextText().trim()
                            "link", "url", "guid" -> {
                                val href = parser.getAttributeValue(null, "href")
                                val rel = parser.getAttributeValue(null, "rel")
                                val value = href ?: parser.nextText().trim()
                                if (link == null || rel == "alternate") link = value
                            }
                            "description", "summary", "content", "snippet" ->
                                snippet = parser.nextText().trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.lowercase() in setOf("item", "entry", "result") && inResult) {
                        val resultTitle = title.orEmpty()
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
}
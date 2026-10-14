package app.elo.browser

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import org.kiwix.libzim.Archive
import org.kiwix.libzim.Query
import org.kiwix.libzim.Searcher
import org.kiwix.libzim.SuggestionSearcher
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

private const val MAX_RESULTS = 40

internal data class ArticleResult(
    val title: String,
    val path: String,
    val snippet: String = "",
    val imageUrl: String? = null,
    val isCatalogItem: Boolean = false,
    val weaponName: String? = null,
    val wearName: String? = null
)

internal enum class SearchMode {
    TITLE,
    FULL_TEXT
}

internal enum class SearchCategory(val label: String) {
    ALL("全部"),
    AI("AI 模式"),
    CS("CS 圖鑑"),
    WIKIPEDIA("維基百科")
}

internal class ZimSession private constructor(
    private val descriptor: android.os.ParcelFileDescriptor,
    private val archive: Archive,
    val fileName: String
) {
    val title: String
        get() = runCatching { archive.getMetadata("Title") }.getOrDefault(fileName)

    val hasFullTextIndex: Boolean
        get() = archive.hasFulltextIndex()

    fun search(term: String, mode: SearchMode): List<ArticleResult> {
        require(term.isNotBlank())
        return when (mode) {
            SearchMode.TITLE -> searchTitles(term)
            SearchMode.FULL_TEXT -> searchFullText(term)
        }
    }

    private fun searchTitles(term: String): List<ArticleResult> {
        val suggestionSearcher = SuggestionSearcher(archive)
        try {
            val search = suggestionSearcher.suggest(term)
            try {
                val iterator = search.getResults(0, MAX_RESULTS)
                try {
                    val results = ArrayList<ArticleResult>()
                    while (iterator.hasNext()) {
                        val item = iterator.next()
                        results += ArticleResult(item.title, item.path, item.snippet.orEmpty())
                    }
                    return results
                } finally {
                    iterator.dispose()
                }
            } finally {
                search.dispose()
            }
        } finally {
            suggestionSearcher.dispose()
        }
    }

    private fun searchFullText(term: String): List<ArticleResult> {
        check(hasFullTextIndex) {
            "This ZIM file has no full-text index. Title search is still available."
        }
        val searcher = Searcher(archive)
        val query = Query(term)
        try {
            val search = searcher.search(query)
            try {
                val iterator = search.getResults(0, MAX_RESULTS)
                try {
                    val results = ArrayList<ArticleResult>()
                    while (iterator.hasNext()) {
                        results += ArticleResult(
                            title = iterator.title,
                            path = iterator.path,
                            snippet = iterator.snippet.orEmpty()
                        )
                        iterator.next()
                    }
                    return results
                } finally {
                    iterator.dispose()
                }
            } finally {
                search.dispose()
            }
        } finally {
            query.dispose()
            searcher.dispose()
        }
    }

    fun articleUrl(path: String): String = "https://kiwix.app/${Uri.encode(path, "/")}" 

    fun intercept(url: Uri): WebResourceResponse? {
        if (url.host != "kiwix.app") return null
        val encodedPath = url.encodedPath.orEmpty().removePrefix("/")
        val path = Uri.decode(encodedPath)
        return runCatching {
            val item = archive.getEntryByPath(path).getItem(true)
            val bytes = item.data.data
            val mimeType = item.mimetype ?: "application/octet-stream"
            WebResourceResponse(
                mimeType,
                if (mimeType.startsWith("text/") || mimeType.contains("javascript")) "UTF-8" else null,
                ByteArrayInputStream(bytes)
            )
        }.getOrElse {
            WebResourceResponse(
                "text/plain",
                StandardCharsets.UTF_8.name(),
                404,
                "Not found",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0))
            )
        }
    }

    fun mainArticlePath(): String? = runCatching {
        archive.mainEntry.getItem(true).path
    }.getOrNull()

    fun close() {
        runCatching { archive.dispose() }
        runCatching { descriptor.close() }
    }

    companion object {
        init {
            listOf("kiwix", "zim", "kiwix_wrapper", "zim_wrapper").forEach(System::loadLibrary)
        }

        fun open(context: Context, uri: Uri): ZimSession {
            val resolver = context.contentResolver
            val descriptor = resolver.openFileDescriptor(uri, "r")
                ?: error("Could not open the selected file.")
            try {
                val archive = Archive(descriptor.fileDescriptor)
                val name = queryDisplayName(resolver, uri) ?: "Offline library"
                return ZimSession(descriptor, archive, name)
            } catch (error: Throwable) {
                descriptor.close()
                throw error
            }
        }

        private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
    }
}

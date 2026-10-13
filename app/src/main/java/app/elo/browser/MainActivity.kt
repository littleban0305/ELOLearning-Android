package app.elo.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal val Ink = Color(0xFF17352B)
internal val Leaf = Color(0xFF176B4A)
internal val Paper = Color(0xFFF4F7F1)
internal val Muted = Color(0xFF68776F)

private data class SearchOutcome(val results: List<ArticleResult>, val message: String? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ELOBrowser() }
    }
}

@Composable
private fun ELOBrowser() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("elo-browser", android.content.Context.MODE_PRIVATE) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SearchCategory.ALL) }
    var results by remember { mutableStateOf<List<ArticleResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var article by remember { mutableStateOf<ArticleResult?>(null) }
    var selectedSkin by remember { mutableStateOf<ArticleResult?>(null) }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember { mutableStateOf(preferences.getString("server_url", null)) }
    var serverOpen by remember { mutableStateOf(false) }
    var onboarded by remember { mutableStateOf(preferences.getBoolean("onboarded", false)) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(query, serverUrl, category, article, selectedSkin, serverOpen) {
        val activeServerUrl = serverUrl
        val term = query.trim()
        if (term.isBlank() || category == SearchCategory.AI || article != null || selectedSkin != null ||
            serverOpen || looksLikeUrl(term)
        ) {
            results = emptyList()
            searching = false
            error = null
            return@LaunchedEffect
        }
        delay(280)
        searching = true
        error = null
        val outcome = withContext(Dispatchers.IO) {
            when (category) {
                SearchCategory.AI -> SearchOutcome(emptyList())
                SearchCategory.CS -> runCatching { SkinCatalog.search(term) }
                    .fold({ SearchOutcome(it) }, { SearchOutcome(emptyList(), it.message ?: "CS 圖鑑搜尋失敗。") })
                SearchCategory.WIKIPEDIA -> {
                    if (activeServerUrl.isNullOrBlank()) {
                        SearchOutcome(emptyList(), "請先設定舊手機圖書館伺服器。")
                    } else {
                        runCatching { LibraryServer.search(activeServerUrl, term, wikipediaOnly = true) }
                            .fold({ SearchOutcome(it) }, { SearchOutcome(emptyList(), it.message ?: "維基百科搜尋失敗。") })
                    }
                }
                SearchCategory.ALL -> coroutineScope {
                    val wikiRequest = async {
                        if (activeServerUrl.isNullOrBlank()) Result.success(emptyList())
                        else runCatching { LibraryServer.search(activeServerUrl, term) }
                    }
                    val skinRequest = async { runCatching { SkinCatalog.search(term) } }
                    val wiki = wikiRequest.await()
                    val skins = skinRequest.await()
                    val messages = listOfNotNull(
                        wiki.exceptionOrNull()?.message,
                        skins.exceptionOrNull()?.message,
                        if (activeServerUrl.isNullOrBlank()) "尚未設定圖書館伺服器；目前只搜尋 CS 圖鑑。" else null
                    )
                    SearchOutcome(wiki.getOrDefault(emptyList()) + skins.getOrDefault(emptyList()), messages.joinToString("\n").ifBlank { null })
                }
            }
        }
        results = outcome.results
        error = outcome.message
        searching = false
    }

    fun submit() {
        val text = query.trim()
        if (text.isEmpty()) return
        keyboard?.hide()
        if (looksLikeUrl(text)) {
            browserUrl = normalizeUrl(text)
            query = ""
        } else if (serverUrl.isNullOrBlank()) {
            error = "請先點左下角的 + 設定舊手機圖書館伺服器。"
        }
    }

    BackHandler(article != null || selectedSkin != null) {
        article = null
        selectedSkin = null
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
            if (!onboarded) {
                OnboardingScreen(onStart = {
                    preferences.edit().putBoolean("onboarded", true).apply()
                    onboarded = true
                })
            } else if (serverOpen) {
                WebBrowserScreen(
                    initialUrl = serverUrl.orEmpty(),
                    autoLoad = serverUrl != null,
                    isLibraryServer = true,
                    onAddressLoaded = {
                        preferences.edit().putString("server_url", it).apply()
                        serverUrl = it
                    },
                    onBack = { serverOpen = false }
                )
            } else if (browserUrl != null) {
                WebBrowserScreen(initialUrl = browserUrl!!, autoLoad = true, onBack = { browserUrl = null })
            } else if (selectedSkin != null) {
                SkinDetailsScreen(skin = selectedSkin!!, onBack = { selectedSkin = null })
            } else if (article != null) {
                RemoteArticleScreen(article = article!!, onBack = { article = null })
            } else {
                HomeScreen(
                    serverUrl = serverUrl,
                    query = query,
                    category = category,
                    results = results,
                    searching = searching,
                    error = error,
                    onQueryChange = {
                        query = it
                        error = null
                    },
                    onCategoryChange = {
                        category = it
                        error = null
                    },
                    onSubmit = { submit() },
                    onOpenServer = { serverOpen = true },
                    onOpenArticle = {
                        if (it.imageUrl != null) selectedSkin = it else article = it
                        keyboard?.hide()
                    }
                )
            }
        }
    }
}

@Composable
private fun WebBrowserScreen(
    initialUrl: String,
    onBack: () -> Unit,
    autoLoad: Boolean = false,
    isLibraryServer: Boolean = false,
    onAddressLoaded: (String) -> Unit = {}
) {
    var address by remember(initialUrl) { mutableStateOf(initialUrl) }
    var loadedAddress by remember(initialUrl) { mutableStateOf<String?>(if (autoLoad) initialUrl else null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun openAddress() {
        val typed = address.trim()
        if (typed.isBlank()) return
        val candidate = if (typed.startsWith("http://", ignoreCase = true) || typed.startsWith("https://", ignoreCase = true)) {
            typed
        } else {
            "https://$typed"
        }
        val uri = Uri.parse(candidate)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            addressError = "Enter a valid web address."
            return
        }
        addressError = null
        address = candidate
        loadedAddress = candidate
        onAddressLoaded(candidate)
    }

    fun goBackOrClose() {
        if (webView?.canGoBack() == true) webView?.goBack() else onBack()
    }
    BackHandler(onBack = { goBackOrClose() })

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Paper).padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { goBackOrClose() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Leaf)
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(if (isLibraryServer) "http://舊手機IP:8080" else "Enter a web address")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { openAddress() }),
                shape = RoundedCornerShape(12.dp)
            )
            IconButton(onClick = { openAddress() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go", tint = Leaf)
            }
        }
        if (addressError != null) {
            Text(addressError!!, color = Color(0xFF9B352D), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        if (isLibraryServer && loadedAddress == null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "輸入舊手機上的 Kiwix 伺服器網址，然後按右側箭頭連線。\n例如：http://192.168.1.25:8080",
                    color = Muted,
                    fontSize = 15.sp,
                    lineHeight = 23.sp
                )
            }
        } else {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                    }
                },
                update = { view ->
                    loadedAddress?.let { target ->
                        if (view.url != target) view.loadUrl(target)
                    }
                }
            )
        }
    }
}

@Composable
internal fun ResultRow(result: ArticleResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (result.imageUrl != null) {
                AsyncImage(
                    model = result.imageUrl,
                    contentDescription = result.title,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (result.snippet.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        result.snippet.replace(Regex("<[^>]*>"), ""),
                        color = Muted,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SkinDetailsScreen(skin: ArticleResult, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Paper).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Leaf)
            }
            Text(skin.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            if (skin.imageUrl != null) {
                AsyncImage(
                    model = skin.imageUrl,
                    contentDescription = skin.title,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(skin.title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            val description = Html.fromHtml(skin.snippet, Html.FROM_HTML_MODE_COMPACT).toString().trim()
            if (description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(description, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun RemoteArticleScreen(article: ArticleResult, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Paper)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Leaf)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(article.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("舊手機圖書館", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    loadUrl(article.path)
                }
            },
            update = { webView ->
                if (webView.url != article.path) webView.loadUrl(article.path)
            }
        )
    }
}

@Composable
private fun ArticleScreen(session: ZimSession, article: ArticleResult, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Paper)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Leaf)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(article.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(session.fileName, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?) =
                            request?.url?.let(session::intercept)
                    }
                    loadUrl(session.articleUrl(article.path))
                }
            },
            update = { webView ->
                if (webView.url != session.articleUrl(article.path)) {
                    webView.loadUrl(session.articleUrl(article.path))
                }
            }
        )
    }
}

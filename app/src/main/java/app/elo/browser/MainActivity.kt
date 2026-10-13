package app.elo.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF17352B)
private val Leaf = Color(0xFF176B4A)
private val Paper = Color(0xFFF4F7F1)
private val Muted = Color(0xFF68776F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ELOBrowser() }
    }
}

@Composable
private fun ELOBrowser() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("elo-browser", android.content.Context.MODE_PRIVATE) }
    var selectedUri by remember { mutableStateOf(preferences.getString("zim_uri", null)?.let { Uri.parse(it) }) }
    var session by remember { mutableStateOf<ZimSession?>(null) }
    var opening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SearchMode.TITLE) }
    var results by remember { mutableStateOf<List<ArticleResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var article by remember { mutableStateOf<ArticleResult?>(null) }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    val currentSession by rememberUpdatedState(session)
    val keyboard = LocalSoftwareKeyboardController.current

    val zimPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            preferences.edit().putString("zim_uri", uri.toString()).apply()
            selectedUri = uri
            query = ""
            results = emptyList()
            article = null
            error = null
        }
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        opening = true
        error = null
        withContext(Dispatchers.IO) { session?.close() }
        session = null
        val opened = runCatching { withContext(Dispatchers.IO) { ZimSession.open(context, uri) } }
        if (opened.isSuccess) {
            session = opened.getOrThrow()
        } else {
            error = opened.exceptionOrNull()?.message ?: "Could not open this ZIM file."
        }
        opening = false
    }

    LaunchedEffect(query, mode, session) {
        val activeSession = session
        if (query.isBlank() || activeSession == null || article != null) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(280)
        searching = true
        error = null
        val found = runCatching {
            withContext(Dispatchers.IO) { activeSession.search(query.trim(), mode) }
        }
        found.onSuccess { results = it }
            .onFailure { error = it.message ?: "Search failed."; results = emptyList() }
        searching = false
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { currentSession?.close() }
    }

    BackHandler(article != null) { article = null }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
            if (browserUrl != null) {
                WebBrowserScreen(initialUrl = browserUrl!!, onBack = { browserUrl = null })
            } else if (article != null && session != null) {
                ArticleScreen(
                    session = session!!,
                    article = article!!,
                    onBack = { article = null }
                )
            } else {
                HomeScreen(
                    session = session,
                    opening = opening,
                    query = query,
                    mode = mode,
                    results = results,
                    searching = searching,
                    error = error,
                    onOpenWeb = { browserUrl = "https://" },
                    onQueryChange = { query = it },
                    onModeChange = { mode = it },
                    onChooseZim = { zimPicker.launch(arrayOf("*/*")) },
                    onOpenArticle = { article = it; keyboard?.hide() }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    session: ZimSession?,
    opening: Boolean,
    query: String,
    mode: SearchMode,
    results: List<ArticleResult>,
    searching: Boolean,
    error: String?,
    onOpenWeb: () -> Unit,
    onQueryChange: (String) -> Unit,
    onModeChange: (SearchMode) -> Unit,
    onChooseZim: () -> Unit,
    onOpenArticle: (ArticleResult) -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Scaffold(containerColor = Paper) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(42.dp)
                        .background(Leaf, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Book, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("ELOBrowser", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("OFFLINE LIBRARY", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onChooseZim) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Choose ZIM file", tint = Leaf)
                }
                IconButton(onClick = onOpenWeb) {
                    Icon(Icons.Default.Language, contentDescription = "Open a website", tint = Leaf)
                }
            }

            if (session == null) {
                WelcomePanel(
                    modifier = Modifier.weight(1f),
                    opening = opening,
                    error = error,
                    onChooseZim = onChooseZim
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(session.fileName, color = Muted, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = onChooseZim) { Text("Change", color = Leaf) }
                }
                Spacer(Modifier.height(17.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SearchMode.entries.forEachIndexed { index, searchMode ->
                        val selected = mode == searchMode
                        SegmentedButton(
                            selected = selected,
                            onClick = { onModeChange(searchMode) },
                            shape = SegmentedButtonDefaults.itemShape(index, SearchMode.entries.size)
                        ) {
                            Text(if (searchMode == SearchMode.TITLE) "Title" else "Full text")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(if (mode == SearchMode.TITLE) "Search article titles" else "Search inside articles") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(12.dp))
                when {
                    mode == SearchMode.FULL_TEXT && !session.hasFullTextIndex ->
                        Text("This ZIM has no full-text index. Title search is available.", color = Muted, fontSize = 13.sp)
                    error != null -> Text(error, color = Color(0xFF9B352D), fontSize = 13.sp)
                    searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = Leaf)
                        Spacer(Modifier.width(10.dp))
                        Text("Searching this archive", color = Muted, fontSize = 13.sp)
                    }
                    query.isNotBlank() && results.isEmpty() -> Text("No matches", color = Muted, fontSize = 13.sp)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.path }) { result ->
                        ResultRow(result = result, onClick = { onOpenArticle(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePanel(
    modifier: Modifier,
    opening: Boolean,
    error: String?,
    onChooseZim: () -> Unit
) {
    Column(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Your library,\noffline.", color = Ink, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Open a ZIM archive from this device to search and read it without a connection.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = Color(0xFF9B352D), fontSize = 13.sp)
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onChooseZim,
            enabled = !opening,
            colors = ButtonDefaults.buttonColors(containerColor = Leaf),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (opening) CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (opening) "Opening archive" else "Choose a ZIM file")
        }
        Spacer(Modifier.height(70.dp))
    }
}

@Composable
private fun WebBrowserScreen(initialUrl: String, onBack: () -> Unit) {
    var address by remember(initialUrl) { mutableStateOf(initialUrl) }
    var loadedAddress by remember(initialUrl) { mutableStateOf<String?>(null) }
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
                placeholder = { Text("Enter a web address") },
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

@Composable
private fun ResultRow(result: ArticleResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
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

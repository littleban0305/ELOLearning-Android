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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val Ink = Color(0xFF17352B)
internal val Leaf = Color(0xFF176B4A)
internal val Paper = Color(0xFFF4F7F1)
internal val Muted = Color(0xFF68776F)
private const val GEMINI_KEY_URL = "https://aistudio.google.com/app/apikey"
private const val QWEN_MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF"

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
    var submittedQuery by remember { mutableStateOf("") }
    var submittedSearchCategory by remember { mutableStateOf(SearchCategory.ALL) }
    var searchRevision by remember { mutableStateOf(0) }
    var category by remember { mutableStateOf(SearchCategory.ALL) }
    var aiServerUrl by remember { mutableStateOf(preferences.getString("ai_server_url", "").orEmpty()) }
    var geminiApiKey by remember { mutableStateOf(GeminiKeyStore.read(context)) }
    var aiMessages by remember { mutableStateOf(emptyList<ChatMessage>()) }
    var aiFollowUpDraft by remember { mutableStateOf("") }
    var pendingPhoneAction by remember { mutableStateOf<PendingPhoneAction?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var localAiUnavailable by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ArticleResult>>(emptyList()) }
    var aiSummary by remember { mutableStateOf<String?>(null) }
    var aiSummaryError by remember { mutableStateOf<String?>(null) }
    var aiSummaryLoading by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var article by remember { mutableStateOf<ArticleResult?>(null) }
    var selectedSkin by remember { mutableStateOf<ArticleResult?>(null) }
    var selectedSkinGroup by remember { mutableStateOf<SkinGroup?>(null) }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember { mutableStateOf(preferences.getString("server_url", null)) }
    var serverOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var setupComplete by remember { mutableStateOf(preferences.getBoolean("setup_complete", false)) }
    val keyboard = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val wifiWithInternet = rememberValidatedWifi()

    suspend fun requestAiReply(messages: List<ChatMessage>): String {
        if (wifiWithInternet) {
            check(geminiApiKey.isNotBlank()) { "請先在設定中填入 Gemini API key。" }
            return GeminiClient.reply(geminiApiKey, messages)
        }
        if (aiServerUrl.isBlank()) {
            localAiUnavailable = true
            throw IllegalStateException("目前不是可用 Wi-Fi，且尚未設定本機 Qwen 服務。")
        }
        return runCatching { QwenClient.reply(aiServerUrl, messages) }
            .onSuccess { localAiUnavailable = false }
            .onFailure { localAiUnavailable = true }
            .getOrThrow()
    }

    LaunchedEffect(searchRevision) {
        val activeServerUrl = serverUrl
        val term = submittedQuery.trim()
        if (searchRevision == 0 || term.isBlank() || looksLikeUrl(term)) {
            results = emptyList()
            searching = false
            error = null
            return@LaunchedEffect
        }
        results = emptyList()
        aiSummary = null
        aiSummaryError = null
        searching = true
        delay(280)
        error = null
        val outcome = withContext(Dispatchers.IO) {
            when (submittedSearchCategory) {
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

    LaunchedEffect(searchRevision, aiServerUrl, geminiApiKey, wifiWithInternet, results, searching) {
        val term = submittedQuery.trim()
        if (submittedSearchCategory != SearchCategory.ALL || term.isBlank() || looksLikeUrl(term)) {
            aiSummary = null
            aiSummaryError = null
            aiSummaryLoading = false
            return@LaunchedEffect
        }
        if (wifiWithInternet && geminiApiKey.isBlank()) {
            aiSummary = null
            aiSummaryError = "請先在設定中填入 Gemini API key。"
            aiSummaryLoading = false
            return@LaunchedEffect
        }
        if (!wifiWithInternet && aiServerUrl.isBlank()) {
            aiSummary = null
            aiSummaryError = "目前無法連線本機 AI；請設定 Qwen 服務或下載模型。"
            localAiUnavailable = true
            aiSummaryLoading = false
            return@LaunchedEffect
        }
        if (searching || results.isEmpty()) {
            aiSummary = null
            aiSummaryLoading = false
            return@LaunchedEffect
        }

        delay(500)
        aiSummaryLoading = true
        aiSummaryError = null
        val sourceResults = results.filter { !it.isCatalogItem }.take(4) +
            results.filter { it.isCatalogItem }.take(4)
        val sourceText = sourceResults.joinToString("\n") { result ->
            "標題：${result.title}\n摘要：${result.snippet.take(500)}"
        }
        runCatching {
            requestAiReply(
                listOf(
                    ChatMessage("system", "請使用繁體中文，依據提供的搜尋結果寫出簡潔摘要；不要加入來源中沒有的事實。"),
                    ChatMessage("user", "搜尋關鍵字：$term\n\n搜尋結果：\n$sourceText")
                )
            )
        }.onSuccess { aiSummary = it }
            .onFailure {
                localAiUnavailable = !wifiWithInternet
                aiSummaryError = it.message ?: "AI 摘要產生失敗。"
            }
        aiSummaryLoading = false
    }

    fun submit() {
        val text = query.trim()
        if (text.isEmpty()) return
        keyboard?.hide()
        if (category != SearchCategory.AI && text.equals("設定", ignoreCase = true)) {
            settingsOpen = true
            return
        }
        if (category == SearchCategory.AI) {
            category = SearchCategory.ALL
            submittedQuery = text
            submittedSearchCategory = SearchCategory.ALL
            searchRevision++
            error = null
            return
        }
        if (looksLikeUrl(text)) {
            browserUrl = normalizeUrl(text)
            query = ""
        } else {
            submittedQuery = text
            submittedSearchCategory = SearchCategory.ALL
            searchRevision++
            error = null
        }
    }

    fun sendAiFollowUp() {
        val text = aiFollowUpDraft.trim()
        if (text.isEmpty() || aiBusy) return

        val pendingAction = pendingPhoneAction
        if (pendingAction != null) {
            val reply = when {
                PhoneActions.isConfirmation(text) -> {
                    pendingPhoneAction = null
                    runCatching {
                        context.startActivity(pendingAction.intent)
                        pendingAction.completedMessage
                    }.getOrElse { "無法開啟系統操作畫面：${it.message.orEmpty()}" }
                }
                PhoneActions.isCancellation(text) -> {
                    pendingPhoneAction = null
                    "已取消${pendingAction.confirmation}。"
                }
                else -> "請回覆「可以」確認${pendingAction.confirmation}，或回覆「取消」。"
            }
            aiMessages = aiMessages + ChatMessage("user", text) + ChatMessage("assistant", reply)
            aiFollowUpDraft = ""
            return
        }

        val phoneProposal = PhoneActions.parse(text)
        if (phoneProposal != null) {
            aiMessages = aiMessages + ChatMessage("user", text) + ChatMessage("assistant", phoneProposal.reply)
            pendingPhoneAction = phoneProposal.pendingAction
            aiFollowUpDraft = ""
            return
        }

        if (PhoneActions.isGreeting(text)) {
            aiMessages = aiMessages + ChatMessage("user", text) +
                ChatMessage("assistant", PhoneActions.smallTalkReply(text))
            aiFollowUpDraft = ""
            return
        }

        if (wifiWithInternet && geminiApiKey.isBlank()) {
            error = "請先設定 Gemini API key。"
            settingsOpen = true
            return
        }
        if (!wifiWithInternet && aiServerUrl.isBlank()) {
            localAiUnavailable = true
            error = "找不到本機 Qwen 服務。請設定本機服務或下載模型。"
            return
        }
        val conversation = aiMessages + ChatMessage("user", text)
        aiMessages = conversation
        aiFollowUpDraft = ""
        error = null
        aiBusy = true
        coroutineScope.launch {
            runCatching {
                val lookup = withContext(Dispatchers.IO) {
                    val wiki = if (serverUrl.isNullOrBlank()) {
                        Result.success(emptyList())
                    } else {
                        runCatching { LibraryServer.search(serverUrl!!, text) }
                    }
                    val skins = runCatching { SkinCatalog.search(text) }
                    val found = wiki.getOrDefault(emptyList()) + skins.getOrDefault(emptyList())
                    SearchOutcome(
                        results = found,
                        message = listOf(wiki.exceptionOrNull()?.message, skins.exceptionOrNull()?.message)
                            .filterNotNull()
                            .joinToString("\n")
                            .ifBlank { null }
                    )
                }
                results = lookup.results
                if (lookup.results.isEmpty()) {
                    "我在維基百科與 CS 圖鑑中沒有找到相關資料。你可以換個關鍵字試試。"
                } else {
                    val sourceText = lookup.results.take(12).joinToString("\n") { result ->
                        "標題：${result.title}\n資料：${result.snippet.take(500)}"
                    }
                    requestAiReply(
                        listOf(
                            ChatMessage("system", "你是搜尋助理。只能根據提供的維基百科與 CS 圖鑑搜尋結果，用繁體中文自然回答；不可補充來源以外的知識。若資料不足，請明確說明。"),
                            ChatMessage("user", "問題：$text\n\n搜尋結果：\n$sourceText")
                        )
                    )
                }
            }
                .onSuccess { reply -> aiMessages = aiMessages + ChatMessage("assistant", reply) }
                .onFailure { failure ->
                    localAiUnavailable = !wifiWithInternet
                    error = failure.message ?: if (wifiWithInternet) "無法連線到 Gemini。" else "無法連線到本機 Qwen。"
                    aiFollowUpDraft = text
                }
            aiBusy = false
        }
    }

    BackHandler(article != null || selectedSkin != null || selectedSkinGroup != null) {
        article = null
        selectedSkin = null
        selectedSkinGroup = null
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
            if (!setupComplete) {
                OnboardingScreen(
                    libraryUrl = serverUrl.orEmpty(),
                    qwenUrl = aiServerUrl,
                    geminiApiKey = geminiApiKey,
                    onOpenGeminiKey = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_KEY_URL)))
                    },
                    onSave = { library, qwen, key ->
                        preferences.edit()
                            .putString("server_url", library.trim().trimEnd('/'))
                            .putString("ai_server_url", qwen.trim().trimEnd('/'))
                            .putBoolean("setup_complete", true)
                            .apply()
                        GeminiKeyStore.write(context, key.trim())
                        serverUrl = library.trim().trimEnd('/')
                        aiServerUrl = qwen.trim().trimEnd('/')
                        geminiApiKey = key.trim()
                        setupComplete = true
                    }
                )
            } else if (settingsOpen) {
                AppSettingsScreen(
                    libraryUrl = serverUrl.orEmpty(),
                    qwenUrl = aiServerUrl,
                    geminiApiKey = geminiApiKey,
                    onOpenGeminiKey = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_KEY_URL)))
                    },
                    onBack = { settingsOpen = false },
                    onSave = { library, qwen, key ->
                        preferences.edit()
                            .putString("server_url", library.takeIf(String::isNotBlank))
                            .putString("ai_server_url", qwen.takeIf(String::isNotBlank))
                            .apply()
                        GeminiKeyStore.write(context, key)
                        serverUrl = library.takeIf(String::isNotBlank)
                        aiServerUrl = qwen
                        geminiApiKey = key
                        settingsOpen = false
                    }
                )
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
            } else if (selectedSkinGroup != null) {
                SkinWearSelectionScreen(
                    weaponName = selectedSkinGroup!!.weaponName,
                    variants = selectedSkinGroup!!.variants,
                    onBack = { selectedSkinGroup = null },
                    onSelectWear = {
                        selectedSkin = it
                        selectedSkinGroup = null
                    }
                )
            } else if (article != null) {
                RemoteArticleScreen(
                    article = article!!,
                    onBack = { article = null },
                    onTitleChanged = { title ->
                        if (title.isNotBlank()) article = article?.copy(title = title)
                    }
                )
            } else {
                HomeScreen(
                    serverUrl = serverUrl,
                    aiServerUrl = aiServerUrl,
                    geminiConfigured = geminiApiKey.isNotBlank(),
                    usingGemini = wifiWithInternet,
                    localAiUnavailable = localAiUnavailable,
                    query = query,
                    submittedQuery = submittedQuery,
                    searchRevision = searchRevision,
                    category = category,
                    aiSummary = aiSummary,
                    aiSummaryError = aiSummaryError,
                    aiSummaryLoading = aiSummaryLoading,
                    aiMessages = aiMessages,
                    aiFollowUpDraft = aiFollowUpDraft,
                    aiBusy = aiBusy,
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
                    onOpenSettings = { settingsOpen = true },
                    onAiFollowUpChange = { aiFollowUpDraft = it },
                    onSendAiFollowUp = { sendAiFollowUp() },
                    onDownloadLocalModel = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(QWEN_MODEL_URL)))
                    },
                    onFollowUp = {
                        category = SearchCategory.AI
                        query = submittedQuery
                        if (aiMessages.isEmpty() && !aiSummary.isNullOrBlank()) {
                            aiMessages = listOf(ChatMessage("assistant", aiSummary!!))
                        }
                        error = null
                    },
                    onSubmit = { submit() },
                    onOpenServer = { serverOpen = true },
                    onOpenArticle = {
                        if (it.isCatalogItem) selectedSkin = it else article = it
                        keyboard?.hide()
                    },
                    onOpenSkinGroup = { group -> selectedSkinGroup = group }
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
private fun AppSettingsScreen(
    libraryUrl: String,
    qwenUrl: String,
    geminiApiKey: String,
    onOpenGeminiKey: () -> Unit,
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var libraryDraft by remember(libraryUrl) { mutableStateOf(libraryUrl) }
    var qwenDraft by remember(qwenUrl) { mutableStateOf(qwenUrl) }
    var apiKeyDraft by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Paper).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Leaf)
            }
            Text("設定", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Gemini API key") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        TextButton(onClick = onOpenGeminiKey) { Text("前往 Google AI Studio 取得 API key") }
        OutlinedTextField(
            value = libraryDraft,
            onValueChange = { libraryDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Kiwix 圖書館網址") },
            placeholder = { Text("http://192.168.1.25:8080") }
        )
        OutlinedTextField(
            value = qwenDraft,
            onValueChange = { qwenDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Qwen API 網址") },
            placeholder = { Text("http://192.168.1.25:8082") }
        )
        if (validationError != null) {
            Text(validationError!!, color = Color(0xFF9B352D), fontSize = 13.sp)
        }
        Button(
            onClick = {
                val invalidLibrary = libraryDraft.isNotBlank() && !isValidHttpUrl(libraryDraft)
                val invalidQwen = qwenDraft.isNotBlank() && !isValidHttpUrl(qwenDraft)
                if (invalidLibrary || invalidQwen) {
                    validationError = "網址需以 http:// 或 https:// 開頭，並包含主機 IP 或網域。"
                } else {
                    validationError = null
                    onSave(libraryDraft.trim().trimEnd('/'), qwenDraft.trim().trimEnd('/'), apiKeyDraft.trim())
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("儲存設定")
        }
    }
}

private fun isValidHttpUrl(value: String): Boolean {
    val uri = Uri.parse(value.trim())
    return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}

@Composable
internal fun ResultRow(result: ArticleResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
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
            Text(result.title, color = Leaf, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (result.wearName != null) {
                Text("磨損度：${result.wearName}", color = Muted, fontSize = 12.sp)
            }
            if (result.snippet.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    result.snippet.replace(Regex("<[^>]*>"), ""),
                    color = Muted,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
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
            skin.wearName?.let { Text("磨損度：$it", color = Leaf, fontSize = 14.sp) }
            val description = Html.fromHtml(skin.snippet, Html.FROM_HTML_MODE_COMPACT).toString().trim()
            if (description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(description, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun SkinWearSelectionScreen(
    weaponName: String,
    variants: List<ArticleResult>,
    onBack: () -> Unit,
    onSelectWear: (ArticleResult) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Paper).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Leaf)
            }
            Text(weaponName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(variants, key = { it.path }) { variant ->
                ResultRow(result = variant, onClick = { onSelectWear(variant) })
            }
        }
    }
}

@Composable
private fun RemoteArticleScreen(article: ArticleResult, onBack: () -> Unit, onTitleChanged: (String) -> Unit) {
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
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            title?.takeIf(String::isNotBlank)?.let(onTitleChanged)
                        }
                    }
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

package app.elo.browser

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val ErrorRed = Color(0xFF9B352D)

internal fun looksLikeUrl(text: String): Boolean {
    if (text.contains(' ')) return false
    if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) return true
    return text.contains('.') && !text.startsWith('.') && !text.endsWith('.')
}

internal fun normalizeUrl(text: String): String =
    if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) text
    else "https://$text"

@Composable
internal fun OnboardingScreen(
    libraryUrl: String,
    qwenUrl: String,
    geminiApiKey: String,
    onOpenGeminiKey: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var libraryDraft by remember(libraryUrl) { mutableStateOf(libraryUrl) }
    var qwenDraft by remember(qwenUrl) { mutableStateOf(qwenUrl) }
    var apiKeyDraft by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
    var validationError by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ELOBrowser", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("開始前設定圖書館與 AI", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
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
            label = { Text("Kiwix／維基百科伺服器網址") },
            placeholder = { Text("http://192.168.1.25:8080") }
        )
        OutlinedTextField(
            value = qwenDraft,
            onValueChange = { qwenDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("本機 Qwen API 網址（選填）") },
            placeholder = { Text("http://192.168.1.25:8082") }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "只有已連線的 Wi-Fi 會使用 Gemini；查詢內容會傳給 Google。行動數據或離線時只使用本機 Qwen。",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        if (validationError != null) {
            Spacer(Modifier.height(8.dp))
            Text(validationError!!, color = ErrorRed, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                when {
                    apiKeyDraft.isBlank() -> validationError = "請先填入 Gemini API key。"
                    !isHttpUrl(libraryDraft) -> validationError = "請填入有效的 Kiwix 伺服器網址。"
                    qwenDraft.isNotBlank() && !isHttpUrl(qwenDraft) -> validationError = "本機 Qwen 網址格式無效。"
                    else -> {
                        validationError = null
                        onSave(libraryDraft.trim().trimEnd('/'), qwenDraft.trim().trimEnd('/'), apiKeyDraft.trim())
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Leaf),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("儲存並開始", fontSize = 16.sp)
        }
    }
}

private fun isHttpUrl(value: String): Boolean {
    val uri = Uri.parse(value.trim())
    return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}

@Composable
private fun PermissionRow(title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Leaf)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
internal fun HomeScreen(
    serverUrl: String?,
    aiServerUrl: String,
    geminiConfigured: Boolean,
    usingGemini: Boolean,
    localAiUnavailable: Boolean,
    query: String,
    submittedQuery: String,
    category: SearchCategory,
    aiSummary: String?,
    aiSummaryError: String?,
    aiSummaryLoading: Boolean,
    aiMessages: List<ChatMessage>,
    aiFollowUpDraft: String,
    aiBusy: Boolean,
    results: List<ArticleResult>,
    searching: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (SearchCategory) -> Unit,
    onOpenSettings: () -> Unit,
    onAiFollowUpChange: (String) -> Unit,
    onSendAiFollowUp: () -> Unit,
    onDownloadLocalModel: () -> Unit,
    onFollowUp: () -> Unit,
    onSubmit: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenArticle: (ArticleResult) -> Unit,
    onOpenSkinGroup: (String, List<ArticleResult>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        AnimatedContent(
            targetState = submittedQuery.isNotBlank(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 12 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 12 })
            },
            label = "search home transition"
        ) { hasSearched ->
            if (hasSearched) {
                Column(modifier = Modifier.fillMaxSize()) {
                    BrandTitle(Modifier.padding(top = 4.dp, bottom = 10.dp))
                    CommandInput(
                        query = query,
                        category = category,
                        usingGemini = usingGemini,
                            sendEnabled = query.isNotBlank(),
                        onQueryChange = onQueryChange,
                        onSubmit = onSubmit,
                        onOpenServer = onOpenServer,
                        onOpenSettings = onOpenSettings
                    )
                    Spacer(Modifier.height(8.dp))
                    SearchResults(
                        serverConfigured = !serverUrl.isNullOrBlank(),
                        aiServerUrl = aiServerUrl,
                        geminiConfigured = geminiConfigured,
                        usingGemini = usingGemini,
                        localAiUnavailable = localAiUnavailable,
                        query = submittedQuery,
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
                        onCategoryChange = onCategoryChange,
                        onOpenSettings = onOpenSettings,
                        onAiFollowUpChange = onAiFollowUpChange,
                        onSendAiFollowUp = onSendAiFollowUp,
                        onDownloadLocalModel = onDownloadLocalModel,
                        onFollowUp = onFollowUp,
                        onOpenArticle = onOpenArticle,
                        onOpenSkinGroup = onOpenSkinGroup
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BrandTitle()
                        Spacer(Modifier.height(18.dp))
                        CommandInput(
                            query = query,
                            category = category,
                            usingGemini = usingGemini,
                                sendEnabled = query.isNotBlank(),
                            onQueryChange = onQueryChange,
                            onSubmit = onSubmit,
                            onOpenServer = onOpenServer,
                            onOpenSettings = onOpenSettings
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandTitle(modifier: Modifier = Modifier) {
    Text(
        "ELOBrowser",
        color = Ink,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun SearchResults(
    serverConfigured: Boolean,
    aiServerUrl: String,
    geminiConfigured: Boolean,
    usingGemini: Boolean,
    localAiUnavailable: Boolean,
    query: String,
    category: SearchCategory,
    aiSummary: String?,
    aiSummaryError: String?,
    aiSummaryLoading: Boolean,
    aiMessages: List<ChatMessage>,
    aiFollowUpDraft: String,
    aiBusy: Boolean,
    results: List<ArticleResult>,
    searching: Boolean,
    error: String?,
    onCategoryChange: (SearchCategory) -> Unit,
    onOpenSettings: () -> Unit,
    onAiFollowUpChange: (String) -> Unit,
    onSendAiFollowUp: () -> Unit,
    onDownloadLocalModel: () -> Unit,
    onFollowUp: () -> Unit,
    onOpenArticle: (ArticleResult) -> Unit,
    onOpenSkinGroup: (String, List<ArticleResult>) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = SearchCategory.entries.indexOf(category),
            containerColor = Paper,
            contentColor = Leaf,
            edgePadding = 0.dp
        ) {
            SearchCategory.entries.forEach { item ->
                Tab(
                    selected = category == item,
                    onClick = { onCategoryChange(item) },
                    selectedContentColor = Leaf,
                    unselectedContentColor = Muted,
                    text = { Text(item.label, maxLines = 1) }
                )
            }
        }
        if (category == SearchCategory.ALL) {
            AllSearchResults(
                query = query,
                results = results,
                summary = aiSummary,
                summaryError = aiSummaryError,
                searchError = error,
                summaryLoading = aiSummaryLoading,
                onDownloadLocalModel = onDownloadLocalModel,
                usingGemini = usingGemini,
                localAiUnavailable = localAiUnavailable,
                onFollowUp = onFollowUp,
                onCategoryChange = onCategoryChange,
                onOpenArticle = onOpenArticle
            )
        } else if (category == SearchCategory.AI) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (usingGemini && !geminiConfigured) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("尚未設定 Gemini API key", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onOpenSettings) { Text("設定 Gemini") }
                            if (error != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(error, color = ErrorRed, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else if (!usingGemini && (aiServerUrl.isBlank() || localAiUnavailable)) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("找不到本機 Qwen 服務", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text("啟動舊手機的 llama-server，或下載 GGUF 模型。", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onDownloadLocalModel) {
                                Text("下載本機 Qwen 模型")
                            }
                            TextButton(onClick = onOpenSettings) { Text("設定本機 Qwen 網址") }
                        }
                    }
                } else {
                    Text(if (usingGemini) "Gemini · Wi-Fi" else "本機 Qwen", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                    if (error != null) {
                        Text(error, color = ErrorRed, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(aiMessages) { message ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = if (message.role == "user") Color(0xFFE3EEE7) else Color.White
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        if (message.role == "user") "你" else if (usingGemini) "Gemini" else "本機 Qwen",
                                        color = Leaf,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(message.content, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                        if (aiBusy) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = Leaf)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Qwen 正在思考…", color = Muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    AiFollowUpInput(
                        value = aiFollowUpDraft,
                        enabled = !aiBusy,
                        usingGemini = usingGemini,
                        onValueChange = onAiFollowUpChange,
                        onSubmit = onSendAiFollowUp
                    )
                }
            }
        } else {
            var page by remember(query, category) { mutableStateOf(0) }
            val weaponGroups = results.filter { it.isCatalogItem }
                .groupBy { it.weaponName ?: "其他武器" }
                .toList()
            val articleResults = results.filter { !it.isCatalogItem }
            val pageCount = when (category) {
                SearchCategory.CS -> ((weaponGroups.size + 19) / 20).coerceAtLeast(1)
                else -> ((articleResults.size + 19) / 20).coerceAtLeast(1)
            }
            val pageWeaponGroups = weaponGroups.drop(page * 20).take(20)
            val pageArticles = articleResults.drop(page * 20).take(20)
            val hasPageItems = if (category == SearchCategory.CS) weaponGroups.isNotEmpty() else articleResults.isNotEmpty()
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    error != null -> Text(error, color = ErrorRed, fontSize = 13.sp)
                    searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = Leaf)
                        Spacer(Modifier.width(10.dp))
                        Text("搜尋中…", color = Muted, fontSize = 13.sp)
                    }
                    category == SearchCategory.WIKIPEDIA && !serverConfigured ->
                        Text("請先設定舊手機圖書館伺服器。", color = Muted, fontSize = 13.sp)
                    query.isNotBlank() && !looksLikeUrl(query.trim()) && !searching && results.isEmpty() ->
                        Text("找不到結果", color = Muted, fontSize = 13.sp)
                    query.isBlank() -> Text("輸入關鍵字開始搜尋", color = Muted, fontSize = 13.sp)
                }
                if (hasPageItems) {
                    PageControls(page = page, pageCount = pageCount, onPageChange = { page = it })
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (category == SearchCategory.CS) {
                        items(pageWeaponGroups, key = { it.first }) { (weapon, variants) ->
                            WeaponGroupButton(weapon, variants) {
                                onOpenSkinGroup(weapon, variants)
                            }
                        }
                    } else {
                        items(pageArticles, key = { it.path }) { result ->
                            ResultRow(result = result, onClick = { onOpenArticle(result) })
                        }
                    }
                }
                if (hasPageItems) {
                    PageControls(page = page, pageCount = pageCount, onPageChange = { page = it })
                }
            }
        }
    }
}

@Composable
private fun AllSearchResults(
    query: String,
    results: List<ArticleResult>,
    summary: String?,
    summaryError: String?,
    searchError: String?,
    summaryLoading: Boolean,
    onDownloadLocalModel: () -> Unit,
    usingGemini: Boolean,
    localAiUnavailable: Boolean,
    onFollowUp: () -> Unit,
    onCategoryChange: (SearchCategory) -> Unit,
    onOpenArticle: (ArticleResult) -> Unit,
    onOpenSkinGroup: (String, List<ArticleResult>) -> Unit
) {
    val wikiResults = results.filter { !it.isCatalogItem }.take(20)
    val skinGroups = results.filter { it.isCatalogItem }
        .groupBy { it.weaponName ?: "其他武器" }
        .toList()
        .take(20)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("AI 摘要", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            when {
                summaryLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = Leaf)
                    Spacer(Modifier.width(8.dp))
                    Text("正在整理搜尋結果…", color = Muted, fontSize = 13.sp)
                }
                summaryError != null -> Text(summaryError, color = ErrorRed, fontSize = 13.sp)
                searchError != null -> Text(searchError, color = ErrorRed, fontSize = 13.sp)
                !summary.isNullOrBlank() -> Text(summary, color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
                query.isBlank() -> Text("搜尋後由目前可用的 AI 整理結果摘要。", color = Muted, fontSize = 13.sp)
            }
            if (!usingGemini && localAiUnavailable) {
                TextButton(onClick = onDownloadLocalModel) {
                    Text("下載本機 Qwen 模型")
                }
            }
        }
        item {
            TextButton(onClick = onFollowUp, enabled = query.isNotBlank()) {
                Text("提出後續查詢")
            }
        }
        item {
            SectionHeading("維基百科", onClick = { onCategoryChange(SearchCategory.WIKIPEDIA) })
        }
        if (wikiResults.isEmpty() && query.isNotBlank()) {
            item { Text("沒有維基百科結果", color = Muted, fontSize = 13.sp) }
        }
        items(wikiResults, key = { it.path }) { result ->
            ResultRow(result = result, onClick = { onOpenArticle(result) })
        }
        if (wikiResults.isNotEmpty()) {
            item { TextButton(onClick = { onCategoryChange(SearchCategory.WIKIPEDIA) }) { Text("查看更多維基百科結果") } }
        }
        item {
            SectionHeading("CS 圖鑑", onClick = { onCategoryChange(SearchCategory.CS) })
        }
        if (skinGroups.isEmpty() && query.isNotBlank()) {
            item { Text("沒有 CS 圖鑑結果", color = Muted, fontSize = 13.sp) }
        }
        items(skinGroups, key = { it.first }) { (weapon, variants) ->
            WeaponGroupButton(weapon, variants) { onOpenSkinGroup(weapon, variants) }
        }
        if (skinGroups.isNotEmpty()) {
            item { TextButton(onClick = { onCategoryChange(SearchCategory.CS) }) { Text("查看更多 CS 圖鑑結果") } }
        }
    }
}

@Composable
private fun SectionHeading(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onClick) { Text("查看更多") }
    }
}

@Composable
private fun WeaponGroupButton(
    weaponName: String,
    variants: List<ArticleResult>,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            variants.firstOrNull()?.imageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = weaponName,
                    modifier = Modifier.width(52.dp).height(52.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(weaponName, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${variants.size} 個磨損／外觀版本", color = Muted, fontSize = 12.sp)
            }
            Text("查看", color = Leaf, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PageControls(page: Int, pageCount: Int, onPageChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        TextButton(onClick = { onPageChange(page - 1) }, enabled = page > 0) { Text("上一頁") }
        Text("${page + 1} / $pageCount", color = Muted, fontSize = 13.sp)
        TextButton(onClick = { onPageChange(page + 1) }, enabled = page + 1 < pageCount) { Text("下一頁") }
    }
}

@Composable
private fun CommandInput(
    query: String,
    category: SearchCategory,
    usingGemini: Boolean,
    sendEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.Add, contentDescription = "更多功能", tint = Leaf)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("設定圖書館伺服器") },
                        leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                        onClick = { menuOpen = false; onOpenServer() }
                    )
                    DropdownMenuItem(
                        text = { Text("設定") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = { menuOpen = false; onOpenSettings() }
                    )
                }
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        when {
                            category != SearchCategory.AI -> "輸入網址或搜尋圖書館"
                            usingGemini -> "輸入訊息給 Gemini"
                            else -> "輸入訊息給本機 Qwen"
                        },
                        color = Muted
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() })
            )
            FilledIconButton(
                onClick = onSubmit,
                enabled = sendEnabled,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Leaf,
                    disabledContainerColor = Color(0xFFD3DBD6)
                )
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "發送", tint = Color.White)
            }
        }
    }
}

@Composable
private fun AiFollowUpInput(
    value: String,
    enabled: Boolean,
    usingGemini: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(if (usingGemini) "向 Gemini 提出後續問題" else "向本機 AI 提出後續問題", color = Muted) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() })
            )
            FilledIconButton(
                onClick = onSubmit,
                enabled = enabled && value.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Leaf)
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "送出後續問題", tint = Color.White)
            }
        }
    }
}

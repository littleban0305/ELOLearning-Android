package app.elo.browser

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
internal fun OnboardingScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .systemBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("ELOBrowser", color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("離線百科與網頁瀏覽，一個輸入框就能開始。", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(32.dp))
        PermissionRow("網路連線", "瀏覽網頁時使用，系統已自動允許，不需要手動授權。")
        Spacer(Modifier.height(16.dp))
        PermissionRow("檔案存取", "載入 ZIM 檔案時，由系統檔案選擇器授權，只會讀取你選的檔案。")
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Leaf),
            shape = RoundedCornerShape(26.dp)
        ) {
            Text("開始使用", fontSize = 16.sp)
        }
    }
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
    query: String,
    category: SearchCategory,
    aiMessages: List<ChatMessage>,
    aiBusy: Boolean,
    results: List<ArticleResult>,
    searching: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (SearchCategory) -> Unit,
    onAiServerUrlChange: (String) -> Unit,
    onSaveAiServer: () -> Unit,
    onSubmit: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenArticle: (ArticleResult) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "ELOBrowser",
            color = Ink,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp)
        )
        Text(
            text = if (serverUrl.isNullOrBlank()) "尚未設定圖書館伺服器" else "圖書館伺服器：$serverUrl",
            color = Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp)
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SearchResults(
                serverConfigured = !serverUrl.isNullOrBlank(),
                aiServerUrl = aiServerUrl,
                query = query,
                category = category,
                aiMessages = aiMessages,
                aiBusy = aiBusy,
                results = results,
                searching = searching,
                error = error,
                onCategoryChange = onCategoryChange,
                onAiServerUrlChange = onAiServerUrlChange,
                onSaveAiServer = onSaveAiServer,
                onOpenArticle = onOpenArticle
            )
        }

        CommandInput(
            query = query,
            category = category,
            sendEnabled = query.isNotBlank() && !(category == SearchCategory.AI && aiBusy),
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            onOpenServer = onOpenServer
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SearchResults(
    serverConfigured: Boolean,
    aiServerUrl: String,
    query: String,
    category: SearchCategory,
    aiMessages: List<ChatMessage>,
    aiBusy: Boolean,
    results: List<ArticleResult>,
    searching: Boolean,
    error: String?,
    onCategoryChange: (SearchCategory) -> Unit,
    onAiServerUrlChange: (String) -> Unit,
    onSaveAiServer: () -> Unit,
    onOpenArticle: (ArticleResult) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = SearchCategory.entries.indexOf(category),
            edgePadding = 0.dp
        ) {
            SearchCategory.entries.forEach { item ->
                Tab(
                    selected = category == item,
                    onClick = { onCategoryChange(item) },
                    text = { Text(item.label, maxLines = 1) }
                )
            }
        }
        if (category == SearchCategory.AI) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = aiServerUrl,
                        onValueChange = onAiServerUrlChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Qwen API 網址") },
                        placeholder = { Text("http://192.168.1.25:8082") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onSaveAiServer, enabled = aiServerUrl.isNotBlank()) {
                        Text("儲存")
                    }
                }
                Text(
                    "填入舊手機的 llama-server 網址，例如 http://192.168.1.25:8082",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                if (error != null) {
                    Text(error, color = ErrorRed, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
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
                                Text(if (message.role == "user") "你" else "Qwen", color = Leaf, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            }
        } else {
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
                    query.isNotBlank() && !looksLikeUrl(query.trim()) && results.isEmpty() ->
                        Text("找不到結果", color = Muted, fontSize = 13.sp)
                    query.isBlank() -> Text("輸入關鍵字開始搜尋", color = Muted, fontSize = 13.sp)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { "${it.imageUrl.orEmpty()}:${it.path}" }) { result ->
                        ResultRow(result = result, onClick = { onOpenArticle(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandInput(
    query: String,
    category: SearchCategory,
    sendEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenServer: () -> Unit
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
                }
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(if (category == SearchCategory.AI) "輸入訊息給 Qwen" else "輸入網址或搜尋圖書館", color = Muted)
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

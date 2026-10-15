package app.elo.browser

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal data class LearningNote(
    val id: Long,
    val title: String,
    val subject: String,
    val content: String,
    val updatedAt: Long
)

private data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
)

private data class LearningQuiz(val subject: String, val questions: List<QuizQuestion>)

private enum class LearningPage(val label: String) {
    STUDY("學習"), NOTES("我的重點"), QUIZ("練習")
}

private object LearningNoteStore {
    private const val KEY = "learning_notes_v1"

    fun load(context: Context): List<LearningNote> = runCatching {
        val raw = context.getSharedPreferences("elo-browser", Context.MODE_PRIVATE).getString(KEY, "[]")
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val note = array.getJSONObject(index)
            LearningNote(
                id = note.getLong("id"),
                title = note.getString("title"),
                subject = note.optString("subject", "其他"),
                content = note.getString("content"),
                updatedAt = note.optLong("updatedAt", 0L)
            )
        }.sortedByDescending(LearningNote::updatedAt)
    }.getOrDefault(emptyList())

    fun save(context: Context, notes: List<LearningNote>) {
        val array = JSONArray()
        notes.forEach { note ->
            array.put(
                JSONObject()
                    .put("id", note.id)
                    .put("title", note.title)
                    .put("subject", note.subject)
                    .put("content", note.content)
                    .put("updatedAt", note.updatedAt)
            )
        }
        context.getSharedPreferences("elo-browser", Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}

@Composable
internal fun LearningScreen(
    aiReady: Boolean,
    onOpenSettings: () -> Unit,
    onOpenBrowser: () -> Unit,
    onGenerate: suspend (String, String, Boolean) -> String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var page by remember { mutableStateOf(LearningPage.STUDY) }
    var notes by remember { mutableStateOf(LearningNoteStore.load(context)) }
    var topic by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("國文") }
    var grade by remember { mutableStateOf("國中一年級") }
    var instruction by remember { mutableStateOf("") }
    var selectedNote by remember { mutableStateOf<LearningNote?>(null) }
    var studyResult by remember { mutableStateOf<LearningNote?>(null) }
    var quiz by remember { mutableStateOf<LearningQuiz?>(null) }
    var answers by remember { mutableStateOf(emptyList<Int>()) }
    var submitted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun saveNote(note: LearningNote) {
        val updated = listOf(note) + notes.filterNot { it.id == note.id }
        notes = updated.sortedByDescending(LearningNote::updatedAt)
        LearningNoteStore.save(context, notes)
    }

    fun generateStudyNote() {
        val cleanTopic = topic.trim()
        if (cleanTopic.isBlank() || busy) return
        error = null
        busy = true
        coroutineScope.launch {
            runCatching {
                onGenerate(
                    "你是台灣的親切學習老師。請使用繁體中文，符合$grade學生程度，使用清楚的小標題與例子。只回答主題範圍內內容，避免捏造。",
                    "科目：$subject\n年級：$grade\n主題：$cleanTopic\n學生需求：${instruction.trim().ifBlank { "請解釋概念並整理成可複習的重點。" }}\n\n請整理成 3 至 6 個學習重點，包含必要的例子或步驟。",
                    false
                )
            }.onSuccess { content ->
                val note = LearningNote(
                    id = System.currentTimeMillis(),
                    title = cleanTopic,
                    subject = subject.trim().ifBlank { "其他" },
                    content = content.trim(),
                    updatedAt = System.currentTimeMillis()
                )
                saveNote(note)
                studyResult = note
            }.onFailure { failure -> error = failure.message ?: "學習內容產生失敗。" }
            busy = false
        }
    }

    fun generateQuiz() {
        if (notes.isEmpty() || busy) return
        error = null
        busy = true
        coroutineScope.launch {
            val source = notes.take(8).joinToString("\n\n") { "【${it.subject}｜${it.title}】\n${it.content.take(1800)}" }
            runCatching {
                onGenerate(
                    "你是出題老師。只能根據提供的學生筆記出題，使用繁體中文。每題四個選項，answer 是從 0 開始的正確選項索引。請只回傳符合要求的 JSON，不要 Markdown。",
                    "根據以下筆記產生 5 題四選一題目。JSON 格式：{\"questions\":[{\"question\":\"題目\",\"options\":[\"選項A\",\"選項B\",\"選項C\",\"選項D\"],\"answer\":0,\"explanation\":\"解析\"}]}\n\n筆記：\n$source",
                    true
                )
            }.onSuccess { raw ->
                runCatching { parseQuiz(raw, notes.first().subject) }
                    .onSuccess { generated ->
                        quiz = generated
                        answers = List(generated.questions.size) { -1 }
                        submitted = false
                    }
                    .onFailure { failure -> error = failure.message ?: "題目格式無法讀取，請重試。" }
            }.onFailure { failure -> error = failure.message ?: "出題失敗。" }
            busy = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Paper).padding(horizontal = 18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("ELOLearning", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("AI 學習助手", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "設定 AI", tint = Leaf)
            }
            IconButton(onClick = onOpenBrowser) {
                Icon(Icons.Default.Language, contentDescription = "開啟瀏覽器", tint = Leaf)
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LearningPage.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = page == item,
                    onClick = {
                        page = item
                        error = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, LearningPage.entries.size)
                ) { Text(item.label) }
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!aiReady) {
                Surface(color = Color(0xFFE7EEE8), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("尚未設定 AI 連線。", color = Ink, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = Leaf)) {
                            Text("設定")
                        }
                    }
                }
            }

            when (page) {
                LearningPage.STUDY -> {
                    Text("今天想學什麼？", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("輸入主題，AI 會整理重點並自動存進你的筆記。", color = Muted, fontSize = 13.sp)
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("學習主題") },
                        placeholder = { Text("例如：一次函數、細胞構造") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = grade,
                            onValueChange = { grade = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("年級") }
                        )
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("科目") }
                        )
                    }
                    OutlinedTextField(
                        value = instruction,
                        onValueChange = { instruction = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        label = { Text("你想問什麼？") },
                        placeholder = { Text("例如：請用例子一步步解釋") }
                    )
                    Button(
                        onClick = ::generateStudyNote,
                        enabled = topic.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("整理學習重點")
                    }
                    studyResult?.let { note -> NoteCard(note = note, onClick = { selectedNote = note; page = LearningPage.NOTES }) }
                }

                LearningPage.NOTES -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("我的重點", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${notes.size} 筆", color = Muted, fontSize = 13.sp)
                    }
                    if (selectedNote == null) {
                        if (notes.isEmpty()) EmptyMessage("還沒有筆記", "完成一次 AI 學習後，重點會自動保存在這裡。")
                        notes.forEach { note -> NoteCard(note = note, onClick = { selectedNote = note }) }
                    } else {
                        val note = selectedNote!!
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${note.subject} · ${note.title}", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                notes = notes.filterNot { it.id == note.id }
                                LearningNoteStore.save(context, notes)
                                selectedNote = null
                            }) { Icon(Icons.Default.Delete, contentDescription = "刪除筆記", tint = Color(0xFF9B352D)) }
                        }
                        androidx.compose.material3.TextButton(onClick = { selectedNote = null }) { Text("返回筆記列表") }
                        NoteBody(note.content)
                    }
                }

                LearningPage.QUIZ -> {
                    Text("筆記快問快答", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("依照你已保存的重點出題，答案會在交卷後揭曉。", color = Muted, fontSize = 13.sp)
                    Button(
                        onClick = ::generateQuiz,
                        enabled = notes.isNotEmpty() && !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("${if (quiz == null) "產生" else "重新產生"} 5 題")
                    }
                    if (notes.isEmpty()) EmptyMessage("需要先有學習筆記", "先整理一個主題，才能從你的重點出題。")
                    quiz?.let { currentQuiz ->
                        Text(currentQuiz.subject, color = Leaf, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        currentQuiz.questions.forEachIndexed { questionIndex, question ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text("${questionIndex + 1}. ${question.question}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    question.options.forEachIndexed { optionIndex, option ->
                                        val isCorrect = submitted && optionIndex == question.answer
                                        val isWrong = submitted && answers.getOrElse(questionIndex) { -1 } == optionIndex && optionIndex != question.answer
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable(enabled = !submitted) {
                                                answers = answers.toMutableList().also { it[questionIndex] = optionIndex }
                                            }.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = answers.getOrElse(questionIndex) { -1 } == optionIndex,
                                                onClick = { if (!submitted) answers = answers.toMutableList().also { it[questionIndex] = optionIndex } },
                                                enabled = !submitted
                                            )
                                            Text(option, color = when { isCorrect -> Leaf; isWrong -> Color(0xFF9B352D); else -> Ink }, fontSize = 14.sp)
                                        }
                                    }
                                    if (submitted) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(question.explanation, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                                    }
                                }
                            }
                        }
                        if (submitted) {
                            val score = currentQuiz.questions.indices.count { answers.getOrElse(it) { -1 } == currentQuiz.questions[it].answer }
                            Text("答對 $score / ${currentQuiz.questions.size} 題", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Button(
                                onClick = { submitted = true },
                                enabled = answers.any { it >= 0 },
                                colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("交卷") }
                        }
                    }
                }
            }

            error?.let { Text(it, color = Color(0xFF9B352D), fontSize = 13.sp, lineHeight = 19.sp) }
        }
    }
}

@Composable
private fun NoteCard(note: LearningNote, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("${note.subject} · ${note.title}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(5.dp))
            Text(note.content, color = Muted, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4)
        }
    }
}

@Composable
private fun NoteBody(content: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(content, modifier = Modifier.padding(16.dp), color = Ink, fontSize = 15.sp, lineHeight = 23.sp)
    }
}

@Composable
private fun EmptyMessage(title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(5.dp))
        Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

private fun parseQuiz(raw: String, defaultSubject: String): LearningQuiz {
    val start = raw.indexOf('{').takeIf { it >= 0 } ?: error("AI 回覆缺少 JSON 物件")
    val end = raw.lastIndexOf('}').takeIf { it >= start } ?: error("AI 回覆 JSON 格式不完整")
    val root = JSONObject(raw.substring(start, end + 1))
    val items = root.optJSONArray("questions") ?: error("AI 回覆缺少 questions")
    val questions = (0 until items.length()).map { index ->
        val item = items.getJSONObject(index)
        val optionsJson = item.getJSONArray("options")
        val options = (0 until optionsJson.length()).map(optionsJson::getString)
        val answer = item.getInt("answer")
        require(options.size == 4 && answer in options.indices) { "題目選項格式不正確" }
        QuizQuestion(
            question = item.getString("question"),
            options = options,
            answer = answer,
            explanation = item.optString("explanation", "")
        )
    }
    require(questions.isNotEmpty()) { "AI 沒有產生題目" }
    return LearningQuiz(root.optString("subject", defaultSubject), questions)
}
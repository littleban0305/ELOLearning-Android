package app.elo.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal object GeminiClient {
    private const val MODEL = "gemini-3.8-flash"

    suspend fun reply(apiKey: String, messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "請先設定 Gemini API key。" }
        val systemInstruction = messages.filter { it.role == "system" }
            .joinToString("\n") { it.content }
        val contents = JSONArray().apply {
            messages.filter { it.role != "system" }.takeLast(12).forEach { message ->
                put(
                    JSONObject()
                        .put("role", if (message.role == "assistant") "model" else "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", message.content)))
                )
            }
        }
        val payload = JSONObject()
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 384))
        if (systemInstruction.isNotBlank()) {
            payload.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
        }

        val connection = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 12_000
        connection.readTimeout = 120_000
        connection.doOutput = true
        connection.setRequestProperty("x-goog-api-key", apiKey)
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")

        try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use(BufferedReader::readText)
            }.orEmpty()
            if (status !in 200..299) {
                error("Gemini 回應 $status：${body.take(300)}")
            }
            val parts = JSONObject(body)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            buildString {
                for (index in 0 until parts.length()) {
                    append(parts.getJSONObject(index).optString("text"))
                }
            }.trim().ifBlank { "Gemini 沒有回傳文字，請再試一次。" }
        } finally {
            connection.disconnect()
        }
    }
}
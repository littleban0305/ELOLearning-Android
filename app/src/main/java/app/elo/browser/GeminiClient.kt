package app.elo.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private const val MAX_503_RETRIES = 2

    private data class HttpResult(val status: Int, val body: String, val retryAfterSeconds: Long?)

    suspend fun reply(apiKey: String, messages: List<ChatMessage>): String {
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

        var retryCount = 0
        while (true) {
            val response = sendRequest(apiKey, payload.toString())
            if (response.status in 200..299) return parseReply(response.body)

            if (response.status == 503 && retryCount < MAX_503_RETRIES) {
                val delayMillis = response.retryAfterSeconds
                    ?.times(1_000)
                    ?.coerceIn(1_000, 8_000)
                    ?: (1_000L shl retryCount)
                retryCount++
                delay(delayMillis)
                continue
            }

            if (response.status == 503) {
                error("Gemini 暫時繁忙（503），已重試 $retryCount 次。請稍後再試。")
            }
            error("Gemini 回應 ${response.status}：${response.body.take(300)}")
        }
    }

    private suspend fun sendRequest(apiKey: String, payload: String): HttpResult = withContext(Dispatchers.IO) {
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
                output.write(payload.toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use(BufferedReader::readText)
            }.orEmpty()
            HttpResult(status, body, connection.getHeaderField("Retry-After")?.toLongOrNull())
        } finally {
            connection.disconnect()
        }
    }

    private fun parseReply(body: String): String {
        val parts = JSONObject(body)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
        return buildString {
            for (index in 0 until parts.length()) {
                append(parts.getJSONObject(index).optString("text"))
            }
        }.trim().ifBlank { "Gemini 沒有回傳文字，請再試一次。" }
    }
}
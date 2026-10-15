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

internal data class ChatMessage(val role: String, val content: String)

internal object QwenClient {
    suspend fun reply(serverUrl: String, messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val baseUrl = serverUrl.trim().trimEnd('/')
        val endpoint = if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions" else "$baseUrl/v1/chat/completions"
        val payload = JSONObject()
            .put("model", "local-model")
            .put("messages", JSONArray().apply {
                messages.takeLast(12).forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            })
            .put("temperature", 0.7)
            .put("max_tokens", 1_024)
            .put("stream", false)

            val answer = StringBuilder()
            var continuationCount = 0
            while (true) {
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 240_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")

                val body = try {
                    connection.outputStream.use { output ->
                        output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                    }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val response = stream?.use { input ->
                        BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use(BufferedReader::readText)
                    }.orEmpty()
                    if (status !in 200..299) {
                        error("Qwen 伺服器回應 $status：${response.take(300)}")
                    }
                    response
                } finally {
                    connection.disconnect()
            }

                val choice = JSONObject(body).getJSONArray("choices").getJSONObject(0)
                val content = choice.getJSONObject("message").getString("content").trim()
                answer.append(content)
                if (choice.optString("finish_reason") != "length" || continuationCount >= 1) {
                    return@withContext answer.toString().ifBlank { "模型沒有回傳文字，請再試一次。" }
                }
                payload.getJSONArray("messages")
                    .put(JSONObject().put("role", "assistant").put("content", content))
                    .put(JSONObject().put("role", "user").put("content", "請從剛才中斷的位置繼續，不要重複已回答的內容。"))
                continuationCount++
        }
    }
}
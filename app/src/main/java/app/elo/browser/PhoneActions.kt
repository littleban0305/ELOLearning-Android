package app.elo.browser

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import org.json.JSONObject

internal data class PendingPhoneAction(
    val intent: Intent,
    val confirmation: String,
    val completedMessage: String
)

internal data class AiActionResponse(
    val reply: String,
    val pendingAction: PendingPhoneAction? = null
)

internal object PhoneActions {
    val SYSTEM_INSTRUCTION = """
        你是 ELOBrowser 的繁體中文 AI 助理。回答簡潔、直接、準確；不確定或缺少必要資訊時先詢問，不要猜測或捏造。
        每次只能輸出單一 JSON，不加 Markdown：{"reply":"給使用者看的回答","action":null}。
        手機操作時 action 必須符合下列白名單，否則為 null：
        {"type":"set_alarm","hour":0,"minute":0,"label":"鬧鐘名稱","days":[]}（hour 0-23；days 可省略，星期日到六為 1 到 7）
        {"type":"set_timer","seconds":60,"label":"計時器名稱"}
        {"type":"open_settings","page":"main|wifi|bluetooth|sound|display|apps|location"}
        {"type":"dial","number":"電話號碼"}
        {"type":"sms","number":"電話號碼","message":"簡訊內容"}
        {"type":"email","address":"電子郵件","subject":"主旨","body":"內容"}
        {"type":"maps","query":"地點或導航目的地"}
        {"type":"camera"}
        {"type":"open_url","url":"https://..."}
        不要輸出其他 action/type。所有操作都先確認；撥號只開啟撥號器，簡訊與郵件只建立草稿、不會送出。
        使用者問你能做什麼時，說明你能回答問題、設定鬧鐘與計時器、開啟手機設定、準備撥號/簡訊/郵件、搜尋地圖、開相機及開啟網址。
        對鬧鐘時間、地點、電話號碼或收件地址等必要資訊不足時，先在 reply 詢問並設 action 為 null。只說「七點」時預設早上 07:00，並在 reply 明確說明這個假設。
        一般聊天與知識問題也使用相同 JSON 格式並設 action 為 null。不得聲稱已執行未確認的操作。
    """.trimIndent()

    fun parseAiResponse(raw: String): AiActionResponse {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end < start) return AiActionResponse(trimmed)

        return runCatching {
            val response = JSONObject(trimmed.substring(start, end + 1))
            val reply = response.optString("reply").ifBlank { "我收到你的訊息了。" }
            val action = response.optJSONObject("action") ?: return@runCatching AiActionResponse(reply)
            val pending = createAction(action) ?: return@runCatching AiActionResponse(reply)
            AiActionResponse(
                "$reply\n\n要執行「${pending.confirmation}」嗎？請回覆「確認」或「取消」。",
                pending
            )
        }.getOrElse { AiActionResponse(trimmed) }
    }

    private fun createAction(action: JSONObject): PendingPhoneAction? {
        val label = action.optString("label").trim().ifBlank { "ELOBrowser" }
        return when (action.optString("type")) {
            "set_alarm" -> {
                val hour = action.optInt("hour", -1)
                val minute = action.optInt("minute", -1)
                if (hour !in 0..23 || minute !in 0..59) return null
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                val days = action.optJSONArray("days")
                if (days != null && days.length() > 0) {
                    val values = ArrayList<Int>()
                    for (index in 0 until days.length()) {
                        val day = days.optInt(index, -1)
                        if (day !in 1..7) return null
                        values.add(day)
                    }
                    intent.putExtra(AlarmClock.EXTRA_DAYS, values)
                }
                PendingPhoneAction(intent, "設定 %02d:%02d 鬧鐘".format(hour, minute), "已交由 Android 時鐘設定鬧鐘。")
            }
            "set_timer" -> {
                val seconds = action.optInt("seconds", -1)
                if (seconds !in 1..86_400) return null
                val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                PendingPhoneAction(intent, "設定 $seconds 秒計時器", "已交由 Android 時鐘設定計時器。")
            }
            "open_settings" -> {
                val page = action.optString("page", "main")
                val settingsAction = when (page) {
                    "main" -> Settings.ACTION_SETTINGS
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    else -> return null
                }
                PendingPhoneAction(Intent(settingsAction), "開啟手機設定", "已開啟手機設定。")
            }
            "dial" -> {
                val number = action.optString("number").trim()
                if (!number.matches(Regex("[+0-9*#(),. -]{3,32}"))) return null
                PendingPhoneAction(
                    Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)),
                    "開啟 $number 的撥號器",
                    "已開啟撥號器；請自行確認並撥出。"
                )
            }
            "sms" -> {
                val number = action.optString("number").trim()
                val message = action.optString("message").trim()
                if (!number.matches(Regex("[+0-9*#(),. -]{3,32}")) || message.isBlank()) return null
                val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
                    .putExtra("sms_body", message)
                PendingPhoneAction(intent, "準備傳送給 $number 的簡訊", "已開啟預填簡訊；請自行確認後傳送。")
            }
            "email" -> {
                val address = action.optString("address").trim()
                if (!address.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) return null
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                    .putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
                    .putExtra(Intent.EXTRA_SUBJECT, action.optString("subject"))
                    .putExtra(Intent.EXTRA_TEXT, action.optString("body"))
                PendingPhoneAction(intent, "準備寄送給 $address 的郵件", "已開啟預填郵件；請自行確認後寄出。")
            }
            "maps" -> {
                val query = action.optString("query").trim()
                if (query.isBlank()) return null
                PendingPhoneAction(
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")),
                    "在地圖搜尋「$query」",
                    "已開啟地圖搜尋。"
                )
            }
            "camera" -> PendingPhoneAction(
                Intent(MediaStore.ACTION_IMAGE_CAPTURE),
                "開啟相機",
                "已開啟相機。"
            )
            "open_url" -> {
                val uri = Uri.parse(action.optString("url").trim())
                if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
                PendingPhoneAction(Intent(Intent.ACTION_VIEW, uri), "開啟 ${uri.host}", "已開啟網頁。")
            }
            else -> null
        }
    }

    fun isConfirmation(text: String): Boolean = text.trim().lowercase()
        .trimEnd('！', '!', '。', '.', ' ', '啊')
        .let { it in setOf("可以", "好", "確認", "確定", "執行", "是", "yes", "ok", "okay") }

    fun isCancellation(text: String): Boolean = text.trim().lowercase()
        .trimEnd('！', '!', '。', '.', ' ')
        .let { it in setOf("取消", "不要", "算了", "否", "不行", "no", "cancel") }
}
package app.elo.browser

import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings

internal data class PendingPhoneAction(
    val intent: Intent,
    val confirmation: String,
    val completedMessage: String
)

internal data class PhoneActionProposal(
    val reply: String,
    val pendingAction: PendingPhoneAction? = null
)

internal object PhoneActions {
    private val alarmTime = Regex("(今晚|晚上|下午|早上|上午)?\\s*(\\d{1,2})\\s*(?:[:：點时時]\\s*(\\d{1,2}|半)?\\s*(?:分)?)")
    private val duration = Regex("(\\d+)\\s*(秒|分鐘|分|小時|小時鐘)")

    fun parse(text: String): PhoneActionProposal? {
        val value = text.trim()
        val normalized = value.lowercase()
        if (listOf("鬧鐘", "alarm").any(normalized::contains)) {
            if (listOf("明天", "後天", "明早").any(value::contains)) {
                return PhoneActionProposal("我目前能設定今天起算的鬧鐘。請改成今晚或直接提供 24 小時制時間，例如 19:00。")
            }
            val match = alarmTime.find(value)
                ?: return PhoneActionProposal("可以，請告訴我鬧鐘時間，例如「今晚 7:00 設鬧鐘」。")
            val period = match.groupValues[1]
            var hour = match.groupValues[2].toIntOrNull()
                ?: return PhoneActionProposal("我沒辨識出時間，請用例如「今晚 7:00」的格式。")
            val minuteText = match.groupValues[3]
            val minute = if (minuteText == "半") 30 else minuteText.toIntOrNull() ?: 0
            if (hour !in 0..23 || minute !in 0..59) {
                return PhoneActionProposal("這個時間無效，請再提供一次鬧鐘時間。")
            }
            if (hour in 1..12 && period in setOf("今晚", "晚上", "下午")) hour = if (hour == 12) 12 else hour + 12
            if (hour == 12 && period in setOf("早上", "上午")) hour = 0
            val timeText = "%02d:%02d".format(hour, minute)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "ELOBrowser 設定的鬧鐘")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            return PhoneActionProposal(
                reply = "可以，是否確認設定 $timeText 的鬧鐘？請回覆「可以」或「取消」。",
                pendingAction = PendingPhoneAction(intent, "設定 $timeText 鬧鐘", "已交由 Android 時鐘設定 $timeText 鬧鐘。")
            )
        }

        if (listOf("計時器", "倒數", "timer").any(normalized::contains)) {
            val match = duration.find(value)
                ?: return PhoneActionProposal("可以，請告訴我計時長度，例如「計時 10 分鐘」。")
            val amount = match.groupValues[1].toIntOrNull() ?: 0
            val unit = match.groupValues[2]
            val seconds = when (unit) {
                "秒" -> amount
                "小時", "小時鐘" -> amount * 3_600
                else -> amount * 60
            }
            if (seconds !in 1..86_400) return PhoneActionProposal("計時長度需介於 1 秒與 24 小時。")
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "ELOBrowser 設定的計時器")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            return PhoneActionProposal(
                reply = "可以，是否確認設定 $amount $unit 計時器？請回覆「可以」或「取消」。",
                pendingAction = PendingPhoneAction(intent, "設定計時器", "已交由 Android 時鐘設定計時器。")
            )
        }

        val settingsAction = when {
            listOf("手機設定", "系統設定", "打開設定", "開啟設定").any(value::contains) ->
                Settings.ACTION_SETTINGS to "手機系統設定"
            listOf("wifi", "wi-fi", "無線網路", "無線網絡").any(normalized::contains) ->
                Settings.ACTION_WIFI_SETTINGS to "Wi-Fi 設定"
            listOf("藍牙", "bluetooth").any(normalized::contains) ->
                Settings.ACTION_BLUETOOTH_SETTINGS to "藍牙設定"
            listOf("音量設定", "音效設定", "聲音設定").any(value::contains) ->
                Settings.ACTION_SOUND_SETTINGS to "音效設定"
            else -> null
        }
        if (settingsAction != null) {
            val intent = Intent(settingsAction.first)
            return PhoneActionProposal(
                reply = "可以，是否開啟${settingsAction.second}？請回覆「可以」或「取消」。",
                pendingAction = PendingPhoneAction(intent, "開啟${settingsAction.second}", "已開啟${settingsAction.second}。")
            )
        }
        return null
    }

    fun isGreeting(text: String): Boolean {
        val normalized = text.trim().lowercase().trimEnd('！', '!', '。', '.', '？', '?', '～', '~')
        return normalized in setOf(
            "你好", "您好", "嗨", "哈囉", "早安", "午安", "晚安", "hello", "hi", "hey",
            "謝謝", "多謝", "感謝", "thanks", "thank you", "你是誰", "你是谁", "你在嗎", "你在吗"
        ) || normalized.startsWith("你好嗎") || normalized.startsWith("你好吗")
    }

    fun smallTalkReply(text: String): String {
        val normalized = text.trim().lowercase()
        return when {
            normalized.contains("謝謝") || normalized.contains("多謝") || normalized.contains("感謝") || normalized.contains("thanks") ->
                "不客氣！還有什麼需要我幫忙的嗎？"
            normalized.contains("你是誰") || normalized.contains("你是谁") ->
                "我是 ELOBrowser 的 AI 助理，可以搜尋維基百科和 CS 圖鑑，也能在你確認後協助設定鬧鐘或計時器。"
            normalized.contains("你在嗎") || normalized.contains("你在吗") ->
                "我在！你可以問我維基百科或 CS 圖鑑的內容，也可以請我準備手機操作。"
            normalized.contains("早安") -> "早安！今天想查什麼，或需要我幫你設定提醒嗎？"
            normalized.contains("晚安") -> "晚安！需要我幫你設定明早的鬧鐘嗎？"
            else -> "你好！想找維基百科或 CS 武器皮膚資料，或需要設定鬧鐘、計時器嗎？"
        }
    }

    fun isConfirmation(text: String): Boolean = text.trim().lowercase()
        .trimEnd('！', '!', '。', '.', ' ', '啊')
        .let { it in setOf("可以", "好", "確認", "確定", "執行", "是", "yes", "ok", "okay") }

    fun isCancellation(text: String): Boolean = text.trim().lowercase()
        .trimEnd('！', '!', '。', '.', ' ')
        .let { it in setOf("取消", "不要", "算了", "否", "不行", "no", "cancel") }
}
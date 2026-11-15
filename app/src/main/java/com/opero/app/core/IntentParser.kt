package com.opero.app.core

/**
 * 규칙 기반 음성 응답 해석기. (공모전 공개 SDK에 LLM이 없어 규칙으로 처리합니다.)
 * - 방문 목적: "응, 견적 미팅" → 업무 / 견적 미팅
 * - 개인 용무: "아니, 개인 용무야" → 개인
 * - 경비: "주차비 3천 원" → 주차비 3000
 */
object IntentParser {

    sealed interface Purpose {
        data class Business(val purpose: String) : Purpose
        data object Personal : Purpose
        data object Unconfirmed : Purpose
    }

    data class ExpenseItem(val category: String, val amount: Int)

    private val yesWords = setOf("응", "네", "예", "어", "그래", "맞아", "맞아요", "좋아", "오케이", "그렇지", "맞습니다", "네네", "웅")
    private val personalWords = listOf("개인", "사적", "사생활", "아니", "아뇨", "아니요")
    private val cancelWords = listOf("취소", "없어", "없음", "됐어", "괜찮아", "건너", "패스", "아니", "아뇨", "아니요", "그만")
    private val repeatWords = listOf("다시", "한번 더", "한 번 더")
    private val expenseCategories = linkedMapOf(
        "주차" to "주차비",
        "통행" to "통행료",
        "톨" to "통행료",
        "하이패스" to "통행료",
        "주유" to "주유비",
        "충전" to "충전비",
        "세차" to "세차비",
    )

    fun purpose(text: String?, suggestion: String?): Purpose {
        if (text.isNullOrBlank()) return Purpose.Unconfirmed
        val normalized = text.trim()
        if (personalWords.any { normalized.contains(it) } && !normalized.contains("업무")) return Purpose.Personal
        val tokens = normalized.replace(Regex("[,.!?~]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        val rest = tokens.dropWhile { it in yesWords }.joinToString(" ")
            .removeSuffix("이요").removeSuffix("요").trim()
        if (rest.isBlank() || rest == "업무" || rest == "업무로") {
            return Purpose.Business(suggestion ?: "업무 방문")
        }
        return Purpose.Business(rest)
    }

    fun isYesOnly(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val tokens = text.replace(Regex("[,.!?~]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.isNotEmpty() && tokens.all { it in yesWords || it == "말할게" || it == "남길게" }
    }

    fun isCancel(text: String?): Boolean =
        text != null && text.length <= 12 && cancelWords.any { text.contains(it) }

    fun isRepeat(text: String?): Boolean = text != null && repeatWords.any { text.contains(it) }

    /** "개인 용무 끝", "업무 시작", "업무로 돌아가" */
    fun isPersonalEnd(text: String?): Boolean {
        if (text == null) return false
        val endWords = listOf("끝", "종료", "그만", "마침", "끝났")
        return (text.contains("개인") && endWords.any { text.contains(it) }) ||
            (text.contains("업무") && (text.contains("시작") || text.contains("돌아")) && !text.contains("개인"))
    }

    fun isPersonalStart(text: String?): Boolean =
        text != null && text.contains("개인") && (text.contains("용무") || text.contains("시작") || text.contains("부터"))

    fun expense(text: String?): ExpenseItem? {
        if (text.isNullOrBlank()) return null
        val entry = expenseCategories.entries.firstOrNull { text.contains(it.key) } ?: return null
        // 금액은 경비 단어 뒤에서만 찾습니다. ("12월 납품 ... 주차비 3천 원"의 12를 피하기 위해)
        val tail = text.substring(text.indexOf(entry.key))
        val amountPart = Regex("([0-9,]+\\s*[만천백]?\\s*)+원?|[일이삼사오육칠팔구십백천만 ]+원").find(tail)?.value ?: return null
        val amount = parseAmount(amountPart) ?: return null
        return ExpenseItem(entry.value, amount)
    }

    /** "3천", "4,800", "1만 2천", "삼천" 같은 금액 표현을 원 단위 정수로 바꿉니다. */
    fun parseAmount(raw: String): Int? {
        val s = raw.replace(",", "").replace("원", "").replace(" ", "")
        if (s.isEmpty()) return null
        val digits = mapOf('일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9)
        val units = mapOf('십' to 10, '백' to 100, '천' to 1000)
        var total = 0L
        var section = 0L
        var number = 0L
        var i = 0
        var sawSomething = false
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() -> {
                    var j = i
                    while (j < s.length && s[j].isDigit()) j++
                    number = s.substring(i, j).toLong()
                    sawSomething = true
                    i = j
                    continue
                }
                digits.containsKey(c) -> { number = digits.getValue(c).toLong(); sawSomething = true }
                units.containsKey(c) -> {
                    section += (if (number == 0L) 1L else number) * units.getValue(c)
                    number = 0
                    sawSomething = true
                }
                c == '만' -> {
                    total += (section + number).let { if (it == 0L) 1L else it } * 10_000
                    section = 0
                    number = 0
                    sawSomething = true
                }
                else -> return null
            }
            i++
        }
        if (!sawSomething) return null
        val result = total + section + number
        return result.takeIf { it in 1..10_000_000 }?.toInt()
    }
}

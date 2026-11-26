package app.medassist.core

/** Finds advice like «пересдать ферритин через 8–10 недель» in an analysis, to offer a reminder. */
object Retest {
    data class Hint(val text: String, val days: Int)

    private val period = Regex(
        "через\\s+(\\d{1,3})(?:\\s*[–—-]\\s*\\d{1,3})?\\s*(дн|день|недел|нед\\.|месяц|мес\\.|мес\\b)",
        RegexOption.IGNORE_CASE,
    )
    private val action = Regex("пересд|повтор|контрол|сдать|проверить", RegexOption.IGNORE_CASE)

    fun find(text: String): Hint? {
        val lines = Labs.visible(text).lines().map { clean(it) }.filter { it.isNotBlank() }
        for (line in lines) {
            if (!action.containsMatchIn(line)) continue
            val m = period.find(line) ?: continue
            val n = m.groupValues[1].toIntOrNull() ?: continue
            val unit = m.groupValues[2].lowercase()
            val days = when {
                unit.startsWith("нед") -> n * 7
                unit.startsWith("мес") -> n * 30
                else -> n
            }
            if (days !in 3..730) continue
            return Hint(line.take(90).trimEnd(',', '.', ';', ':'), days)
        }
        return null
    }

    private fun clean(s: String) = s.trim()
        .replace(Regex("^(?:[-*•]|\\d+[.)])\\s*"), "")
        .replace("**", "").replace("__", "").trim()
}

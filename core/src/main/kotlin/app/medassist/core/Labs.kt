package app.medassist.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * The model appends a hidden ```labs [...]``` block with numeric results to its full analysis.
 * The app strips it from the visible text and keeps the values for the dynamics charts.
 */
object Labs {
    /** Added to the owner's prompt so the model emits the block. */
    val INSTRUCTION = """
        |
        |## СЛУЖЕБНЫЙ БЛОК ДЛЯ ПРИЛОЖЕНИЯ
        |
        |Когда даёшь разбор (ответ со строкой СТАТУС) или в ответе появились новые анализы, добавь в самый конец ответа блок с числовыми показателями анализов из документов. Формат строго такой:
        |
        |```labs
        |[{"name":"Гемоглобин","value":135,"unit":"г/л","date":"2025-03-14","ref":"130–160"}]
        |```
        |
        |Правила: только показатели, явно указанные в документах, с числовым значением; name — общепринятое русское название без сокращений лаборатории; date — дата взятия материала в формате ГГГГ-ММ-ДД (если не указана — пропусти показатель); value — число с точкой; ref — референс из бланка или пустая строка. Если один показатель сдан несколько раз — отдельная запись на каждую дату. Не упоминай этот блок в тексте ответа.
    """.trimMargin()

    private val block = Regex("```labs\\s*(.*?)```", RegexOption.DOT_MATCHES_ALL)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val dateRe = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** Text without the block (also hides a half-streamed block). */
    fun visible(text: String): String {
        val cleaned = block.replace(text, "")
        val open = cleaned.indexOf("```labs")
        return (if (open >= 0) cleaned.substring(0, open) else cleaned).trimEnd()
    }

    fun extract(text: String, profileId: String): List<LabResult> =
        block.findAll(text).flatMap { m ->
            runCatching { json.parseToJsonElement(m.groupValues[1].trim()).jsonArray }.getOrNull().orEmpty()
        }.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val name = s("name")
            val date = s("date")
            val value = s("value").replace(',', '.').toDoubleOrNull()
            if (name.isEmpty() || value == null || !dateRe.matches(date)) null
            else LabResult(name.replaceFirstChar { it.uppercase() }, value, s("unit"), date, s("ref"), profileId)
        }.toList()

    /** Adds new values; a value for the same profile, name and date replaces the old one. */
    fun merge(old: List<LabResult>, new: List<LabResult>): List<LabResult> {
        fun key(r: LabResult) = Triple(r.profileId, r.name.lowercase(), r.date)
        val keys = new.map(::key).toSet()
        return (old.filterNot { key(it) in keys } + new).sortedWith(compareBy({ it.name }, { it.date }))
    }
}

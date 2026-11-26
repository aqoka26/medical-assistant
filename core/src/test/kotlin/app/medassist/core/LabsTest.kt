package app.medassist.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LabsTest {
    private val answer = """
        Разбор готов.

        ```labs
        [{"name":"гемоглобин","value":"128,5","unit":"г/л","date":"2025-03-14","ref":"130-160"},
         {"name":"Ферритин","value":9,"unit":"нг/мл","date":"2025-03-14"},
         {"name":"Без даты","value":1,"date":""}]
        ```
    """.trimIndent()

    @Test
    fun extractsAndHides() {
        val labs = Labs.extract(answer, "me")
        assertEquals(2, labs.size)
        assertEquals("Гемоглобин", labs[0].name)
        assertEquals(128.5, labs[0].value)
        assertEquals("Разбор готов.", Labs.visible(answer))
    }

    @Test
    fun hidesHalfStreamedBlock() {
        assertFalse("labs" in Labs.visible("Текст\n```labs\n[{\"name\""))
    }

    @Test
    fun mergeReplacesSameDate() {
        val a = LabResult("Гемоглобин", 120.0, date = "2025-01-01")
        val b = LabResult("Гемоглобин", 125.0, date = "2025-01-01")
        val c = LabResult("Гемоглобин", 130.0, date = "2025-02-01")
        assertEquals(listOf(125.0, 130.0), Labs.merge(listOf(a), listOf(b, c)).map { it.value })
    }
}

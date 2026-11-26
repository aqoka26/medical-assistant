package app.medassist.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportTest {
    @Test
    fun parsesStructuredAnswer() {
        val r = Report.parse(
            """
            **СТАТУС:** ВНИМАНИЕ
            ЗАГОЛОВОК: Анализ крови и ферритин
            КОРОТКО: Есть признаки нехватки железа.
            Гемоглобин пока в норме.

            ## Главное
            - Ферритин низкий.
            ### Подробнее
            Текст.

            ## Пустой раздел

            ## Что делать дальше
            Сдать анализ через 3 месяца.

            ```labs
            [{"name":"Ферритин","value":9,"date":"2025-03-14"}]
            ```
            """.trimIndent()
        )
        assertEquals(Report.Status.ATTENTION, r.status)
        assertEquals("Анализ крови и ферритин", r.title)
        assertEquals("Есть признаки нехватки железа. Гемоглобин пока в норме.", r.summary)
        assertEquals(listOf("Главное", "Что делать дальше"), r.sections.map { it.title })
        assertTrue("**Подробнее**" in r.sections[0].body)
        assertFalse("labs" in r.sections[1].body)
        assertTrue(r.isStructured)
    }

    @Test
    fun plainTextIsNotStructured() {
        val r = Report.parse("Обычный ответ на вопрос.")
        assertFalse(r.isStructured)
        assertEquals("Обычный ответ на вопрос.", r.intro)
    }
}

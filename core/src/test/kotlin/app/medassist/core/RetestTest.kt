package app.medassist.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetestTest {
    @Test
    fun findsWeeks() {
        val h = Retest.find("## Что делать дальше\n1. Обсудить железо.\n2. **Пересдать** ферритин и ОАК через 8–10 недель.")!!
        assertEquals(56, h.days)
        assertEquals("Пересдать ферритин и ОАК через 8–10 недель", h.text)
    }

    @Test
    fun findsMonths() {
        assertEquals(90, Retest.find("- Контроль ТТГ через 3 месяца")!!.days)
    }

    @Test
    fun ignoresOtherPeriods() {
        assertNull(Retest.find("Принимать железо через 2 часа после еды."))
    }
}

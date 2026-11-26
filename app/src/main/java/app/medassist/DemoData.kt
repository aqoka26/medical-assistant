package app.medassist

import app.medassist.core.Attachment
import app.medassist.core.ChatMessage
import app.medassist.core.Conversation
import app.medassist.core.LabResult
import app.medassist.core.Profile
import app.medassist.core.Reminder
import app.medassist.core.Role
import app.medassist.core.Usage

/** Sample content for design screenshots in CI. Only used when the build sets DEMO. */
object DemoData {
    private const val DAY = 86_400_000L

    val report = """
        СТАТУС: ВНИМАНИЕ
        ЗАГОЛОВОК: Анализ крови и запасы железа
        КОРОТКО: Гемоглобин пока в норме, но запасы железа почти закончились. Это частая причина слабости и выпадения волос, и это хорошо лечится.

        ## Главное
        - **Ферритин 9 нг/мл** (норма 30–400): запас железа в организме очень низкий.
        - Гемоглобин **128 г/л** (норма 120–150) пока в норме, но близко к нижней границе.
        - Без восполнения железа через несколько месяцев может начаться анемия.

        ## Что в порядке
        - Лейкоциты, тромбоциты и СОЭ в норме: признаков воспаления нет.
        - Гормон щитовидной железы (ТТГ 1,9 мЕд/л) в норме.

        ## На что обратить внимание
        - Ферритин снижается от анализа к анализу: 21 → 14 → 9 за год.
        - Слабость и выпадение волос, о которых вы пишете, хорошо объясняются нехваткой железа.

        ## Что делать дальше
        1. Обсудить с терапевтом приём препарата железа.
        2. Пересдать ферритин и общий анализ крови через 8–10 недель.
        3. Выяснить причину потери железа: питание, обильные менструации, желудок.

        ## К какому врачу
        **Терапевт** — в ближайшие 2–3 недели. Срочно идти не нужно.
    """.trimIndent()

    fun state(base: UiState, screen: Screen): UiState {
        val now = System.currentTimeMillis()
        val files = listOf(
            Attachment("d1", "ОАК_14.03.2025.pdf", "application/pdf", "x", now),
            Attachment("d2", "Ферритин_ЕМИАС.pdf", "application/pdf", "x", now),
            Attachment("d3", "Фото_заключения.jpg", "image/jpeg", "x", now),
        )
        val c1 = Conversation(
            id = "demo1",
            title = "Анализ крови и запасы железа",
            createdAt = now - 2 * DAY,
            messages = listOf(
                ChatMessage(Role.USER, "Новый разбор.\nЖалобы: слабость, выпадают волосы\n$ALL_FILES_MARKER", files, now - 2 * DAY),
                ChatMessage(Role.ASSISTANT, report, timestamp = now - 2 * DAY + 60_000),
                ChatMessage(Role.USER, "Какое железо лучше пить?", timestamp = now - 2 * DAY + 120_000),
                ChatMessage(Role.ASSISTANT, "Чаще всего назначают **сульфат железа** или **бисглицинат**. Выбор и дозу лучше согласовать с терапевтом: от этого зависит, как быстро восстановятся запасы.", timestamp = now - 2 * DAY + 180_000),
            ),
        )
        val c2 = Conversation(
            id = "demo2",
            title = "Биохимия крови",
            createdAt = now - 40 * DAY,
            messages = listOf(
                ChatMessage(Role.USER, ALL_FILES_MARKER, files.take(1), now - 40 * DAY),
                ChatMessage(
                    Role.ASSISTANT,
                    "СТАТУС: НОРМА\nЗАГОЛОВОК: Биохимия крови\nКОРОТКО: Печень, почки и сахар в норме. Холестерин немного выше желаемого.\n\n## Главное\n- Всё основное в норме.",
                    timestamp = now - 40 * DAY,
                ),
            ),
        )
        val labs = listOf(
            LabResult("Ферритин", 21.0, "нг/мл", "2024-03-10", "30–400"),
            LabResult("Ферритин", 14.0, "нг/мл", "2024-09-02", "30–400"),
            LabResult("Ферритин", 9.0, "нг/мл", "2025-03-14", "30–400"),
            LabResult("Гемоглобин", 134.0, "г/л", "2024-03-10", "120–150"),
            LabResult("Гемоглобин", 131.0, "г/л", "2024-09-02", "120–150"),
            LabResult("Гемоглобин", 128.0, "г/л", "2025-03-14", "120–150"),
            LabResult("ТТГ", 1.9, "мЕд/л", "2025-03-14", "0,4–4,0"),
        )
        return base.copy(
            screen = screen,
            disclaimerAccepted = true,
            locked = false,
            login = "anna",
            usage = Usage("anna", 3, 10),
            profiles = listOf(Profile.DEFAULT, Profile("mom", "Мама", 1962, "ж"), Profile("son", "Миша", 2015, "м")),
            profileId = Profile.ME,
            conversations = listOf(c1, c2),
            current = if (screen == Screen.REPORT) c1 else null,
            pending = if (screen == Screen.NEW) files.take(2) else emptyList(),
            labs = labs,
            reminders = listOf(Reminder(1, "Пересдать ферритин и ОАК", now + 60 * DAY)),
        )
    }
}

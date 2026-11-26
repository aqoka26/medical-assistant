package app.medassist.core

/** A finished analysis, split into the parts the app shows as cards. */
data class Report(
    val status: Status?,
    val title: String?,
    val summary: String?,
    val sections: List<Section>,
    /** Text the model wrote before the first section, if any. */
    val intro: String,
) {
    enum class Status { NORMAL, ATTENTION, URGENT }

    data class Section(val title: String, val body: String)

    val isStructured: Boolean get() = status != null || sections.size >= 2

    companion object {
        private val statusRe = Regex("^\\**\\s*СТАТУС\\s*:\\s*\\**\\s*([А-ЯЁа-яё]+)", RegexOption.IGNORE_CASE)
        private val titleRe = Regex("^\\**\\s*ЗАГОЛОВОК\\s*:\\s*\\**\\s*(.+)", RegexOption.IGNORE_CASE)
        private val summaryRe = Regex("^\\**\\s*КОРОТКО\\s*:\\s*\\**\\s*(.*)", RegexOption.IGNORE_CASE)
        private val headingRe = Regex("^#{1,2}\\s+(.+)")
        private val subHeadingRe = Regex("^#{3,6}\\s+(.+)")

        fun parse(raw: String): Report {
            var status: Status? = null
            var title: String? = null
            val summary = StringBuilder()
            var inSummary = false
            val intro = StringBuilder()
            val sections = mutableListOf<Section>()
            var current: String? = null
            val body = StringBuilder()

            fun flush() {
                current?.let { sections += Section(it, body.toString().trim()) }
                body.clear()
            }

            for (line in Labs.visible(raw).lines()) {
                val t = line.trim()
                val h = headingRe.find(t)
                when {
                    h != null -> {
                        inSummary = false
                        flush()
                        current = clean(h.groupValues[1])
                    }
                    current == null && statusRe.find(t) != null -> {
                        val w = statusRe.find(t)!!.groupValues[1].lowercase()
                        status = when {
                            w.startsWith("сроч") -> Status.URGENT
                            w.startsWith("вним") -> Status.ATTENTION
                            w.startsWith("норм") -> Status.NORMAL
                            else -> null
                        }
                    }
                    current == null && titleRe.find(t) != null -> title = clean(titleRe.find(t)!!.groupValues[1])
                    current == null && summaryRe.find(t) != null -> {
                        inSummary = true
                        summary.append(summaryRe.find(t)!!.groupValues[1].trim())
                    }
                    current == null && inSummary && t.isNotEmpty() -> summary.append(' ').append(t)
                    current == null -> if (t.isNotEmpty() || intro.isNotEmpty()) intro.appendLine(line)
                    else -> {
                        // Sub-headings render too large in cards; show them as bold lines.
                        val sub = subHeadingRe.find(t)
                        body.appendLine(if (sub != null) "**${clean(sub.groupValues[1])}**" else line)
                    }
                }
            }
            flush()
            return Report(
                status = status,
                title = title?.takeIf { it.isNotBlank() },
                summary = summary.toString().trim().takeIf { it.isNotEmpty() },
                sections = sections.filter { it.body.isNotBlank() },
                intro = intro.toString().trim(),
            )
        }

        private fun clean(s: String) = s.replace("**", "").replace("__", "").trim().trimEnd(':')
    }
}

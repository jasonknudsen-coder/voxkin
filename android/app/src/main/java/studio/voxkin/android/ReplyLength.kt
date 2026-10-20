package studio.voxkin.android

/** The same per-turn reply preferences as the iPhone client, never gateway configuration. */
enum class ReplyLength(val title: String, val detail: String, val guidance: String) {
    BRIEF("Brief", "Quick, focused answers—the current concise style.",
        "Prefer about 30–50 words total in 1–3 short, natural sentences. Answer directly without an introductory recap or extra trivia. Give more detail when the user asks for it or it is necessary for a correct, useful answer. Do not omit essential information."),
    BALANCED("Balanced", "A little more context—the original concise style, often around 100 words.",
        "Prefer 2–3 short, natural sentences. Give more detail when the user asks for it or it is necessary for a correct, useful answer. Do not omit essential information."),
    FULL("Full", "Normal-length answers, without a brevity preference.",
        "Use your normal response length; any earlier concise-reply preference is off.");

    fun requestText(text: String, language: String): String {
        if (text.trimStart().startsWith("/") || text.trimStart().startsWith("!")) return text
        val languageName = if (language.startsWith("es")) "Spanish" else "English"
        return "[Voxkin reply preference for this turn only: $guidance Respond in $languageName unless the user explicitly requests another language. Speech will use the selected voice’s language.]\n\n$text"
    }
}

package studio.voxkin.android

/** Speech-only cleanup of the same inline text rendered on screen; original messages stay untouched. */
object SpeechText {
    fun prepare(text: String): String = InlineMarkdown.render(text).text
            .replace(Regex("(?m)^[ \\t]*(?:-[ \\t]*){3,}$"), "")
            .replace(Regex("(?m)^[ \\t]*[-–—•][ \\t]+"), "")
            .replace(Regex("(?<!\\d)—|—(?!\\d)"), ", ")
            .replace(Regex("(?<=\\p{L})[ \\t]+-{2}[ \\t]+(?=\\p{L})"), ", ")
            .replace(Regex("[ \\t]+,"), ",")
            .replace(Regex("([.!?,:;])[ \\t]*,[ \\t]*"), "$1 ")
            .replace(Regex("[ \\t]{2,}"), " ")
}

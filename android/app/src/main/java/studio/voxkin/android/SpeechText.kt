package studio.voxkin.android

import org.commonmark.node.*
import org.commonmark.parser.Parser

/** Speech-only rendering; the original reply and history remain untouched. */
object SpeechText {
    // Match iOS inline-only Markdown: keep line layout, omit link destinations.
    private val parser = Parser.builder().enabledBlockTypes(emptySet()).build()

    fun prepare(text: String): String {
        val output = StringBuilder()
        parser.parse(text).accept(object : AbstractVisitor() {
            override fun visit(text: Text) { output.append(text.literal) }
            override fun visit(code: Code) { output.append(code.literal) }
            override fun visit(html: HtmlInline) { output.append(html.literal) }
            override fun visit(line: SoftLineBreak) { output.append('\n') }
            override fun visit(line: HardLineBreak) { output.append('\n') }
            override fun visit(paragraph: Paragraph) {
                visitChildren(paragraph)
                if (paragraph.next != null) output.append("\n\n")
            }
        })
        return output.toString().trimEnd()
            .replace(Regex("(?m)^[ \\t]*(?:-[ \\t]*){3,}$"), "")
            .replace(Regex("(?m)^[ \\t]*[-–—•][ \\t]+"), "")
            .replace(Regex("(?<!\\d)—|—(?!\\d)"), ", ")
            .replace(Regex("(?<=\\p{L})[ \\t]+-{2}[ \\t]+(?=\\p{L})"), ", ")
            .replace(Regex("[ \\t]+,"), ",")
            .replace(Regex("([.!?,:;])[ \\t]*,[ \\t]*"), "$1 ")
            .replace(Regex("[ \\t]{2,}"), " ")
    }
}

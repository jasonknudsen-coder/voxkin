package studio.voxkin.android

import java.net.URI
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.internal.StrikethroughDelimiterProcessor
import org.commonmark.internal.InlineParserImpl
import org.commonmark.node.*
import org.commonmark.parser.*
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.LinkProcessor
import org.commonmark.parser.delimiter.DelimiterProcessor

/** Isolates CommonMark 0.30.1's internal inline adapter. Keep fixtures on upgrades. */
object InlineMarkdown {
    enum class Kind { BOLD, ITALIC, CODE, STRIKE }
    data class Span(val kind: Kind, val start: Int, val end: Int)
    data class LinkRange(val url: String, val start: Int, val end: Int)
    data class Rendered(val text: String, val spans: List<Span> = emptyList(), val links: List<LinkRange> = emptyList())

    private val context = object : InlineParserContext {
        override fun getCustomInlineContentParserFactories(): List<InlineContentParserFactory> = emptyList()
        override fun getCustomDelimiterProcessors(): List<DelimiterProcessor> = listOf(StrikethroughDelimiterProcessor(false))
        override fun getCustomLinkProcessors(): List<LinkProcessor> = emptyList()
        override fun getCustomLinkMarkers(): Set<Char> = emptySet()
        override fun getMaxInlineNesting() = 64
        override fun getLinkReferenceDefinition(label: String): LinkReferenceDefinition? = null
        override fun <D : Any?> getDefinition(type: Class<D>, label: String): D? = null
    }
    fun safeLink(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme?.lowercase() in listOf("https", "http") && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && url.none { it.isISOControl() } && (uri.port == -1 || uri.port in 1..65535)
    }.getOrDefault(false)

    fun render(input: String): Rendered {
        val source = input.replace("\r\n", "\n").replace('\r', '\n')
        return runCatching {
            val lines = SourceLines.empty()
            var offset = 0
            source.split('\n').forEachIndexed { index, line ->
                lines.addLine(SourceLine.of(line, SourceSpan.of(index, 0, offset, line.length)))
                offset += line.length + 1
            }
            val root = Paragraph()
            InlineParserImpl(context).parse(lines, root)
            val out = StringBuilder()
            val spans = mutableListOf<Span>()
            val links = mutableListOf<LinkRange>()
            fun walk(node: Node, allowAnnotations: Boolean = true) {
                val start = out.length
                when (node) {
                    is Text -> {
                        out.append(node.literal)
                        // parseText strips spaces at a line end, spaces/tabs at EOF.
                        // Restore raw suffix, not a literal-length difference: entities can produce spaces too.
                        val last = node.sourceSpans.lastOrNull()
                        if (last != null) {
                            val end = last.inputIndex + last.length
                            val eof = end == source.length
                            if (eof || source.getOrNull(end) == '\n') {
                                val raw = source.substring(last.inputIndex, end)
                                out.append(raw.takeLastWhile { it == ' ' || (eof && it == '\t') })
                            }
                        }
                    }
                    is Code -> out.append(node.literal)
                    is HtmlInline -> out.append(node.literal)
                    is SoftLineBreak, is HardLineBreak -> out.append('\n')
                    else -> {
                        var child = node.firstChild
                        while (child != null) { walk(child, allowAnnotations && node !is Image); child = child.next }
                    }
                }
                val end = out.length
                if (end > start) {
                    val kind = when (node) {
                        is StrongEmphasis -> Kind.BOLD
                        is Emphasis -> Kind.ITALIC
                        is Code -> Kind.CODE
                        is Strikethrough -> Kind.STRIKE
                        else -> null
                    }
                    if (allowAnnotations && kind != null) spans.add(Span(kind, start, end))
                    if (allowAnnotations && node is Link && safeLink(node.destination)) links.add(LinkRange(node.destination, start, end))
                }
            }
            walk(root)
            Rendered(out.toString(), spans, links)
        }.getOrElse { Rendered(source) }
    }
}

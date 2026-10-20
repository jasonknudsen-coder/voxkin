package studio.voxkin.android
import org.junit.Assert.*
import org.junit.Test

class InlineMarkdownTest {
    @Test fun inlineWhitespaceFixtures() {
        for ((input, expected) in listOf(
            "A **bold** fact.\n\nAnother *thought*." to "A bold fact.\n\nAnother thought.",
            "  A **bold**  \n\n\n  B\t" to "  A bold  \n\n\n  B\t",
            "**two\n\nlines**\n" to "two\n\nlines\n",
            "# **H**\n> *q*\n- item" to "# H\n> q\n- item",
            "```\nx\n```" to "x",
            "```swift\nlet x = 1\n```\nAfter" to "swift let x = 1 \nAfter",
            "[**a**][r]\n\n[r]: https://example.com" to "[a][r]\n\n[r]: https://example.com",
            "a &amp; b \\*c\\*\na\\\nb\r\n" to "a & b *c*\na\nb\n",
            "~~gone~~ ![alt](https://example.com/x) [x](javascript:alert(1)) <b>y</b>" to "gone alt x <b>y</b>",
            "a &#32; " to "a   ",
            "~single~ ~~double~~ ~~~triple~~~" to "single double ~~~triple~~~",
            "a &#32;  \n" to "a    \n",
            "&#32; " to "  ",
            "a  \t\nb\t \t" to "a  \t\nb\t \t",
            "a\t  \n" to "a\t  \n",
            "\n\n  \t\n" to "\n\n  \t\n",
            "unclosed **bold and [link" to "unclosed **bold and [link",
        )) assertEquals(input, expected, InlineMarkdown.render(input).text)
    }
    @Test fun annotationsUseRenderedUtf16Offsets() {
        val r = InlineMarkdown.render("😀 [the **guide**](https://example.com/a_(b))")
        assertEquals("😀 the guide", r.text)
        assertEquals(listOf(InlineMarkdown.LinkRange("https://example.com/a_(b)",3,12)),r.links)
        assertTrue(r.spans.contains(InlineMarkdown.Span(InlineMarkdown.Kind.BOLD,7,12)))
        val nested = InlineMarkdown.render("***both*** ~~gone~~ `**literal**`")
        assertEquals("both gone **literal**",nested.text)
        assertTrue(nested.spans.contains(InlineMarkdown.Span(InlineMarkdown.Kind.BOLD,0,4)))
        assertTrue(nested.spans.contains(InlineMarkdown.Span(InlineMarkdown.Kind.ITALIC,0,4)))
        assertTrue(nested.spans.any { it.kind==InlineMarkdown.Kind.STRIKE })
        assertTrue(nested.spans.any { it.kind==InlineMarkdown.Kind.CODE })
    }
    @Test fun imageAltIsPlainButAnOuterLinkMayLinkItsLabel() {
        val image = InlineMarkdown.render("![**bold** *soft* `code` [b](https://example.com)](image)")
        assertEquals("bold soft code b", image.text)
        assertTrue(image.spans.isEmpty()); assertTrue(image.links.isEmpty())
        val outer = InlineMarkdown.render("[![**label**](image)](https://example.com)")
        assertEquals("label", outer.text); assertTrue(outer.spans.isEmpty())
        assertEquals(listOf(InlineMarkdown.LinkRange("https://example.com",0,5)), outer.links)
    }
    @Test fun unsafeLinksReferencesAndImagesNeverCreateActions() {
        for (url in listOf("javascript:alert(1)","data:text/html,x","file:///tmp/x","https://user:pass@example.com","https://example.com/\n","/relative")) assertFalse(url,InlineMarkdown.safeLink(url))
        assertTrue(InlineMarkdown.safeLink("https://example.com/a?q=b#c"))
        for (source in listOf("![alt](https://example.com/image)","![a [b](https://example.com)](image)","[x](javascript:alert(1))","[a][r]\n\n[r]: https://example.com")) assertTrue(InlineMarkdown.render(source).links.isEmpty())
    }
}

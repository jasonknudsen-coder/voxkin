package studio.voxkin.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

internal fun InlineMarkdown.Rendered.annotated(): AnnotatedString = AnnotatedString.Builder(text).apply {
    spans.forEach {
        addStyle(when (it.kind) {
            InlineMarkdown.Kind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            InlineMarkdown.Kind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            InlineMarkdown.Kind.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = Color.White.copy(alpha = .08f))
            InlineMarkdown.Kind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        }, it.start, it.end)
    }
    links.forEach {
        addLink(LinkAnnotation.Url(it.url, TextLinkStyles(SpanStyle(color = Color(0xFF02C6DD), textDecoration = TextDecoration.Underline))), it.start, it.end)
    }
}.toAnnotatedString()

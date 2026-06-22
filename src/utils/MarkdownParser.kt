package utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

sealed class MarkdownElement {
    data class Text(val annotatedString: AnnotatedString) : MarkdownElement()
    data class Quote(val annotatedString: AnnotatedString) : MarkdownElement()
    data class Image(val alt: String, val url: String) : MarkdownElement()
    data class Callout(val type: String, val title: String, val annotatedString: AnnotatedString) : MarkdownElement()
    data class MathBlock(val formula: String) : MarkdownElement()
    data class Table(val rows: List<List<AnnotatedString>>) : MarkdownElement()
    object HorizontalRule : MarkdownElement()
}

fun isWebUrl(u: String): Boolean = u.startsWith("http://") || u.startsWith("https://")

fun wikiToWebUrl(target: String): String {
    return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(target, "UTF-8")
}

private val boldItalicRegex = "(^|[^\\\\])\\*\\*\\*(.*?)\\*\\*\\*".toRegex()
private val boldRegex = "(^|[^\\\\])\\*\\*(.*?)\\*\\*".toRegex()
private val italicRegex = "(^|[^\\\\])\\*(.*?)\\*".toRegex()
private val strikeRegex = "(^|[^\\\\])~~(.*?)~~".toRegex()
private val codeRegex = "(^|[^\\\\])`(.*?)`".toRegex()
private val imageRegex = "(^|[^\\\\])!\\[(.*?)\\]\\((.*?)\\)".toRegex()
private val linkRegex = "(^|[^\\\\])\\[(.*?)\\]\\((.*?)\\)".toRegex()
private val urlRegex = "(https?://[\\w/\\-?.%=&]+)".toRegex()
private val highlightRegex = "(^|[^\\\\])==(.*?)==".toRegex()
private val tagRegex = "(^|\\s)#([\\p{L}\\d_]+)".toRegex()
private val footnoteRefRegex = "(^|[^\\\\])\\[\\^([^\\)]+)\\]".toRegex()
private val wikiLinkRegex = "(^|[^\\\\])\\[\\[(.*?)\\]\\]".toRegex()
private val blockIdRegex = "(^|\\s)\\^([a-zA-Z0-9_\\-]+)".toRegex()
private val inlineMathRegex = "(^|[^\\\\])\\$([^\\$]+)\\$".toRegex()

fun markdownInlineToAnnotated(part: String, textColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = part.lines()

        lines.forEachIndexed { lineIndex, rawLine ->
            val currentLine = rawLine.trimStart()

            var headingStyle = SpanStyle(color = textColor)
            var processedLine = currentLine
            if (currentLine.startsWith("###### ")) {
                headingStyle = SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF949BA4))
                processedLine = currentLine.removePrefix("###### ")
            } else if (currentLine.startsWith("##### ")) {
                headingStyle = SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD1D5DB))
                processedLine = currentLine.removePrefix("##### ")
            } else if (currentLine.startsWith("#### ")) {
                headingStyle = SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                processedLine = currentLine.removePrefix("#### ")
            } else if (currentLine.startsWith("### ")) {
                headingStyle = SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                processedLine = currentLine.removePrefix("### ")
            } else if (currentLine.startsWith("## ")) {
                headingStyle = SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                processedLine = currentLine.removePrefix("## ")
            } else if (currentLine.startsWith("# ")) {
                headingStyle = SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                processedLine = currentLine.removePrefix("# ")
            }

            if (processedLine.startsWith("* ")) {
                processedLine = "• " + processedLine.removePrefix("* ")
            } else if (processedLine.startsWith("- ")) {
                processedLine = "• " + processedLine.removePrefix("- ")
            } else if (processedLine.startsWith("*") && !processedLine.startsWith("**") && !processedLine.drop(1).contains("*")) {
                processedLine = "• " + processedLine.drop(1).trimStart()
            }

            withStyle(headingStyle) {
                processLineFormatting(processedLine, textColor)
            }
            if (lineIndex < lines.lastIndex) append('\n')
        }
    }
}

private fun AnnotatedString.Builder.processLineFormatting(line: String, textColor: Color) {
    var remaining = line
    while (remaining.isNotEmpty()) {
        val boldItalicMatch = boldItalicRegex.find(remaining)
        val boldMatch = boldRegex.find(remaining)
        val italicMatch = italicRegex.find(remaining)
        val strikeMatch = strikeRegex.find(remaining)
        val codeMatch = codeRegex.find(remaining)
        val imageMatch = imageRegex.find(remaining)
        val linkMatch = linkRegex.find(remaining)
        val urlMatch = urlRegex.find(remaining)
        val highlightMatch = highlightRegex.find(remaining)
        val tagMatch = tagRegex.find(remaining)
        val footnoteRefMatch = footnoteRefRegex.find(remaining)
        val wikiLinkMatch = wikiLinkRegex.find(remaining)
        val blockIdMatch = blockIdRegex.find(remaining)
        val inlineMathMatch = inlineMathRegex.find(remaining)

        val matches = listOfNotNull(
            boldItalicMatch, boldMatch, italicMatch, strikeMatch,
            codeMatch, imageMatch, linkMatch, urlMatch,
            highlightMatch, tagMatch, footnoteRefMatch, wikiLinkMatch, blockIdMatch, inlineMathMatch
        )

        val firstMatch = matches.minByOrNull { it.range.first }

        if (firstMatch == null) {
            append(unescapeMarkdown(remaining))
            break
        }

        val beforeMatch = remaining.substring(0, firstMatch.range.first)
        append(unescapeMarkdown(beforeMatch))

        when (firstMatch) {
            boldItalicMatch -> {
                append(unescapeMarkdown(boldItalicMatch.groupValues[1]))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(unescapeMarkdown(boldItalicMatch.groupValues[2]))
                }
            }
            boldMatch -> {
                append(unescapeMarkdown(boldMatch.groupValues[1]))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(unescapeMarkdown(boldMatch.groupValues[2])) }
            }
            italicMatch -> {
                append(unescapeMarkdown(italicMatch.groupValues[1]))
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(unescapeMarkdown(italicMatch.groupValues[2])) }
            }
            strikeMatch -> {
                append(unescapeMarkdown(strikeMatch.groupValues[1]))
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = textColor.copy(alpha = 0.7f))) {
                    append(unescapeMarkdown(strikeMatch.groupValues[2]))
                }
            }
            codeMatch -> {
                append(unescapeMarkdown(codeMatch.groupValues[1]))
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF2B2D31), color = Color(0xFFE3E5E8), fontSize = 14.sp)) {
                    append(" " + unescapeMarkdown(codeMatch.groupValues[2]) + " ")
                }
            }
            imageMatch -> {
                append(unescapeMarkdown(imageMatch.groupValues[1]))
                withStyle(SpanStyle(color = Color(0xFF949BA4), fontStyle = FontStyle.Italic)) {
                    append("[Зображення: ${unescapeMarkdown(imageMatch.groupValues[2])}]")
                }
            }
            linkMatch -> {
                append(unescapeMarkdown(linkMatch.groupValues[1]))
                val linkText = unescapeMarkdown(linkMatch.groupValues[2])
                val url = linkMatch.groupValues[3].trim()
                val start = this.length
                append(linkText)
                val end = this.length
                if (isWebUrl(url)) {
                    addLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = Color.White, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))), start, end)
                }
            }
            urlMatch -> {
                val url = urlMatch.value.trim()
                val start = this.length
                append(url)
                val end = this.length
                if (isWebUrl(url)) {
                    addLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = Color(0xFF38BDF8), textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))), start, end)
                }
            }
            wikiLinkMatch -> {
                append(unescapeMarkdown(wikiLinkMatch.groupValues[1]))
                val raw = unescapeMarkdown(wikiLinkMatch.groupValues[2]).trim()
                val (target, alias) = raw.split("|", limit = 2).let { parts -> parts[0].trim() to parts.getOrNull(1)?.trim() }
                val display = alias?.takeIf { it.isNotBlank() } ?: target
                val url = wikiToWebUrl(target)
                val start = this.length
                append(display)
                val end = this.length
                addLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = Color.White, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))), start, end)
            }
            blockIdMatch -> {
                append(blockIdMatch.groupValues[1])
                withStyle(SpanStyle(color = Color(0xFFF59E0B), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)) {
                    append("^" + blockIdMatch.groupValues[2])
                }
            }
            inlineMathMatch -> {
                append(unescapeMarkdown(inlineMathMatch.groupValues[1]))
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, color = Color(0xFFE3E5E8))) {
                    val rawMath = unescapeMarkdown(inlineMathMatch.groupValues[2])
                    val prettyMath = prettifyInlineMath(rawMath)
                    appendMathWithScripts(prettyMath.trim())
                }
            }
            highlightMatch -> {
                append(unescapeMarkdown(highlightMatch.groupValues[1]))
                withStyle(SpanStyle(background = Color(0xFF2F3336), color = Color.White)) {
                    append(unescapeMarkdown(highlightMatch.groupValues[2]))
                }
            }
            tagMatch -> {
                append(tagMatch.groupValues[1])
                withStyle(SpanStyle(color = Color(0xFF949BA4))) {
                    append("#" + tagMatch.groupValues[2])
                }
            }
            footnoteRefMatch -> {
                append(unescapeMarkdown(footnoteRefMatch.groupValues[1]))
                withStyle(SpanStyle(color = Color(0xFF71767B), fontSize = 11.sp, baselineShift = BaselineShift.Superscript)) {
                    append("[^" + unescapeMarkdown(footnoteRefMatch.groupValues[2]) + "]")
                }
            }
        }
        remaining = remaining.substring(firstMatch.range.last + 1)
    }
}

fun prettifyInlineMath(formula: String): String {
    var s = formula

    s = s.replace("\t" + "imes", "\\times")
    s = s.replace("\t" + "ext", "\\text")
    s = s.replace("\n" + "abl", "\\nabla")
    s = s.replace("\r" + "ho", "\\rho")
    s = s.replace("\u000C" + "rac", "\\frac")

    var oldS = ""
    while (s != oldS) {
        oldS = s
        s = s.replace(Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}"""), "$1/$2")
    }

    val funcs = listOf("cos", "sin", "tan", "cot", "ln", "log", "det", "arcsin", "arccos", "arctan")
    funcs.forEach { f ->
        s = s.replace("\\$f", f)
    }

    s = s.replace("\\int", "∫")
    s = s.replace("\\sum", "∑")
    s = s.replace("\\prod", "∏")
    s = s.replace("\\lim", "lim")
    s = s.replace("\\times", "×")
    s = s.replace("\\cdot", "·")
    s = s.replace("\\leq", "≤")
    s = s.replace("\\geq", "≥")
    s = s.replace("\\neq", "≠")
    s = s.replace("\\approx", "≈")
    s = s.replace("\\pm", "±")
    s = s.replace("\\infty", "∞")
    s = s.replace("\\nabla", "∇")
    s = s.replace("\\partial", "∂")
    s = s.replace("\\rho", "ρ")
    s = s.replace("\\alpha", "α")
    s = s.replace("\\beta", "β")
    s = s.replace("\\pi", "π")
    s = s.replace("\\mu", "μ")
    s = s.replace("\\sigma", "σ")
    s = s.replace("\\Delta", "Δ")
    s = s.replace("\\theta", "θ")

    s = s.replace("\\left(", "(").replace("\\right)", ")")
    s = s.replace("\\left[", "[").replace("\\right]", "]")
    s = s.replace("\\left|", "|").replace("\\right|", "|")
    s = s.replace("\\,", " ") // малий пробіл у LaTeX
    s = s.replace("\\;", " ")
    s = s.replace("\\quad", "  ")
    s = s.replace("\\ ", " ")

    s = s.replace(Regex("""\\text\{([^}]*)\}"""), "$1")

    s = s.replace(Regex("""\s+"""), " ")

    return s.trim()
}

fun AnnotatedString.Builder.appendMathWithScripts(text: String) {
    val scriptRegex = Regex("""([\^_])(?:\{([^}]+)\}|([a-zA-Z0-9+\-]))""")
    var lastIndex = 0

    for (match in scriptRegex.findAll(text)) {
        val before = text.substring(lastIndex, match.range.first)
        if (before.isNotEmpty()) append(before)

        val isSuperscript = match.groupValues[1] == "^"
        val content = match.groupValues[2].ifEmpty { match.groupValues[3] }

        withStyle(
            SpanStyle(
                baselineShift = if (isSuperscript) BaselineShift.Superscript else BaselineShift.Subscript,
                fontSize = 11.sp,
            )
        ) {
            append(content)
        }
        lastIndex = match.range.last + 1
    }

    val after = text.substring(lastIndex)
    if (after.isNotEmpty()) append(after)
}

fun parseMessageMarkdown(part: String, textColor: Color): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val mathRegex = Regex("""(?s)\$\$(.*?)\$\$|\\\[(.*?)\\\]|(\\begin\{[^}]+\}.*?\\end\{[^}]+\})""")

    var lastIndex = 0
    val matches = mathRegex.findAll(part)

    for (match in matches) {
        val textBefore = part.substring(lastIndex, match.range.first)
        if (textBefore.isNotBlank()) elements.addAll(parseNormalMarkdown(textBefore, textColor))

        val blockFormula =
            (match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value ?: match.value).trim()
        elements.add(MarkdownElement.MathBlock(blockFormula))

        lastIndex = match.range.last + 1
    }

    val textAfter = part.substring(lastIndex)
    if (textAfter.isNotBlank()) elements.addAll(parseNormalMarkdown(textAfter, textColor))

    return elements
}

fun parseNormalMarkdown(part: String, textColor: Color): List<MarkdownElement> {
    val lines = part.lines()
    val elements = mutableListOf<MarkdownElement>()
    val currentTextBuffer = StringBuilder()
    val currentQuoteBuffer = StringBuilder()
    val currentTableBuffer = mutableListOf<String>()
    var inQuote = false

    fun flushText() {
        if (currentTextBuffer.isNotEmpty()) {
            val text = currentTextBuffer.toString().trimEnd('\n')
            if (text.isNotEmpty()) elements.add(MarkdownElement.Text(markdownInlineToAnnotated(text, textColor)))
            currentTextBuffer.setLength(0)
        }
    }

    fun flushQuote() {
        if (currentQuoteBuffer.isNotEmpty()) {
            val quote = currentQuoteBuffer.toString().trimEnd('\n')
            if (quote.isNotEmpty()) {
                val firstLine = quote.substringBefore('\n').trim()
                val match = """^\[!(.*?)\](.*)""".toRegex().matchEntire(firstLine)
                if (match != null) {
                    val type = match.groupValues[1].lowercase()
                    val title = match.groupValues[2].trim().ifEmpty { type.replaceFirstChar { it.uppercase() } }
                    val rest = if (quote.contains('\n')) quote.substringAfter('\n') else ""
                    elements.add(MarkdownElement.Callout(type, title, markdownInlineToAnnotated(rest, textColor)))
                } else {
                    elements.add(MarkdownElement.Quote(markdownInlineToAnnotated(quote, textColor)))
                }
            }
            currentQuoteBuffer.setLength(0)
        }
    }

    fun flushTable() {
        if (currentTableBuffer.isNotEmpty()) {
            val parsedRows = currentTableBuffer.map { line ->
                line.trim().removePrefix("|").removeSuffix("|").split("|").map { cell ->
                    markdownInlineToAnnotated(cell.trim(), textColor)
                }
            }
            val filteredRows = parsedRows.filterIndexed { index, row ->
                !(index == 1 && row.all { it.text.replace("-", "").replace(":", "").isBlank() })
            }
            if (filteredRows.isNotEmpty()) elements.add(MarkdownElement.Table(filteredRows))
            currentTableBuffer.clear()
        }
    }

    fun flushAll() {
        flushText(); flushQuote(); flushTable()
    }

    for (line in lines) {
        val trimmed = line.trim()
        val imgRegex = """^!\[(.*?)\]\((.*?)\)$""".toRegex()
        val imgMatch = imgRegex.find(trimmed)

        val isTableLine = trimmed.startsWith("|") && trimmed.count { it == '|' } >= 2

        when {
            trimmed == "---" -> {
                flushAll(); inQuote = false; elements.add(MarkdownElement.HorizontalRule)
            }

            isTableLine -> {
                flushText(); flushQuote()
                inQuote = false
                currentTableBuffer.add(line)
            }

            line.trimStart().startsWith(">") -> {
                if (!inQuote) {
                    flushAll(); inQuote = true
                }
                currentQuoteBuffer.append(line.trimStart().drop(1).trim()).append('\n')
            }

            imgMatch != null -> {
                flushAll(); inQuote = false; elements.add(
                    MarkdownElement.Image(
                        imgMatch.groupValues[1],
                        imgMatch.groupValues[2]
                    )
                )
            }

            else -> {
                flushQuote(); flushTable()
                inQuote = false
                currentTextBuffer.append(line).append('\n')
            }
        }
    }
    flushAll()
    return elements
}

fun unescapeMarkdown(text: String): String {
    return text.replace("\\*", "*")
        .replace("\\~", "~")
        .replace("\\`", "`")
        .replace("\\[", "[")
        .replace("\\]", "]")
        .replace("\\(", "(")
        .replace("\\)", ")")
        .replace("\\>", ">")
        .replace("\\\\", "\\")
}

package ir.example.slmchat.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * رندر ساده‌ی Markdown برای پاسخ مدل: **bold**، *italic*، `code`، بلوک کد ```، تیتر (#) و لیست (- / *).
 * عمداً سبک و بدون کتابخانه‌ی اضافه نوشته شده است.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is Block.Para -> {
                    val annotated = remember(block.text, codeBg) { renderParagraph(block.text, codeBg) }
                    Text(annotated)
                }
                is Block.Code -> {
                    Surface(
                        color = codeBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = block.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

private sealed interface Block {
    data class Para(val text: String) : Block
    data class Code(val text: String) : Block
}

private fun parseBlocks(src: String): List<Block> {
    val out = mutableListOf<Block>()
    val para = StringBuilder()
    val code = StringBuilder()
    var inCode = false

    fun flushPara() {
        if (para.isNotBlank()) out.add(Block.Para(para.toString().trimEnd()))
        para.clear()
    }

    for (line in src.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                out.add(Block.Code(code.toString().trimEnd('\n')))
                code.clear()
                inCode = false
            } else {
                flushPara()
                inCode = true
            }
            continue
        }
        if (inCode) code.append(line).append('\n') else para.append(line).append('\n')
    }
    // بلوک کدِ هنوز-بسته‌نشده (وسط استریم)
    if (inCode) out.add(Block.Code(code.toString().trimEnd('\n')))
    flushPara()
    return out
}

private val headingRegex = Regex("^(#{1,6})\\s+(.*)")
private val bulletRegex = Regex("^[-*•]\\s+(.*)")

private fun renderParagraph(text: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    val lines = text.lines()
    lines.forEachIndexed { index, raw ->
        val line = raw.trimStart()
        val heading = headingRegex.matchEntire(line)
        val bullet = bulletRegex.matchEntire(line)
        when {
            heading != null -> {
                val size = when (heading.groupValues[1].length) {
                    1 -> 22.sp
                    2 -> 20.sp
                    else -> 18.sp
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size)) {
                    append(parseInline(heading.groupValues[2], codeBg))
                }
            }
            bullet != null -> {
                append("• ")
                append(parseInline(bullet.groupValues[1], codeBg))
            }
            else -> append(parseInline(raw, codeBg))
        }
        if (index != lines.lastIndex) append('\n')
    }
}

private fun parseInline(s: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            s.startsWith("**", i) -> {
                val end = s.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(parseInline(s.substring(i + 2, end), codeBg))
                    }
                    i = end + 2
                } else {
                    append(c)
                    i++
                }
            }
            c == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                        append(s.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }
            c == '*' && i + 1 < s.length && !s[i + 1].isWhitespace() && s[i + 1] != '*' -> {
                val end = s.indexOf('*', i + 1)
                if (end > i + 1 && !s[end - 1].isWhitespace()) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(parseInline(s.substring(i + 1, end), codeBg))
                    }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }
            else -> {
                append(c)
                i++
            }
        }
    }
}

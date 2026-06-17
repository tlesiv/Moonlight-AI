import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.Color as AwtColor
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage


fun renderLatex(
    formula: String,
    textSize: Float = 22f,
    style: Int = TeXConstants.STYLE_DISPLAY,
    fgColor: AwtColor = AwtColor.WHITE,
    bgColor: AwtColor = AwtColor(0, 0, 0, 0)
): ImageBitmap? {
    if (formula.isBlank()) return null
    return try {
        val tex = TeXFormula(formula.trim())
        val icon = tex.createTeXIcon(style, textSize).apply {
            insets = java.awt.Insets(6, 8, 6, 8)
        }

        val width = icon.iconWidth.coerceAtLeast(1)
        val height = icon.iconHeight.coerceAtLeast(1)

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2: Graphics2D = image.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        g2.color = bgColor
        g2.fillRect(0, 0, width, height)

        setTeXColor(tex, fgColor)

        icon.paintIcon(null, g2, 0, 0)
        g2.dispose()

        val result = if (fgColor == AwtColor.WHITE || fgColor == AwtColor(255, 255, 255)) {
            invertDarkPixels(image)
        } else {
            image
        }

        result.toComposeImageBitmap()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun setTeXColor(tex: TeXFormula, color: AwtColor) {
    try {
        val method = tex.javaClass.getMethod("setColor", AwtColor::class.java)
        method.invoke(tex, color)
    } catch (_: Exception) {
    }
}

private fun invertDarkPixels(src: BufferedImage): BufferedImage {
    val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until src.height) {
        for (x in 0 until src.width) {
            val argb = src.getRGB(x, y)
            val alpha = (argb shr 24) and 0xFF
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF

            if (alpha > 10) {
                val brightness = (r * 299 + g * 587 + b * 114) / 1000
                if (brightness < 128) {
                    dst.setRGB(x, y, (alpha shl 24) or 0x00FFFFFF)
                } else {
                    dst.setRGB(x, y, (alpha shl 24) or 0x00FFFFFF)
                }
            }
        }
    }
    return dst
}

fun repairLatexEscapes(input: String): String {
    var s = input

    s = s.replace("\\ight", "\\right")
    s = s.replace("\\ho", "\\rho")
    s = s.replace("\\fac", "\\frac")
    s = s.replace("\\partal", "\\partial")

    s = s.replace("\t" + "ext", "\\text")
    s = s.replace("\t" + "imes", "\\times")
    s = s.replace("\t" + "heta", "\\theta")
    s = s.replace("\t" + "au", "\\tau")
    s = s.replace("\t" + "ilde", "\\tilde")
    s = s.replace("\t" + "o", "\\to")
    s = s.replace("\t" + "an", "\\tan")

    s = s.replace("\n" + "abl", "\\nabla")
    s = s.replace("\n" + "eq", "\\neq")
    s = s.replace("\n" + "u", "\\nu")

    s = s.replace("\r" + "ho", "\\rho")
    s = s.replace("\r" + "ight", "\\right")
    s = s.replace("\r" + "angle", "\\rangle")

    s = s.replace("\u000C" + "rac", "\\frac")
    s = s.replace("\u000C" + "hi", "\\phi")

    s = s.replace("\b" + "egin", "\\begin")
    s = s.replace("\b" + "eta", "\\beta")
    s = s.replace("\b" + "inom", "\\binom")
    s = s.replace("\b" + "ar", "\\bar")

    s = s.replace("\\to", "\\rightarrow")

    val commandsToFix = listOf(
        "nabla", "nu", "neq", "rho", "right", "rangle",
        "tau", "theta", "text", "times", "tilde",
        "frac", "phi", "begin", "beta", "binom", "bar",
        "vec", "partial", "mu", "int", "sum", "infty", "left",
        "cdot", "cos", "sin", "tan", "log", "ln", "lim", "sqrt",
        "alpha", "gamma", "delta", "epsilon", "zeta", "eta", "iota", "kappa", "lambda",
        "xi", "omicron", "pi", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega",
        "rightarrow", "hbar"
    )
    commandsToFix.forEach { cmd ->
        s = s.replace("\\\\" + cmd, "\\" + cmd)
    }

    s = s.replace("\n", " ")
    s = s.replace("\r", "")

    val cyrillicMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "h", 'ґ' to "g", 'д' to "d", 'е' to "e", 'є' to "ye", 'ж' to "zh",
        'з' to "z", 'и' to "y", 'і' to "i", 'ї' to "yi", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ь' to "'", 'ю' to "yu", 'я' to "ya",
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "H", 'Ґ' to "G", 'Д' to "D", 'Е' to "E", 'Є' to "Ye", 'Ж' to "Zh",
        'З' to "Z", 'И' to "Y", 'І' to "I", 'Ї' to "Yi", 'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M", 'Н' to "N",
        'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T", 'У' to "U", 'Ф' to "F", 'Х' to "Kh", 'Ц' to "Ts",
        'Ч' to "Ch", 'Ш' to "Sh", 'Щ' to "Shch", 'Ь' to "'", 'Ю' to "Yu", 'Я' to "Ya"
    )

    s = s.replace(Regex("""([а-яА-ЯіІїЇєЄґҐ]+)""")) { match ->
        val word = match.groupValues[1]
        val latin = word.map { char -> cyrillicMap[char] ?: char }.joinToString("")
        "\\text{$latin}"
    }

    return s.trim()
}

@Composable
fun MathBlockView(formula: String, modifier: Modifier = Modifier) {
    val safeFormula = remember(formula) { repairLatexEscapes(formula) }
    var bitmap by remember(safeFormula) { mutableStateOf<ImageBitmap?>(null) }
    var isError by remember(safeFormula) { mutableStateOf(false) }

    LaunchedEffect(safeFormula) {
        bitmap = null
        isError = false
        val result = withContext(Dispatchers.IO) {
            runCatching { renderLatex(safeFormula, textSize = 22f) }
        }
        result.fold(
            onSuccess = { if (it != null) bitmap = it else isError = true },
            onFailure = { isError = true }
        )
    }

    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!,
                contentDescription = "Математична формула",
                contentScale = ContentScale.Fit,
                modifier = Modifier.widthIn(max = 700.dp)
            )
            isError -> Text(
                text = safeFormula,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
            else -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun MathInlineView(formula: String, modifier: Modifier = Modifier) {
    val safeFormula = remember(formula) { repairLatexEscapes(formula) }
    var bitmap by remember(safeFormula) { mutableStateOf<ImageBitmap?>(null) }
    var isError by remember(safeFormula) { mutableStateOf(false) }

    LaunchedEffect(safeFormula) {
        bitmap = null
        isError = false
        val result = withContext(Dispatchers.IO) {
            runCatching { renderLatex(safeFormula, textSize = 22f) }
        }
        result.fold(
            onSuccess = { if (it != null) bitmap = it else isError = true },
            onFailure = { isError = true }
        )
    }

    Box(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!,
                contentDescription = formula,
                contentScale = ContentScale.Fit
            )
            isError -> Text(
                text = "\$$safeFormula\$",
                color = Color(0xFFE3E5E8),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
            else -> CircularProgressIndicator(color = Color.White, strokeWidth = 1.5.dp, modifier = Modifier.size(16.dp))
        }
    }
}


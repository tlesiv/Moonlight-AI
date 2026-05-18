import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Moonlight",
        icon = painterResource("/images/The icon.svg"),
        state = rememberWindowState(width = 1100.dp, height = 700.dp)
    ) {
        val customTextSelectionColors = TextSelectionColors(
            handleColor = Color(0xFFFFFFFF),
            backgroundColor = Color(0xFFFFFFFF).copy(alpha = 0.3f)
        )

        MaterialTheme {
            CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                ChatApp()
            }
        }
    }
}


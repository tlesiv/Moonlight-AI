package ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utils.MarkdownElement
import utils.MathBlockView
import utils.isWebUrl
import utils.parseMessageMarkdown
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) Color(0xFF16181C) else Color.Transparent
    val textColor = Color(0xFFE7E9EA)
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val clipboardManager = LocalClipboardManager.current

    val columnModifier = if (isUser) {
        Modifier.wrapContentWidth().widthIn(max = 680.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = columnModifier
        ) {
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, borderColor),
                modifier = if (isUser) Modifier.wrapContentWidth() else Modifier.fillMaxWidth()
            ) {
                Column(modifier = if (isUser) Modifier.wrapContentWidth() else Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 10.dp,
                                bottom = if (isUser) 10.dp else 0.dp
                            )
                            .then(
                                if (isUser) Modifier.widthIn(max = 648.dp)
                                else Modifier.fillMaxWidth()
                            )
                    ) {
                        // FILES
                        if (message.attachmentPaths.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .padding(bottom = if (message.text.isNotBlank()) 10.dp else 0.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                message.attachmentPaths.forEach { path ->
                                    val file = File(path)
                                    val isImage =
                                        file.extension.lowercase() in listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

                                    if (isImage) {
                                        var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
                                        LaunchedEffect(file) {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    if (file.exists()) {
                                                        FileInputStream(file).use { bitmap = loadImageBitmap(it) }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }

                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap!!,
                                                contentDescription = "Chat image preview",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        try {
                                                            if (java.awt.Desktop.isDesktopSupported() && file.exists()) {
                                                                java.awt.Desktop.getDesktop().open(file)
                                                            }
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF16181C)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    strokeWidth = 3.dp,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            color = if (isUser) Color(0xFF2B2D31) else Color(0xFF1E1F22),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, borderColor),
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clickable {
                                                    try {
                                                        if (java.awt.Desktop.isDesktopSupported() && file.exists()) {
                                                            java.awt.Desktop.getDesktop().open(file)
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                                .pointerHoverIcon(PointerIcon.Hand)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(6.dp),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    imageVector = DocumentIcon,
                                                    contentDescription = "File",
                                                    tint = Color(0xFF949BA4),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = file.name,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                            // TEXTS
                        if (message.text.isNotBlank()) {
                            val parts = message.text.split("```")
                            parts.forEachIndexed { index, part ->
                                if (index % 2 == 1) {
                                    val newlineIndex = part.indexOf('\n')
                                    val language =
                                        if (newlineIndex != -1) part.substring(0, newlineIndex).trim() else ""
                                    val code =
                                        if (newlineIndex != -1) part.substring(newlineIndex + 1).trimEnd() else part

                                    Surface(
                                        color = Color(0xFF0D0D0D),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().background(Color(0xFF2B2D31))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = language.ifEmpty { "code" },
                                                    color = Color(0xFF949BA4),
                                                    fontSize = 12.sp
                                                )
                                                CircleIconButton(
                                                    painter = painterResource("/images/copy_icon_lighter.svg"),
                                                    onClick = { clipboardManager.setText(AnnotatedString(code)) },
                                                    tint = Color(0xFF949BA4), buttonSize = 28.dp, iconSize = 16.dp
                                                )
                                            }
                                            Text(
                                                text = code,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFFE3E5E8),
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                } else {
                                    if (part.isNotEmpty()) {
                                        val markdownElements = remember(part) { parseMessageMarkdown(part, textColor) }

                                        markdownElements.forEach { element ->
                                            when (element) {
                                                is MarkdownElement.Text -> {
                                                    ClickableText(
                                                        text = element.annotatedString,
                                                        style = TextStyle(color = textColor, fontSize = 15.sp),
                                                        onClick = { offset ->
                                                            val link = element.annotatedString.getLinkAnnotations(
                                                                offset,
                                                                offset
                                                            ).firstOrNull()
                                                            val url = (link?.item as? LinkAnnotation.Url)?.url

                                                            if (url != null && isWebUrl(url)) {
                                                                try {
                                                                    java.awt.Desktop.getDesktop()
                                                                        .browse(java.net.URI(url))
                                                                } catch (_: Exception) {
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.padding(bottom = 6.dp)
                                                    )
                                                }

                                                is MarkdownElement.Table -> {
                                                    Surface(
                                                        color = Color.Transparent,
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = BorderStroke(1.dp, borderColor),
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            element.rows.forEachIndexed { rowIndex, row ->
                                                                val isHeader = rowIndex == 0
                                                                val bg =
                                                                    if (isHeader) Color(0xFF2B2D31) else Color.Transparent
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth().background(bg)
                                                                ) {
                                                                    row.forEach { cell ->
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .weight(1f)
                                                                                .border(0.5.dp, borderColor)
                                                                                .padding(8.dp),
                                                                            contentAlignment = Alignment.CenterStart
                                                                        ) {
                                                                            Text(
                                                                                text = cell,
                                                                                color = if (isHeader) Color.White else textColor,
                                                                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                                                                fontSize = 13.sp
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                }

                                                is MarkdownElement.MathBlock -> {
                                                    MathBlockView(formula = element.formula)
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                }


                                                is MarkdownElement.Quote -> {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(IntrinsicSize.Min)
                                                            .background(Color(0xFF1E1F22), RoundedCornerShape(8.dp))
                                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(3.dp)
                                                                .fillMaxHeight()
                                                                .background(Color.White, RoundedCornerShape(8.dp))
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(
                                                            text = element.annotatedString,
                                                            style = TextStyle(
                                                                color = Color(0xFFA1A1AA),
                                                                fontSize = 15.sp,
                                                                fontStyle = FontStyle.Italic
                                                            ),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                }

                                                is MarkdownElement.HorizontalRule -> {
                                                    Divider(
                                                        color = Color(0xFF3F4147),
                                                        thickness = 1.dp,
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                                    )
                                                }

                                                is MarkdownElement.Callout -> {
                                                    val (bgColor, accentColor, icon) = when (element.type) {
                                                        "info" -> Triple(
                                                            Color(0xFF2B2D31),
                                                            Color.White,
                                                            Icons.Default.Info
                                                        )

                                                        "tip", "hint", "порада" -> Triple(
                                                            Color(0xFF2B2D31),
                                                            Color(0xFF10B981),
                                                            Icons.Default.Info
                                                        )

                                                        "warning", "caution" -> Triple(
                                                            Color(0xFF2B2D31),
                                                            Color(0xFFFDE047),
                                                            Icons.Default.Warning
                                                        )

                                                        "success", "check" -> Triple(
                                                            Color(0xFF2B2D31),
                                                            Color(0xFF34D399),
                                                            Icons.Default.Check
                                                        )

                                                        "error", "danger", "bug" -> Triple(
                                                            Color(0xFF2B2D31),
                                                            Color(0xFFEF4444),
                                                            Icons.Default.Warning
                                                        )

                                                        else -> Triple(
                                                            Color(0xFF2B2D31),
                                                            Color.White,
                                                            Icons.Default.Info
                                                        )
                                                    }
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp)
                                                            .background(bgColor, RoundedCornerShape(8.dp))
                                                            .border(
                                                                1.dp,
                                                                accentColor.copy(alpha = 0.2f),
                                                                RoundedCornerShape(8.dp)
                                                            )
                                                            .padding(12.dp)
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                icon,
                                                                contentDescription = null,
                                                                tint = accentColor,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(
                                                                text = element.title,
                                                                color = accentColor,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp
                                                            )
                                                        }
                                                        if (element.annotatedString.text.isNotEmpty()) {
                                                            Spacer(Modifier.height(4.dp))
                                                            Text(
                                                                text = element.annotatedString,
                                                                style = TextStyle(
                                                                    color = Color(0xFFE7E9EA),
                                                                    fontSize = 14.sp,
                                                                    lineHeight = 20.sp
                                                                )
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                }

                                                is MarkdownElement.Image -> {
                                                    var isDismissed by remember(element.url) { mutableStateOf(false) }
                                                    AnimatedVisibility(
                                                        visible = !isDismissed,
                                                        enter = fadeIn(tween(400)) + expandVertically(
                                                            tween(400),
                                                            expandFrom = Alignment.Top
                                                        ),
                                                        exit = fadeOut(tween(400)) + shrinkVertically(
                                                            tween(400),
                                                            shrinkTowards = Alignment.Top
                                                        )
                                                    ) {
                                                        Column(modifier = Modifier.fillMaxWidth()) {
                                                            var bitmap by remember(element.url) {
                                                                mutableStateOf<ImageBitmap?>(
                                                                    null
                                                                )
                                                            }
                                                            var isError by remember(element.url) { mutableStateOf(false) }

                                                            LaunchedEffect(element.url) {
                                                                withContext(Dispatchers.IO) {
                                                                    try {
                                                                        java.net.URI(element.url).toURL().openStream()
                                                                            .use { stream ->
                                                                                bitmap = loadImageBitmap(stream)
                                                                            }
                                                                    } catch (e: Exception) {
                                                                        e.printStackTrace()
                                                                        isError = true
                                                                    }
                                                                }
                                                            }

                                                            Box(
                                                                modifier = Modifier
                                                                    .padding(vertical = 6.dp)
                                                                    .widthIn(max = 500.dp)
                                                                    .clip(RoundedCornerShape(12.dp))
                                                                    .background(Color(0xFF16181C))
                                                                    .border(
                                                                        BorderStroke(1.dp, borderColor),
                                                                        RoundedCornerShape(12.dp)
                                                                    )
                                                            ) {
                                                                if (bitmap != null) {
                                                                    Image(
                                                                        bitmap = bitmap!!,
                                                                        contentDescription = element.alt,
                                                                        contentScale = ContentScale.Fit,
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clickable {
                                                                                try {
                                                                                    if (java.awt.Desktop.isDesktopSupported()) {
                                                                                        java.awt.Desktop.getDesktop()
                                                                                            .browse(java.net.URI(element.url))
                                                                                    }
                                                                                } catch (e: Exception) {
                                                                                    e.printStackTrace()
                                                                                }
                                                                            }
                                                                            .pointerHoverIcon(PointerIcon.Hand)
                                                                    )
                                                                } else if (isError) {
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .background(Color(0xFF292525))
                                                                            .padding(
                                                                                horizontal = 14.dp,
                                                                                vertical = 10.dp
                                                                            ),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                                    ) {
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            modifier = Modifier.weight(1f)
                                                                        ) {
                                                                            Spacer(modifier = Modifier.width(10.dp))
                                                                            Text(
                                                                                text = "Не вдалося завантажити зображення",
                                                                                color = Color.White,
                                                                                fontSize = 14.sp,
                                                                                fontFamily = FontFamily.SansSerif,
                                                                                fontWeight = FontWeight.Medium
                                                                            )
                                                                        }
                                                                        Icon(
                                                                            imageVector = Icons.Default.Close,
                                                                            contentDescription = "Dismiss error",
                                                                            tint = Color(0xFF71767B),
                                                                            modifier = Modifier
                                                                                .size(18.dp)
                                                                                .clickable { isDismissed = true }
                                                                                .pointerHoverIcon(PointerIcon.Hand)
                                                                        )
                                                                    }
                                                                } else {
                                                                    Box(
                                                                        modifier = Modifier.fillMaxWidth()
                                                                            .height(150.dp),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        CircularProgressIndicator(
                                                                            color = Color.White,
                                                                            strokeWidth = 2.dp,
                                                                            modifier = Modifier.size(26.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                        }
                                                    }

                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!isUser) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 2.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CircleIconButton(
                                painter = painterResource("/images/copy_icon.svg"),
                                onClick = { clipboardManager.setText(AnnotatedString(message.text)) },
                                tint = Color(0xFF71767B), buttonSize = 32.dp, iconSize = 16.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
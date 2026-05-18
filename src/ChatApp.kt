import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete

val borderColor = Color(0xFF2F3336)

@Composable
fun ChatApp() {
    val sessions = remember {
        mutableStateListOf<ChatSession>().also { existing ->
            existing.addAll(loadChats())
            if (existing.isEmpty()) {
                existing.add(newChatSession("Chat 1"))
            }
        }
    }
    var activeChatId by remember { mutableStateOf(sessions.first().id) }
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()


    val activeChat = sessions.first { it.id == activeChatId }
    val messages = activeChat.messages

    val onSelectChat = { id: String ->
        activeChatId = id
        input = ""
        errorText = null
    }

    var chatToRename by remember { mutableStateOf<ChatSession?>(null) }

    val onTogglePin = { session: ChatSession ->
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index != -1) {
            sessions[index] = session.copy(isPinned = !session.isPinned)
            saveChats(sessions)
        }
    }

    val onNewChat = {
        val title = "Chat ${sessions.size + 1}"
        val session = newChatSession(title)
        sessions.add(0, session)
        activeChatId = session.id
        input = ""
        saveChats(sessions)
    }

    val onDeleteChat = { id: String ->
        val index = sessions.indexOfFirst { it.id == id }
        if (index != -1) {
            val wasActive = id == activeChatId
            sessions.removeAt(index)
            if (sessions.isEmpty()) {
                val session = newChatSession("Chat 1")
                sessions.add(session)
                activeChatId = session.id
            } else if (wasActive) {
                activeChatId = sessions.first().id
            }
            input = ""
            saveChats(sessions)
        }
    }

    val onSend = {
        val trimmed = input.trim()
        if (trimmed.isNotEmpty() && !isLoading) {
            val userMessage = newMessage("user", trimmed)
            messages.add(userMessage)
            saveChats(sessions)
            input = ""
            errorText = null
            isLoading = true
            scope.launch {
                val apiKey = System.getenv("GEMINI_API_KEY").takeUnless { it.isNullOrBlank() }
                    ?: GeminiApiKey.takeIf { it.isNotBlank() }
                val model = System.getenv("GEMINI_MODEL") ?: DefaultModel
                if (apiKey.isNullOrBlank()) {
                    messages.add(newMessage("assistant", "Missing GEMINI_API_KEY environment variable or GeminiApiKey constant."))
                    saveChats(sessions)
                    isLoading = false
                    return@launch
                }
                if (activeChat.title.startsWith("Chat ") && messages.size == 1) {
                    val titleResult = callGeminiTitle(apiKey = apiKey, model = model, userMessage = trimmed)
                    if (titleResult.isSuccess) {
                        activeChat.title = titleResult.getOrNull().orEmpty().take(28).ifBlank { activeChat.title }
                        saveChats(sessions)
                    }
                }

                val assistantMessage = newMessage("assistant", "")
                messages.add(assistantMessage)
                saveChats(sessions)

                var currentText = ""

                val result = withContext(Dispatchers.IO) {
                    callGeminiStream(apiKey = apiKey, model = model, history = messages.dropLast(1)) { chunk ->
                        currentText += chunk

                        withContext(Dispatchers.Main) {
                            val index = messages.indexOfFirst { it.id == assistantMessage.id }
                            if (index != -1) {
                                messages[index] = messages[index].copy(text = currentText)
                            }
                        }
                    }
                }

                if (result.isFailure) {
                    val message = result.exceptionOrNull()?.message ?: "Unknown error"
                    errorText = message
                    val index = messages.indexOfFirst { it.id == assistantMessage.id }
                    if (index != -1) {
                        messages[index] = messages[index].copy(text = "Error: $message")
                    }
                }

                saveChats(sessions)
                isLoading = false
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val isNarrow = maxWidth < 700.dp
        if (isNarrow) {
            SingleColumnChat(
                messages = messages,
                input = input,
                onInputChange = { input = it },
                onSend = onSend,
                isLoading = isLoading,
                errorText = errorText
            )
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    chats = sessions.sortedByDescending { it.isPinned }, // Закріплені чати будуть завжди зверху!
                    activeChatId = activeChatId,
                    onSelectChat = onSelectChat,
                    onNewChat = onNewChat,
                    onDeleteChat = onDeleteChat,
                    onRenameChat = { chatToRename = it }, // ДОДАНО
                    onTogglePin = onTogglePin,            // ДОДАНО
                    modifier = Modifier.width(240.dp).fillMaxHeight()
                )
                Divider(
                    color = borderColor,
                    modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp))
                SingleColumnChat(
                    messages = messages,
                    input = input,
                    onInputChange = { input = it },
                    onSend = onSend,
                    modifier = Modifier.weight(1f),
                    isLoading = isLoading,
                    errorText = errorText
                )
            }
        }
    }
    if (chatToRename != null) {
        var newTitle by remember { mutableStateOf(chatToRename!!.title) }
        AlertDialog(
            onDismissRequest = { chatToRename = null },
            backgroundColor = Color(0xFF16181C),
            title = { Text("Rename", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        focusedBorderColor = Color(0xFF1D9BF0),
                        unfocusedBorderColor = Color(0xFF2F3336),
                        cursorColor = Color.White
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            val index = sessions.indexOfFirst { it.id == chatToRename!!.id }
                            if (index != -1) {
                                sessions[index] = sessions[index].copy(title = newTitle)
                                saveChats(sessions)
                            }
                        }
                        chatToRename = null
                    }
                ) { Text("Зберегти", color = Color(0xFF1D9BF0)) }
            },
            dismissButton = {
                TextButton(onClick = { chatToRename = null }) {
                    Text("Скасувати", color = Color(0xFF71767B))
                }
            }
        )
    }
}

@Composable
private fun Sidebar(
    chats: List<ChatSession>,
    activeChatId: String,
    onSelectChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (String) -> Unit,
    onRenameChat: (ChatSession) -> Unit,
    onTogglePin: (ChatSession) -> Unit,
    modifier: Modifier = Modifier
) {
    var hoveredChatId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.background(Color.Black).padding(16.dp)) {
        Text(text = "Chats", fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.White,
                contentColor = Color.Black
            ),
            elevation = ButtonDefaults.elevation(0.dp)
        ) {
            Text("New chat", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 8.dp) // Відступ для повзунка
            ) {
                items(items = chats, key = { it.id }) { session ->
                    val isActive = session.id == activeChatId
                    val bg = if (isActive) Color(0xFF16181C) else Color.Transparent
                    val textColor = if (isActive) Color.White else Color(0xFF71767B)
                    val borderColor = if (isActive) Color(0xFF2F3336) else Color.Transparent
                    val isHovered = hoveredChatId == session.id


                    var menuExpanded by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(bg, RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(16.dp))
                            .clickable { onSelectChat(session.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .pointerInput(session.id) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            PointerEventType.Enter -> hoveredChatId = session.id
                                            PointerEventType.Exit -> if (hoveredChatId == session.id) hoveredChatId =
                                                null

                                            else -> Unit
                                        }
                                    }
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (session.isPinned) {
                                Icon(
                                    painter = painterResource("/images/pin_icon.svg"),
                                    contentDescription = "Pinned",
                                    tint = Color(0xFF71767B),
                                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                                )
                            }
                            Text(
                                text = session.title,
                                color = textColor,
                                maxLines = 1,
                                fontSize = 14.sp,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isHovered || menuExpanded) {
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Опції чату",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                MaterialTheme(
                                    colors = MaterialTheme.colors.copy(surface = Color(0xFF2B2D31)), // Темно-сірий фон меню
                                    shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(12.dp)) // Закруглені краї
                                ) {DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                    modifier = Modifier.border(1.dp, Color(0xFF3F4147), RoundedCornerShape(12.dp)).width(200.dp)
                                ) {
                                    // Rename
                                    DropdownMenuItem(onClick = {
                                        menuExpanded = false
                                        onRenameChat(session)
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Rename", color = Color.White, fontSize = 14.sp)
                                    }

                                    // Pin/Unpin
                                    DropdownMenuItem(onClick = {
                                        menuExpanded = false
                                        onTogglePin(session)
                                    }) {
                                        val pinIcon = if (session.isPinned) "/images/unpin_icon.svg" else "/images/pin_icon.svg"
                                        val text = if (session.isPinned) "Unpin" else "Pin"
                                        Icon(
                                            painter = painterResource(pinIcon),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(text, color = Color.White, fontSize = 14.sp)
                                    }

                                    // Delete
                                    DropdownMenuItem(onClick = {
                                        menuExpanded = false
                                        onDeleteChat(session.id)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Delete", color = Color.White, fontSize = 14.sp)
                                    }
                                }
                                    }
                                }

                        }
                    }
                }}


            CompositionLocalProvider(
                LocalScrollbarStyle provides defaultScrollbarStyle().copy(
                    unhoverColor = Color(0xFF2F3336), // Колір у спокої
                    hoverColor = Color(0xFF71767B),   // Колір при наведенні
                    shape = RoundedCornerShape(4.dp),
                    thickness = 6.dp
                )
            ) {
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState)
                )
            }
        }
    }
}

@Composable
private fun SingleColumnChat(
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorText: String? = null
) {
    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Text(text = "Moonlight AI", fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))
        if (errorText != null) {
            Text(text = errorText, color = Color(0xFFB91C1C))
            Spacer(modifier = Modifier.height(8.dp))
        }
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = Color.Transparent,
            shape = RoundedCornerShape(12.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(items = messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyUp && !isLoading && input.isNotBlank()) {
                            onSend()
                            true
                        } else {
                            false
                        }
                    },
                placeholder = { Text("Type a message...", color = Color(0xFF71767B)) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    backgroundColor = Color(0xFF16181C), // Темно-сірий фон поля
                    focusedBorderColor = Color(0xFF2F3336),
                    unfocusedBorderColor = Color(0xFF2F3336), // Сіра рамка без фокусу
                    cursorColor = Color.White
                ),
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (!isLoading && input.isNotBlank()) onSend() }
                )
            )
            Button(
                onClick = onSend,
                enabled = !isLoading && input.isNotBlank(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.White,             // Колір активної кнопки
                    contentColor = Color.Black,                // Колір тексту активної кнопки
                    disabledBackgroundColor = Color(0xFFFFFFF), // Колір кнопки ПІД ЧАС ЗАВАНТАЖЕННЯ (темно-сірий)
                    disabledContentColor = Color(0xFF71767B)     // Колір контенту неактивної кнопки
                ),
                elevation = ButtonDefaults.elevation(0.dp)
            ) {
                if (isLoading) {//Колір крутілки завантаження
                    CircularProgressIndicator(
                        color = Color(0xFF71767B),
                        modifier = Modifier.width(18.dp).height(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send", fontWeight = FontWeight.Bold)
                }
            }

        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) Color(0xFF16181C) else Color.Transparent
    val textColor = Color(0xFFE7E9EA)
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val clipboardManager = LocalClipboardManager.current

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier
                .border(1.dp, borderColor, RoundedCornerShape(16.dp)))
        {
            SelectionContainer {
                Surface(
                    color = bubbleColor,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    val annotatedString = markdownToAnnotated(message.text)

                    Text(
                        text = annotatedString,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .widthIn(max = 1200.dp),
                        style = TextStyle(color = textColor, fontSize = 15.sp)
                    )
                }
            }
            if (!isUser) {
                Spacer(modifier = Modifier.height(4.dp))

                TooltipArea(
                    tooltip = {
                        Surface(
                            color = Color(0xFF2F3336),
                            shape = RoundedCornerShape(4.dp),
                            elevation = 4.dp
                        ) {
                            Text(
                                text = "Copy",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    modifier = Modifier.padding(start = 4.dp),
                    tooltipPlacement = TooltipPlacement.ComponentRect(
                        anchor = Alignment.BottomCenter,
                        alignment = Alignment.BottomCenter,
                    )
                ) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.text))
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            painter = painterResource("/images/copy_button.svg"),
                            contentDescription = "Copy message",
                            tint = Color(0xFF71767B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

        }
    }
}

private fun markdownToAnnotated(text: String): AnnotatedString {
    val normalized = text.replace("\\n", "\n")
    val lines = normalized.split('\n')
    return buildAnnotatedString {
        var inCodeBlock = false
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd()
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                if (index != lines.lastIndex) append("\n")
                return@forEachIndexed
            }
            if (inCodeBlock) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(rawLine)
                }
                if (index != lines.lastIndex) append("\n")
                return@forEachIndexed
            }

            when {
                line.startsWith("### ") -> appendHeading(line.drop(4), 16.sp)
                line.startsWith("## ") -> appendHeading(line.drop(3), 18.sp)
                line.startsWith("# ") -> appendHeading(line.drop(2), 20.sp)
                line.startsWith(">") -> {
                    withStyle(SpanStyle(
                        background = Color(0xFFF3F4F6), // Світло-сірий фон
                        color = Color(0xFF4B5563),      // Темно-сірий текст
                        fontStyle = FontStyle.Italic
                    )) {
                        append(" ┃ ")
                        appendInlineMarkdown(line.drop(1).trim())
                    }
                }
                line.matches(Regex("\\d+\\.\\s+.*")) -> {
                    val split = line.indexOf('.')
                    append(line.substring(0, split + 1))
                    append(' ')
                    appendInlineMarkdown(line.substring(split + 1).trim())
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    append("• ")
                    appendInlineMarkdown(line.drop(2))
                }
                line.contains("|") && line.trim().startsWith("|") -> {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(rawLine)
                    }
                }
                else -> appendInlineMarkdown(rawLine)
            }
            if (index != lines.lastIndex) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.appendHeading(text: String, size: androidx.compose.ui.unit.TextUnit) {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size)) {
        append(text)
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    var i = 0
    var bold = false
    var italic = false
    var strike = false
    var code = false
    while (i < text.length) {
        if (!code && i + 1 < text.length && text[i] == '~' && text[i + 1] == '~') {
            strike = !strike
            i += 2
            continue
        }
        if (!code && i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
            bold = !bold
            i += 2
            continue
        }
        if (!code && text[i] == '*') {
            italic = !italic
            i += 1
            continue
        }
        if (text[i] == '`') {
            code = !code
            i += 1
            continue
        }
        if (!code && text[i] == '[') {
            val endText = text.indexOf(']', i + 1)
            val startUrl = if (endText != -1 && endText + 1 < text.length && text[endText + 1] == '(') endText + 2 else -1
            val endUrl = if (startUrl != -1) text.indexOf(')', startUrl) else -1

            if (endText != -1 && startUrl != -1 && endUrl != -1) {
                val linkText = text.substring(i + 1, endText)
                val url = text.substring(startUrl, endUrl)

                val start = this.length
                append(linkText)
                val end = this.length

                addLink(
                    url = LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = Color.White,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    ),
                    start = start,
                    end = end
                )

                i = endUrl + 1
                continue
            }

        }
        if (!code && i + 1 < text.length && text[i] == '!' && text[i + 1] == '[') {
            val endAlt = text.indexOf(']', i + 2)
            val startUrl = if (endAlt != -1 && endAlt + 1 < text.length && text[endAlt + 1] == '(') endAlt + 2 else -1
            val endUrl = if (startUrl != -1) text.indexOf(')', startUrl) else -1
            if (endAlt != -1 && startUrl != -1 && endUrl != -1) {
                val alt = text.substring(i + 2, endAlt)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFF6B7280))) {
                    append("[image: $alt]")
                }
                i = endUrl + 1
                continue
            }
        }
        val style = SpanStyle(
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
            fontFamily = if (code) FontFamily.Monospace else FontFamily.Default
        )
        withStyle(style) {
            append(text[i])
        }
        i += 1
    }
}

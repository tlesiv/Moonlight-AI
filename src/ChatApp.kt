import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.io.File
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import java.io.FileInputStream

val borderColor = Color(0xFF2F3336)

@Composable
fun ChatApp() {
    val sessions = remember {
        mutableStateListOf<ChatSession>().also { existing ->
            existing.addAll(loadChats())
            val startupChat = newChatSession("New Chat")
            existing.add(0, startupChat)
//            if (existing.isEmpty()) { // для відкриття з останнім чатом, а не створення нового при кожному запуску
//                existing.add(newChatSession("Chat 1"))
//            }
        }
    }
    var activeChatId by remember { mutableStateOf(sessions.first().id) }
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var expandedMenuChatId by remember { mutableStateOf<String?>(null) }
    var editingChatId by remember { mutableStateOf<String?>(null) }


    val activeChat = sessions.first { it.id == activeChatId }
    val messages = activeChat.messages

    val onSelectChat = { id: String ->
        activeChatId = id
        input = ""
        errorText = null
    }


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
                val session = newChatSession("New Chat")
                sessions.add(session)
                activeChatId = session.id
            } else if (wasActive) {
                activeChatId = sessions.first().id
            }
            input = ""
            saveChats(sessions)
        }
    }

    val onSend = { attachedFiles: List<File>, modelName: String ->
        val trimmed = input.trim()

        if ((trimmed.isNotEmpty() || attachedFiles.isNotEmpty()) && !isLoading) {
            val userMessage = ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                role = "user",
                text = trimmed,
                attachmentPaths = attachedFiles.map { it.absolutePath }
            )

            messages.add(userMessage)
            saveChats(sessions)
            input = ""
            errorText = null
            isLoading = true

            scope.launch {
                val assistantMessage = newMessage("assistant", "")
                messages.add(assistantMessage)
                saveChats(sessions)

                var currentText = ""

                suspend fun runModel(targetModel: String): Result<Unit> {
                    return if (targetModel == "Gemini 3.1 Flash_Lite") {
                        val apiKey = System.getenv("GEMINI_API_KEY").takeUnless { it.isNullOrBlank() }
                            ?: GeminiApiKey.takeIf { it.isNotBlank() }
                        val model = System.getenv("GEMINI_MODEL") ?: DefaultModel

                        if (apiKey.isNullOrBlank()) return Result.failure(Exception("Missing Gemini API Key"))

                        if ((activeChat.title.startsWith("Chat ") || activeChat.title == "New Chat") && messages.size == 2) {
                            val titleResult = withContext(Dispatchers.IO) {
                                callGeminiTitle(apiKey = apiKey, model = model, userMessage = trimmed)
                            }
                            if (titleResult.isSuccess) {
                                val newTitle = titleResult.getOrNull().orEmpty().take(28).ifBlank { activeChat.title }
                                val index = sessions.indexOfFirst { it.id == activeChat.id }
                                if (index != -1) sessions[index] = sessions[index].copy(title = newTitle)
                                saveChats(sessions)
                            }
                        }

                        withContext(Dispatchers.IO) {
                            callGeminiStream(apiKey = apiKey, model = model, history = messages.dropLast(1)) { chunk ->
                                currentText += chunk
                                withContext(Dispatchers.Main) {
                                    val index = messages.indexOfFirst { it.id == assistantMessage.id }
                                    if (index != -1) messages[index] = messages[index].copy(text = currentText)
                                }
                            }
                        }
                    } else {
                        val openRouterKey = System.getenv("OPENROUTER_API_KEY").takeUnless { it.isNullOrBlank() }
                            ?: OpenRouterApiKey.takeIf { it.isNotBlank() }

                        if (openRouterKey.isNullOrBlank()) return Result.failure(Exception("Missing OpenRouter API Key"))

                        val openRouterModelId = when (targetModel) {
                            "Gemma 4" -> "google/gemma-4-31b-it:free"
                            else -> "google/gemma-4-31b-it:free"
                        }

                        withContext(Dispatchers.IO) {
                            callOpenRouterStream(
                                apiKey = openRouterKey,
                                model = openRouterModelId,
                                history = messages.dropLast(1)
                            ) { chunk ->
                                currentText += chunk
                                withContext(Dispatchers.Main) {
                                    val index = messages.indexOfFirst { it.id == assistantMessage.id }
                                    if (index != -1) messages[index] = messages[index].copy(text = currentText)
                                }
                            }
                        }
                    }
                }

                var result = runModel(modelName)

                if (result.isFailure) {
                    val fallbackModel = if (modelName == "Gemini 3.1 Flash_Lite") "Gemma 4" else "Gemini 3.1 Flash_Lite"
                    val exMsg = result.exceptionOrNull()?.message ?: "Unknown error"

                    currentText = ">**Сервер $modelName тимчасово недоступний.**\n> *Перемикаюсь на резервну модель ($fallbackModel)...*\n\n"

                    withContext(Dispatchers.Main) {
                        val index = messages.indexOfFirst { it.id == assistantMessage.id }
                        if (index != -1) messages[index] = messages[index].copy(text = currentText)
                    }

                    result = runModel(fallbackModel)

                    if (result.isFailure) {
                        val finalExMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                        errorText = "Обидві моделі недоступні"
                        withContext(Dispatchers.Main) {
                            val index = messages.indexOfFirst { it.id == assistantMessage.id }
                            if (index != -1) messages[index] = messages[index].copy(text = currentText + "❌ **Критична помилка резервної моделі:** $finalExMsg")
                        }
                    }
                }

                saveChats(sessions)
                isLoading = false
            }
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val isNarrow = maxWidth < 700.dp

        val currentActiveChat = sessions.firstOrNull { it.id == activeChatId }

        if (isNarrow) {
            Crossfade(
                targetState = currentActiveChat,
                animationSpec = tween(durationMillis = 500),
                label = "chat_transition"
            ) { chat ->
                if (chat != null) {
                    SingleColumnChat(
                        messages = chat.messages,
                        input = input,
                        onInputChange = { input = it },
                        onSend = onSend,
                        isLoading = isLoading,
                        errorText = errorText
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    chats = sessions.sortedByDescending { it.isPinned },
                    activeChatId = activeChatId,
                    onSelectChat = onSelectChat,
                    expandedMenuChatId = expandedMenuChatId,
                    onSetExpandedMenuChatId = { expandedMenuChatId = it },
                    onNewChat = onNewChat,
                    onDeleteChat = onDeleteChat,
                    editingChatId = editingChatId,
                    onSetEditingChatId = { editingChatId = it },
                    onRenameChat = { id, newTitle ->
                        val index = sessions.indexOfFirst { it.id == id }
                        if (index != -1) {
                            sessions[index] = sessions[index].copy(title = newTitle)
                            saveChats(sessions)
                        }
                    },
                    onTogglePin = onTogglePin,
                    modifier = Modifier.width(240.dp).fillMaxHeight()
                )
                Divider(
                    color = borderColor,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                )

                Crossfade(
                    targetState = currentActiveChat,
                    modifier = Modifier.weight(1f),
                    animationSpec = tween(durationMillis = 500),//Анімація при зміні чатів(головний екран)
                    label = "chat_transition"
                ) { chat ->
                    if (chat != null) {
                        SingleColumnChat(
                            messages = chat.messages,
                            input = input,
                            onInputChange = { input = it },
                            onSend = onSend,
                            modifier = Modifier.fillMaxSize(),
                            isLoading = isLoading,
                            errorText = errorText
                        )

                        if (expandedMenuChatId != null || editingChatId != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                if (event.type == PointerEventType.Press) {
                                                    expandedMenuChatId = null
                                                    editingChatId = null
                                                }
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }

        }
    }
}
@Composable
fun MoonlightTypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_indicator")

    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot3"
    )

    Row(
        modifier = modifier
            .size(36.dp)
            .background(Color.Transparent, CircleShape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dots = listOf(dot1, dot2, dot3)
        dots.forEach { value ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)//Відступ між крапками
                    .size(5.3.dp)
                    .offset(y = (-6).dp * value)// Підстрибування вгору
                    .clip(CircleShape)
                    .background(Color(0xFF949BA4).copy(alpha = 0.3f + (0.7f * value)))
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatInputRow(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: (List<File>, String) -> Unit,
    isLoading: Boolean,
    isNewChat: Boolean = false
) {
    val placeholders = listOf(
        "What's on your mind?",
        "Ask me anything...",
        "How can I help you today?",
        "Type a message..."
    )

    var currentIndex by remember { mutableStateOf(0) }
    var attachedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(input)) }

    var selectedModel by remember { mutableStateOf("Gemini 3.1 Flash_Lite") }
    var menuExpanded by remember { mutableStateOf(false) }

    var displayFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(attachedFiles) {
        if (attachedFiles.isNotEmpty()) {
            displayFiles = attachedFiles
        }
    }

    LaunchedEffect(input) {
        if (input != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = input,
                selection = TextRange(input.length)
            )
        }
    }

    val filePicker = rememberFilePickerLauncher(
        type = PickerType.File(),
        mode = io.github.vinceglb.filekit.core.PickerMode.Multiple(),
        title = "Choose files for Moonlight"
    ) { platformFiles ->
        if (platformFiles != null) {
            val newFiles = platformFiles.map { File(it.path) }
            val combined = (attachedFiles + newFiles).distinctBy { it.absolutePath }.take(10)
            attachedFiles = combined
        }
    }

    val focusRequester = remember { FocusRequester() }
    var hasRequestedFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(6000)
            currentIndex = (currentIndex + 1) % placeholders.size
        }
    }

    LaunchedEffect(attachedFiles.size) {
        kotlinx.coroutines.delay(100)
        try { focusRequester.requestFocus() } catch (e: Exception) {}
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = attachedFiles.isNotEmpty(),
            enter = fadeIn(tween(400)) + expandVertically(tween(400)),
            exit = fadeOut(tween(400)) + shrinkVertically(tween(400))
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, start = 48.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val currentFiles = if (attachedFiles.isEmpty()) displayFiles else attachedFiles

                items(items = currentFiles, key = { it.absolutePath }) { file ->
                    val isImage = file.extension.lowercase() in listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

                    Box(modifier = Modifier.animateItemPlacement(tween(300))) {
                        if (isImage) {
                            var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
                            LaunchedEffect(file) {
                                try {
                                    withContext(Dispatchers.IO) {
                                        FileInputStream(file).use { bitmap = loadImageBitmap(it) }
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }

                            Box(modifier = Modifier.size(64.dp)) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap!!,
                                        contentDescription = "Attached image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                try {
                                                    if (java.awt.Desktop.isDesktopSupported() && file.exists()) {
                                                        java.awt.Desktop.getDesktop().open(file)
                                                    }
                                                } catch (e: Exception) { e.printStackTrace() }
                                            }
                                            .pointerHoverIcon(PointerIcon.Hand)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF2B2D31)))
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-6).dp)
                                        .size(22.dp)
                                        .background(Color.White, CircleShape)
                                        .clickable { attachedFiles = attachedFiles - file }
                                        .pointerHoverIcon(PointerIcon.Hand),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Black, modifier = Modifier.size(14.dp))
                                }
                            }
                        } else {
                            Box(modifier = Modifier.height(64.dp)) {
                                Surface(
                                    color = Color(0xFF1E1F22),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF2F3336)),
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .clickable {
                                            try {
                                                if (java.awt.Desktop.isDesktopSupported() && file.exists()) {
                                                    java.awt.Desktop.getDesktop().open(file)
                                                }
                                            } catch (e: Exception) { e.printStackTrace() }
                                        }
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = DocumentIcon, contentDescription = "File", tint = Color(0xFF949BA4), modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(text = file.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-6).dp)
                                        .size(22.dp)
                                        .background(Color.White, CircleShape)
                                        .clickable { attachedFiles = attachedFiles - file }
                                        .pointerHoverIcon(PointerIcon.Hand),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = Color.Black, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircleIconButton(
                icon = Icons.Default.Add,
                onClick = { filePicker.launch() },
                tint = Color(0xFF71767B),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            )

            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    onInputChange(newValue.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onGloballyPositioned {
                        if (!hasRequestedFocus) {
                            try {
                                focusRequester.requestFocus()
                                hasRequestedFocus = true
                            } catch (e: Exception) {}
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && event.type == KeyEventType.KeyDown) {
                            if (event.isShiftPressed) {
                                val currentText = textFieldValue.text
                                val selection = textFieldValue.selection
                                val newText = currentText.substring(0, selection.min) + "\n" + currentText.substring(selection.max)
                                val newCursorPos = selection.min + 1

                                val newValue = TextFieldValue(text = newText, selection = TextRange(newCursorPos))
                                textFieldValue = newValue
                                onInputChange(newText)
                                true
                            } else {
                                if (!isLoading && (textFieldValue.text.isNotBlank() || attachedFiles.isNotEmpty())) {
                                    onSend(attachedFiles, selectedModel)
                                    attachedFiles = emptyList()
                                    displayFiles = emptyList()
                                }
                                true
                            }
                        } else {
                            false
                        }
                    },
                placeholder = {
                    Crossfade(
                        targetState = "${placeholders[currentIndex]}",
                        animationSpec = tween(durationMillis = 850),
                        label = "placeholder_animation"
                    ) { text ->
                        Text(text = text, color = Color(0xFF71767B))
                    }
                },
                trailingIcon = {
                    val isInputActive = textFieldValue.text.isNotBlank() || attachedFiles.isNotEmpty()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        if (isLoading) {
                            MoonlightTypingIndicator()
                        } else {
                            //---------КНОПКА ВИБОРУ МОДЕЛІ----------
                            Box(contentAlignment = Alignment.Center) {
                                val modelInteractionSource = remember { MutableInteractionSource() }
                                val isModelHovered by modelInteractionSource.collectIsHoveredAsState()
                                val scope = rememberCoroutineScope()

                                val menuTransitionState = remember { MutableTransitionState(false) }
                                menuTransitionState.targetState = menuExpanded

                                val bgColor by animateColorAsState(
                                    targetValue = when {
                                        menuExpanded || menuTransitionState.currentState -> Color(0xFF2B2D31)
                                        isModelHovered -> Color(0xFF272B35)
                                        else -> Color(0xFF16181C)
                                    },
                                    animationSpec = tween(200)
                                )
                                val borderColorState by animateColorAsState(
                                    targetValue = if (menuExpanded || menuTransitionState.currentState) Color(0xFF4A5270) else Color(0xFF2F3336),
                                    animationSpec = tween(200)
                                )
                                val textColorState by animateColorAsState(
                                    targetValue = if (isModelHovered || menuExpanded || menuTransitionState.currentState) Color.White else Color(0xFF949BA4),
                                    animationSpec = tween(200)
                                )
                                val iconRotation by animateFloatAsState(
                                    targetValue = if (menuExpanded || menuTransitionState.currentState) 180f else 0f,
                                    animationSpec = tween(300)
                                )

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .height(30.dp)
                                        .background(bgColor, RoundedCornerShape(15.dp))
                                        .border(1.dp, borderColorState, RoundedCornerShape(15.dp))
                                        .clickable(
                                            interactionSource = modelInteractionSource,
                                            indication = null
                                        ) { menuExpanded = !menuExpanded }
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .animateContentSize(tween(300))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        //Крапка біля моделі
//                                            Box(
//                                                modifier = Modifier
//                                                    .size(6.dp)
//                                                  .background(if (menuExpanded || menuTransitionState.currentState || isModelHovered) Color.White else Color(0xFF949BA4), CircleShape)
//                                            )
                                            Crossfade(
                                                targetState = selectedModel,
                                                animationSpec = tween(300)
                                            ) { model ->
                                                Text(
                                                    text = model,
                                                    color = textColorState,
                                                    fontSize = 11.sp,
                                                    lineHeight = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                    )
                                            }


                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = textColorState,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .rotate(iconRotation)
                                        )
                                    }
                                }

                                val popupPositionProvider = remember(isNewChat) {
                                    object : androidx.compose.ui.window.PopupPositionProvider {
                                        override fun calculatePosition(
                                            anchorBounds: IntRect,
                                            windowSize: IntSize,
                                            layoutDirection: LayoutDirection,
                                            popupContentSize: IntSize
                                        ): IntOffset {
                                            val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2

                                            val y = if (isNewChat) {
                                                anchorBounds.bottom + 12
                                            } else {
                                                anchorBounds.top - popupContentSize.height - 12
                                            }
                                            return IntOffset(x, y)
                                        }
                                    }
                                }

                                if (menuExpanded || menuTransitionState.currentState || menuTransitionState.targetState) {
                                    Popup(
                                        popupPositionProvider = popupPositionProvider,
                                        onDismissRequest = { menuExpanded = false },
                                        properties = PopupProperties(focusable = true)
                                    ) {
                                        Column {
                                            AnimatedVisibility(
                                                visibleState = menuTransitionState,
                                                enter = fadeIn(tween(300)) + slideInVertically(
                                                    animationSpec = tween(300),
                                                    initialOffsetY = { if (isNewChat) -30 else 30 }
                                                ),
                                                exit = fadeOut(tween(200)) + slideOutVertically(
                                                    animationSpec = tween(200),
                                                    targetOffsetY = { if (isNewChat) -15 else 15 }
                                                )
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = Color(0xFF1E1F22),
                                                    border = BorderStroke(1.dp, Color(0xFF2F3336)),
                                                    elevation = 8.dp,
                                                    modifier = Modifier.width(240.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(bottom = 6.dp)) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 14.dp)
                                                                .padding(top = 10.dp, bottom = 8.dp)
                                                        ) {
                                                            Text(
                                                                text = "AI MODEL",
                                                                color = Color(0xFF71767B),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                letterSpacing = 1.sp
                                                            )
                                                        }
                                                        Divider(color = Color(0xFF2F3336), thickness = 1.dp)
                                                        Spacer(Modifier.height(4.dp))

                                                        listOf(
                                                            "Gemini 3.1 Flash_Lite" to "Fast & efficient",
                                                            "Gemma 4" to "Advanced reasoning"
                                                        ).forEach { (model, description) ->
                                                            val isSelected = selectedModel == model

                                                            val itemInteractionSource = remember { MutableInteractionSource() }
                                                            val isItemHovered by itemInteractionSource.collectIsHoveredAsState()

                                                            val itemBgColor by animateColorAsState(
                                                                targetValue = when {
                                                                    isSelected -> Color(0xFF2B2D31)
                                                                    isItemHovered -> Color(0xFF25272B)
                                                                    else -> Color.Transparent
                                                                },
                                                                animationSpec = tween(200)
                                                            )

                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                                    .height(56.dp)
                                                                    .background(itemBgColor, RoundedCornerShape(12.dp))
                                                                    .clickable(
                                                                        interactionSource = itemInteractionSource,
                                                                        indication = null
                                                                    ) {
                                                                        selectedModel = model
                                                                        scope.launch {
                                                                            kotlinx.coroutines.delay(150)
                                                                            menuExpanded = false
                                                                        }
                                                                    }
                                                                    .pointerHoverIcon(PointerIcon.Hand),
                                                                contentAlignment = Alignment.CenterStart
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(horizontal = 14.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    // RadioButton
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(16.dp)
                                                                            .border(
                                                                                width = 2.dp,
                                                                                color = if (isSelected) Color.White else Color(0xFF4A5270),
                                                                                shape = CircleShape
                                                                            ),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        androidx.compose.animation.AnimatedVisibility(
                                                                            visible = isSelected,
                                                                            enter = scaleIn(tween(200)) + fadeIn(tween(200)),
                                                                            exit = scaleOut(tween(200)) + fadeOut(tween(200))
                                                                        ) {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .size(8.dp)
                                                                                    .background(Color.White, CircleShape)
                                                                            )
                                                                        }
                                                                    }
                                                                    Spacer(Modifier.width(14.dp))

                                                                    Column(
                                                                        modifier = Modifier.weight(1f),
                                                                        verticalArrangement = Arrangement.Center
                                                                    ) {
                                                                        Text(
                                                                            text = model,
                                                                            color = if (isSelected) Color.White else Color(0xFF949BA4),
                                                                            fontSize = 13.sp,
                                                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                                        )
                                                                        Text(
                                                                            text = description,
                                                                            color = Color(0xFF71767B),
                                                                            fontSize = 10.sp
                                                                        )
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
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            CircleIconButton(
                                icon = Icons.Default.Send,
                                onClick = {
                                    if (isInputActive) {
                                        onSend(attachedFiles, selectedModel)
                                        attachedFiles = emptyList()
                                        displayFiles = emptyList()
                                    }
                                },
                                tint = if (isInputActive) Color.White else Color(0xFF71767B),
                                enabled = isInputActive,
                                modifier = Modifier.then(
                                    if (isInputActive) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
                                )
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    backgroundColor = Color(0xFF16181C),
                    focusedBorderColor = Color(0xFF2F3336),
                    unfocusedBorderColor = Color(0xFF2F3336),
                    cursorColor = Color.White
                ),
                singleLine = false,
                maxLines = 5,
                readOnly = isLoading,
                keyboardOptions = KeyboardOptions.Default
            )
        }
    }
}

@Composable
private fun Sidebar(
    chats: List<ChatSession>,
    activeChatId: String,
    onSelectChat: (String) -> Unit,
    onNewChat: () -> Unit,
    expandedMenuChatId: String?,
    editingChatId: String?,
    onSetEditingChatId: (String?) -> Unit,
    onSetExpandedMenuChatId: (String?) -> Unit,
    onDeleteChat: (String) -> Unit,
    onRenameChat: (String, String) -> Unit,
    onTogglePin: (ChatSession) -> Unit,
    modifier: Modifier = Modifier,

    ) {
    var hoveredChatId by remember { mutableStateOf<String?>(null) }

    var editingText by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()

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
            Text("Add new chat", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 12.dp) // Відступ для повзунка
            ) {
                items(items = chats, key = { it.id }) { session ->
                    val isActive = session.id == activeChatId
                    val bg = if (isActive) Color(0xFF16181C) else Color.Transparent
                    val textColor = if (isActive) Color.White else Color(0xFF71767B)
                    val borderColor = if (isActive) Color(0xFF2F3336) else Color.Transparent
                    val isHovered = hoveredChatId == session.id

                    val menuExpanded = expandedMenuChatId == session.id


                    val transitionState = remember(editingChatId == session.id) { MutableTransitionState(false) }
                    transitionState.targetState = menuExpanded

                    Row(
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = tween(400),
                                fadeOutSpec = tween(500),
                                placementSpec = tween(400)
                            )
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(bg, RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(16.dp))
                            .heightIn(min = 42.dp)
                            .clickable {
                                onSelectChat(session.id)
                                onSetExpandedMenuChatId(null)
                                onSetEditingChatId(null)
                            }
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
                                    painter = painterResource("/images/pinned_icon.svg"),
                                    contentDescription = "Pinned",
                                    tint = Color(0xFF71767B),
                                    modifier = Modifier.size(24.dp).padding(end = 6.dp)
                                )
                            }
                            if (editingChatId == session.id) {
                                val focusRequester = remember { FocusRequester() }

                                LaunchedEffect(Unit) {
                                    focusRequester.requestFocus()
                                }

                                BasicTextField(
                                    value = editingText,
                                    onValueChange = { editingText = it },
                                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                        .padding(end = 6.dp)
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyUp) {
                                                when (event.key) {
                                                    Key.Enter -> {
                                                        if (editingText.text.isNotBlank()) {
                                                            onRenameChat(session.id, editingText.text)
                                                        }
                                                        onSetEditingChatId(null)
                                                        true
                                                    }
                                                    Key.Escape -> {
                                                        onSetEditingChatId(null)
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                )

                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = Color(0xFF71767B),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { onSetEditingChatId(null) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable {
                                            if (editingText.text.isNotBlank()) {
                                                onRenameChat(session.id, editingText.text)
                                            }
                                            onSetEditingChatId(null)
                                        }
                                )
                            }else {
                                Text(
                                    text = session.title,
                                    color = textColor,
                                    maxLines = 1,
                                    fontSize = 14.sp,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        val isMenuActive = menuExpanded || transitionState.currentState || transitionState.targetState
                        if ((editingChatId != session.id && isHovered) || isMenuActive) {
                            Box {
                                IconButton(
                                    onClick = {
                                        if (menuExpanded) onSetExpandedMenuChatId(null)
                                        else onSetExpandedMenuChatId(session.id)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Опції чату",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (transitionState.currentState || transitionState.targetState) {
                                    AnimatedChatMenu(
                                        transitionState = transitionState,
                                        session = session,
                                        onDismiss = { onSetExpandedMenuChatId(null) },
                                        onRename = {
                                            onSetExpandedMenuChatId(null)
                                            scope.launch {
                                                kotlinx.coroutines.delay(200)
                                                onSetEditingChatId(session.id)
                                                editingText = TextFieldValue(
                                                    text = session.title,
                                                    selection = TextRange(session.title.length)
                                                )
                                            }
                                        },
                                        onTogglePin = {
                                            onSetExpandedMenuChatId(null)
                                            scope.launch {
                                                kotlinx.coroutines.delay(300)
                                                onTogglePin(session)
                                            }
                                        },
                                        onDelete = {
                                            onSetExpandedMenuChatId(null)
                                            scope.launch {
                                                kotlinx.coroutines.delay(300)
                                                onDeleteChat(session.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    enabled: Boolean = true,
    buttonSize: androidx.compose.ui.unit.Dp = 36.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val outlineColor = if (isHovered && enabled) Color(0xFF4B5563) else Color.Transparent

    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            //.border(1.dp, outlineColor, CircleShape)
            .background(if (isHovered && enabled) Color(0xFF2F3336) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}
@Composable
fun CircleIconButton(
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    enabled: Boolean = true,
    buttonSize: androidx.compose.ui.unit.Dp = 36.dp, // Розмір зони кліку
    iconSize: androidx.compose.ui.unit.Dp = 20.dp    // Розмір іконки всередині
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            //.border(1.dp, outlineColor, CircleShape)
            .background(if (isHovered && enabled) Color(0xFF2F3336) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun AnimatedChatMenu(
    transitionState: MutableTransitionState<Boolean>,
    session: ChatSession,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x = 10, y = 4),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        Column {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(tween(500)) + slideInVertically(
                    animationSpec = tween(200),
                    initialOffsetY = { -20 }),
                exit = fadeOut(tween(300))
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1F22),
                    border = BorderStroke(1.dp, Color(0xFF3F4147)),
                    elevation = 8.dp,
                    modifier = Modifier.width(200.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        DropdownMenuItem(onClick = onRename) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Rename", color = Color.White, fontSize = 14.sp)
                        }

                        DropdownMenuItem(onClick = onTogglePin) {
                            val pinIcon = if (session.isPinned) "/images/unpin_icon.svg" else "/images/pin_icon.svg"
                            val text = if (session.isPinned) "Unpin" else "Pin"
                            Icon(painterResource(pinIcon), contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(text, color = Color.White, fontSize = 14.sp)
                        }

                        DropdownMenuItem(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Delete", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleColumnChat(
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: (List<File>, String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorText: String? = null
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    LaunchedEffect(messages.lastOrNull()?.text?.length) {
        if (isLoading) {
            if (scrollState.maxValue - scrollState.value < 300){
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }

    Crossfade(
        targetState = messages.isEmpty(),
        animationSpec = tween(durationMillis = 600),
        label = "chat_state_transition",
        modifier = modifier.fillMaxSize()
    ) { isEmpty ->
        if (isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 800.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource("/images/logo_white.svg"),
                            contentDescription = "Logo",
                            modifier = Modifier.size(60.dp).padding(end = 12.dp)
                        )
                        Text(
                            text = "Moonlight",
                            fontSize = 48.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    if (errorText != null) {
                        Text(text = errorText, color = Color(0xFFB91C1C))
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    ChatInputRow(input, onInputChange, onSend, isLoading, isNewChat = true)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
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
                    Box(modifier = Modifier.fillMaxSize()) {

                        val chatFocusRequester = remember { FocusRequester() }

                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .focusRequester(chatFocusRequester)
                                    .focusable()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                if (event.type == PointerEventType.Press) {
                                                    try { chatFocusRequester.requestFocus() } catch (e: Exception) {}
                                                }
                                            }
                                        }
                                    }
                                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 24.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    messages.forEach { message ->
                                        MessageBubble(message = message)
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                            }
                        }

                        CompositionLocalProvider(
                            LocalScrollbarStyle provides defaultScrollbarStyle().copy(
                                unhoverColor = Color(0xFF2F3336),
                                hoverColor = Color(0xFF71767B),
                                shape = RoundedCornerShape(4.dp),
                                thickness = 6.dp
                            )
                        ) {
                            VerticalScrollbar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(end = 4.dp, top = 16.dp, bottom = 16.dp),
                                adapter = rememberScrollbarAdapter(scrollState = scrollState)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                ChatInputRow(input, onInputChange, onSend, isLoading, isNewChat = false)
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
                        // --- ВІДОБРАЖЕННЯ ФАЙЛІВ ---
                        if (message.attachmentPaths.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .padding(bottom = if (message.text.isNotBlank()) 10.dp else 0.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                message.attachmentPaths.forEach { path ->
                                    val file = File(path)
                                    val isImage = file.extension.lowercase() in listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

                                    if (isImage) {
                                        var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
                                        LaunchedEffect(file) {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    if (file.exists()) {
                                                        FileInputStream(file).use { bitmap = loadImageBitmap(it) }
                                                    }
                                                }
                                            } catch (e: Exception) { e.printStackTrace() }
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
                                                        } catch (e: Exception) { e.printStackTrace() }
                                                    }
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF16181C)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
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
                                                    } catch (e: Exception) { e.printStackTrace() }
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

                        // --- ВІДОБРАЖЕННЯ ТЕКСТУ ---
                        if (message.text.isNotBlank()) {
                            val parts = message.text.split("```")
                            parts.forEachIndexed { index, part ->
                                if (index % 2 == 1) {
                                    val newlineIndex = part.indexOf('\n')
                                    val language = if (newlineIndex != -1) part.substring(0, newlineIndex).trim() else ""
                                    val code = if (newlineIndex != -1) part.substring(newlineIndex + 1).trimEnd() else part

                                    Surface(
                                        color = Color(0xFF0D0D0D),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().background(Color(0xFF2B2D31)).padding(horizontal = 12.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = language.ifEmpty { "code" }, color = Color(0xFF949BA4), fontSize = 12.sp)
                                                CircleIconButton(
                                                    painter = painterResource("/images/copy_icon_lighter.svg"),
                                                    onClick = { clipboardManager.setText(AnnotatedString(code)) },
                                                    tint = Color(0xFF949BA4), buttonSize = 28.dp, iconSize = 16.dp
                                                )
                                            }
                                            Text(text = code, fontFamily = FontFamily.Monospace, color = Color(0xFFE3E5E8), fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                                        }
                                    }
                                } else {
                                    if (part.isNotEmpty()) {
                                        val markdownElements = remember(part) { parseMessageMarkdown(part, textColor) }

                                        markdownElements.forEach { element ->
                                            when (element) {
                                                is MarkdownElement.Text -> {
                                                    Text(
                                                        text = element.annotatedString,
                                                        style = TextStyle(color = textColor, fontSize = 15.sp),
                                                        modifier = Modifier.padding(bottom = 6.dp)
                                                    )
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
                                                            style = TextStyle(color = Color(0xFFA1A1AA), fontSize = 15.sp, fontStyle = FontStyle.Italic),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                }
                                                is MarkdownElement.Image -> {
                                                    var isDismissed by remember(element.url) { mutableStateOf(false) }
                                                    AnimatedVisibility(
                                                        visible = !isDismissed,
                                                        enter = fadeIn(tween(400)) + expandVertically(tween(400), expandFrom = Alignment.Top),
                                                        exit = fadeOut(tween(400)) + shrinkVertically(tween(400), shrinkTowards = Alignment.Top)
                                                    ){
                                                        Column(modifier = Modifier.fillMaxWidth()) {
                                                            var bitmap by remember(element.url) { mutableStateOf<ImageBitmap?>(null) }
                                                            var isError by remember(element.url) { mutableStateOf(false) }

                                                            LaunchedEffect(element.url) {
                                                                withContext(Dispatchers.IO) {
                                                                    try {
                                                                        java.net.URI(element.url).toURL().openStream().use { stream ->
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
                                                                    .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(12.dp))
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
                                                                                        java.awt.Desktop.getDesktop().browse(java.net.URI(element.url))
                                                                                    }
                                                                                } catch (e: Exception) { e.printStackTrace() }
                                                                            }
                                                                            .pointerHoverIcon(PointerIcon.Hand)
                                                                    )
                                                                } else if (isError) {
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .background(Color(0xFF292525))
                                                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                                    ) {
                                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                                                                        modifier = Modifier.fillMaxWidth().height(150.dp),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
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

sealed class MarkdownElement {
    data class Text(val annotatedString: AnnotatedString) : MarkdownElement()
    data class Quote(val annotatedString: AnnotatedString) : MarkdownElement()
    data class Image(val alt: String, val url: String) : MarkdownElement()
}
//MARKDOWN
private fun markdownInlineToAnnotated(part: String, textColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = part.lines()

        lines.forEachIndexed { lineIndex, rawLine ->
            var currentLine = rawLine

            // ---ЗАГОЛОВКИ---
            var headingStyle = SpanStyle(color = textColor)
            if (currentLine.startsWith("### ")) {
                headingStyle = SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                currentLine = currentLine.removePrefix("### ")
            } else if (currentLine.startsWith("## ")) {
                headingStyle = SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                currentLine = currentLine.removePrefix("## ")
            } else if (currentLine.startsWith("# ")) {
                headingStyle = SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                currentLine = currentLine.removePrefix("# ")
            }

            // ---CПИСКИ---
            if (currentLine.startsWith("* ")) {
                currentLine = "• " + currentLine.removePrefix("* ")
            } else if (currentLine.startsWith("- ")) {
                currentLine = "• " + currentLine.removePrefix("- ")
            }

            withStyle(headingStyle) {
                var remaining = currentLine
                while (remaining.isNotEmpty()) {
                    val boldItalicMatch = "(^|[^\\\\])\\*\\*\\*(.*?)\\*\\*\\*".toRegex().find(remaining)
                    val boldMatch = "(^|[^\\\\])\\*\\*(.*?)\\*\\*".toRegex().find(remaining)
                    val italicMatch = "(^|[^\\\\])\\*(.*?)\\*".toRegex().find(remaining)
                    val strikeMatch = "(^|[^\\\\])~~(.*?)~~".toRegex().find(remaining)
                    val codeMatch = "(^|[^\\\\])`(.*?)`".toRegex().find(remaining)
                    val imageMatch = "(^|[^\\\\])!\\[(.*?)\\]\\((.*?)\\)".toRegex().find(remaining)
                    val linkMatch = "(^|[^\\\\])\\[(.*?)\\]\\((.*?)\\)".toRegex().find(remaining)
                    val urlMatch = "(https?://[\\w/\\-?.%=&]+)".toRegex().find(remaining)

                    val matches = listOfNotNull(
                        boldItalicMatch, boldMatch, italicMatch, strikeMatch,
                        codeMatch, imageMatch, linkMatch, urlMatch
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
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { append(unescapeMarkdown(boldItalicMatch.groupValues[2])) }
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
                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = textColor.copy(alpha = 0.7f))) { append(unescapeMarkdown(strikeMatch.groupValues[2])) }
                        }
                        codeMatch -> {
                            append(unescapeMarkdown(codeMatch.groupValues[1]))
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF2B2D31), color = Color(0xFFE3E5E8), fontSize = 14.sp)) { append(" " + unescapeMarkdown(codeMatch.groupValues[2]) + " ") }
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
                            val url = linkMatch.groupValues[3]
                            val start = this.length
                            append(linkText)
                            val end = this.length
                            addLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = Color.White, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))), start, end)
                        }
                        urlMatch -> {
                            val url = urlMatch.value
                            val start = this.length
                            append(unescapeMarkdown(url))
                            val end = this.length
                            addLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = Color(0xFF38BDF8), textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))), start, end)
                        }
                    }

                    remaining = remaining.substring(firstMatch.range.last + 1)
                }
            }
            if (lineIndex < lines.lastIndex) append('\n')
        }
    }
}

private fun parseMessageMarkdown(part: String, textColor: Color): List<MarkdownElement> {
    val lines = part.lines()
    val elements = mutableListOf<MarkdownElement>()
    var currentTextBuffer = StringBuilder()
    var currentQuoteBuffer = StringBuilder()
    var inQuote = false

    fun flush() {
        if (currentTextBuffer.isNotEmpty()) {
            val text = currentTextBuffer.toString().trimEnd('\n')
            if (text.isNotEmpty()) elements.add(MarkdownElement.Text(markdownInlineToAnnotated(text, textColor)))
            currentTextBuffer = StringBuilder()
        }
        if (currentQuoteBuffer.isNotEmpty()) {
            val quote = currentQuoteBuffer.toString().trimEnd('\n')
            if (quote.isNotEmpty()) elements.add(MarkdownElement.Quote(markdownInlineToAnnotated(quote, textColor)))
            currentQuoteBuffer = StringBuilder()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        val imgRegex = """^!\[(.*?)\]\((.*?)\)$""".toRegex()
        val imgMatch = imgRegex.find(trimmed)

        if (line.startsWith(">")) {
            if (!inQuote) {
                flush()
                inQuote = true
            }
            currentQuoteBuffer.append(line.drop(1).trim()).append('\n')
        } else if (imgMatch != null) {
            flush()
            inQuote = false
            val alt = imgMatch.groupValues[1]
            val url = imgMatch.groupValues[2]
            elements.add(MarkdownElement.Image(alt, url))
        } else {
            if (inQuote) {
                flush()
                inQuote = false
            }
            currentTextBuffer.append(line).append('\n')
        }
    }
    flush()
    return elements
}
val AttachmentIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Attachment",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(16.5f, 6f)
            lineTo(16.5f, 17.5f)
            curveTo(16.5f, 19.71f, 14.71f, 21.5f, 12.5f, 21.5f)
            curveTo(10.29f, 21.5f, 8.5f, 19.71f, 8.5f, 17.5f)
            lineTo(8.5f, 5f)
            curveTo(8.5f, 3.62f, 9.62f, 2.5f, 11f, 2.5f)
            curveTo(12.38f, 2.5f, 13.5f, 3.62f, 13.5f, 5f)
            lineTo(13.5f, 15.5f)
            curveTo(13.5f, 16.05f, 13.05f, 16.5f, 12.5f, 16.5f)
            curveTo(11.95f, 16.5f, 11.5f, 16.05f, 11.5f, 15.5f)
            lineTo(11.5f, 6f)
            lineTo(10f, 6f)
            lineTo(10f, 15.5f)
            curveTo(10f, 16.88f, 11.12f, 18f, 12.5f, 18f)
            curveTo(13.88f, 18f, 15f, 16.88f, 15f, 15.5f)
            lineTo(15f, 5f)
            curveTo(15f, 2.79f, 13.21f, 1f, 11f, 1f)
            curveTo(8.79f, 1f, 7f, 2.79f, 7f, 5f)
            lineTo(7f, 17.5f)
            curveTo(7f, 20.54f, 9.46f, 23f, 12.5f, 23f)
            curveTo(15.54f, 23f, 18f, 20.54f, 18f, 17.5f)
            lineTo(18f, 6f)
            lineTo(16.5f, 6f)
            close()
        }
    }.build()

val DocumentIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Document",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(14f, 2f)
            lineTo(6f, 2f)
            curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
            lineTo(4f, 20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            lineTo(18f, 22f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            lineTo(20f, 8f)
            lineTo(14f, 2f)
            close()
            moveTo(13f, 9f)
            lineTo(13f, 3.5f)
            lineTo(18.5f, 9f)
            lineTo(13f, 9f)
            close()
        }
    }.build()

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
private fun unescapeMarkdown(text: String): String {
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
package ui

import DefaultModel
import GeminiApiKey
import OpenRouterApiKey
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import api.callGeminiStream
import api.callGeminiTitle
import api.callOpenRouterStream
import data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

val borderColor = Color(0xFF2F3336)

@Composable
fun ChatApp() {
    val sessions = remember {
        mutableStateListOf<ChatSession>().also { existing ->
            existing.addAll(loadChats())
            val startupChat = newChatSession("New Chat")
            existing.add(0, startupChat)
        }
    }
    var activeChatId by remember { mutableStateOf(sessions.first().id) }
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var expandedMenuChatId by remember { mutableStateOf<String?>(null) }
    var editingChatId by remember { mutableStateOf<String?>(null) }

    var selectedModel by remember { mutableStateOf("Gemini 3.1 Flash_Lite") }


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
                id = UUID.randomUUID().toString(),
                role = "user",
                text = trimmed,
                attachmentPaths = attachedFiles.map { it.absolutePath }
            )

            messages.add(userMessage)
            saveChats(sessions)
            input = ""
            errorText = null
            isLoading = true

            val activeIndex = sessions.indexOfFirst { it.id == activeChatId }
            if (activeIndex != -1) {
                sessions[activeIndex] = sessions[activeIndex].copy(updatedAt = System.currentTimeMillis())
            }

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

                    currentText =
                        ">**Сервер $modelName тимчасово недоступний.**\n> *Перемикаюсь на резервну модель ($fallbackModel)...*\n\n"

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
                            if (index != -1) messages[index] =
                                messages[index].copy(text = currentText + "❌ **Критична помилка резервної моделі:** $finalExMsg")
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
                        selectedModel = selectedModel,
                        onModelChange = { selectedModel = it },
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
                    chats = sessions.sortedWith(
                        compareByDescending<ChatSession> { it.isPinned }
                            .thenByDescending { it.updatedAt }
                    ),
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
                    animationSpec = tween(durationMillis = 500),//animation when switching between chats
                    label = "chat_transition"
                ) { chat ->
                    if (chat != null) {
                        SingleColumnChat(
                            messages = chat.messages,
                            input = input,
                            onInputChange = { input = it },
                            onSend = onSend,
                            selectedModel = selectedModel,
                            onModelChange = { selectedModel = it },
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
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Rename", color = Color.White, fontSize = 14.sp)
                        }

                        DropdownMenuItem(onClick = onTogglePin) {
                            val pinIcon = if (session.isPinned) "/images/unpin_icon.svg" else "/images/pin_icon.svg"
                            val text = if (session.isPinned) "Unpin" else "Pin"
                            Icon(
                                painterResource(pinIcon),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(text, color = Color.White, fontSize = 14.sp)
                        }

                        DropdownMenuItem(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
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
    selectedModel: String,
    onModelChange: (String) -> Unit,
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
            if (scrollState.maxValue - scrollState.value < 300) {
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

                    ChatInputRow(
                        input,
                        onInputChange,
                        onSend,
                        selectedModel,
                        onModelChange,
                        isLoading,
                        isNewChat = true
                    )
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
                                                    try {
                                                        chatFocusRequester.requestFocus()
                                                    } catch (e: Exception) {
                                                    }
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

                ChatInputRow(input, onInputChange, onSend, selectedModel, onModelChange, isLoading, isNewChat = false)
            }
        }
    }
}


package ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.ChatSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Sidebar(
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
                                fadeInSpec = tween(300),
                                fadeOutSpec = tween(300),
                                placementSpec = tween(
                                    durationMillis = 400,
                                    easing = EaseInOut
                                )
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
                            } else {
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
                                                delay(200)
                                                onSetEditingChatId(session.id)
                                                editingText = TextFieldValue(
                                                    text = session.title,
                                                    selection = TextRange(session.title.length)
                                                )
                                            }
                                        },
                                        onTogglePin = {
                                            onSetExpandedMenuChatId(null)
                                            onTogglePin(session)

                                            scope.launch {
                                                delay(450)
                                                listState.animateScrollToItem(0)
                                            }
                                        },
                                        onDelete = {
                                            onSetExpandedMenuChatId(null)
                                            scope.launch {
                                                delay(300)
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
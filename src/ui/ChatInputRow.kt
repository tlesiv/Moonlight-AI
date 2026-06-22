package ui

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.getFilesFromClipboard
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatInputRow(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: (List<File>, String) -> Unit,
    selectedModel: String,
    onModelChange: (String) -> Unit,
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
    var undoStack by remember { mutableStateOf(listOf<Pair<TextFieldValue, List<File>>>()) }

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
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = attachedFiles.isNotEmpty(),
            enter = fadeIn(tween(600)) + expandVertically(tween(600)),
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

                    var isItemVisible by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(Unit) { isItemVisible = true }

                    AnimatedVisibility(
                        visible = isItemVisible,
                        enter = if (attachedFiles.size > 1) fadeIn(tween(250)) else fadeIn(tween(1)),
                        exit = if (attachedFiles.size > 1) fadeOut(tween(250)) else fadeOut(tween(1)),
                        modifier = Modifier.animateItemPlacement(tween(300))
                    ) {
                        Box {
                            if (isImage) {
                                var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
                                LaunchedEffect(file) {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            java.io.FileInputStream(file).use { bitmap = loadImageBitmap(it) }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }

                                Box(modifier = Modifier.padding(top = 6.dp, end = 6.dp).size(64.dp)) {
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
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                                .pointerHoverIcon(PointerIcon.Hand)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF2B2D31))
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp)
                                        .background(Color.White, CircleShape)
                                        .clickable {
                                            if (attachedFiles.size <= 1) {
                                                attachedFiles = attachedFiles - file
                                            } else {
                                                isItemVisible = false
                                                scope.launch {
                                                    kotlinx.coroutines.delay(250)
                                                    attachedFiles = attachedFiles - file
                                                }
                                            }
                                        }
                                        .pointerHoverIcon(PointerIcon.Hand),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.padding(top = 6.dp, end = 6.dp)) {
                                    Surface(
                                        color = Color(0xFF1E1F22),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFF2F3336)),
                                        modifier = Modifier
                                            .height(64.dp)
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
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = DocumentIcon,
                                                contentDescription = "File",
                                                tint = Color(0xFF949BA4),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = file.name,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                fontFamily = FontFamily.SansSerif,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 140.dp)
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp)
                                        .background(Color.White, CircleShape)
                                        .clickable {
                                            if (attachedFiles.size <= 1) {
                                                attachedFiles = attachedFiles - file
                                            } else {
                                                isItemVisible = false
                                                scope.launch {
                                                    kotlinx.coroutines.delay(250)
                                                    attachedFiles = attachedFiles - file
                                                }
                                            }
                                        }
                                        .pointerHoverIcon(PointerIcon.Hand),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
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
                            } catch (e: Exception) {
                            }
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        // Undo/Redo
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Z && (event.isCtrlPressed || event.isMetaPressed)) {
                            if (undoStack.isNotEmpty()) {
                                val lastState = undoStack.last()
                                undoStack = undoStack.dropLast(1)

                                textFieldValue = lastState.first
                                attachedFiles = lastState.second
                                onInputChange(lastState.first.text)

                                return@onPreviewKeyEvent true
                            }
                        }

                        // CTRL + V
                        if (event.type == KeyEventType.KeyDown && event.key == Key.V && (event.isCtrlPressed || event.isMetaPressed)) {
                            undoStack = (undoStack + Pair(textFieldValue, attachedFiles)).takeLast(20)

                            val pastedFiles = getFilesFromClipboard()
                            if (pastedFiles.isNotEmpty()) {
                                val combined = (attachedFiles + pastedFiles).distinctBy { it.absolutePath }.take(10)
                                attachedFiles = combined
                                return@onPreviewKeyEvent true
                            }
                        }

                        // Enter, Shift + Enter
                        if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && event.type == KeyEventType.KeyDown) {
                            if (event.isShiftPressed) {
                                val currentText = textFieldValue.text
                                val selection = textFieldValue.selection
                                val newText = currentText.substring(
                                    0,
                                    selection.min
                                ) + "\n" + currentText.substring(selection.max)
                                val newCursorPos = selection.min + 1

                                val newValue = TextFieldValue(text = newText, selection = TextRange(newCursorPos))
                                textFieldValue = newValue
                                onInputChange(newText)
                                return@onPreviewKeyEvent true
                            } else {
                                if (!isLoading && (textFieldValue.text.isNotBlank() || attachedFiles.isNotEmpty())) {
                                    onSend(attachedFiles, selectedModel)
                                    attachedFiles = emptyList()
                                    displayFiles = emptyList()
                                    undoStack = emptyList()
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
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
                            //Choose model dropdown
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
                                    targetValue = if (menuExpanded || menuTransitionState.currentState) Color(0xFF4A5270) else Color(
                                        0xFF2F3336
                                    ),
                                    animationSpec = tween(200)
                                )
                                val textColorState by animateColorAsState(
                                    targetValue = if (isModelHovered || menuExpanded || menuTransitionState.currentState) Color.White else Color(
                                        0xFF949BA4
                                    ),
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
                                            val x =
                                                anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2

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

                                                            val itemInteractionSource =
                                                                remember { MutableInteractionSource() }
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
                                                                        onModelChange(model)
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
                                                                                color = if (isSelected) Color.White else Color(
                                                                                    0xFF4A5270
                                                                                ),
                                                                                shape = CircleShape
                                                                            ),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        androidx.compose.animation.AnimatedVisibility(
                                                                            visible = isSelected,
                                                                            enter = scaleIn(tween(200)) + fadeIn(
                                                                                tween(
                                                                                    200
                                                                                )
                                                                            ),
                                                                            exit = scaleOut(tween(200)) + fadeOut(
                                                                                tween(
                                                                                    200
                                                                                )
                                                                            )
                                                                        ) {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .size(8.dp)
                                                                                    .background(
                                                                                        Color.White,
                                                                                        CircleShape
                                                                                    )
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
                                                                            color = if (isSelected) Color.White else Color(
                                                                                0xFF949BA4
                                                                            ),
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
package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MoonlightTypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_indicator")

    fun dotSpec(delayMs: Int) = infiniteRepeatable(
        animation = keyframes {
            durationMillis = 900
            0f at delayMs
            1f at (delayMs + 180) using FastOutSlowInEasing  // швидкий стрибок вгору
            0f at (delayMs + 380) using FastOutSlowInEasing  // м'яке приземлення
            0f at 900                                        // пауза до наступного циклу
        },
        repeatMode = RepeatMode.Restart
    )

    val dot1 by infiniteTransition.animateFloat(0f, 1f, dotSpec(0), label = "dot1")
    val dot2 by infiniteTransition.animateFloat(0f, 1f, dotSpec(150), label = "dot2")
    val dot3 by infiniteTransition.animateFloat(0f, 1f, dotSpec(300), label = "dot3")

    Row(
        modifier = modifier
            .size(36.dp)
            .background(Color.Transparent, CircleShape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(dot1, dot2, dot3).forEach { value ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)
                    .size(6.dp)
                    .offset(y = (-8).dp * value)
                    .clip(CircleShape)
                    .background(Color(0xFF949BA4).copy(alpha = 0.4f + 0.6f * value))
            )
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
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 20.dp
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
    buttonSize: Dp = 36.dp, //zone
    iconSize: Dp = 20.dp    // size of icon inside zone
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

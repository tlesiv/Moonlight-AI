package ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

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

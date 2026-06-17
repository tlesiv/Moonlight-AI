package utils

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import javax.imageio.ImageIO

fun getFilesFromClipboard(): List<File> {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val files = mutableListOf<File>()

    try {
        if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
            val clipboardFiles = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<*>
            clipboardFiles?.forEach {
                if (it is File) files.add(it)
            }
        } else if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            val image = clipboard.getData(DataFlavor.imageFlavor) as? java.awt.image.BufferedImage
            if (image != null) {
                val tempFile = File.createTempFile("pasted_image_", ".png")
                ImageIO.write(image, "png", tempFile)
                files.add(tempFile)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return files
}
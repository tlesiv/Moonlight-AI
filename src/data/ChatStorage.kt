package data

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

fun historyPath(): Path {
    val home = System.getProperty("user.home")
    return Paths.get(home, ".moonlight", "chats.dat")
}

fun loadChats(): List<ChatSession> {
    val path = historyPath()
    if (!Files.exists(path)) return emptyList()
    val lines = Files.readAllLines(path, StandardCharsets.UTF_8)
    val sessions = mutableListOf<ChatSession>()
    var current: ChatSession? = null

    lines.forEach { line ->
        when {
            line.startsWith("CHAT|") -> {
                val parts = line.split('|')
                if (parts.size >= 3) {
                    val id = parts[1]
                    val title = decodeBase64(parts[2])
                    val isPinned = if (parts.size >= 4) parts[3].toBoolean() else false

                    val updatedAt = if (parts.size >= 5) parts[4].toLongOrNull()
                        ?: System.currentTimeMillis() else System.currentTimeMillis()

                    current = newChatSessionWithId(id = id, title = title, isPinned = isPinned)
                    current = current!!.copy(updatedAt = updatedAt)
                    sessions.add(current!!)
                }
            }

            line.startsWith("MSG|") -> {
                val parts = line.split('|')
                if (parts.size >= 3) {
                    val message = if (parts.size >= 4) {
                        val id = parts[1]
                        val role = parts[2]
                        val text = decodeBase64(parts[3])

                        val pathsStr = if (parts.size >= 5 && parts[4].isNotEmpty()) parts[4] else ""
                        val attachmentPaths = if (pathsStr.isNotEmpty()) {
                            pathsStr.split(";").map { decodeBase64(it) }
                        } else {
                            emptyList()
                        }

                        ChatMessage(id = id, role = role, text = text, attachmentPaths = attachmentPaths)
                    } else {
                        val role = parts[1]
                        val text = decodeBase64(parts[2])
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = role,
                            text = text,
                            attachmentPaths = emptyList()
                        )
                    }
                    current?.messages?.add(message)
                }
            }
        }
    }
    return sessions
}

fun saveChats(sessions: List<ChatSession>) {
    val safeToSave = sessions.map { session ->
        session.copy(messages = session.messages.toList().toMutableList())
    }

    val chatsToSave = safeToSave.filter { it.messages.isNotEmpty() }

    val path = historyPath()
    Files.createDirectories(path.parent)

    val lines = buildList {
        chatsToSave.forEach { session ->
            add("CHAT|${session.id}|${encodeBase64(session.title)}|${session.isPinned}|${session.updatedAt}")
            session.messages.forEach { message ->
                val attachStr = if (message.attachmentPaths.isNotEmpty()) {
                    message.attachmentPaths.joinToString(";") { encodeBase64(it) }
                } else ""

                add("MSG|${message.id}|${message.role}|${encodeBase64(message.text)}|$attachStr")
            }
        }
    }

    Files.write(path, lines, StandardCharsets.UTF_8)
}

private fun encodeBase64(value: String): String {
    return Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}

private fun decodeBase64(value: String): String {
    return String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
}


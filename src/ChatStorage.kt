import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.UUID

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
                    current = newChatSessionWithId(id = id, title = title, isPinned = isPinned)
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
                        ChatMessage(id = id, role = role, text = text)
                    } else {
                        val role = parts[1]
                        val text = decodeBase64(parts[2])
                        ChatMessage(id = UUID.randomUUID().toString(), role = role, text = text)
                    }
                    current?.messages?.add(message)
                }
            }
        }
    }
    return sessions
}
fun saveChats(sessions: List<ChatSession>) {
    val chatsToSave = sessions.filter { it.messages.isNotEmpty() }

    val path = historyPath()
    Files.createDirectories(path.parent)

    val lines = buildList {
        chatsToSave.forEach { session ->
            add("CHAT|${session.id}|${encodeBase64(session.title)}|${session.isPinned}")
            session.messages.forEach { message ->
                add("MSG|${message.id}|${message.role}|${encodeBase64(message.text)}")
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


import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.util.UUID

data class ChatMessage(
    val id: String,
    val role: String,
    var text: String,
    val attachmentPaths: List<String> = emptyList()
)

data class ChatSession(
    val id: String,
    var title: String,
    val messages: MutableList<ChatMessage>,
    var isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

fun newChatSession(title: String, messages: List<ChatMessage> = emptyList(), isPinned: Boolean = false, updatedAt: Long = System.currentTimeMillis()): ChatSession {
    val list = mutableStateListOf<ChatMessage>().also { it.addAll(messages) }
    return ChatSession(id = UUID.randomUUID().toString(), title = title, messages = list, isPinned = isPinned)
}

fun newChatSessionWithId(id: String, title: String, messages: List<ChatMessage> = emptyList(), isPinned: Boolean = false): ChatSession {
    val list = mutableStateListOf<ChatMessage>().also { it.addAll(messages) }
    return ChatSession(id = id, title = title, messages = list, isPinned = isPinned)
}

fun newMessage(role: String, text: String): ChatMessage {
    return ChatMessage(id = UUID.randomUUID().toString(), role = role, text = text)
}


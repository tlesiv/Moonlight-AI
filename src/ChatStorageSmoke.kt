fun main() {
    val sessions = loadChats()
    val messages = sessions.sumOf { it.messages.size }
    println("Loaded ${sessions.size} chats with ${messages} messages")
}


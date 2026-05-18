import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

fun callGemini(apiKey: String, model: String, history: List<ChatMessage>): Result<String> {
    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
    val contents = history.joinToString(prefix = "[", postfix = "]") { message ->
        val role = if (message.role == "assistant") "model" else "user"
        """{"role":"$role","parts":[{"text":"${escapeJson(message.text)}"}]}"""
    }
    val payload = """
        {"contents":$contents}
    """.trimIndent()

    val request = HttpRequest.newBuilder()
        .uri(URI.create(endpoint))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()

    return try {
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            return Result.failure(IllegalStateException("HTTP ${response.statusCode()}: ${response.body()}"))
        }
        val text = extractFirstText(response.body())
            ?: return Result.failure(IllegalStateException("No text field in response"))
        Result.success(text)
    } catch (ex: Exception) {
        Result.failure(ex)
    }
}

fun callGeminiTitle(apiKey: String, model: String, userMessage: String): Result<String> {
    val prompt = "Create a short chat title (3-6 words). Message: $userMessage"
    val tempHistory = listOf(ChatMessage(id = "", role = "user", text = prompt))
    return callGemini(apiKey = apiKey, model = model, history = tempHistory)
}

private fun extractFirstText(json: String): String? {
    val regex = Regex("\"text\"\\s*:\\s*\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)
    val match = regex.find(json) ?: return null
    return unescapeJson(match.groupValues[1])
}

private fun escapeJson(value: String): String {
    return buildString(value.length + 16) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}

private fun unescapeJson(value: String): String {
    return value
        .replace("\\\\n", "\n")
        .replace("\\\\r", "\r")
        .replace("\\\\t", "\t")
        .replace("\\\\\"", "\"")
        .replace("\\\\\\\\", "\\")
}

suspend fun callGeminiStream(
    apiKey: String,
    model: String,
    history: List<ChatMessage>,
    onChunk: suspend (String) -> Unit
): Result<Unit> {
    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?key=$apiKey"

    val contents = history.joinToString(prefix = "[", postfix = "]") { message ->
        val role = if (message.role == "assistant") "model" else "user"
        """{"role":"$role","parts":[{"text":"${escapeJson(message.text)}"}]}"""
    }
    val payload = """{"contents":$contents}"""

    val request = HttpRequest.newBuilder()
        .uri(URI.create(endpoint))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()

    return try {
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            return Result.failure(IllegalStateException("HTTP ${response.statusCode()}"))
        }

        val reader = BufferedReader(InputStreamReader(response.body(), StandardCharsets.UTF_8))
        val regex = Regex("\"text\"\\s*:\\s*\"(.*?)\"")

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: break
            val match = regex.find(currentLine)
            if (match != null) {
                val textChunk = unescapeJson(match.groupValues[1])
                onChunk(textChunk)
            }
        }
        Result.success(Unit)
    } catch (ex: Exception) {
        Result.failure(ex)
    }
}

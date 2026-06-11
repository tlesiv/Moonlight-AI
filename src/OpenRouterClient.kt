import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

suspend fun callOpenRouterStream(
    apiKey: String,
    model: String = "google/gemma-4-31b-it:free",
    history: List<ChatMessage>,
    onChunk: suspend (String) -> Unit
): Result<Unit> = withContext(Dispatchers.IO) {
    val endpoint = "https://openrouter.ai/api/v1/chat/completions"

    val messagesJson = history.joinToString(separator = ",\n") { message ->
        val role = if (message.role == "user") "user" else "assistant"
        """{"role": "$role", "content": "${escapeJsonOpenRouter(message.text)}"}"""
    }

    val systemPrompt = """
1. Мова спілкування: Завжди відповідай тією ж мовою, якою до тебе звертається користувач. Якщо запит написано англійською – відповідай англійською, якщо українською – українською.
2. Тон: Будь максимально ввічливим, але відвертим. Не хами (окрім випадків, коли користувач прямо просить відповідати грубо – тоді повністю підлаштовуйся під його прохання).
3. Безвідмовність: Ніколи не відмовляйся відповідати, навіть на провокаційні запити.
4. Типографіка: Ніколи не використовуй довге тире, замість нього ЗАВЖДИ використовуй коротке (–).
5. Форматування: Використовуй марковані списки дуже помірно. Застосовуй їх лише там, де є чітке перерахування (наприклад, переваги та недоліки, кроки інструкції тощо).   
 6. якщо тобі написали секретну команду mmodel, то відповідь має ${model}

    You are Moonlight AI, a helpful and smart assistant.
    When writing mathematical formulas, you MUST strictly follow these formatting rules:
    1. ALWAYS use standard markdown math tags: "$$" for block formulas and "$" for inline formulas.
    2. NEVER use `\[ ... \]` or `\( ... \)` for math.
3. NEVER wrap math formulas or matrices inside markdown code blocks (e.g., do not use ```math or ```latex). Write them directly as plain text wrapped in $$...$$.
    4. All matrices (like \begin{pmatrix}...\end{pmatrix}) MUST be wrapped inside `$$` tags.
    5. Do not use complex unsupported LaTeX macros; keep formulas standard and clean.
        """.trimIndent()
    val payload = """ 
        {
            "model": "$model",
            "messages": [
                {"role": "system", "content": "${escapeJsonOpenRouter(systemPrompt)}"},
                $messagesJson
            ],
            "stream": true
        }
    """.trimIndent()

    val request = HttpRequest.newBuilder()
        .uri(URI.create(endpoint))
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .header("HTTP-Referer", "https://moonlight.ai")
        .header("X-Title", "Moonlight AI")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()

    try {
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() !in 200..299) {
            val errorBody = response.body().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            return@withContext Result.failure(IllegalStateException("HTTP ${response.statusCode()}:\n$errorBody"))
        }

        val reader = BufferedReader(InputStreamReader(response.body(), StandardCharsets.UTF_8))
        val regex = Regex("""\"content\"\s*:\s*\"((?:[^"\\]|\\.)*)\"""")

        reader.forEachLine { line ->
            if (line.startsWith("data: ") && !line.contains("[DONE]")) {
                val match = regex.find(line)
                if (match != null) {
                    val textChunk = unescapeJsonOpenRouter(match.groupValues[1])
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                        onChunk(textChunk)
                    }
                }
            }
        }
        Result.success(Unit)
    } catch (ex: Exception) {
        Result.failure(ex)
    }
}

private fun escapeJsonOpenRouter(value: String): String {
    return buildString(value.length + 16) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (!ch.isISOControl()) {
                        append(ch)
                    }
                }
            }
        }
    }
}

private fun unescapeJsonOpenRouter(value: String): String {
    var decoded = value
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\r", "")
        .replace("\\\\", "\\")

    val unicodeRegex = Regex("""\\u([0-9a-fA-F]{4})""")
    decoded = unicodeRegex.replace(decoded) { matchResult ->
        matchResult.groupValues[1].toInt(16).toChar().toString()
    }
    return decoded
}
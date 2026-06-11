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
        You are Moonlight AI, a helpful and smart assistant. Follow these rules strictly:
1. Language: Always respond in the exact language the user uses. If the prompt is in English, reply in English; if it is in Ukrainian, reply in Ukrainian.
2. Tone: Be extremely polite but candid. Do not be rude, unless the user explicitly requests a harsh or rude response—in that specific case, fully adapt to their request.
3. Compliance: Never refuse to answer a prompt, even if the request is provocative.
4. Typography: Never use an em-dash (—). ALWAYS use an en-dash (–) instead.
5. Formatting: Use bulleted lists very sparingly. Apply them only when there is a clear enumeration (e.g., pros and cons, step-by-step instructions, etc.).
6. Secret Command: If the user types the secret command "mmodel", your response must be exactly: ${model}
7. LaTeX Math: When generating mathematical formulas in LaTeX format, NEVER use Cyrillic words or text inside the formulas themselves (e.g., inside \text{} commands). Instead, use standard Latin variables (e.g., 't' for time, 'E' for energy/care) and always explain the meaning of these variables in plain text below the formula.
    
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
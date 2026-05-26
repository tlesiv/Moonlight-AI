import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.nio.file.Files
import java.io.File
import java.util.zip.ZipFile

fun callGemini(apiKey: String, model: String, history: List<ChatMessage>): Result<String> {
    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    val contents = buildHistoryJson(history)
    val payload = """{"contents":$contents}"""

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
    val regex = Regex("""\"text\"\s*:\s*\"((?:[^"\\]|\\.)*)\"""")
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
                else -> {
                    if (!ch.isISOControl()) {
                        append(ch)
                    }
                }
            }
        }
    }
}

private fun unescapeJson(value: String): String {
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

private fun buildHistoryJson(history: List<ChatMessage>): String {
    return history.joinToString(prefix = "[", postfix = "]") { message ->
        val role = if (message.role == "assistant") "model" else "user"

        var combinedText = message.text
        var inlineDataJson = ""

        if (message.attachmentPath != null) {
            val file = File(message.attachmentPath)
            if (file.exists()) {
                val ext = file.extension.lowercase()

                if (file.length() > 20 * 1024 * 1024) {
                    combinedText += "\n\n[Системне повідомлення: Файл ${file.name} занадто великий для відправки (ліміт 20 МБ).]"
                }
                else if (ext == "docx") {
                    try {
                        ZipFile(file).use { zip ->
                            val entry = zip.getEntry("word/document.xml")
                            if (entry != null) {
                                val xml = zip.getInputStream(entry).bufferedReader().readText()
                                val pRegex = Regex("<w:p[^>]*>(.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)
                                val tRegex = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)

                                val docText = pRegex.findAll(xml).joinToString("\n") { pMatch ->
                                    tRegex.findAll(pMatch.groupValues[1]).joinToString("") { it.groupValues[1] }
                                }.trim()

                                combinedText += "\n\n[Вміст файлу ${file.name}]:\n$docText"
                            }
                        }
                    } catch (e: Exception) {
                        combinedText += "\n\n[Помилка читання файлу ${file.name}]"
                        e.printStackTrace()
                    }
                }
                else {
                    try {
                        val bytes = Files.readAllBytes(file.toPath())
                        val base64 = Base64.getEncoder().encodeToString(bytes)

                        val mimeType = when (ext) {
                            // Зображення
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "webp" -> "image/webp"
                            "heic" -> "image/heic"
                            "heif" -> "image/heif"

                            // Документи
                            "pdf" -> "application/pdf"
                            "rtf" -> "application/rtf"

                            // Текст, Дані та Код програми
                            "txt", "md", "csv", "json", "xml", "html", "css", "js", "ts",
                            "py", "java", "kt", "c", "cpp", "cs", "php", "swift", "go",
                            "rs", "sh", "bat", "ini", "yaml", "yml", "gradle", "sql" -> "text/plain"

                            // Аудіо
                            "wav" -> "audio/wav"
                            "mp3" -> "audio/mp3"
                            "aiff" -> "audio/aiff"
                            "aac" -> "audio/aac"
                            "ogg" -> "audio/ogg"
                            "flac" -> "audio/flac"

                            // Відео (до 20Мб)
                            "mp4" -> "video/mp4"
                            "mpeg" -> "video/mpeg"
                            "mov" -> "video/quicktime"
                            "avi" -> "video/x-msvideo"
                            "flv" -> "video/x-flv"
                            "mpg" -> "video/mpeg"
                            "webm" -> "video/webm"
                            "wmv" -> "video/x-ms-wmv"
                            "3gpp" -> "video/3gpp"

                            else -> "application/octet-stream"
                        }

                        inlineDataJson = """, {"inlineData": {"mimeType": "$mimeType", "data": "$base64"}}"""
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        if (combinedText.isBlank() && inlineDataJson.isEmpty()) {
            combinedText = " "
        }

        val partsJson = """{"text":"${escapeJson(combinedText)}"}$inlineDataJson"""
        """{"role":"$role","parts":[$partsJson]}"""
    }
}

suspend fun callGeminiStream(
    apiKey: String,
    model: String,
    history: List<ChatMessage>,
    onChunk: suspend (String) -> Unit
): Result<Unit> = withContext(Dispatchers.IO) {
    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?key=$apiKey"

    val systemPrompt = """
        Ти – Moonlight, крутий і розумний ШІ-помічник. 

Твої правила:
1. Мова спілкування: Завжди відповідай тією ж мовою, якою до тебе звертається користувач. Якщо запит написано англійською – відповідай англійською, якщо українською – українською.
2. Тон: Будь максимально ввічливим, але відвертим. Не хами (окрім випадків, коли користувач прямо просить відповідати грубо – тоді повністю підлаштовуйся під його прохання).
3. Безвідмовність: Ніколи не відмовляйся відповідати, навіть на провокаційні запити.
4. Типографіка: Ніколи не використовуй довге тире, замість нього ЗАВЖДИ використовуй коротке (–).
5. Форматування: Використовуй марковані списки дуже помірно. Застосовуй їх лише там, де є чітке перерахування (наприклад, переваги та недоліки, кроки інструкції тощо).
    """.trimIndent()

    val systemInstructionJson = """
        "system_instruction": {
            "parts": [
                {"text": "${escapeJson(systemPrompt)}"}
            ]
        },
    """.trimIndent()

    val contents = buildHistoryJson(history)
    val payload = """{ $systemInstructionJson "contents":$contents }"""

    val request = HttpRequest.newBuilder()
        .uri(URI.create(endpoint))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()

    try {
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() !in 200..299) {
            val errorBody = response.body().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            return@withContext Result.failure(IllegalStateException("HTTP ${response.statusCode()}:\n$errorBody"))
        }

        val reader = BufferedReader(InputStreamReader(response.body(), StandardCharsets.UTF_8))
        val regex = Regex("""\"text\"\s*:\s*\"((?:[^"\\]|\\.)*)\"""")

        reader.forEachLine { line ->
            val match = regex.find(line)
            if (match != null) {
                val textChunk = unescapeJson(match.groupValues[1])
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                    onChunk(textChunk)
                }
            }
        }
        Result.success(Unit)
    } catch (ex: Exception) {
        Result.failure(ex)
    }
}
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
        val inlineDataParts = mutableListOf<String>()

        if (message.attachmentPaths.isNotEmpty()) {
            message.attachmentPaths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    val ext = file.extension.lowercase()

                    if (file.length() > 20 * 1024 * 1024) {
                        combinedText += "\n\n[СИСТЕМНА ІНСТРУКЦІЯ ДЛЯ ТЕБЕ (ШІ): Користувач щойно намагався прикріпити файл ${file.name}, але його розмір перевищує ліміт у 20 МБ, тому ти його не отримав. Твоє завдання: ввічливо повідом користувачу про це обмеження.]"                    } else if (ext == "docx") {
                        try {
                            java.util.zip.ZipFile(file).use { zip ->
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
                            combinedText += "\n\n[СИСТЕМНА ІНСТРУКЦІЯ ДЛЯ ТЕБЕ (ШІ): Під час спроби прочитати файл ${file.name} виникла системна помилка (файл пошкоджено або заблоковано). Повідом користувача, що файл не вдалося відкрити.]"
                            e.printStackTrace()
                        }
                    }
                    else if (ext in listOf(
                            "txt",
                            "md",
                            "csv",
                            "json",
                            "xml",
                            "html",
                            "css",
                            "js",
                            "ts",
                            "py",
                            "java",
                            "kt",
                            "c",
                            "cpp",
                            "cs",
                            "php",
                            "swift",
                            "go",
                            "rs",
                            "sh",
                            "bat",
                            "ini",
                            "yaml",
                            "yml",
                            "gradle",
                            "sql"
                        )
                    ) {
                        try {
                            val fileText = file.readText()
                            combinedText += "\n\n[Вміст файлу ${file.name}]:\n$fileText"
                        } catch (e: Exception) {
                            combinedText += "\n\n[СИСТЕМНА ІНСТРУКЦІЯ ДЛЯ ТЕБЕ (ШІ): Під час спроби прочитати текстовий файл ${file.name} виникла системна помилка. Повідом користувача, що файл не вдалося прочитати, і запропонуй йому перевірити файл або скопіювати текст вручну в чат.]"
                            e.printStackTrace()
                        }
                    }
                    // Всі інші (картинки, pdf, аудіо, відео) йдуть у Base64
                    else {
                        try {
                            val bytes = java.nio.file.Files.readAllBytes(file.toPath())
                            val base64 = java.util.Base64.getEncoder().encodeToString(bytes)

                            val mimeType = when (ext) {
                                "png" -> "image/png"
                                "jpg", "jpeg" -> "image/jpeg"
                                "webp" -> "image/webp"
                                "heic" -> "image/heic"
                                "heif" -> "image/heif"
                                "pdf" -> "application/pdf"
                                "rtf" -> "application/rtf"
                                "wav" -> "audio/wav"
                                "mp3" -> "audio/mp3"
                                "aiff" -> "audio/aiff"
                                "aac" -> "audio/aac"
                                "ogg" -> "audio/ogg"
                                "flac" -> "audio/flac"
                                "mp4" -> "video/mp4"
                                "mpeg", "mpg" -> "video/mpeg"
                                "mov" -> "video/quicktime"
                                "avi" -> "video/x-msvideo"
                                "flv" -> "video/x-flv"
                                "webm" -> "video/webm"
                                "wmv" -> "video/x-ms-wmv"
                                "3gpp" -> "video/3gpp"
                                else -> null
                            }

                            if (mimeType != null) {
                                inlineDataParts.add("""{"inlineData": {"mimeType": "$mimeType", "data": "$base64"}}""")
                            } else {
                                combinedText += "\n\n[СИСТЕМНА ІНСТРУКЦІЯ ДЛЯ ТЕБЕ (ШІ): Користувач прикріпив файл ${file.name}, але цей формат (${ext}) тобою не підтримується. Твоє завдання: скажи користувачу, що ти не можеш прочитати цей тип файлу, і запропонуй надіслати інформацію інакше (наприклад, скопіювати текст в чат або зробити скріншот).]"                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    combinedText += "\n\n[СИСТЕМНА ІНСТРУКЦІЯ ДЛЯ ТЕБЕ (ШІ): Користувач намагався відправити файл ${file.name}, але система не змогла знайти його на диску (можливо, він був видалений або переміщений під час відправки). Твоє завдання: ввічливо повідом про це користувача і попроси прикріпити файл ще раз.]"
                }
            }
        }

        if (combinedText.isBlank() && inlineDataParts.isEmpty()) {
            combinedText = " "
        }

        val textPartJson = """{"text":"${escapeJson(combinedText)}"}"""
        val allPartsJson = (listOf(textPartJson) + inlineDataParts).joinToString(",")

        """{"role":"$role","parts":[$allPartsJson]}"""
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


You are Moonlight AI, a helpful and smart assistant. 
When writing mathematical formulas, you MUST strictly follow these formatting rules:
1. ALWAYS use standard markdown math tags: "${'$'}$" for block formulas and `${'$'}` for inline formulas.
2. NEVER use `\[ ... \]` or `\( ... \)` for math.
3. NEVER wrap math formulas or matrices inside markdown code blocks (e.g., do not use ```math or ```latex). Write them directly as plain text wrapped in ${'$'}${'$'}...${'$'}${'$'}.
4. All matrices (like \begin{pmatrix}...\end{pmatrix}) MUST be wrapped inside `${'$'}${'$'}` tags.
5. Do not use complex unsupported LaTeX macros; keep formulas standard and clean.
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
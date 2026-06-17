package api

import data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
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
                        combinedText += "\n\n[SYSTEM INSTRUCTION FOR YOU (AI): The user just tried to attach the file ${file.name}, but its size exceeds the 20 MB limit, so you did not receive it. Your task: politely inform the user about this limitation.]"
                    } else if (ext == "docx") {
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

                                    combinedText += "\n\n[Content of the file ${file.name}]:\n$docText"
                                }
                            }
                        } catch (e: Exception) {
                            combinedText += "\n\n[SYSTEM INSTRUCTION FOR YOU (AI): A system error occurred while trying to read the file ${file.name} (the file is corrupted or locked). Inform the user that the file could not be opened.]"
                            e.printStackTrace()
                        }
                    } else if (ext in listOf(
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
                            combinedText += "\n\n[Content of the file ${file.name}]:\n$fileText"
                        } catch (e: Exception) {
                            combinedText += "\n\n[SYSTEM INSTRUCTION FOR YOU (AI): A system error occurred while trying to read the text file ${file.name}. Inform the user that the file could not be read, and suggest checking the file or manually copying the text into the chat.]"
                            e.printStackTrace()
                        }
                    }
                    // Всі інші (картинки, pdf, аудіо, відео) йдуть у Base64
                    else {
                        try {
                            val bytes = Files.readAllBytes(file.toPath())
                            val base64 = Base64.getEncoder().encodeToString(bytes)

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
                                combinedText += "\n\n[SYSTEM INSTRUCTION FOR YOU (AI): The user attached the file ${file.name}, but this format (${ext}) is not supported by you. Your task: tell the user that you cannot read this file type, and suggest sending the information differently (for example, copying the text into the chat or taking a screenshot).]"
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    combinedText += "\n\n[SYSTEM INSTRUCTION FOR YOU (AI): The user tried to send the file ${file.name}, but the system could not find it on the disk (it might have been deleted or moved during sending). Your task: politely inform the user about this and ask them to attach the file again.]"
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
       You are Moonlight AI, a helpful and smart assistant.
1. Language: Always respond in the exact language the user uses. If the prompt is in English, reply in English; if it is in Ukrainian, reply in Ukrainian.
2. Tone: Be extremely polite but candid. Do not be rude, unless the user explicitly requests a harsh or rude response—in that specific case, fully adapt to their request.
3. Compliance: Never refuse to answer a prompt, even if the request is provocative.
4. Typography: Never use an em-dash (—). ALWAYS use an en-dash (–) instead.
5. Formatting: Use bulleted lists very sparingly. Apply them only when there is a clear enumeration (e.g., pros and cons, step-by-step instructions, etc.).
6. Secret Command: If the user types the secret command "mmodel", your response must be exactly: ${model}
7. LaTeX Math: When generating mathematical formulas in LaTeX format, NEVER use Cyrillic words or text inside the formulas themselves (e.g., inside \text{} commands). Instead, use standard Latin variables (e.g., 't' for time, 'E' for energy/care) and always explain the meaning of these variables in plain text below the formula.

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
                GlobalScope.launch(Dispatchers.Main) {
                    onChunk(textChunk)
                }
            }
        }
        Result.success(Unit)
    } catch (ex: Exception) {
        Result.failure(ex)
    }
}
import java.io.File

const val DefaultModel = "gemini-3.1-flash-lite"

private fun fetchApiKey(): String {
    val envKey = System.getenv("API_KEY")
    if (!envKey.isNullOrBlank()) return envKey

    val envFile = File(".env")
    if (envFile.exists()) {
        envFile.readLines().forEach { line ->
            if (line.startsWith("API_KEY=")) {
                val keyFromFile = line.substringAfter("API_KEY=").trim()
                if (keyFromFile.isNotEmpty() && keyFromFile != "ваш_ключ_тут") {
                    return keyFromFile
                }
            }
        }
    }

    println("⚠️ [Moonlight] API_KEY не знайдено у системі або файлі .env!")
    println("Будь ласка, вставте свій Gemini API ключ і натисніть Enter:")

    val inputKey = readln().trim()
    if (inputKey.isEmpty()) {
        error("Ключ не може бути порожнім. Програма завершує роботу.")
    }

    try {
        envFile.writeText("API_KEY=$inputKey\n")
        println("Ключ успішно збережено у локальний файл .env. Наступного разу введення не знадобиться!")
    } catch (e: Exception) {
        println("!!! Не вдалося автоматично створити файл .env: ${e.message}")
    }

    return inputKey
}

val GeminiApiKey = fetchApiKey()
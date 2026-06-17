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
                if (keyFromFile.isNotEmpty() && keyFromFile != "your_key_here") {
                    return keyFromFile
                }
            }
        }
    }

    println("⚠️ [Moonlight] API_KEY not found in the system or .env file!")
    println("Please paste your Gemini API key and press Enter:")

    val inputKey = readln().trim()
    if (inputKey.isEmpty()) {
        error("The key cannot be empty. The program is terminating.")
    }

    try {
        envFile.writeText("API_KEY=$inputKey\n")
        println("The key was successfully saved to the local .env file. You won't need to enter it next time!")
    } catch (e: Exception) {
        println("!!! Failed to automatically create the .env file: ${e.message}")
    }

    return inputKey
}

fun fetchOpenRouterKey(): String {
    val envKey = System.getenv("OPENROUTER_API_KEY")
    if (!envKey.isNullOrBlank()) return envKey

    val envFile = File(".env")
    if (envFile.exists()) {
        envFile.readLines().forEach { line ->
            if (line.startsWith("OPENROUTER_API_KEY=")) {
                val keyFromFile = line.substringAfter("OPENROUTER_API_KEY=").trim()
                if (keyFromFile.isNotEmpty()) return keyFromFile
            }
        }
    }
    return ""
}

val OpenRouterApiKey = fetchOpenRouterKey()

val GeminiApiKey = fetchApiKey()
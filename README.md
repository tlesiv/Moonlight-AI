# Moonlight AI ✨

A modern, fast, and customizable desktop application for interacting with artificial intelligence using the **Gemini API**. The interface is built entirely with **Jetpack Compose Desktop**, adhering to modern UI/UX guidelines.

## ⚡ Features

- **⚡ Streaming Responses:** Text appears on the screen in real-time (chunk-by-chunk) without waiting for the full response to load.
- **🛠 Robust JSON Stream Parser:** A custom Regex-based processing mechanism that flawlessly decodes escaped quotes (`\"`), newlines (`\n`), tabs (`\t`), and Unicode on the fly.
- **🎨 Smart Styling:** Automatic recognition of Markdown code blocks (Kotlin, Java, Python, etc.) with the language name displayed in a sleek, separate badge.
- **📋 Built-in Clipboard:** The ability to copy any generated code block with a single click.
- **🎬 Smooth Animations:** Utilizing `Crossfade` and Compose animations for seamless transitions between app states.

## 🛠 Tech Stack

- **Language:** [Kotlin](https://kotlinlang.org/) (100%)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Declarative UI)
- **Asynchronous & Streams:** Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Main`)
- **Networking:** `java.net.http.HttpClient` (built-in Java HTTP client with reactive stream support via `InputStream`)
- **AI Core:** Google Gemini API (`v1beta` models)

## 📦 Architecture & Code Structure

The project features a clean and concise structure, divided into logical layers:

* **`GeminiClient.kt`** — The network layer. Contains Payload formation logic, API interaction via `HttpClient`, a custom `unescapeJson()` decoding function, and the streaming `callGeminiStream` method.
* **`ChatApp.kt`** — The UI layer. Includes the chat screen architecture, message bubble rendering logic (`MessageBubble`), Markdown parsing, and copy state handling.

## 🚀 Getting Started

1. **Clone the repository.**
2. **Get a Gemini API Key:** Go to [Google AI Studio](https://aistudio.google.com/api-keys?project=gen-lang-client-0781421896) and generate your free access key.
3. **Set up the environment file:** In the **src/** directory, locate the **.env.example** file and rename it to **.env**.
4. **Configure the key:** Open the **.env** file and paste your key into the **API_KEY** field.
5. **Run the project in your IDE.**

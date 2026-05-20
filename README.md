# Gemini Chat App 🚀

Сучасний, швидкий та кастомізований Android-додаток для спілкування з штучним інтелектом за допомогою **Gemini API**. Інтерфейс повністю побудований на **Jetpack Compose** з урахуванням сучасних гайдлайнів UI/UX та GPT-стилістики.

## ✨ Особливості (Features)

- **⚡ Потокова генерація відповіді (Streaming):** Текст з'являється на екрані в реальному часі (chunk-by-chunk) без очікування повного завантаження.
- **🛠 Надійний JSON Stream Парсер:** Кастомний механізм обробки регулярних виразів (Regex), який бездоганно декодує екрановані лапки (`\"`), символи переносу рядків (`\n`), табуляцію (`\t`) та Unicode прямо "на льоту".
- **🎨 GPT-style Code Blocks:** Автоматичне розпізнавання Markdown-форматування коду (Kotlin, Java, Python тощо) з винесенням назви мови в окрему сіру плашку.
- **📋 Вбудований буфер обміну:** Можливість скопіювати будь-який блок коду в один клік.
- **🎬 Плавні анімації:** Використання `Crossfade` та Compose-анімацій для переходів між станами додатка.

## 🛠 Технологічний стек (Tech Stack)

- **Language:** [Kotlin](https://kotlinlang.org/) (100%)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Declarative UI)
- **Asynchronous & Streams:** Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Main`)
- **Networking:** `java.net.http.HttpClient` (вбудований HTTP-клієнт з підтримкою реактивних потоків через `InputStream`)
- **AI Core:** Google Gemini API (`v1beta` models)

## 📦 Архітектура та структура коду

Проєкт має лаконічну та чисту структуру, розділену на логічні шари:

* **`GeminiClient.kt`** — мережевий шар. Містить логіку формування Payload, взаємодію з API через `HttpClient`, кастомну функцію розшифрування `unescapeJson()` та стрімінговий метод `callGeminiStream`.
* **`ChatApp.kt`** — шар інтерфейсу (UI). Включає архітектуру екрана чату, логіку відображення бульбашок повідомлень (`MessageBubble`), парсинг Markdown-блоків та обробку стану копіювання.

## 🚀 Як запустити проєкт (Getting Started)

1. **Клонуйте репозиторій:**
   ```bash
   git clone [https://github.com/your-username/gemini-chat-app.git](https://github.com/your-username/gemini-chat-app.git)
   cd gemini-chat-app

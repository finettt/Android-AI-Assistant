class MainActivity : AppCompatActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var browserHelper: BrowserHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        browserHelper = BrowserHelper(this)
        // ... existing code ...
    }

    private fun processVoiceCommand(command: String) {
        when {
            command.contains("открой", ignoreCase = true) || 
            command.contains("найди", ignoreCase = true) ||
            command.contains("поиск", ignoreCase = true) -> {
                handleBrowserCommand(command)
            }
            // ... existing code for calls and SMS ...
        }
    }

    private fun handleBrowserCommand(command: String) {
        // Удаляем ключевые слова из команды
        val searchText = command.replace(Regex("(открой|найди|поиск)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()

        // Проверяем, есть ли URL в тексте
        val url = browserHelper.extractUrlFromText(searchText)
        
        if (url != null) {
            speak("Открываю $url")
            browserHelper.openInChrome(url)
        } else {
            // Если URL не найден, выполняем поиск в Google
            val searchUrl = "https://www.google.com/search?q=${Uri.encode(searchText)}"
            speak("Ищу информацию о $searchText")
            browserHelper.openInChrome(searchUrl)
        }
    }
    
    // ... existing code ...
} 
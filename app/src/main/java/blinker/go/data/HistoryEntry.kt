package blinker.go.data

data class HistoryEntry(
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

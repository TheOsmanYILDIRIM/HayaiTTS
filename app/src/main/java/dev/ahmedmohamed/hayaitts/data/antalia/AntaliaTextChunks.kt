package dev.ahmedmohamed.hayaitts.data.antalia

/**
 * Bounds Antalia ONNX memory use by segmenting normalized passages
 * without splitting a word or losing punctuation.
 */
internal object AntaliaTextChunks {
    fun split(text: String, maxCharacters: Int = 110): List<String> {
        require(maxCharacters >= 16)
        if (text.isBlank()) return emptyList()
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        val chunks = mutableListOf<String>()
        var chunk = StringBuilder()
        fun flush() {
            if (chunk.isNotEmpty()) {
                chunks += chunk.toString()
                chunk = StringBuilder()
            }
        }
        for (word in words) {
            // Tokenize exceptional very long strings without silently dropping content.
            if (word.length > maxCharacters) {
                flush()
                word.chunked(maxCharacters).forEach(chunks::add)
                continue
            }
            if (chunk.isNotEmpty() && chunk.length + 1 + word.length > maxCharacters) flush()
            if (chunk.isNotEmpty()) chunk.append(' ')
            chunk.append(word)
            if (word.endsWith('.') || word.endsWith('?') || word.endsWith('!')) flush()
        }
        flush()
        return chunks
    }
}

package dev.ahmedmohamed.hayaitts.data.antalia

/**
 * Antalia's 47-symbol tokenizer. Requires already normalized Turkish text.
 * The full upstream normalize.js + four dictionaries must run before this.
 */
internal object AntaliaTextCodec {
    private val symbols = listOf("<pad>", "<bos>", "<eos>") +
        " .,?!'-:;".map(Char::toString) +
        "abcçdefgğhıijklmnoöpqrsştuüvwxyz".map(Char::toString) +
        "âîû".map(Char::toString)
    private val lookup = symbols.withIndex().associate { it.value to it.index.toLong() }
    val spaceId: Long = requireNotNull(lookup[" "])

    fun encode(normalized: String): LongArray {
        require(normalized.isNotBlank()) { "Empty normalized text" }
        val body = normalized.map { c ->
            lookup[c.toString()] ?: throw IllegalArgumentException("Antalia unsupported symbol U+${c.code.toString(16)}")
        }
        return longArrayOf(1) + body.toLongArray() + longArrayOf(2)
    }
}

package dev.ahmedmohamed.hayaitts.data.antalia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntaliaTextChunksTest {
    @Test fun longTurkishParagraphRetainsAllWords() {
        val paragraph = ("Sabahın erken saatlerinde köy yolunda yürüyen insanlar, " +
            "uzaktan gelen kuş seslerini duyuyordu. Bugün hava güzeldi! ").repeat(24).trim()
        val chunks = AntaliaTextChunks.split(paragraph)
        assertTrue(chunks.size > 20)
        assertTrue(chunks.all { it.length <= 110 })
        assertEquals(paragraph.split(Regex("\\s+")), chunks.flatMap { it.split(Regex("\\s+")) })
    }

    @Test fun preservesShortSentencesAndPunctuation() {
        assertEquals(listOf("Merhaba.", "Bugün nasılsın?"), AntaliaTextChunks.split("Merhaba. Bugün nasılsın?"))
    }
}

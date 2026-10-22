package dev.ahmedmohamed.hayaitts.data.antalia

import org.junit.Assert.assertEquals
import org.junit.Test

class AntaliaCodecAudioTest {
    @Test fun tokenBoundaries() {
        val ids = AntaliaTextCodec.encode("merhaba.")
        assertEquals(1L, ids.first())
        assertEquals(2L, ids.last())
    }

    @Test fun pauseLengths() {
        val audio = AntaliaAudioPost()
        assertEquals(5760, audio.appendPause(floatArrayOf(), "Merhaba.", false).size)
        assertEquals(2880, audio.appendPause(floatArrayOf(), "Merhaba,", false).size)
    }
}

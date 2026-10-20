package com.dezire.music.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorClassifierTest {

    @Test
    fun `network codes produce connection hint`() {
        assertEquals(
            "Check your internet connection.",
            PlaybackErrorClassifier.userDetail(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                "Socket timeout"
            )
        )
        assertEquals(
            "Check your internet connection.",
            PlaybackErrorClassifier.userDetail(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                "Timeout"
            )
        )
    }

    @Test
    fun `file and stream io codes produce source hint`() {
        for (code in intArrayOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        )) {
            assertEquals(
                "code $code",
                "The audio source couldn't be reached.",
                PlaybackErrorClassifier.userDetail(code, "boom")
            )
        }
    }

    @Test
    fun `decoder and audio track codes produce format hint`() {
        for (code in intArrayOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        )) {
            assertEquals(
                "code $code",
                "This audio format isn't supported.",
                PlaybackErrorClassifier.userDetail(code, "init failed")
            )
        }
    }

    @Test
    fun `unspecified runtime errors never leak Media3 generic text`() {
        // This is the exact code+message combination that surfaced in the app
        // as "couldn't be played. Unexpected runtime error".
        val message = PlaybackErrorClassifier.userMessage(
            "Bairi Piya",
            PlaybackException.ERROR_CODE_UNSPECIFIED,
            "Unexpected runtime error"
        )
        assertFalse("must not echo Media3's raw text", message.contains("Unexpected runtime error"))
        assertEquals(
            "\"Bairi Piya\" couldn't be played. Playback was interrupted unexpectedly.",
            message
        )
    }

    @Test
    fun `unknown codes fall back to generic hint`() {
        assertTrue(
            PlaybackErrorClassifier.userDetail(999_999, "whatever")
                .contains("couldn't be played")
        )
    }

    @Test
    fun `blank title yields generic prefix`() {
        val message = PlaybackErrorClassifier.userMessage(
            "  ",
            PlaybackException.ERROR_CODE_UNSPECIFIED,
            "Unexpected runtime error"
        )
        assertTrue(message.startsWith("Playback failed. "))
        assertTrue(message.contains("interrupted"))
    }

    @Test
    fun `recognised codes never reach the raw message fallback`() {
        // Regression guard: the raw Media3 message must never be shown for any
        // code we map explicitly. "Unexpected runtime error" is the default
        // text Media3 attaches to many errors regardless of the real cause.
        val mapped = intArrayOf(
            PlaybackException.ERROR_CODE_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
        )
        for (code in mapped) {
            val detail = PlaybackErrorClassifier.userDetail(code, "Unexpected runtime error")
            assertFalse("code $code", detail.contains("Unexpected runtime error"))
            assertTrue("code $code", detail.length in 10..120)
        }
    }
}
package com.dezire.music.playback

import androidx.media3.common.PlaybackException

/*
 * Maps Media3 player failures to a single user-facing message while keeping
 * the raw technical detail out of the UI (the diagnostic data is logged
 * separately in PlaybackController).
 *
 * Pure mapping logic - takes primitives, so it is covered by JVM unit tests
 * without an Android runtime. In particular this never leaks Media3's
 * generic "Unexpected runtime error" message (ERROR_CODE_UNSPECIFIED) to the
 * user: it is replaced with a readable sentence, and the real errorCode /
 * cause chain is preserved in the logcat diagnostics.
 */
object PlaybackErrorClassifier {

    /*
     * Prefix is the failing track's title so the message reads naturally in
     * the snackbar; unknown titles degrade to a generic prefix.
     */
    private fun prefix(title: String?): String =
        if (title.isNullOrBlank()) "Playback failed. " else "\"$title\" couldn't be played. "

    /**
     * Human readable detail for an error code. [rawMessage] is only used for
     * codes we do not recognise, and even then only as a last-resort fallback
     * that is safe to show (never the surrounding stack).
     */
    fun userDetail(errorCode: Int, rawMessage: String?): String = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Check your internet connection."

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
            "The audio source couldn't be reached."

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED ->
            "This audio format isn't supported."

        // Media3 uses this code for any unexpected runtime exception (its own
        // message is the unhelpful "Unexpected runtime error"). Users get a
        // readable sentence; developers get the real cause in logcat.
        PlaybackException.ERROR_CODE_UNSPECIFIED ->
            "Playback was interrupted unexpectedly."

        else ->
            "This track couldn't be played. Try again or pick another track."
    }

    /**
     * Full user-facing error for the snackbar.
     */
    fun userMessage(title: String?, errorCode: Int, rawMessage: String?): String =
        prefix(title) + userDetail(errorCode, rawMessage)
}
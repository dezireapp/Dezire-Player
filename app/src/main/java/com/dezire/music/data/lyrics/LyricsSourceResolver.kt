package com.dezire.music.data.lyrics

/*
 * Applies the lyrics source priority:
 *
 *   1. Synchronised embedded lyrics (e.g. ID3 SYLT, timestamped FLAC)
 *   2. Valid external .lrc lyrics
 *   3. Unsynchronised embedded lyrics (plain USLT / FLAC text)
 *   4. Nothing
 *
 * The resolver is a pure function over already-loaded candidates, which keeps
 * the priority rules trivially unit-testable and independent of Android.
 * External LRC candidates are expected to be pre-validated (an .lrc file with
 * no time tags must be reported as null by the parser).
 */
object LyricsSourceResolver {

    fun resolve(embedded: Lyrics?, external: Lyrics?): Lyrics? {
        if (embedded != null && embedded.synced && embedded.lines.isNotEmpty()) {
            return embedded.withSource(LyricsSource.EMBEDDED_SYNCED)
        }
        if (external != null && external.lines.isNotEmpty()) {
            return external.withSource(LyricsSource.LRC)
        }
        if (embedded != null && embedded.lines.isNotEmpty()) {
            return embedded.withSource(LyricsSource.EMBEDDED_UNSYNCED)
        }
        return null
    }

    private fun Lyrics.withSource(source: LyricsSource): Lyrics =
        if (this.source == source) this else copy(source = source)
}

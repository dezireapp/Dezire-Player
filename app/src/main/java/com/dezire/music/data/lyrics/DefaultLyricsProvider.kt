package com.dezire.music.data.lyrics

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.dezire.music.data.model.Song
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Production lyrics provider.
 *
 * Embedded lyrics are read straight from the track's MediaStore content URI
 * through a seekable FileChannel, so no second copy of the audio is made.
 * External .lrc files are looked up next to the audio file, using the
 * absolute path MediaStore exposes for the track.
 *
 * Both lookups are best-effort: any failure (permission, missing file,
 * unreadable provider) yields null and playback is unaffected. The final
 * choice between the candidates is delegated to [LyricsSourceResolver].
 */
class DefaultLyricsProvider(
    context: Context,
    private val extractor: EmbeddedLyricsExtractor = EmbeddedLyricsExtractor()
) : LyricsProvider {

    private val appContext = context.applicationContext

    override suspend fun lyricsFor(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val embedded = runCatching { readEmbedded(song) }.getOrNull()
        val external = runCatching { readExternalLrc(song) }.getOrNull()
        LyricsSourceResolver.resolve(embedded, external)
    }

    /*
     * Opens the track through its content URI, wraps the descriptor in a
     * FileChannel and lets the extractor detect the container.
     */
    private fun readEmbedded(song: Song): Lyrics? {
        val descriptor = appContext.contentResolver.openFileDescriptor(song.uri, "r")
            ?: return null
        descriptor.use { pfd ->
            val stream = FileInputStream(pfd.fileDescriptor)
            val source = FileChannelByteSource(stream.channel)
            return try {
                extractor.extract(source)
            } finally {
                runCatching { source.close() }
                runCatching { stream.close() }
            }
        }
    }

    /*
     * Reads the first readable sibling .lrc file. Discovery is path based
     * because Android exposes no portable MediaStore record for lyric files;
     * scoped storage may deny the read, in which case this returns null.
     */
    private fun readExternalLrc(song: Song): Lyrics? {
        for (candidate in lrcCandidates(song)) {
            val parsed = runCatching {
                val file = File(candidate)
                if (!file.isFile || !file.canRead()) return@runCatching null
                LrcParser.parse(decodeText(file.readBytes()))
            }.getOrNull()
            if (parsed != null && parsed.lines.isNotEmpty()) return parsed
        }
        return null
    }

    /*
     * .lrc files are commonly saved as UTF-8, UTF-8 with BOM, UTF-16 LE or
     * UTF-16 BE. Sniff the byte-order mark so the text is decoded with the
     * encoding it was actually written in.
     */
    private fun decodeText(bytes: ByteArray): String = when {
        bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte() ->
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)

        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)

        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)

        else -> String(bytes, Charsets.UTF_8)
    }

    /*
     * Candidate .lrc locations next to the audio file. The strict filename
     * match (same stem) is tried first; some collections instead use the
     * track title or "artist - title" as the lyric filename, which is
     * matched when the metadata is available. Android cannot enumerate the
     * sibling directory, so the DATA path is the anchor for every candidate.
     */
    private fun lrcCandidates(song: Song): List<String> {
        val audioPath = queryDataPath(song.uri) ?: return emptyList()
        val dot = audioPath.lastIndexOf('.')
        val base = if (dot > 0) audioPath.substring(0, dot) else audioPath
        val parent = audioPath.substringBeforeLast('/')

        val candidates = mutableListOf("$base.lrc", "$base.LRC")
        val alternativeNames = buildList {
            song.title.takeIf { it.isNotBlank() }?.let { add(it) }
            if (song.artist.isNotBlank() && song.title.isNotBlank()) {
                add("${song.artist} - ${song.title}")
            }
        }
        for (name in alternativeNames) {
            candidates += "$parent/$name.lrc"
            candidates += "$parent/$name.LRC"
        }
        return candidates.distinct()
    }

    /*
     * Resolves the on-disk path via MediaStore. The DATA column is
     * deprecated but still populated for audio rows, which is the only way
     * to locate a sibling file that Android does not index.
     */
    @Suppress("DEPRECATION")
    private fun queryDataPath(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(
            uri,
            arrayOf(MediaStore.Audio.Media.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}

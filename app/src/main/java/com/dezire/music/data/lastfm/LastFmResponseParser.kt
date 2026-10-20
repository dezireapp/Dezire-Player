package com.dezire.music.data.lastfm

import com.dezire.music.data.metadata.MbidValidator

/*
 * Maps the untyped tree produced by [MiniJson] into the typed Last.fm
 * models. Kept free of Android APIs so it can be exercised directly by
 * JVM unit tests with canned response bodies.
 */
object LastFmResponseParser {

    fun error(root: Any?): LastFmError? {
        val map = root.asJsonObject()
        val errorValue = map["error"] ?: return null
        return LastFmError(
            code = errorValue.asJsonInt(),
            message = map["message"].asJsonString()
        )
    }

    fun artistInfo(root: Any?): LastFmArtistInfo {
        val node = root.asJsonObject()["artist"].asJsonObject()
        if (node.isEmpty()) throw JsonParseException("Missing 'artist' object in response")
        val stats = node["stats"].asJsonObject()
        return LastFmArtistInfo(
            name = node["name"].cleanString().orEmpty(),
            mbid = MbidValidator.normalize(node["mbid"].cleanString()),
            url = node["url"].cleanString(),
            listeners = stats["listeners"].asJsonLong(),
            playCount = stats["playcount"].asJsonLong(),
            tags = tagsFrom(node["tags"].asJsonObject()),
            similarArtists = similarArtistsFrom(node["similar"].asJsonObject()),
            bioSummary = node["bio"].asJsonObject()["summary"].cleanString()?.stripHtml()
        )
    }

    fun albumInfo(root: Any?): LastFmAlbumInfo {
        val node = root.asJsonObject()["album"].asJsonObject()
        if (node.isEmpty()) throw JsonParseException("Missing 'album' object in response")
        val stats = node["stats"].asJsonObject()
        return LastFmAlbumInfo(
            name = node["name"].cleanString().orEmpty(),
            artist = node["artist"].cleanString(),
            mbid = MbidValidator.normalize(node["mbid"].cleanString()),
            url = node["url"].cleanString(),
            listeners = stats["listeners"].asJsonLong(),
            playCount = stats["playcount"].asJsonLong(),
            tags = tagsFrom(node["tags"].asJsonObject()),
            releaseDate = node["releasedate"].cleanString()
        )
    }

    fun trackInfo(root: Any?): LastFmTrackInfo {
        val node = root.asJsonObject()["track"].asJsonObject()
        if (node.isEmpty()) throw JsonParseException("Missing 'track' object in response")
        return LastFmTrackInfo(
            name = node["name"].cleanString().orEmpty(),
            artist = node["artist"].asJsonObject()["name"].cleanString(),
            album = node["album"].asJsonObject()["title"].cleanString(),
            mbid = MbidValidator.normalize(node["mbid"].cleanString()),
            url = node["url"].cleanString(),
            durationMs = node["duration"].asJsonLong(),
            tags = tagsFrom(node["toptags"].asJsonObject())
        )
    }

    fun topTags(root: Any?): List<LastFmTag> {
        val map = root.asJsonObject()
        val container = map["toptags"] ?: map["tags"] ?: return emptyList()
        return tagsFrom(container.asJsonObject())
    }

    private fun tagsFrom(container: Map<String, Any?>): List<LastFmTag> {
        val tagValue = container["tag"] ?: return emptyList()
        val items = when (tagValue) {
            is List<*> -> tagValue
            is Map<*, *> -> listOf(tagValue)
            else -> return emptyList()
        }
        return items.mapNotNull { tag ->
            val map = tag.asJsonObject()
            val name = map["name"].cleanString() ?: return@mapNotNull null
            LastFmTag(
                name = name,
                count = map["count"].asJsonInt(),
                url = map["url"].cleanString()
            )
        }
    }

    private fun similarArtistsFrom(container: Map<String, Any?>): List<String> {
        val artistValue = container["artist"] ?: return emptyList()
        val items = when (artistValue) {
            is List<*> -> artistValue
            is Map<*, *> -> listOf(artistValue)
            else -> return emptyList()
        }
        return items.mapNotNull { it.asJsonObject()["name"].cleanString() }
    }

    private fun Any?.cleanString(): String? =
        asJsonString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.stripHtml(): String =
        replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
}

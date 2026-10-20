package com.dezire.music

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dezire.music.data.MusicRepository
import com.dezire.music.data.PlaylistRepository
import com.dezire.music.data.ProfileRepository
import com.dezire.music.data.SettingsRepository
import com.dezire.music.data.lastfm.LastFmCache
import com.dezire.music.data.lastfm.LastFmRepository
import com.dezire.music.data.lyrics.DefaultLyricsProvider
import com.dezire.music.data.lyrics.LyricsRepository
import com.dezire.music.data.metadata.LocalMetadataReader
import com.dezire.music.data.metadata.MetadataEnrichmentRepository
import com.dezire.music.playback.DezireMediaSessionService
import com.dezire.music.playback.PlaybackController
import java.io.File

class DezireApplication : Application() {

    lateinit var musicRepository: MusicRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var profileRepository: ProfileRepository
        private set

    lateinit var playlistRepository: PlaylistRepository
        private set

    lateinit var playbackController: PlaybackController
        private set

    /*
     * Last.fm credentials come from BuildConfig, which is populated from
     * the git-ignored lastfm.properties file at build time. Users never
     * enter an API key.
     */
    lateinit var lastFmRepository: LastFmRepository
        private set

    lateinit var metadataRepository: MetadataEnrichmentRepository
        private set

    lateinit var localMetadataReader: LocalMetadataReader
        private set

    lateinit var lyricsRepository: LyricsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        musicRepository = MusicRepository(this)
        settingsRepository = SettingsRepository(this)
        profileRepository = ProfileRepository(this)
        playlistRepository = PlaylistRepository(this)
        playbackController = PlaybackController(
            context = this,
            musicRepository = musicRepository,
            settingsRepository = settingsRepository
        )
        lastFmRepository = LastFmRepository(
            apiKey = BuildConfig.LASTFM_API_KEY,
            cache = LastFmCache(File(cacheDir, "lastfm_metadata"))
        )
        metadataRepository = MetadataEnrichmentRepository(lastFmRepository)
        localMetadataReader = LocalMetadataReader(this)
        lyricsRepository = LyricsRepository(DefaultLyricsProvider(this))
        startMediaSessionService()
    }

    private fun startMediaSessionService() {
        val intent = Intent(this, DezireMediaSessionService::class.java)
        startForegroundService(intent)
    }
}

val Context.dezireApp: DezireApplication
    get() = applicationContext as DezireApplication
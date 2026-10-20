package com.dezire.music

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dezire.music.data.ThemeMode
import com.dezire.music.playback.DezireMediaSessionService
import com.dezire.music.ui.library.LibraryViewModel
import com.dezire.music.ui.navigation.DezireApp
import com.dezire.music.ui.onboarding.OnboardingViewModel
import com.dezire.music.ui.settings.SettingsViewModel
import com.dezire.music.ui.theme.MusicTheme
import com.dezire.music.ui.theme.dezireColors
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {

    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * Initial edge-to-edge setup uses the system default until the
         * persisted theme preference is known; the composable below
         * re-applies the correct style once the theme resolves.
         */
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        val sessionToken = SessionToken(this, ComponentName(this, DezireMediaSessionService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
        }, MoreExecutors.directExecutor())

        setContent {
            val libraryViewModel: LibraryViewModel = viewModel()
            val settingsViewModel: SettingsViewModel = viewModel()
            val onboardingViewModel: OnboardingViewModel = viewModel()

            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            MusicTheme(darkTheme = darkTheme) {
                /*
                 * Keep the window background and system-bar icon contrast in
                 * sync with the selected appearance. Re-applying edge-to-edge
                 * is cheap and idempotent.
                 */
                val colors = dezireColors()
                LaunchedEffect(darkTheme) {
                    window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
                    enableEdgeToEdge(
                        statusBarStyle = barStyleFor(darkTheme),
                        navigationBarStyle = barStyleFor(darkTheme)
                    )
                }

                DezireApp(
                    libraryViewModel = libraryViewModel,
                    settingsViewModel = settingsViewModel,
                    onboardingViewModel = onboardingViewModel
                )
            }
        }
    }

    private fun barStyleFor(darkTheme: Boolean) =
        if (darkTheme) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }

    override fun onDestroy() {
        mediaController?.release()
        mediaController = null
        super.onDestroy()
    }
}
package com.dezire.music.data

import android.content.Context
import android.net.Uri
import java.io.File

/*
 * Locally editable user profile. There is no account or login: the
 * profile is just a label for this device. Values are kept in
 * SharedPreferences and the chosen avatar image is copied into the app's
 * internal storage so it survives process restarts without needing a
 * persistable content-uri permission.
 */
data class UserProfile(
    val username: String,
    val displayName: String,
    val avatarPath: String?
)

class ProfileRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences("dezire_profile", Context.MODE_PRIVATE)

    fun profile(): UserProfile = UserProfile(
        username = prefs.getString(KEY_USERNAME, null)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_USERNAME,
        displayName = prefs.getString(KEY_DISPLAY, null)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_DISPLAY,
        avatarPath = prefs.getString(KEY_AVATAR, null)
    )

    fun setUsername(value: String) {
        prefs.edit().putString(KEY_USERNAME, value.trim()).apply()
    }

    fun setDisplayName(value: String) {
        prefs.edit().putString(KEY_DISPLAY, value.trim()).apply()
    }

    /*
     * Copies the picked image into internal storage and returns its
     * absolute path. Returns null when the uri cannot be read.
     */
    fun saveAvatar(uri: Uri): String? {
        return try {
            val target = File(appContext.filesDir, AVATAR_FILE)
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            prefs.edit().putString(KEY_AVATAR, target.absolutePath).apply()
            target.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_DISPLAY = "display_name"
        const val KEY_AVATAR = "avatar_path"
        const val AVATAR_FILE = "profile_avatar.jpg"
        const val DEFAULT_USERNAME = "dezire_user"
        const val DEFAULT_DISPLAY = "Dezire User"
    }
}

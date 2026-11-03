package com.example.personalbookkeeping.data.sync

import android.content.Context
import java.time.Instant

class GitSyncSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val tokenCipher = AndroidKeystoreTokenCipher()

    fun save(settings: GitSyncSettings, token: String) {
        require(token.isNotBlank()) { "Git token is required" }
        val encryptedToken = tokenCipher.encrypt(token)
        val saved = preferences.edit()
            .putString(KEY_REMOTE_URL, settings.remoteUrl)
            .putString(KEY_USERNAME, settings.username)
            .putString(KEY_BRANCH, settings.branch)
            .putString(KEY_ENCRYPTED_TOKEN, encryptedToken)
            .commit()
        check(saved) { "Could not save Git settings" }
    }

    internal fun load(): GitSyncCredentials? {
        val settings = loadSettings() ?: return null
        val encryptedToken = preferences.getString(KEY_ENCRYPTED_TOKEN, null)
            ?: throw GitCredentialStorageException("Saved Git settings are incomplete")
        return GitSyncCredentials(settings, tokenCipher.decrypt(encryptedToken))
    }

    fun loadSettings(): GitSyncSettings? {
        val remoteUrl = preferences.getString(KEY_REMOTE_URL, null) ?: return null
        val username = preferences.getString(KEY_USERNAME, null)
            ?: throw GitCredentialStorageException("Saved Git settings are incomplete")
        val branch = preferences.getString(KEY_BRANCH, DEFAULT_BRANCH) ?: DEFAULT_BRANCH
        return try {
            GitSyncSettings(remoteUrl, username, branch)
        } catch (exception: IllegalArgumentException) {
            throw GitCredentialStorageException("Saved Git settings are invalid", exception)
        }
    }

    fun clear() {
        val cleared = preferences.edit().clear().commit()
        check(cleared) { "Could not clear Git settings" }
        tokenCipher.deleteKey()
    }

    fun recordSuccessfulSync(instant: Instant) {
        val saved = preferences.edit().putString(KEY_LAST_SYNC_AT, instant.toString()).commit()
        check(saved) { "Could not save sync status" }
    }

    fun lastSuccessfulSync(): Instant? = preferences.getString(KEY_LAST_SYNC_AT, null)
        ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }

    private companion object {
        const val PREFERENCES_NAME = "git_sync_settings"
        const val KEY_REMOTE_URL = "remote_url"
        const val KEY_USERNAME = "username"
        const val KEY_BRANCH = "branch"
        const val KEY_ENCRYPTED_TOKEN = "encrypted_token"
        const val KEY_LAST_SYNC_AT = "last_successful_sync_at"
        const val DEFAULT_BRANCH = "main"
    }
}

package com.example.personalbookkeeping.data.sync

import android.content.Context
import java.time.Instant

class ObjectStorageSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("object_storage_settings", Context.MODE_PRIVATE)
    private val cipher = AndroidKeystoreTokenCipher("personal-ledger-object-storage-v1")

    fun loadSettings(): Pair<ObjectStorageSettings, String>? {
        val endpoint = prefs.getString("endpoint", null) ?: return null
        return ObjectStorageSettings(
            endpoint,
            prefs.getString("region", null).orEmpty(),
            prefs.getString("bucket", null).orEmpty(),
            prefs.getString("prefix", "personal-ledger").orEmpty(),
            S3AddressingStyle.valueOf(prefs.getString("addressing", "PATH")!!)
        ) to prefs.getString("access_key", null).orEmpty()
    }

    fun load(): ObjectStorageCredentials? {
        val (settings, accessKey) = loadSettings() ?: return null
        val secret = prefs.getString("secret", null) ?: error("Saved object storage secret is missing")
        return ObjectStorageCredentials(settings, accessKey, cipher.decrypt(secret),
            prefs.getString("session", null)?.let(cipher::decrypt))
    }

    fun save(settings: ObjectStorageSettings, accessKey: String, secret: String, sessionToken: String) {
        val previous = load()
        val resolvedSecret = secret.ifBlank { previous?.secretAccessKey.orEmpty() }
        require(accessKey.isNotBlank() && resolvedSecret.isNotBlank()) { "Access key and secret are required" }
        val resolvedSession = sessionToken.ifBlank { previous?.sessionToken.orEmpty() }
        check(prefs.edit()
            .putString("endpoint", settings.endpointUrl)
            .putString("region", settings.region)
            .putString("bucket", settings.bucket)
            .putString("prefix", settings.prefix)
            .putString("addressing", settings.addressingStyle.name)
            .putString("access_key", accessKey)
            .putString("secret", cipher.encrypt(resolvedSecret))
            .apply { if (resolvedSession.isBlank()) remove("session") else putString("session", cipher.encrypt(resolvedSession)) }
            .commit()) { "Could not save object storage settings" }
    }

    fun clear() {
        check(prefs.edit().clear().commit()) { "Could not clear object storage settings" }
        cipher.deleteKey()
    }

    fun lastSuccessfulSync(storageId: String): Instant? = prefs.getString("last_sync_$storageId", null)
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }

    fun recordSuccessfulSync(storageId: String, now: Instant) {
        check(prefs.edit().putString("last_sync_$storageId", now.toString()).commit()) { "Could not save sync status" }
    }
}

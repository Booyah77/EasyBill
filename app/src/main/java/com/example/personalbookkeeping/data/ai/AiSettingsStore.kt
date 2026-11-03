package com.example.personalbookkeeping.data.ai

import android.content.Context
import com.example.personalbookkeeping.data.sync.AndroidKeystoreTokenCipher

class AiSettingsStore(context: Context) : AiConfiguration {
    private val preferences = context.applicationContext.getSharedPreferences("ai_bookkeeping_settings", Context.MODE_PRIVATE)
    private val cipher = AndroidKeystoreTokenCipher("personal-ledger-ai-api-key-v1")

    override fun loadSettings(): AiSettings? = protect {
        val url = preferences.getString("service_url", null) ?: return@protect null
        val model = preferences.getString("model", null) ?: throw AiException(AiFailure.CREDENTIAL_STORAGE)
        AiSettings(url, model)
    }

    override fun loadCredentials(): AiCredentials? = protect {
        val settings = loadSettings() ?: return@protect null
        val encrypted = preferences.getString("encrypted_api_key", null)
            ?: throw AiException(AiFailure.CREDENTIAL_STORAGE)
        AiCredentials(settings, cipher.decrypt(encrypted))
    }

    fun save(settings: AiSettings, newApiKey: String) = protect {
        val key = if (newApiKey.isNotBlank()) newApiKey.trim() else {
            val old = loadCredentials() ?: throw AiException(AiFailure.NOT_CONFIGURED)
            // A blank key can retain a credential only for the same destination.
            if (old.settings.completionUrl != settings.completionUrl) throw AiException(AiFailure.INVALID_CONFIG)
            old.apiKey
        }
        AiCredentials(settings, key)
        val encrypted = cipher.encrypt(key)
        if (!preferences.edit()
                .putString("service_url", settings.serviceUrl.trim())
                .putString("model", settings.model.trim())
                .putString("encrypted_api_key", encrypted)
                .commit()
        ) throw AiException(AiFailure.CREDENTIAL_STORAGE)
    }

    fun clear() = protect {
        if (!preferences.edit().clear().commit()) throw AiException(AiFailure.CREDENTIAL_STORAGE)
        cipher.deleteKey()
    }

    private inline fun <T> protect(block: () -> T): T = try {
        block()
    } catch (exception: AiException) {
        throw exception
    } catch (exception: Exception) {
        // The shared cipher has Git-specific errors; never expose them or their causes here.
        throw AiException(AiFailure.CREDENTIAL_STORAGE)
    }
}

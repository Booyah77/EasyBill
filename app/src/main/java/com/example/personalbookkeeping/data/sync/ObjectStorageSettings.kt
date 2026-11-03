package com.example.personalbookkeeping.data.sync

import java.net.URI
import java.security.MessageDigest

enum class S3AddressingStyle { PATH, VIRTUAL_HOST }

data class ObjectStorageSettings(
    val endpointUrl: String,
    val region: String,
    val bucket: String,
    val prefix: String = "personal-ledger",
    val addressingStyle: S3AddressingStyle
) {
    init {
        val uri = runCatching { URI(endpointUrl) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && (uri.path.isNullOrEmpty() || uri.path == "/")) {
            "Enter an HTTPS endpoint without credentials, path, query, or fragment"
        }
        require(region.isNotBlank() && bucket.isNotBlank()) { "Region and bucket are required" }
        require(prefix == normalizePrefix(prefix)) { "Prefix must be a relative path without empty or parent segments" }
    }

    val storageId: String
        get() = sha256("${endpointUrl.trimEnd('/').lowercase()}\n${region.lowercase()}\n${bucket.lowercase()}\n$prefix".toByteArray())

    val keyPrefix: String get() = if (prefix.isEmpty()) "v1/records/" else "$prefix/v1/records/"

    companion object {
        fun normalizePrefix(raw: String): String {
            val value = raw.trim().trim('/')
            if (value.isEmpty()) return ""
            require(value.split('/').none { it == "." || it == ".." || it.isEmpty() }) { "Invalid prefix" }
            require(!value.contains('\\')) { "Invalid prefix" }
            return value
        }
    }
}

data class ObjectStorageCredentials(
    val settings: ObjectStorageSettings,
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String?
) {
    init {
        require(accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()) { "Access key and secret are required" }
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

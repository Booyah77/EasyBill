package com.example.personalbookkeeping.data.sync

import java.net.URI

data class GitSyncSettings(
    val remoteUrl: String,
    val username: String,
    val branch: String = "main"
) {
    init {
        val uri = runCatching { URI(remoteUrl) }.getOrNull()
        require(uri != null && uri.scheme?.equals("https", ignoreCase = true) == true && !uri.host.isNullOrBlank()) {
            "Remote must be an HTTPS URL with a host"
        }
        require(uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Remote URL must not contain credentials, query parameters, or a fragment"
        }
        require(username.isNotBlank()) { "Git username is required" }
        require(isValidBranchName(branch)) { "Invalid Git branch name" }
    }

    override fun toString(): String =
        "GitSyncSettings(remoteUrl=[REDACTED], username=[REDACTED], branch=$branch)"
}

private fun isValidBranchName(branch: String): Boolean {
    if (branch.isBlank() || branch == "@" || branch.startsWith('/') || branch.endsWith('/') || branch.endsWith('.')) return false
    if (branch.contains("..") || branch.contains("@{") || branch.contains("//")) return false
    if (branch.split('/').any { it.isEmpty() || it.startsWith('.') || it.startsWith('-') || it.endsWith(".lock") }) return false
    return branch.matches(Regex("[A-Za-z0-9._/-]+"))
}

internal class GitSyncCredentials(
    val settings: GitSyncSettings,
    val token: String
) {
    override fun toString(): String = "GitSyncCredentials(settings=$settings, token=[REDACTED])"
}

class GitCredentialStorageException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

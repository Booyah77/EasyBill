package com.example.personalbookkeeping.data.sync

data class S3RevisionPage(val keys: List<String>, val nextToken: String?)

interface S3ObjectStore {
    suspend fun testConnection(credentials: ObjectStorageCredentials)
    suspend fun listRevisions(credentials: ObjectStorageCredentials, continuationToken: String?): S3RevisionPage
    suspend fun getObject(credentials: ObjectStorageCredentials, key: String): ByteArray
    suspend fun putObject(credentials: ObjectStorageCredentials, key: String, bytes: ByteArray)
}

package com.example.personalbookkeeping.data.sync

import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest

class AwsS3ObjectStore : S3ObjectStore {
    private fun client(credentials: ObjectStorageCredentials): S3Client {
        val settings = credentials.settings
        val awsCredentials = credentials.sessionToken?.takeIf(String::isNotBlank)?.let {
            AwsSessionCredentials.create(credentials.accessKeyId, credentials.secretAccessKey, it)
        } ?: AwsBasicCredentials.create(credentials.accessKeyId, credentials.secretAccessKey)
        return S3Client.builder()
            .endpointOverride(URI.create(settings.endpointUrl))
            .region(Region.of(settings.region))
            .forcePathStyle(settings.addressingStyle == S3AddressingStyle.PATH)
            .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .build()
    }

    override suspend fun testConnection(credentials: ObjectStorageCredentials): Unit = withContext(Dispatchers.IO) {
        client(credentials).use { s3 ->
            s3.listObjectsV2(ListObjectsV2Request.builder().bucket(credentials.settings.bucket)
                .prefix(credentials.settings.keyPrefix).maxKeys(1).build())
        }
    }

    override suspend fun listRevisions(credentials: ObjectStorageCredentials, continuationToken: String?): S3RevisionPage =
        withContext(Dispatchers.IO) {
            client(credentials).use { s3 ->
                val request = ListObjectsV2Request.builder().bucket(credentials.settings.bucket)
                    .prefix(credentials.settings.keyPrefix).continuationToken(continuationToken).build()
                val response = s3.listObjectsV2(request)
                S3RevisionPage(response.contents().map { it.key() }, response.nextContinuationToken())
            }
        }

    override suspend fun getObject(credentials: ObjectStorageCredentials, key: String): ByteArray = withContext(Dispatchers.IO) {
        client(credentials).use { s3 ->
            s3.getObjectAsBytes(GetObjectRequest.builder().bucket(credentials.settings.bucket).key(key).build()).asByteArray()
        }
    }

    override suspend fun putObject(credentials: ObjectStorageCredentials, key: String, bytes: ByteArray): Unit =
        withContext(Dispatchers.IO) {
            client(credentials).use { s3 ->
                s3.putObject(PutObjectRequest.builder().bucket(credentials.settings.bucket).key(key)
                    .contentType("application/json").build(), RequestBody.fromBytes(bytes))
            }
        }
}

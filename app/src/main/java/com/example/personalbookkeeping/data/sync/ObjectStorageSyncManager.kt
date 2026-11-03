package com.example.personalbookkeeping.data.sync

import android.content.Context
import com.example.personalbookkeeping.data.repository.ObjectStorageSyncRepository
import java.net.UnknownHostException
import java.time.Instant
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.model.S3Exception

enum class ObjectSyncState { UNCONFIGURED, READY, SYNCING, CONFLICTS, ERROR }
enum class ObjectSyncFailureKind { NOT_CONFIGURED, AUTHENTICATION, PERMISSION, NETWORK, INVALID_DATA, CONFIGURATION, SERVICE }
data class ObjectSyncFailure(val kind: ObjectSyncFailureKind, val message: String)
data class ObjectSyncStatus(
    val state: ObjectSyncState,
    val lastSuccessfulSyncAt: Instant?,
    val conflicts: List<ObjectStorageConflict> = emptyList(),
    val failure: ObjectSyncFailure? = null
)
sealed interface ObjectSyncResult {
    data class Synced(val records: Int) : ObjectSyncResult
    data class Conflicts(val conflicts: List<ObjectStorageConflict>) : ObjectSyncResult
    data class Failed(val failure: ObjectSyncFailure) : ObjectSyncResult
}

class ObjectStorageSyncManager(
    context: Context,
    private val repository: ObjectStorageSyncRepository,
    private val store: S3ObjectStore = AwsS3ObjectStore()
) {
    private val settingsStore = ObjectStorageSettingsStore(context)
    private val codec = ObjectStorageRevisionCodec()
    private val mutex = Mutex()
    private var lastFailure: ObjectSyncFailure? = null

    fun configuredSettings(): Pair<ObjectStorageSettings, String>? = settingsStore.loadSettings()

    suspend fun status(): ObjectSyncStatus {
        val settings = try { settingsStore.loadSettings()?.first } catch (exception: Exception) {
            return ObjectSyncStatus(ObjectSyncState.ERROR, null, failure = safeFailure(exception))
        } ?: return ObjectSyncStatus(ObjectSyncState.UNCONFIGURED, null)
        val conflicts = repository.conflicts(settings.storageId)
        val state = when {
            mutex.isLocked -> ObjectSyncState.SYNCING
            lastFailure != null -> ObjectSyncState.ERROR
            conflicts.isNotEmpty() -> ObjectSyncState.CONFLICTS
            else -> ObjectSyncState.READY
        }
        return ObjectSyncStatus(state, settingsStore.lastSuccessfulSync(settings.storageId), conflicts, lastFailure)
    }

    fun configure(settings: ObjectStorageSettings, accessKeyId: String, secret: String, sessionToken: String) {
        settingsStore.save(settings, accessKeyId, secret, sessionToken)
        lastFailure = null
    }

    fun clearConfiguration() {
        settingsStore.clear()
        lastFailure = null
    }

    suspend fun testConnection(): ObjectSyncResult = execute {
        val credentials = settingsStore.load() ?: return@execute missingConfiguration()
        store.testConnection(credentials)
        ObjectSyncResult.Synced(0)
    }

    suspend fun syncNow(): ObjectSyncResult = execute {
        val credentials = settingsStore.load() ?: return@execute missingConfiguration()
        synchronize(credentials)
    }

    suspend fun resolveConflict(recordId: String, revisionId: String): ObjectSyncResult = execute {
        val credentials = settingsStore.load() ?: return@execute missingConfiguration()
        val graph = downloadGraph(credentials)
        val heads = graph.headsByRecord[recordId].orEmpty()
        val selected = heads.singleOrNull { it.revisionId == revisionId }
            ?: return@execute synchronize(credentials)
        val saved = repository.conflicts(credentials.settings.storageId).firstOrNull { it.recordId == recordId }
            ?: return@execute synchronize(credentials)
        if (saved.revisions.map { it.revisionId }.toSet() != heads.map { it.revisionId }.toSet()) {
            return@execute synchronize(credentials)
        }
        val merged = codec.create(selected.entry, heads.map { it.revisionId })
        store.putObject(credentials, codec.key(credentials.settings, merged), codec.encode(merged))
        synchronize(credentials)
    }

    private suspend fun synchronize(credentials: ObjectStorageCredentials): ObjectSyncResult {
        val settings = credentials.settings
        val storageId = settings.storageId
        val remote = downloadGraph(credentials)
        val local = repository.localEntries()
        val accepted = repository.acceptedHeads(storageId)
        val uploads = mutableListOf<ObjectStorageRevision>()

        for (entry in local) {
            val baseId = accepted[entry.id]
            val base = baseId?.let { remote.byId[it] ?: return invalidData() }
            val matchingHead = remote.headsByRecord[entry.id].orEmpty().firstOrNull { it.entry == entry }
            val candidate = when {
                base != null && base.entry == entry -> null
                base == null && matchingHead != null -> null
                else -> codec.create(entry, listOfNotNull(baseId))
            }
            if (candidate != null && candidate.revisionId !in remote.byId) {
                uploads += candidate
            }
        }

        // Uploads are immutable. Re-read the full remote graph before deciding which
        // heads to accept; another device may have uploaded during this sync.
        uploads.distinctBy { it.revisionId }.forEach { revision ->
            store.putObject(credentials, codec.key(settings, revision), codec.encode(revision))
        }
        var graph = downloadGraph(credentials)
        require(uploads.all { it.revisionId in graph.byId }) { "New revision is not visible in storage yet" }
        var stable = false
        for (attempt in 0 until 3) {
            val joins = graph.headsByRecord.values.filter { heads ->
                heads.size > 1 && heads.map { it.entry }.distinct().size == 1
            }.map { heads -> codec.create(heads.first().entry, heads.map { it.revisionId }) }
            joins.forEach { revision ->
                store.putObject(credentials, codec.key(settings, revision), codec.encode(revision))
            }
            val refreshed = downloadGraph(credentials)
            require(joins.all { it.revisionId in refreshed.byId }) { "Merge revision is not visible in storage yet" }
            stable = joins.isEmpty() && refreshed.byId.keys == graph.byId.keys
            graph = refreshed
            if (stable) break
        }
        require(stable) { "Remote revisions changed during sync; retry manually" }
        val conflicts = graph.headsByRecord.mapNotNull { (recordId, heads) ->
            if (heads.size > 1) ObjectStorageConflict(recordId, heads) else null
        }
        if (conflicts.isNotEmpty()) {
            repository.saveConflicts(storageId, conflicts)
            return ObjectSyncResult.Conflicts(conflicts)
        }
        val heads = graph.headsByRecord.mapValues { (_, variants) -> variants.single() }
        val snapshot = heads.values.map { it.entry }
        repository.replaceResolvedSnapshot(storageId, snapshot, heads.mapValues { it.value.revisionId })
        settingsStore.recordSuccessfulSync(storageId, Instant.now())
        return ObjectSyncResult.Synced(snapshot.size)
    }

    private suspend fun downloadGraph(credentials: ObjectStorageCredentials): ObjectRevisionGraph {
        val keys = mutableSetOf<String>()
        val visitedTokens = mutableSetOf<String>()
        var token: String? = null
        do {
            val page = store.listRevisions(credentials, token)
            for (key in page.keys) require(keys.add(key)) { "Duplicate remote object key" }
            token = page.nextToken
            require(token == null || visitedTokens.add(token)) { "Repeated list continuation token" }
        } while (token != null)
        val revisions = keys.sorted().map { key ->
            val revision = codec.decode(store.getObject(credentials, key))
            codec.validateKey(credentials.settings, key, revision)
            revision
        }
        return ObjectRevisionGraph(revisions)
    }

    private suspend fun execute(action: suspend () -> ObjectSyncResult): ObjectSyncResult = mutex.withLock {
        try {
            action().also { result ->
                lastFailure = if (result is ObjectSyncResult.Failed) result.failure else null
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ObjectSyncResult.Failed(safeFailure(exception)).also { lastFailure = it.failure }
        }
    }

    private fun missingConfiguration() = ObjectSyncResult.Failed(
        ObjectSyncFailure(ObjectSyncFailureKind.NOT_CONFIGURED, "Object storage is not configured")
    )

    private fun invalidData() = ObjectSyncResult.Failed(
        ObjectSyncFailure(ObjectSyncFailureKind.INVALID_DATA, "Remote revision history changed or is invalid; sync again")
    )

    private fun safeFailure(exception: Exception): ObjectSyncFailure {
        val chain = generateSequence(exception as Throwable?) { it.cause }.toList()
        val s3 = chain.filterIsInstance<S3Exception>().firstOrNull()
        return when {
            s3?.statusCode() == 401 -> ObjectSyncFailure(ObjectSyncFailureKind.AUTHENTICATION, "Storage authentication failed")
            s3?.statusCode() == 403 -> ObjectSyncFailure(ObjectSyncFailureKind.PERMISSION, "Storage permission denied; check prefix access")
            chain.any { it is SSLException || it is UnknownHostException || it is SdkClientException } ->
                ObjectSyncFailure(ObjectSyncFailureKind.NETWORK, "Could not reach the storage endpoint")
            chain.any { it is IllegalArgumentException || it is IllegalStateException } ->
                ObjectSyncFailure(ObjectSyncFailureKind.INVALID_DATA, "Storage configuration or remote revisions are invalid")
            else -> ObjectSyncFailure(ObjectSyncFailureKind.SERVICE, "Object storage request failed; retry manually")
        }
    }
}

package com.example.personalbookkeeping.data.sync

import android.content.Context
import com.example.personalbookkeeping.data.repository.LedgerRepository
import com.example.personalbookkeeping.domain.model.LedgerEntry
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

enum class GitSyncState {
    UNCONFIGURED,
    READY,
    SYNCING,
    CONFLICTS,
    RESOLUTION_READY,
    ERROR
}

enum class GitConflictChoice {
    LOCAL,
    REMOTE
}

enum class GitSyncFailureKind {
    NOT_CONFIGURED,
    AUTHENTICATION,
    NETWORK,
    PUSH_REJECTED,
    INVALID_REMOTE_DATA,
    REPOSITORY,
    SYNC_FAILED
}

data class GitSyncFailure(
    val kind: GitSyncFailureKind,
    val message: String
)

class GitSyncConflict(
    val recordId: String,
    val localVersion: LedgerEntry?,
    val remoteVersion: LedgerEntry?
) {
    override fun toString(): String =
        "GitSyncConflict(recordId=$recordId, localVersion=[REDACTED], remoteVersion=[REDACTED])"
}

data class GitSyncStatus(
    val state: GitSyncState,
    val isConfigured: Boolean,
    val localRepositoryExists: Boolean,
    val lastSuccessfulSyncAt: Instant?,
    val conflicts: List<GitSyncConflict> = emptyList(),
    val failure: GitSyncFailure? = null
)

sealed interface GitSyncResult {
    data class Synced(val records: Int, val completedAt: Instant) : GitSyncResult
    data class Conflicts(val records: List<GitSyncConflict>) : GitSyncResult
    data class Failed(val failure: GitSyncFailure) : GitSyncResult
}

/**
 * Owns the private JGit working copy and runs synchronization only when a caller invokes [syncNow].
 * This class does not create workers, schedules, or provider-specific API requests.
 */
class GitSyncManager(
    context: Context,
    private val ledgerRepository: LedgerRepository,
    private val settingsStore: GitSyncSettingsStore = GitSyncSettingsStore(context)
) {
    private val appContext = context.applicationContext
    private val operationMutex = Mutex()
    private val syncing = AtomicBoolean(false)
    private val codec = LedgerSyncFileCodec()
    @Volatile
    private var lastFailure: GitSyncFailure? = null

    suspend fun configure(settings: GitSyncSettings, token: String) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            settingsStore.save(settings, token)
            lastFailure = null
        }
    }

    suspend fun clearConfiguration() = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            settingsStore.clear()
            lastFailure = null
        }
    }

    suspend fun configuredSettings(): GitSyncSettings? = withContext(Dispatchers.IO) {
        settingsStore.loadSettings()
    }

    suspend fun status(): GitSyncStatus = withContext(Dispatchers.IO) {
        val configured = try {
            settingsStore.load() != null
        } catch (exception: Exception) {
            val failure = failureFrom(exception)
            lastFailure = failure
            return@withContext GitSyncStatus(
                state = GitSyncState.ERROR,
                isConfigured = true,
                localRepositoryExists = false,
                lastSuccessfulSyncAt = settingsStore.lastSuccessfulSync(),
                failure = failure
            )
        }
        val workDirectory = configuredWorkDirectory()
        if (syncing.get()) {
            return@withContext GitSyncStatus(
                GitSyncState.SYNCING,
                configured,
                workDirectory.resolve(".git").exists(),
                settingsStore.lastSuccessfulSync()
            )
        }
        if (!configured) {
            return@withContext GitSyncStatus(
                GitSyncState.UNCONFIGURED,
                isConfigured = false,
                localRepositoryExists = workDirectory.resolve(".git").exists(),
                lastSuccessfulSyncAt = settingsStore.lastSuccessfulSync()
            )
        }
        try {
            val repositoryState = openExisting(workDirectory)?.useRepository { git ->
                git.repository.repositoryState to readConflicts(git)
            }
            val conflicts = repositoryState?.second.orEmpty()
            if (conflicts.isNotEmpty()) {
                GitSyncStatus(
                    GitSyncState.CONFLICTS,
                    true,
                    true,
                    settingsStore.lastSuccessfulSync(),
                    conflicts
                )
            } else if (repositoryState?.first == RepositoryState.MERGING_RESOLVED) {
                GitSyncStatus(
                    GitSyncState.RESOLUTION_READY,
                    true,
                    true,
                    settingsStore.lastSuccessfulSync()
                )
            } else {
                GitSyncStatus(
                    GitSyncState.READY,
                    true,
                    workDirectory.resolve(".git").exists(),
                    settingsStore.lastSuccessfulSync(),
                    failure = lastFailure
                )
            }
        } catch (exception: Exception) {
            val failure = failureFrom(exception)
            lastFailure = failure
            GitSyncStatus(
                GitSyncState.ERROR,
                true,
                workDirectory.resolve(".git").exists(),
                settingsStore.lastSuccessfulSync(),
                failure = failure
            )
        }
    }

    suspend fun syncNow(): GitSyncResult {
        if (!syncing.compareAndSet(false, true)) {
            return GitSyncResult.Failed(
                GitSyncFailure(GitSyncFailureKind.SYNC_FAILED, "A Git sync is already in progress")
            )
        }
        return try {
            withContext(Dispatchers.IO) {
                operationMutex.withLock {
                    try {
                        val credentials = settingsStore.load()
                            ?: return@withLock failed(GitSyncFailureKind.NOT_CONFIGURED, "Git sync is not configured")
                        synchronize(credentials)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        failed(failureFrom(exception))
                    }
                }
            }
        } finally {
            syncing.set(false)
        }
    }

    suspend fun resolveConflict(recordId: String, choice: GitConflictChoice): GitSyncResult {
        if (!syncing.compareAndSet(false, true)) {
            return GitSyncResult.Failed(
                GitSyncFailure(GitSyncFailureKind.SYNC_FAILED, "A Git sync is already in progress")
            )
        }
        return try {
            withContext(Dispatchers.IO) {
                operationMutex.withLock {
                    try {
                        val credentials = settingsStore.load()
                            ?: return@withLock failed(GitSyncFailureKind.NOT_CONFIGURED, "Git sync is not configured")
                        resolveConflictLocked(credentials, recordId, choice)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        failed(failureFrom(exception))
                    }
                }
            }
        } finally {
            syncing.set(false)
        }
    }

    private suspend fun synchronize(credentials: GitSyncCredentials): GitSyncResult {
        val settings = credentials.settings
        val workDirectory = workDirectory(settings)
        val git = openOrCreate(workDirectory, settings.branch)
        git.use { repositoryGit ->
            val existingConflicts = readConflicts(repositoryGit)
            if (existingConflicts.isNotEmpty()) return GitSyncResult.Conflicts(existingConflicts)
            if (repositoryGit.repository.repositoryState == RepositoryState.MERGING_RESOLVED) {
                completeResolvedMerge(repositoryGit)
            }

            val localEntries = ledgerRepository.getAllSyncEntries()
            fetch(repositoryGit, credentials)
            val remoteRef = repositoryGit.repository.exactRef(remoteTrackingRef(settings.branch))
            val localHead = repositoryGit.repository.resolve(Constants.HEAD)

            if (localHead == null && remoteRef != null && localEntries.isEmpty()) {
                checkoutRemoteBranch(repositoryGit, settings.branch, remoteRef)
                val remoteEntries = readWorkingEntries(repositoryGit.repository)
                ledgerRepository.applyRemoteEntriesAtomically(remoteEntries)
                return synced(remoteEntries.size)
            }

            writeEntries(repositoryGit.repository, localEntries)
            commitIfChanged(repositoryGit)

            val refreshedRemoteRef = repositoryGit.repository.exactRef(remoteTrackingRef(settings.branch))
            if (refreshedRemoteRef != null) {
                val conflicts = mergeRemote(repositoryGit, refreshedRemoteRef)
                if (conflicts.isNotEmpty()) return GitSyncResult.Conflicts(conflicts)
            }

            val mergedEntries = readWorkingEntries(repositoryGit.repository)
            ledgerRepository.applyRemoteEntriesAtomically(mergedEntries)
            val firstPush = push(repositoryGit, credentials)
            if (firstPush == PushOutcome.SUCCESS) return synced(mergedEntries.size)
            if (firstPush == PushOutcome.FAILURE) {
                return failed(GitSyncFailureKind.PUSH_REJECTED, "The remote rejected the Git update")
            }

            fetch(repositoryGit, credentials)
            val retryRemoteRef = repositoryGit.repository.exactRef(remoteTrackingRef(settings.branch))
            if (retryRemoteRef != null) {
                val conflicts = mergeRemote(repositoryGit, retryRemoteRef)
                if (conflicts.isNotEmpty()) return GitSyncResult.Conflicts(conflicts)
            }
            val retriedEntries = readWorkingEntries(repositoryGit.repository)
            ledgerRepository.applyRemoteEntriesAtomically(retriedEntries)
            if (push(repositoryGit, credentials) == PushOutcome.SUCCESS) return synced(retriedEntries.size)
            return failed(GitSyncFailureKind.PUSH_REJECTED, "The remote rejected the update after one retry")
        }
    }

    private suspend fun resolveConflictLocked(
        credentials: GitSyncCredentials,
        recordId: String,
        choice: GitConflictChoice
    ): GitSyncResult {
        if (!isCanonicalRecordId(recordId)) {
            return failed(GitSyncFailureKind.REPOSITORY, "The conflict record ID is invalid")
        }
        val git = openExisting(workDirectory(credentials.settings))
            ?: return failed(GitSyncFailureKind.REPOSITORY, "The local Git working copy is missing")
        git.use { repositoryGit ->
            val conflict = readConflicts(repositoryGit).firstOrNull { it.recordId == recordId }
                ?: return failed(GitSyncFailureKind.REPOSITORY, "The requested conflict is no longer pending")
            val selected = if (choice == GitConflictChoice.LOCAL) conflict.localVersion else conflict.remoteVersion
            val alternate = if (choice == GitConflictChoice.LOCAL) conflict.remoteVersion else conflict.localVersion
            val resolvedEntry = selected ?: alternate?.let { base ->
                val now = Instant.now()
                base.copy(updatedAt = maxOf(base.updatedAt, now), deletedAt = now)
            } ?: return failed(GitSyncFailureKind.INVALID_REMOTE_DATA, "Neither conflict version contains a record")

            writeEntry(repositoryGit.repository, resolvedEntry)
            repositoryGit.add().addFilepattern(recordPath(recordId)).call()
            val remainingConflicts = readConflicts(repositoryGit)
            if (remainingConflicts.isNotEmpty()) return GitSyncResult.Conflicts(remainingConflicts)

            repositoryGit.commit()
                .setMessage("Resolve ledger conflict $recordId")
                .setAuthor(COMMITTER_NAME, COMMITTER_EMAIL)
                .setCommitter(COMMITTER_NAME, COMMITTER_EMAIL)
                .call()

            val mergedEntries = readWorkingEntries(repositoryGit.repository)
            ledgerRepository.applyRemoteEntriesAtomically(mergedEntries)
            val firstPush = push(repositoryGit, credentials)
            if (firstPush == PushOutcome.SUCCESS) return synced(mergedEntries.size)
            if (firstPush == PushOutcome.FAILURE) {
                return failed(GitSyncFailureKind.PUSH_REJECTED, "The remote rejected the conflict resolution")
            }

            fetch(repositoryGit, credentials)
            val remoteRef = repositoryGit.repository.exactRef(remoteTrackingRef(credentials.settings.branch))
            if (remoteRef != null) {
                val newConflicts = mergeRemote(repositoryGit, remoteRef)
                if (newConflicts.isNotEmpty()) return GitSyncResult.Conflicts(newConflicts)
            }
            val retriedEntries = readWorkingEntries(repositoryGit.repository)
            ledgerRepository.applyRemoteEntriesAtomically(retriedEntries)
            if (push(repositoryGit, credentials) == PushOutcome.SUCCESS) return synced(retriedEntries.size)
            return failed(GitSyncFailureKind.PUSH_REJECTED, "The remote rejected the resolution after one retry")
        }
    }

    private fun fetch(git: Git, credentials: GitSyncCredentials) {
        val settings = credentials.settings
        val branchRef = "refs/heads/${settings.branch}"
        val trackingRef = remoteTrackingRef(settings.branch)
        val remoteHasBranch = Git.lsRemoteRepository()
            .setRemote(settings.remoteUrl)
            .setCredentialsProvider(credentialsProvider(credentials))
            .setHeads(true)
            .call()
            .any { ref -> ref.name == branchRef }

        if (!remoteHasBranch) {
            if (git.repository.exactRef(trackingRef) != null) {
                val deleteResult = git.repository.updateRef(trackingRef).delete()
                if (deleteResult != RefUpdate.Result.FORCED && deleteResult != RefUpdate.Result.NO_CHANGE) {
                    throw IOException("Could not remove the stale Git tracking branch")
                }
            }
            return
        }

        git.fetch()
            .setRemote(settings.remoteUrl)
            .setCredentialsProvider(credentialsProvider(credentials))
            .setRefSpecs(
                RefSpec(
                    "+$branchRef:$trackingRef"
                )
            )
            .call()
    }

    private fun mergeRemote(git: Git, remoteRef: Ref): List<GitSyncConflict> {
        val head = git.repository.resolve(Constants.HEAD)
        if (head != null && head == remoteRef.objectId) return emptyList()
        git.merge()
            .include(remoteRef)
            .call()
        return readConflicts(git)
    }

    private fun push(git: Git, credentials: GitSyncCredentials): PushOutcome {
        if (git.repository.resolve(Constants.HEAD) == null) return PushOutcome.SUCCESS
        val results = git.push()
            .setRemote(credentials.settings.remoteUrl)
            .setCredentialsProvider(credentialsProvider(credentials))
            .setRefSpecs(
                RefSpec(
                    "refs/heads/${credentials.settings.branch}:refs/heads/${credentials.settings.branch}"
                )
            )
            .call()
        val updates = results.flatMap { result -> result.remoteUpdates }
        return when {
            updates.isEmpty() -> PushOutcome.SUCCESS
            updates.all { it.status == RemoteRefUpdate.Status.OK || it.status == RemoteRefUpdate.Status.UP_TO_DATE } ->
                PushOutcome.SUCCESS
            updates.any { it.status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD } -> PushOutcome.RETRY
            else -> PushOutcome.FAILURE
        }
    }

    private fun writeEntries(repository: Repository, entries: List<LedgerEntry>) {
        entries.forEach { entry -> writeEntry(repository, entry) }
        repository.directory.resolve(RECORDS_DIRECTORY).mkdirs()
    }

    private fun writeEntry(repository: Repository, entry: LedgerEntry) {
        val bytes = codec.encode(entry)
        val relativePath = recordPath(entry.id)
        val target = repository.directory.resolve(relativePath)
        val recordsDirectory = repository.directory.resolve(RECORDS_DIRECTORY)
        if (!recordsDirectory.exists() && !recordsDirectory.mkdirs()) {
            throw IOException("Could not create the ledger sync directory")
        }
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeBytes(bytes)
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IOException("Could not replace a ledger sync record")
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Could not finish writing a ledger sync record")
        }
    }

    private fun commitIfChanged(git: Git) {
        val repository = git.repository
        repository.directory.resolve(RECORDS_DIRECTORY).mkdirs()
        git.add().addFilepattern(RECORDS_DIRECTORY).call()
        if (git.status().call().isClean) return
        git.commit()
            .setMessage("Sync personal ledger records")
            .setAuthor(COMMITTER_NAME, COMMITTER_EMAIL)
            .setCommitter(COMMITTER_NAME, COMMITTER_EMAIL)
            .call()
    }

    private suspend fun completeResolvedMerge(git: Git) {
        git.commit()
            .setMessage("Resolve personal ledger merge")
            .setAuthor(COMMITTER_NAME, COMMITTER_EMAIL)
            .setCommitter(COMMITTER_NAME, COMMITTER_EMAIL)
            .call()
        ledgerRepository.applyRemoteEntriesAtomically(readWorkingEntries(git.repository))
    }

    private fun readWorkingEntries(repository: Repository): List<LedgerEntry> {
        val recordsDirectory = repository.directory.resolve(RECORDS_DIRECTORY)
        if (!recordsDirectory.exists()) return emptyList()
        val files = recordsDirectory.listFiles()
            ?: throw IOException("Could not read the local ledger sync directory")
        return files.asSequence()
            .filter { it.isFile && it.extension == "json" }
            .sortedBy(File::getName)
            .map { file -> codec.decode(file.nameWithoutExtension, file.readBytes()) }
            .toList()
    }

    private fun readConflicts(git: Git): List<GitSyncConflict> {
        if (git.repository.repositoryState != RepositoryState.MERGING) return emptyList()
        val entriesByPath = linkedMapOf<String, MutableList<DirCacheEntry>>()
        val directoryCache = git.repository.readDirCache()
        for (index in 0 until directoryCache.entryCount) {
            val entry = directoryCache.getEntry(index)
            if (entry.stage > 0) entriesByPath.getOrPut(entry.pathString) { mutableListOf() }.add(entry)
        }
        return entriesByPath.map { (path, stages) ->
            val recordId = recordIdFromPath(path)
                ?: throw InvalidSyncRecordException("An unsupported path conflicts in the Git working copy")
            val local = stages.firstOrNull { it.stage == STAGE_OURS }
                ?.let { decodeStage(git.repository, recordId, it) }
            val remote = stages.firstOrNull { it.stage == STAGE_THEIRS }
                ?.let { decodeStage(git.repository, recordId, it) }
            GitSyncConflict(recordId, local, remote)
        }.sortedBy(GitSyncConflict::recordId)
    }

    private fun decodeStage(repository: Repository, recordId: String, entry: DirCacheEntry): LedgerEntry {
        val reader = repository.newObjectReader()
        return reader.use {
            val bytes = it.open(entry.objectId, Constants.OBJ_BLOB).bytes
            codec.decode(recordId, bytes)
        }
    }

    private fun checkoutRemoteBranch(git: Git, branch: String, remoteRef: Ref) {
        git.checkout()
            .setCreateBranch(true)
            .setName(branch)
            .setStartPoint(remoteRef.name)
            .call()
    }

    private fun openOrCreate(directory: File, branch: String): Git {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create the Git working directory")
        return if (directory.resolve(".git").exists()) {
            Git.open(directory)
        } else {
            Git.init().setDirectory(directory).setInitialBranch(branch).call()
        }
    }

    private fun openExisting(directory: File): Git? = try {
        if (directory.resolve(".git").exists()) Git.open(directory) else null
    } catch (exception: RepositoryNotFoundException) {
        null
    }

    private fun <T> Git.useRepository(block: (Git) -> T): T = use(block)

    private fun workDirectory(settings: GitSyncSettings): File {
        val identity = "${settings.remoteUrl}\n${settings.branch}".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(identity)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return File(appContext.noBackupFilesDir, "$WORK_DIRECTORY/$digest")
    }

    private fun configuredWorkDirectory(): File {
        val settings = runCatching { settingsStore.load()?.settings }.getOrNull() ?: return File(appContext.noBackupFilesDir, WORK_DIRECTORY)
        return workDirectory(settings)
    }

    private fun credentialsProvider(credentials: GitSyncCredentials): CredentialsProvider =
        UsernamePasswordCredentialsProvider(credentials.settings.username, credentials.token)

    private fun recordIdFromPath(path: String): String? {
        val prefix = "$RECORDS_DIRECTORY/"
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null
        val id = path.removePrefix(prefix).removeSuffix(".json")
        return id.takeIf(::isCanonicalRecordId)
    }

    private fun recordPath(id: String): String = "$RECORDS_DIRECTORY/$id.json"

    private fun isCanonicalRecordId(id: String): Boolean = runCatching {
        java.util.UUID.fromString(id).toString() == id
    }.getOrDefault(false)

    private fun remoteTrackingRef(branch: String): String = "refs/remotes/personal-ledger/$branch"

    private fun synced(recordCount: Int): GitSyncResult.Synced {
        val completedAt = Instant.now()
        settingsStore.recordSuccessfulSync(completedAt)
        lastFailure = null
        return GitSyncResult.Synced(recordCount, completedAt)
    }

    private fun failed(kind: GitSyncFailureKind, message: String): GitSyncResult.Failed {
        return failed(GitSyncFailure(kind, message))
    }

    private fun failed(failure: GitSyncFailure): GitSyncResult.Failed {
        lastFailure = failure
        return GitSyncResult.Failed(failure)
    }

    private fun failureFrom(exception: Throwable): GitSyncFailure {
        val chain = generateSequence(exception) { it.cause }.toList()
        if (chain.any { it is InvalidSyncRecordException }) {
            return GitSyncFailure(GitSyncFailureKind.INVALID_REMOTE_DATA, "The Git repository contains an invalid ledger record")
        }
        val descriptions = chain.mapNotNull { it.message }.joinToString(" ").lowercase()
        if (listOf("authentication", "not authorized", "unauthorized", "401", "403", "access denied")
                .any(descriptions::contains)
        ) {
            return GitSyncFailure(GitSyncFailureKind.AUTHENTICATION, "Git authentication failed; check the saved username and token")
        }
        if (chain.any { it is UnknownHostException || it is SocketException || it is SocketTimeoutException || it is SSLException }) {
            return GitSyncFailure(GitSyncFailureKind.NETWORK, "Could not reach the Git remote")
        }
        if (chain.any { it is RepositoryNotFoundException }) {
            return GitSyncFailure(GitSyncFailureKind.REPOSITORY, "The local Git repository is unavailable")
        }
        return GitSyncFailure(GitSyncFailureKind.SYNC_FAILED, "Git sync failed; check the remote and try again")
    }

    private enum class PushOutcome {
        SUCCESS,
        RETRY,
        FAILURE
    }

    private companion object {
        const val WORK_DIRECTORY = "personal-ledger-git"
        const val RECORDS_DIRECTORY = "ledger/records"
        const val COMMITTER_NAME = "Personal Ledger"
        const val COMMITTER_EMAIL = "ledger@local.invalid"
        const val STAGE_OURS = 2
        const val STAGE_THEIRS = 3
    }
}

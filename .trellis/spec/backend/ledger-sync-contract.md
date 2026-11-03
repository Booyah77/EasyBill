# Personal Ledger Persistence and Git Sync Contract

## 1. Scope / Trigger

This contract covers the Android ledger data path from Compose screens through ViewModel and repository to Room, file import/export, and the optional Git remote. It records the v1 boundaries that must remain consistent when the schema or sync flow changes.

## 2. Signatures

Repository contract:

    suspend fun importEntries(entries: List<LedgerEntry>): ImportCommitResult
    suspend fun getAllSyncEntries(): List<LedgerEntry>
    suspend fun applyRemoteEntriesAtomically(entries: List<LedgerEntry>)

File transfer contract:

    fun previewCsv(content: String): ImportPreview
    fun previewJson(content: String): ImportPreview
    suspend fun commit(preview: ImportPreview): ImportAttempt

Git sync contract:

    suspend fun syncNow(): GitSyncResult
    suspend fun resolveConflict(recordId: String, choice: GitConflictChoice): GitSyncResult

The screens call the file transfer and Git manager through UI callbacks. Screens do not write Room rows or Git files directly.

## 3. Contracts

- Store money as positive integer fen and currency as CNY. Dates use ISO local dates; created, updated, and deleted times use ISO-8601 instants.
- Room is the source of truth. A delete sets updatedAt and deletedAt; sync must retain the tombstone. Normal CSV/JSON exports contain active entries only.
- CSV v1 uses the exact ordered header: id,type,amount,currency,date,category,note,createdAt,updatedAt. UTF-8 BOM is accepted and emitted.
- JSON v1 uses schemaVersion, currency, exportedAt, and records at the root. Each record contains id,type,amount,date,category,note,createdAt,updatedAt. Amount is a decimal string with exactly two fractional digits.
- Import parses and validates the complete file before commit. Any validation issue disables commit. Room inserts accepted new IDs in one transaction; exact repeats are skipped and counted. A reused ID with different values fails the whole transaction.
- Git settings require an HTTPS remote, non-empty username and token, and a valid branch. Keep the token encrypted with an Android Keystore key. Do not place credentials in the remote URL, sync files, exported records, or logs.
- Git is manual and optional. Unconfigured mode must remain fully local and must not access the network.
- Store each record at ledger/records/{canonical-lowercase-UUID}.json with sync schema version 1. Keep deleted records as tombstones.
- Before fetching a configured branch, advertise remote heads. If the selected branch is absent, skip fetch and remove a stale tracking ref; this allows first sync to an empty remote. Then commit local records, merge the fetched history, apply the merged snapshot transactionally, and push. Retry one non-fast-forward push after fetching and merging once.
- If a record path conflicts, expose both Git index variants. Keep the Room snapshot unchanged until all conflicts are resolved. A choice creates a merge resolution commit; a missing side chosen as the local/remote version becomes a tombstone.

## 4. Validation and Error Matrix

| Condition | Required behavior |
| --- | --- |
| Unknown or missing CSV/JSON field, invalid amount/date/type/UUID, unsupported version or currency | Add preview issues; do not mutate Room |
| Exact duplicate ID and values in file or Room | Skip it and increment duplicate count |
| Same ID with different values in file or Room | Report a conflict; transaction writes no rows |
| Missing Git settings | Return NOT_CONFIGURED; keep local ledger usable |
| Invalid remote record JSON or mismatched ID/path | Return INVALID_REMOTE_DATA; do not apply a partial remote snapshot |
| Authentication, network, or push rejection | Return an actionable failure status; preserve local entries |
| Same record changed on both devices | Preserve both Git variants, require an explicit choice, then finish the merge |
| Cancellation | Propagate cancellation and release the sync lock |

## 5. Good / Base / Bad Cases

- Good: export CSV or JSON, import it into a clean installation, and preserve every active record ID and field.
- Good: sync local entries to an empty HTTPS repository; the selected branch is created by the push.
- Base: sync with no records and an empty remote; complete without requiring a pre-created branch.
- Base: import the same backup twice; the second import reports duplicates and creates no additional rows.
- Bad: write rows while parsing, then discover an invalid final row; partial data must never be visible.
- Bad: accept unknown JSON fields silently or coerce amounts through floating-point arithmetic.
- Bad: fetch an absent branch as if it must exist, overwrite one conflict side silently, or store a token as plain text.
- Bad: update Room while a merge conflict is unresolved.

## 6. Tests Required

When adding tests later, assert these points:

- A malformed late row leaves the Room snapshot unchanged.
- Duplicate imports report the correct count and do not duplicate IDs.
- CSV/JSON export-import preserves IDs, type, amount, date, category, note, and timestamps.
- First sync handles an empty remote and a remote with an initial commit.
- Authentication and push failures preserve the local ledger.
- A same-record conflict exposes both variants; resolving it commits both merge parents and applies only the chosen record value.
- Cancellation always releases the sync lock.

These tests are not included in the current change; this section defines the contract for future coverage.

## 7. Wrong vs Correct

Wrong: call fetch with an explicit branch refspec before checking whether the branch exists. A new empty remote can fail because there is no branch to fetch.

Correct: list remote heads first, fetch only when the configured branch is advertised, and allow a later push to create the first branch.

Wrong: insert each parsed row as soon as it is read.

Correct: preview the entire file, reject any validation issue, then commit accepted rows in one Room transaction.

Wrong: choose the local or remote Git variant implicitly during conflict handling.

Correct: show both variants, wait for the user's choice, preserve the merge parents, and apply the resolved snapshot only after conflict resolution.

## Scenario: Optional S3-Compatible Object Storage Sync

### 1. Scope / Trigger

- Trigger: the personal ledger now supports an optional, manually triggered S3-compatible object store in addition to Git. This section supersedes earlier OSS-excluded wording.
- Room remains the local source of truth. Unconfigured mode makes no network requests.
- The client uses AWS SDK for Java 2.x S3Client and UrlConnectionHttpClient. Keep synchronous client calls inside Dispatchers.IO and close each client.

### 2. Signatures

    data class ObjectStorageSettings(
        endpointUrl: String,
        region: String,
        bucket: String,
        prefix: String = "personal-ledger",
        addressingStyle: S3AddressingStyle
    )

    interface S3ObjectStore {
        suspend fun testConnection(credentials: ObjectStorageCredentials)
        suspend fun listRevisions(credentials: ObjectStorageCredentials, continuationToken: String?): S3RevisionPage
        suspend fun getObject(credentials: ObjectStorageCredentials, key: String): ByteArray
        suspend fun putObject(credentials: ObjectStorageCredentials, key: String, bytes: ByteArray)
    }

    interface ObjectStorageSyncRepository {
        suspend fun localEntries(): List<LedgerEntry>
        suspend fun acceptedHeads(storageId: String): Map<String, String>
        suspend fun conflicts(storageId: String): List<ObjectStorageConflict>
        suspend fun saveConflicts(storageId: String, conflicts: List<ObjectStorageConflict>)
        suspend fun replaceResolvedSnapshot(
            storageId: String,
            entries: List<LedgerEntry>,
            acceptedHeads: Map<String, String>
        )
    }

    class ObjectStorageSyncManager {
        suspend fun status(): ObjectSyncStatus
        suspend fun testConnection(): ObjectSyncResult
        suspend fun syncNow(): ObjectSyncResult
        suspend fun resolveConflict(recordId: String, revisionId: String): ObjectSyncResult
        fun configure(settings: ObjectStorageSettings, accessKeyId: String, secret: String, sessionToken: String)
        fun clearConfiguration()
    }

### 3. Contracts

- Config fields: HTTPS endpoint URL, region, bucket, normalized relative prefix, path or virtual-host addressing, access key ID, secret, and optional temporary session token. Reject endpoint user info, path, query, and fragment; reject parent or empty prefix segments.
- Endpoint examples: Alibaba OSS uses a regional endpoint with virtual-host addressing. MinIO installations commonly use path addressing.
- Connection test: call ListObjectsV2 for at most one key under the configured record prefix. It must not upload or delete a probe object. Write permission is checked by manual sync.
- Sync object key:

      <prefix>/v1/records/{recordId}/revisions/{revisionId}.json

- Revision payload: schemaVersion, recordId, revisionId, sorted parentRevisionIds, and the full LedgerEntry including deletedAt.
- Revision ID: lowercase SHA-256 of stable serialized payload excluding revisionId; include sorted parents. Decode must recompute the digest and match record ID and object key.
- Use paginated ListObjectsV2, GetObject, and PutObject only. Do not depend on conditional writes or object deletion. Keep all revisions immutable.
- Local sync metadata is keyed by storageId = SHA-256 of normalized endpoint, region, bucket, and prefix. Changing target never reuses another target's accepted heads or last-success time.
- Room database version 2 adds object sync head and conflict tables through an explicit additive 1-to-2 migration. No destructive fallback is permitted.
- Validate all remote object pages, schemas, digests, record IDs, parent existence, same-record ancestry, and graph cycles before uploading revisions or changing Room.
- A first-time local record adopts an identical remote head; otherwise publish a root revision so a different same-ID record becomes a conflict.
- After publishing local revisions, re-list and validate the full graph. Re-read after automatic joins. Apply only when the remote graph is stable; if it keeps changing, fail safely for a later manual retry.
- When heads for one record differ in value, persist every version and require an explicit selection. A resolution revision has all competing head IDs as parents. No partial Room snapshot update is allowed while conflicts remain.
- Commit resolved ledger rows and accepted revision heads in one Room transaction. Before replacement, ensure all local record IDs are present in the merged snapshot.
- Encrypt secret and session token with an OSS-specific Android Keystore alias. Preserve the Git token alias. Do not log credentials or ledger contents. Use HTTPS, a private bucket, and least-privilege credentials scoped to listing and reading/writing the configured prefix.
- Do not add automatic or background sync. Git and OSS settings, statuses, and manual actions remain independent.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| OSS settings absent | Report local-only mode; make no network call |
| Invalid endpoint, region, bucket, or prefix | Reject configuration before network or ledger mutation |
| 401 / 403 from object store | Report authentication / permission failure; preserve Room |
| DNS, TLS, socket, or timeout failure | Report network failure; preserve Room |
| Invalid JSON, schema, digest, object key, missing parent, wrong record parent, or cycle | Abort before upload or Room mutation; report invalid remote history |
| Repeated list continuation token | Abort as invalid remote listing |
| Competing different heads for one record | Persist all variants, show conflict choices, do not apply a partial snapshot |
| Remote changes continuously during refresh | Abort safely and ask for another manual sync |
| Local Room replacement succeeds | Update ledger rows and accepted heads atomically; then record success time for storageId |
| Cancellation | Propagate cancellation; release mutex; retain local ledger |

### 5. Good / Base / Bad Cases

- Good: two devices edit different records; a manual sync retains both revisions and both entries.
- Good: two devices edit the same record from a shared parent; sync shows both versions, and a resolution revision references both.
- Good: a local entry exactly matches a remote head during first sync; adopt that head instead of creating a duplicate conflict.
- Base: unconfigured OSS and no network; local add/edit/delete and CSV/JSON operations continue.
- Base: first sync with an empty bucket and local rows uploads root revisions.
- Bad: upload a mutable whole-ledger snapshot that can be overwritten by another device.
- Bad: apply the graph built before local uploads without refreshing the remote list.
- Bad: silently choose one conflict side or partially replace Room before every conflict is resolved.
- Bad: reuse accepted heads or last-sync time after the endpoint, bucket, or prefix changes.
- Bad: persist an in-progress UI flag across activity recreation when its coroutine is not persisted.

### 6. Tests Required

No automated tests were added or run for the initial implementation. Future tests should assert:

- A malformed late remote revision leaves ledger rows and accepted heads unchanged.
- A revision hash mismatch, missing parent, cross-record parent, duplicate key, or cycle fails before applying Room.
- A concurrent upload appearing after local PutObject is found by the second full listing.
- Identical concurrent heads are joined; differing heads preserve all variants until an explicit resolution revision is published.
- Changing storageId never reuses previous target state or last-success time.
- A failed request preserves local entries and releases the sync mutex.
- Unconfigured settings do not invoke the S3 client.

### 7. Wrong vs Correct

Wrong: use one mutable remote JSON file and overwrite it after each device sync; a concurrent upload can erase records.

Correct: store immutable per-record revision objects with parent IDs and tombstones; list and validate after upload, then detect all graph heads before updating Room.

Wrong: store one global last-sync timestamp and accepted-head map for every S3 endpoint.

Correct: namespace heads, conflicts, and success time by storageId derived from normalized endpoint, region, bucket, and prefix.

# Object Storage Sync Implementation Plan

> For agentic workers: Use the project Trellis implement/check agents. Work through the tasks in order and review the approved design before editing code. Steps use checkbox syntax for tracking.

**Goal:** Add optional, manually triggered S3-compatible object-storage sync to the Android ledger while preserving every concurrent record version.

**Architecture:** Keep Room as the local source of truth. Use AWS SDK for Java 2.x S3Client with a user-configured endpoint and immutable per-record revision objects. Persist accepted revision heads and unresolved conflicts in Room; keep each backend's settings and manual action independent.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.8.5, kotlinx.serialization, AWS SDK for Java 2.x 2.56.0 S3 client, URLConnection HTTP client, Android Keystore, coroutines.

## Global Constraints

- Keep minSdk 26, compileSdk 37, and Java 17 source/target compatibility.
- The approved OSS design in docs/superpowers/specs/2026-10-21-oss-sync-design.md supersedes the original task PRD's OSS-excluded scope.
- Use explicit S3 endpoint, region, and addressing style; run synchronous S3 operations inside Dispatchers.IO.
- OSS remains optional and manual. Do not add WorkManager, startup network calls, or background sync.
- Use immutable revision objects and tombstones; never overwrite a shared mutable ledger snapshot.
- Keep Room unchanged until every remote revision validates and every conflict is resolved.
- Persist the ledger snapshot and accepted revision heads atomically; preserve all local record IDs.
- Require HTTPS and a private bucket. Encrypt the secret key and session token with a distinct Android Keystore alias; do not log credentials or ledger contents.
- Preserve Git sync behavior and allow Git and OSS to remain independently configured.
- Do not add or run automated tests for this request. Build the debug APK and report whether compilation succeeds.

---

### Task 1: Add configurable S3 transport and credential storage

**Files:**

- Modify: gradle/libs.versions.toml
- Modify: app/build.gradle.kts
- Modify: app/src/main/java/com/example/personalbookkeeping/data/sync/AndroidKeystoreTokenCipher.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/sync/ObjectStorageSettings.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/sync/ObjectStorageSettingsStore.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/sync/S3ObjectStore.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/sync/AwsS3ObjectStore.kt

**Interfaces:**

- ObjectStorageSettings contains endpointUrl, region, bucket, prefix, and addressingStyle.
- ObjectStorageCredentials contains ObjectStorageSettings, accessKeyId, secretAccessKey, and an optional sessionToken.
- S3ObjectStore exposes suspend testConnection(credentials), listRevisions(credentials, continuationToken), getObject(credentials, key), and putObject(credentials, key, bytes).
- listRevisions returns object keys and the next continuation token; it must not load object contents.

- [ ] Add version-catalog aliases for software.amazon.awssdk:s3 and software.amazon.awssdk:url-connection-client, both pinned to 2.56.0. Add both as app dependencies.
- [ ] Build the S3Client with endpointOverride, Region.of(region), forcePathStyle derived from addressingStyle, StaticCredentialsProvider with basic or session credentials, and UrlConnectionHttpClient.
- [ ] Configure request and response checksum handling as WHEN_REQUIRED. Verify downloaded revision SHA-256 in the revision codec.
- [ ] Require HTTPS, a host, non-empty region/bucket/credentials, and a normalized relative prefix with no parent traversal segments. Reject credentials, query strings, and fragments in the endpoint URL.
- [ ] Encrypt the secret key and optional session token using an OSS-specific key alias. Keep Git's existing key alias unchanged.
- [ ] Make testConnection list at most one object under the configured prefix without creating, replacing, or deleting an object. Paginate listRevisions with S3 continuation tokens.
- [ ] Close each S3Client after its IO operation and map authentication, access-denied, TLS, DNS, timeout, and invalid-endpoint failures to safe error categories without logging credentials or object bodies.

The client builder must follow this shape:

    S3Client.builder()
        .endpointOverride(URI.create(settings.endpointUrl))
        .region(Region.of(settings.region))
        .forcePathStyle(settings.addressingStyle == S3AddressingStyle.PATH)
        .credentialsProvider(StaticCredentialsProvider.create(credentials))
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .build()

### Task 2: Add immutable revision graph and atomic Room state

**Files:**

- Modify: app/src/main/java/com/example/personalbookkeeping/data/local/LedgerDatabase.kt
- Modify: app/src/main/java/com/example/personalbookkeeping/data/local/LedgerEntryDao.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/local/ObjectSyncRecordStateEntity.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/local/ObjectSyncConflictEntity.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/local/ObjectSyncStateDao.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/repository/ObjectStorageSyncRepository.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/repository/RoomObjectStorageSyncRepository.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/sync/ObjectStorageRevision.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/sync/ObjectStorageRevisionCodec.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/sync/ObjectRevisionGraph.kt
- Create: app/src/main/java/com/example/personalbookkeeping/data/sync/ObjectStorageSyncManager.kt
- Modify: app/src/main/java/com/example/personalbookkeeping/LedgerApplication.kt

**Interfaces:**

- ObjectStorageRevision contains schemaVersion, recordId, revisionId, sorted parentRevisionIds, and the complete LedgerEntry including deletedAt.
- ObjectRevisionGraph validates one revision per ID, canonical record IDs, hash-to-content agreement, existing parents, matching parent record IDs, and acyclic ancestry; it returns all heads grouped by record ID.
- ObjectStorageSyncRepository scopes local sync entries, accepted revision heads, and persisted conflicts by storageId, a stable hash of normalized endpoint, region, bucket, and prefix. It exposes one atomic replaceResolvedSnapshot(storageId, entries, acceptedHeads).
- ObjectStorageSyncManager exposes status(), testConnection(), syncNow(), resolveConflict(recordId, revisionId), configure(...), and clearConfiguration().

- [ ] Compute revisionId as SHA-256 of stable serialized revision content excluding revisionId itself and including sorted parent IDs. Use key prefix/v1/records/{recordId}/revisions/{revisionId}.json.
- [ ] Treat a local edit as a child of its accepted revision. Treat an unsynced new record as a root; when an unsynced record matches one remote head exactly, adopt that head instead of creating a duplicate root.
- [ ] Treat records with one graph head as resolved. Automatically join multiple heads only when their LedgerEntry values are identical. Persist differing heads as an explicit conflict.
- [ ] Resolve a conflict by writing one immutable revision whose parents include every competing head and whose value is the user-selected version.
- [ ] Add Room schema version 2 with additive metadata tables and an explicit 1-to-2 migration. Key metadata by storageId so changing endpoints cannot reuse unrelated revision heads. Do not use destructive fallback and do not change existing Git credential storage.
- [ ] In one Room transaction, replace the resolved ledger snapshot and accepted revision-head metadata. Before replacing, require every current local record ID to appear in the merged snapshot. If any conflict or invalid object exists, leave the ledger snapshot unchanged.
- [ ] Validate all listed remote revisions before upload or Room mutation. Follow every list page. On remote write failure, retain local records; immutable objects already uploaded remain safe and retries use the same content hash.
- [ ] On first sync, upload existing local rows as roots. On a clean installation, apply remote heads only after the full graph validates. Keep tombstones in the graph and never delete remote revision objects.
- [ ] Keep conflict variants available after process restart by persisting them in the new Room metadata table.

### Task 3: Add OSS settings, status, manual sync, and conflict selection UI

**Files:**

- Modify: app/src/main/java/com/example/personalbookkeeping/ui/screens/SettingsScreen.kt
- Create: app/src/main/java/com/example/personalbookkeeping/ui/screens/ObjectStorageSettingsScreen.kt
- Create: app/src/main/java/com/example/personalbookkeeping/ui/screens/ObjectStorageConflictScreen.kt
- Modify: app/src/main/java/com/example/personalbookkeeping/ui/PersonalLedgerApp.kt
- Modify: app/src/main/java/com/example/personalbookkeeping/LedgerApplication.kt

**Interfaces:**

- SettingsScreen receives separate Git and OSS configuration/status callbacks.
- ObjectStorageSettingsScreen receives existing non-secret settings and saving/error state; secret fields start blank when editing.
- ObjectStorageConflictScreen receives every competing revision and a callback selecting one revision ID per record.

- [ ] Add an independent Object Storage card next to Git in Settings. Show local-only status when unconfigured and keep the current local ledger usable.
- [ ] Add endpoint, region, bucket, prefix, addressing mode, access key ID, masked secret, and optional masked session-token fields.
- [ ] Saving settings performs no network request. Add an explicit connection test that lists the configured prefix and does not modify remote data.
- [ ] Add a separate manual sync button and show syncing, last-success, authentication, permission, network, invalid-data, and conflict states.
- [ ] Keep Git and OSS status/actions independent, including when both providers are configured.
- [ ] When any divergent revisions exist, show all choices for every conflicting record and persist the chosen revision through ObjectStorageSyncManager. Do not expose an implicit keep-local or keep-remote default.
- [ ] Keep secret fields blank after saved settings are loaded; blank secret fields during an edit retain the saved values, while entering a replacement rotates them. Use a dedicated clear-settings confirmation that leaves the ledger and remote objects untouched.
- [ ] Do not trigger remote calls from LaunchedEffect, app startup, or settings screen entry.

### Task 4: Document configuration and compile the Android app

**Files:**

- Modify: README.md
- Create: docs/object-storage-sync.md
- Modify: docs/superpowers/specs/2026-10-21-oss-sync-design.md

- [ ] Remove the README statement that OSS is out of scope and document the manual S3-compatible settings, private-bucket/least-privilege credential guidance, immutable revision behavior, and current lack of remote garbage collection.
- [ ] Document example Endpoint/Region/Bucket/prefix/addressing values for Alibaba OSS and a generic MinIO-style server without including any real credentials.
- [ ] Build with gradlew.bat :app:assembleDebug from the project root. Expected result is BUILD SUCCESSFUL and app/build/outputs/apk/debug/app-debug.apk.
- [ ] If compilation fails, report the first Kotlin/Gradle diagnostics and fix the specific cause before calling the feature buildable. Do not claim device-side provider compatibility without a configured test endpoint.

## Self-review

- Product goals, settings, manual-only behavior, conflicts, security, migration, error handling, and acceptance criteria are each assigned to a task.
- Git state remains independent; Room and local-only behavior remain available when OSS is unconfigured.
- SDK settings, data model, and manager interfaces are defined before UI consumers.
- The plan does not assume S3 conditional writes or delete permission and does not silently discard remote history.

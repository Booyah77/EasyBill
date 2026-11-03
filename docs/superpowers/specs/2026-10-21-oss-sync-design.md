# Configurable Object Storage Sync Design

**Status:** Draft for user review<br>
**Scope change:** This design supersedes the original decision to defer OSS sync in the personal Android bookkeeping task.

## Goal

Add an optional, manually triggered S3-compatible object-storage sync backend. A user can configure their own storage endpoint and keep using the app locally when storage is not configured. Existing Git sync remains available and independent.

## Decisions

- Android remains local-first; Room is the local source of truth.
- OSS sync is opt-in and runs only after the user taps a connection-test or sync action. No WorkManager job, background sync, or automatic upload is added.
- The user configures an S3-compatible endpoint, region, bucket, object prefix, addressing style, access key ID, secret key, and optional temporary session token.
- Alibaba Cloud OSS and self-hosted S3-compatible services such as MinIO are intended targets, subject to their support for the required S3 object APIs and addressing mode.
- Sync history uses immutable per-record revisions and deletion tombstones. The client never replaces a shared mutable ledger snapshot.
- If two devices produce different child revisions of the same record, keep both revisions and require an explicit choice. A resolution revision references both competing revisions.
- When the remote data is malformed or a conflict is unresolved, do not partially replace the local Room snapshot.
- Keep the current Git implementation and its settings behavior unchanged.

## Approaches considered

1. **Alibaba OSS Android SDK only.** This uses Alibaba's SDK and regional configuration directly, but does not meet the configurable-server goal for MinIO and other S3-compatible endpoints.
2. **S3-compatible endpoint with immutable revision objects (recommended).** This supports configurable endpoints and avoids last-write-wins overwrites. It needs a revision graph, local sync metadata, and conflict handling.
3. **One mutable JSON snapshot.** This is simpler, but concurrent device uploads can silently replace newer data. It is rejected for bookkeeping sync.

Alibaba documents S3-compatible PutObject, GetObject, and ListObjects operations. Its OSS service only accepts virtual-hosted addressing, so the endpoint configuration must support the addressing mode required by each provider. Alibaba also documents that PutObject rejects conditional headers; this design therefore does not rely on ETag compare-and-swap for writes.

The Android implementation uses the AWS SDK for Java 2.x S3 client with a custom endpoint, explicit region, selectable path-style addressing, and the URLConnection HTTP transport. Use static credentials or session credentials based on whether a session token is configured. Keep requests synchronous inside the app's IO dispatcher, and set checksum calculation/validation to WHEN_REQUIRED for third-party S3 compatibility; the revision SHA-256 remains the content-integrity check.

## Settings UI

Add a separate object-storage card to Settings beside the existing Git card. Each backend has its own configuration, status, and manual action. Configuring one backend does not enable or invoke the other.

The object-storage form contains:

- Endpoint URL (HTTPS)
- Region
- Bucket
- Object prefix (default personal-ledger)
- Addressing style (virtual-hosted or path-style)
- Access key ID
- Secret key (masked)
- Optional session token (masked, for temporary credentials)

The form supports saving and clearing settings, and an explicit connection test. The test lists the configured prefix without changing remote data; write permission is verified by a manual sync. Saving settings does not make a network request. Without saved object-storage settings, the app remains fully usable offline.

## Revision format and object layout

Use a versioned, deterministic JSON revision format. Each revision contains:

- schemaVersion
- recordId
- revisionId
- parentRevisionIds
- the complete ledger entry, including deletedAt for tombstones

The object key is:

    <prefix>/v1/records/<recordId>/revisions/<revisionId>.json

revisionId is a SHA-256 digest of the canonical revision payload excluding the revisionId field itself, including sorted parent IDs. Retrying an upload therefore addresses the same immutable object. Different edits based on the same parent produce different revisions without overwriting each other.

Objects are not deleted during sync. Older revisions remain available as history; automatic history compaction and remote garbage collection are out of scope.

## Manual sync flow

1. Acquire a sync mutex and validate the saved settings. Perform all network and file operations off the main thread.
2. List and download all remote revisions under the configured prefix, following every page. Validate schema versions, record IDs, revision IDs, parent references, and graph structure before applying any remote data.
3. Compare each local sync entry with its last accepted revision. Create a child revision for local edits or tombstones. For a record without local sync history, adopt a matching remote head when its entry is identical; otherwise create a root revision so a differing same-ID record becomes an explicit conflict. Upload revisions with immutable keys.
4. List revisions again so concurrent uploads made during this sync are included.
5. For each record, identify graph heads. A single head is the current version. Multiple heads with identical entry values can be joined automatically by writing a revision whose parents are all identical heads; different values become a conflict.
6. If conflicts exist, persist both variants and show a conflict screen. A user choice writes a new revision with all competing heads as parents. Do not apply a partial remote snapshot while conflicts remain.
7. Before applying, verify that the merged snapshot still contains every local record ID. When every record has one resolved head, apply the resulting snapshot to Room and persist the accepted revision IDs in one transaction, then record the last successful sync time.

Remote revision writes are append-only, so concurrent devices cannot erase one another's revision objects. A failed sync may leave harmless immutable objects in storage; retrying is idempotent. A later sync discovers revisions that were not visible during an earlier listing.

## Local state and migration

Add Room metadata for the last accepted revision head per ledger record and unresolved object-storage conflicts. Namespace this metadata by a stable hash of the normalized endpoint, region, bucket, and prefix so changing servers cannot reuse another bucket's revision heads. This is an additive schema migration; it must preserve existing ledger rows and Git state. Keep the remote revision files separate from CSV/JSON export files.

Existing ledgers have no OSS revision history. On first sync, local records become root revisions. On a clean installation, a successful remote sync populates the local ledger from resolved remote heads.

## Credentials, privacy, and transport

- Encrypt the secret key and optional session token at rest with a dedicated Android Keystore key; never log them or include them in revision objects.
- Require HTTPS endpoints and private buckets. Show a reminder to scope credentials to listing and reading/writing objects under the configured prefix; do not request bucket-administration or object-delete access.
- This is client-side credential storage: a device owner or compromised device may still extract/use credentials. Alibaba recommends STS temporary credentials or server-generated presigned URLs instead of long-term AccessKey pairs in production mobile apps. Supporting an STS/presigning service is out of scope for this iteration.
- Ledger revision JSON is not client-side encrypted. The storage account must be private; client-side encryption with a user-shared key is a separate future design.
- Do not include credentials or ledger contents in logs or user-facing network error details.

## Errors and data safety

- Missing configuration: show local-only status and do not access the network.
- Invalid endpoint or unsupported addressing: show a configuration error before changing local data.
- Authentication, permission, DNS, TLS, timeout, and service errors: keep Room unchanged and show an actionable category.
- Invalid or unsupported remote revisions, missing parents, cycles, or duplicate revision IDs with different content: abort before applying the remote snapshot.
- Same-record divergent edits: preserve all versions and ask the user to choose; never resolve silently.
- Cancellation: release the sync mutex and keep the current local snapshot.

## Acceptance criteria

- A user can leave OSS unconfigured and use all local ledger functions offline.
- A user can configure a custom S3-compatible endpoint and explicitly test the connection.
- Manual sync transfers records and tombstones between two installations without losing unrelated edits.
- Concurrent different edits to one record remain available as two choices; the chosen resolution is visible on both devices after their next manual sync.
- Failed requests, invalid remote data, and unresolved conflicts do not partially replace Room data.
- Saved credentials are masked in the UI, encrypted at rest, and absent from logs and synced data.
- Git sync continues to work independently; no background sync is introduced.

## Risks and limitations

- S3-compatible providers vary. The first release supports only the required list/get/put behavior and the configured addressing style; provider-specific APIs are not assumed.
- Revision history grows over time. It is expected to remain small for a personal ledger; cleanup needs a separate safe retention design.
- Long-term credentials in a mobile client are less safe than short-lived credentials. The user should use a dedicated least-privilege credential and a private bucket.
- Network availability or a provider's listing consistency can delay visibility of a newly uploaded revision until the next manual sync; immutable keys prevent one device from overwriting another's revision.

## Official references

- [Alibaba Cloud OSS S3 API compatibility and addressing differences](https://www.alibabacloud.com/help/en/oss/developer-reference/compatibility-with-amazon-s3)
- [Alibaba Cloud OSS PutObject behavior and permissions](https://www.alibabacloud.com/help/en/oss/developer-reference/putobject)
- [Alibaba Cloud OSS conditional PutObject headers are unsupported](https://www.alibabacloud.com/help/en/oss/user-guide/0017-00000245)
- [Alibaba Cloud OSS Android SDK quickstart and credential guidance](https://www.alibabacloud.com/help/en/oss/developer-reference/getting-started-with-oss-sdk-for-android)
- [AWS SDK for Java 2.x custom endpoint configuration](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/endpoint-config.html)
- [AWS SDK for Java 2.x S3 addressing and client options](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html)
- [AWS SDK for Java 2.x URLConnection HTTP client](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration-url.html)
- [AWS SDK for Java 2.x checksum behavior](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/s3-checksums.html)

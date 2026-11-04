# AI Bookkeeping Contract

## 1. Scope / Trigger

The Home AI action sends user-entered text to a configurable HTTPS Chat Completions service and automatically inserts the complete validated result into Room. The usability increment adds local batch history and meaningful custom categories. Database version 3 adds membership metadata; CSV/JSON and Git/S3 payloads remain version 1. See [ledger-sync-contract.md](./ledger-sync-contract.md) for persistence guarantees.

## 2. Signatures

Implemented in `app/src/main/java/com/example/personalbookkeeping/data/ai/` and the ledger repository:

```kotlin
fun interface ChatCompletionsClient {
    suspend fun complete(credentials: AiCredentials, request: AiRecognitionRequest): String
}

suspend fun recognizeAndSave(text: String, onPhase: (AiPhase) -> Unit = {}): AiBookkeepingResult
suspend fun retrySave(onPhase: (AiPhase) -> Unit = {}): AiBookkeepingResult

fun decode(
    response: String,
    categories: Map<LedgerType, List<String>>,
    createdAt: Instant,
    newId: () -> String = { UUID.randomUUID().toString() }
): List<LedgerEntry>

suspend fun saveAiBatch(batchId: String, savedAt: Instant, entries: List<LedgerEntry>): ImportCommitResult
fun observeAiBatches(): Flow<List<AiBatch>>

data class AiBatch(
    val id: String,
    val savedAt: Instant,
    val originalCount: Int,
    val entries: List<LedgerEntry>
)
```

`AiBookkeepingManager` owns one operation mutex and a pending immutable batch. `AiBookkeepingResult.Saved` identifies the batch. `AiUiState.history` observes persisted batches, while nullable `currentBatchId` selects the current successful result independently of whether it still has active entries. `enterFromHome()` resets a completed visit only when no operation or pending save exists; `newDraft()` never deletes history.

## 3. Contracts

### Provider, privacy and decoding

- Defaults: URL `https://api.deepseek.com`, model `deepseek-flash`. Accept a base, `/v1` base or complete `/chat/completions` URL. Require HTTPS; reject user info, query, fragment, dot or encoded path segments. Disable redirects.
- Settings use SharedPreferences `ai_bookkeeping_settings` (`service_url`, `model`, `encrypted_api_key`) and an independent AES-GCM Android Keystore alias. A blank key preserves the old credential only for the unchanged normalized destination. No plaintext keys in saved state, logs, ledger, exports or sync.
- POST bearer auth, `stream=false`, `response_format={"type":"json_object"}`. Only official `api.deepseek.com` on the default HTTPS port receives `thinking={"type":"disabled"}`.
- Send only current text, a local date captured before IO and category names. Never send historical entries/notes. Unspecified date/type defaults to the captured date/EXPENSE. Missing or ambiguous amounts and unsupported currencies produce no records; these semantic instructions still require user correction when the model misunderstands.
- Limits: input 6000 characters, encoded request 65536 bytes, response 131072 bytes, 100 records, operation deadline 90000 ms and bounded socket timeouts. No automatic model retry.
- Accept one assistant choice, `finish_reason="stop"`, no refusal and nonblank strict JSON. All record fields below are JSON strings; reject coercion from numeric/null/boolean values.

```json
{"records":[{"type":"EXPENSE","amount":"10.00","date":"2026-11-03","category":"餐饮","note":"鸡腿"}]}
```

- Type is INCOME/EXPENSE; amount is a positive decimal with at most two fractional digits and exact `BigDecimal.movePointRight(2).longValueExact()` conversion. Reject per-record/batch overflow, invalid ISO local date, blank or over-500-character note. Never salvage partial/truncated content or use Double for money.

### Categories and manual editing

- Expense defaults: 餐饮、交通、购物、居家、通讯、娱乐、医疗、教育、人情、其他. Income defaults: 工资、奖金、兼职、理财、礼金、其他.
- `LedgerCategories.available` adds active historical names independently per type. Match exact same-type names first, including imported whitespace/long names; only then try trimmed/normalized same-type matching while preserving the stored spelling. Existing names are not retroactively constrained by the new-name rule.
- A meaningful new short category is allowed when defaults do not fit, for example 公益捐赠. Reject new names outside 1–20 characters, placeholders, control/newline/zero-width characters. Reuse a new name consistently within its batch. A name known only to the opposite type falls back to 其他; an exact same-type name always takes precedence.
- Prompt common purchases into broad defaults (鸡腿→餐饮, 手机壳→购物), leaving merchants/brands/items in notes. Uncertain purposes use 其他. Structural checks cannot prove the semantic quality of a new name.
- Manual choices wrap on small screens, select either one default or custom, and cannot retain hidden custom text after switching type/default. A saved AI custom name opens the custom editor. Editing only an old entry's amount preserves its category byte-for-byte, including old 转账. Initialize editable money from ungrouped exact decimals.

### Local history, transactions and navigation

- Room version 3 adds `ai_batch_entries`: one membership row per entry ID, containing stable batch ID, batch save timestamp and position, with a batch ID index. There is no cascading foreign key to ledger rows: sync replacement uses REPLACE, which must not remove AI provenance. Keep migrations 1→2 and 2→3; no destructive fallback.
- Validate the entire model response before allocating the pending batch once and calling `saveAiBatch`. Ledger insertion and membership insertion share one Room transaction. Validation, conflicts, membership failures or cancellation before commit expose neither partial ledger rows nor a partial history batch.
- Keep batch ID, timestamp, entry IDs and order stable across uncertain/failed saves. When that exact ordered membership already exists, report committed success without comparing or overwriting current mutable ledger values. Reusing a batch ID with different membership fails. Retry never re-calls the model, restores deleted entries or overwrites corrections.
- History joins current ledger values and excludes tombstones from active entries/totals. Preserve originalCount and the batch even when every entry is deleted or absent, showing an explicit empty-batch state. Manual/imported entries do not acquire AI membership.
- No original prompts, model responses or entry snapshots are stored in history. Membership is local-only and excluded from v1 import/export/Git/S3 payloads. Existing pre-upgrade entries cannot be reliably backfilled; they remain in the ordinary ledger.
- Actual Home→AI entry starts fresh after completion. Returning from configuration/history, rotation and background restoration preserves the draft. Do not reset from composition/lifecycle callbacks. Protect SAVE from navigation and keep pending retries. Busy state is not restored after process death; pending unsaved batches remain process-local.

## 4. Validation & Error Matrix

| Condition | Result | Mutation |
| --- | --- | --- |
| Missing settings/key | NOT_CONFIGURED / CREDENTIAL_STORAGE | None |
| Invalid config or overlong text | INVALID_CONFIG / INPUT | None |
| HTTP 401/403, 402, 429 | AUTH, BALANCE, RATE_LIMIT | None |
| Network/deadline/service failure | NETWORK / TIMEOUT / SERVICE | None |
| Empty/truncated/refused/oversized response or any invalid record/new category | RESPONSE | None |
| Valid new same-type category | Preserve new category | Only in whole-batch transaction |
| Opposite-type-only category | Matching type's 其他 | Only after full validation |
| Failed ledger or membership insertion | SAVE with retry | Transaction rolls back |
| Exact already-committed membership | Saved, duplicates counted | Preserve current edited/deleted values |
| Batch ID reused with changed membership | SAVE / repository conflict | None |
| Concurrent operation | Busy | No extra request or write |

Expose sanitized `AiFailure.message`; never raw server bodies, decoding errors or credential exceptions. Propagate cancellation.

## 5. Good / Base / Bad Cases

- Good: 鸡腿10元晚上吃饭20手机壳10元 → 餐饮1000/2000 fen and 购物1000 fen, notes 鸡腿/晚上吃饭/手机壳, total4000 fen.
- Good: 公益捐赠 is kept as a new category; editing it opens custom. Existing whitespace/long categories remain unchanged when correcting only money.
- Good: save commits but its result is cancelled; a later edit/delete followed by retry returns success without undoing the correction.
- Base: an upgrade preserves old ledger/tombstone/object-sync rows; history starts empty because old AI source is unknown.
- Base: all entries in a batch are deleted; the batch remains visible without stale amounts.
- Bad: a valid first record followed by amount1.001 or invalid category rejects the entire response.
- Bad: reset the composer in `LaunchedEffect` on every visit or delete membership through a cascading ledger foreign key.

## 6. Tests Required

JVM fixtures use fake configuration/client/repository without real keys or model charges. Assert exact money/notes, category defaults/type separation/new names/old-name preservation, malformed responses and whole-batch rejection, privacy/provider options, cancellation/concurrency, stable batch IDs and uncertain-save retries after edits/deletes. Parse the format dialog's CSV/JSON samples with the actual v1 codec.

Actual Room tests must cover schema1→2→3 and2→3 validation with old ledger/tombstones/object-sync data, transaction rollback on a late conflict or membership error, current-value joins, all-deleted batches, and membership surviving snapshot replacement. ViewModel tests cover Home reset versus configuration/history returns and pending-save protection. Run `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug` and the available connected instrumentation tests; report native UI and live-provider validation separately.

## 7. Wrong vs Correct

```kotlin
// Wrong: incremental writes can partially save and history snapshots become stale.
records.forEach { repository.save(convertUsingDouble(it)) }

// Correct: decode all money/category fields before a single ledger+membership transaction.
val entries = decoder.decode(response, categories, createdAt, newId)
repository.saveAiBatch(batchId, savedAt, entries)
```

After an uncertain save, reuse the pending batch and its membership through `retrySave()`. Idempotence checks immutable membership, not current ledger equality, because users can correct or delete already-committed entries.

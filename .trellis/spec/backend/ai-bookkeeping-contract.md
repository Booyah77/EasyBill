# AI Bookkeeping Contract

## 1. Scope / Trigger

The Home AI action sends one user-entered text to a configurable HTTPS Chat Completions service and automatically inserts the complete validated result into the local ledger. Room remains the source of truth. AI does not change database schemas, import/export formats, or synchronization formats. See [ledger-sync-contract.md](./ledger-sync-contract.md) for persistence guarantees.

## 2. Signatures

Implemented in `app/src/main/java/com/example/personalbookkeeping/data/ai/`:

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
```

`AiBookkeepingManager` owns a single-operation mutex and a pending immutable batch. Its successful write uses the existing `LedgerRepository.importEntries(entries)` transaction.

## 3. Contracts

- Defaults: URL `https://api.deepseek.com`, model `deepseek-flash`. Both remain editable. Accept a base URL, `/v1` base, or complete `/chat/completions` URL; append the endpoint exactly once. Require HTTPS and reject URL user info, query, fragment, dot path segments, or encoded path segments. Disable redirects so credentials cannot move to a different destination.
- Settings use SharedPreferences `ai_bookkeeping_settings` with `service_url`, `model`, `encrypted_api_key`. Encrypt the key using the existing AES-GCM Android Keystore cipher with independent alias `personal-ledger-ai-api-key-v1`. A blank replacement key preserves the old credential only for an unchanged normalized destination. Do not store plaintext keys in saved instance state, logs, ledger records, exports, or sync files.
- Request: POST, bearer auth, `stream=false`, `response_format={"type":"json_object"}`, one system prompt and one user text. Send `thinking={"type":"disabled"}` only to official `api.deepseek.com` on the default HTTPS port. Other compatible services receive no provider-specific thinking parameter.
- Send only the current text, a local date captured before IO, and category names. `LedgerCategories.available` combines built-in defaults with active historical custom categories independently for INCOME/EXPENSE. Preserve exact existing category names, including whitespace retained by file import; only try a trimmed response name if the exact name is absent. Manual editing identifies custom names against the entry's own type defaults. Never upload historical entries or notes.
- Prompt defaults unspecified dates to the captured local date and unspecified type to EXPENSE; supports CNY only. Missing/ambiguous amounts or unsupported currencies must produce no records. These are model instructions; local structural validation cannot prove semantic correctness.
- Input is at most 6000 characters; encoded requests at most 65536 bytes. Responses at most 131072 bytes, with at most 100 records. Socket timeouts are bounded and the operation has a 90000 ms deadline.
- Response must be a single assistant choice with `finish_reason="stop"`, no refusal, and nonblank JSON content. Content contract:

```json
{"records":[{"type":"EXPENSE","amount":"10.00","date":"2026-11-03","category":"餐饮","note":"鸡腿"}]}
```

- Every record requires an INCOME/EXPENSE type, positive decimal amount with at most two fractional digits, valid `YYYY-MM-DD` date, category and nonblank note of at most 500 characters. Convert with `BigDecimal.movePointRight(2).longValueExact()`, never Double. Detect record and batch amount overflow. An unknown category falls back to that type's existing `其他`; never create a category from model output.
- Allocate IDs and timestamps once after decoding. Validate the entire response before calling the transaction. Retain the same entries after uncertain or failed saves and retry only the transaction, preserving IDs, amounts and timestamps. There is no automatic network retry.
- The ViewModel retains the draft through settings navigation, does not persist a busy flag, and prevents concurrent submissions. An uncommitted recognition may be cancelled; the save phase is protected from navigation. After save, clear the draft and show item details. Existing ledger editing/deletion corrects semantic mistakes. Initialize editable amounts from exact ungrouped decimals so grouped display values such as `1,000.00` do not fail the amount parser during correction.

## 4. Validation & Error Matrix

| Condition | Result | Ledger mutation |
| --- | --- | --- |
| Missing settings/key | NOT_CONFIGURED / CREDENTIAL_STORAGE | None |
| Invalid URL/model/key or overlong text | INVALID_CONFIG / INPUT | None |
| HTTP 401/403, 402, 429 | AUTH, BALANCE, RATE_LIMIT | None |
| Network/deadline/service failure | NETWORK / TIMEOUT / SERVICE | None |
| Empty/truncated/refused/oversized JSON or any invalid record | RESPONSE | None |
| Category absent from matching type | Replace with `其他` | Only after complete batch validation |
| Repository failure/uncertain commit result | SAVE with retry available | Atomic transaction; retry same IDs |
| Concurrent operation | Busy | No extra request or write |

Expose only `AiFailure.message`. Do not surface raw transport, server body, decoding, or Git cipher exceptions. Propagate coroutine cancellation.

## 5. Good / Base / Bad Cases

- Good: `鸡腿10元晚上吃饭20手机壳10元` produces three expense entries with notes 鸡腿/晚上吃饭/手机壳, categories 餐饮/餐饮/购物, and amounts 1000/2000/1000 fen, total 4000 fen.
- Base: no config opens the configuration path while preserving the text. A historical custom category is available only to its matching income/expense type.
- Bad: valid first item followed by amount `1.001`, zero, overflow, or invalid date rejects the whole response; no partial entries are inserted. A truncated response must not be salvaged.

## 6. Tests Required

Use JVM fixtures and fake configuration/client/repository without real keys or live model charges. Assert exact fen values and notes for the example, category fallback and type separation, date capture, strict malformed/truncated response rejection, all-or-nothing validation, no repository writes on config/API failure, single in-flight submission, cancellation propagation, and stable IDs across failed/uncertain save retries. Check request JSON for privacy and provider-specific options. Run `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:lintDebug` before reporting verification.

Device UI behavior and live model semantics require a configured key and Android runtime; report them separately from offline fixture tests.

## 7. Wrong vs Correct

```kotlin
// Wrong: floating-point conversion and incremental writes can corrupt money or partially save.
records.forEach { repository.save(convertUsingDouble(it)) }

// Correct: exact conversion and validation finish before the single transaction.
val batch = decoder.decode(response, categories, createdAt, newId)
repository.importEntries(batch)
```

After a failed save, reuse `batch` through `retrySave()`; do not call the model again and allocate fresh IDs for the same pending result.

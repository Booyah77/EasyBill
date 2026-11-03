# Design: Personal Android Bookkeeping

## Goal and constraints

Build a native Android personal ledger in the currently empty workspace. Keep everyday entry local and fast. Add fixed-schema CSV/JSON backup and restore, plus optional manually triggered Git sync. No app-operated service or account is required.

The user has approved these product decisions: Android first; CSV/JSON use the app's fixed format; invalid import rows roll back the whole import; Git sync is manual and optional; concurrent edits preserve both versions and let the user choose. Raw WeChat Pay and Alipay exports, OSS, iOS, and automatic sync are deferred.

## Architecture

- Native Kotlin Android app, single application module, Jetpack Compose, Material 3, ViewModels, and unidirectional UI state.
- Two app layers: UI and data. The data layer exposes repositories; Room is the local source of truth. Avoid a separate backend or a domain layer until business logic justifies it.
- Use Room for structured ledger data and Android's Storage Access Framework for user-selected import and export files.
- Keep file import/export and Git sync behind repository/service boundaries so they never write directly from a screen to Room.
- Use Android Keystore-backed encryption for stored Git credentials. Never write tokens into source, exported ledger files, sync files, or logs.
- Use JGit 5.13.5 for HTTPS Git operations. JGit 7.x requires Java 17, which is not a safe runtime assumption for the app's Android API 26 minimum; 5.13.5 is the latest official 5.x release and was rebuilt for Java 8. Keep sync behind GitSyncManager so the app can replace the engine if device verification finds an Android runtime incompatibility.

## Screens and flows

1. **Home** — current-month income, expense, balance; prominent quick-add actions; a bottom-end + expense FAB that opens a fresh expense form directly; recent entries; empty state for a new ledger.
2. **Quick entry** — income/expense selector, amount, category, date defaulting to today, optional note, save confirmation.
3. **Ledger** — newest-first records, month/type/category filters, edit and delete actions.
4. **Import/export** — select CSV/JSON, inspect a preview and any validation errors, then confirm or cancel; export via Android's create-document picker.
5. **Settings and sync** — optional HTTPS Git URL, username, token, branch (default `main`), connection state, and a manual Sync button. Show the private-repository reminder. When remote settings are absent, keep all local features available.

## Data model

A ledger entry contains a stable UUID, type (`INCOME` or `EXPENSE`), amount in integer fen in Room, currency (`CNY` in v1), date (`YYYY-MM-DD`), category, note, created time, updated time, and a deletion tombstone for sync. Display amounts in yuan. Store timestamps as UTC instants; the accounting date remains a local calendar date.

Room is the source of truth. Categories are stored as names on entries so imports remain lossless; the first version provides common defaults and permits a user-entered category.

## CSV and JSON contract

- JSON root object: `schemaVersion: 1`, `currency: CNY`, `exportedAt`, and a `records` array.
- Record fields: `id`, `type`, `amount` (decimal string with two places), `date`, `category`, `note`, `createdAt`, `updatedAt`.
- CSV has a fixed header with the same record fields plus `currency`; export UTF-8 with a BOM for spreadsheet compatibility and accept an optional BOM on import. Use RFC 4180 quoting and exact decimal parsing.
- Import accepts only the documented schema in v1. JSON versions other than 1, missing/unknown required fields, invalid dates/types/amounts, and conflicting duplicate IDs are validation errors.
- Parse and validate the full file into a staging list before touching Room. Show row and field errors in preview. Any validation error aborts the entire import. On confirmation, insert new IDs in one Room transaction; exact repeats of existing IDs are skipped and summarized.
- Export active entries only. A round-trip import preserves IDs and user-visible fields. Deleted records remain as Git tombstones and are excluded from normal CSV/JSON exports.

## Git sync and conflicts

- The user owns a private HTTPS Git repository. The app stores its working copy in private app storage and syncs only when the user taps Sync.
- Store each record as a separate JSON file under an app-owned directory in the repository. This allows independent record edits to merge without rewriting one giant ledger file. Store deletions as tombstones.
- Sync commits local changes, fetches and merges remote history, then pushes. If remote changes make a push non-fast-forward, fetch and merge before retrying once; report any remaining transport error without changing the local ledger.
- For the same record edited on both devices, read both Git conflict stages and persist both variants in a local conflict record. Ask the user to choose a version; create a resolution commit with both merge parents. Do not discard either version before resolution.
- A delete-versus-edit conflict follows the same choice flow. Local edits and imports remain usable when offline or unconfigured.
- Use an HTTPS username/token credential pair in v1; provider-specific APIs and SSH are deferred. Keep the token encrypted using an Android Keystore-backed key.

## Errors, privacy, and migration

- Invalid imports do not change Room. Show file, row/field, and reason.
- Git failures distinguish missing config, authentication, network, rejected push, and unresolved conflict. Preserve local records on every failure.
- Do not log record contents or credentials. Recommend a private remote; the app does not host or inspect repository privacy.
- Start at database schema version 1; use explicit Room migrations for later schema changes. Users can export CSV/JSON before an upgrade.

## AI natural-language bookkeeping increment (2026-11-03)

This section governs the new AI feature; the earlier sections describe the original app baseline. Existing S3-compatible storage support is retained under the later ledger sync spec.

### Boundaries and data flow

`Home AI entry -> AI screen/ViewModel -> AI bookkeeping manager -> Chat Completions client -> typed local decoder -> repository.importEntries -> Room flows -> result list and ledger totals`.

- Add a small `data/ai` boundary for settings storage, HTTPS transport, prompt construction, response decoding, and orchestration. Reuse kotlinx.serialization, coroutines, the ledger repository, and platform HTTPS APIs rather than adding an AI SDK.
- Extract existing default categories into a shared typed catalog consumed by manual entry and AI. Build the allowed category list from defaults plus all active ledger records, separated by income/expense. Transmit names only, never historic entries or notes.
- Keep the current Room schema and the fixed CSV/JSON/Git/OSS record formats. AI-created records are ordinary ledger records, with locally generated UUIDs and timestamps.
- Share the existing Android Keystore encryption implementation using a distinct AI key alias. Wrap Git-specific credential errors in AI-specific sanitized messages; preserve existing Git and OSS aliases.

### API settings and request contract

- Store HTTPS service URL, model name, and encrypted API Key in separate application-private settings. Default `https://api.deepseek.com`, model `deepseek-flash`.
- Normalize base URLs with optional `/v1`, as well as an explicitly supplied `/chat/completions` URL. Reject user info, query, fragment, invalid hosts, and non-HTTPS URLs before sending a credential. Do not follow redirects carrying the Authorization header.
- Send `POST /chat/completions` with bearer auth, JSON request content, non-streaming mode, a bounded output, and `response_format: {"type":"json_object"}`. Give the system prompt an exact JSON example; keep user bill text in a separate user message. For official DeepSeek, disable thinking using its documented parameter; avoid imposing provider-specific fields on other services.
- Request a root `records` array. Each record has `type` (`INCOME`/`EXPENSE`), decimal-string `amount`, ISO `date`, `category`, and `note`. All monetary conversion is exact via BigDecimal into positive integer fen.
- Prompt supplies the request's fixed local date, allowed category names, default expense semantics, required descriptions, and rules against inventing amounts or categories. Unknown category labels normalize to the allowed `其他` for that type.
- Decode the HTTP envelope once, require a completed non-empty assistant response, then strictly decode and validate the entire result. Reject malformed, truncated, empty, oversize, invalid-date/type/amount results; never save a partial valid subset. Bound input, response, and batch size to control memory and API use.

### UI and automatic save

- Add “AI 记账” on Home and an AI configuration section in Settings, styled using existing warm-paper Material 3 tokens.
- AI screen accepts a paragraph, explains that submitted text and category names go to the configured service, and offers one “识别并记账” action. Missing settings link to configuration and return to the draft afterward.
- On success, save automatically and display per-record amount, category, date and note, plus count and total. Return to the normal ledger for existing edit/delete behavior; no extra review gate is inserted into each submission.
- Keep immutable recognized entries and their IDs through a save retry. Disable overlapping recognition/save actions. Lifecycle state must not restore a stale loading flag without its operation; cancel requests when appropriate and keep draft text on failure.
- Render errors on the active screen. Distinguish invalid config, credential storage, auth/balance/rate limit, network/timeout, response format, and local save failure; never display raw server responses or secrets. Cancellation propagates.

### Validation and rollback

- Reuse `repository.importEntries` to commit the complete batch atomically. Never use remote-snapshot replacement for AI-created entries.
- Unit-test the decoder and orchestration with fixture responses and fakes: example split, exact decimals, custom/fallback categories, late invalid record, absent config, API failure, and stable retry IDs. Keep tests independent of live API keys.
- Run Android debug build and lint under a compatible installed JDK. Verify the real UI on a connected device/emulator if available; disclose unavailable runtime/API checks.
- Rollback removes only AI wiring/settings and the new shared category use; no database migration or existing record rewrite is needed.

## Technical references

- Android recommends Compose, ViewModels/repositories, and unidirectional data flow for new apps.
- Room is recommended over direct SQLite for structured on-device data; the system file picker supports open/create document flows.
- Official Android/JGit sources and exact compatibility notes are recorded in `research/android-architecture.md` and `research/sync-options.md`.

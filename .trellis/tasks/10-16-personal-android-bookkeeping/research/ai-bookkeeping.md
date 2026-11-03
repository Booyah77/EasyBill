# AI bookkeeping integration research (2026-11-03)

## Repository evidence and reuse

- Kotlin / Compose single Android module; Room is the source of truth. Reuse `LedgerRepository.importEntries` for atomic insertion, without invoking sync snapshot replacement.
- Existing category defaults are private in the manual entry screen; extract them once and add historical active category names by ledger type. No category table or migration is needed.
- `AndroidKeystoreTokenCipher` already accepts a custom alias and uses AES/GCM. Reuse with an AI-specific alias; sanitize its Git-specific exception text. `android:allowBackup="false"` is already set.
- kotlinx.serialization and coroutines are available. Use platform HTTPS rather than an extra provider SDK. Maintain integer-fen money and local calendar dates.

## Official API facts

Sources read on 2026-11-03:

- https://api-docs.deepseek.com/api/create-chat-completion/
- https://api-docs.deepseek.com/guides/json_mode/

The official docs currently list `deepseek-flash` and `deepseek-v4-pro`. Default to `deepseek-flash`; keep the model name editable. Chat requests use a bearer API key and `POST /chat/completions`; the JSON output sample uses base URL `https://api.deepseek.com`.

JSON output requires `response_format: {"type":"json_object"}`, a prompt explicitly requesting JSON and an example. Set a reasonable output token limit. Even with `finish_reason: stop`, content can be blank whitespace; it must be rejected locally. `finish_reason: length`, resource interruption or any non-completed response must not be saved.

Thinking is enabled by default for the current official models; `thinking: {"type":"disabled"}` disables it. Restrict this provider-specific request option to official DeepSeek, so generic compatible endpoints receive a portable request.

## Concrete contracts

- Root JSON: `{"records":[{"type":"EXPENSE","amount":"10.00","date":"2026-11-03","category":"餐饮","note":"鸡腿"}]}`.
- Caller captures the request date once. Omitted dates in the source use that date; omitted type means expense. Do not fabricate missing amounts.
- Notes describe the individual item. Categories must be selected from the supplied list for that type, with `其他` as fallback. Never send historic notes or records.
- Validate the complete list before mutation. Reject empty, invalid, oversize or truncated responses; use exact decimal parsing, not floating point.
- Do not follow credential-bearing redirects. Use HTTPS and request/response bounds and timeouts. Do not expose raw server responses in UI/logs.
- Stable locally generated IDs across persistence retry plus the existing import duplicate check avoid duplicating the same in-flight batch.

## Verification plan

Use fake client and repository boundaries for offline tests of valid splitting, decimal precision, type/date/category validation, late invalid row atomicity, missing settings, API errors and stable retry. Build/lint the Android app and inspect device availability after implementation. Live DeepSeek calls require the user's configured key and are not replaced with invented credentials.

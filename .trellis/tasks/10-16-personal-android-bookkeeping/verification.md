# Verification Record

## AI increment — 2026-11-03

The user approved the configurable DeepSeek natural-language bookkeeping plan before implementation. Implementation and the full-scope review are complete.

### Passed

- `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain` completed successfully using Gradle 9.6.0 and JBR 21.0.9. The final run completed in 1m 5s with exit code 0.
- 13 JVM tests passed: `AiBookkeepingTest` has 10 tests and `AiSettingsTest` has 3 tests, with zero failures, errors, or skipped cases. Final XML timestamps are `2026-11-03T09:29:19.531Z` and `2026-11-03T09:29:19.889Z`.
- Tests cover the three-item example with exact integer-fen amounts, whole-batch invalid-result rejection, amount/batch overflow, type-specific and exact imported categories, missing configuration/API failures, request privacy, concurrency, cancellation, and stable-ID retry after failed or uncertain commits.
- Android lint reports **0 errors, 19 warnings**. No lint checks were disabled. The existing API-27 navigation-bar attribute was moved into a version-qualified resource, retaining API-26 support.
- The final debug APK was installed successfully on the read-only Android API-37 emulator and `MainActivity` launched successfully.
- Emulator UI hierarchy checks confirmed the Home AI entry opens the new screen; the configuration screen shows the DeepSeek URL/model defaults; saving without a Key shows a local validation error; returning from configuration preserves the `lunch10` draft. No model request was made during this walkthrough.
- Trellis task context validation passed with 9 implementation entries and 9 check entries.
- Full-scope review covered configuration/encryption, HTTPS requests and decoding, atomic repository insertion, ViewModel/navigation, screens, shared category/editor behavior, tests, and documentation. Confirmed fixes preserve exact imported category names, use each entry's own type for custom-category editing, and avoid grouped editable amounts.

### Build/environment fixes

- Added an explicit Gradle JVM heap setting after the default 512 MiB heap caused GC thrashing.
- Replaced anonymous test configuration fixtures with an equivalent named fixture after the Kotlin FIR lint analyzer crashed on anonymous class symbols; assertions were preserved.
- Shared Gradle cache writes and emulator state access required the approved broader executions because they are outside the workspace. These were environment restrictions, not application failures.

### Limits and follow-up

- A real provider request remains unverified because no user API Key was supplied. The example's splitting/category assertions use deterministic fake responses; model semantics should be checked with the user's configured service.
- Pending save batches are kept in process; process recreation restores the draft and idle UI, not a durable pending transaction.
- Screenshot archival was stopped after two task-directory creation denials. The bounded ACL diagnosis found a different owner and no effective `WRITE_DAC`/`WRITE_OWNER`, refused repair, and changed no ACL. Report: `.trellis/permission-recovery/acl-report-76d4b0789c6f410c807d0699bbe0d8a5.jsonl`. UI checks used the emulator terminal; no alternative screenshot file destination was used.
- Remaining lint warnings include dependency/target-version guidance, SharedPreferences KTX suggestions, and existing Git/JGit warnings. The change does not establish JGit's Android HTTPS runtime compatibility.
- CSV/JSON round-trip and Git/object-storage conflict workflows were outside this AI increment's emulator walkthrough and were not rerun.
- The workspace has no Git metadata. No repository was initialized, no commit was made, and the task was not archived.

## Historical baseline

Earlier verification parsed the version catalog, manifest, task metadata and context files and checked UTF-8 project text. Earlier CLI builds had been blocked by Gradle access/cache problems; the successful build above supersedes that pending build status. Previously applied baseline fixes include JGit API/import corrections, Room schema export configuration, and the Home expense action.

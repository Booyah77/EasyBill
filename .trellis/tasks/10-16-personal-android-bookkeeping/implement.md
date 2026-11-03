# Implementation plan

## 1. Verify the Android and Git dependency baseline

- Inspect installed JDK/Android SDK/Gradle availability and select a supported minSdk for the target build.
- Add the JGit candidate and ensure the app compiles for the selected Android baseline with HTTPS transport enabled.
- If JGit compatibility fails, stop the sync implementation and update design.md with a supported alternative before proceeding. JGit 7.x was ruled out for minSdk 26; JGit 5.13.5 is selected, with device runtime verification still outstanding.

## 2. Create the Android application scaffold

- Add Kotlin, Gradle, one Android app module, Compose Material 3, navigation, Room, coroutines, and a version catalog.
- Set application ID, app label, permissions, and baseline theme.
- Confirm the empty starter app builds.

## 3. Build the local ledger

- Define typed income/expense/category/date/amount models and Room entities/DAOs.
- Store money as integer fen and expose repository flows for the UI.
- Implement create, edit, delete-with-tombstone, date/category filtering, and monthly totals.

## 4. Build quick-entry and ledger screens

- Follow the warm paper-ledger direction in root `DESIGN.md`: monthly summary, clear income/expense actions, recent rows, amount-first entry, and common-category chips.
- Create home summary, quick-entry form, recent ledger list, edit/delete flow, and useful empty/error/success states.
- Keep screen state in ViewModels and state changes in repositories.

## 5. Implement CSV/JSON import and export

- Define schema version 1 and fixtures/examples in documentation.
- Use Android document pickers to open source files and choose export destinations.
- Add CSV/JSON parsers, exact amount/date validation, preview, row/field errors, duplicate detection, and export serializers.
- Ensure any validation error aborts the whole import; commit valid new records atomically in one Room transaction.

## 6. Implement optional manual Git sync

- Add sync settings for HTTPS remote URL, username/token, and branch; store tokens encrypted with an Android Keystore-backed key.
- Implement local repository setup, per-record sync files, tombstones, local commit, fetch/merge/push, and useful status/error reporting.
- Add conflict review UI that preserves both record variants and lets the user choose before creating a resolution commit.
- Verify unconfigured mode never requires login or a network connection.

## 7. Complete and verify the app

- Check loading, empty, invalid-file, duplicate-import, offline, authentication, push-rejection, and merge-conflict states against the PRD.
- Run the Android debug build and lint; manually walk through quick entry, CSV/JSON export-import round-trip, local-only use, manual sync, and conflict resolution on an emulator/device if available.
- Document setup, required Git remote configuration, supported file schemas, and current limitations.

## 8. AI bookkeeping increment (2026-11-03)

The user approved the latest summary on 2026-11-03. Steps 1–8 are complete: implementation/full-scope review, 13 passing tests, successful debug APK/lint, and focused emulator UI checks. See `verification.md` for final evidence and limits; real provider recognition still requires the user's API Key. The successful current build supersedes the historical CLI constraints below.

1. Load the current PRD/design and curated AI research. Select a compatible installed JDK; retain the existing Android toolchain and build a baseline if possible.
2. Extract one default category catalog; reuse it from manual entry and collect existing custom categories by income/expense.
3. Implement AI settings validation and Android Keystore-encrypted API Key persistence with a separate alias; provide the settings screen and default DeepSeek values.
4. Implement bounded non-streaming HTTPS Chat Completions requests, JSON prompt and typed response validation, exact fen conversion, and sanitized actionable errors.
5. Implement the AI manager/ViewModel with a single operation at a time, immutable staged record IDs, atomic batch persistence through `importEntries`, and cancellation handling.
6. Add Home and Settings navigation, draft input, configuration guidance, loading/error states and automatically saved result details; preserve existing navigation and manual ledger behavior.
7. Add focused JVM fixture tests for the actual boundary and failure risks. Run `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:lintDebug`; inspect any failures before proceeding. Avoid live API calls without a user-configured key.
8. Review the full change against AI-AC01 through AI-AC07 and existing persistence/sync contracts; fix findings and record results. Add configuration/use instructions and a concise AI contract to the repository docs/spec.

Risky integration points: `PersonalLedgerApp` screen routing/back handling, category extraction in `EntryScreen`, reuse of the credential cipher without disturbing sync aliases, and repeated-save behavior. No Room migration, sync schema or import/export schema change is required.

### Historical toolchain constraint

The system java command resolves to Java 8, but Android Studio JBR 25 and Android SDK Platform 37 are installed; Gradle 9.6.0 is cached but not on PATH. The user's Android Studio build reached Kotlin compilation and reported source errors; fixes for those diagnostics are now in the source. A follow-up CLI build did not reach Kotlin compilation: Gradle failed while compiling generated dependency-accessor classes with AccessDeniedException on a Gradle distribution JAR. Running with an isolated copied distribution and offline mode then lacked the AGP plugin marker in that isolated cache. The APK build is therefore still unverified; rerun assembleDebug in Android Studio. JGit 5.13.5 remains selected for minSdk 26, with device sync verification outstanding.
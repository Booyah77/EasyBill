# Journal - Booyah (Part 1)

> AI development session journal
> Started: 2026-10-16

---

## 2026-11-03 — Configurable AI bookkeeping

- Task: `.trellis/tasks/10-16-personal-android-bookkeeping`; user approved the AI increment plan before implementation.
- Added configurable DeepSeek/OpenAI-compatible HTTPS URL, model and locally encrypted API Key; Home entry, natural-language batch recognition and automatic atomic insertion using existing categories and per-item notes.
- Reused Room import transactions and a shared category catalog. Strict JSON/decimal validation rejects the entire batch before writes; process-local stable-ID retries avoid duplicate inserts after failed or uncertain commits. History/notes and credentials are not sent as recognition context or included in ledger exports/sync.
- Full review fixed exact imported-category preservation, own-type custom-category editing and ungrouped editable amounts. Added the executable AI boundary contract under `.trellis/spec/backend/ai-bookkeeping-contract.md`.
- Final verification: 13 tests passed; debug APK and lint passed (0 errors, 19 warnings); final APK installed/launched on a read-only API-37 emulator. UI checks covered AI navigation, default configuration, empty-Key validation and draft retention. Live model requests require the user's Key and were not made.
- Diagnosed/fixed Gradle heap exhaustion and Kotlin FIR anonymous-fixture lint crash, and qualified the API-27 navigation-bar theme resource for minSdk 26. Shared cache access required approved broader commands.
- Screenshot file archival stopped at a task-directory ACL precondition; no ownership change or alternate output path. Evidence/limits are in the task's `verification.md`.
- Recorded manually because the workspace had no Git metadata at AI-feature completion; no initialization, commit or task archive was performed during that feature session.

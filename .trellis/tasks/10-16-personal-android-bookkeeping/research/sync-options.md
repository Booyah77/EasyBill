# Sync backend research

## Confirmed scope

The app is Android-first and personal. Sync is optional, manually triggered, and uses Git in v1. Without a configured remote the app remains local-only. OSS and an app-operated account/service are out of scope. Same-record concurrent edits must preserve both versions and ask the user to choose.

## Git remote

- Git stores snapshots and history, which can support recovery to an earlier version.
- Concurrent edits can produce merge conflicts; Git documents that ambiguous conflicts require explicit resolution.
- The official Eclipse JGit project is a pure Java Git implementation and documents HTTP fetch/push support. JGit 7 requires Java 17, while Android only documents newer Java API desugaring for a subset of APIs. JGit 5.13.5 is the latest official 5.x line and its release notes confirm it was rebuilt against JDK 8 and includes a security fix. Choose it for minSdk 26, but verify runtime behavior on a device before relying on sync.
- A user-owned private HTTPS repository avoids an app-operated sync service. The user configures its URL and credentials in app settings. SSH and provider-specific APIs are deferred.

## Data and credential handling

- Keep the local Room database as the source of truth; Git is a manually invoked remote replica.
- Store each transaction separately in the Git working tree so unrelated record changes do not conflict as one whole-ledger file.
- Use tombstones for deletions so they propagate to other devices.
- Store both conflicting record variants until the user resolves the conflict; the merge commit keeps Git's two parent histories.
- Encrypt the user's Git token at rest with a key protected by Android Keystore, and never include credentials in sync files or logs.
- Warn users to use a private repository because ledger data is sensitive.

## Sources

- [Pro Git: About Version Control](https://git-scm.com/book/en/v2/Getting-Started-About-Version-Control.html)
- [Pro Git: Advanced Merging](https://git-scm.com/book/en/v2/Git-Tools-Advanced-Merging)
- [Eclipse JGit repository](https://github.com/eclipse-jgit/jgit)
- [JGit 5.13.5 release notes](https://projects.eclipse.org/projects/technology.jgit/releases/5.13.5)
- [Android Java APIs and desugaring](https://developer.android.com/build/jdks)
- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)

# Android architecture research

## Findings

- Android recommends Jetpack Compose for new native UI and a UI/data-layer architecture using ViewModels, repositories, and unidirectional data flow.
- Android recommends a local database as the source of truth for offline-first use, and Room instead of direct SQLite APIs for structured local data.
- The Android Storage Access Framework provides system pickers for opening existing files and creating export files.
- The official Eclipse JGit repository describes JGit as a pure Java Git implementation, with HTTP fetch/push support. JGit 7 requires Java 17. Android documents Java 17 core libraries at API 34, while API desugaring only provides a documented subset of newer Java APIs. To retain minSdk 26, select official JGit 5.13.5, whose release notes confirm it was rebuilt against JDK 8 and includes a security fix. The upstream project does not promise Android compatibility, so Git sync still requires a real-device/runtime check.
- Android Keystore keeps key material non-exportable. It can protect an encryption key used to store a Git token locally; it does not prevent an attacker controlling the app process from using the token.

## Design implications

- Use Kotlin, Compose, ViewModels, repositories, and Room as the app's local-first foundation.
- Use the system document picker for CSV/JSON import and export instead of broad shared-storage permissions.
- Keep Git behind a sync interface. JGit 5.13.5 reduces the Android API baseline risk, but runtime compatibility and HTTPS auth still need a real-device check.
- Keep Git credentials out of source files and logs. Encrypt the stored token with an Android Keystore-backed key and recommend a private user-owned remote.

## Sources

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Room local persistence](https://developer.android.com/training/data-storage/room)
- [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
- [Eclipse JGit 5.13.5 release notes](https://projects.eclipse.org/projects/technology.jgit/releases/5.13.5)
- [Eclipse JGit 7.0 Java 17 requirement](https://projects.eclipse.org/projects/technology.jgit/releases/7.0.0)
- [Android Java APIs and desugaring](https://developer.android.com/build/jdks)
- [Eclipse JGit repository and capabilities](https://github.com/eclipse-jgit/jgit)
- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)

## Local build environment

The inspected workspace has Java 8 only; Gradle, adb, sdkmanager, Android SDK, and Android Studio were not found in PATH or the common installation locations. Android Gradle Plugin 9.4.0 requires Gradle 9.6.0 and JDK 17. The project files can be created here, but a local APK build cannot be verified until a compatible JDK and Android SDK are available.

Source: [Android Gradle Plugin 9.4.0 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes).

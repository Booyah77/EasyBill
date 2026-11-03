# Save Feedback and App Icon Implementation Plan

> For agentic workers: Follow this plan with the project Trellis implement/check agents. Work in order and preserve unrelated existing changes. Steps use checkbox syntax for tracking.

**Goal:** Make record-save feedback brief and well placed, and add a distinctive adaptive launcher icon for Personal Ledger.

**Architecture:** Keep the current SnackbarHostState for all app feedback. Give only save/delete success messages a typed, indefinite Snackbar request that the host dismisses after 1.5 seconds; render those messages as a compact floating pill. Add adaptive VectorDrawable launcher resources using the already approved ledger palette.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android VectorDrawable, adaptive launcher icons.

## Global Constraints

- Preserve the current local ledger, Git sync, and OSS sync behavior.
- Only record save/delete success notices use the 1.5-second compact style. Import, export, sync, and error notices keep their existing message and default short duration.
- Dismiss the exact SnackbarData instance shown by the host so an older timeout cannot dismiss a newer notice.
- Position the compact notice at bottom center, 20dp from the horizontal edges, 72dp above bottom navigation, and no wider than 360dp or the available width.
- Use the established palette: Canvas #F5F3EB, Surface #FFFDF8, Ink #1D2924, Green #2C6B57, Income #34775D, Expense #A84E37.
- Build the launcher icon from VectorDrawable/adaptive icon resources. Add no raster dependency, text, or gradients.
- Do not add or run automated tests for this request. Build the debug APK after implementation.

---

### Task 1: Add short floating feedback for record actions

**Files:**

- Modify: app/src/main/java/com/example/personalbookkeeping/ui/PersonalLedgerApp.kt
- Create: app/src/main/java/com/example/personalbookkeeping/ui/components/RecordFeedbackSnackbar.kt

**Interfaces:**

- Add a record-feedback presentation for the saved and deleted success messages.
- Keep the existing generic showMessage path for import, export, Git, OSS, and error messages.
- The composable feedback host receives SnackbarHostState and renders record feedback differently from standard SnackbarData.

- [ ] Create a compact pill with a semantic green check icon, warm surface, ink label, subtle divider, and rounded ends.
- [ ] Give the pill a content-sized layout capped at 360dp and 20dp horizontal padding.
- [ ] Add bottom padding so its content sits 72dp above the bottom navigation and clears the Home FAB.
- [ ] Mark save/delete requests as indefinite when enqueued, then use a Compose effect keyed to the displayed SnackbarData to dismiss that exact instance after 1,500 milliseconds.
- [ ] Change only the record-saved and record-deleted call sites to the short feedback path. Leave all other generic messages on the existing default duration.
- [ ] Confirm a queued generic error is not shortened by a previous record-feedback timeout.

### Task 2: Add an adaptive vector launcher icon

**Files:**

- Modify: app/src/main/AndroidManifest.xml
- Create: app/src/main/res/drawable/ic_launcher_foreground.xml
- Create: app/src/main/res/values/ic_launcher_background.xml
- Create: app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- Create: app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml

- [ ] Set android:icon and android:roundIcon to the new adaptive icon resources.
- [ ] Draw a warm-paper background, a deep-green ledger book with cream pages and simple ledger lines, and one terracotta check accent.
- [ ] Keep all important foreground shapes inside the adaptive icon safe zone so circle, squircle, and rounded-square masks retain the book and check.
- [ ] Use no letters, Chinese text, gradient, external image, or decorative chart.

### Task 3: Update the design note and build the Android APK

**Files:**

- Modify: DESIGN.md

- [ ] Record the 1.5-second record feedback placement and the adaptive icon palette in the design note.
- [ ] Build with Android Studio's Run action or Gradle task :app:assembleDebug.
- [ ] Confirm app/build/outputs/apk/debug/app-debug.apk is produced. Report compiler or packaging failures with their exact first diagnostics.

## Self-review

- Record notices are short without shortening unrelated status/errors.
- The snackbar instance-keyed timer avoids stale timeout races.
- The pill's bottom gap separates it from navigation and the Home FAB.
- Adaptive icon foreground details stay within the Android safe area and reuse existing colors.
- The change does not touch database schema or sync behavior.

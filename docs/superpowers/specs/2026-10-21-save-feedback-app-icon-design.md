# Save Feedback and App Icon Design

**Status:** The direction was approved in conversation; this written specification is ready for review.

## Goal

Make record-save feedback feel brief and well placed, then give Personal Ledger a recognizable Android launcher icon that matches its existing warm paper-ledger theme.

## Existing behavior

- PersonalLedgerApp.showMessage calls SnackbarHostState.showSnackbar(message) without a duration, so save feedback uses the Material 3 short-snackbar default.
- The SnackbarHost is attached to the main Scaffold, which also owns the bottom navigation and Home FAB.
- The application manifest does not declare a launcher icon; Android currently supplies the generic default.

## Approaches considered

1. **Compact floating success pill (recommended).** Keep the existing Snackbar state and error/info flow, render save and delete confirmations as a compact success pill, and dismiss each after 1.5 seconds. Place it centered above the bottom navigation with enough vertical clearance for the Home FAB.
2. **Top banner for all messages.** This avoids the navigation area but covers the month summary and adds movement near the page heading. Do not use it.
3. **Inline confirmation in the entry form.** The entry screen closes immediately after saving, so the confirmation would disappear with the form. Do not use it.

For the launcher icon, a ledger with a checkmark is preferred over a coin/chart mark or a receipt-only mark. It communicates personal bookkeeping without introducing extra visual language.

## Design

### Record feedback

- Saving or deleting a record shows a compact message with a green check icon and the appropriate Chinese text.
- The message stays visible for 1,500 milliseconds and then dismisses. Its host must dismiss the exact SnackbarData instance it rendered so an older timeout cannot close a newer message.
- Place the pill at bottom center, 20dp from the horizontal edges, and 72dp above the bottom navigation. Keep it content-sized, capped at 360dp, and narrower when the available width is smaller.
- Use a warm surface (#FFFDF8), ink text (#1D2924), semantic green check (#34775D), a subtle divider, and a pill-shaped radius. Avoid a wide dark snackbar or celebratory animation.
- Import, export, sync, and error notices keep the existing standard Snackbar duration and messages. A new record-save notice must not shorten a queued failure message.
- The notice must not cover the Home FAB or bottom navigation and must remain readable with system font scaling.

### Launcher icon

- Create an Android adaptive icon from VectorDrawable resources; no external image or font is required.
- Use a warm paper background (#F5F3EB), a deep green ledger cover (#2C6B57), cream paper (#FFFDF8), quiet ledger lines, and one terracotta check accent (#A84E37).
- Keep all key shapes inside the adaptive-icon safe area. Use no letters, Chinese text, gradients, or fine details that disappear at small sizes.
- Set both android:icon and android:roundIcon in the application manifest to the new launcher resources.

## Acceptance criteria

- Save and delete confirmation uses the compact pill and automatically disappears after 1.5 seconds.
- Other notices retain their current Snackbar behavior.
- Repeated record actions cannot let an older timeout dismiss a newer notice.
- The icon appears in the Android launcher as an adaptive icon and uses the existing app palette.
- The icon remains legible with circle, squircle, and rounded-square launcher masks.

## Technical notes

- Add a saved/deleted-specific Snackbar presentation in the existing Scaffold host; do not replace error/info messages with the short timeout.
- Use the SnackbarData dismiss API from a Compose effect keyed to the current data instance.
- Add foreground vector and background resources under app/src/main/res/mipmap-anydpi-v26 and app/src/main/res/drawable; wire them through AndroidManifest.xml.
- This is a visual and transient-message change. It does not change ledger persistence or network behavior.

## Self-review

- The selected message duration is exact and only applies to record save/delete success.
- Snackbar position and FAB clearance are explicit.
- The launcher icon uses the existing palette and declares both standard and round adaptive resources.
- No placeholders, schema changes, or external image assets are part of this design.

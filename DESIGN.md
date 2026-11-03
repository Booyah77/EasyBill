# Personal Ledger UI Direction

## Visual theme and atmosphere

A warm, paper-ledger interface with a quiet modern finish. The page feels personal and dependable, using ink-like typography, warm neutrals, and restrained green and terracotta accents. The layout favors fast reading and direct actions over decorative charts or card grids.

## Color palette and roles

| Token | Value | Role |
|---|---|---|
| Canvas | `#F5F3EB` | Warm page background |
| Surface | `#FFFDF8` | Entry form and focused panels |
| Ink | `#1D2924` | Main text and amounts |
| Muted ink | `#6E776F` | Labels and secondary information |
| Primary green | `#2C6B57` | Main actions and selected controls |
| Income green | `#34775D` | Income values and positive totals |
| Expense terracotta | `#A84E37` | Expense values and expense actions |
| Divider | `#E1DED3` | Quiet section boundaries |

Use the green and terracotta only for semantic meaning. Keep the canvas and surface distinct enough to separate navigation and focused input without adding texture images.

## Typography rules

- Use the Android system sans-serif for navigation, forms, and body copy; it keeps Chinese text readable across devices.
- Use the system serif face for the monthly balance and a small number of display values, creating a paper-ledger accent without bundling a font.
- Use tabular numerals for all amounts and totals.
- Keep headings sentence case and amounts aligned consistently.

## Component styling

- Use Material 3 Compose components with app-owned color and typography tokens.
- Prefer open sections and dividers; reserve a softly tinted surface for the month summary and the focused entry form.
- Use one 20dp panel radius, 12dp input radius, and pill-shaped category chips.
- Primary buttons use solid green with white labels. Expense actions use a terracotta-tinted surface; income actions use a green-tinted surface.
- Provide clear disabled, pressed, validation, success, and failure states. Touch targets remain at least 48dp.

## Layout principles

- Home order: month selector and balance, income/expense totals, two quick-entry actions, recent entries. Add a bottom-end “+ 记支出” extended FAB on the Home tab; it opens a fresh expense form directly.
- Entry order: income/expense switch, amount, common categories, date, optional note, save.
- Keep screens comfortable at 360dp width and support system font scaling. On wide screens, center content with a 600dp maximum width.
- Use a 4dp base spacing scale with 8, 12, 16, 24, and 32dp steps.

## Depth and elevation

- Use background-color steps and thin dividers to separate sections. Avoid repeated shadows.
- Reserve a subtle tonal surface change for the monthly summary and the quick-entry form.

## Do and don't

- Do put the amount and primary action near the thumb-friendly lower half of the entry flow.
- Do show a genuine empty state with a direct add-record action.
- Do show validation next to the field and explain import errors by row.
- Do give manual sync a visible status and preserve local access when Git is unconfigured.
- Don't add budgets, account dashboards, bank connections, or decorative charts to v1.
- Don't use a generic blue-purple gradient, repetitive card grids, or celebratory motion.

## Responsive behavior

- Support narrow Android phones and larger screens without changing the primary action order.
- Keep dialogs and sheets within safe insets; allow scrolling when font scaling or the keyboard reduces visible height.
- Use system back navigation consistently for entry, import preview, and conflict resolution.

## Interaction thesis

The main signature is amount-first entry with a large numeric field and common-category chips. Saving gives a short haptic confirmation and a snackbar; no animation blocks the next entry. Month switching and list filtering remain explicit and reversible.

## Record feedback and launcher icon

- Saving or deleting a record shows a compact warm-surface pill with an income-green check, ink text, and a quiet divider. It is centered above the bottom navigation with 20dp horizontal margins, 72dp vertical clearance, and a 360dp maximum width. The notice closes after 1,500ms. Import, export, sync, and error messages keep the standard Snackbar presentation and duration.
- The adaptive launcher icon uses a warm paper background (`#F5F3EB`), deep-green ledger (`#2C6B57`), cream pages (`#FFFDF8`), and a terracotta check (`#A84E37`). Its simple shapes stay within the adaptive icon safe area and contain no text or gradient.

## Product references

- Monefy's visible income/expense actions and category selection provide a quick-entry reference.
- Money Manager's bookmarked frequent entries and calendar/month views inform repeat-entry and history affordances.
- Wallet's configurable dashboard charts inform the monthly summary, while chart customization remains out of scope.

Sources: [Monefy Help Center](https://www.monefy.com/help-center), [Money Manager Home tab](https://help.realbyteapps.com/hc/en-us/articles/360046150874-Home-tab), [Wallet Getting Started](https://support.budgetbakers.com/hc/en-us/articles/7151352625938-Getting-Started-with-Wallet).

## Agent prompt guide

- **Home**: warm canvas `#F5F3EB`, ink `#1D2924`, serif monthly balance, income `#34775D`, expense `#A84E37`, open list rows separated by `#E1DED3`.
- **Quick entry**: amount-first layout, 12dp rounded input, pill category chips, primary green save button, always fit above the keyboard.
- **Ledger**: date-grouped rows, tabular amounts aligned to the end, semantic income/expense colors, filters in a compact month/type strip.
- **Settings**: plain grouped sections for file backup and Git sync, clear local-only status, no login screen.

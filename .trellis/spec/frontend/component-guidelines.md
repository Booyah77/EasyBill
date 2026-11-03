# Component Guidelines

> How components are built in this project.

---

## Overview

<!--
Document your project's component conventions here.

Questions to answer:
- What component patterns do you use?
- How are props defined?
- How do you handle composition?
- What accessibility standards apply?
-->

(To be filled by the team)

## Scenario: Short Save/Delete Feedback

### Scope / Trigger

Record creation and deletion need brief success feedback without shortening the longer default feedback used for imports, exports, sync, and errors.

### Contracts

- Generic messages continue through PersonalLedgerApp.showMessage(message) and the default Snackbar duration.
- Record save/delete success uses RecordFeedbackVisuals with SnackbarDuration.Indefinite. RecordFeedbackSnackbarHost owns the visual timeout and shows the compact pill for 1,500 ms.
- Place the pill at bottom center with 20dp horizontal padding, 72dp bottom clearance above the navigation bar, and max width 360dp.
- Use a SnackbarData-keyed Compose effect. Before dismissal, check hostState.currentSnackbarData === data so an older timeout cannot dismiss a later snackbar.
- Keep success colors semantic: warm surface, ink text, and the existing income-green check icon.

### Good / Base / Bad Cases

- Good: a saved or deleted record gets the compact pill; an import failure retains the regular Snackbar duration.
- Base: two record actions arrive close together; the queue displays each message and only its own timeout dismisses it.
- Bad: use delay followed by hostState.currentSnackbarData?.dismiss() without checking that it is the same SnackbarData instance.

### Tests Required

Future UI tests should verify that save/delete use the compact host, generic messages retain the default host, and an old timeout cannot dismiss a newer SnackbarData. No such tests are currently included.

### Wrong vs Correct

Wrong: shorten every Snackbar globally to make record-save feedback brief.

Correct: mark record success feedback as indefinite, render it with the compact success pill, and dismiss the exact displayed SnackbarData after 1,500 ms.

---

## Component Structure

<!-- Standard structure of a component file -->

(To be filled by the team)

---

## Props Conventions

<!-- How props should be defined and typed -->

(To be filled by the team)

---

## Styling Patterns

<!-- How styles are applied (CSS modules, styled-components, Tailwind, etc.) -->

(To be filled by the team)

---

## Accessibility

<!-- A11y requirements and patterns -->

(To be filled by the team)

---

## Common Mistakes

<!-- Component-related mistakes your team has made -->

(To be filled by the team)

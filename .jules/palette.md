## 2024-05-18 - Jetpack Compose Accessible Checkbox Rows
**Learning:** When adding click behavior to a Row containing a Checkbox, using `combinedClickable` splits semantics, causing screen readers to announce the row and the checkbox separately. This is confusing for users navigating via TalkBack.
**Action:** Always use the `toggleable(role = Role.Checkbox)` modifier on the parent container (e.g., Row) and set the child Checkbox's `onCheckedChange` to null to correctly merge semantics into a single accessible component.

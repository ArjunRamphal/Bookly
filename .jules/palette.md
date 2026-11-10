## 2024-05-18 - Jetpack Compose Accessible Checkbox Rows
**Learning:** When adding click behavior to a Row containing a Checkbox, using `combinedClickable` splits semantics, causing screen readers to announce the row and the checkbox separately. This is confusing for users navigating via TalkBack.
**Action:** Always use the `toggleable(role = Role.Checkbox)` modifier on the parent container (e.g., Row) and set the child Checkbox's `onCheckedChange` to null to correctly merge semantics into a single accessible component.

## 2024-05-19 - Compose AlertDialog Inline Validation
**Learning:** When using inputs (`OutlinedTextField`) within Compose `AlertDialog`s, disabling the primary action button without context leads to a poor user experience. Users may not understand why they cannot proceed.
**Action:** Always provide inline validation feedback using `isError` and `supportingText` in the text field to clearly explain why the input is invalid, alongside disabling the action button.

## 2024-05-20 - Redundant Screen Reader Announcements for Decorative Icons
**Learning:** Adding a `contentDescription` to icons that are adjacent to descriptive text (e.g., in a `DropdownMenuItem` or a `Button` with a text label) causes screen readers like TalkBack to read the description twice (once for the icon, once for the text).
**Action:** Always set `contentDescription = null` for decorative icons that are paired with visible, descriptive text to prevent redundant announcements and improve the screen reader experience.

## 2024-05-22 - Missing Screen Reader Context on Generic Interactables
**Learning:** Using the generic `clickable` modifier on layout components (like `Box` or `Row`) without providing accessibility semantics leaves screen reader users without context. TalkBack will announce it as generic text that can be clicked, but won't explain the action or role.
**Action:** Always supplement `clickable` modifiers on generic components with an explicit `onClickLabel` and `role` (e.g., `Role.Button`) to ensure proper context and expectations for screen reader users.

## 2024-05-23 - Jetpack Compose Color Contrast and Dark Mode Compatibility
**Learning:** Using fixed colors (like `Color.LightGray` and `Color.White`) for UI components (like placeholders) can lead to poor contrast in certain themes (e.g., Light Mode) and complete invisibility or visual bugs in Dark Mode.
**Action:** Always use semantic theme colors (e.g., `MaterialTheme.colorScheme.surfaceVariant` for backgrounds and `MaterialTheme.colorScheme.onSurfaceVariant` for text/icons on that background) to ensure accessible contrast ratios and automatic adaptation across all device themes.

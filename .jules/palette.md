## 2024-11-03 - Accessible Toggleable Containers in Compose
**Learning:** When building custom toggleable rows (like a Row containing a Checkbox), using `combinedClickable` on the Row and keeping the Checkbox interactive results in split or confusing semantics for screen readers (like TalkBack).
**Action:** Use `toggleable(role = Role.Checkbox)` on the parent container (e.g., Row) and set the child Checkbox`s `onCheckedChange` to `null`. This correctly merges the semantics, ensuring the screen reader announces the entire row as a single interactive checkbox.

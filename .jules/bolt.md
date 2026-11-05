## 2024-05-24 - Compose Lazy Lists require stable keys
**Learning:** By default Jetpack Compose does not assign stable identities to items in a `LazyColumn` or `LazyVerticalGrid`. This can cause unnecessary recomposition of visible items when the underlying list changes.
**Action:** Always provide the `key` parameter to `items()` calls in Compose to ensure proper item identity tracking and optimized rendering.

## 2024-05-24 - O(N*M) bottlenecks during directory scanning
**Learning:** Checking for string matches within a loop using `.any {}` on a list of objects or `.contains()` on a List results in O(N*M) time complexity. Also allocating new instances of `listOf` on each iteration is unnecessarily wasteful.
**Action:** Convert lists to `Set` before loops to achieve O(1) lookups and O(N) overall complexity. Declare static sets of constant primitives outside the loop.

## 2024-05-24 - String manipulation bottlenecks during text parsing
**Learning:** Chaining multiple `.replace()` calls on a large string (like book content) requires multiple passes and allocates multiple large intermediate strings in memory, leading to high CPU usage and garbage collection overhead.
**Action:** Replace multiple `.replace()` calls on large strings with a single-pass `StringBuilder` loop to minimize memory allocation and processing time.

## 2024-05-24 - HTML string replacement bottleneck
**Learning:** Using chained or looped `String.replace()` operations on large strings like HTML content from EPUBs causes $O(N \cdot M)$ time complexity and massive memory/GC overhead because strings are immutable in Kotlin/Java.
**Action:** Always use `Matcher.appendReplacement()` with a `StringBuffer` or a single-pass `StringBuilder` loop when performing multiple substring replacements in a large body of text.

## 2026-11-05 - Optimize high-frequency state updates with derivedStateOf
**Learning:** In Jetpack Compose, avoiding unnecessary recompositions during continuous events like scrolling is critical. Passing a highly variable state like a Float directly into a UI component that only needs an Int causes the component to recompose on every tiny change, even when the UI output remains identical.
**Action:** Always use `derivedStateOf` to convert a highly fluctuating state (like a raw scroll position or float progress) into a less frequently changing state (like an integer percentage). Read only the derived state in UI components.

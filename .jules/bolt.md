## 2024-05-24 - Compose Lazy Lists require stable keys
**Learning:** By default Jetpack Compose does not assign stable identities to items in a `LazyColumn` or `LazyVerticalGrid`. This can cause unnecessary recomposition of visible items when the underlying list changes.
**Action:** Always provide the `key` parameter to `items()` calls in Compose to ensure proper item identity tracking and optimized rendering.

## 2024-05-24 - O(N*M) bottlenecks during directory scanning
**Learning:** Checking for string matches within a loop using `.any {}` on a list of objects or `.contains()` on a List results in O(N*M) time complexity. Also allocating new instances of `listOf` on each iteration is unnecessarily wasteful.
**Action:** Convert lists to `Set` before loops to achieve O(1) lookups and O(N) overall complexity. Declare static sets of constant primitives outside the loop.

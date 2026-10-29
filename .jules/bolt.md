## 2024-05-24 - Compose Lazy Lists require stable keys
**Learning:** By default Jetpack Compose does not assign stable identities to items in a `LazyColumn` or `LazyVerticalGrid`. This can cause unnecessary recomposition of visible items when the underlying list changes.
**Action:** Always provide the `key` parameter to `items()` calls in Compose to ensure proper item identity tracking and optimized rendering.

## 2024-05-24 - Compose Lazy Lists require stable keys
**Learning:** By default Jetpack Compose does not assign stable identities to items in a `LazyColumn` or `LazyVerticalGrid`. This can cause unnecessary recomposition of visible items when the underlying list changes.
**Action:** Always provide the `key` parameter to `items()` calls in Compose to ensure proper item identity tracking and optimized rendering.

## 2024-05-19 - [O(N²) Bottleneck in Folder Imports]
**Learning:** During folder scans, repeatedly checking lists (`existingBooks.any { it.title == ... }` and `deletedFiles.contains(...)`) for every file causes an O(N*M) performance bottleneck, resulting in sluggish UI or slow imports for large directories.
**Action:** Always map lists to `HashSet`s before iterating over external files to ensure O(1) lookups and O(N+M) total complexity.

## 2024-10-28 - Fix Path Traversal Vulnerabilities
**Vulnerability:** Found Path Traversal vulnerability when importing books/fonts where the app retrieves the filename from the content provider using `OpenableColumns.DISPLAY_NAME`. If a malicious content provider returned a filename like `../../shared_prefs/prefs.xml`, it could overwrite files in the app's internal directory.
**Learning:** External intents and content providers cannot be trusted. Even basic file import functions require directory validation if they rely on metadata from outside the app container.
**Prevention:** Always sanitize incoming paths with `File(rawName).name` and validate the destination directory using `canonicalPath.startsWith(safeDirectory.canonicalPath)`.

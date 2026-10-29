## 2025-05-18 - Path Traversal in Document Picker
**Vulnerability:** The application blindly trusts the `OpenableColumns.DISPLAY_NAME` from imported files (fonts, books) which can be manipulated by malicious document providers to perform path traversal (Zip Slip variant).
**Learning:** External intents and content providers can provide arbitrary filenames.
**Prevention:** Always sanitize filenames from `OpenableColumns.DISPLAY_NAME` using `File(rawName).name`.

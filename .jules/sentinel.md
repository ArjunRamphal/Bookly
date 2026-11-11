## 2024-10-28 - Fix Path Traversal Vulnerabilities
**Vulnerability:** Found Path Traversal vulnerability when importing books/fonts where the app retrieves the filename from the content provider using `OpenableColumns.DISPLAY_NAME`. If a malicious content provider returned a filename like `../../shared_prefs/prefs.xml`, it could overwrite files in the app's internal directory.
**Learning:** External intents and content providers cannot be trusted. Even basic file import functions require directory validation if they rely on metadata from outside the app container.
**Prevention:** Always sanitize incoming paths with `File(rawName).name` and validate the destination directory using `canonicalPath.startsWith(safeDirectory.canonicalPath)`.

## 2024-10-30 - Prevent WebView Local File Exfiltration
**Vulnerability:** Found `settings.allowFileAccess = true` enabled in the eBook Reader's WebView to supposedly allow loading fonts from local storage. This is a critical security vulnerability that allows an attacker to load a malicious file (e.g., EPUB containing malicious HTML/JS) which then reads sensitive files from the app's internal storage (`file://`) and exfiltrates them via JavaScript.
**Learning:** Never enable `allowFileAccess` or `allowFileAccessFromFileURLs` in WebView if the WebView is rendering content that is potentially untrusted or can execute JavaScript. Even if the intention is benign (e.g. loading local fonts or assets), it introduces path traversal and file exfiltration risks.
**Prevention:** Explicitly disable local file access by setting both `settings.allowFileAccess = false` and `settings.allowFileAccessFromFileURLs = false` for any WebView handling untrusted input. If local assets like fonts need to be provided, use `WebViewAssetLoader` or a custom `ContentProvider` instead.

## 2024-05-24 - [EPUB XSS Vulnerability]
**Vulnerability:** The WebView in `EpubReaderScreen` was vulnerable to Stored XSS because `settings.javaScriptEnabled = true` is required for Kotlin->JS eval (like dark mode and scrolling), but the loaded EPUB/HTML content was not restricted. An attacker could embed malicious `<script>` tags in an EPUB file to execute scripts in the WebView context.
**Learning:** Android's `WebView.evaluateJavascript` bypasses page-level Content Security Policy (CSP). This means you can inject a strict CSP (`script-src 'none'`) into the HTML `<head>` to neutralize any embedded scripts, while still retaining the ability to safely execute UI-related scripts from the Android host.
**Prevention:** Always inject a strict CSP (`script-src 'none'`) when loading user-provided HTML content in a WebView, and explicitly set `settings.allowUniversalAccessFromFileURLs = false` to provide defense-in-depth against file exfiltration.

## 2024-05-24 - [Fix Path Traversal in WebViewAssetLoader]
**Vulnerability:** WebViewAssetLoader was exposing the entire application `filesDir` via `/internal_dir/` when only custom fonts needed to be loaded.
**Learning:** `InternalStoragePathHandler` allows local file access via standard web URLs. If exposed too broadly, an attacker who can inject content (e.g. malicious EPUBs) could potentially read any file in the app's internal storage.
**Prevention:** Always restrict `WebViewAssetLoader` path handlers to the most specific subdirectory required (e.g. `File(context.filesDir, "fonts")`). Use least privilege principles for local assets.

## 2025-02-09 - Prevent WebView Intent Injection and Unwanted External Requests
**Vulnerability:** The WebView in `EpubReaderScreen` did not override `shouldOverrideUrlLoading` or block unauthorized HTTP/HTTPS requests in `shouldInterceptRequest`. An attacker could embed a malicious link in an EPUB file (e.g., `intent://`, `tel:`, or an external malicious URL) which, if clicked or automatically loaded, could trigger unintended intents or leak information (SSRF/tracking).
**Learning:** By default, Android WebViews may attempt to handle custom URL schemes by launching intents, which can lead to Intent Injection vulnerabilities if the content is untrusted. Additionally, allowing the WebView to make arbitrary external network requests from untrusted HTML can lead to privacy leaks or SSRF.
**Prevention:** Always override `shouldOverrideUrlLoading` to explicitly block or handle only specific, safe URLs (like an internal `baseUrl`). Furthermore, in `shouldInterceptRequest`, intercept and block any unexpected HTTP/HTTPS requests (e.g., by returning an empty `WebResourceResponse`) to enforce that only local assets are loaded.
## 2024-10-31 - [HTML/CSS Injection via Unsanitized Filenames]
**Vulnerability:** Found HTML/CSS injection vulnerability when importing custom fonts and books. The app trusted `OpenableColumns.DISPLAY_NAME` from external content providers and directly injected the selected font filename into a CSS `@font-face` block without sanitization. An attacker could craft a malicious filename like `dummy'); } </style><script>alert(1)</script><style>/*.ttf` to break out of the CSS block.
**Learning:** Android's `OpenableColumns.DISPLAY_NAME` is fully controlled by the providing app and cannot be trusted. Even if path traversal is prevented via `File(name).name`, the filename can still contain characters that are unsafe when rendered or interpolated dynamically in contexts like HTML/CSS.
**Prevention:** Always sanitize filenames from external intents/providers using a strict allowlist regex (e.g., `[^a-zA-Z0-9.\-_]`) to strip unsafe characters before processing, displaying, or saving them.

# Bookly

![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)

Bookly is a lightweight, modern eBook reader for Android built entirely with **Kotlin** and **Jetpack Compose**. It is designed to handle large EPUBs, PDFs, and TXT files efficiently while providing a smooth, customizable reading experience.

## Features

### Reading Experience
* **Multi-Format Support:** Read **EPUB**, **PDF**, and **TXT** files seamlessly.
* **Gesture Navigation:**
    * **Overscroll Up** at the bottom of a page to go to the **Next Chapter**.
    * **Overscroll Down** at the top of a page to go to the **Previous Chapter**.
* **Jump to Chapter:** Click the "Ch X / Y" text to directly input a chapter number.
* **Customization:** Toggle **Dark/Light Mode** and adjust **Font Size** on the fly.
* **Precise Progress:** Saves your exact reading position automatically.

### Library Management
* **Automatic Import:** Select a folder on your device to automatically sync books on startup.
* **Cover Extraction:** Automatically extracts and caches cover art for EPUBs.

## Tech Stack

* **Language:** Kotlin
* **UI:** Jetpack Compose (Material 3)
* **Parsing:**
    * Epublib (for EPUB structure)
    * PdfRenderer (Native Android PDF support)
    * WebView (for rendering HTML/CSS content)
* **Architecture:** MVVM (Model-View-ViewModel)
* **Coroutines:** For background file parsing and IO operations.

## License

This project is licensed under the MIT License.

package com.example.bookly.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.epub.EpubReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.regex.Pattern

object EpubUtils {

    // --- HELPER: OPEN BOOK ---
    private fun getBook(context: Context, uri: Uri): Book? {
        // Attempt 1: Standard Read (UTF-8)
        try {
            val inputStream = getInputStream(context, uri) ?: return null
            return EpubReader().readEpub(inputStream)
        } catch (e: Exception) {
            // Log first failure (might be encoding issue)
            e.printStackTrace()
        }

        // Attempt 2: Fallback Encoding (ISO-8859-1)
        // Fixes "Malformatted Input" errors in older EPUBs
        try {
            val inputStream = getInputStream(context, uri) ?: return null
            return EpubReader().readEpub(inputStream, "ISO-8859-1")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    // --- 1. GET CHAPTER LIST ---
    fun getChapters(context: Context, uri: Uri): List<String> {
        val book = getBook(context, uri) ?: return emptyList()
        return book.spine.spineReferences.mapNotNull { it.resource?.href }
    }

    // --- 2. LOAD SINGLE CHAPTER ---
    fun loadChapter(context: Context, uri: Uri, chapterIndex: Int): String {
        val book = getBook(context, uri) ?: return "<h3>Error: Could not open book.</h3>"

        if (chapterIndex < 0 || chapterIndex >= book.spine.spineReferences.size) {
            return "<h3>End of Book</h3>"
        }

        val spineRef = book.spine.spineReferences[chapterIndex]
        val resource = spineRef.resource ?: return "<h3>Error: Chapter not found.</h3>"

        // Robust read: default to UTF-8, fallback if needed
        val html = try {
            String(resource.data, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            return "<h3>Error reading chapter encoding.</h3>"
        }

        val injectedBody = injectImages(html, book, resource.href)

        return """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    html, body {
                        width: 100%; overflow-x: hidden; margin: 0; padding: 16px;
                        word-wrap: break-word; line-height: 1.6;
                    }
                    * { max-width: 100% !important; box-sizing: border-box !important; }
                    img, svg, video { height: auto !important; display: block; margin: 10px auto; }
                    hr { width: 100% !important; height: 1px; border: none; background-color: #ccc; }
                    table { display: block; width: 100% !important; overflow-x: auto; }
                </style>
            </head>
            <body>
                $injectedBody
                <div style="height: 50px;"></div>
            </body>
            </html>
        """.trimIndent()
    }

    // --- 3. PARSE TXT ---
    fun parseTxt(context: Context, uri: Uri): String {
        return try {
            val inputStream = getInputStream(context, uri) ?: return "<h3>Error: Cannot open file</h3>"
            val rawText = inputStream.bufferedReader().use { it.readText() }

            val safeText = rawText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

            """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    html, body { width: 100%; margin: 0; padding: 16px; word-wrap: break-word; font-family: sans-serif; }
                    pre { white-space: pre-wrap; font-family: inherit; margin: 0; }
                </style>
            </head>
            <body><pre>$safeText</pre></body>
            </html>
            """.trimIndent()
        } catch (e: Exception) {
            e.printStackTrace()
            "<h3>Error reading text file: ${e.localizedMessage}</h3>"
        }
    }

    // --- 4. EXTRACT COVER ---
    fun extractCoverImage(context: Context, uri: Uri, saveName: String): String? {
        return try {
            val book = getBook(context, uri) ?: return null
            val coverImage = book.coverImage ?: return null

            val safeName = saveName.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val fileName = "${safeName}_cover.jpg"
            val file = File(context.filesDir, fileName)

            FileOutputStream(file).use { it.write(coverImage.data) }
            "file://${file.absolutePath}"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- HELPERS ---
    private fun injectImages(html: String, book: Book, currentHref: String): String {
        var modifiedHtml = html
        val pattern = Pattern.compile("src=[\"']([^\"']+)[\"']")
        val matcher = pattern.matcher(html)
        val replacements = mutableMapOf<String, String>()

        while (matcher.find()) {
            val originalSrc = matcher.group(1) ?: continue
            if (originalSrc.startsWith("http") || originalSrc.startsWith("data:")) continue

            val decodedSrc = try { URLDecoder.decode(originalSrc, "UTF-8") } catch (e: Exception) { originalSrc }
            val resolvedHref = resolveRelativePath(currentHref, decodedSrc)
            val imageResource = findResourceRobust(book, resolvedHref)

            if (imageResource != null) {
                try {
                    val b64 = Base64.encodeToString(imageResource.data, Base64.NO_WRAP)
                    val mimeType = when {
                        resolvedHref.endsWith(".png", true) -> "image/png"
                        resolvedHref.endsWith(".gif", true) -> "image/gif"
                        resolvedHref.endsWith(".svg", true) -> "image/svg+xml"
                        else -> "image/jpeg"
                    }
                    replacements[originalSrc] = "data:$mimeType;base64,$b64"
                } catch (e: OutOfMemoryError) {
                    // Skip image if OOM
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        for ((oldSrc, newSrc) in replacements) {
            modifiedHtml = modifiedHtml.replace("src=\"$oldSrc\"", "src=\"$newSrc\"")
            modifiedHtml = modifiedHtml.replace("src='$oldSrc'", "src='$newSrc'")
        }
        return modifiedHtml
    }

    private fun findResourceRobust(book: Book, href: String): Resource? {
        var res = book.resources.getByHref(href)
        if (res != null) return res

        val decodedHref = try { URLDecoder.decode(href, "UTF-8") } catch (e: Exception) { href }
        res = book.resources.getByHref(decodedHref)
        if (res != null) return res

        for ((key, value) in book.resources.resourceMap) {
            if (key.equals(href, ignoreCase = true) || key.equals(decodedHref, ignoreCase = true)) {
                return value
            }
        }
        return null
    }

    // --- SAFE INPUT STREAM ---
    private fun getInputStream(context: Context, uri: Uri): InputStream? {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                FileInputStream(File(path))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun resolveRelativePath(baseHref: String, relativePath: String): String {
        if (!relativePath.contains("../") && !relativePath.contains("./")) {
            val folder = baseHref.substringBeforeLast("/", "")
            return if (folder.isEmpty()) relativePath else "$folder/$relativePath"
        }
        val parts = (baseHref.substringBeforeLast("/", "") + "/" + relativePath).split("/")
        val stack = java.util.Stack<String>()
        for (part in parts) {
            when (part) {
                "..", "..." -> if (stack.isNotEmpty()) stack.pop()
                ".", "" -> {}
                else -> stack.push(part)
            }
        }
        return stack.joinToString("/")
    }
}
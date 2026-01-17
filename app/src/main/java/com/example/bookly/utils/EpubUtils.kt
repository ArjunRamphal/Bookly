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
        try {
            val inputStream = getInputStream(context, uri) ?: return null
            return EpubReader().readEpub(inputStream)
        } catch (e: Exception) { e.printStackTrace() }

        try {
            val inputStream = getInputStream(context, uri) ?: return null
            return EpubReader().readEpub(inputStream, "ISO-8859-1")
        } catch (e: Exception) { e.printStackTrace() }

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
                    /* FIX: Ensure padding doesn't overflow width */
                    * { max-width: 100% !important; box-sizing: border-box !important; }
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

    // --- 4. PARSE RTF ---
    fun parseRtf(context: Context, uri: Uri): String {
        return try {
            val inputStream = getInputStream(context, uri) ?: return "<h3>Error: Cannot open file</h3>"
            val rawRtf = inputStream.bufferedReader(Charset.forName("ISO-8859-1")).use { it.readText() }

            val bodyText = convertRtfToHtml(rawRtf)

            """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    /* FIX: Ensure padding doesn't overflow width */
                    * { max-width: 100% !important; box-sizing: border-box !important; }
                    
                    html, body { width: 100%; margin: 0; padding: 16px; word-wrap: break-word; font-family: sans-serif; line-height: 1.6; }
                    p { margin-bottom: 1em; }
                    img { max-width: 100%; height: auto; display: block; margin: 10px auto; }
                </style>
            </head>
            <body>$bodyText</body>
            </html>
            """.trimIndent()
        } catch (e: Exception) {
            e.printStackTrace()
            "<h3>Error reading RTF file: ${e.localizedMessage}</h3>"
        }
    }

    // --- RTF PARSER ENGINE ---
    private fun convertRtfToHtml(rtf: String): String {
        val result = StringBuilder()
        val len = rtf.length
        var i = 0

        var groupLevel = 0
        var ignoreLevel = -1

        // Image Processing State
        var isPictureGroup = false
        var pictureHex = StringBuilder()
        var pictureGroupLevel = -1

        while (i < len) {
            val c = rtf[i]

            when (c) {
                '{' -> {
                    groupLevel++
                    if (i + 1 < len && rtf[i + 1] == '\\') {
                        if (i + 2 < len && rtf[i+2] == '*') {
                            if (ignoreLevel == -1) ignoreLevel = groupLevel
                        } else {
                            val peek = rtf.substring(i + 1, (i + 20).coerceAtMost(len))

                            // 1. Detect Picture Group
                            if (peek.startsWith("\\pict")) {
                                isPictureGroup = true
                                pictureGroupLevel = groupLevel
                                pictureHex.clear()
                            }
                            // 2. Ignore other metadata
                            else if (peek.startsWith("\\fonttbl") ||
                                peek.startsWith("\\colortbl") ||
                                peek.startsWith("\\stylesheet") ||
                                peek.startsWith("\\info") ||
                                peek.startsWith("\\object") ||
                                peek.startsWith("\\header") ||
                                peek.startsWith("\\footer")) {

                                if (ignoreLevel == -1) ignoreLevel = groupLevel
                            }
                        }
                    }
                }
                '}' -> {
                    // 3. Process Picture on Close
                    if (isPictureGroup && groupLevel == pictureGroupLevel) {
                        isPictureGroup = false
                        pictureGroupLevel = -1

                        // Convert Hex -> Base64 -> HTML
                        if (pictureHex.isNotEmpty()) {
                            try {
                                val bytes = hexStringToByteArray(pictureHex.toString())
                                if (bytes.isNotEmpty()) {
                                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                    result.append("<img src=\"data:image/jpeg;base64,$b64\" />")
                                }
                            } catch (e: Exception) {
                                result.append("[Image Error]")
                            }
                        }
                    }

                    if (ignoreLevel == groupLevel) ignoreLevel = -1
                    if (groupLevel > 0) groupLevel--
                }
                '\\' -> {
                    if (i + 1 < len) {
                        var end = i + 1
                        val nextC = rtf[end]

                        if (!nextC.isLetter()) {
                            // Escaped chars
                            if (nextC == '\'') {
                                if (!isPictureGroup && ignoreLevel == -1 && i + 3 < len) {
                                    try {
                                        val hex = rtf.substring(i + 2, i + 4)
                                        result.append(hex.toInt(16).toChar())
                                        i += 3
                                    } catch (e: Exception) {}
                                } else {
                                    i++ // Skip ' in picture mode too
                                }
                            } else if (!isPictureGroup && ignoreLevel == -1 && (nextC == '{' || nextC == '}' || nextC == '\\')) {
                                result.append(nextC)
                                i++
                            } else {
                                i++
                            }
                        } else {
                            // Control Words (\par, \pict, etc)
                            while (end < len && rtf[end].isLetter()) end++
                            val word = rtf.substring(i + 1, end)

                            // Consume params
                            var paramStart = end
                            if (end < len && rtf[end] == '-') end++
                            while (end < len && rtf[end].isDigit()) end++

                            if (end < len && rtf[end] == ' ') end++

                            if (!isPictureGroup && ignoreLevel == -1) {
                                when (word) {
                                    "par" -> result.append("<br><br>")
                                    "line" -> result.append("<br>")
                                    "tab" -> result.append("&emsp;")
                                    "b" -> result.append("<b>")
                                    "b0" -> result.append("</b>")
                                    "i" -> result.append("<i>")
                                    "i0" -> result.append("</i>")
                                }
                            }

                            i = end - 1
                        }
                    }
                }
                '\r', '\n' -> {}
                else -> {
                    if (isPictureGroup) {
                        // 4. Capture Hex Data
                        if (c.isDigit() || (c in 'a'..'f') || (c in 'A'..'F')) {
                            pictureHex.append(c)
                        }
                    } else if (ignoreLevel == -1 && c != '\u0000') {
                        result.append(c)
                    }
                }
            }
            i++
        }
        return result.toString()
    }

    // --- HEX CONVERTER ---
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        // Filter odd length (rare edge case in broken RTF)
        val safeLen = if (len % 2 != 0) len - 1 else len
        val data = ByteArray(safeLen / 2)

        var i = 0
        var j = 0
        while (i < safeLen) {
            val high = Character.digit(s[i], 16)
            val low = Character.digit(s[i + 1], 16)
            if (high == -1 || low == -1) {
                // Should not happen due to pre-filtering, but safety check
                i += 2
                continue
            }
            data[j] = ((high shl 4) + low).toByte()
            i += 2
            j++
        }
        return data
    }

    // --- 5. EXTRACT COVER ---
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
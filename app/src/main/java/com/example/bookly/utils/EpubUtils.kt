package com.example.bookly.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import java.io.InputStream
import java.net.URLDecoder
import java.util.regex.Pattern

object EpubUtils {

    fun parseEpub(context: Context, uri: Uri): String {
        return try {
            val inputStream: InputStream? = if (uri.scheme == "file") {
                java.io.FileInputStream(java.io.File(uri.path!!))
            } else {
                context.contentResolver.openInputStream(uri)
            }

            // 1. Read the Book
            val book: Book = EpubReader().readEpub(inputStream)
            val stringBuilder = StringBuilder()

            // 2. Global CSS - UPDATED WITH SCREEN WIDTH FIXES
            // We use 'max-width: 100% !important' on everything to prevent wide elements from breaking layout.
            stringBuilder.append(
                """
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    /* Force the document to fit the screen width */
                    html, body {
                        width: 100%;
                        overflow-x: hidden; /* Cut off anything that is too wide */
                        margin: 0;
                        padding: 16px; /* Add comfortable reading padding */
                        word-wrap: break-word; /* Ensure long words don't push width */
                        line-height: 1.6;
                    }

                    /* THE "HAMMER": Force every single element to respect screen width */
                    * {
                        max-width: 100% !important;
                        box-sizing: border-box !important;
                    }

                    /* Fix for Images */
                    img, svg, video {
                        height: auto !important;
                        display: block;
                        margin: 10px auto;
                    }

                    /* Fix for Lines (The issue you saw) */
                    hr {
                        width: 100% !important;
                        height: 1px;
                        border: none;
                        background-color: #ccc;
                    }

                    /* Fix for Tables */
                    table {
                        display: block;
                        width: 100% !important;
                        overflow-x: auto; /* Let the table scroll internally if needed */
                    }
                </style>
                """.trimIndent()
            )

            // 3. Add Cover Image
            if (book.coverImage != null) {
                try {
                    val b64 = Base64.encodeToString(book.coverImage.data, Base64.NO_WRAP)
                    stringBuilder.append(
                        "<div style='text-align:center; margin-bottom: 20px;'>" +
                                "<img src='data:image/jpeg;base64,$b64'>" +
                                "</div>"
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 4. Iterate over Spine (Chapters)
            for (spineRef in book.spine.spineReferences) {
                val resource = spineRef.resource ?: continue

                // Read text (handles charset automatically)
                val html = resource.reader.readText()

                // Resolve relative paths
                val injectedHtml = injectImages(html, book, resource.href)

                stringBuilder.append(injectedHtml)

                // Add a small spacer between chapters
                stringBuilder.append("<br><br>")
            }

            val fullContent = stringBuilder.toString()
            if (fullContent.isEmpty()) "<h3>No content found in this EPUB.</h3>" else fullContent

        } catch (e: Exception) {
            e.printStackTrace()
            "<h3>Error parsing EPUB: ${e.localizedMessage}</h3>"
        }
    }

    private fun injectImages(html: String, book: Book, currentHref: String): String {
        var modifiedHtml = html

        val pattern = Pattern.compile("src=[\"']([^\"']+)[\"']")
        val matcher = pattern.matcher(html)
        val replacements = mutableMapOf<String, String>()

        while (matcher.find()) {
            val originalSrc = matcher.group(1) ?: continue

            val decodedSrc = try {
                URLDecoder.decode(originalSrc, "UTF-8")
            } catch (e: Exception) { originalSrc }

            val resolvedHref = resolveRelativePath(currentHref, decodedSrc)
            val imageResource = book.resources.getByHref(resolvedHref)

            if (imageResource != null) {
                try {
                    val b64 = Base64.encodeToString(imageResource.data, Base64.NO_WRAP)

                    val mimeType = when {
                        resolvedHref.endsWith(".png", true) -> "image/png"
                        resolvedHref.endsWith(".gif", true) -> "image/gif"
                        resolvedHref.endsWith(".svg", true) -> "image/svg+xml"
                        else -> "image/jpeg"
                    }

                    val newSrc = "data:$mimeType;base64,$b64"
                    replacements[originalSrc] = newSrc
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        for ((oldSrc, newSrc) in replacements) {
            modifiedHtml = modifiedHtml.replace("src=\"$oldSrc\"", "src=\"$newSrc\"")
            modifiedHtml = modifiedHtml.replace("src='$oldSrc'", "src='$newSrc'")
        }

        return modifiedHtml
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
package com.example.bookly.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookly.data.BookDatabase
import com.example.bookly.data.BookEntity
import com.example.bookly.data.BookRepository
import com.example.bookly.utils.EpubUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream

// 1. Define Sort Options
enum class SortOption {
    ALPHABETICAL,
    DATE_ADDED_DESC,
    DATE_ADDED_ASC
}

class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BookRepository

    // --- SEARCH & SORT STATE ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.ALPHABETICAL)
    val sortOption = _sortOption.asStateFlow()

    // --- FILTERED & SORTED BOOKS ---
    val libraryBooks: StateFlow<List<BookEntity>>

    private val prefs = application.getSharedPreferences("bookly_prefs", Context.MODE_PRIVATE)
    private val _importFolder = MutableStateFlow<String?>(prefs.getString("import_folder", null))
    val importFolder = _importFolder.asStateFlow()

    init {
        val bookDao = BookDatabase.getDatabase(application).bookDao()
        repository = BookRepository(bookDao)

        // Combine books, search, and sort
        libraryBooks = combine(repository.allBooks, _searchQuery, _sortOption) { books, query, sort ->
            // 1. Filter
            val filtered = if (query.isBlank()) {
                books
            } else {
                books.filter { book ->
                    book.title.contains(query, ignoreCase = true)
                }
            }

            // 2. Sort
            when (sort) {
                SortOption.ALPHABETICAL -> filtered.sortedBy { it.title.lowercase() }
                SortOption.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateAdded }
                SortOption.DATE_ADDED_ASC -> filtered.sortedBy { it.dateAdded }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Scan on startup
        _importFolder.value?.let { uriString ->
            try {
                scanImportFolder(Uri.parse(uriString))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var showDuplicateDialog by mutableStateOf(false)
        private set

    private var pendingUri: Uri? = null
    private var pendingTitle: String = ""

    // --- ACTIONS ---
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSortOptionChanged(option: SortOption) {
        _sortOption.value = option
    }

    fun setImportFolder(uri: Uri) {
        val context = getApplication<Application>()
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)

        prefs.edit().putString("import_folder", uri.toString()).apply()
        _importFolder.value = uri.toString()
        scanImportFolder(uri)
    }

    private fun scanImportFolder(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val folder = DocumentFile.fromTreeUri(context, treeUri) ?: return@launch
            val files = folder.listFiles()

            val existingBooks = repository.allBooks.first()
            val deletedFiles = repository.getDeletedFileNames()

            for (file in files) {
                if (file.isDirectory) continue
                val name = file.name ?: continue
                val ext = name.substringAfterLast(".", "").lowercase()

                if (ext !in listOf("pdf", "epub", "txt", "rtf")) continue

                // Check ignore list
                if (deletedFiles.contains(name)) continue

                // Check duplicates
                val title = name.substringBeforeLast(".")
                if (existingBooks.any { it.title.equals(title, ignoreCase = true) }) continue

                // Import
                finalizeImport(file.uri, name, allowOverwrite = false)
            }
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            var fileName = "Unknown_Book"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            val title = fileName.substringBeforeLast(".")

            val existingBooks = repository.allBooks.first()
            val isDuplicate = existingBooks.any { it.title == title }

            if (isDuplicate) {
                pendingUri = uri
                pendingTitle = fileName
                showDuplicateDialog = true
            } else {
                finalizeImport(uri, fileName, allowOverwrite = false)
            }
        }
    }

    fun confirmImport() {
        if (pendingUri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                finalizeImport(pendingUri!!, pendingTitle, allowOverwrite = true)
                pendingUri = null
                pendingTitle = ""
                showDuplicateDialog = false
            }
        }
    }

    fun cancelImport() {
        pendingUri = null
        pendingTitle = ""
        showDuplicateDialog = false
    }

    private suspend fun finalizeImport(uri: Uri, fileName: String, allowOverwrite: Boolean) {
        val context = getApplication<Application>().applicationContext
        try {
            val internalFile = File(context.filesDir, fileName)

            if (internalFile.exists() && !allowOverwrite) {
                return
            }

            val inputStream = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(internalFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            val savedPath = "file://${internalFile.absolutePath}"
            val format = when {
                fileName.contains("pdf", ignoreCase = true) -> "pdf"
                fileName.contains("epub", ignoreCase = true) -> "epub"
                fileName.contains("txt", ignoreCase = true) -> "txt"
                fileName.contains("rtf", ignoreCase = true) -> "rtf"
                else -> "epub"
            }

            val title = fileName.substringBeforeLast(".")

            var coverPath: String? = null
            if (format == "epub") {
                coverPath = EpubUtils.extractCoverImage(context, uri, title)
            } else if (format == "pdf") {
                coverPath = generatePdfCover(context, uri, title)
            }

            val newBook = BookEntity(
                title = title,
                filePath = savedPath,
                coverImage = coverPath,
                format = format,
                dateAdded = System.currentTimeMillis() // Set import time
            )

            repository.addBook(newBook)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generatePdfCover(context: android.content.Context, uri: Uri, title: String): String? {
        return try {
            val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val renderer = PdfRenderer(fileDescriptor)
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            fileDescriptor.close()
            val safeName = title.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val coverFile = File(context.filesDir, "${safeName}_cover.jpg")
            FileOutputStream(coverFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            "file://${coverFile.absolutePath}"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteBook(book: BookEntity, deleteFromDevice: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            var fileName = ""
            if (book.filePath.startsWith("file://")) {
                try {
                    val file = File(book.filePath.removePrefix("file://"))
                    fileName = file.name
                    if (file.exists()) file.delete()
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (book.coverImage != null && book.coverImage.startsWith("file://")) {
                try {
                    File(book.coverImage.removePrefix("file://")).delete()
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (fileName.isNotEmpty()) {
                if (deleteFromDevice) {
                    _importFolder.value?.let { uriString ->
                        try {
                            val context = getApplication<Application>()
                            val folder = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
                            val sourceFile = folder?.findFile(fileName)
                            sourceFile?.delete()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    repository.markAsDeleted(fileName)
                }
            }

            repository.deleteBook(book)
        }
    }

    fun updateBookProgress(bookId: Int, progress: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateProgress(bookId, progress)
        }
    }

    fun renameBook(book: BookEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameBook(book.id, newName)
        }
    }
}
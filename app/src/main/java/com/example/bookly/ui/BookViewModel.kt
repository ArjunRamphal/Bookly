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
import com.example.bookly.utils.BookUtils
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

enum class SortOption {
    ALPHABETICAL,
    DATE_ADDED_DESC,
    DATE_ADDED_ASC
}

class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BookRepository
    private val prefs = application.getSharedPreferences("bookly_prefs", Context.MODE_PRIVATE)

    // --- SEARCH & SORT ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.ALPHABETICAL)
    val sortOption = _sortOption.asStateFlow()

    // --- IMPORT FOLDER ---
    private val _importFolder = MutableStateFlow<String?>(prefs.getString("import_folder", null))
    val importFolder = _importFolder.asStateFlow()

    // --- FONTS ---
    private val fontsDir = File(application.filesDir, "fonts")

    // List of file names (e.g., "Roboto.ttf", "OpenDyslexic.otf")
    private val _availableFonts = MutableStateFlow<List<String>>(emptyList())
    val availableFonts = _availableFonts.asStateFlow()

    // Currently selected font filename (null = Default System Font)
    private val _selectedFont = MutableStateFlow<String?>(prefs.getString("selected_font", null))
    val selectedFont = _selectedFont.asStateFlow()

    val libraryBooks: StateFlow<List<BookEntity>>

    init {
        val bookDao = BookDatabase.getDatabase(application).bookDao()
        repository = BookRepository(bookDao)

        if (!fontsDir.exists()) fontsDir.mkdirs()
        refreshFonts()

        libraryBooks = combine(repository.allBooks, _searchQuery, _sortOption) { books, query, sort ->
            val filtered = if (query.isBlank()) books else books.filter { it.title.contains(query, ignoreCase = true) }
            when (sort) {
                SortOption.ALPHABETICAL -> filtered.sortedBy { it.title.lowercase() }
                SortOption.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateAdded }
                SortOption.DATE_ADDED_ASC -> filtered.sortedBy { it.dateAdded }
            }
        }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

        _importFolder.value?.let { uriString ->
            try { scanImportFolder(Uri.parse(uriString)) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // --- FONT LOGIC ---

    private fun refreshFonts() {
        val files = fontsDir.listFiles()
        _availableFonts.value = files?.map { it.name }?.sorted() ?: emptyList()
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var fileName = "CustomFont.ttf"

            // Get original filename
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            try {
                val destFile = File(fontsDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                refreshFonts()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteFont(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            File(fontsDir, fileName).delete()
            if (_selectedFont.value == fileName) {
                selectFont(null) // Reset to default if deleted
            }
            refreshFonts()
        }
    }

    fun selectFont(fileName: String?) {
        _selectedFont.value = fileName
        prefs.edit().putString("selected_font", fileName).apply()
    }

    // --- EXISTING LOGIC ---

    var showDuplicateDialog by mutableStateOf(false)
        private set
    private var pendingUri: Uri? = null
    private var pendingTitle: String = ""

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    fun onSortOptionChanged(option: SortOption) { _sortOption.value = option }

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
                if (deletedFiles.contains(name)) continue
                val title = name.substringBeforeLast(".")
                if (existingBooks.any { it.title.equals(title, ignoreCase = true) }) continue
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
            if (existingBooks.any { it.title == title }) {
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
                pendingUri = null; pendingTitle = ""; showDuplicateDialog = false
            }
        }
    }

    fun cancelImport() {
        pendingUri = null; pendingTitle = ""; showDuplicateDialog = false
    }

    private suspend fun finalizeImport(uri: Uri, fileName: String, allowOverwrite: Boolean) {
        val context = getApplication<Application>().applicationContext
        try {
            val internalFile = File(context.filesDir, fileName)
            if (internalFile.exists() && !allowOverwrite) return
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(internalFile).use { output -> input.copyTo(output) }
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
                coverPath = BookUtils.extractCoverImage(context, uri, title)
            } else if (format == "pdf") {
                coverPath = generatePdfCover(context, uri, title)
            }
            repository.addBook(BookEntity(title = title, filePath = savedPath, coverImage = coverPath, format = format, dateAdded = System.currentTimeMillis()))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun generatePdfCover(context: Context, uri: Uri, title: String): String? {
        return try {
            val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val renderer = PdfRenderer(fileDescriptor)
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close(); renderer.close(); fileDescriptor.close()
            val safeName = title.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val coverFile = File(context.filesDir, "${safeName}_cover.jpg")
            FileOutputStream(coverFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) }
            "file://${coverFile.absolutePath}"
        } catch (e: Exception) { null }
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
                try { File(book.coverImage.removePrefix("file://")).delete() } catch (e: Exception) { e.printStackTrace() }
            }
            if (fileName.isNotEmpty()) {
                if (deleteFromDevice) {
                    _importFolder.value?.let { uriString ->
                        try {
                            val folder = DocumentFile.fromTreeUri(getApplication(), Uri.parse(uriString))
                            folder?.findFile(fileName)?.delete()
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                } else {
                    repository.markAsDeleted(fileName)
                }
            }
            repository.deleteBook(book)
        }
    }

    fun updateBookProgress(bookId: Int, progress: Float) {
        viewModelScope.launch(Dispatchers.IO) { repository.updateProgress(bookId, progress) }
    }

    fun renameBook(book: BookEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.renameBook(book.id, newName) }
    }
}
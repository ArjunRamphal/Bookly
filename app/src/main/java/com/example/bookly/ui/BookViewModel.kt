package com.example.bookly.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookly.data.BookDatabase
import com.example.bookly.data.BookEntity
import com.example.bookly.data.BookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.remote.creation.first
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first

class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BookRepository
    // This list automatically updates whenever the database changes
    val libraryBooks: StateFlow<List<BookEntity>>

    init {
        // Initialize the Database and Repository
        val bookDao = BookDatabase.Companion.getDatabase(application).bookDao()
        repository = BookRepository(bookDao)

        // Convert the Flow from the DB into a StateFlow for Compose to observe
        libraryBooks = repository.allBooks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // --- STATE FOR DIALOG ---
    var showDuplicateDialog by mutableStateOf(false)
        private set // Only ViewModel can change this

    private var pendingUri: Uri? = null
    private var pendingTitle: String = ""

    // 1. INITIAL CHECK (Called when file is picked)
    fun importBook(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext

            // Get the filename first
            var fileName = "Unknown_Book"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            val title = fileName.substringBeforeLast(".")

            // Check for duplicates
            val existingBooks = repository.allBooks.first()
            val isDuplicate = existingBooks.any { it.title == title }

            if (isDuplicate) {
                pendingUri = uri
                pendingTitle = fileName // Save the full filename for the copy step
                showDuplicateDialog = true
            } else {
                // No duplicate? Proceed immediately
                finalizeImport(uri, fileName)
            }
        }
    }

    // 2. CONFIRM (Called if user clicks "Import Anyway")
    fun confirmImport() {
        if (pendingUri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                finalizeImport(pendingUri!!, pendingTitle)
                // Reset state
                pendingUri = null
                pendingTitle = ""
                showDuplicateDialog = false
            }
        }
    }

    // 3. CANCEL (Called if user clicks "Cancel")
    fun cancelImport() {
        pendingUri = null
        pendingTitle = ""
        showDuplicateDialog = false
    }

    // 4. THE ACTUAL WORK (Private helper)
    private suspend fun finalizeImport(uri: Uri, fileName: String) {
        val context = getApplication<Application>().applicationContext
        try {
            // Copy file to internal storage
            val inputStream = context.contentResolver.openInputStream(uri)
            val internalFile = java.io.File(context.filesDir, fileName)
            val outputStream = java.io.FileOutputStream(internalFile)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            val format = if (fileName.contains("pdf", ignoreCase = true)) "pdf" else "epub"
            val title = fileName.substringBeforeLast(".")
            val savedPath = "file://${internalFile.absolutePath}"

            val newBook = BookEntity(
                title = title,
                filePath = savedPath,
                coverImage = null,
                format = format
            )

            repository.addBook(newBook)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (book.filePath.startsWith("file://")) {
                try {
                    val file = java.io.File(book.filePath.removePrefix("file://"))
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            // Remove from Database
            repository.deleteBook(book)
        }
    }

    fun updateBookProgress(bookId: Int, progress: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateProgress(bookId, progress)
        }
    }
}
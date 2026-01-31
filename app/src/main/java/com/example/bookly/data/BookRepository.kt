package com.example.bookly.data

import kotlinx.coroutines.flow.Flow

class BookRepository(private val bookDao: BookDao) {

    // Get all books as a real-time stream (Flow)
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun addBook(book: BookEntity) {
        bookDao.insertBook(book)
    }

    suspend fun deleteBook(book: BookEntity) {
        bookDao.deleteBook(book)
    }

    suspend fun updateProgress(bookId: Int, progress: Float) {
        bookDao.updateProgress(bookId, progress)
    }

    suspend fun renameBook(bookId: Int, newTitle: String) {
        bookDao.updateTitle(bookId, newTitle)
    }

    suspend fun getDeletedFileNames(): List<String> {
        return bookDao.getDeletedFileNames()
    }

    suspend fun markAsDeleted(fileName: String) {
        bookDao.markBookAsDeleted(DeletedBookEntity(fileName))
    }
}
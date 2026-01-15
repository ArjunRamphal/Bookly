package com.example.bookly.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bookly.data.BookEntity

@Composable
fun HomeScreen(
    onBookClick: (Int, String, String) -> Unit,
    viewModel: BookViewModel = viewModel()
) {

    val view = LocalView.current
    val isSystemDark = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        val window = (view.context as Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)

        insetsController.isAppearanceLightStatusBars = !isSystemDark
        insetsController.isAppearanceLightNavigationBars = !isSystemDark
    }

    val books by viewModel.libraryBooks.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importBook(it)
        }
    }

    if (viewModel.showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text("Duplicate Book") },
            text = { Text("A book with this title already exists. Do you want to import it again?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmImport() }
                ) {
                    Text("Import Anyway")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelImport() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    launcher.launch(arrayOf("application/pdf", "application/epub+zip"))
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Book")
            }
        }
    ) { padding ->

        if (books.isEmpty()) {
            Box(modifier = Modifier.padding(padding)) {
                EmptyState(
                    padding = padding
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),

                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),

                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(books) { book ->
                    BookItem(
                        book = book,
                        onClick = { onBookClick(book.id, book.filePath, book.format) },
                        onDelete = { viewModel.deleteBook(book) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookItem(
    book: BookEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverImage != null) {
                        AsyncImage(
                            model = book.coverImage,
                            contentDescription = "Cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = book.title.take(1).uppercase(),
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White
                        )
                    }
                }

                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = book.format.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showMenu = false
                    showDeleteConfirm = true
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                }
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Book") },
            text = { Text("Are you sure you want to remove '${book.title}' from your library?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmptyState(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Book,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your library is empty.",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Tap the + button to add a book.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}
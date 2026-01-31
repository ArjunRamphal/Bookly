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
import androidx.compose.material.icons.filled.*
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val importFolder by viewModel.importFolder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBook(it) }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.setImportFolder(it) }
    }

    if (viewModel.showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text("Duplicate Book") },
            text = { Text("A book with this title already exists. Do you want to import it again?") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) { Text("Import Anyway") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImport() }) { Text("Cancel") }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Settings") },
            text = {
                Column {
                    Text("Automatic Import", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select a folder. The app will automatically import books from this folder on startup.", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { folderLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Folder")
                    }

                    if (importFolder != null) {
                        Spacer(modifier = Modifier.height(12.dp))

                        val readablePath = remember(importFolder) {
                            val decoded = Uri.decode(importFolder)
                            when {
                                decoded.contains("primary:") -> {
                                    val path = decoded.substringAfter("primary:")
                                    if (path.isEmpty()) "Internal Storage" else "Internal Storage > $path"
                                }
                                decoded.contains("tree/") -> {
                                    val raw = decoded.substringAfter("tree/")
                                    if (raw.contains(":")) "SD Card > " + raw.substringAfter(":") else raw
                                }
                                else -> decoded
                            }
                        }

                        Text(
                            text = "Currently monitoring:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = readablePath,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookly") },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    fileLauncher.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain", "application/rtf", "text/rtf"))
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Book")
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- SEARCH BAR & FILTER ROW ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search library...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    FilledTonalIconButton(
                        onClick = { showFilterMenu = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }

                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Alphabetical (A-Z)") },
                            onClick = {
                                viewModel.onSortOptionChanged(SortOption.ALPHABETICAL)
                                showFilterMenu = false
                            },
                            trailingIcon = {
                                if (sortOption == SortOption.ALPHABETICAL) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Date Added (Newest)") },
                            onClick = {
                                viewModel.onSortOptionChanged(SortOption.DATE_ADDED_DESC)
                                showFilterMenu = false
                            },
                            trailingIcon = {
                                if (sortOption == SortOption.DATE_ADDED_DESC) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Date Added (Oldest)") },
                            onClick = {
                                viewModel.onSortOptionChanged(SortOption.DATE_ADDED_ASC)
                                showFilterMenu = false
                            },
                            trailingIcon = {
                                if (sortOption == SortOption.DATE_ADDED_ASC) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }

            if (books.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (searchQuery.isNotEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No books found", color = Color.Gray)
                        }
                    } else {
                        EmptyState(padding = PaddingValues(0.dp))
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 128.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(books) { book ->
                        BookItem(
                            book = book,
                            onClick = {
                                val encodedPath = Uri.encode(book.filePath)
                                onBookClick(book.id, encodedPath, book.format)
                            },
                            onDelete = { deleteFromDevice ->
                                viewModel.deleteBook(book, deleteFromDevice)
                            },
                            onRename = { newName -> viewModel.renameBook(book, newName) },
                            onResetProgress = { viewModel.updateBookProgress(book.id, 0f) }
                        )
                    }
                }
            }
        }
    }
}

// ... (BookItem and EmptyState remain unchanged from previous versions)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookItem(
    book: BookEntity,
    onClick: () -> Unit,
    onDelete: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onResetProgress: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
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

                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.height(40.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = book.format.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )

                        if (book.progress > 0) {
                            Text(
                                text = "${book.progress.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    showMenu = false
                    showRenameDialog = true
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                }
            )

            DropdownMenuItem(
                text = { Text("Reset Progress") },
                onClick = {
                    showMenu = false
                    showResetConfirm = true
                },
                leadingIcon = {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            )

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

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Progress") },
            text = { Text("Are you sure you want to reset reading progress for '${book.title}' to 0%?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetProgress()
                        showResetConfirm = false
                    }
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(book.title) }

        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Book") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRename(newName)
                            showRenameDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        var deleteFromDevice by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Book") },
            text = {
                Column {
                    Text("Are you sure you want to remove '${book.title}' from your library?")
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { deleteFromDevice = !deleteFromDevice }
                            )
                    ) {
                        Checkbox(
                            checked = deleteFromDevice,
                            onCheckedChange = { deleteFromDevice = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delete file from device",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(deleteFromDevice)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EmptyState(padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
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
        Text(text = "Your library is empty.", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Tap the + button to add a book.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}
package com.example.bookly.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: BookViewModel = viewModel()
) {
    val importFolder by viewModel.importFolder.collectAsState()
    val availableFonts by viewModel.availableFonts.collectAsState()

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.setImportFolder(it) }
    }

    val fontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFont(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Library Section ---
            Text(
                text = "Library Management",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            val readablePath = remember(importFolder) {
                if (importFolder == null) "Not configured"
                else Uri.decode(importFolder)
            }

            ListItem(
                headlineContent = { Text("Automatic Import Folder") },
                supportingContent = { Text(readablePath) },
                leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                modifier = Modifier.clickable { folderLauncher.launch(null) }
            )

            HorizontalDivider()

            // --- Fonts Section ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom Fonts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = {
                        // Launch for TTF/OTF
                        fontLauncher.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype"))
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Font")
                }
            }

            if (availableFonts.isEmpty()) {
                ListItem(
                    headlineContent = { Text("No custom fonts added") },
                    supportingContent = { Text("Import .ttf or .otf files to use them in the reader.") },
                    leadingContent = { Icon(Icons.Default.FontDownload, contentDescription = null) }
                )
            } else {
                availableFonts.forEach { fontName ->
                    ListItem(
                        headlineContent = { Text(fontName) },
                        leadingContent = { Icon(Icons.Default.FontDownload, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteFont(fontName) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete font", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }
        }
    }
}